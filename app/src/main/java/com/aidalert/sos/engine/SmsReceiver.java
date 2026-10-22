package com.aidalert.sos.engine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.SmsProtocol;
import com.aidalert.sos.util.AppExecutors;

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
                IncidentIngest.fromText(app, p, "SMS");
            } finally {
                pr.finish();
            }
        });
    }
}
