package com.aidalert.sos.alerts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bangladesh divisions and districts, and a text match used to filter alerts by area. Pure Java. */
public final class Regions {
    private Regions() {}

    public static final Map<String, String[]> DIVISIONS = new LinkedHashMap<>();

    static {
        DIVISIONS.put("Barishal", new String[]{"Barguna", "Barishal", "Bhola", "Jhalokati", "Patuakhali", "Pirojpur"});
        DIVISIONS.put("Chattogram", new String[]{"Bandarban", "Brahmanbaria", "Chandpur", "Chattogram", "Cumilla", "Cox's Bazar",
                "Feni", "Khagrachhari", "Lakshmipur", "Noakhali", "Rangamati"});
        DIVISIONS.put("Dhaka", new String[]{"Dhaka", "Faridpur", "Gazipur", "Gopalganj", "Kishoreganj", "Madaripur", "Manikganj",
                "Munshiganj", "Narayanganj", "Narsingdi", "Rajbari", "Shariatpur", "Tangail"});
        DIVISIONS.put("Khulna", new String[]{"Bagerhat", "Chuadanga", "Jashore", "Jhenaidah", "Khulna", "Kushtia", "Magura",
                "Meherpur", "Narail", "Satkhira"});
        DIVISIONS.put("Mymensingh", new String[]{"Jamalpur", "Mymensingh", "Netrokona", "Sherpur"});
        DIVISIONS.put("Rajshahi", new String[]{"Bogura", "Chapainawabganj", "Joypurhat", "Naogaon", "Natore", "Pabna", "Rajshahi",
                "Sirajganj"});
        DIVISIONS.put("Rangpur", new String[]{"Dinajpur", "Gaibandha", "Kurigram", "Lalmonirhat", "Nilphamari", "Panchagarh",
                "Rangpur", "Thakurgaon"});
        DIVISIONS.put("Sylhet", new String[]{"Habiganj", "Moulvibazar", "Sunamganj", "Sylhet"});
    }

    private static final String[][] ALIASES = {
            {"Chattogram", "chittagong"}, {"Cumilla", "comilla"}, {"Barishal", "barisal"}, {"Jashore", "jessore"},
            {"Bogura", "bogra"}, {"Cox's Bazar", "coxs bazar"}, {"Cox's Bazar", "cox s bazar"}, {"Cox's Bazar", "cox’s bazar"},
            {"Khagrachhari", "khagrachari"}, {"Jhalokati", "jhalakathi"}, {"Moulvibazar", "maulvibazar"},
            {"Brahmanbaria", "brahmanbaria"}, {"Narsingdi", "narshingdi"}, {"Chapainawabganj", "nawabganj"}
    };

    /** Names to look for: the place itself, its aliases, and for a division, all its districts. */
    static List<String> names(String place) {
        List<String> out = new ArrayList<>();
        if (place == null || place.isEmpty()) return out;
        String[] districts = DIVISIONS.get(place);
        if (districts != null) {
            out.add(place.toLowerCase(Locale.ROOT));
            for (String d : districts) addPlace(out, d);
        } else {
            addPlace(out, place);
        }
        return out;
    }

    private static void addPlace(List<String> out, String d) {
        out.add(d.toLowerCase(Locale.ROOT));
        for (String[] a : ALIASES) if (a[0].equals(d)) out.add(a[1]);
    }

    private static List<String> allNames() {
        List<String> all = new ArrayList<>();
        for (Map.Entry<String, String[]> e : DIVISIONS.entrySet()) {
            all.add(e.getKey().toLowerCase(Locale.ROOT));
            for (String d : e.getValue()) addPlace(all, d);
        }
        return all;
    }

    /**
     * True when the text is about the chosen place, or names no particular place at all (a national item).
     * A text that names only other districts is not relevant. An empty place means "everywhere".
     */
    public static boolean relevant(String text, String place) {
        if (place == null || place.isEmpty()) return true;
        String l = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String n : names(place)) if (l.contains(n)) return true;
        for (String n : allNames()) if (l.contains(n)) return false;
        return true;
    }

    /** True when the text names any Bangladesh division or district. */
    public static boolean mentionsPlace(String text) {
        if (text == null) return false;
        String l = text.toLowerCase(Locale.ROOT);
        for (String n : allNames()) if (l.contains(n)) return true;
        return false;
    }

    /** The first of the 64 districts named in the text (its standard spelling), or "" when none is named. */
    public static String districtIn(String text) {
        if (text == null) return "";
        String l = text.toLowerCase(Locale.ROOT);
        for (String[] ds : DIVISIONS.values()) {
            for (String d : ds) {
                for (String n : names(d)) if (l.contains(n)) return d;
            }
        }
        return "";
    }

    /** The 64 district names, A to Z. */
    public static List<String> districtsOnly() {
        List<String> ds = new ArrayList<>();
        for (String[] arr : DIVISIONS.values()) for (String d : arr) ds.add(d);
        java.util.Collections.sort(ds);
        return ds;
    }

    /** All Bangladesh, the 8 divisions, then every district: used by the area picker. */
    public static List<String> choicesWithDistricts() {
        List<String> c = choices();
        List<String> ds = new ArrayList<>();
        for (String[] arr : DIVISIONS.values()) for (String d : arr) ds.add(d);
        java.util.Collections.sort(ds);
        c.addAll(ds);
        return c;
    }

    public static List<String> choices() {
        List<String> c = new ArrayList<>();
        c.add("All Bangladesh");
        for (Map.Entry<String, String[]> e : DIVISIONS.entrySet()) c.add(e.getKey());
        return c;
    }
}
