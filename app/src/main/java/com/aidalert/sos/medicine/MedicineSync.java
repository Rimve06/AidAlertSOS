package com.aidalert.sos.medicine;

import android.content.Context;
import android.content.SharedPreferences;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps each account's medicines in the Realtime Database under medicines/{uid}. The phone works first
 * (SQLite + alarms); every change is uploaded as soon as there is internet, and a newer server copy is pulled down.
 */
public final class MedicineSync {
    private MedicineSync() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("med_sync", Context.MODE_PRIVATE);
    }

    private static String key(Context c, String name) {
        return name + "_" + Prefs.of(c).uid();
    }

    /** Plain-words state of the cloud backup, shown on the Medicine screen. */
    public static String status(Context c) {
        return sp(c).getString(key(c, "status"), "Cloud backup: waiting for internet");
    }

    private static void setStatus(Context c, String s) {
        sp(c).edit().putString(key(c, "status"), s).apply();
    }

    /** A medicine was added, changed or removed: mark dirty and upload right away if online. */
    public static void changed(Context c) {
        final Context app = c.getApplicationContext();
        Prefs p = Prefs.of(app);
        if (!p.isLoggedIn() || p.isCenter()) return;
        sp(app).edit().putBoolean(key(app, "dirty"), true).apply();
        if (NetUtil.isOnline(app)) AppExecutors.io(() -> sync(app));
    }

    /** PUSH, PULL or NOTHING. Pure, so it is unit-testable. */
    public static String decide(boolean dirty, boolean initialised, boolean remoteExists, long remoteTs, long lastSyncedTs) {
        if (dirty || !initialised) return "PUSH";
        if (!remoteExists) return "PUSH";
        return remoteTs != lastSyncedTs ? "PULL" : "NOTHING";
    }

    static JSONArray toJson(List<MedicineStore.Med> list) throws JSONException {
        JSONArray a = new JSONArray();
        for (MedicineStore.Med m : list) {
            JSONObject o = new JSONObject();
            o.put("name", m.name).put("dose", m.dose).put("times", m.times).put("note", m.note)
                    .put("days", m.days).put("startMs", m.startMs).put("endMs", m.endMs).put("active", m.active)
                    .put("strength", m.strength).put("unit", m.unit).put("frequency", m.frequency).put("food", m.food);
            a.put(o);
        }
        return a;
    }

    static List<MedicineStore.Med> fromJson(JSONArray a) {
        List<MedicineStore.Med> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            MedicineStore.Med m = new MedicineStore.Med();
            m.name = o.optString("name", "");
            if (m.name.isEmpty()) continue;
            m.dose = o.optString("dose", "");
            m.times = o.optString("times", "08:00");
            m.note = o.optString("note", "");
            m.days = o.optInt("days", 0);
            m.startMs = o.optLong("startMs", 0);
            m.endMs = o.optLong("endMs", 0);
            m.active = o.optBoolean("active", true);
            m.strength = o.optString("strength", "");
            m.unit = o.optString("unit", "");
            m.frequency = o.optString("frequency", "");
            m.food = o.optString("food", "");
            out.add(m);
        }
        return out;
    }

    /** One round: upload local changes, or download a newer server copy. Background thread only. */
    public static synchronized void sync(Context c) {
        Context app = c.getApplicationContext();
        Prefs p = Prefs.of(app);
        if (!p.isLoggedIn() || p.isCenter() || !NetUtil.isOnline(app)) return;
        SharedPreferences sp = sp(app);
        String uid = p.uid();
        FirebaseRest rest = new FirebaseRest(app);
        FirebaseRest.Result r = rest.get("medicines/" + uid);
        if (!r.ok()) {                             // refused or offline: next round
            setStatus(app, "Cloud backup FAILED: " + com.aidalert.sos.blood.AccessInfo.explain("Medicine backup", r.code, r.body,
                    rest.configured(), com.aidalert.sos.blood.AccessInfo.fromToken(com.aidalert.sos.auth.AuthManager.idToken(false)))
                    + " (publish database.rules.json in the Firebase console)");
            return;
        }
        JSONObject remote = r.obj();
        boolean dirty = sp.getBoolean(key(app, "dirty"), false);
        boolean init = sp.getBoolean(key(app, "init"), false);
        long lastTs = sp.getLong(key(app, "ts"), 0);
        String action = decide(dirty, init, remote != null, remote == null ? 0 : remote.optLong("updatedAt", 0), lastTs);
        MedicineStore store = new MedicineStore(app);
        try {
            if ("PUSH".equals(action)) {
                long now = System.currentTimeMillis();
                JSONObject body = new JSONObject().put("updatedAt", now).put("items", toJson(store.all(uid)));
                if (!rest.put("medicines/" + uid, body)) {
                    setStatus(app, "Cloud backup FAILED: could not upload. Publish database.rules.json in the Firebase console.");
                } else {
                    setStatus(app, "Cloud backup OK (" + store.all(uid).size() + " medicines saved in Firebase)");
                    sp.edit().putBoolean(key(app, "dirty"), false).putBoolean(key(app, "init"), true).putLong(key(app, "ts"), now).apply();
                }
            } else if ("PULL".equals(action) && remote != null) {
                MedicineScheduler.cancelAll(app);
                store.replaceFor(uid, fromJson(remote.optJSONArray("items")));
                sp.edit().putBoolean(key(app, "init"), true).putLong(key(app, "ts"), remote.optLong("updatedAt", 0)).apply();
                MedicineScheduler.rescheduleAll(app);
            } else {
                setStatus(app, "Cloud backup OK (up to date)");
            }
        } catch (JSONException | RuntimeException ignored) {
            // retried next round
        }
    }
}
