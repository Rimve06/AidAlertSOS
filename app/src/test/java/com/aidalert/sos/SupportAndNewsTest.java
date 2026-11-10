package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.net.Html;
import com.aidalert.sos.news.FeedConfig;
import com.aidalert.sos.news.NewsRules;
import com.aidalert.sos.support.RiskRules;

import org.junit.Test;

public class SupportAndNewsTest {

    @Test
    public void highRiskPhrasesAreFlagged() {
        assertEquals(RiskRules.HIGH, RiskRules.level("I am going to kill myself tonight"));
        assertEquals(RiskRules.HIGH, RiskRules.level("sesh kore dibo sob"));
        assertEquals(RiskRules.HIGH, RiskRules.level("আমি আত্মহত্যা করব"));
    }

    @Test
    public void concernPhrasesAreFlaggedLower() {
        assertEquals(RiskRules.CONCERN, RiskRules.level("I feel hopeless and I want to die"));
        assertEquals(RiskRules.CONCERN, RiskRules.level("ami morte chai"));
    }

    @Test
    public void negationAndOrdinaryTextAreNotFlagged() {
        assertEquals(RiskRules.NONE, RiskRules.level("I do not want to die, I just failed an exam"));
        assertEquals(RiskRules.NONE, RiskRules.level("I am hungry and going to the market"));
        assertEquals(RiskRules.NONE, RiskRules.level(null));
    }

    @Test
    public void repeatedConcernEscalates() {
        RiskRules.Tracker t = new RiskRules.Tracker();
        assertEquals(RiskRules.CONCERN, t.add("I feel hopeless"));
        assertEquals(RiskRules.CONCERN, t.add("nothing matters"));
        assertEquals(RiskRules.HIGH, t.add("I am worthless"));
        t = new RiskRules.Tracker();
        assertEquals(RiskRules.HIGH, t.add("I will end my life"));
    }

    @Test
    public void emergencyNewsSortsFirst() {
        assertEquals(3, NewsRules.rank("Cyclone warning signal 5 raised for Chattogram", "Prothom Alo"));
        assertEquals(3, NewsRules.rank("Man killed in road crash in Dhaka", "The Daily Star"));
        assertEquals(0, NewsRules.rank("Cricket team announced for series", "ESPN"));
        assertEquals(2, NewsRules.severity("Signal 8 hoisted, evacuation begins"));
        assertEquals(1, NewsRules.severity("Heavy rain warning for Sylhet"));
    }

    @Test
    public void htmlAndPublisher() {
        assertEquals("Flood hits north\nRead more", Html.strip("<p>Flood hits north</p><a href='x'>Read more</a>").replace("\n \n", "\n").replaceAll("\\n\\s+", "\n").replaceAll(" +\\n", "\n"));
        assertEquals("The Daily Star", Html.publisher("Cyclone nears coast - The Daily Star"));
        assertEquals("Cyclone nears coast", Html.withoutPublisher("Cyclone nears coast - The Daily Star"));
        assertEquals("", Html.publisher("No publisher"));
    }

    @Test
    public void feedConfigKeepsOnlyHttpUrls() throws Exception {
        FeedConfig c = FeedConfig.parse("{\"alerts\":[{\"name\":\"a\",\"url\":\"https://x/rss\"},{\"name\":\"bad\",\"url\":\"file:///etc\"}],"
                + "\"news\":[{\"name\":\"n\",\"url\":\"http://y/rss\"}],\"pages\":[]}");
        assertEquals(1, c.alerts.size());
        assertEquals(1, c.news.size());
        assertTrue(c.pages.isEmpty());
    }

    @Test
    public void junkHeadlinesAreNotHazardAlerts() {
        assertEquals(0, NewsRules.emergencyLevel("Political storm brews over budget"));
        assertEquals(3, NewsRules.rank("Bus crash in Gazipur kills 5", "Dhaka Post"));
        assertEquals(2, NewsRules.rank("Earthquake strikes Japan coast", "Reuters"));
    }

    @Test
    public void lawOrderRulesAreStrict() {
        org.junit.Assert.assertTrue(NewsRules.isLawOrder("Section 144 imposed in Dighalia, Khulna"));
        org.junit.Assert.assertTrue(NewsRules.isLawOrder("Police advise residents to avoid unnecessary movement at night"));
        org.junit.Assert.assertFalse(NewsRules.isLawOrder("Bangladesh clash with India in Asia Cup match"));
        org.junit.Assert.assertFalse(NewsRules.isLawOrder("Political storm over budget"));
        org.junit.Assert.assertTrue(NewsRules.isOrderLevel("Curfew declared in Rangpur"));
        org.junit.Assert.assertFalse(NewsRules.isOrderLevel("Clash in Sylhet leaves 5 injured"));
    }

    @Test
    public void districtIsFoundInText() {
        assertEquals("Khulna", com.aidalert.sos.alerts.Regions.districtIn("Gun violence rises in Khulna"));
        assertEquals("Chattogram", com.aidalert.sos.alerts.Regions.districtIn("Rain in Chittagong port"));
        assertEquals("", com.aidalert.sos.alerts.Regions.districtIn("Nationwide strike"));
        assertEquals(1 + 8 + 64, com.aidalert.sos.alerts.Regions.choicesWithDistricts().size());
    }
}
