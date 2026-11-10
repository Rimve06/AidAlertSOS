package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.alerts.Regions;
import com.aidalert.sos.blood.BloodRules;

import org.junit.Test;

public class BloodAndRegionTest {
    @Test
    public void compatibility() {
        assertTrue(BloodRules.usuallyCompatible("O-", "AB+"));
        assertTrue(BloodRules.usuallyCompatible("O+", "A+"));
        assertFalse(BloodRules.usuallyCompatible("A+", "O+"));
        assertFalse(BloodRules.usuallyCompatible("O+", "O-"));
        assertEquals(8, BloodRules.donorGroupsFor("AB+").size());
        assertEquals(1, BloodRules.donorGroupsFor("O-").size());
    }

    @Test
    public void requestChecks() {
        assertNull(BloodRules.checkRequest("B+", 2, "Khulna Medical College", "Khulna", "+8801712345678"));
        assertNotNull(BloodRules.checkRequest("X", 2, "Khulna Medical College", "Khulna", "+8801712345678"));
        assertNotNull(BloodRules.checkRequest("B+", 0, "Khulna Medical College", "Khulna", "+8801712345678"));
        assertNotNull(BloodRules.checkRequest("B+", 2, "KMC", "Khulna", ""));
    }

    @Test
    public void recentDonationHint() {
        long now = 1_000_000_000_000L;
        assertTrue(BloodRules.recentlyDonated(now - 30L * 24 * 3600 * 1000, now));
        assertFalse(BloodRules.recentlyDonated(now - 200L * 24 * 3600 * 1000, now));
        assertFalse(BloodRules.recentlyDonated(0, now));
    }

    @Test
    public void regionFilter() {
        assertEquals(64, Regions.DIVISIONS.values().stream().mapToInt(a -> a.length).sum());
        assertTrue(Regions.relevant("Flood warning for Sylhet", "Sylhet"));
        assertFalse(Regions.relevant("Flood warning for Sylhet", "Khulna"));
        assertTrue(Regions.relevant("Cyclone expected in Bay of Bengal", "Khulna")); // no district named: national
        assertTrue(Regions.relevant("Heavy rain in Chittagong", "Chattogram"));
        assertTrue(Regions.relevant("Landslide in Bandarban", "Chattogram"));
        assertTrue(Regions.relevant("anything", ""));
    }

    private static com.aidalert.sos.blood.Donor donor(String uid, String group, String district, boolean avail, long ts) {
        com.aidalert.sos.blood.Donor d = new com.aidalert.sos.blood.Donor();
        d.uid = uid;
        d.name = uid;
        d.group = group;
        d.district = district;
        d.available = avail;
        d.ts = ts;
        return d;
    }

    @Test
    public void donorSearchFiltersAndRanks() {
        java.util.List<com.aidalert.sos.blood.Donor> all = new java.util.ArrayList<>();
        all.add(donor("a", "O+", "Khulna", true, 1));
        all.add(donor("b", "B+", "Khulna", true, 2));
        all.add(donor("c", "B+", "Dhaka", true, 3));
        all.add(donor("d", "B+", "Khulna", false, 4));
        all.add(donor("e", "O-", "Khulna", true, 5));
        long now = 1_000_000_000_000L;
        java.util.List<com.aidalert.sos.blood.Donor> r =
                BloodRules.search(all, "B+", false, "khulna", now);
        assertEquals(2, r.size());
        assertEquals("b", r.get(0).uid);                       // available before not available
        java.util.List<com.aidalert.sos.blood.Donor> compat = BloodRules.search(all, "B+", true, "Khulna", now);
        assertEquals(4, compat.size());                        // B+, O+, O-, plus the unavailable B+
        assertEquals("b", compat.get(0).uid);                  // exact group first
        assertEquals(5, BloodRules.search(all, "", true, "", now).size());
    }

    @Test
    public void donorRegistrationNeedsVerifiedBangladeshMobile() {
        assertEquals(null, BloodRules.checkDonor("A+", "Khulna", "+8801712345678"));
        org.junit.Assert.assertNotNull(BloodRules.checkDonor("A+", "Khulna", "01712345678"));
        org.junit.Assert.assertNotNull(BloodRules.checkDonor("Z+", "Khulna", "+8801712345678"));
        org.junit.Assert.assertNotNull(BloodRules.checkDonor("A+", "", "+8801712345678"));
    }
}
