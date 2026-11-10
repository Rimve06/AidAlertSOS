package bd.aidalert.ingest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signs in as the control center (email + password) and replaces stats/latest in the Realtime Database.
 * The database rules still decide: only the account with roles/{uid} = CENTER can write.
 */
public final class FirebasePublisher {
    private final String apiKey, dbUrl, email, password;
    private final HttpClient http = HttpClient.newHttpClient();

    public FirebasePublisher(String apiKey, String dbUrl, String email, String password) {
        this.apiKey = apiKey;
        this.dbUrl = dbUrl.endsWith("/") ? dbUrl.substring(0, dbUrl.length() - 1) : dbUrl;
        this.email = email;
        this.password = password;
    }

    private String idToken() throws IOException, InterruptedException {
        String body = "{\"email\":\"" + email.replace("\"", "") + "\",\"password\":\"" + password.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"returnSecureToken\":true}";
        HttpRequest r = HttpRequest.newBuilder(URI.create(
                "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + apiKey))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = http.send(r, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("Sign-in failed (HTTP " + res.statusCode() + ")");
        Matcher m = Pattern.compile("\"idToken\"\\s*:\\s*\"([^\"]+)\"").matcher(res.body());
        if (!m.find()) throw new IOException("No token in the sign-in answer");
        return m.group(1);
    }

    /** PUT replaces the previous report completely. */
    public void publishStats(String json) throws IOException, InterruptedException {
        String token = idToken();
        HttpRequest r = HttpRequest.newBuilder(URI.create(dbUrl + "/stats/latest.json?auth=" + token))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        HttpResponse<String> res = http.send(r, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("Publish failed (HTTP " + res.statusCode() + "): " + res.body());
    }
}
