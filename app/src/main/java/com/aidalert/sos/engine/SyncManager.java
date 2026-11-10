package com.aidalert.sos.engine;

import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.aidalert.sos.data.Db;
import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.Crypto;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.DisasterRespondActivity;
import com.aidalert.sos.ui.HelpRequestActivity;
import com.aidalert.sos.ui.RecordsActivity;
import com.aidalert.sos.ui.ChatActivity;
import com.aidalert.sos.ui.center.CenterHomeActivity;
import com.aidalert.sos.util.Geo;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.Notifier;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pulls server-side changes (messages, broadcasts, records, disasters, help requests) every few
 * seconds and turns them into notifications. Runs on a background thread inside the guard service.
 * A tiny polling design keeps the course scope (HTTP + JSON) without needing push services.
 */
public final class SyncManager {
    private SyncManager() {}

    /** Newest-N query on push-ids, which sort by time and need no database index. */
    public static final String LAST_20 = "orderBy=%22%24key%22&limitToLast=20";
    public static final String LAST_10 = "orderBy=%22%24key%22&limitToLast=10";
    public static final String LAST_50 = "orderBy=%22%24key%22&limitToLast=50";

    public static synchronized void syncNow(Context app) {
        Prefs p = Prefs.of(app);
        if (!p.isLoggedIn() || !NetUtil.isOnline(app)) return;
        FirebaseRest rest = new FirebaseRest(app);
        if (!rest.configured()) return;
        if (p.isCenter()) syncCenter(app, rest);
        else syncCitizen(app, rest, p);
    }

    // ================================================================ citizen

    private static void syncCitizen(Context app, FirebaseRest rest, Prefs p) {
        Dispatcher.flushPending(app);
        readCenterConfig(rest, p);
        checkCenterMessages(app, rest, p);
        checkBroadcasts(app, rest, p);
        checkRecords(app, rest, p);
        checkDisasters(app, rest, p);
        if (p.helperOn()) checkHelpRequests(app, rest, p);
    }

    /** Public key + phone numbers published by the control center. */
    public static void readCenterConfig(FirebaseRest rest, Prefs p) {
        JSONObject cfg = rest.getObj("config/center");
        if (cfg == null) return;
        String pub = cfg.optString("pub", "");
        if (!pub.isEmpty()) p.centerPub(pub);
        String phone = cfg.optString("phone", "");
        if (!phone.isEmpty()) p.centerPhone(phone);
        if (cfg.has("feedUrl")) p.feedUrl(cfg.optString("feedUrl", ""));
        if (cfg.has("ambulance")) p.numAmbulance(cfg.optString("ambulance", "999"));
        if (cfg.has("fire")) p.numFire(cfg.optString("fire", "102"));
        if (cfg.has("police")) p.numPolice(cfg.optString("police", "999"));
        if (cfg.has("rescue")) p.numRescue(cfg.optString("rescue", "999"));
    }

