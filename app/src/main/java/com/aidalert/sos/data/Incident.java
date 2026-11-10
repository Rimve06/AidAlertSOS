package com.aidalert.sos.data;

import android.content.ContentValues;
import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One SOS incident.
 *
 * In Firebase it is stored in two children so the citizen and the control center never overwrite
 * each other:  incidents/{id}/report  (written by the citizen phone)
 *              incidents/{id}/ops     (written by the control center: status, dispatch, notes)
 */
public class Incident {
    public static final String[] TYPES = {"MEDICAL", "ACCIDENT", "FIRE", "VIOLENCE", "MENTAL_HEALTH", "OTHER", "UNSPECIFIED"};
    public static final String[] STATUSES = {"NEW", "ACKNOWLEDGED", "CONFIRMED", "DISPATCHED", "RESOLVED", "CANCELLED", "FALSE_ALARM"};

    public String id = "";
    public String userId = "";
    public String userName = "";
    public String phone = "";
    public String type = "UNSPECIFIED";
    public String source = "";
    public String status = "NEW";
    public String via = "INTERNET";
    public String note = "";
    public String bloodGroup = "";
    public String medical = "";
    public double lat = Double.NaN;
    public double lon = Double.NaN;
    public double acc = 0;
    public boolean silent;
    public int battery = -1;
    public long createdAt;
    public long updatedAt;

    // control-center owned
    public String dispatched = "";
    public String opsNote = "";
    public String ackBy = "";
    public long opsAt;

    // local only
    public boolean synced;
    public boolean mine;

    public boolean hasFix() {
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    public boolean isActive() {
        return !("RESOLVED".equals(status) || "CANCELLED".equals(status) || "FALSE_ALARM".equals(status));
    }

    public static String label(String type) {
        if (type == null) return "Unknown";
        switch (type) {
            case "MEDICAL": return "Medical";
            case "ACCIDENT": return "Accident";
            case "FIRE": return "Fire";
            case "VIOLENCE": return "Violence / crime";
            case "MENTAL_HEALTH": return "Mental health crisis";
            case "OTHER": return "Other";
            default: return "Unspecified";
        }
    }

    public static int color(String type) {
        if (type == null) return 0xFF667085;
        switch (type) {
            case "MEDICAL": return 0xFFD81B60;
            case "ACCIDENT": return 0xFFEF6C00;
            case "FIRE": return 0xFFE65100;
            case "VIOLENCE": return 0xFF6A1B9A;
            case "MENTAL_HEALTH": return 0xFF00838F;
            case "OTHER": return 0xFF1565C0;
            default: return 0xFFC62828;
        }
    }

    private static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (JSONException ignored) {
            // NaN / infinite numbers are skipped on purpose
        }
    }

    public JSONObject toReportJson() {
        JSONObject o = new JSONObject();
        put(o, "id", id);
        put(o, "userId", userId);
        put(o, "userName", userName);
        put(o, "phone", phone);
        put(o, "type", type);
        put(o, "source", source);
        put(o, "status", status);
        put(o, "via", via);
        put(o, "note", note);
        put(o, "bloodGroup", bloodGroup);
        put(o, "medical", medical);
        put(o, "silent", silent);
        put(o, "battery", battery);
        put(o, "createdAt", createdAt);
        put(o, "updatedAt", updatedAt);
        if (hasFix()) {
            put(o, "lat", lat);
            put(o, "lon", lon);
            put(o, "acc", acc);
        }
        return o;
    }

    public JSONObject toOpsJson() {
        JSONObject o = new JSONObject();
        put(o, "status", status);
        put(o, "dispatched", dispatched);
        put(o, "note", opsNote);
        put(o, "ackBy", ackBy);
        put(o, "at", opsAt);
        return o;
    }

