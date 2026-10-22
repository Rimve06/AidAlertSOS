package com.aidalert.sos.engine;

import android.content.Context;
import android.content.Intent;

import com.aidalert.sos.data.Db;
import com.aidalert.sos.data.Incident;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.net.SmsProtocol;
import com.aidalert.sos.ui.center.CenterHomeActivity;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.Notifier;

/** Control center: turns an alert that arrived by SMS or LAN socket into an incident and uploads it. */
public final class IncidentIngest {
    private IncidentIngest() {}

    public static Incident fromText(Context app, SmsProtocol.Parsed p, String via) {
        Incident i = new Incident();
        i.id = p.incidentId;
        i.userId = p.userId;
        i.userName = p.name;
        i.phone = p.phone;
        i.type = p.type;
        i.source = via;
        i.via = via;
        i.status = "NEW";
        i.lat = p.lat;
        i.lon = p.lon;
        i.createdAt = p.timeMs;
        i.updatedAt = System.currentTimeMillis();
        i.mine = true; // reuse the "unsynced mine" flush on the center phone
        boolean ok = NetUtil.isOnline(app) && new FirebaseRest(app).put("incidents/" + i.id + "/report", i.toReportJson());
        i.synced = ok;
        Db.get(app).saveIncident(i);
        Notifier.show(app, Notifier.ID_NEW_INCIDENT, Notifier.CH_SOS, "SOS by " + via + ": " + Incident.label(i.type),
                i.userName + " " + i.phone, new Intent(app, CenterHomeActivity.class));
        return i;
    }
}
