package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.alerts.AlertItem;
import com.aidalert.sos.alerts.AlertRules;
import com.aidalert.sos.alerts.QuakeParser;
import com.aidalert.sos.stats.CrimeStats;

import org.junit.Test;

import java.util.List;

/** Public alert filtering and the crime-statistics model. */
public class AlertsAndStatsTest {

    @Test
    public void regionBoxKeepsBangladeshAndNeighbours() {
        assertTrue(AlertRules.inRegion(23.8, 90.4));   // Dhaka
        assertTrue(AlertRules.inRegion(22.0, 92.5));   // Myanmar border
        assertFalse(AlertRules.inRegion(35.7, 139.7)); // Tokyo
    }

    @Test
    public void textMatchFindsBangladeshPlaces() {
        assertTrue(AlertRules.mentionsBangladesh("Tropical cyclone near Cox's Bazar"));
        assertTrue(AlertRules.mentionsBangladesh(null, "Flood in Sylhet"));
        assertFalse(AlertRules.mentionsBangladesh("Typhoon near Japan"));
    }

    @Test
    public void severityAndKind() {
        assertEquals(2, AlertRules.quakeSeverity(6.2));
        assertEquals(1, AlertRules.quakeSeverity(4.6));
        assertEquals(0, AlertRules.quakeSeverity(3.0));
        assertEquals(2, AlertRules.levelSeverity("Red alert"));
        assertEquals(AlertItem.WEATHER, AlertRules.kindFromText("Cyclone warning"));
        assertEquals(AlertItem.QUAKE, AlertRules.kindFromText("Earthquake M5"));
        assertEquals(AlertItem.OTHER, AlertRules.kindFromText("Notice"));
    }

    @Test
    public void datesParse() {
        assertTrue(AlertRules.parseDate("Wed, 28 Oct 2026 10:00:00 GMT") > 0);
        assertTrue(AlertRules.parseDate("2026-10-28T10:00:00Z") > 0);
        assertEquals(0, AlertRules.parseDate("not a date"));
        assertEquals(0, AlertRules.parseDate(null));
    }

    @Test
    public void quakeFeedKeepsOnlyRegionalQuakesAboveMinimum() throws Exception {
        String json = "{\"features\":["
                + "{\"properties\":{\"mag\":5.1,\"place\":\"Sylhet\",\"time\":1000,\"url\":\"https://x\"},"
                + "\"geometry\":{\"coordinates\":[91.8,24.9,10]}},"
                + "{\"properties\":{\"mag\":2.0,\"place\":\"small\",\"time\":1},"
                + "\"geometry\":{\"coordinates\":[91.8,24.9,10]}},"
                + "{\"properties\":{\"mag\":6.5,\"place\":\"Tokyo\",\"time\":1},"
                + "\"geometry\":{\"coordinates\":[139.7,35.7,10]}}]}";
        List<AlertItem> l = QuakeParser.parse(json, 3.5);
        assertEquals(1, l.size());
        assertEquals(1, l.get(0).severity);
        assertEquals(1000, l.get(0).timeMs);
    }

    @Test
    public void crimeBarsSortedAndFilteredByGroup() throws Exception {
        String json = "{\"crimeTypes\":[\"Murder\",\"Theft\"],\"regions\":["
                + "{\"name\":\"Dhaka\",\"group\":\"Dhaka\",\"counts\":[5,40]},"
                + "{\"name\":\"Gazipur\",\"group\":\"Dhaka\",\"counts\":[9,10]},"
                + "{\"name\":\"Sylhet\",\"group\":\"Sylhet\",\"counts\":[1,3]}]}";
        CrimeStats s = CrimeStats.parse(json);
        assertFalse(s.isEmpty());
        assertEquals(2, s.groups().size());
        List<CrimeStats.Bar> all = s.bars(1, "");
        assertEquals("Dhaka", all.get(0).label);
        assertEquals(2, s.bars(0, "Dhaka").size());
        assertEquals("Gazipur", s.bars(0, "Dhaka").get(0).label);
        assertTrue(s.bars(5, "").isEmpty());
        assertTrue(CrimeStats.parse("{}").isEmpty());
    }

    @Test
    public void overviewTotalsAndHistory() throws Exception {
        String json = "{\"period\":\"2026-10\",\"source\":\"x\",\"crimeTypes\":[\"Theft\",\"Robbery\",\"Murder\"],"
                + "\"regions\":[{\"name\":\"Dhaka\",\"group\":\"Metro\",\"counts\":[10,4,2]},"
                + "{\"name\":\"Khulna\",\"group\":\"Metro\",\"counts\":[6,1,1]}]}";
        com.aidalert.sos.stats.CrimeStats s = com.aidalert.sos.stats.CrimeStats.parse(json);
        org.junit.Assert.assertEquals(24.0, s.total(), 0.001);
        org.junit.Assert.assertEquals("Dhaka", s.topRegion().label);
        java.util.List<com.aidalert.sos.stats.CrimeStats.Bar> top = com.aidalert.sos.stats.CrimeStats.topWithOthers(s.totalsByType(), 2);
        org.junit.Assert.assertEquals(2, top.size());
        org.junit.Assert.assertEquals("Theft", top.get(0).label);
        org.junit.Assert.assertEquals("Others", top.get(1).label);
        org.junit.Assert.assertEquals(8.0, top.get(1).value, 0.001);
        String h = com.aidalert.sos.stats.CrimeStats.addToHistory("", s);
        h = com.aidalert.sos.stats.CrimeStats.addToHistory(h, s);
        org.junit.Assert.assertEquals(1, com.aidalert.sos.stats.CrimeStats.history(h).size());
    }
}
