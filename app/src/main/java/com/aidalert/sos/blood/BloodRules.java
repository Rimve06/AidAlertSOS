package com.aidalert.sos.blood;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Blood group rules for the donor network. Pure Java. Group matching never replaces the hospital's crossmatch. */
public final class BloodRules {
    private BloodRules() {}

    public static final String[] GROUPS = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};

    /** Donors whose red cells are usually accepted by a patient of this group. */
    public static List<String> donorGroupsFor(String patient) {
        List<String> out = new ArrayList<>();
        if (patient == null) return out;
        boolean pos = patient.endsWith("+");
        String abo = patient.substring(0, patient.length() - 1);
        for (String d : GROUPS) {
            boolean dPos = d.endsWith("+");
            String dAbo = d.substring(0, d.length() - 1);
            if (dPos && !pos) continue; // Rh+ blood never goes to an Rh- patient
            boolean ok;
            switch (abo) {
                case "AB": ok = true; break;
                case "A": ok = dAbo.equals("A") || dAbo.equals("O"); break;
                case "B": ok = dAbo.equals("B") || dAbo.equals("O"); break;
                case "O": ok = dAbo.equals("O"); break;
                default: ok = false;
            }
            if (ok) out.add(d);
        }
        return out;
    }

    public static boolean isGroup(String g) {
        return g != null && Arrays.asList(GROUPS).contains(g);
    }

    public static boolean usuallyCompatible(String donor, String patient) {
        return donorGroupsFor(patient).contains(donor);
    }

    public static final long MIN_GAP_MS = 120L * 24 * 3600 * 1000;

    /** Soft hint only: the collecting centre decides eligibility. */
    public static boolean recentlyDonated(long lastDonationMs, long nowMs) {
        return lastDonationMs > 0 && nowMs - lastDonationMs < MIN_GAP_MS;
    }

    /** @return an error text, or null when the request is acceptable. */
    public static String checkRequest(String group, int units, String hospital, String district, String phoneE164) {
        if (!isGroup(group)) return "Choose the blood group.";
        if (units < 1 || units > 10) return "Units must be between 1 and 10.";
        if (hospital == null || hospital.trim().length() < 3) return "Enter the hospital name.";
        if (district == null || district.trim().length() < 3) return "Enter the district.";
        if (phoneE164 == null || phoneE164.isEmpty()) return "Enter a valid contact mobile number.";
        return null;
    }

    /**
     * Donors for a patient group. With includeCompatible the groups that are usually accepted are included too.
     * Best first: exact group, same district, available, not donated recently, newest profile.
     */
    public static List<Donor> search(List<Donor> all, String group, boolean includeCompatible, String district, long now) {
        List<String> groups = new ArrayList<>();
        if (isGroup(group)) {
            if (includeCompatible) groups.addAll(donorGroupsFor(group));
            else groups.add(group);
        }
        String dist = district == null ? "" : district.trim().toLowerCase(java.util.Locale.ROOT);
        List<Donor> out = new ArrayList<>();
        for (Donor d : all) {
            if (!groups.isEmpty() && !groups.contains(d.group)) continue;
            if (!dist.isEmpty() && !d.district.toLowerCase(java.util.Locale.ROOT).contains(dist)
                    && !d.area.toLowerCase(java.util.Locale.ROOT).contains(dist)) continue;
            out.add(d);
        }
        final String g = group == null ? "" : group;
        java.util.Collections.sort(out, (x, y) -> {
            int a = score(x, g, now), b = score(y, g, now);
            if (a != b) return Integer.compare(b, a);
            return Long.compare(y.ts, x.ts);
        });
        return out;
    }

    private static int score(Donor d, String group, long now) {
        int s = 0;
        if (d.group.equals(group)) s += 4;
        if (d.available) s += 8;
        if (!recentlyDonated(d.lastDonation, now)) s += 2;
        if (d.showPhone) s += 1;
        return s;
    }

    /** Message the person can send to a donor. Never includes a promise of payment. */
    public static String smsText(String patientGroup, String hospital, String district) {
        return "Assalamu alaikum. I found you on AidAlert SOS. We need " + (isGroup(patientGroup) ? patientGroup : "blood")
                + " blood" + (hospital == null || hospital.isEmpty() ? "" : " at " + hospital)
                + (district == null || district.isEmpty() ? "" : ", " + district)
                + ". Can you donate? Thank you.";
    }

    /** Donor eligibility for a registration: needs a group, a district and a valid Bangladesh mobile in E.164. */
    public static String checkDonor(String group, String district, String phoneE164) {
        if (!isGroup(group)) return "Choose your blood group.";
        if (district == null || district.trim().length() < 3) return "Choose your district.";
        if (phoneE164 == null || !phoneE164.matches("\\+8801[3-9]\\d{8}")) return "Your verified mobile number is needed first (Profile).";
        return null;
    }

    /** A donor the person copied from a partner site: needs a name, a group and a plausible phone. */
    public static String checkExternal(String name, String group, String phoneE164) {
        if (name == null || name.trim().length() < 2) return "Enter the donor's name.";
        if (!isGroup(group)) return "Choose the blood group.";
        if (phoneE164 == null || !phoneE164.matches("\\+8801[3-9]\\d{8}")) return "Enter a valid Bangladesh mobile number.";
        return null;
    }
}
