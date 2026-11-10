package com.aidalert.sos.alerts;

import android.content.Context;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.engine.SyncManager;
import com.aidalert.sos.net.AlertFeedParser;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.net.Html;
import com.aidalert.sos.news.FeedConfig;
import com.aidalert.sos.news.NewsRules;
import com.aidalert.sos.net.Http;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Collects public alerts from several independent sources and keeps the last result for offline use.
 * Each source fails on its own: one broken feed never hides the others.
 *
 *   USGS earthquakes (GeoJSON)        public, no key, filtered to the Bangladesh region
 *   GDACS disaster feed (RSS / XML)   global; only items that mention Bangladesh are kept
 *   Control center feed (XML URL)     set by the control center in Numbers
 *   Control center messages           broadcasts written by the control center in Firebase
 *
 * Call from a background thread.
 */
public final class AlertRepo {
    private AlertRepo() {}

    public static final String USGS_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson"
            + "&minlatitude=19&maxlatitude=28&minlongitude=86&maxlongitude=94.5&minmagnitude=3.5&orderby=time&limit=30";
    public static final String USGS_WORLD_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson"
            + "&minmagnitude=6&orderby=magnitude&limit=15";
    public static final String GDACS_URL = "https://www.gdacs.org/xml/rss.xml";

    public static final class Result {
        public final List<AlertItem> items = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public long fetchedAt;
        public boolean fromCache;
    }

