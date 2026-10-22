package com.aidalert.sos.engine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import com.aidalert.sos.data.Db;
import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.net.SmsProtocol;
import com.aidalert.sos.ui.center.CenterHomeActivity;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.Notifier;

/**
 * Control-center phone only: turns an "AIDSOS|v1|..." text message (sent by a citizen phone that has
 * a SIM signal but no internet) into an incident and uploads it as soon as the center is online.
 */
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        final Context app = context.getApplicationContext();
        if (!Prefs.of(app).isCenter()) return;

        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;
        StringBuilder body = new StringBuilder();
        for (SmsMessage m : msgs) body.append(m.getMessageBody());
        final SmsProtocol.Parsed p = SmsProtocol.decode(body.toString().trim());
        if (p == null) return;

        final PendingResult pr = goAsync();
        AppExecutors.io(() -> {
            try {
                Incident i = new Incident();
                i.id = p.incidentId;
                i.userId = p.userId;
                i.userName = p.name;
                i.phone = p.phone;
                i.type = p.type;
                i.source = "SMS";
                i.via = "SMS";
                i.status = "NEW";
                i.lat = p.lat;
                i.lon = p.lon;
                i.createdAt = p.timeMs;
                i.updatedAt = System.currentTimeMillis();
                i.mine = true; // reuse "unsynced mine" flush on the center phone
                boolean ok = NetUtil.isOnline(app) && new FirebaseRest(app).put("incidents/" + i.id + "/report", i.toReportJson());
                i.synced = ok;
                Db.get(app).saveIncident(i);
                Notifier.show(app, Notifier.ID_NEW_INCIDENT, Notifier.CH_SOS, "SOS by SMS: " + Incident.label(i.type),
                        i.userName + " " + i.phone, new Intent(app, CenterHomeActivity.class));
            } finally {
                pr.finish();
            }
        });
    }
}
