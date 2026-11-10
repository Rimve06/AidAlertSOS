package bd.aidalert.ingest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the newest report on the police statistics page (https://www.police.gov.bd/en/january_2020).
 * That page lists its PDFs with the latest one first, so the first PDF link is the newest report.
 */
public final class PageLinks {
    private PageLinks() {}

    private static final Pattern HREF = Pattern.compile("href\\s*=\\s*[\"']([^\"']+?\\.pdf)(?:\\?[^\"']*)?[\"']", Pattern.CASE_INSENSITIVE);

    /** The first PDF link on the page, made absolute against the page address. Null when there is none. */
    public static String firstPdf(String html, String pageUrl) {
        if (html == null) return null;
        Matcher m = HREF.matcher(html);
        if (!m.find()) return null;
        try {
            return URI.create(pageUrl).resolve(m.group(1).trim().replace(" ", "%20")).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Reports are published for the month that just ended, so "auto" means last month in Bangladesh time. */
    public static String previousMonth(java.time.Instant now) {
        return YearMonth.now(java.time.Clock.fixed(now, ZoneId.of("Asia/Dhaka"))).minusMonths(1).toString();
    }

    public static String fetchPage(String url) throws IOException, InterruptedException {
        if (!PdfFetcher.trustedUrl(url)) throw new IOException("Refusing " + url + ": only https .gov.bd addresses are allowed.");
        HttpClient c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "AidAlertSOS-ingest/1.0 (student project)").GET().build();
        HttpResponse<String> res = c.send(r, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
        return res.body();
    }
}
