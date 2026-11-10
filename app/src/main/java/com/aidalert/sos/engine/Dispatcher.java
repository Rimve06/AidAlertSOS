package com.aidalert.sos.engine;

import android.content.Context;
import android.util.Log;

import com.aidalert.sos.channel.DeliveryChannel;
import com.aidalert.sos.channel.DeliveryException;
import com.aidalert.sos.channel.InternetChannel;
import com.aidalert.sos.channel.LanChannel;
import com.aidalert.sos.channel.RelayChannel;
import com.aidalert.sos.channel.SmsChannel;

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

import java.util.Arrays;
import java.util.List;

/**
 * Gets an SOS out of the phone, over the best channel available, in this order:
 *   1. internet  -> Firebase (report + GPS trail)
 *   2. SIM       -> SMS to the control-center phone
 *   3. nearby    -> encrypted packet queued for the Nearby relay (other phones carry it)
 * Plus: SMS to the preselected emergency contacts and an automatic call.
 * All methods block: call them from AppExecutors.io().
 */
public final class Dispatcher {
    private static final String TAG = "AidDispatch";

    private Dispatcher() {}

    /** Tried in this order when the internet channel is not available or fails. */
    private static final List<DeliveryChannel> FALLBACK =
            Arrays.asList(new LanChannel(), new SmsChannel(), new RelayChannel());
    private static final InternetChannel INTERNET = new InternetChannel();

    public static void sendInitial(Context app, Incident i) {
        transmit(app, i, true);
    }

    /** @param important true for a type change / end of SOS, false for plain GPS updates. */
    public static void sendUpdate(Context app, Incident i, boolean important) {
        transmit(app, i, important);
    }

    private static void transmit(Context app, Incident i, boolean important) {
        boolean ok = false;
        if (INTERNET.isAvailable(app)) {
            try {
                INTERNET.deliver(app, i, true);
                ok = true;
            } catch (DeliveryException e) {
                Log.w(TAG, "internet failed: " + e.getMessage());
            }
        }
        i.synced = ok;
        Db.get(app).setSynced(i.id, ok);
        if (ok) return;
        // Offline: use every fallback channel that works (they are throttled internally).
        for (DeliveryChannel ch : FALLBACK) {
            if (!ch.isAvailable(app)) continue;
            try {
                ch.deliver(app, i, important);
            } catch (DeliveryException e) {
                Log.w(TAG, ch.name() + " failed: " + e.getMessage());
            }
        }
    }

    /** Called when internet comes back: upload unsent incidents and queued relay packets. */
    public static void flushPending(Context app) {
        if (!NetUtil.isOnline(app)) return;
        Db db = Db.get(app);
        FirebaseRest rest = new FirebaseRest(app);
        Prefs prefs = Prefs.of(app);
        String problem = "";
        for (Incident i : db.unsyncedMine()) {
            i.via = "INTERNET";
            FirebaseRest.Result r = rest.request("PUT", "incidents/" + i.id + "/report", null, i.toReportJson().toString());
            if (r.ok()) db.setSynced(i.id, true);
            else problem = com.aidalert.sos.blood.AccessInfo.explain("Sending your alert", r.code, r.body, rest.configured(),
                    com.aidalert.sos.blood.AccessInfo.fromToken(com.aidalert.sos.auth.AuthManager.idToken(false)));
        }
        prefs.syncError(problem);
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
        if ("MENTAL_HEALTH".equals(i.type)) {
            String t = i.userName + " may be having a very hard time and needs someone to check on them now. "
                    + "Please call or go to them. Location: " + where + " - sent by AidAlert SOS";
            for (Db.Contact c : Db.get(app).contacts()) SmsHelper.send(app, c.phone, t);
            return;
        }
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
