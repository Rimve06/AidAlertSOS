package com.aidalert.sos.directory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds hospitals, fire stations and police stations around the phone from OpenStreetMap (Overpass API),
 * on the phone itself. Data (c) OpenStreetMap contributors, ODbL. Phone numbers in OSM are volunteer entered,
 * so they are always shown as unverified. Pure Java apart from the HTTP call in {@linkDirectoryRepo}.
 */
public final class OsmNearby {
    private OsmNearby() {}

    public static final String SOURCE = "OpenStreetMap contributors (ODbL)";

    /** Overpass QL for the three kinds inside a radius in metres. */
    public static String query(double lat, double lon, int radiusM) {
        String around = String.format(Locale.US, "(around:%d,%.5f,%.5f)", radiusM, lat, lon);
        return "[out:json][timeout:15];("
                + "nwr[\"amenity\"~\"^(hospital|clinic)$\"]" + around + ";"
                + "nwr[\"amenity\"=\"fire_station\"]" + around + ";"
                + "nwr[\"amenity\"=\"police\"]" + around + ";"
                + "nwr[\"healthcare\"=\"blood_donation\"]" + around + ";"
                + "nwr[\"amenity\"=\"blood_bank\"]" + around + ";"
                + ");out center tags 250;";
    }

    /** The groups of places the phone can download. Each group is one small, fast request. */
    public static final String[] CORE_GROUPS = {"medical", "police", "fire", "blood", "shelter"};

    private static final double M_PER_DEG = 111320.0;

    /** The Emergency directory covers everything within 50 km of the phone. */
    public static final int DIRECTORY_RADIUS_M = 50000;

    /** Bounding box "(south,west,north,east)": much cheaper for the Overpass server than a radius search. */
    static String box(double lat, double lon, int radiusM) {
        double dLat = radiusM / M_PER_DEG;
        double dLon = radiusM / (M_PER_DEG * Math.max(0.2, Math.cos(Math.toRadians(lat))));
        return String.format(Locale.US, "(%.5f,%.5f,%.5f,%.5f)", lat - dLat, lon - dLon, lat + dLat, lon + dLon);
    }

    private static String selectors(String group) {
        switch (group) {
            case "medical": return "[\"amenity\"~\"^(hospital|clinic)$\"]";
            case "police": return "[\"amenity\"=\"police\"]";
            case "fire": return "[\"amenity\"=\"fire_station\"]";
            case "blood": return "[\"healthcare\"=\"blood_donation\"]|[\"amenity\"=\"blood_bank\"]";
            case "shelter": return "[\"emergency\"~\"^(assembly_point|cyclone_shelter)$\"]|[\"building\"=\"cyclone_shelter\"]"
                    + "|[\"amenity\"=\"shelter\"][\"shelter_type\"~\"disaster|public_shelter|cyclone|emergency\"]|[\"social_facility\"=\"shelter\"]";
            case "pharmacy": return "[\"amenity\"=\"pharmacy\"]";
            case "atm": return "[\"amenity\"=\"atm\"]";
            case "food": return "[\"amenity\"~\"^(restaurant|cafe|fast_food)$\"]";
            case "fuel": return "[\"amenity\"=\"fuel\"]";
            case "hotel": return "[\"tourism\"~\"^(hotel|guest_house)$\"]";
            case "shop": return "[\"shop\"~\"^(supermarket|mall|department_store)$\"]";
            default: return null;
        }
    }

    /** Overpass QL for ONE group inside a radius. Null for an unknown group. */
    public static String groupQuery(String group, double lat, double lon, int radiusM) {
        String sel = selectors(group);
        if (sel == null) return null;
        String bb = box(lat, lon, radiusM);
        StringBuilder q = new StringBuilder(radiusM >= 30000 ? "[out:json][timeout:60];(" : "[out:json][timeout:25];(");
        for (String one : sel.split("\\|(?=\\[)")) {
            q.append("node").append(one).append(bb).append(";way").append(one).append(bb).append(";");
        }
        return q.append(");out center tags ").append(radiusM >= 30000 ? 900 : 300).append(";").toString();
    }

    public static String kindOf(String amenity) {
        if ("hospital".equals(amenity) || "clinic".equals(amenity)) return Facility.HOSPITAL;
        if ("fire_station".equals(amenity)) return Facility.FIRE;
        if ("police".equals(amenity)) return Facility.POLICE;
        return null;
    }

