package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.engine.PowerTapDetector;
import com.aidalert.sos.net.AlertFeedParser;
import com.aidalert.sos.net.Crypto;
import com.aidalert.sos.net.SmsProtocol;
import com.aidalert.sos.util.Geo;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/** Pure-Java logic tests (run on the PC, no emulator): power sequence rule, SMS format, crypto, distance. */
public class CoreLogicTest {

    @Test
    public void fiveQuickPressesTrigger() {
        PowerTapDetector d = new PowerTapDetector(5, 1500);
        long t = 0;
        for (int i = 0; i < 4; i++) assertFalse(d.onPowerPress(t += 400));
        assertTrue(d.onPowerPress(t += 400));
        assertEquals(0, d.getCount());
    }

    @Test
    public void otherInputBetweenPressesResets() {
        PowerTapDetector d = new PowerTapDetector(5, 1500);
        d.onPowerPress(100);
        d.onPowerPress(500);
        d.onOtherInput();                      // user touched something after press 2
        assertEquals(0, d.getCount());
        assertFalse(d.onPowerPress(900));      // counting restarts at 1
        assertFalse(d.onPowerPress(1300));
        assertFalse(d.onPowerPress(1700));
        assertFalse(d.onPowerPress(2100));
        assertTrue(d.onPowerPress(2500));      // five NEW presses needed
    }

    @Test
    public void longPauseResets() {
        PowerTapDetector d = new PowerTapDetector(5, 1500);
        for (int i = 0; i < 4; i++) d.onPowerPress(i * 300L);
        assertFalse(d.onPowerPress(5000));     // gap too long: this is press 1 again
        assertEquals(1, d.getCount());
    }

    @Test
    public void sixPressesTriggerOnlyOnce() {
        PowerTapDetector d = new PowerTapDetector(5, 1500);
        int triggers = 0;
        for (int i = 0; i < 6; i++) if (d.onPowerPress(i * 300L)) triggers++;
        assertEquals(1, triggers);
    }

    @Test
    public void smsRoundTrip() {
        String s = SmsProtocol.encode("u1_123", "FIRE", 23.81234, 90.41256, 1_700_000_000_000L, "uid9", "Rahim|Uddin", "8801711000000");
        SmsProtocol.Parsed p = SmsProtocol.decode(s);
        assertNotNull(p);
        assertEquals("u1_123", p.incidentId);
        assertEquals("FIRE", p.type);
        assertEquals(23.81234, p.lat, 1e-9);
        assertEquals(90.41256, p.lon, 1e-9);
        assertEquals(1_700_000_000_000L, p.timeMs);
        assertEquals("Rahim Uddin", p.name);   // '|' is stripped from free text
        assertEquals("8801711000000", p.phone);
    }

    @Test
    public void smsWithoutFixAndGarbage() {
        String s = SmsProtocol.encode("id", "MEDICAL", Double.NaN, Double.NaN, 5000, "u", "A", "1");
        SmsProtocol.Parsed p = SmsProtocol.decode(s);
        assertNotNull(p);
        assertTrue(Double.isNaN(p.lat));
        assertNull(SmsProtocol.decode("hello"));
        assertNull(SmsProtocol.decode("AIDSOS|v1|only|two"));
    }

    @Test
    public void phoneNormalisationAndUserId() {
        assertEquals("8801711000000", Crypto.normalizePhone("01711-000000"));
        assertEquals("8801711000000", Crypto.normalizePhone("+880 1711 000000"));
        assertEquals(Crypto.phoneKey("01711000000"), Crypto.phoneKey("+8801711000000"));
        assertEquals(20, Crypto.phoneKey("01711000000").length());
    }

    @Test
    public void hybridEncryptionOnlyCenterCanRead() {
        String[] center = Crypto.generateKeyPair();
        String[] stranger = Crypto.generateKeyPair();
        byte[] msg = "{\"id\":\"x\",\"lat\":23.8}".getBytes(StandardCharsets.UTF_8);
        String packet = Crypto.encrypt(center[0], msg);
        assertEquals("{\"id\":\"x\",\"lat\":23.8}", new String(Crypto.decrypt(center[1], packet), StandardCharsets.UTF_8));
        boolean failed = false;
        try {
            Crypto.decrypt(stranger[1], packet);
        } catch (IllegalStateException e) {
            failed = true;
        }
        assertTrue("a relaying phone must not be able to read the packet", failed);
    }

    @Test
    public void distance() {
        // Dhaka -> Chattogram is about 215-220 km in a straight line
        double km = Geo.distanceKm(23.8103, 90.4125, 22.3569, 91.7832);
        assertTrue(km > 205 && km < 230);
        assertEquals(0.0, Geo.distanceKm(1, 1, 1, 1), 1e-9);
    }

    @Test
    public void parsesRssAtomAndCapFeeds() throws Exception {
        String rss = "<?xml version=\"1.0\"?><rss><channel><title>Feed</title>"
                + "<item><title>Flood warning</title><description>River rising</description><pubDate>Mon, 1 Jan</pubDate></item>"
                + "<item><title>Cyclone</title><description>Signal 4</description></item></channel></rss>";
        java.util.List<AlertFeedParser.Item> a = AlertFeedParser.parse(rss);
        assertEquals(2, a.size());                 // the channel <title> is not an alert
        assertEquals("Flood warning", a.get(0).title);
        assertEquals("River rising", a.get(0).description);
        assertEquals("Mon, 1 Jan", a.get(0).date);

        String atom = "<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>x</title>"
                + "<entry><title>Heat</title><summary>Stay inside</summary><updated>2026-10-01</updated></entry></feed>";
        assertEquals("Stay inside", AlertFeedParser.parse(atom).get(0).description);

        String cap = "<cap:alert xmlns:cap=\"urn:oasis:names:tc:emergency:cap:1.2\"><cap:info>"
                + "<cap:headline>Landslide risk</cap:headline><cap:description>Hill areas</cap:description></cap:info></cap:alert>";
        assertEquals("Landslide risk", AlertFeedParser.parse(cap).get(0).title);
    }

    @Test
    public void rejectsBrokenXml() {
        boolean failed = false;
        try {
            AlertFeedParser.parse("<rss><item><title>x</rss>");
        } catch (Exception e) {
            failed = true;
        }
        assertTrue(failed);
    }
}
