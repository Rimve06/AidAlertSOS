package com.aidalert.sos.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.aidalert.sos.Config;

import java.util.HashSet;
import java.util.Set;

/** Thin wrapper over SharedPreferences. Every setting has a named accessor to avoid key typos. */
public final class Prefs {
    public static final String ROLE_CITIZEN = "CITIZEN";
    public static final String ROLE_CENTER = "CENTER";

    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("aidalert_prefs", Context.MODE_PRIVATE);
    }

    public static Prefs of(Context c) {
        return new Prefs(c);
    }

    // ------------------------------------------------------------ generic
    private String str(String k, String d) { return sp.getString(k, d); }
    private void putStr(String k, String v) { sp.edit().putString(k, v == null ? "" : v).apply(); }
    private boolean bool(String k, boolean d) { return sp.getBoolean(k, d); }
    private void putBool(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    private long lng(String k, long d) { return sp.getLong(k, d); }
    private void putLng(String k, long v) { sp.edit().putLong(k, v).apply(); }

    // ------------------------------------------------------------ session
    public String uid() { return str("uid", ""); }
    /** The last reason an upload to the server failed ("" when the last one worked). */
    public String syncError() { return str("syncError", ""); }
    public void syncError(String v) { putStr("syncError", v); }
    public String name() { return str("name", ""); }
    public void name(String v) { putStr("name", v == null ? "" : v.trim()); }
    public String phone() { return str("phone", ""); }
    public String role() { return str("role", ROLE_CITIZEN); }
    public boolean isCenter() { return ROLE_CENTER.equals(role()); }
    public boolean isLoggedIn() { return !uid().isEmpty(); }

    public void setSession(String uid, String name, String phone, String role) {
        sp.edit().putString("uid", uid).putString("name", name).putString("phone", phone)
                .putString("role", role).apply();
    }

    /** Clears the account but keeps the server URL and the control-center private key. */
    public void logout() {
        String url = dbUrl();
        String priv = centerPriv();
        sp.edit().clear().apply();
        dbUrl(url);
        centerPriv(priv);
        // No password material is ever stored here: Firebase Auth owns the credentials.
    }

    // ------------------------------------------------------------ server
    public String dbUrl() { return str("dbUrl", Config.DB_URL); }

    public void dbUrl(String v) {
        String s = v == null ? "" : v.trim();
        if (!s.isEmpty() && !s.startsWith("http")) s = "https://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".json")) s = s.substring(0, s.length() - 5);
        putStr("dbUrl", s);
    }

    /** Difference to the server clock (server minus phone), learned from the Date header of server answers. */
    public long clockOffset() { return lng("clockOffset", 0); }
    public long clockAt() { return lng("clockAt", 0); }
    public void clock(long offsetMs, long at) { sp.edit().putLong("clockOffset", offsetMs).putLong("clockAt", at).apply(); }

    // ------------------------------------------------------------ numbers
    public String centerPhone() { return str("centerPhone", ""); }
    public void centerPhone(String v) { putStr("centerPhone", v == null ? "" : v.trim()); }

    /** Number the phone calls automatically on SOS. Falls back to the center phone, then 999. */
    public String callNumber() {
        String n = str("callNumber", "");
        if (!n.isEmpty()) return n;
        if (!centerPhone().isEmpty()) return centerPhone();
        return Config.DEFAULT_EMERGENCY_NUMBER;
    }
    public String callNumberRaw() { return str("callNumber", ""); }
    public void callNumber(String v) { putStr("callNumber", v == null ? "" : v.trim()); }

    public String numAmbulance() { return str("numAmbulance", "999"); }
    public void numAmbulance(String v) { putStr("numAmbulance", v); }
    public String numFire() { return str("numFire", "102"); }
    public void numFire(String v) { putStr("numFire", v); }
    public String numPolice() { return str("numPolice", "999"); }
    public void numPolice(String v) { putStr("numPolice", v); }
    public String numRescue() { return str("numRescue", "999"); }
    public void numRescue(String v) { putStr("numRescue", v); }

    /** Optional LAN address of the control center phone (socket channel). Empty = Wi-Fi gateway. */
    public String lanHost() { return str("lanHost", ""); }
    public void lanHost(String v) { putStr("lanHost", v == null ? "" : v.trim()); }

    /** XML alert feed (RSS / Atom / CAP-like) published by the control center. */
    public String feedUrl() { return str("feedUrl", ""); }
    public void feedUrl(String v) { putStr("feedUrl", v == null ? "" : v.trim()); }

    // ------------------------------------------------------------ keys (hybrid encryption)
    public String centerPub() { return str("centerPub", ""); }
    public void centerPub(String v) { putStr("centerPub", v); }
    public String centerPriv() { return str("centerPriv", ""); }
    public void centerPriv(String v) { putStr("centerPriv", v); }

    // ------------------------------------------------------------ switches
    public boolean autoCall() { return bool("autoCall", true); }
    public void autoCall(boolean v) { putBool("autoCall", v); }
    /** Accident detection (crash / hard fall + stillness), off until the user turns it on. */
    public boolean impactOn() { return bool("impactOn", false); }
    public void impactOn(boolean v) { putBool("impactOn", v); }
    public boolean onboarded() { return bool("onboarded", false); }
    public void onboarded(boolean v) { putBool("onboarded", v); }
    public boolean guardOn() { return bool("guardOn", true); }
    public void guardOn(boolean v) { putBool("guardOn", v); }
    public boolean relayOn() { return bool("relayOn", true); }
    public void relayOn(boolean v) { putBool("relayOn", v); }
    public boolean distressOn() { return bool("distressOn", false); }
    public void distressOn(boolean v) { putBool("distressOn", v); }
    public boolean helperOn() { return bool("helperOn", false); }
    public void helperOn(boolean v) { putBool("helperOn", v); }
    public boolean permsShown() { return bool("permsShown", false); }
    public void permsShown(boolean v) { putBool("permsShown", v); }

    // ------------------------------------------------------------ profile
    public String area() { return str("area", ""); }
    public void area(String v) { putStr("area", v); }
    public String bio() { return str("bio", ""); }
    public void bio(String v) { putStr("bio", v); }
    public String blood() { return str("blood", ""); }
    public void blood(String v) { putStr("blood", v); }
    public String medicalNote() { return str("medicalNote", ""); }
    public void medicalNote(String v) { putStr("medicalNote", v); }
    public boolean searchable() { return bool("searchable", true); }
    public void searchable(boolean v) { putBool("searchable", v); }
    public boolean showPhone() { return bool("showPhone", false); }
    public void showPhone(boolean v) { putBool("showPhone", v); }
    public boolean includeMedical() { return bool("includeMedical", true); }
    public void includeMedical(boolean v) { putBool("includeMedical", v); }

    // ------------------------------------------------------------ active SOS
    public String activeId() { return str("activeId", ""); }
    public boolean activeSilent() { return bool("activeSilent", false); }
    public long activeStarted() { return lng("activeStarted", 0); }

    public void setActive(String id, boolean silent) {
        sp.edit().putString("activeId", id).putBoolean("activeSilent", silent)
                .putLong("activeStarted", System.currentTimeMillis()).apply();
    }

    public void clearActive() {
        sp.edit().putString("activeId", "").putBoolean("activeSilent", false).apply();
    }

    public boolean sosActive() { return !activeId().isEmpty(); }

    /** Incident id for which contacts were already texted / the call was already placed. */
    public String notifiedFor() { return str("notifiedFor", ""); }
    public void notifiedFor(String id) { putStr("notifiedFor", id); }

    // ------------------------------------------------------------ sync markers
    public long lastCenterMsg() { return lng("lastCenterMsg", 0); }
    public void lastCenterMsg(long v) { putLng("lastCenterMsg", v); }
    public long lastBroadcast() { return lng("lastBroadcast", 0); }
    public void lastBroadcast(long v) { putLng("lastBroadcast", v); }
    public long lastRecordTs() { return lng("lastRecordTs", 0); }
    public void lastRecordTs(long v) { putLng("lastRecordTs", v); }
    public String alertsCache() { return str("alertsCache", "[]"); }
    public void alertsCache(String v) { putStr("alertsCache", v); }
    public long alertsCacheAt() { return lng("alertsCacheAt", 0); }
    public void alertsCacheAt(long v) { putLng("alertsCacheAt", v); }
    public String newsCache() { return str("newsCache", "[]"); }
    public void newsCache(String v) { putStr("newsCache", v); }
    public long newsCacheAt() { return lng("newsCacheAt", 0); }
    public void newsCacheAt(long v) { putLng("newsCacheAt", v); }
    public String statsHistory() { return str("statsHistory", ""); }
    public void statsHistory(String v) { putStr("statsHistory", v); }
    public String statsCache() { return str("statsCache", ""); }
    public void statsCache(String v) { putStr("statsCache", v); }
    /** Groq API key: the one the user typed (private app storage), otherwise the build-time default from BuildConfig. */
    public String groqKey() {
        String own = str("groqKey", "");
        // a stored value that is not a real Groq key (for example text pasted into the wrong box) is ignored
        return own.trim().startsWith("gsk_") ? own.trim() : com.aidalert.sos.BuildConfig.GROQ_API_KEY;
    }
    public void groqKey(String v) { putStr("groqKey", v == null ? "" : v.trim()); }
    /** The chat model to use. A saved model that Groq has since shut down is ignored. */
    public String groqModel() {
        String m = str("groqModel", "");
        return com.aidalert.sos.ai.ChatRules.isRetired(m) ? com.aidalert.sos.ai.ChatRules.DEFAULT_MODEL : m;
    }
    public void groqModel(String v) {
        putStr("groqModel", com.aidalert.sos.ai.ChatRules.isRetired(v) ? com.aidalert.sos.ai.ChatRules.DEFAULT_MODEL : v.trim());
    }
    public String groqVisionModel() { return str("groqVisionModel", "meta-llama/llama-4-scout-17b-16e-instruct"); }
    public String alertRegion() { return str("alertRegion", ""); }
    public void alertRegion(String v) { putStr("alertRegion", v); }
    public long lastPostAt() { return lng("lastPostAt", 0); }
    public void lastPostAt(long v) { putLng("lastPostAt", v); }
    public long lastCheckin() { return lng("lastCheckin", 0); }
    public void lastCheckin(long v) { putLng("lastCheckin", v); }
    public long lastIncidentSeen() { return lng("lastIncidentSeen", 0); }
    public void lastIncidentSeen(long v) { putLng("lastIncidentSeen", v); }

    /** Opt-in: notify about new missing-person reports on the public sites (off until the user turns it on). */
    public boolean missingWatch() { return bool("missingWatch", false); }
    public void missingWatch(boolean v) { putBool("missingWatch", v); }
    public boolean missingBaselineDone() { return bool("missingBaseline", false); }
    public void missingBaselineDone(boolean v) { putBool("missingBaseline", v); }
    public long missingLastCheck() { return lng("missingLastCheck", 0); }
    public void missingLastCheck(long v) { putLng("missingLastCheck", v); }

    public void setSeen(String key, Set<String> ids) {
        sp.edit().putStringSet("seen_" + key, new HashSet<>(ids)).apply();
    }

    public Set<String> seen(String key) {
        return new HashSet<>(sp.getStringSet("seen_" + key, new HashSet<String>()));
    }

    public void addSeen(String key, String id) {
        Set<String> s = seen(key);
        s.add(id);
        sp.edit().putStringSet("seen_" + key, s).apply();
    }
}