    private static void checkCenterMessages(Context app, FirebaseRest rest, Prefs p) {
        JSONObject msgs = rest.getQuery("msgs/" + p.uid(), LAST_20);
        if (msgs == null) return;
        long newest = p.lastCenterMsg();
        String text = null;
        Iterator<String> it = msgs.keys();
        while (it.hasNext()) {
            JSONObject m = msgs.optJSONObject(it.next());
            if (m == null || !"CENTER".equals(m.optString("from"))) continue;
            long ts = m.optLong("ts", 0);
            if (ts > p.lastCenterMsg()) {
                if (ts > newest) {
                    newest = ts;
                    text = m.optString("text", "");
                }
            }
        }
        if (text != null && p.lastCenterMsg() != 0) {
            Intent i = new Intent(app, ChatActivity.class)
                    .putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_CENTER);
            Notifier.show(app, Notifier.ID_CENTER_MSG, Notifier.CH_MSG, "Message from control center", text, i);
        }
        if (newest != p.lastCenterMsg()) p.lastCenterMsg(newest);
        if (p.lastCenterMsg() == 0) p.lastCenterMsg(System.currentTimeMillis());
    }

    private static void checkBroadcasts(Context app, FirebaseRest rest, Prefs p) {
        JSONObject all = rest.getQuery("broadcasts", LAST_10);
        if (all == null) return;
        long newest = p.lastBroadcast();
        String text = null;
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            JSONObject b = all.optJSONObject(it.next());
            if (b == null) continue;
            long ts = b.optLong("ts", 0);
            if (ts > newest) {
                newest = ts;
                text = b.optString("text", "");
            }
        }
        if (text != null && p.lastBroadcast() != 0) {
            Notifier.show(app, Notifier.ID_BROADCAST, Notifier.CH_ALERT, "Announcement for all users", text,
                    new Intent(app, ChatActivity.class).putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_CENTER));
        }
        p.lastBroadcast(newest == 0 ? System.currentTimeMillis() : newest);
    }

    /** Missing-person / wanted-criminal list is copied into every phone's local SQLite database. */
    private static void checkRecords(Context app, FirebaseRest rest, Prefs p) {
        JSONObject all = rest.getObj("records");
        List<Db.Record> list = new ArrayList<>();
        long newest = 0;
        String newName = null;
        if (all != null) {
            Iterator<String> it = all.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject o = all.optJSONObject(k);
                if (o == null) continue;
                Db.Record r = new Db.Record();
                r.id = k;
                r.kind = o.optString("kind", "MISSING");
                r.name = o.optString("name", "");
                r.age = o.optString("age", "");
                r.description = o.optString("description", "");
                r.lastSeen = o.optString("lastSeen", "");
                r.ts = o.optLong("ts", 0);
                r.authority = o.optString("authority", "");
                r.ref = o.optString("ref", "");
                r.sourceUrl = o.optString("sourceUrl", "");
                list.add(r);
                if (r.ts > newest) {
                    newest = r.ts;
                    newName = r.kind + ": " + r.name;
                }
            }
        }
        Db.get(app).replaceRecords(list);
        if (newest > p.lastRecordTs()) {
            if (p.lastRecordTs() != 0 && newName != null) {
                Notifier.show(app, Notifier.ID_RECORD, Notifier.CH_ALERT, "New alert on the records list", newName,
                        new Intent(app, RecordsActivity.class).putExtra(RecordsActivity.EXTRA_KIND, newName.startsWith("WANTED") ? "WANTED" : "MISSING"));
            }
            p.lastRecordTs(newest);
        }
    }

    private static void checkDisasters(Context app, FirebaseRest rest, Prefs p) {
        JSONObject all = rest.getQuery("disasters", LAST_10);
        if (all == null) return;
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject d = all.optJSONObject(k);
            if (d == null || !d.optBoolean("active", false)) continue;
            if (p.seen("disaster").contains(k)) continue;
            p.addSeen("disaster", k);
            Intent i = new Intent(app, DisasterRespondActivity.class)
                    .putExtra(DisasterRespondActivity.EXTRA_ID, k)
                    .putExtra(DisasterRespondActivity.EXTRA_TITLE, d.optString("title", "Disaster alert"))
                    .putExtra(DisasterRespondActivity.EXTRA_MESSAGE, d.optString("message", ""))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Notifier.show(app, Notifier.ID_DISASTER, Notifier.CH_ALERT,
                    "DISASTER: " + d.optString("title", ""), d.optString("message", "") + "\nTap to respond.", i);
        }
    }

    /**
     * Helper mode: the distance check happens on THIS phone against the victim's position in the
     * request, so no helper location is uploaded unless the helper taps "I can help".
     */
    private static void checkHelpRequests(Context app, FirebaseRest rest, Prefs p) {
        JSONObject all = rest.getQuery("helpRequests", LAST_10);
        if (all == null) return;
        Location me = new LocationHelper(app).lastKnown();
        if (me == null) return;
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject r = all.optJSONObject(k);
            if (r == null || !r.optBoolean("active", false)) continue;
            if (r.optString("requesterUid", "").equals(p.uid())) continue;
            if (p.seen("help").contains(k)) continue;
            double km = Geo.distanceKm(me.getLatitude(), me.getLongitude(), r.optDouble("lat", 0), r.optDouble("lon", 0));
            if (km > r.optDouble("radiusKm", 3)) continue;
            p.addSeen("help", k);
            Intent i = new Intent(app, HelpRequestActivity.class)
                    .putExtra(HelpRequestActivity.EXTRA_ID, k)
                    .putExtra(HelpRequestActivity.EXTRA_TYPE, r.optString("type", ""))
                    .putExtra(HelpRequestActivity.EXTRA_LAT, r.optDouble("lat", 0))
                    .putExtra(HelpRequestActivity.EXTRA_LON, r.optDouble("lon", 0))
                    .putExtra(HelpRequestActivity.EXTRA_KM, km)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Notifier.show(app, Notifier.ID_HELP, Notifier.CH_ALERT, "Someone nearby needs help",
                    Incident.label(r.optString("type", "")) + " emergency, about " + String.format("%.1f", km) + " km away", i);
        }
    }

    // ================================================================ control center

    private static void syncCenter(Context app, FirebaseRest rest) {
        Dispatcher.flushPending(app);
        ingestRelay(app);
        // Incident ids are "uid_time", so push-id ordering does not apply: read the node.
        JSONObject all = rest.getObj("incidents");
        if (all == null) return;
        Prefs p = Prefs.of(app);
        long newest = p.lastIncidentSeen();
        String text = null;
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            String k = it.next();
            Incident i = Incident.fromFirebase(k, all.optJSONObject(k) == null ? new JSONObject() : all.optJSONObject(k));
            if (i.createdAt > newest && "NEW".equals(i.status)) {
                newest = i.createdAt;
                text = Incident.label(i.type) + " - " + i.userName + (i.silent ? " (SILENT)" : "");
            }
        }
        if (text != null && p.lastIncidentSeen() != 0) {
            Notifier.show(app, Notifier.ID_NEW_INCIDENT, Notifier.CH_SOS, "NEW SOS", text,
                    new Intent(app, CenterHomeActivity.class));
        }
        if (newest > p.lastIncidentSeen()) p.lastIncidentSeen(newest);
        if (p.lastIncidentSeen() == 0) p.lastIncidentSeen(System.currentTimeMillis());
    }

    /**
     * Control center: decrypt packets that relay phones uploaded (only the center holds the private
     * key), create incidents from them, and delete the packets.
     */
    public static void ingestRelay(Context app) {
        Prefs p = Prefs.of(app);
        if (!p.isCenter() || p.centerPriv().isEmpty() || !NetUtil.isOnline(app)) return;
        FirebaseRest rest = new FirebaseRest(app);
        JSONObject all = rest.getObj("relay");
        if (all == null) return;
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject o = all.optJSONObject(k);
            if (o == null) continue;
            try {
                String json = new String(Crypto.decrypt(p.centerPriv(), o.optString("payload", "")), StandardCharsets.UTF_8);
                JSONObject report = new JSONObject(json);
                String id = report.optString("id", "");
                if (!id.isEmpty()) {
                    // Do not overwrite a newer direct upload of the same incident.
                    JSONObject existing = rest.getObj("incidents/" + id + "/report");
                    long old = existing == null ? 0 : existing.optLong("updatedAt", 0);
                    if (report.optLong("updatedAt", 0) >= old) rest.put("incidents/" + id + "/report", report);
                }
            } catch (JSONException | RuntimeException ignored) {
                // corrupt or foreign packet: discard it below
            }
            rest.delete("relay/" + k);
        }
    }
}
