package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.directory.Facility;
import com.aidalert.sos.directory.NearbyRanker;
import com.aidalert.sos.directory.PhoneRules;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Offline-directory rules that run on the PC: nearest-first ranking and phone validation. */
public class DirectoryLogicTest {

    private static Facility f(String id, String kind, double lat, double lon, String phone, boolean verified) {
        Facility x = new Facility();
        x.id = id;
        x.kind = kind;
        x.name = id;
        x.lat = lat;
        x.lon = lon;
        x.phone = phone;
        x.phoneVerified = verified;
        return x;
    }

    @Test
    public void nearestFirstAndFiltersByKind() {
        List<Facility> all = new ArrayList<>();
        all.add(f("far", Facility.HOSPITAL, 24.37, 88.60, "", false));      // Rajshahi
        all.add(f("near", Facility.HOSPITAL, 23.78, 90.40, "", false));     // Dhaka
        all.add(f("fire", Facility.FIRE, 23.78, 90.41, "", false));
        List<Facility> out = NearbyRanker.nearest(all, 23.7808, 90.4000, Facility.HOSPITAL, 10);
        assertEquals(2, out.size());
        assertEquals("near", out.get(0).id);
        assertEquals("far", out.get(1).id);
        assertTrue(out.get(0).distanceKm < out.get(1).distanceKm);
    }

    @Test
    public void limitIsApplied() {
        List<Facility> all = new ArrayList<>();
        for (int i = 0; i < 5; i++) all.add(f("h" + i, Facility.HOSPITAL, 23.0 + i * 0.1, 90.0, "", false));
        assertEquals(3, NearbyRanker.nearest(all, 23.0, 90.0, null, 3).size());
    }

    @Test
    public void facilitiesWithoutValidLocationAreSkipped() {
        List<Facility> all = new ArrayList<>();
        all.add(f("nan", Facility.HOSPITAL, Double.NaN, 90.0, "", false));
        all.add(f("bad", Facility.HOSPITAL, 123.0, 90.0, "", false));
        all.add(f("ok", Facility.HOSPITAL, 23.7, 90.4, "", false));
        List<Facility> out = NearbyRanker.nearest(all, 23.7, 90.4, null, 10);
        assertEquals(1, out.size());
        assertEquals("ok", out.get(0).id);
    }

    @Test
    public void missingAndUnverifiedPhonesAreLabelledNotInvented() {
        assertEquals("No phone number on record", NearbyRanker.phoneLabel(f("a", Facility.FIRE, 1, 1, "", false)));
        assertTrue(NearbyRanker.phoneLabel(f("b", Facility.FIRE, 1, 1, "02-1234567", false)).contains("unverified"));
        assertEquals("02-1234567", NearbyRanker.phoneLabel(f("c", Facility.FIRE, 1, 1, "02-1234567", true)));
    }

    @Test
    public void phoneValidation() {
        assertEquals("999", PhoneRules.clean(" 999 "));
        assertEquals("+880 2-5566778", PhoneRules.clean("+880 2-5566778"));
        assertEquals("", PhoneRules.clean("call us"));
        assertEquals("", PhoneRules.clean("12"));
        assertEquals("", PhoneRules.clean("01711-<script>"));
        assertEquals("", PhoneRules.clean(null));
        assertFalse(PhoneRules.clean("1234567890123456789").length() > 0);
    }
}
