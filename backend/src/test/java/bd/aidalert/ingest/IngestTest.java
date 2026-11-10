package bd.aidalert.ingest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class IngestTest {
    @Test
    public void onlyGovernmentHttpsIsTrusted() {
        assertTrue(PdfFetcher.trustedUrl("https://www.police.gov.bd/uploads/report.pdf"));
        assertFalse(PdfFetcher.trustedUrl("http://www.police.gov.bd/report.pdf"));
        assertFalse(PdfFetcher.trustedUrl("https://evil.example.com/police.gov.bd.pdf"));
        assertFalse(PdfFetcher.trustedUrl("not a url"));
    }

    @Test
    public void pdfSignatureIsChecked() {
        assertTrue(PdfFetcher.looksLikePdf("%PDF-1.7 rest".getBytes()));
        assertFalse(PdfFetcher.looksLikePdf("<html>".getBytes()));
    }

    @Test
    public void rowsNeedExactColumnCount() {
        String text = "Monthly report\nDMP Dhaka 12 20 1,150\nCMP Chattogram 7 9 90\nTotal 19 29\nGazipur 3 4\nNote 2025 page 1";
        StatsRowParser.Result r = StatsRowParser.parse(text, 3);
        assertEquals(2, r.rows.size());
        assertEquals("DMP Dhaka", r.rows.get(0).name);
        assertEquals(1150, r.rows.get(0).counts[2]);
        assertTrue(r.skipped.contains("Total 19 29"));
        assertTrue(r.skipped.contains("Gazipur 3 4"));
    }

    @Test
    public void jsonMatchesAppFormat() {
        StatsRowParser.Result r = StatsRowParser.parse("DMP Dhaka 12 20", 2);
        String j = StatsRowParser.toJson(r, List.of("Murder", "Theft"), "t", "2025-11", "src", "", Map.of("DMP Dhaka", "Dhaka"));
        assertTrue(j.contains("\"crimeTypes\":[\"Murder\",\"Theft\"]"));
        assertTrue(j.contains("{\"name\":\"DMP Dhaka\",\"group\":\"Dhaka\",\"counts\":[12,20]}"));
    }

    @Test
    public void argsParse() {
        Map<String, String> m = Args.parse(new String[]{"--pdf", "a.pdf", "--dry-run"});
        assertEquals("a.pdf", m.get("pdf"));
        assertEquals("true", m.get("dry-run"));
    }

    @Test
    public void firstPdfOnThePageIsTheNewest() {
        String html = "<a href=\"/uploads/stat/oct-2026.pdf\">October</a><a href='/uploads/stat/sep-2026.pdf'>September</a>";
        assertEquals("https://www.police.gov.bd/uploads/stat/oct-2026.pdf",
                PageLinks.firstPdf(html, "https://www.police.gov.bd/en/january_2020"));
        assertEquals(null, PageLinks.firstPdf("<a href=\"/x.html\">none</a>", "https://www.police.gov.bd/en/january_2020"));
        assertEquals("https://www.police.gov.bd/a%20b.pdf",
                PageLinks.firstPdf("<a href=\"a b.pdf?x=1\">x</a>", "https://www.police.gov.bd/"));
    }

    @Test
    public void autoPeriodIsLastMonthInDhaka() {
        assertEquals("2026-10", PageLinks.previousMonth(java.time.Instant.parse("2026-11-10T04:00:00Z")));
        // 30 Nov 20:00 UTC is already 1 Dec in Dhaka, so "last month" is November
        assertEquals("2026-11", PageLinks.previousMonth(java.time.Instant.parse("2026-11-30T20:00:00Z")));
    }
}
