package com.aidalert.sos.engine;

import android.content.Context;

import com.aidalert.sos.data.Db;
import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.Crypto;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.net.SmsProtocol;
import com.aidalert.sos.util.CallHelper;
import com.aidalert.sos.util.Geo;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.SmsHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gets an SOS out of the phone, over the best channel available, in this order:
 *   1. internet  -> Firebase (report + GPS trail)
 *   2. SIM       -> SMS to the control-center phone
 *   3. nearby    -> encrypted packet queued for the Nearby relay (other phones carry it)
 * Plus: SMS to the preselected emergency contacts and an automatic call.
 * All methods block: call them from AppExecutors.io().
 */
public final class Dispatcher {
    private Dispatcher() {}

    private static final Map<String, Long> LAST_SMS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_RELAY = new ConcurrentHashMap<>();

    public static void sendInitial(Context app, Incident i) {
        transmit(app, i, true);
    }

    /** @param important true for a type change / end of SOS, false for plain GPS updates. */
    public static void sendUpdate(Context app, Incident i, boolean important) {
        transmit(app, i, important);
    }

    private static void transmit(Context app, Incident i, boolean important) {
        Db db = Db.get(app);
        boolean ok = false;
        if (NetUtil.isOnline(app)) {
            FirebaseRest rest = new FirebaseRest(app);
            i.via = "INTERNET";
            ok = rest.put("incidents/" + i.id + "/report", i.toReportJson());
            if (ok && i.hasFix()) {
                JSONObject pt = new JSONObject();
                try {
                    pt.put("lat", i.lat);
                    pt.put("lon", i.lon);
                    pt.put("t", System.currentTimeMillis());
                    rest.post("tracks/" + i.id, pt);
                } catch (JSONException ignored) {
                    // skip this trail point
                }
            }
        }
        i.synced = ok;
        db.setSynced(i.id, ok);
        if (!ok) offlineFallback(app, i, important);
    }

    private static void offlineFallback(Context app, Incident i, boolean important) {
        Prefs p = Prefs.of(app);
        long now = System.currentTimeMillis();

        // 1) SMS to the control center: first one at once, later ones at most every 3 minutes.
        String center = p.centerPhone();
        Long lastSms = LAST_SMS.get(i.id);
        if (!center.isEmpty() && (important || lastSms == null || now - lastSms > 180_000)) {
            String text = SmsProtocol.encode(i.id, i.type, i.lat, i.lon, now, i.userId, i.userName, i.phone);
            if (SmsHelper.send(app, center, text)) LAST_SMS.put(i.id, now);
        }

        // 2) Encrypted packet for phones that happen to be nearby (store-and-forward).
        String pub = p.centerPub();
        Long lastRelay = LAST_RELAY.get(i.id);
        if (!pub.isEmpty() && (important || lastRelay == null || now - lastRelay > 60_000)) {
            try {
                JSONObject report = i.toReportJson();
                report.put("via", "RELAY");
                Db.Packet pk = new Db.Packet();
                pk.id = i.id + "-" + (now / 1000);
                pk.payload = Crypto.encrypt(pub, report.toString().getBytes(StandardCharsets.UTF_8));
                pk.hops = 0;
                pk.created = now;
                Db.get(app).savePacket(pk);
                LAST_RELAY.put(i.id, now);
                RelayManager.get(app).nudge();
            } catch (JSONException | RuntimeException ignored) {
                // key missing or malformed: SMS above is still the fallback
            }
        }
    }

    /** Called when internet comes back: upload unsent incidents and queued relay packets. */
    public static void flushPending(Context app) {
        if (!NetUtil.isOnline(app)) return;
        Db db = Db.get(app);
        FirebaseRest rest = new FirebaseRest(app);
        for (Incident i : db.unsyncedMine()) {
            i.via = "INTERNET";
            if (rest.put("incidents/" + i.id + "/report", i.toReportJson())) db.setSynced(i.id, true);
        }
        flushRelay(app);
    }

    /** Upload relay packets we carry (our own or other people's, still encrypted). */
    public static void flushRelay(Context app) {
        if (!NetUtil.isOnline(app)) return;
        Db db = Db.get(app);
        FirebaseRest rest = new FirebaseRest(app);
        for (Db.Packet pk : db.pendingPackets()) {
            JSONObject o = new JSONObject();
            try {
                o.put("payload", pk.payload);
                o.put("hops", pk.hops);
                o.put("created", pk.created);
            } catch (JSONException e) {
                continue;
            }
            if (rest.put("relay/" + pk.id, o)) db.markPacketUploaded(pk.id);
        }
        db.prunePackets(System.currentTimeMillis() - 24L * 3600 * 1000);
    }

    /** SMS with type and map link to every preselected emergency contact. */
    public static void notifyContacts(Context app, Incident i) {
        String where = i.hasFix() ? Geo.mapsLink(i.lat, i.lon) : "location not available yet";
        String text = "EMERGENCY! " + i.userName + " needs help (" + Incident.label(i.type) + "). Location: "
                + where + " - sent by AidAlert SOS";
        for (Db.Contact c : Db.get(app).contacts()) {
            SmsHelper.send(app, c.phone, text);
        }
    }

    /** Calls the preselected number (defaults to the control center, then 999). */
    public static boolean autoCall(Context app) {
        return CallHelper.place(app, Prefs.of(app).callNumber());
    }
}
