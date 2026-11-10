package com.aidalert.sos.directory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** JSON parsing for the bundled assets and the Firebase refresh. Rejects malformed entries. */
public final class DirectoryJson {
    private DirectoryJson() {}

    public static class Hotline {
        public String id = "", name = "", category = "ALL", number = "", description = "";
        public boolean verified;
        public String source = "", sourceUrl = "";
    }

    public static class FacilityFile {
        public int version;
        public String updated = "";
        public String source = "";
        public List<Facility> facilities = new ArrayList<>();
        public int rejected;
    }

    public static String cleanPhone(String raw) {
        return PhoneRules.clean(raw);
    }

    public static List<Hotline> parseHotlines(String json) throws JSONException {
        List<Hotline> out = new ArrayList<>();
        JSONArray arr = new JSONObject(json).optJSONArray("hotlines");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Hotline h = new Hotline();
            h.id = o.optString("id");
            h.name = o.optString("name");
            h.number = cleanPhone(o.optString("number"));
            if (h.id.isEmpty() || h.name.isEmpty() || h.number.isEmpty()) continue;
            h.category = o.optString("category", "ALL");
            h.description = o.optString("description");
            h.source = o.optString("source");
            h.sourceUrl = o.optString("sourceUrl");
            // A hotline only counts as verified when a source is cited.
            h.verified = o.optBoolean("verified", false) && !h.sourceUrl.isEmpty();
            out.add(h);
        }
        return out;
    }

    public static FacilityFile parseFacilities(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        FacilityFile ff = new FacilityFile();
        ff.version = root.optInt("version", 1);
        ff.updated = root.optString("updated");
        ff.source = root.optString("source");
        JSONArray arr = root.optJSONArray("facilities");
        if (arr == null) return ff;
        for (int i = 0; i < arr.length(); i++) {
            Facility f = parseFacility(arr.optJSONObject(i), ff.source);
            if (f == null) ff.rejected++;
            else ff.facilities.add(f);
        }
        return ff;
    }

    /** @return null when the entry has no id, name, valid kind or valid coordinates */
    public static Facility parseFacility(JSONObject o, String defaultSource) {
        if (o == null) return null;
        Facility f = new Facility();
        f.id = o.optString("id");
        f.name = o.optString("name").trim();
        f.kind = o.optString("kind");
        if (!(Facility.HOSPITAL.equals(f.kind) || Facility.FIRE.equals(f.kind) || Facility.POLICE.equals(f.kind))) {
            return null;
        }
        if (f.id.isEmpty() || f.name.isEmpty() || !o.has("lat") || !o.has("lon")) return null;
        f.lat = o.optDouble("lat", Double.NaN);
        f.lon = o.optDouble("lon", Double.NaN);
        if (!f.hasLocation()) return null;
        f.phone = cleanPhone(o.optString("phone"));
        // Verified only if a number exists and the source says it was checked.
        f.phoneVerified = !f.phone.isEmpty() && o.optBoolean("phoneVerified", false);
        f.district = o.optString("district");
        f.source = o.optString("source", defaultSource);
        f.sourceUrl = o.optString("sourceUrl");
        f.updated = o.optLong("updated", 0);
        return f;
    }
}
