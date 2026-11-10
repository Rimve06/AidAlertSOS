package com.aidalert.sos.net;

import android.content.Context;

import com.aidalert.sos.auth.AuthManager;
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

        public Result(int code, String body) {
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

    /**
     * One request. A refusal (401/403) is repeated once with a freshly downloaded ID token: the token kept on the phone
     * can be up to an hour old and then lacks "email verified" / "phone number" even though the account has them now.
     */
    public Result request(String method, String path, String query, String body) {
        Result r = requestOnce(method, path, query, body, false);
        if (r.code == 401 || r.code == 403) {
            Result again = requestOnce(method, path, query, body, true);
            if (again.code != -1) return again;
        }
        return r;
    }

    /** Learns the server clock now when it is not known or older than 30 minutes. Returns the current offset in ms. */
    public long measureClock(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && prefs.clockAt() != 0 && now - prefs.clockAt() < 30 * 60 * 1000L) return prefs.clockOffset();
        if (!configured()) return prefs.clockOffset();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(prefs.dbUrl() + "/.json?shallow=true").openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.getResponseCode();
            noteDate(conn);
        } catch (IOException ignored) {
            // offline: the old value stays
        } finally {
            if (conn != null) conn.disconnect();
        }
        return prefs.clockOffset();
    }

    private void noteDate(HttpURLConnection conn) {
        long server = conn.getDate();
        if (server > 0) {
            long phone = System.currentTimeMillis();
            prefs.clock(ServerClock.offsetMs(server, phone), phone);
        }
    }

    private Result requestOnce(String method, String path, String query, String body, boolean freshToken) {
        if (!configured()) return new Result(-2, "Server URL is not set");
        HttpURLConnection conn = null;
        try {
            if (body != null && !"DELETE".equals(method)) {
                measureClock(false);
                body = ServerClock.fixTs(body, prefs.clockOffset());
            }
            StringBuilder u = new StringBuilder(prefs.dbUrl()).append('/').append(path).append(".json");
            char sep = '?';
            if (query != null && !query.isEmpty()) {
                u.append(sep).append(query);
                sep = '&';
            }
            // Firebase ID token of the signed-in user; the database rules decide what it may touch.
            String token = AuthManager.idToken(freshToken);
            if (token.isEmpty() && prefs.isLoggedIn()) token = AuthManager.idToken(true);
            if (token.isEmpty() && prefs.isLoggedIn()) {
                return new Result(-1, "could not get your login token (no internet, or you are signed out)");
            }
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
            noteDate(conn);
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
        return postResult(path, o).key();
    }

    /** Like post but keeps the server's answer, so the screen can say exactly why it was refused. */
    public PostResult postResult(String path, JSONObject o) {
        Result r = request("POST", path, null, o.toString());
        return new PostResult(r);
    }

    public static final class PostResult {
        public final Result result;

        PostResult(Result r) {
            result = r;
        }

        public boolean ok() {
            return result.ok();
        }

        /** The new child key, or null when the post failed. */
        public String key() {
            JSONObject j = result.obj();
            return j == null ? null : j.optString("name", null);
        }
    }

    public boolean delete(String path) {
        return request("DELETE", path, null, null).ok();
    }
}