    /** Kind from all tags: blood banks and blood donation centres first, then the amenity. */
    public static String kindOfTags(JSONObject tags) {
        if ("blood_donation".equals(tags.optString("healthcare", "")) || "blood_bank".equals(tags.optString("amenity", ""))
                || "blood_bank".equals(tags.optString("healthcare", ""))) return Facility.BLOOD;
        if (tags.optString("emergency", "").matches("assembly_point|cyclone_shelter") || "cyclone_shelter".equals(tags.optString("building", ""))
                || "shelter".equals(tags.optString("social_facility", ""))
                || ("shelter".equals(tags.optString("amenity", "")) && tags.optString("shelter_type", "").matches(".*(disaster|public_shelter|cyclone|emergency).*"))) {
            return Facility.SHELTER;
        }
        String amenity = tags.optString("amenity", "");
        String k = kindOf(amenity);
        if (k != null) return k;
        if ("pharmacy".equals(amenity)) return Facility.PHARMACY;
        if ("atm".equals(amenity)) return Facility.ATM;
        if (amenity.matches("restaurant|cafe|fast_food")) return Facility.FOOD;
        if ("fuel".equals(amenity)) return Facility.FUEL;
        if (tags.optString("tourism", "").matches("hotel|guest_house")) return Facility.HOTEL;
        if (tags.optString("shop", "").matches("supermarket|mall|department_store")) return Facility.SHOP;
        return null;
    }

    /** First usable phone from the OSM tags, or "" (OSM often lists several separated by ";"). */
    public static String phoneFrom(JSONObject tags) {
        String[] keys = {"phone", "contact:phone", "contact:mobile", "mobile", "emergency:phone"};
        for (String k : keys) {
            String v = tags.optString(k, "");
            if (v.isEmpty()) continue;
            for (String part : v.split("[;,/]")) {
                String c = PhoneRules.clean(part);
                if (!c.isEmpty()) return c;
            }
        }
        return "";
    }

    public static List<Facility> parse(String json, long now) throws JSONException {
        List<Facility> out = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray els = root.optJSONArray("elements");
        if (els == null) return out;
        for (int i = 0; i < els.length(); i++) {
            JSONObject e = els.optJSONObject(i);
            if (e == null) continue;
            JSONObject tags = e.optJSONObject("tags");
            if (tags == null) continue;
            String kind = kindOfTags(tags);
            if (kind == null) continue;
            double lat = e.has("lat") ? e.optDouble("lat") : Double.NaN;
            double lon = e.has("lon") ? e.optDouble("lon") : Double.NaN;
            JSONObject c = e.optJSONObject("center");
            if (c != null) {
                lat = c.optDouble("lat", lat);
                lon = c.optDouble("lon", lon);
            }
            String name = tags.optString("name:en", "");
            if (name.isEmpty()) name = tags.optString("name", "");
            if (name.isEmpty()) {
                name = Facility.HOSPITAL.equals(kind) ? "Unnamed medical facility"
                        : Facility.FIRE.equals(kind) ? "Unnamed fire station"
                        : Facility.BLOOD.equals(kind) ? "Unnamed blood centre"
                        : Facility.POLICE.equals(kind) ? "Unnamed police station"
                        : "Unnamed " + Facility.label(kind).toLowerCase(Locale.ROOT);
            }
            String type = e.optString("type", "node");
            long id = e.optLong("id");
            Facility f = new Facility();
            f.id = "osm:" + type + "/" + id;
            f.kind = kind;
            f.name = name;
            f.phone = phoneFrom(tags);
            f.phoneVerified = false;
            f.lat = lat;
            f.lon = lon;
            String d = tags.optString("addr:district", "");
            if (d.isEmpty()) d = tags.optString("addr:city", "");
            if (d.isEmpty()) d = tags.optString("addr:suburb", "");
            f.district = d;
            f.source = SOURCE;
            f.sourceUrl = "https://www.openstreetmap.org/" + type + "/" + id;
            f.updated = now;
            if (f.hasLocation()) out.add(f);
        }
        return out;
    }

    /** One ~11 km grid cell per 0.1 degree, so the phone does not ask the server again while standing still. */
    public static String cellKey(double lat, double lon) {
        return String.format(Locale.US, "%.1f,%.1f", lat, lon);
    }
}
