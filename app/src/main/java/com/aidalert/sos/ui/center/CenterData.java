package com.aidalert.sos.ui.center;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.ui.RowAdapter;
import com.aidalert.sos.util.TimeUtil;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Small helpers shared by the Control Center screens. */
final class CenterData {
    private CenterData() {
    }

    /** A location older than this is shown as "stale" instead of "live". */
    static final long STALE_MS = 5 * 60_000L;

    static List<Incident> parseIncidents(JSONObject node) {
        List<Incident> list = new ArrayList<>();
        if (node != null) {
            Iterator<String> it = node.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject o = node.optJSONObject(k);
                if (o != null) list.add(Incident.fromFirebase(k, o));
            }
            Collections.sort(list, (x, y) -> Long.compare(y.createdAt, x.createdAt));
        }
        return list;
    }

    static RowAdapter.Row row(Incident i) {
        String sub = (i.userName.isEmpty() ? "Unknown" : i.userName) + "  " + i.phone + "\n"
                + TimeUtil.ago(i.createdAt) + "  |  via " + i.via
                + (i.hasFix() ? "" : "  |  NO GPS YET") + (i.silent ? "  |  SILENT" : "")
                + (i.dispatched.isEmpty() ? "" : "\nDispatched: " + i.dispatched);
        return new RowAdapter.Row(Incident.label(i.type), sub, i.status.replace('_', ' '), Incident.color(i.type), i);
    }
}
