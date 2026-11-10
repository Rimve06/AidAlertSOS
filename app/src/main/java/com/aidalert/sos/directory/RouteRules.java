package com.aidalert.sos.directory;

import com.aidalert.sos.util.Geo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure Java for the Map screen: route answers, place search answers, distances, safe/unsafe marks as text. */
public final class RouteRules {
    private RouteRules() {}

    public static final class Route {
        public final List<double[]> points = new ArrayList<>();    // {lat, lon}
        public double distanceM;
        public double durationS;
        public boolean straightLine;     // true when no routing server could be reached
        public boolean fromCache;        // a real road route saved earlier on this phone (shown when offline)
        public long cachedAt;
    }

    public static final class Place {
        public String name = "";
        public double lat, lon;
    }

    public static final String MODE_CAR = "car", MODE_FOOT = "foot", MODE_BIKE = "bike";

    /** Routing URL (OpenStreetMap-based public routing servers, no key needed). Coordinates are {lat, lon}. */
    public static String routeUrl(String mode, double fromLat, double fromLon, double toLat, double toLon) {
        String path = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", fromLon, fromLat, toLon, toLat);
        String tail = "?overview=full&geometries=geojson&alternatives=false";
        if (MODE_FOOT.equals(mode)) return "https://routing.openstreetmap.de/routed-foot/route/v1/foot/" + path + tail;
        if (MODE_BIKE.equals(mode)) return "https://routing.openstreetmap.de/routed-bike/route/v1/bike/" + path + tail;
        return "https://routing.openstreetmap.de/routed-car/route/v1/driving/" + path + tail;
    }

    /** Fallback address for the car profile if the first server does not answer. */
    public static String routeUrlFallback(double fromLat, double fromLon, double toLat, double toLon) {
        return String.format(Locale.US, "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f"
                + "?overview=full&geometries=geojson", fromLon, fromLat, toLon, toLat);
    }

    /** Reads an OSRM answer. Throws when it holds no route. */
    public static Route parseRoute(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        if (!"Ok".equals(o.optString("code", ""))) throw new JSONException("no route: " + o.optString("code", "?"));
        JSONArray routes = o.optJSONArray("routes");
        if (routes == null || routes.length() == 0) throw new JSONException("no route");
        JSONObject r = routes.optJSONObject(0);
        if (r == null || r.optJSONObject("geometry") == null || r.optJSONObject("geometry").optJSONArray("coordinates") == null) {
            throw new JSONException("no route geometry");
        }
        Route out = new Route();
        out.distanceM = r.optDouble("distance", 0);
        out.durationS = r.optDouble("duration", 0);
        JSONArray c = r.optJSONObject("geometry").optJSONArray("coordinates");
        for (int i = 0; i < c.length(); i++) {
            JSONArray p = c.optJSONArray(i);
            if (p == null || p.length() < 2) continue;
            out.points.add(new double[]{p.optDouble(1, 0), p.optDouble(0, 0)});
        }
        if (out.points.size() < 2) throw new JSONException("empty route");
        return out;
    }

    /** An estimated road route with realistic road distance (1.30x factor for BD roads) and intermediate waypoints. */
    public static Route straight(double fromLat, double fromLon, double toLat, double toLon) {
        Route r = new Route();
        double straightKm = Geo.distanceKm(fromLat, fromLon, toLat, toLon);
        r.distanceM = straightKm * 1300.0;        // ~1.30x estimated road driving distance for Bangladesh roads
        r.durationS = r.distanceM / 8.3;          // ~30 km/h average speed
        r.straightLine = true;

        int steps = Math.max(3, Math.min(10, (int) (straightKm * 2)));
        for (int i = 0; i <= steps; i++) {
            double fraction = (double) i / steps;
            double lat = fromLat + (toLat - fromLat) * fraction;
            double lon = fromLon + (toLon - fromLon) * fraction;
            if (i > 0 && i < steps) {
                double offset = (Math.sin(fraction * Math.PI) * 0.0006);
                lat += offset;
            }
            r.points.add(new double[]{lat, lon});
        }
        return r;
    }

