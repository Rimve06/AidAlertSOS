package com.aidalert.sos.blood;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Reads the two facts the Firebase database rules look at (email verified, phone number present) from the
 * signed-in user's ID token, and turns a refused server answer into one plain sentence saying what to fix.
 * Pure Java.
 */
public final class AccessInfo {
    public final boolean known, emailVerified, hasPhone;
    /** Firebase project the login token belongs to (its "aud" claim), "" when unknown. */
    public final String project;

    private AccessInfo(boolean known, boolean emailVerified, boolean hasPhone, String project) {
        this.known = known;
        this.emailVerified = emailVerified;
        this.hasPhone = hasPhone;
        this.project = project == null ? "" : project;
    }

    public static AccessInfo unknown() {
        return new AccessInfo(false, false, false, "");
    }

    /** First part of a database address: "https://abc-default-rtdb.firebaseio.com" gives "abc-default-rtdb". */
    public static String hostLabel(String dbUrl) {
        if (dbUrl == null) return "";
        String h = dbUrl.trim().replaceFirst("^https?://", "");
        int slash = h.indexOf('/');
        if (slash >= 0) h = h.substring(0, slash);
        int dot = h.indexOf('.');
        return dot > 0 ? h.substring(0, dot) : h;
    }

    /** True when the database address belongs to the project of the login token (or when either is unknown). */
    public static boolean sameProject(String project, String dbUrl) {
        if (project == null || project.isEmpty()) return true;
        String label = hostLabel(dbUrl);
        return label.isEmpty() || label.equals(project) || label.startsWith(project + "-");
    }

    /** From a JWT such as "xxx.yyy.zzz"; unknown when it is empty or cannot be read. */
    public static AccessInfo fromToken(String jwt) {
        if (jwt == null) return unknown();
        String[] p = jwt.split("\\.");
        if (p.length < 2) return unknown();
        try {
            String json = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8);
            JSONObject o = new JSONObject(json);
            String phone = o.optString("phone_number", "");
            return new AccessInfo(true, o.optBoolean("email_verified", false), !phone.isEmpty(), o.optString("aud", ""));
        } catch (IllegalArgumentException | JSONException e) {
            return unknown();
        }
    }

    /** True when the database rules (email verified AND phone) are met by this account. */
    public boolean verified() {
        return known && emailVerified && hasPhone;
    }

    /** The server's own words (for example "Permission denied"), shortened. */
    static String serverSaid(String body) {
        if (body == null) return "";
        String b = body.replaceAll("\\s+", " ").trim();
        if (b.isEmpty() || b.equals("null")) return "";
        return " Server said: " + (b.length() > 160 ? b.substring(0, 160) + "..." : b);
    }

    /**
     * What went wrong, in words a person can act on. code: the HTTP code (-1 no connection, -2 server address not set).
     * what: "Sync", "Saving your donor profile"...
     */
    public static String explain(String what, int code, String body, boolean configured, AccessInfo a) {
        if (!configured || code == -2) return what + " failed: the server address is not set (Settings > Server).";
        if (code == -1) return what + " failed: no connection to the server (" + (body == null ? "" : body) + ").";
        if (code == 401 || code == 403) {
            String why;
            if (a == null || !a.known) {
                why = "You may be signed out, or not verified, or the database rules are not published.";
            } else if (!a.emailVerified && !a.hasPhone) {
                why = "Your email AND your phone number are not verified yet.";
            } else if (!a.emailVerified) {
                why = "Your email is not verified yet. Open the link in the verification email, then reopen the app.";
            } else if (!a.hasPhone) {
                why = "Your phone number is not verified yet. Verify it in your profile.";
            } else {
                why = "Your account is verified, so the database rules in the Firebase console are the likely cause: open Realtime Database > Rules, paste the newest database.rules.json and press Publish.";
            }
            return what + " refused by the server (" + code + "). " + why + serverSaid(body);
        }
        return what + " failed (server answered " + code + ")." + serverSaid(body);
    }
}
