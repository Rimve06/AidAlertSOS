package com.aidalert.sos.channel;

import android.content.Context;

import com.aidalert.sos.data.Db;
import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.engine.RelayManager;
import com.aidalert.sos.net.Crypto;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/** Store-and-forward: an encrypted packet is queued and handed to any phone that comes near. */
public class RelayChannel extends AbstractChannel {
    public RelayChannel() {
        super(60_000);
    }

    @Override
    public String name() {
        return "RELAY";
    }

    @Override
    public boolean isAvailable(Context c) {
        return !Prefs.of(c).centerPub().isEmpty();
    }

    @Override
    protected void send(Context app, Incident i) throws DeliveryException {
        try {
            long now = System.currentTimeMillis();
            JSONObject report = i.toReportJson();
            report.put("via", "RELAY");
            Db.Packet pk = new Db.Packet();
            pk.id = i.id + "-" + (now / 1000);
            pk.payload = Crypto.encrypt(Prefs.of(app).centerPub(), report.toString().getBytes(StandardCharsets.UTF_8));
            pk.hops = 0;
            pk.created = now;
            Db.get(app).savePacket(pk);
            RelayManager.get(app).nudge();
        } catch (JSONException | RuntimeException e) {
            throw new DeliveryException("could not queue relay packet", e);
        }
    }
}
