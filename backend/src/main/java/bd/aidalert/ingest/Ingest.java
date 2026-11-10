package bd.aidalert.ingest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Crime-statistics ingest. Run by hand or from the operating system's scheduler (Task Scheduler / cron):
 *
 * Monthly use (every 10th): give --page instead of --pdf. The newest PDF on the police statistics page is downloaded,
 * and --period auto means "last month":
 *
 *   java -jar aidalert-ingest.jar --page https://www.police.gov.bd/en/january_2020 --period auto --columns "..."
 *
 *   java -jar aidalert-ingest.jar --pdf URL_OR_FILE --period 2025-11 --title "Reported cases, November 2025"
 *        --columns "Murder,Robbery,Theft" [--groups groups.csv] [--source-url PAGE] [--dry-run]
 *
 * Environment for publishing: AID_API_KEY, AID_DB_URL, AID_EMAIL, AID_PASSWORD (the control center login).
 * With --dry-run it only prints the JSON and the lines it did not understand. The same PDF is never published twice
 * (its SHA-256 is kept in .last_hash).
 */
public final class Ingest {
    private Ingest() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> a = Args.parse(args);
        String pdf = a.get("pdf");
        String cols = a.get("columns");
        if (pdf == null && a.get("page") != null) {
            pdf = PageLinks.firstPdf(PageLinks.fetchPage(a.get("page")), a.get("page"));
            if (pdf == null) {
                System.err.println("No PDF link found on " + a.get("page") + ". Nothing was published.");
                System.exit(5);
            }
            System.out.println("Newest report: " + pdf);
        }
        if ("auto".equals(a.get("period"))) a.put("period", PageLinks.previousMonth(java.time.Instant.now()));
        if (pdf == null || cols == null || a.get("period") == null) {
            System.err.println("Needs (--pdf or --page), --columns and --period. See the class comment.");
            System.exit(2);
        }
        byte[] data = pdf.startsWith("http") ? PdfFetcher.download(pdf) : PdfFetcher.readLocal(Path.of(pdf));
        String hash = PdfFetcher.sha256(data);
        Path last = Path.of(".last_hash");
        if (!a.containsKey("dry-run") && Files.exists(last) && Files.readString(last).trim().equals(hash)) {
            System.out.println("This PDF was already published. Nothing to do.");
            return;
        }
        String text = PdfText.extract(data);
        if (text.trim().isEmpty()) {
            System.err.println("No text in the PDF (it may be a scan). It needs OCR; nothing was published.");
            System.exit(3);
        }
        List<String> crimeTypes = Arrays.stream(cols.split(",")).map(String::trim).toList();
        StatsRowParser.Result res = StatsRowParser.parse(text, crimeTypes.size());
        Map<String, String> groups = a.containsKey("groups")
                ? StatsRowParser.parseGroups(Files.readString(Path.of(a.get("groups")), StandardCharsets.UTF_8)) : Map.of();
        String json = StatsRowParser.toJson(res, crimeTypes, a.getOrDefault("title", "Reported cases " + a.get("period")),
                a.get("period"), "Bangladesh Police Headquarters crime statistics",
                a.getOrDefault("source-url", pdf.startsWith("http") ? pdf : ""), groups);
        System.out.println("Rows accepted: " + res.rows.size() + ", lines skipped: " + res.skipped.size());
        for (String s : res.skipped) System.out.println("  SKIPPED: " + s);
        if (res.rows.isEmpty()) {
            System.err.println("No rows matched the column count. Check --columns against the PDF. Nothing was published.");
            System.exit(4);
        }
        if (a.containsKey("dry-run")) {
            System.out.println(json);
            return;
        }
        new FirebasePublisher(env("AID_API_KEY"), env("AID_DB_URL"), env("AID_EMAIL"), env("AID_PASSWORD")).publishStats(json);
        Files.writeString(last, hash);
        System.out.println("Published. The previous report was replaced.");
    }

    private static String env(String k) {
        String v = System.getenv(k);
        if (v == null || v.isEmpty()) throw new IllegalStateException("Set the environment variable " + k);
        return v;
    }
}
