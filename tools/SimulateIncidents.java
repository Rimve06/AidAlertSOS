import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-data generator, so you do not need many phones. Runs on the laptop, plain Java 17, no libraries:
 *
 *   java tools/SimulateIncidents.java <webApiKey> <email> <password> [count]
 *
 * It signs in as ONE real, fully verified test citizen account (Firebase Auth REST), then writes
 * `count` fake incidents under that account's id (the database rules only allow ids that start with
 * the signer's uid). Open the Control Center in the emulator on this laptop and they appear.
 * Every simulated incident is labelled "[SIMULATION]". Nothing is dispatched to real services.
 *
 * The web API key is the "Web API Key" in Firebase Console > Project settings. It is not a secret,
 * but do not commit it with a password.
 */
public class SimulateIncidents {
    static final String DB = "https://aid-alert-18ac6-default-rtdb.firebaseio.com";
    static final String[] TYPES = {"MEDICAL", "ACCIDENT", "FIRE", "VIOLENCE", "OTHER"};
    // Dhaka area only: a rough box so the map shows a cluster
    static final double LAT0 = 23.70, LAT1 = 23.85, LON0 = 90.35, LON1 = 90.45;

    public static void main(String[] a) throws Exception {
        if (a.length < 3) {
            System.err.println("usage: java tools/SimulateIncidents.java <webApiKey> <email> <password> [count]");
            System.exit(2);
        }
        int count = a.length > 3 ? Integer.parseInt(a[3]) : 10;
        String body = "{\"email\":\"" + a[1] + "\",\"password\":\"" + a[2] + "\",\"returnSecureToken\":true}";
        String login = call("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + a[0], body);
        String token = find(login, "idToken");
        String uid = find(login, "localId");
        if (token == null || uid == null) throw new RuntimeException("Sign-in failed: " + login);
        Random r = new Random();
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            long t = now - r.nextInt(600_000);
            String id = uid + "_" + (t + i);
            String type = TYPES[r.nextInt(TYPES.length)];
            double lat = LAT0 + r.nextDouble() * (LAT1 - LAT0);
            double lon = LON0 + r.nextDouble() * (LON1 - LON0);
            String json = String.format(Locale.US,
                    "{\"id\":\"%s\",\"userId\":\"%s\",\"userName\":\"[SIMULATION] Person %d\",\"phone\":\"\",\"type\":\"%s\","
                            + "\"source\":\"SIMULATION\",\"status\":\"NEW\",\"via\":\"INTERNET\",\"note\":\"Simulated test incident\","
                            + "\"silent\":false,\"battery\":%d,\"createdAt\":%d,\"updatedAt\":%d,\"lat\":%.6f,\"lon\":%.6f,\"acc\":12.0}",
                    id, uid, i + 1, type, 20 + r.nextInt(80), t, t, lat, lon);
            String res = call("PUT", DB + "/incidents/" + id + "/report.json?auth=" + token, json);
            System.out.println((i + 1) + "/" + count + " " + type + " -> " + (res.contains("\"error\"") ? res : "ok"));
        }
    }

    static String find(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static String call(String method, String url, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
