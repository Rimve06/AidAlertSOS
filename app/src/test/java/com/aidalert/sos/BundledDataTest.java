package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.blood.BloodRules;
import com.aidalert.sos.blood.BloodSites;
import com.aidalert.sos.stats.CrimeStats;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BundledDataTest {
    private static String asset(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/assets/" + name)), StandardCharsets.UTF_8);
    }

    @Test
    public void bundledCrimeStatisticsMatchThePoliceTotal() throws Exception {
        CrimeStats s = CrimeStats.parse(asset("crime_stats.json"));
        assertFalse(s.isEmpty());
        assertEquals("2026-09", s.period);
        assertEquals(17, s.regions.size());
        // The sheet's own "Total Cases" figure
        assertEquals(18650.0, s.total(), 0.001);
        assertEquals(3337.0, s.topRegion().value, 0.001); // Dhaka Range is the largest unit
        assertEquals("Dhaka Range", s.topRegion().label);
    }

    @Test
    public void partnerSitesAreTheDirectUrls() {
        assertEquals(5, BloodSites.ALL.length);
        assertEquals("https://www.roktodata.org/", BloodSites.ALL[0].url);
        assertEquals("https://iom.edu.bd/ibd/", BloodSites.ALL[3].url);
        String t = BloodSites.registrationText("Tas", "B+", "+8801943114467", "Khulna", "", -1);
        assertTrue(t.contains("Blood group: B+"));
        assertTrue(t.contains("Last donation: never"));
        assertTrue(BloodSites.registrationText("Tas", "B+", "+880", "Khulna", "KUET", 200).contains("200 days ago"));
    }

    @Test
    public void savedDonorFromAnotherSiteNeedsNameGroupPhone() {
        assertNull(BloodRules.checkExternal("Rahim", "A+", "+8801712345678"));
        assertNotNull(BloodRules.checkExternal("", "A+", "+8801712345678"));
        assertNotNull(BloodRules.checkExternal("Rahim", "", "+8801712345678"));
        assertNotNull(BloodRules.checkExternal("Rahim", "A+", "12345"));
    }

    @Test
    public void roktoDataOpensTheDistrictPage() {
        assertEquals("https://www.roktodata.org/donors/khulna", BloodSites.searchUrl(BloodSites.ALL[0], "Khulna"));
        assertEquals("https://www.roktodata.org/", BloodSites.searchUrl(BloodSites.ALL[0], ""));
        assertEquals("https://bdfb.info/", BloodSites.searchUrl(BloodSites.ALL[2], "Khulna"));
        assertEquals("coxs-bazar", BloodSites.slug("Cox's Bazar"));
    }
}
