package com.aidalert.sos.ui.center;

import android.content.Context;
import android.content.SharedPreferences;

import com.aidalert.sos.net.FirebaseRest;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Loads every citizen conversation with the control center and tracks which ones this phone has already opened. */
final class InboxData {
    private InboxData() {
    }

    static final class Convo {
        String uid = "", name = "", lastText = "", lastFrom = "";
        long lastTs;
        int unread;           // citizen messages newer than the last time the operator opened this chat
        int total;            // messages loaded (up to the last 30)
    }

    static final class Result {
        final List<Convo> list = new ArrayList<>();
        String error = "";
        int unreadChats() {
            int n = 0;
            for (Convo c : list) if (c.unread > 0) n++;
            return n;
        }
    }

    private static final String LAST_30 = "orderBy=%22%24key%22&limitToLast=30";
    private static final int MAX_THREADS = 100;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("center_inbox", Context.MODE_PRIVATE);
    }

    static void markSeen(Context c, String uid, long ts) {
        sp(c).edit().putLong("seen_" + uid, ts).apply();
    }

    /** Call off the main thread. */
    static Result fetch(Context ctx, FirebaseRest rest) {
        Result res = new Result();
        Set<String> uids = new LinkedHashSet<>();
        FirebaseRest.Result sh = rest.request("GET", "msgs", "shallow=true", null);
        if (sh.ok()) {
            JSONObject o = sh.obj();
            if (o != null) {
                Iterator<String> it = o.keys();
                while (it.hasNext()) uids.add(it.next());
            }
        } else {
            res.error = "Could not list citizen messages (error " + sh.code + "). Publish database.rules.json in Firebase "
                    + "(it now allows reading /msgs), then reopen this screen.";
        }
        // Also check everyone who ever sent an SOS, in case the listing above was refused.
        JSONObject inc = rest.getObj("incidents");
        if (inc != null) {
            Iterator<String> it = inc.keys();
            while (it.hasNext()) {
                JSONObject n = inc.optJSONObject(it.next());
                JSONObject r = n == null ? null : n.optJSONObject("report");
                String u = r == null ? "" : r.optString("userId", "");
                if (!u.isEmpty()) uids.add(u);
            }
        }
        SharedPreferences sp = sp(ctx);
        for (String uid : uids) {
            if (res.list.size() >= MAX_THREADS) break;
            JSONObject all = rest.getQuery("msgs/" + uid, LAST_30);
            if (all == null || all.length() == 0) continue;
            Convo c = new Convo();
            c.uid = uid;
            long seen = sp.getLong("seen_" + uid, 0);
            Iterator<String> it = all.keys();
            while (it.hasNext()) {
                JSONObject m = all.optJSONObject(it.next());
                if (m == null) continue;
                c.total++;
                long ts = m.optLong("ts", 0);
                String from = m.optString("from", "");
                if ("USER".equals(from)) {
                    if (!m.optString("name", "").isEmpty()) c.name = m.optString("name", "");
                    if (ts > seen) c.unread++;
                }
                if (ts >= c.lastTs) {
                    c.lastTs = ts;
                    c.lastFrom = from;
                    c.lastText = m.optString("text", "");
                }
            }
            if (c.name.isEmpty()) {
                JSONObject prof = rest.getObj("users/" + uid);
                c.name = prof == null ? "" : prof.optString("name", "");
                if (c.name.isEmpty()) c.name = "Citizen " + uid.substring(0, Math.min(6, uid.length()));
            }
            res.list.add(c);
        }
        // Unread first, then awaiting-reply, then newest.
        Collections.sort(res.list, (x, y) -> {
            if ((x.unread > 0) != (y.unread > 0)) return x.unread > 0 ? -1 : 1;
            boolean wx = "USER".equals(x.lastFrom), wy = "USER".equals(y.lastFrom);
            if (wx != wy) return wx ? -1 : 1;
            return Long.compare(y.lastTs, x.lastTs);
        });
        return res;
    }
}