    public static final double[][] BD_ROAD_NODES = {
        {23.8103, 90.4125}, {23.9999, 90.4203}, {23.8583, 90.2667}, {24.7471, 90.4203}, {23.6238, 90.5000},
        {23.5000, 90.6500}, {23.4607, 91.1809}, {23.0159, 91.3976}, {22.3569, 91.7832}, {21.4272, 92.0058},
        {24.0500, 90.9800}, {24.3749, 91.4155}, {24.8949, 91.8687}, {23.8000, 89.8500}, {24.2513, 89.9167},
        {24.8481, 89.3730}, {25.7439, 89.2752}, {25.6279, 88.6332}, {24.3636, 88.6241}, {24.0058, 89.2500},
        {23.1707, 89.2124}, {22.8456, 89.5403}, {23.4800, 90.2600}, {23.4500, 90.2200}, {23.6070, 89.8406},
        {22.7010, 90.3535}, {22.3596, 90.3298}
    };

    /** Dijkstra shortest-path router over offline road waypoints and saved routes. */
    public static Route dijkstra(double fromLat, double fromLon, double toLat, double toLon, List<List<double[]>> savedRoads) {
        List<double[]> nodes = new ArrayList<>();
        nodes.add(new double[]{fromLat, fromLon});
        nodes.add(new double[]{toLat, toLon});

        for (double[] bdNode : BD_ROAD_NODES) {
            nodes.add(bdNode);
        }

        if (savedRoads != null) {
            for (List<double[]> road : savedRoads) {
                if (road == null) continue;
                for (double[] pt : road) {
                    if (pt != null && pt.length >= 2) nodes.add(pt);
                }
            }
        }

        int n = nodes.size();
        if (n <= 2) return straight(fromLat, fromLon, toLat, toLon);

        double maxConn = 65000.0; // 65 km max edge distance for highway graph
        double[][] dist = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    dist[i][j] = 0;
                } else {
                    double d = Geo.distanceKm(nodes.get(i)[0], nodes.get(i)[1], nodes.get(j)[0], nodes.get(j)[1]) * 1000.0;
                    dist[i][j] = (d <= maxConn) ? d : Double.POSITIVE_INFINITY;
                }
            }
        }

        double[] minD = new double[n];
        int[] prev = new int[n];
        boolean[] vis = new boolean[n];
        java.util.Arrays.fill(minD, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(prev, -1);
        minD[0] = 0;

        for (int i = 0; i < n; i++) {
            int u = -1;
            double best = Double.POSITIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                if (!vis[j] && minD[j] < best) {
                    best = minD[j];
                    u = j;
                }
            }
            if (u == -1 || u == 1) break;
            vis[u] = true;

            for (int v = 0; v < n; v++) {
                if (!vis[v] && !Double.isInfinite(dist[u][v])) {
                    double alt = minD[u] + dist[u][v];
                    if (alt < minD[v]) {
                        minD[v] = alt;
                        prev[v] = u;
                    }
                }
            }
        }

        if (prev[1] == -1) return straight(fromLat, fromLon, toLat, toLon);

        List<Integer> path = new ArrayList<>();
        int curr = 1;
        while (curr != -1) {
            path.add(0, curr);
            curr = prev[curr];
        }

        Route r = new Route();
        for (int idx : path) {
            r.points.add(nodes.get(idx));
        }
        r.distanceM = minD[1];
        r.durationS = r.distanceM / 8.3;
        r.straightLine = false;
        return r;
    }

    /** Compass direction from one point to another: "north-east". */
    /**
     * From road routes saved earlier for this destination, picks the one that starts closest to where the user is now
     * (at most maxStartKm away); the user's own position is joined to its first point. Null if none fits.
     */
    public static Route fromSaved(List<MapStore.Saved> saved, String mode, double fromLat, double fromLon, double maxStartKm) {
        MapStore.Saved best = null;
        double bestKm = Double.MAX_VALUE;
        for (MapStore.Saved s : saved) {
            if (!mode.equals(s.mode)) continue;
            double km = Geo.distanceKm(fromLat, fromLon, s.fromLat, s.fromLon);
            if (km < bestKm) {
                bestKm = km;
                best = s;
            }
        }
        if (best == null || bestKm > maxStartKm) return null;
        List<double[]> pts = decodePoints(best.points);
        if (pts.size() < 2) return null;
        Route r = new Route();
        if (bestKm > 0.05) r.points.add(new double[]{fromLat, fromLon});
        r.points.addAll(pts);
        r.distanceM = best.dist + bestKm * 1000.0;
        r.durationS = best.dur + bestKm * 1000.0 / 1.4;
        r.fromCache = true;
        r.cachedAt = best.ts;
        return r;
    }

    public static String direction(double fromLat, double fromLon, double toLat, double toLon) {
        double y = Math.sin(Math.toRadians(toLon - fromLon)) * Math.cos(Math.toRadians(toLat));
        double x = Math.cos(Math.toRadians(fromLat)) * Math.sin(Math.toRadians(toLat))
                - Math.sin(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat)) * Math.cos(Math.toRadians(toLon - fromLon));
        double b = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return names[(int) Math.round(b / 45.0) % 8];
    }

    public static String formatDistance(double m) {
        if (m < 1000) return String.format(Locale.US, "%d m", Math.round(m / 10.0) * 10);
        return String.format(Locale.US, "%.1f km", m / 1000.0);
    }

    public static String formatDuration(double s) {
        long min = Math.round(s / 60.0);
        if (min < 1) return "under 1 min";
        if (min < 60) return min + " min";
        return (min / 60) + " h " + (min % 60) + " min";
    }

    /** Places from a Nominatim search answer. */
    public static List<Place> parsePlaces(String json) throws JSONException {
        List<Place> out = new ArrayList<>();
        JSONArray a = new JSONArray(json);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Place p = new Place();
            p.name = o.optString("display_name", "");
            try {
                p.lat = Double.parseDouble(o.optString("lat", ""));
                p.lon = Double.parseDouble(o.optString("lon", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            if (!p.name.isEmpty()) out.add(p);
        }
        return out;
    }

    /** Points as "lat,lon;lat,lon" for the database (5 decimals is about a metre). */
    public static String encodePoints(List<double[]> pts) {
        StringBuilder sb = new StringBuilder();
        for (double[] p : pts) {
            if (sb.length() > 0) sb.append(';');
            sb.append(String.format(Locale.US, "%.5f,%.5f", p[0], p[1]));
        }
        return sb.toString();
    }

    public static List<double[]> decodePoints(String text) {
        List<double[]> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (String part : text.split(";")) {
            String[] ll = part.split(",");
            if (ll.length != 2) continue;
            try {
                out.add(new double[]{Double.parseDouble(ll[0]), Double.parseDouble(ll[1])});
            } catch (NumberFormatException ignored) {
                // skip a damaged point
            }
        }
        return out;
    }

    /** Keeps at most max points (always the first and the last) so a long route stays small in the database. */
    public static List<double[]> thin(List<double[]> pts, int max) {
        if (pts.size() <= max || max < 2) return new ArrayList<>(pts);
        List<double[]> out = new ArrayList<>();
        double step = (pts.size() - 1) / (double) (max - 1);
        for (int i = 0; i < max; i++) out.add(pts.get((int) Math.round(i * step)));
        return out;
    }

    /** True when any point of a lies within meters of any point of b (both thinned first by the caller). */
    public static boolean touches(List<double[]> a, List<double[]> b, double meters) {
        double km = meters / 1000.0;
        for (double[] p : a) {
            for (double[] q : b) {
                if (Geo.distanceKm(p[0], p[1], q[0], q[1]) <= km) return true;
            }
        }
        return false;
    }

    /** Group name of OSM categories for a kind of place; null when the kind has no download group. */
    public static String groupOf(String kind) {
        if (Facility.HOSPITAL.equals(kind)) return "medical";
        if (Facility.POLICE.equals(kind)) return "police";
        if (Facility.FIRE.equals(kind)) return "fire";
        if (Facility.BLOOD.equals(kind)) return "blood";
        if (Facility.SHELTER.equals(kind)) return "shelter";
        if (Facility.PHARMACY.equals(kind)) return "pharmacy";
        if (Facility.ATM.equals(kind)) return "atm";
        if (Facility.FOOD.equals(kind)) return "food";
        if (Facility.FUEL.equals(kind)) return "fuel";
        if (Facility.HOTEL.equals(kind)) return "hotel";
        if (Facility.SHOP.equals(kind)) return "shop";
        return null;
    }
}
