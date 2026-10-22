package com.aidalert.sos.util;

import java.util.Locale;

public final class Geo {
    private Geo() {}

    /** Great-circle distance in kilometres (haversine). */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Round to 3 decimals (about 110 m) so exact positions are not shared before consent. */
    public static double coarse(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public static String mapsLink(double lat, double lon) {
        return String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon);
    }

    public static String pretty(double lat, double lon) {
        return String.format(Locale.US, "%.5f, %.5f", lat, lon);
    }
}
