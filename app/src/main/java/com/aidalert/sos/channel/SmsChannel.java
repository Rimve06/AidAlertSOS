package com.aidalert.sos.channel;

import android.content.Context;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.SmsProtocol;
import com.aidalert.sos.util.Perms;
import com.aidalert.sos.util.SmsHelper;

/** SIM network: a compact text message that the control-center phone parses automatically. */
public class SmsChannel extends AbstractChannel {
    public SmsChannel() {
        super(180_000);
    }

    @Override
    public String name() {
        return "SMS";
    }

    @Override
    public boolean isAvailable(Context c) {
        return Perms.sms(c) && !Prefs.of(c).centerPhone().isEmpty();
    }

    @Override
    protected void send(Context app, Incident i) throws DeliveryException {
        String text = SmsProtocol.encode(i.id, i.type, i.lat, i.lon, System.currentTimeMillis(),
                i.userId, i.userName, i.phone);
        if (!SmsHelper.send(app, Prefs.of(app).centerPhone(), text)) {
            throw new DeliveryException("SMS could not be sent");
        }
    }
}