    /** Reads {report:{...}, ops:{...}} from Firebase. */
    public static Incident fromFirebase(String id, JSONObject node) {
        Incident i = new Incident();
        i.id = id;
        JSONObject r = node.optJSONObject("report");
        JSONObject ops = node.optJSONObject("ops");
        if (r != null) {
            i.userId = r.optString("userId", "");
            i.userName = r.optString("userName", "");
            i.phone = r.optString("phone", "");
            i.type = r.optString("type", "UNSPECIFIED");
            i.source = r.optString("source", "");
            i.status = r.optString("status", "NEW");
            i.via = r.optString("via", "INTERNET");
            i.note = r.optString("note", "");
            i.bloodGroup = r.optString("bloodGroup", "");
            i.medical = r.optString("medical", "");
            i.silent = r.optBoolean("silent", false);
            i.battery = r.optInt("battery", -1);
            i.createdAt = r.optLong("createdAt", 0);
            i.updatedAt = r.optLong("updatedAt", 0);
            if (r.has("lat") && r.has("lon")) {
                i.lat = r.optDouble("lat", Double.NaN);
                i.lon = r.optDouble("lon", Double.NaN);
                i.acc = r.optDouble("acc", 0);
            }
        }
        if (ops != null) {
            i.dispatched = ops.optString("dispatched", "");
            i.opsNote = ops.optString("note", "");
            i.ackBy = ops.optString("ackBy", "");
            i.opsAt = ops.optLong("at", 0);
            String s = ops.optString("status", "");
            // The citizen cancelling always wins; otherwise the control center decides.
            if (!s.isEmpty() && !"CANCELLED".equals(i.status)) i.status = s;
        }
        i.synced = true;
        return i;
    }

    // ------------------------------------------------------------ SQLite mapping

    public ContentValues toValues() {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("user_id", userId);
        v.put("user_name", userName);
        v.put("phone", phone);
        v.put("type", type);
        v.put("source", source);
        v.put("status", status);
        v.put("via", via);
        if (hasFix()) {
            v.put("lat", lat);
            v.put("lon", lon);
        } else {
            v.putNull("lat");
            v.putNull("lon");
        }
        v.put("acc", acc);
        v.put("silent", silent ? 1 : 0);
        v.put("note", note);
        v.put("created", createdAt);
        v.put("updated", updatedAt);
        v.put("synced", synced ? 1 : 0);
        v.put("mine", mine ? 1 : 0);
        v.put("dispatched", dispatched);
        v.put("ops_note", opsNote);
        return v;
    }

    public static Incident fromCursor(Cursor c) {
        Incident i = new Incident();
        i.id = c.getString(c.getColumnIndexOrThrow("id"));
        i.userId = c.getString(c.getColumnIndexOrThrow("user_id"));
        i.userName = c.getString(c.getColumnIndexOrThrow("user_name"));
        i.phone = c.getString(c.getColumnIndexOrThrow("phone"));
        i.type = c.getString(c.getColumnIndexOrThrow("type"));
        i.source = c.getString(c.getColumnIndexOrThrow("source"));
        i.status = c.getString(c.getColumnIndexOrThrow("status"));
        i.via = c.getString(c.getColumnIndexOrThrow("via"));
        int la = c.getColumnIndexOrThrow("lat");
        int lo = c.getColumnIndexOrThrow("lon");
        if (!c.isNull(la) && !c.isNull(lo)) {
            i.lat = c.getDouble(la);
            i.lon = c.getDouble(lo);
        }
        i.acc = c.getDouble(c.getColumnIndexOrThrow("acc"));
        i.silent = c.getInt(c.getColumnIndexOrThrow("silent")) == 1;
        i.note = c.getString(c.getColumnIndexOrThrow("note"));
        i.createdAt = c.getLong(c.getColumnIndexOrThrow("created"));
        i.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated"));
        i.synced = c.getInt(c.getColumnIndexOrThrow("synced")) == 1;
        i.mine = c.getInt(c.getColumnIndexOrThrow("mine")) == 1;
        i.dispatched = c.getString(c.getColumnIndexOrThrow("dispatched"));
        i.opsNote = c.getString(c.getColumnIndexOrThrow("ops_note"));
        return i;
    }

    public String csvRow() {
        return String.join(",",
                q(id), q(userName), q(phone), q(type), q(status), q(source), q(via),
                hasFix() ? String.valueOf(lat) : "", hasFix() ? String.valueOf(lon) : "",
                String.valueOf(createdAt), String.valueOf(updatedAt), q(dispatched), q(opsNote));
    }

    public static String csvHeader() {
        return "id,name,phone,type,status,source,via,lat,lon,created_ms,updated_ms,dispatched,ops_note";
    }

    private static String q(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }
}
