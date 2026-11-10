package com.aidalert.sos.alerts;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Pure Java helpers: is an alert about Bangladesh, how severe is it, what time is it. */
public final class AlertRules {
    private AlertRules() {}

    // Bangladesh plus a margin: quakes in Myanmar / north-east India are felt in Bangladesh.
    public static final double MIN_LAT = 19.0, MAX_LAT = 28.0, MIN_LON = 86.0, MAX_LON = 94.5;

    public static boolean inRegion(double lat, double lon) {
        return lat >= MIN_LAT && lat <= MAX_LAT && lon >= MIN_LON && lon <= MAX_LON;
    }

    /** Text match for global feeds (GDACS etc.): keep only items that mention Bangladesh or the Bay of Bengal. */
    public static boolean mentionsBangladesh(String... texts) {
        for (String t : texts) {
            if (t == null) continue;
            String l = t.toLowerCase(Locale.ROOT);
            if (l.contains("bangladesh") || l.contains("bay of bengal") || l.contains("dhaka") || l.contains("chittagong")
                    || l.contains("chattogram") || l.contains("sylhet") || l.contains("khulna") || l.contains("barisal")
                    || l.contains("barishal") || l.contains("rajshahi") || l.contains("rangpur") || l.contains("mymensingh")
                    || l.contains("cox's bazar") || l.contains("cox s bazar")) return true;
        }
        return false;
    }

    public static int quakeSeverity(double magnitude) {
        if (magnitude >= 6.0) return 2;
        if (magnitude >= 4.5) return 1;
        return 0;
    }

    /** GDACS alert level words: Red = severe, Orange = caution, Green = info. */
    public static int levelSeverity(String text) {
        String l = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (l.contains("red")) return 2;
        if (l.contains("orange")) return 1;
        return 0;
    }

    private static final String[] HEALTH_WORDS = {"dengue", "malaria", "cholera", "outbreak", "epidemic", "pandemic", "covid",
            "nipah", "chikungunya", "diarrhoea", "diarrhea", "typhoid", "measles", "bird flu", "avian influenza", "health alert",
            "health advisory", "virus", "mosquito", "hepatitis", "rabies", "anthrax", "ডেঙ্গু", "ম্যালেরিয়া", "কলেরা"};

    /** True when the text is about a disease or public-health warning. */
    public static boolean isHealth(String text) {
        String l = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String w : HEALTH_WORDS) if (l.contains(w)) return true;
        return false;
    }

    public static String kindFromText(String text) {
        String l = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (l.contains("earthquake") || l.contains("quake")) return AlertItem.QUAKE;
        if (isHealth(l)) return AlertItem.HEALTH;
        if (l.contains("cyclone") || l.contains("storm") || l.contains("flood") || l.contains("rain")
                || l.contains("tropical") || l.contains("depression") || l.contains("landslide")) return AlertItem.WEATHER;
        return AlertItem.OTHER;
    }

    /** Parses RFC 822 (RSS), ISO 8601 (Atom / CAP) dates; returns 0 if unknown. */
    public static long parseDate(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        String t = s.trim();
        String[] patterns = {"EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss zzz",
                "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd"};
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = f.parse(t);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {
                // try the next pattern
            }
        }
        return 0;
    }

    // ------------------------------------------------------------ the alert chips
    public static final String F_ALL = "";
    public static final String F_DISASTER = "DISASTER_WEATHER";   // floods, cyclones, storms, fires, landslides
    public static final String F_QUAKE = "QUAKE";
    public static final String F_HEALTH = "HEALTH";               // dengue, malaria, outbreaks
    public static final String F_LAW = AlertItem.CAT_LAW;
    public static final String F_NEWS = AlertItem.CAT_NEWS;
    public static final String F_WORLD = AlertItem.SCOPE_WORLD;

    /** Does an alert belong under the chosen chip? "All" always matches, whatever the type. */
    public static boolean matchesFilter(String filter, String kind, String category, String scope) {
        if (filter == null || filter.isEmpty()) return true;
        boolean world = AlertItem.SCOPE_WORLD.equals(scope);
        if (filter.equals(F_WORLD)) return world;
        if (filter.equals(F_QUAKE)) return AlertItem.QUAKE.equals(kind);
        if (world) return false;
        if (filter.equals(F_HEALTH)) return AlertItem.HEALTH.equals(kind);
        if (filter.equals(F_DISASTER)) {
            return AlertItem.CAT_EMERGENCY.equals(category) && !AlertItem.QUAKE.equals(kind) && !AlertItem.HEALTH.equals(kind);
        }
        return filter.equals(category);
    }

    /** Short label shown on each alert row. */
    public static String tagFor(String kind, String category, String scope) {
        if (AlertItem.SCOPE_WORLD.equals(scope)) return "World";
        if (AlertItem.CAT_LAW.equals(category)) return "Law & order";
        if (AlertItem.CAT_NEWS.equals(category)) return "News";
        if (AlertItem.QUAKE.equals(kind)) return "Earthquake";
        if (AlertItem.HEALTH.equals(kind)) return "Health";
        return "Disaster & weather";
    }

    /** Bangladesh alerts first, international ones after; newest first inside each group. */
    public static int order(AlertItem x, AlertItem y) {
        boolean wx = AlertItem.SCOPE_WORLD.equals(x.scope), wy = AlertItem.SCOPE_WORLD.equals(y.scope);
        if (wx != wy) return wx ? 1 : -1;
        return Long.compare(y.timeMs, x.timeMs);
    }
}
