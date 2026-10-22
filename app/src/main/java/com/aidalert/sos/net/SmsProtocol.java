package com.aidalert.sos.net;

import java.util.Locale;

/**
 * Compact text format used when there is no internet but the SIM network works.
 * The control-center phone parses these SMS messages automatically.
 *
 *   AIDSOS|v1|incidentId|type|lat|lon|epochSeconds|userId|name|phone
 */
public final class SmsProtocol {
    private SmsProtocol() {}

    public static final String PREFIX = "AIDSOS|v1|";

    public static final class Parsed {
        public String incidentId = "";
        public String type = "";
        public String userId = "";
        public String name = "";
        public String phone = "";
        public double lat = Double.NaN;
        public double lon = Double.NaN;
        public long timeMs;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('|', ' ').replace('\n', ' ').trim();
    }

    public static String encode(String incidentId, String type, double lat, double lon, long timeMs,
                                String userId, String name, String phone) {
        String la = Double.isNaN(lat) ? "NA" : String.format(Locale.US, "%.5f", lat);
        String lo = Double.isNaN(lon) ? "NA" : String.format(Locale.US, "%.5f", lon);
        return PREFIX + clean(incidentId) + "|" + clean(type) + "|" + la + "|" + lo + "|"
                + (timeMs / 1000) + "|" + clean(userId) + "|" + clean(name) + "|" + clean(phone);
    }

    /** Returns null when the text is not an AidAlert SOS message. */
    public static Parsed decode(String body) {
        if (body == null || !body.startsWith(PREFIX)) return null;
        String[] p = body.substring(PREFIX.length()).split("\\|", -1);
        if (p.length < 8 || p[0].isEmpty()) return null;
        try {
            Parsed r = new Parsed();
            r.incidentId = p[0];
            r.type = p[1];
            r.lat = "NA".equals(p[2]) ? Double.NaN : Double.parseDouble(p[2]);
            r.lon = "NA".equals(p[3]) ? Double.NaN : Double.parseDouble(p[3]);
            r.timeMs = Long.parseLong(p[4]) * 1000L;
            r.userId = p[5];
            r.name = p[6];
            r.phone = p[7];
            return r;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
