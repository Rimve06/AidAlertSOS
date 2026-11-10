package com.aidalert.sos.net;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Cloud Firestore REST speaks a typed JSON ({"fields":{"name":{"stringValue":"Rahim"}}}). This class converts between that
 * and the plain flat JSON the rest of the app uses, and explains Firestore errors in plain words. Pure Java.
 */
public final class FirestoreJson {
    private FirestoreJson() {}

    /** Flat JSON to a Firestore document body. Supports string, boolean, whole and decimal numbers. */
    public static JSONObject toDocument(JSONObject flat) throws JSONException {
        JSONObject fields = new JSONObject();
        Iterator<String> it = flat.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = flat.get(k);
            JSONObject typed = new JSONObject();
            if (v instanceof Boolean) {
                typed.put("booleanValue", v);
            } else if (v instanceof Integer || v instanceof Long) {
                typed.put("integerValue", String.valueOf(((Number) v).longValue()));
            } else if (v instanceof Number) {
                typed.put("doubleValue", ((Number) v).doubleValue());
            } else if (v == JSONObject.NULL) {
                typed.put("nullValue", JSONObject.NULL);
            } else {
                typed.put("stringValue", String.valueOf(v));
            }
            fields.put(k, typed);
        }
        return new JSONObject().put("fields", fields);
    }

    /** A Firestore document to flat JSON, with "_id" set to the last part of its name. */
    public static JSONObject fromDocument(JSONObject doc) throws JSONException {
        JSONObject out = new JSONObject();
        JSONObject fields = doc.optJSONObject("fields");
        if (fields != null) {
            Iterator<String> it = fields.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject t = fields.optJSONObject(k);
                if (t == null) continue;
                if (t.has("stringValue")) out.put(k, t.getString("stringValue"));
                else if (t.has("booleanValue")) out.put(k, t.getBoolean("booleanValue"));
                else if (t.has("integerValue")) out.put(k, Long.parseLong(t.getString("integerValue")));
                else if (t.has("doubleValue")) out.put(k, t.getDouble("doubleValue"));
                else if (t.has("timestampValue")) out.put(k, t.getString("timestampValue"));
                // null, maps and arrays are not used by the donor records and are left out
            }
        }
        String name = doc.optString("name", "");
        out.put("_id", name.substring(name.lastIndexOf('/') + 1));
        return out;
    }

    /** One page of a list answer as {id: {flat fields}} (the shape the app already reads), added into node. */
    public static String nextPageToken(String listBody, JSONObject node) throws JSONException {
        JSONObject o = new JSONObject(listBody.trim().isEmpty() ? "{}" : listBody);
        JSONArray docs = o.optJSONArray("documents");
        if (docs != null) {
            for (int i = 0; i < docs.length(); i++) {
                JSONObject d = docs.optJSONObject(i);
                if (d == null) continue;
                JSONObject flat = fromDocument(d);
                String id = flat.optString("_id");
                flat.remove("_id");
                if (!id.isEmpty()) node.put(id, flat);
            }
        }
        return o.optString("nextPageToken", "");
    }

    /** What went wrong with a Firestore call, in words a person can act on. */
    public static String explain(String what, int code, String body) {
        String b = body == null ? "" : body;
        String l = b.toLowerCase();
        String said = b.replaceAll("\\s+", " ").trim();
        if (said.length() > 200) said = said.substring(0, 200) + "...";
        said = said.isEmpty() ? "" : " Server said: " + said;
        if (code == -2) return what + " failed: no Firebase project is known yet. Sign in again.";
        if (code == -1) return what + " failed: no connection to Firestore (" + b + ").";
        if (l.contains("has not been used") || l.contains("service_disabled") || l.contains("is disabled")
                || (code == 404 && (l.contains("does not exist") || l.contains("not_found")))) {
            return what + " failed: Cloud Firestore is not created in your Firebase project yet. Open Firebase console > Build > "
                    + "Firestore Database > Create database, then publish firestore.rules (Rules tab)." + said;
        }
        if (code == 401) return what + " refused: your login was not accepted. Sign out and sign in again." + said;
        if (code == 403) {
            return what + " refused by the Firestore rules. Open Firebase console > Build > Firestore Database > Rules, paste the "
                    + "whole firestore.rules file from the project and press Publish." + said;
        }
        return what + " failed (Firestore answered " + code + ")." + said;
    }
}
