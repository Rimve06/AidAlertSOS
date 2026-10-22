package com.aidalert.sos.channel;

import android.content.Context;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONException;
import org.json.JSONObject;

/** Normal path: report + GPS trail point to the server over HTTPS. */
public class InternetChannel extends AbstractChannel {
    public InternetChannel() {
        super(0);
    }

    @Override
    public String name() {
        return "INTERNET";
    }

    @Override
    public boolean isAvailable(Context c) {
        return NetUtil.isOnline(c) && new FirebaseRest(c).configured();
    }

    @Override
    protected void send(Context app, Incident i) throws DeliveryException {
        FirebaseRest rest = new FirebaseRest(app);
        i.via = "INTERNET";
        if (!rest.put("incidents/" + i.id + "/report", i.toReportJson())) {
            throw new DeliveryException("server refused the report");
        }
        if (i.hasFix()) {
            JSONObject pt = new JSONObject();
            try {
                pt.put("lat", i.lat);
                pt.put("lon", i.lon);
                pt.put("t", System.currentTimeMillis());
            } catch (JSONException e) {
                throw new DeliveryException("bad trail point", e);
            }
            rest.post("tracks/" + i.id, pt);
        }
    }
}
