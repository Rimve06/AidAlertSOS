package com.aidalert.sos.net;

import android.content.Context;

import com.aidalert.sos.auth.AuthManager;
import com.aidalert.sos.blood.AccessInfo;
import com.aidalert.sos.data.Prefs;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Cloud Firestore client over the REST API (HttpURLConnection + org.json, like FirebaseRest). The signed-in
 * user's Firebase ID token is sent as "Authorization: Bearer ..." so firestore.rules decide what is allowed.
 * Used for the blood donor list. Always call from a background thread.
 */
public final class FirestoreRest {
    private final Prefs prefs;

    public FirestoreRest(Context c) {
        prefs = Prefs.of(c);
    }

    /** The Firebase project id: taken from the login token, else from the database address. */
    public static String projectOf(String token, String dbUrl) {
        String p = AccessInfo.fromToken(token).project;
        if (!p.isEmpty()) return p;
        String label = AccessInfo.hostLabel(dbUrl);
        return label.endsWith("-default-rtdb") ? label.substring(0, label.length() - "-default-rtdb".length()) : label;
    }

    private String base(String token) {
        String project = projectOf(token, prefs.dbUrl());
        return project.isEmpty() ? "" : "https://firestore.googleapis.com/v1/projects/" + project + "/databases/(default)/documents";
    }

    private FirebaseRest.Result call(String method, String pathAndQuery, String body, boolean fresh) {
        String token = AuthManager.idToken(fresh);
        if (token.isEmpty()) return new FirebaseRest.Result(-1, "could not get your login token (no internet, or you are signed out)");
        String base = base(token);
        if (base.isEmpty()) return new FirebaseRest.Result(-2, "no project");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base + pathAndQuery).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            return new FirebaseRest.Result(code, is == null ? "" : readAll(is));
        } catch (IOException e) {
            return new FirebaseRest.Result(-1, String.valueOf(e.getMessage()));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** A refusal is repeated once with a fresh token (the cached one may be old). */
    private FirebaseRest.Result request(String method, String pathAndQuery, String body) {
        FirebaseRest.Result r = call(method, pathAndQuery, body, false);
        if (r.code == 401 || r.code == 403) {
            FirebaseRest.Result again = call(method, pathAndQuery, body, true);
            if (again.code != -1) return again;
        }
        return r;
    }

    private static String readAll(InputStream is) throws IOException {
        try (InputStream in = is) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    /**
     * Every document of a collection as {id: {fields}} in the body (the same shape the app reads from the Realtime
     * Database); an empty collection gives "null". Reads up to 5 pages of 300.
     */
    public FirebaseRest.Result list(String collection) {
        JSONObject node = new JSONObject();
        String token = "";
        try {
            for (int page = 0; page < 5; page++) {
                String q = "/" + collection + "?pageSize=300" + (token.isEmpty() ? "" : "&pageToken=" + URLEncoder.encode(token, "UTF-8"));
                FirebaseRest.Result r = request("GET", q, null);
                if (!r.ok()) return r;
                token = FirestoreJson.nextPageToken(r.body, node);
                if (token.isEmpty()) break;
            }
        } catch (JSONException | IOException e) {
            return new FirebaseRest.Result(-1, "could not read the Firestore answer: " + e.getMessage());
        }
        return new FirebaseRest.Result(200, node.length() == 0 ? "null" : node.toString());
    }

    /** One document as flat JSON in the body; a missing document gives 200 and "null". */
    public FirebaseRest.Result get(String collection, String id) {
        FirebaseRest.Result r = request("GET", "/" + collection + "/" + id, null);
        if (r.code == 404 && !r.body.toLowerCase().contains("database")) return new FirebaseRest.Result(200, "null");
        if (!r.ok()) return r;
        try {
            JSONObject flat = FirestoreJson.fromDocument(new JSONObject(r.body));
            flat.remove("_id");
            return new FirebaseRest.Result(200, flat.toString());
        } catch (JSONException | NumberFormatException e) {
            return new FirebaseRest.Result(-1, "could not read the Firestore answer");
        }
    }

    /** Creates or replaces the document. */
    public FirebaseRest.Result put(String collection, String id, JSONObject flat) {
        try {
            return request("PATCH", "/" + collection + "/" + id, FirestoreJson.toDocument(flat).toString());
        } catch (JSONException e) {
            return new FirebaseRest.Result(-1, "could not build the document");
        }
    }

    /** Deletes the document (deleting one that does not exist counts as success). */
    public FirebaseRest.Result delete(String collection, String id) {
        FirebaseRest.Result r = request("DELETE", "/" + collection + "/" + id, null);
        if (r.code == 404 && !r.body.toLowerCase().contains("database")) return new FirebaseRest.Result(200, "{}");
        return r;
    }
}
