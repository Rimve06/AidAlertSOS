package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.engine.ImpactDetector;
import com.aidalert.sos.medicine.MedicineRules;
import com.aidalert.sos.tools.SirenTone;

import org.junit.Test;

import java.util.List;
import java.util.TimeZone;

public class ToolsAndMedicineTest {
    private static final float G = 9.80665f;

    @Test
    public void impactThenStillnessIsAnAccident() {
        ImpactDetector d = new ImpactDetector();
        assertFalse(d.onSample(0, 0, 0, G));
        assertFalse(d.onSample(1000, 0, 0, 4.5f * G));          // hard impact
        boolean fired = false;
        for (long t = 1100; t <= 9000; t += 100) fired |= d.onSample(t, 0, 0, G);   // lying still
        assertTrue(fired);
    }

    @Test
    public void impactThenWalkingIsNot() {
        ImpactDetector d = new ImpactDetector();
        d.onSample(1000, 0, 0, 4.5f * G);
        boolean fired = false;
        for (long t = 1100; t <= 12000; t += 100) fired |= d.onSample(t, 0, 0, (t / 100 % 2 == 0) ? 1.8f * G : 0.4f * G);
        assertFalse(fired);
    }

    @Test
    public void bumpsAndCooldown() {
        ImpactDetector d = new ImpactDetector();
        boolean any = false;
        for (long t = 0; t <= 20000; t += 100) any |= d.onSample(t, 0, 0, 2.0f * G);   // 2 g jolts only
        assertFalse(any);
        d.onSample(21000, 0, 0, 5f * G);
        int fires = 0;
        for (long t = 21100; t <= 40000; t += 100) if (d.onSample(t, 0, 0, G)) fires++;
        assertEquals(1, fires);
    }

    @Test
    public void sirenCycleHasExpectedLengthAndRange() {
        short[] pcm = SirenTone.cycle(22050);
        assertEquals((int) Math.round(22050 * SirenTone.CYCLE_S), pcm.length);
        int max = 0;
        for (short s : pcm) max = Math.max(max, Math.abs(s));
        assertTrue(max > 20000 && max <= Short.MAX_VALUE);
    }

    @Test
    public void timesAreParsedAndValidated() {
        List<String> t = MedicineRules.parseTimes("20:00, 8:00 14:30 8:00 25:00");
        assertEquals(3, t.size());
        assertEquals("08:00", t.get(0));
        assertEquals("20:00", t.get(2));
        assertTrue(MedicineRules.hasBadTimes("08:00, 7pm"));
        assertFalse(MedicineRules.hasBadTimes("08:00, 20:00"));
    }

    @Test
    public void nextTriggerIsTodayOrTomorrow() {
        TimeZone dhaka = TimeZone.getTimeZone("Asia/Dhaka");
        long now = java.time.Instant.parse("2026-11-07T04:00:00Z").toEpochMilli();       // 10:00 in Dhaka
        long at14 = MedicineRules.nextTrigger(now, "14:00", dhaka);
        assertEquals(java.time.Instant.parse("2026-11-07T08:00:00Z").toEpochMilli(), at14);
        long at08 = MedicineRules.nextTrigger(now, "08:00", dhaka);
        assertEquals(java.time.Instant.parse("2026-11-08T02:00:00Z").toEpochMilli(), at08);
    }

    @Test
    public void durationsAndEnd() {
        assertEquals(5, MedicineRules.daysFrom("5 days"));
        assertEquals(14, MedicineRules.daysFrom("2 weeks"));
        assertEquals(0, MedicineRules.daysFrom("as needed"));
        assertTrue(MedicineRules.hasEnded(0, 5, 6 * 86_400_000L));
        assertFalse(MedicineRules.hasEnded(0, 0, 999 * 86_400_000L));
    }

    @Test
    public void scanReplyIsParsedStrictly() {
        String reply = "Here you go:\n[{\"name\":\"Napa 500mg\",\"dose\":\"1 tab\",\"frequency\":\"1+1+1\",\"duration\":\"5 days\",\"unclear\":false},"
                + "{\"name\":\"\",\"dose\":\"x\"},{\"name\":\"Zxcv\",\"unclear\":true}]";
        List<MedicineRules.Scanned> l = MedicineRules.parseScan(reply);
        assertEquals(2, l.size());
        assertEquals("Napa 500mg", l.get(0).name);
        assertTrue(l.get(1).unclear);
        assertEquals(0, MedicineRules.parseScan("sorry, cannot read").size());
        assertEquals(0, MedicineRules.parseScan("[not json]").size());
    }
}
