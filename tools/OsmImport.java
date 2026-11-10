import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Run on your laptop (needs internet), not on the phone:
 *
 *   java tools/OsmImport.java app/src/main/assets/facilities_seed.json
 *
 * Downloads hospitals, fire stations and police stations in Bangladesh from OpenStreetMap
 * (Overpass API) and writes the offline seed file. Data (c) OpenStreetMap contributors, ODbL.
 * Phone numbers are copied only when OSM has one, and are always marked unverified:
 * nothing is invented and nothing is called "verified" until a person checks it.
 */
public class OsmImport {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: java tools/OsmImport.java <output facilities_seed.json>");
            System.exit(2);
        }
        String q = "[out:csv(::type,::id,::lat,::lon,amenity,name,\"name:en\",phone,\"contact:phone\","
                + "\"addr:district\";false;\"\\t\")][timeout:180];"
                + "area[\"ISO3166-1\"=\"BD\"]->.bd;"
                + "(nwr[\"amenity\"~\"^(hospital|fire_station|police)$\"](area.bd););out center;";
        HttpURLConnection c = (HttpURLConnection) new URL("https://overpass-api.de/api/interpreter").openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("User-Agent", "AidAlertSOS-import/1.0 (student project)");
        try (OutputStream os = c.getOutputStream()) {
            os.write(("data=" + URLEncoder.encode(q, "UTF-8")).getBytes(StandardCharsets.UTF_8));
        }
        if (c.getResponseCode() != 200) throw new RuntimeException("Overpass returned HTTP " + c.getResponseCode());
        int kept = 0, skipped = 0;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(args[0], "UTF-8")) {
            out.println("{\n  \"version\": 1,\n  \"updated\": \"" + LocalDate.now() + "\",");
            out.println("  \"source\": \"OpenStreetMap contributors (ODbL)\",");
            out.println("  \"facilities\": [");
            String line;
            boolean first = true;
            while ((line = in.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length < 10 || p[2].isEmpty() || p[3].isEmpty() || p[5].trim().isEmpty()) { skipped++; continue; }
                String kind = p[4].equals("hospital") ? "HOSPITAL" : p[4].equals("fire_station") ? "FIRE" : "POLICE";
                String name = !p[6].isEmpty() ? p[6] : p[5];
                String phone = !p[7].isEmpty() ? p[7] : p[8];
                phone = phone.split(";")[0].trim();
                String id = "osm:" + p[0] + "/" + p[1];
                if (!first) out.println(",");
                first = false;
                out.print("    {\"id\":" + q(id) + ",\"kind\":" + q(kind) + ",\"name\":" + q(name)
                        + ",\"lat\":" + p[2] + ",\"lon\":" + p[3] + ",\"phone\":" + q(phone)
                        + ",\"phoneVerified\":false,\"district\":" + q(p[9])
                        + ",\"source\":\"OpenStreetMap\",\"sourceUrl\":" + q("https://www.openstreetmap.org/" + p[0] + "/" + p[1]) + "}");
                kept++;
            }
            out.println("\n  ]\n}");
        }
        System.out.println("Wrote " + kept + " facilities, skipped " + skipped + " without name/location.");
    }

    private static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20) b.append(' ');
            else b.append(ch);
        }
        return b.append('"').toString();
    }
}
