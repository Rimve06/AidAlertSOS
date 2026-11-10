package com.aidalert.sos.directory;

import android.content.Context;
import android.location.Location;

import com.aidalert.sos.engine.LocationHelper;
import com.aidalert.sos.net.Http;
import com.aidalert.sos.util.Geo;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * While online, saves real road routes from where the user is to the nearest hospitals, police stations and fire
 * stations within 50 km, so the map can still show real roads (not a straight line) when the internet is gone.
 * Background thread only.
 */
public final class RouteCache {
    private RouteCache() {}

    public static final double RADIUS_KM = 50;
    public static final double CLOSE_KM = 20;
    private static final int MIN_PER_KIND = 4, MAX_PER_KIND = 10;
    private static final long REFRESH_MS = 6L * 3600 * 1000;
    private static boolean running;

    public static void warmFromLastKnown(Context app) {
        try {
            Location l = new LocationHelper(app).lastKnown();
            if (l != null) warm(app, l.getLatitude(), l.getLongitude(), false);
        } catch (RuntimeException ignored) {
            // no location permission yet
        }
    }

    public static void warm(Context app, double lat, double lon, boolean force) {
        synchronized (RouteCache.class) {
            if (running || !NetUtil.isOnline(app)) return;
            running = true;
        }
        try {
            MapStore ms = new MapStore(app);
            if (!force) {
                try {
                    double wl = Double.parseDouble(ms.meta("warm2_lat"));
                    double wo = Double.parseDouble(ms.meta("warm2_lon"));
                    long ts = Long.parseLong(ms.meta("warm2_ts"));
                    if (System.currentTimeMillis() - ts < REFRESH_MS && Geo.distanceKm(lat, lon, wl, wo) < 3) return;
                } catch (NumberFormatException ignored) {
                    // never warmed
                }
            }
            // make sure police, fire, blood and shelter places around here are in the offline directory (50 km), not only hospitals
            try {
                new DirectoryRepo(app).syncNearby(lat, lon, false);
            } catch (RuntimeException ignored) {
                // use what is already stored
            }
            List<Facility> near = new ArrayList<>();
            List<Facility> every = new DirectoryStore(app).facilities();
            for (String kind : Facility.CORE) {            // hospital, police, fire, blood, shelter
                List<Facility> k = new ArrayList<>();
                for (Facility f : every) {
                    if (!kind.equals(f.kind) || !f.hasLocation()) continue;
                    f.distanceKm = Geo.distanceKm(lat, lon, f.lat, f.lon);
                    if (f.distanceKm <= RADIUS_KM) k.add(f);
                }
                Collections.sort(k, new Comparator<Facility>() {
                    @Override
                    public int compare(Facility a, Facility b) {
                        return Double.compare(a.distanceKm, b.distanceKm);
                    }
                });
                int within20 = 0;
                for (Facility f : k) if (f.distanceKm <= CLOSE_KM) within20++;
                // everything within 20 km (at most 10 per kind), and at least the 4 nearest within 50 km
                near.addAll(k.subList(0, Math.min(k.size(), Math.max(MIN_PER_KIND, Math.min(MAX_PER_KIND, within20)))));
            }
            int saved = 0;
            for (Facility f : near) {
                if (!NetUtil.isOnline(app)) break;
                try {
                    RouteRules.Route r = RouteRules.parseRoute(Http.get(
                            RouteRules.routeUrl(RouteRules.MODE_CAR, lat, lon, f.lat, f.lon), "application/json", 2 * 1024 * 1024, 15000));
                    MapStore.Saved s = new MapStore.Saved();
                    s.fromLat = lat; s.fromLon = lon; s.toLat = f.lat; s.toLon = f.lon; s.mode = RouteRules.MODE_CAR;
                    s.dist = r.distanceM; s.dur = r.durationS;
                    s.points = RouteRules.encodePoints(RouteRules.thin(r.points, 400));
                    ms.saveRoute(s);
                    saved++;
                    Thread.sleep(400);                   // be gentle with the free routing server
                } catch (IOException | JSONException e) {
                    // skip this one
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (saved > 0 || near.isEmpty()) {
                ms.setMeta("warm2_lat", String.valueOf(lat));
                ms.setMeta("warm2_lon", String.valueOf(lon));
                ms.setMeta("warm2_ts", String.valueOf(System.currentTimeMillis()));
            }
        } catch (RuntimeException ignored) {
            // never crash a background warm-up
        } finally {
            synchronized (RouteCache.class) {
                running = false;
            }
        }
    }
}
