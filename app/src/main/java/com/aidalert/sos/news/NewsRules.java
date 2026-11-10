package com.aidalert.sos.news;

import com.aidalert.sos.alerts.Regions;

import java.util.Locale;

/**
 * Decides which headlines count as emergencies and which are about Bangladesh. Pure Java.
 * News order in the app: 3 Bangladesh emergency and safety, 2 international emergency, 1 other Bangladesh, 0 other world.
 */
public final class NewsRules {
    private NewsRules() {}

    public static final int BD_EMERGENCY = 3, WORLD_EMERGENCY = 2, BD_OTHER = 1, WORLD_OTHER = 0;

    /** Words that make a headline an emergency (disaster, major accident, outbreak). Whole words only. */
    private static final String[] EMERGENCY = {
            "cyclone", "tornado", "nor'wester", "kalbaishakhi", "thunderstorm", "lightning", "storm surge", "tidal surge",
            "flood", "flash flood", "landslide", "earthquake", "tremor", "tsunami", "heatwave", "heat wave", "cold wave",
            "warning signal", "danger signal", "evacuat", "dengue", "cholera", "outbreak", "epidemic", "pandemic",
            "fire", "blaze", "explosion", "blast", "collapse", "gas leak", "capsize", "drown", "boat sinks", "plane crash",
            "train crash", "bus crash", "road crash", "road accident", "curfew", "wildfire", "volcano", "hurricane", "typhoon",
            "ঘূর্ণিঝড়", "বন্যা", "ভূমিকম্প", "আগুন", "অগ্নিকাণ্ড", "বজ্রপাত", "ভূমিধস", "ডেঙ্গু", "সতর্কসংকেত", "নৌকাডুবি", "দুর্ঘটনা"
    };
    /** Safety, crime and violence. */
    private static final String[] SAFETY = {
            "killed", "dead", "death", "died", "murder", "shot dead", "stabbed", "rape", "assault", "attack", "violence",
            "clash", "robbery", "mugging", "kidnap", "abducted", "missing", "arrested", "bomb", "terror", "mob", "riot",
            "harassment", "trafficking", "drug", "rescue", "injured",
            "নিহত", "খুন", "ধর্ষণ", "সহিংসতা", "সংঘর্ষ", "ডাকাতি", "অপহরণ", "নিখোঁজ", "গ্রেপ্তার"
    };
    /** Words that look like emergencies but are usually politics or sport ("political storm", "firing line"). */
    private static final String[] NOISE = {
            "political storm", "storm of", "fire back", "fires back", "firing line", "fire up", "fires up", "fired from",
            "fired as", "wildfire of rumours", "under fire", "on fire", "fire sale", "fire-brand", "firebrand", "crash course",
            "market crash", "stock crash", "blast from", "death of a", "to die for", "killed it", "kill the",
            "attack on form", "bowler", "batter", "wicket", "tournament", "box office"
    };

    private static boolean hasAny(String l, String[] words) {
        for (String w : words) if (l.contains(w)) return true;
        return false;
    }

    /** 2 = emergency or disaster, 1 = safety or crime, 0 = ordinary news. */
    public static int emergencyLevel(String title) {
        if (title == null) return 0;
        String l = title.toLowerCase(Locale.ROOT);
        if (hasAny(l, NOISE)) return 0;
        if (hasAny(l, EMERGENCY)) return 2;
        if (hasAny(l, SAFETY)) return 1;
        return 0;
    }

    private static final String[] BD_PUBLISHERS = {
            "daily star", "prothom alo", "bdnews24", "dhaka tribune", "new age", "dhaka post", "tbs news", "business standard",
            "the financial express", "financial express", "daily sun", "jugantor", "kaler kantho", "samakal", "ittefaq",
            "bangla tribune", "somoy", "jamuna", "channel 24", "ntv", "rtv", "independent tv", "bss", "the new nation",
            "country today", "daily observer", "bangladesh pratidin", "manab zamin", "janakantha", "inqilab", "noya diganta"
    };

