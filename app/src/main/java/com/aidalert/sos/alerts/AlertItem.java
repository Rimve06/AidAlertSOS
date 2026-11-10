package com.aidalert.sos.alerts;

import org.json.JSONException;
import org.json.JSONObject;

/** One public alert, whatever its source. */
public class AlertItem {
    public static final String QUAKE = "EARTHQUAKE";
    public static final String WEATHER = "CYCLONE_FLOOD";
    public static final String HEALTH = "HEALTH";       // dengue, malaria, cholera, outbreaks: a disease is not a disaster
    public static final String OFFICIAL = "OFFICIAL";   // control center
    public static final String OTHER = "OTHER";

    /** category: what the alert is about. */
    public static final String CAT_EMERGENCY = "EMERGENCY";     // floods, cyclones, quakes, fires, outbreaks
    public static final String CAT_LAW = "LAW_ORDER";           // Section 144, curfew, police advisories, road closures
    public static final String CAT_NEWS = "SAFETY_NEWS";        // reported crime and developing incidents (news, not orders)
    /** scope: how far it reaches. */
    public static final String SCOPE_NATIONAL = "NATIONAL";
    public static final String SCOPE_LOCAL = "LOCAL";           // a district or smaller
    public static final String SCOPE_WORLD = "INTERNATIONAL";

    public String title = "";
    public String body = "";
    public String source = "";
    public String kind = OTHER;
    /** 0 info, 1 caution, 2 severe */
    public int severity;
    public long timeMs;
    public String link = "";
    public String area = "";
    public String category = CAT_EMERGENCY;
    public String scope = SCOPE_NATIONAL;
    public String district = "";
    /** True only for alerts written by the control center or read from an authority's own page. News is never official. */
    public boolean official;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("title", title);
            o.put("body", body);
            o.put("source", source);
            o.put("kind", kind);
            o.put("severity", severity);
            o.put("time", timeMs);
            o.put("link", link);
            o.put("area", area);
            o.put("category", category);
            o.put("scope", scope);
            o.put("district", district);
            o.put("official", official);
        } catch (JSONException ignored) {
            // all values are plain
        }
        return o;
    }

    public static AlertItem fromJson(JSONObject o) {
        AlertItem a = new AlertItem();
        a.title = o.optString("title");
        a.body = o.optString("body");
        a.source = o.optString("source");
        a.kind = o.optString("kind", OTHER);
        a.severity = o.optInt("severity", 0);
        a.timeMs = o.optLong("time", 0);
        a.link = o.optString("link");
        a.area = o.optString("area");
        a.category = o.optString("category", CAT_EMERGENCY);
        a.scope = o.optString("scope", SCOPE_NATIONAL);
        a.district = o.optString("district");
        a.official = o.optBoolean("official", false);
        // alerts saved by an older version: a disease headline is Health, not Disaster
        if (OTHER.equals(a.kind) && AlertRules.isHealth(a.title)) a.kind = HEALTH;
        return a;
    }
}
