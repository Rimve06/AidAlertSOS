package com.aidalert.sos.report;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/** Pure Java rules for incident reports: tracking IDs, checks on what was typed, reading a typed position. */
public final class ReportRules {
    private ReportRules() {}

    public static final String[] TYPES = {"Theft / robbery", "Road accident", "Fire", "Violence / harassment", "Medical emergency",
            "Flood / storm / landslide", "Missing person", "Suspicious activity", "Other"};

    public static final int MIN_DESC = 10, MAX_DESC = 1000;
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";   // no 0/O, 1/I/L: easy to read out on the phone

    /** "AA-261109-K7Q4": AA, the date (yyMMdd, Dhaka time) and four letters/digits. */
    public static String trackingId(long nowMs, Random rnd) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"));
        c.setTimeInMillis(nowMs);
        StringBuilder sb = new StringBuilder("AA-");
        sb.append(String.format(Locale.US, "%02d%02d%02d", c.get(Calendar.YEAR) % 100, c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)));
        sb.append('-');
        for (int i = 0; i < 4; i++) sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    public static boolean isTrackingId(String s) {
        return s != null && s.matches("AA-\\d{6}-[A-HJKMNP-Z2-9]{4}");
    }

    /** null when the report can be sent, otherwise what to fix. */
    public static String check(int typeIndex, String description) {
        if (typeIndex < 0 || typeIndex >= TYPES.length) return "Choose what kind of incident it is.";
        String d = description == null ? "" : description.trim();
        if (d.length() < MIN_DESC) return "Describe what happened in a few words (at least " + MIN_DESC + " characters).";
        if (d.length() > MAX_DESC) return "The description is too long (most " + MAX_DESC + " characters).";
        return null;
    }

    /** "22.8456, 89.5403" -> {22.8456, 89.5403}; null when it is not a valid position. */
    public static double[] parseLatLon(String text) {
        if (text == null) return null;
        String[] p = text.trim().split("\\s*[,;]\\s*|\\s+");
        if (p.length != 2) return null;
        try {
            double lat = Double.parseDouble(p[0]), lon = Double.parseDouble(p[1]);
            if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
            return new double[]{lat, lon};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatLatLon(double lat, double lon) {
        return String.format(Locale.US, "%.5f, %.5f", lat, lon);
    }

    /** Words for a status code written by the control center. */
    public static String statusLabel(String status) {
        if (status == null || status.isEmpty()) return "Submitted";
        switch (status) {
            case "RECEIVED": return "Received";
            case "UNDER_REVIEW": return "Under review";
            case "ACTION_TAKEN": return "Action taken";
            case "RESOLVED": return "Resolved";
            case "REJECTED": return "Not accepted";
            default: return status.substring(0, 1) + status.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }
}