    /** Bangladesh news: names Bangladesh or one of its places, is from a Bangladeshi publisher, or is in Bangla script. */
    public static boolean isBangladesh(String title, String publisher) {
        String t = title == null ? "" : title;
        String l = t.toLowerCase(Locale.ROOT);
        if (l.contains("bangladesh") || l.contains("bangladeshi") || l.contains("dhaka") || l.contains("bay of bengal")) return true;
        if (Regions.mentionsPlace(t)) return true;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 0x0980 && c <= 0x09FF) return true;
        }
        if (publisher != null) {
            String p = publisher.toLowerCase(Locale.ROOT);
            for (String b : BD_PUBLISHERS) if (p.contains(b)) return true;
        }
        return false;
    }

    public static int rank(String title, String publisher) {
        boolean bd = isBangladesh(title, publisher);
        int e = emergencyLevel(title);
        if (bd) return e > 0 ? BD_EMERGENCY : BD_OTHER;
        return e == 2 ? WORLD_EMERGENCY : WORLD_OTHER;
    }

    public static String label(int rank) {
        switch (rank) {
            case BD_EMERGENCY: return "BD ALERT";
            case WORLD_EMERGENCY: return "WORLD ALERT";
            case BD_OTHER: return "BANGLADESH";
            default: return "WORLD";
        }
    }

    // ------------------------------------------------------------------ alert feeds

    private static final String[] WEATHER = {"cyclone", "tornado", "nor'wester", "kalbaishakhi", "thunderstorm", "lightning",
            "storm surge", "tidal surge", "flood", "landslide", "heavy rain", "heavy rainfall", "heatwave", "heat wave", "cold wave",
            "warning signal", "danger signal", "signal no", "low pressure", "depression", "monsoon", "river level", "danger level",
            "ঘূর্ণিঝড়", "বন্যা", "ভারী বর্ষণ", "বজ্রপাত", "ভূমিধস", "সতর্কসংকেত"};
    private static final String[] QUAKE = {"earthquake", "tremor", "quake", "ভূমিকম্প"};
    private static final String[] HEALTH_LAW = {"dengue", "cholera", "outbreak", "epidemic", "pandemic", "health alert", "nipah",
            "curfew", "section 144", "ডেঙ্গু"};

    /**
     * Strict check for the Alerts screen: the headline itself must name the hazard, so "Political storm" or a
     * visa story that merely came up in a search never becomes an alert.
     */
    public static boolean isHazardAlert(String title, String kind) {
        if (title == null) return false;
        String l = title.toLowerCase(Locale.ROOT);
        if (hasAny(l, NOISE)) return false;
        if ("EARTHQUAKE".equals(kind)) return hasAny(l, QUAKE);
        if ("CYCLONE_FLOOD".equals(kind)) return hasAny(l, WEATHER);
        return hasAny(l, HEALTH_LAW) || hasAny(l, WEATHER) || hasAny(l, QUAKE);
    }

    private static final String[] LAW_ORDER = {"section 144", "144 imposed", "curfew", "hartal", "blockade", "movement restriction",
            "avoid unnecessary movement", "avoid movement", "police advisory", "advises residents", "advised residents",
            "road closed", "roads closed", "road blocked", "highway blocked", "traffic diversion", "diverted", "ban on gathering",
            "clash", "riot", "unrest", "mob ", "lockdown", "internet shutdown",
            "১৪৪ ধারা", "কারফিউ", "হরতাল", "সংঘর্ষ", "সড়ক অবরোধ"};

    /** A law-and-order headline: Section 144, curfew, hartal, police advisories, clashes, road closures. */
    public static boolean isLawOrder(String title) {
        if (title == null) return false;
        String l = title.toLowerCase(Locale.ROOT);
        if (hasAny(l, NOISE)) return false;
        // sport "clash" ("El Clasico clash") is not public safety
        if (l.contains("clash") && hasAny(l, new String[]{"match", "cup", "league", "vs ", "final", "series", "tournament"})) return false;
        return hasAny(l, LAW_ORDER);
    }

    /** Orders that restrict movement or gathering (red) rather than general unrest (orange). */
    public static boolean isOrderLevel(String title) {
        if (title == null) return false;
        String l = title.toLowerCase(Locale.ROOT);
        return hasAny(l, new String[]{"section 144", "curfew", "১৪৪ ধারা", "কারফিউ", "lockdown", "movement restriction"});
    }

    /** Alert severity for headlines from the alert feeds: 2 severe, 1 caution, 0 info. */
    public static int severity(String text) {
        if (text == null) return 0;
        String l = text.toLowerCase(Locale.ROOT);
        String[] severe = {"signal 8", "signal 9", "signal 10", "signal no. 8", "signal no. 9", "signal no. 10", "signal no 8",
                "signal no 9", "signal no 10", "tsunami", "deadly", "death toll", "killed", "devastat", "super cyclone",
                "evacuat", "great danger"};
        for (String k : severe) if (l.contains(k)) return 2;
        String[] caution = {"warning", "signal", "alert", "flood", "landslide", "cyclone", "earthquake", "outbreak",
                "curfew", "heavy rain", "depression", "heatwave", "heat wave", "lightning", "tidal", "surge"};
        for (String k : caution) if (l.contains(k)) return 1;
        return 0;
    }
}
