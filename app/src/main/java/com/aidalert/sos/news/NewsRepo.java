package com.aidalert.sos.news;

import android.content.Context;

import com.aidalert.sos.alerts.AlertItem;
import com.aidalert.sos.alerts.AlertRules;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.AlertFeedParser;
import com.aidalert.sos.net.Html;
import com.aidalert.sos.net.Http;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Daily news about the country from public feeds, emergencies, alerts and violence first. Background thread. */
public final class NewsRepo {
    private NewsRepo() {}

    public static final class Result {
        public final List<AlertItem> items = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public long fetchedAt;
        public boolean fromCache;
    }

    /** priority is kept in AlertItem.severity (2 alert, 1 safety, 0 news). */
    public static void sort(List<AlertItem> list) {
        Collections.sort(list, (x, y) -> {
            if (x.severity != y.severity) return Integer.compare(y.severity, x.severity);
            return Long.compare(y.timeMs, x.timeMs);
        });
    }

    public static Result fetch(Context ctx) {
        Prefs prefs = Prefs.of(ctx);
        if (!NetUtil.isOnline(ctx)) return cached(prefs, "You are offline. Showing the last saved news.");
        Result r = new Result();
        Set<String> seen = new HashSet<>();
        long oldest = System.currentTimeMillis() - 3L * 86400_000L;
        for (FeedConfig.Feed f : FeedConfig.load(ctx).news) {
            try {
                for (AlertFeedParser.Item it : AlertFeedParser.parse(Http.get(f.url))) {
                    String head = Html.withoutPublisher(it.title);
                    if (head.isEmpty() || !seen.add(head.toLowerCase(Locale.ROOT))) continue;
                    long t = AlertRules.parseDate(it.date);
                    if (t != 0 && t < oldest) continue;
                    AlertItem a = new AlertItem();
                    a.title = head;
                    a.body = Html.strip(it.description);
                    String pub = Html.publisher(it.title);
                    a.source = pub.isEmpty() ? f.name : pub;
                    a.kind = AlertRules.kindFromText(head);
                    a.severity = NewsRules.rank(head, pub);
                    a.timeMs = t;
                    a.link = it.link;
                    r.items.add(a);
                }
            } catch (Exception e) {
                r.errors.add(f.name + ": " + e.getMessage());
            }
        }
        sort(r.items);
        r.fetchedAt = System.currentTimeMillis();
        if (r.items.isEmpty()) return cached(prefs, "Could not load the news feeds. Showing the last saved news.");
        JSONArray arr = new JSONArray();
        for (AlertItem a : r.items) arr.put(a.toJson());
        prefs.newsCache(arr.toString());
        prefs.newsCacheAt(r.fetchedAt);
        return r;
    }

    public static Result cached(Prefs prefs, String note) {
        Result r = new Result();
        r.fromCache = true;
        r.fetchedAt = prefs.newsCacheAt();
        if (note != null) r.errors.add(note);
        try {
            JSONArray arr = new JSONArray(prefs.newsCache());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) r.items.add(AlertItem.fromJson(o));
            }
        } catch (JSONException ignored) {
            // empty cache
        }
        return r;
    }
}
