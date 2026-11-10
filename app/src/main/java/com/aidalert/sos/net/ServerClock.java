package com.aidalert.sos.net;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * The database rules only accept a record whose "ts" is within 5 minutes of the server clock. A phone with a wrong
 * date or time would be refused with "Permission denied" for every post, comment and blood request. The app therefore
 * learns the difference to the server clock (from the HTTP Date header) and sends corrected times. Pure Java.
 */
public final class ServerClock {
    private ServerClock() {}

    /** Differences smaller than this are left alone. */
    public static final long IGNORE_BELOW_MS = 30_000;

    /** serverMs - phoneMs: what must be added to the phone clock to get server time. */
    public static long offsetMs(long serverMs, long phoneMs) {
        return serverMs - phoneMs;
    }

    /** The JSON with its top-level numeric "ts" moved onto the server clock. Other text is returned unchanged. */
    public static String fixTs(String json, long offsetMs) {
        if (json == null || Math.abs(offsetMs) < IGNORE_BELOW_MS) return json;
        String t = json.trim();
        if (!t.startsWith("{") || !t.contains("\"ts\"")) return json;
        try {
            JSONObject o = new JSONObject(t);
            if (o.opt("ts") instanceof Number) {
                o.put("ts", o.getLong("ts") + offsetMs);
                return o.toString();
            }
        } catch (JSONException ignored) {
            // not an object we understand: send as it was
        }
        return json;
    }

    /** "2 h 5 min ahead" / "7 min behind" / "correct". */
    public static String describe(long offsetMs) {
        long a = Math.abs(offsetMs) / 1000;
        if (a < 60) return "correct (within a minute)";
        String span;
        if (a >= 86400) span = String.format(Locale.US, "%d days %d h", a / 86400, (a % 86400) / 3600);
        else if (a >= 3600) span = String.format(Locale.US, "%d h %d min", a / 3600, (a % 3600) / 60);
        else span = String.format(Locale.US, "%d min", a / 60);
        return span + (offsetMs > 0 ? " behind the server" : " ahead of the server");
    }

    /** True when the rules' 5-minute window would be missed without the correction. */
    public static boolean tooFarOff(long offsetMs) {
        return Math.abs(offsetMs) > 4 * 60_000L;
    }
}
