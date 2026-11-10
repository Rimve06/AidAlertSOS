package com.aidalert.sos.directory;

import com.aidalert.sos.util.Geo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure logic (no Android classes): picks the closest facilities from the local cache. */
public final class NearbyRanker {
    private NearbyRanker() {}

    /**
     * @param kind  HOSPITAL / FIRE / POLICE, or null / empty for all kinds
     * @param limit maximum number of results
     * @return closest-first copies; facilities with no valid coordinates are skipped
     */
    public static List<Facility> nearest(List<Facility> all, double lat, double lon, String kind, int limit) {
        List<Facility> out = new ArrayList<>();
        for (Facility f : all) {
            if (!f.hasLocation()) continue;
            if (kind != null && !kind.isEmpty() && !kind.equals(f.kind)) continue;
            f.distanceKm = Geo.distanceKm(lat, lon, f.lat, f.lon);
            out.add(f);
        }
        Collections.sort(out, new Comparator<Facility>() {
            @Override
            public int compare(Facility a, Facility b) {
                return Double.compare(a.distanceKm, b.distanceKm);
            }
        });
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** Radius of the Emergency directory list: everything within 50 km of the phone. */
    public static final double DIRECTORY_RADIUS_KM = 50.0;

    /** Like {@link #nearest} but only places inside maxKm. */
    public static List<Facility> within(List<Facility> all, double lat, double lon, String kind, double maxKm, int limit) {
        List<Facility> out = new ArrayList<>();
        for (Facility f : nearest(all, lat, lon, kind, Integer.MAX_VALUE)) {
            if (f.distanceKm > maxKm) break;
            out.add(f);
            if (out.size() >= limit) break;
        }
        return out;
    }

    /** Text for the "phone" line, so missing or unverified data is always shown clearly. */
    public static String phoneLabel(Facility f) {
        if (!f.hasPhone()) return "No phone number on record";
        return f.phoneVerified ? f.phone : f.phone + " (unverified, confirm before relying on it)";
    }
}
