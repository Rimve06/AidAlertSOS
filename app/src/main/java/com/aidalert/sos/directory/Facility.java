package com.aidalert.sos.directory;

/** One hospital / fire station / police station in the offline directory. */
public class Facility {
    public static final String HOSPITAL = "HOSPITAL";
    public static final String FIRE = "FIRE";
    public static final String POLICE = "POLICE";
    public static final String BLOOD = "BLOOD";
    /** Safe haven: cyclone / flood shelter or emergency assembly point. */
    public static final String SHELTER = "SHELTER";
    // map-only categories (shown on the Map screen, not in the emergency directory lists)
    public static final String PHARMACY = "PHARMACY";
    public static final String ATM = "ATM";
    public static final String FOOD = "FOOD";
    public static final String FUEL = "FUEL";
    public static final String HOTEL = "HOTEL";
    public static final String SHOP = "SHOP";

    /** The five kinds listed in the Emergency directory. */
    public static final String[] CORE = {HOSPITAL, POLICE, FIRE, BLOOD, SHELTER};

    public static String label(String kind) {
        if (HOSPITAL.equals(kind)) return "Hospital";
        if (POLICE.equals(kind)) return "Police";
        if (FIRE.equals(kind)) return "Fire station";
        if (BLOOD.equals(kind)) return "Blood bank";
        if (SHELTER.equals(kind)) return "Safe haven";
        if (PHARMACY.equals(kind)) return "Pharmacy";
        if (ATM.equals(kind)) return "ATM";
        if (FOOD.equals(kind)) return "Food";
        if (FUEL.equals(kind)) return "Fuel";
        if (HOTEL.equals(kind)) return "Hotel";
        if (SHOP.equals(kind)) return "Shop";
        return kind;
    }

    /** One-letter pin text for the map. */
    public static String pin(String kind) {
        if (HOSPITAL.equals(kind)) return "H";
        if (POLICE.equals(kind)) return "P";
        if (FIRE.equals(kind)) return "F";
        if (BLOOD.equals(kind)) return "B";
        if (SHELTER.equals(kind)) return "S";
        if (PHARMACY.equals(kind)) return "+";
        if (ATM.equals(kind)) return "$";
        if (FOOD.equals(kind)) return "R";
        if (FUEL.equals(kind)) return "G";
        if (HOTEL.equals(kind)) return "T";
        return "M";
    }

    public static int color(String kind) {
        if (HOSPITAL.equals(kind)) return 0xFFD32F2F;
        if (POLICE.equals(kind)) return 0xFF0B2A5B;
        if (FIRE.equals(kind)) return 0xFFEF6C00;
        if (BLOOD.equals(kind)) return 0xFF8E0000;
        if (SHELTER.equals(kind)) return 0xFF2E7D32;
        if (PHARMACY.equals(kind)) return 0xFF00897B;
        return 0xFF5C6BC0;
    }

    public String id = "";
    public String kind = HOSPITAL;
    public String name = "";
    /** Empty when no verified phone number is known. Never filled in by guessing. */
    public String phone = "";
    public boolean phoneVerified;
    public double lat = Double.NaN;
    public double lon = Double.NaN;
    public String district = "";
    public String source = "";
    public String sourceUrl = "";
    public long updated;

    /** Filled by the ranker, not stored. */
    public double distanceKm = Double.NaN;

    public boolean hasLocation() {
        return !Double.isNaN(lat) && !Double.isNaN(lon)
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    public boolean hasPhone() {
        return phone != null && !phone.trim().isEmpty();
    }
}
