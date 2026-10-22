package com.aidalert.sos.net;

import android.content.Context;

import com.aidalert.sos.data.Prefs;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Firebase Realtime Database client that speaks the REST API with HttpURLConnection and
 * org.json (course topics: basic networking, HTTP/API requests, JSON parsing).
 * Always call from a background thread (see AppExecutors.io).
 *
 *   GET    {db}/{path}.json     read
 *   PUT    {db}/{path}.json     write / replace
 *   POST   {db}/{path}.json     push a new child, returns {"name": "-key"}
 *   DELETE {db}/{path}.json     remove
 */
public final class FirebaseRest {

    public static final class Result {
        public final int code;
        public final String body;

        Result(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }

        public boolean ok() {
            return code >= 200 && code < 300;
        }

        /** Parsed JSON value, or null for failures and for Firebase's "null". */
        public Object json() {
            if (!ok()) return null;
            String t = body.trim();
            if (t.isEmpty() || t.equals("null")) return null;
            try {
                return new JSONTokener(t).nextValue();
            } catch (JSONException e) {
                return null;
            }
        }

        public JSONObject obj() {
            Object o = json();
            return o instanceof JSONObject ? (JSONObject) o : null;
        }
    }

    private final Prefs prefs;

    public FirebaseRest(Context c) {
        prefs = Prefs.of(c);
    }

    public boolean configured() {
        return !prefs.dbUrl().isEmpty();
    }

    public Result request(String method, String path, String query, String body) {
        if (!configured()) return new Result(-2, "Server URL is not set");
        HttpURLConnection conn = null;
        try {
            StringBuilder u = new StringBuilder(prefs.dbUrl()).append('/').append(path).append(".json");
            char sep = '?';
            if (query != null && !query.isEmpty()) {
                u.append(sep).append(query);
                sep = '&';
            }
            String token = prefs.authToken();
            if (!token.isEmpty()) {
                u.append(sep).append("auth=").append(URLEncoder.encode(token, "UTF-8"));
            }
            conn = (HttpURLConnection) new URL(u.toString()).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestMethod(method);
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
            return new Result(code, is == null ? "" : readAll(is));
        } catch (IOException e) {
            return new Result(-1, String.valueOf(e.getMessage()));
        } finally {
            if (conn != null) conn.disconnect();
        }
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

    // ------------------------------------------------------------ convenience

    public Result get(String path) {
        return request("GET", path, null, null);
    }

    public JSONObject getObj(String path) {
        return get(path).obj();
    }

    /** Query string must already be URL-encoded, e.g. orderBy=%22%24key%22&limitToLast=1 */
    public JSONObject getQuery(String path, String query) {
        return request("GET", path, query, null).obj();
    }

    /** Only the child keys of a node (cheap way to list thread names). */
    public JSONObject getShallow(String path) {
        return request("GET", path, "shallow=true", null).obj();
    }

    public boolean put(String path, JSONObject o) {
        return request("PUT", path, null, o.toString()).ok();
    }

    public boolean putString(String path, String value) {
        return request("PUT", path, null, JSONObject.quote(value)).ok();
    }

    /** Pushes a child with a Firebase-generated key; returns the key or null on failure. */
    public String post(String path, JSONObject o) {
        Result r = request("POST", path, null, o.toString());
        JSONObject j = r.obj();
        return j == null ? null : j.optString("name", null);
    }

    public boolean delete(String path) {
        return request("DELETE", path, null, null).ok();
    }
}