    public static Result fetch(Context ctx) {
        Prefs prefs = Prefs.of(ctx);
        Result r = new Result();
        if (!NetUtil.isOnline(ctx)) {
            return cached(prefs, "You are offline. Showing the last saved alerts.");
        }
        // 1. earthquakes
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            String since = f.format(new Date(System.currentTimeMillis() - 14L * 86400_000L));
            r.items.addAll(QuakeParser.parse(Http.get(USGS_URL + "&starttime=" + since, "application/json"), 3.5));
        } catch (Exception e) {
            r.errors.add("Earthquakes (USGS): " + e.getMessage());
        }
        // 1b. big earthquakes anywhere in the world (international alerts)
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            String since = f.format(new Date(System.currentTimeMillis() - 7L * 86400_000L));
            for (AlertItem q : QuakeParser.parse(Http.get(USGS_WORLD_URL + "&starttime=" + since, "application/json"), 6.0)) {
                if (isNearBangladesh(q) || hasTitle(r.items, q.title)) continue;
                q.scope = AlertItem.SCOPE_WORLD;
                r.items.add(q);
            }
        } catch (Exception e) {
            r.errors.add("World earthquakes (USGS): " + e.getMessage());
        }
        // 2. GDACS
        int worldGdacs = 0;
        try {
            for (AlertFeedParser.Item it : AlertFeedParser.parse(Http.get(GDACS_URL, "application/xml, application/rss+xml, text/xml", 4 * 1024 * 1024, 25000))) {
                boolean bd = AlertRules.mentionsBangladesh(it.title, it.description, it.area);
                int sev = AlertRules.levelSeverity(it.title);
                if (!bd && sev < 1) continue;            // world items: only orange and red alerts
                if (!bd && ++worldGdacs > 25) continue;
                AlertItem a = new AlertItem();
                a.title = it.title;
                a.body = it.description;
                a.source = "GDACS (UN / EU disaster alerts)";
                a.kind = AlertRules.kindFromText(it.title + " " + it.description);
                a.severity = sev;
                a.scope = bd ? AlertItem.SCOPE_NATIONAL : AlertItem.SCOPE_WORLD;
                a.timeMs = AlertRules.parseDate(it.date);
                a.link = it.link;
                a.area = it.area;
                r.items.add(a);
            }
        } catch (Exception e) {
            r.errors.add("Disaster alerts (GDACS): " + e.getMessage());
        }
        // 2b. extra public feeds from assets/feeds.json (Google News alert searches and others)
        java.util.Set<String> seen = new java.util.HashSet<>();
        long oldest = System.currentTimeMillis() - 10L * 86400_000L;
        for (FeedConfig.Feed f : FeedConfig.load(ctx).alerts) {
            try {
                for (AlertFeedParser.Item it : AlertFeedParser.parse(Http.get(f.url))) {
                    String head = Html.withoutPublisher(it.title);
                    String key = head.toLowerCase(Locale.ROOT);
                    if (key.isEmpty() || !seen.add(key)) continue;
                    long t = AlertRules.parseDate(it.date);
                    if (t != 0 && t < oldest) continue;
                    String pub = Html.publisher(it.title);
                    boolean bd = NewsRules.isBangladesh(head, pub);
                    AlertItem a = new AlertItem();
                    switch (f.kind == null ? "" : f.kind) {
                        case "LAW_ORDER":
                            // Section 144, curfew, police advisories: must be Bangladesh and must say so in the headline.
                            if (!bd || !NewsRules.isLawOrder(head)) continue;
                            a.category = AlertItem.CAT_LAW;
                            a.kind = AlertItem.OTHER;
                            a.severity = NewsRules.isOrderLevel(head) ? 2 : 1;
                            break;
                        case "SAFETY_NEWS":
                            if (!bd || NewsRules.emergencyLevel(head) == 0) continue;
                            a.category = AlertItem.CAT_NEWS;
                            a.kind = AlertItem.OTHER;
                            a.severity = 0;
                            break;
                        case "WORLD_DISASTER":
                            if (bd || NewsRules.emergencyLevel(head) != 2 || !NewsRules.isHazardAlert(head, "OTHER")) continue;
                            a.scope = AlertItem.SCOPE_WORLD;
                            a.kind = AlertRules.kindFromText(head);
                            a.severity = NewsRules.severity(head);
                            break;
                        default:
                            // Must be a real hazard headline about Bangladesh, not a story that only matched the search.
                            if (!NewsRules.isHazardAlert(head, f.kind) || !bd) continue;
                            a.kind = AlertItem.OTHER.equals(f.kind) ? AlertRules.kindFromText(head) : f.kind;
                            if (AlertRules.isHealth(head) && !AlertItem.QUAKE.equals(a.kind)) a.kind = AlertItem.HEALTH;
                            a.severity = NewsRules.severity(head);
                    }
                    a.title = head;
                    a.body = Html.strip(it.description);
                    a.source = pub.isEmpty() ? f.name : pub + " (via Google News)";
                    a.timeMs = t;
                    a.link = it.link;
                    a.official = false;                 // a news report is never an official order
                    r.items.add(a);
                }
            } catch (Exception e) {
                r.errors.add(f.name + ": " + e.getMessage());
            }
        }
        // 3. control center XML feed
        String feed = prefs.feedUrl();
        if (!feed.isEmpty()) {
            try {
                for (AlertFeedParser.Item it : AlertFeedParser.parse(Http.get(feed))) {
                    AlertItem a = new AlertItem();
                    a.title = it.title;
                    a.body = it.description;
                    a.source = "Control center feed";
                    a.kind = AlertRules.kindFromText(it.title + " " + it.description);
                    a.severity = 1;
                    a.timeMs = AlertRules.parseDate(it.date);
                    a.link = it.link;
                    a.area = it.area;
                    r.items.add(a);
                }
            } catch (Exception e) {
                r.errors.add("Control center feed: " + e.getMessage());
            }
        }
        // 4. control center messages
        try {
            FirebaseRest rest = new FirebaseRest(ctx);
            if (rest.configured()) {
                JSONObject all = rest.getQuery("broadcasts", SyncManager.LAST_10);
                if (all != null) {
                    Iterator<String> it = all.keys();
                    while (it.hasNext()) {
                        JSONObject o = all.optJSONObject(it.next());
                        if (o == null) continue;
                        AlertItem a = new AlertItem();
                        a.title = "Message from the control center";
                        a.body = o.optString("text");
                        a.source = "Control center";
                        a.kind = AlertItem.OFFICIAL;
                        a.severity = Math.max(1, o.optInt("level", 1));
                        a.official = true;
                        a.category = o.optString("category", AlertItem.CAT_EMERGENCY);
                        a.district = o.optString("district");
                        a.scope = a.district.isEmpty() ? AlertItem.SCOPE_NATIONAL : AlertItem.SCOPE_LOCAL;
                        a.area = a.district;
                        a.timeMs = o.optLong("ts");
                        r.items.add(a);
                    }
                }
            }
        } catch (RuntimeException e) {
            r.errors.add("Control center messages: " + e.getMessage());
        }

        for (AlertItem a : r.items) {
            if (a.district.isEmpty() && !AlertItem.SCOPE_WORLD.equals(a.scope)) {
                a.district = Regions.districtIn(a.title + " " + a.area);
                if (!a.district.isEmpty() && AlertItem.SCOPE_NATIONAL.equals(a.scope) && !AlertItem.CAT_EMERGENCY.equals(a.category)) {
                    a.scope = AlertItem.SCOPE_LOCAL;
                }
            }
        }
        Collections.sort(r.items, AlertRules::order);
        r.fetchedAt = System.currentTimeMillis();
        if (!r.items.isEmpty() || r.errors.isEmpty()) save(prefs, r);
        else return cached(prefs, "Could not reach the alert sources. Showing the last saved alerts.");
        return r;
    }

    private static boolean hasTitle(List<AlertItem> items, String title) {
        for (AlertItem a : items) if (a.title.equals(title)) return true;
        return false;
    }

    /** USGS items that fall in the Bangladesh box are already listed as local alerts. */
    private static boolean isNearBangladesh(AlertItem q) {
        return q.area != null && Regions.mentionsPlace(q.area) || q.title.toLowerCase(Locale.ROOT).contains("bangladesh");
    }

    private static void save(Prefs prefs, Result r) {
        JSONArray arr = new JSONArray();
        for (AlertItem a : r.items) arr.put(a.toJson());
        prefs.alertsCache(arr.toString());
        prefs.alertsCacheAt(r.fetchedAt);
    }

    public static Result cached(Prefs prefs, String note) {
        Result r = new Result();
        r.fromCache = true;
        r.fetchedAt = prefs.alertsCacheAt();
        if (note != null) r.errors.add(note);
        try {
            JSONArray arr = new JSONArray(prefs.alertsCache());
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
