package com.aidalert.sos;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.ai.ChatRules;
import com.aidalert.sos.medicine.MedicineRules;

import org.junit.Test;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class MedicineScanAndModelTest {
    private static final TimeZone DHAKA = TimeZone.getTimeZone("Asia/Dhaka");

    @Test
    public void ocrTextOfAPrescriptionBecomesMedicines() {
        String text = "Dr. Rahman  MBBS\nRx\n1. Tab. Napa 500 mg 1+0+1 after meal x 5 days\n"
                + "2. Cap Omeprazole 20mg\n   1+0+1 before food for 14 days\nAdvice: take rest\n3) Syp Ambrox 30 mg/5 ml 1+1+1";
        List<MedicineRules.Scanned> r = MedicineRules.parseOcrText(text);
        assertEquals(3, r.size());
        assertEquals("Napa", r.get(0).name);
        assertEquals("500 mg", r.get(0).dose);
        assertEquals("1+0+1", r.get(0).frequency);
        assertEquals("5 days", r.get(0).duration);
        assertEquals("After food", r.get(0).food);
        assertEquals("Tablet", r.get(0).form);
        assertEquals("Omeprazole", r.get(1).name);
        assertEquals("20 mg", r.get(1).dose);
        assertEquals("14 days", r.get(1).duration);
        assertEquals("Before food", r.get(1).food);
        assertEquals("Capsule", r.get(1).form);
        assertEquals("Ambrox", r.get(2).name);
        assertEquals("Syrup", r.get(2).form);
    }

    @Test
    public void textWithoutMedicineLinesGivesNothing() {
        assertTrue(MedicineRules.parseOcrText("Patient: Karim\nAge 30\nBP 120/80").isEmpty());
        assertTrue(MedicineRules.parseOcrText(null).isEmpty());
        assertTrue(MedicineRules.parseOcrText("Tab.").isEmpty());
    }

    @Test
    public void frequencyBecomesClockTimes() {
        assertEquals("08:00,20:00", MedicineRules.timesForScannedFrequency("1+0+1"));
        assertEquals("08:00,14:00,20:00", MedicineRules.timesForScannedFrequency("1-1-1"));
        assertEquals("14:00", MedicineRules.timesForScannedFrequency("0+1+0"));
        assertEquals("08:00,20:00", MedicineRules.timesForScannedFrequency("BD"));
        assertEquals("08:00,14:00,20:00", MedicineRules.timesForScannedFrequency("twice daily".equals("x") ? "" : "TDS"));
        assertEquals("", MedicineRules.timesForScannedFrequency("SOS"));
        assertEquals("", MedicineRules.timesForScannedFrequency(""));
        assertEquals("08:00,20:00", MedicineRules.timesForLabel("Twice daily"));
        assertEquals("", MedicineRules.timesForLabel("Custom times"));
        assertEquals(1, MedicineRules.frequencyIndexForTimes(2));
        assertEquals(MedicineRules.FREQUENCIES.length - 1, MedicineRules.frequencyIndexForTimes(7));
    }

    @Test
    public void strengthIsSplitIntoNumberAndUnit() {
        assertArrayEquals(new String[]{"500", "mg"}, MedicineRules.splitStrength("500mg"));
        assertArrayEquals(new String[]{"2.5", "ml"}, MedicineRules.splitStrength("Dose 2.5 ML"));
        assertArrayEquals(new String[]{"", ""}, MedicineRules.splitStrength("1 tablet"));
        assertEquals(0, MedicineRules.unitIndex("mg"));
        assertEquals(3, MedicineRules.unitIndex("ML"));
    }

    @Test
    public void endDateGivesTheNumberOfDays() {
        Calendar a = Calendar.getInstance(DHAKA);
        a.set(2026, Calendar.NOVEMBER, 10, 0, 0, 0);
        long start = a.getTimeInMillis();
        Calendar b = Calendar.getInstance(DHAKA);
        b.set(2026, Calendar.NOVEMBER, 14, 23, 0, 0);
        assertEquals(5, MedicineRules.daysBetween(start, b.getTimeInMillis(), DHAKA));
        assertEquals(1, MedicineRules.daysBetween(start, start, DHAKA));
        assertEquals(0, MedicineRules.daysBetween(start, 0, DHAKA));
    }

    @Test
    public void todaysScheduleKnowsStartAndEnd() {
        Calendar s = Calendar.getInstance(DHAKA);
        s.set(2026, Calendar.NOVEMBER, 10, 0, 0, 0);
        long start = s.getTimeInMillis();
        Calendar now = Calendar.getInstance(DHAKA);
        now.set(2026, Calendar.NOVEMBER, 9, 12, 0, 0);
        assertFalse(MedicineRules.isDueToday(start, 3, now.getTimeInMillis(), DHAKA));    // not started
        now.set(2026, Calendar.NOVEMBER, 10, 12, 0, 0);
        assertTrue(MedicineRules.isDueToday(start, 3, now.getTimeInMillis(), DHAKA));
        now.set(2026, Calendar.NOVEMBER, 12, 22, 0, 0);
        assertTrue(MedicineRules.isDueToday(start, 3, now.getTimeInMillis(), DHAKA));     // third day
        now.set(2026, Calendar.NOVEMBER, 13, 1, 0, 0);
        assertFalse(MedicineRules.isDueToday(start, 3, now.getTimeInMillis(), DHAKA));    // finished
        assertTrue(MedicineRules.isDueToday(start, 0, now.getTimeInMillis(), DHAKA));     // until stopped
        assertEquals("2026-11-13", MedicineRules.dayKey(now.getTimeInMillis(), DHAKA));
    }

    @Test
    public void timeLabelsAndUpcoming() {
        assertEquals("8:00 AM", MedicineRules.prettyTime("08:00"));
        assertEquals("2:05 PM", MedicineRules.prettyTime("14:05"));
        assertEquals("12:00 AM", MedicineRules.prettyTime("00:00"));
        assertEquals("12:30 PM", MedicineRules.prettyTime("12:30"));
        Calendar now = Calendar.getInstance(DHAKA);
        now.set(2026, Calendar.NOVEMBER, 10, 13, 0, 0);
        assertTrue(MedicineRules.isUpcoming(now.getTimeInMillis(), "14:00", DHAKA));
        assertFalse(MedicineRules.isUpcoming(now.getTimeInMillis(), "08:00", DHAKA));
        assertEquals(2, MedicineRules.foodIndex("after meal"));
        assertEquals(0, MedicineRules.foodIndex(""));
    }

    @Test
    public void groqTextRequestAndAnswer() throws Exception {
        String body = MedicineRules.textBody("openai/gpt-oss-20b", "Tab Napa 500 mg");
        assertTrue(body.contains("Tab Napa 500 mg"));
        assertTrue(body.contains("reasoning_effort"));
        assertFalse(MedicineRules.textBody("qwen/qwen3.8-27b", "x").contains("reasoning_effort"));
        List<MedicineRules.Scanned> r = MedicineRules.parseScan(
                "[{\"name\":\"Napa\",\"dose\":\"500 mg\",\"form\":\"tablet\",\"frequency\":\"1+0+1\",\"duration\":null,\"food\":\"after food\",\"unclear\":false}]");
        assertEquals(1, r.size());
        assertEquals("", r.get(0).duration);
        assertEquals("tablet", r.get(0).form);
        assertEquals("after food", r.get(0).food);
    }

    @Test
    public void retiredGroqModelsAreNeverSent() {
        assertTrue(ChatRules.isRetired("llama-3.3-70b-versatile"));
        assertTrue(ChatRules.isRetired("llama-3.1-8b-instant"));
        assertTrue(ChatRules.isRetired(""));
        assertTrue(ChatRules.isRetired(null));
        assertFalse(ChatRules.isRetired("openai/gpt-oss-20b"));
        List<String> c = ChatRules.candidates("llama-3.3-70b-versatile");
        assertEquals("openai/gpt-oss-20b", c.get(0));
        assertEquals(3, c.size());
        List<String> d = ChatRules.candidates("openai/gpt-oss-120b");
        assertEquals("openai/gpt-oss-120b", d.get(0));
        assertEquals(3, d.size());
    }

    @Test
    public void groqErrorsAreToldApart() {
        assertTrue(ChatRules.isModelProblem("HTTP 404: {\"error\":{\"message\":\"The model `x` does not exist\"}}"));
        assertTrue(ChatRules.isModelProblem("HTTP 400: model_decommissioned"));
        assertFalse(ChatRules.isModelProblem("HTTP 401: invalid api key"));
        assertFalse(ChatRules.isModelProblem("timeout"));
    }

    @Test
    public void modelListFromGroqKeepsOnlyChatModels() {
        String json = "{\"data\":[{\"id\":\"whisper-large-v3\",\"active\":true},{\"id\":\"openai/gpt-oss-120b\",\"active\":true},"
                + "{\"id\":\"meta-llama/llama-guard-4-12b\",\"active\":true},{\"id\":\"llama-3.1-8b-instant\",\"active\":true},"
                + "{\"id\":\"qwen/qwen3.8-27b\",\"active\":true},{\"id\":\"old\",\"active\":false}]}";
        List<String> m = ChatRules.chatModelsFrom(json);
        assertEquals(2, m.size());
        assertEquals("openai/gpt-oss-120b", ChatRules.pickModel(m));
        assertTrue(ChatRules.chatModelsFrom("not json").isEmpty());
        assertEquals("", ChatRules.pickModel(ChatRules.chatModelsFrom("{}")));
    }

    @Test
    public void chatRequestAsksForLowReasoning() throws Exception {
        String b = ChatRules.requestBody("openai/gpt-oss-20b", new java.util.ArrayList<>(), "hello");
        assertTrue(b.contains("\"reasoning_effort\":\"low\""));
        assertFalse(ChatRules.requestBody("qwen/qwen3.8-27b", new java.util.ArrayList<>(), "hello").contains("reasoning_effort"));
    }

    @Test
    public void dengueIsHealthNotDisaster() {
        assertEquals(com.aidalert.sos.alerts.AlertItem.HEALTH, com.aidalert.sos.alerts.AlertRules.kindFromText("Dengue cases rise in Dhaka"));
        assertEquals(com.aidalert.sos.alerts.AlertItem.QUAKE, com.aidalert.sos.alerts.AlertRules.kindFromText("Earthquake of magnitude 5"));
        assertEquals(com.aidalert.sos.alerts.AlertItem.WEATHER, com.aidalert.sos.alerts.AlertRules.kindFromText("Cyclone signal 4"));
        assertEquals("Health", com.aidalert.sos.alerts.AlertRules.tagFor("HEALTH", "EMERGENCY", "NATIONAL"));
        assertEquals("Disaster & weather", com.aidalert.sos.alerts.AlertRules.tagFor("CYCLONE_FLOOD", "EMERGENCY", "NATIONAL"));
    }

    @Test
    public void alertChipsSortEveryTypeAndAllShowsEverything() {
        String[][] items = {
                {"HEALTH", "EMERGENCY", "NATIONAL"}, {"CYCLONE_FLOOD", "EMERGENCY", "NATIONAL"}, {"EARTHQUAKE", "EMERGENCY", "NATIONAL"},
                {"OTHER", "LAW_ORDER", "NATIONAL"}, {"OTHER", "SAFETY_NEWS", "LOCAL"}, {"EARTHQUAKE", "EMERGENCY", "INTERNATIONAL"}};
        for (String[] it : items) assertTrue(com.aidalert.sos.alerts.AlertRules.matchesFilter("", it[0], it[1], it[2]));
        // dengue only under Health
        assertTrue(com.aidalert.sos.alerts.AlertRules.matchesFilter("HEALTH", "HEALTH", "EMERGENCY", "NATIONAL"));
        assertFalse(com.aidalert.sos.alerts.AlertRules.matchesFilter("DISASTER_WEATHER", "HEALTH", "EMERGENCY", "NATIONAL"));
        assertTrue(com.aidalert.sos.alerts.AlertRules.matchesFilter("DISASTER_WEATHER", "CYCLONE_FLOOD", "EMERGENCY", "NATIONAL"));
        assertFalse(com.aidalert.sos.alerts.AlertRules.matchesFilter("DISASTER_WEATHER", "EARTHQUAKE", "EMERGENCY", "NATIONAL"));
        assertTrue(com.aidalert.sos.alerts.AlertRules.matchesFilter("QUAKE", "EARTHQUAKE", "EMERGENCY", "NATIONAL"));
        assertTrue(com.aidalert.sos.alerts.AlertRules.matchesFilter("LAW_ORDER", "OTHER", "LAW_ORDER", "NATIONAL"));
        assertFalse(com.aidalert.sos.alerts.AlertRules.matchesFilter("LAW_ORDER", "OTHER", "SAFETY_NEWS", "LOCAL"));
        assertTrue(com.aidalert.sos.alerts.AlertRules.matchesFilter("INTERNATIONAL", "EARTHQUAKE", "EMERGENCY", "INTERNATIONAL"));
        assertFalse(com.aidalert.sos.alerts.AlertRules.matchesFilter("SAFETY_NEWS", "OTHER", "SAFETY_NEWS", "INTERNATIONAL"));
    }

    private static String jwt(String payloadJson) {
        String b = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "xxx." + b + ".sig";
    }

    @Test
    public void serverRefusalIsExplainedFromTheToken() {
        com.aidalert.sos.blood.AccessInfo ok = com.aidalert.sos.blood.AccessInfo.fromToken(jwt("{\"email_verified\":true,\"phone_number\":\"+8801712345678\"}"));
        assertTrue(ok.verified());
        com.aidalert.sos.blood.AccessInfo noMail = com.aidalert.sos.blood.AccessInfo.fromToken(jwt("{\"email_verified\":false,\"phone_number\":\"+8801712345678\"}"));
        assertFalse(noMail.verified());
        assertTrue(com.aidalert.sos.blood.AccessInfo.explain("Sync", 401, "", true, noMail).contains("email is not verified"));
        com.aidalert.sos.blood.AccessInfo noPhone = com.aidalert.sos.blood.AccessInfo.fromToken(jwt("{\"email_verified\":true}"));
        assertTrue(com.aidalert.sos.blood.AccessInfo.explain("Sync", 403, "", true, noPhone).contains("phone number is not verified"));
        assertTrue(com.aidalert.sos.blood.AccessInfo.explain("Sync", 401, "", true, ok).contains("Publish"));
        assertTrue(com.aidalert.sos.blood.AccessInfo.explain("Sync", -1, "timeout", true, ok).contains("no connection"));
        assertTrue(com.aidalert.sos.blood.AccessInfo.explain("Sync", 500, "", true, ok).contains("500"));
        assertTrue(com.aidalert.sos.blood.AccessInfo.explain("Sync", 401, "", true, null).contains("401"));
        assertFalse(com.aidalert.sos.blood.AccessInfo.fromToken("").known);
        assertFalse(com.aidalert.sos.blood.AccessInfo.fromToken("a.b").known);
        assertFalse(com.aidalert.sos.blood.AccessInfo.fromToken(null).known);
    }

    @Test
    public void incidentReportRules() {
        java.util.Random rnd = new java.util.Random(7);
        String id = com.aidalert.sos.report.ReportRules.trackingId(1_794_000_000_000L, rnd);
        assertTrue(id, com.aidalert.sos.report.ReportRules.isTrackingId(id));
        assertFalse(com.aidalert.sos.report.ReportRules.isTrackingId("AA-261109-0OIL"));
        assertEquals(null, com.aidalert.sos.report.ReportRules.check(0, "A bag was snatched near the bus stand"));
        assertTrue(com.aidalert.sos.report.ReportRules.check(0, "short") != null);
        assertTrue(com.aidalert.sos.report.ReportRules.check(-1, "long enough description") != null);
        assertArrayEquals(new double[]{22.8456, 89.5403}, com.aidalert.sos.report.ReportRules.parseLatLon("22.8456, 89.5403"), 1e-9);
        assertArrayEquals(new double[]{22.8, 89.5}, com.aidalert.sos.report.ReportRules.parseLatLon("22.8 89.5"), 1e-9);
        assertEquals(null, com.aidalert.sos.report.ReportRules.parseLatLon("near the bus stand"));
        assertEquals(null, com.aidalert.sos.report.ReportRules.parseLatLon("122.8, 89.5"));
        assertEquals("22.84560, 89.54030", com.aidalert.sos.report.ReportRules.formatLatLon(22.8456, 89.5403));
        assertEquals("Submitted", com.aidalert.sos.report.ReportRules.statusLabel(""));
        assertEquals("Under review", com.aidalert.sos.report.ReportRules.statusLabel("UNDER_REVIEW"));
    }

    @Test
    public void bangladeshAlertsComeBeforeInternationalOnes() {
        com.aidalert.sos.alerts.AlertItem bdOld = new com.aidalert.sos.alerts.AlertItem();
        bdOld.scope = "NATIONAL"; bdOld.timeMs = 1000;
        com.aidalert.sos.alerts.AlertItem worldNew = new com.aidalert.sos.alerts.AlertItem();
        worldNew.scope = com.aidalert.sos.alerts.AlertItem.SCOPE_WORLD; worldNew.timeMs = 9000;
        com.aidalert.sos.alerts.AlertItem bdNew = new com.aidalert.sos.alerts.AlertItem();
        bdNew.scope = "NATIONAL"; bdNew.timeMs = 5000;
        java.util.List<com.aidalert.sos.alerts.AlertItem> l = new java.util.ArrayList<>(java.util.Arrays.asList(worldNew, bdOld, bdNew));
        java.util.Collections.sort(l, com.aidalert.sos.alerts.AlertRules::order);
        assertEquals(bdNew, l.get(0));
        assertEquals(bdOld, l.get(1));
        assertEquals(worldNew, l.get(2));
    }
}
