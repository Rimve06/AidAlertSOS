package bd.aidalert.ingest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;

/** Downloads a PDF from an official site and refuses anything that does not look like one. */
public final class PdfFetcher {
    private PdfFetcher() {}

    static final long MAX_BYTES = 25L * 1024 * 1024;

    /** Only https pages on a government domain are accepted. */
    public static boolean trustedUrl(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
            return "https".equals(u.getScheme()) && (host.endsWith(".gov.bd") || host.equals("gov.bd"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean looksLikePdf(byte[] b) {
        return b != null && b.length > 8 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
    }

    public static String sha256(byte[] data) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte x : h) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static byte[] download(String url) throws IOException, InterruptedException {
        if (!trustedUrl(url)) throw new IOException("Refusing " + url + ": only https .gov.bd addresses are allowed.");
        HttpClient c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "AidAlertSOS-ingest/1.0 (student project)").GET().build();
        HttpResponse<byte[]> res = c.send(r, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
        byte[] body = res.body();
        if (body.length > MAX_BYTES) throw new IOException("File too large");
        if (!looksLikePdf(body)) throw new IOException("The download is not a PDF");
        return body;
    }

    public static byte[] readLocal(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        if (!looksLikePdf(b)) throw new IOException(p + " is not a PDF");
        return b;
    }

    static String utf8(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
