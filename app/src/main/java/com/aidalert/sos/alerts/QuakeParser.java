package com.aidalert.sos.alerts;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses the USGS earthquake GeoJSON feed into alerts for the Bangladesh region. */
public final class QuakeParser {
    private QuakeParser() {}

    public static List<AlertItem> parse(String json, double minMagnitude) throws JSONException {
        List<AlertItem> out = new ArrayList<>();
        JSONArray feats = new JSONObject(json).optJSONArray("features");
        if (feats == null) return out;
        for (int i = 0; i < feats.length(); i++) {
            JSONObject f = feats.optJSONObject(i);
            if (f == null) continue;
            JSONObject props = f.optJSONObject("properties");
            JSONObject geom = f.optJSONObject("geometry");
            if (props == null || geom == null) continue;
            JSONArray c = geom.optJSONArray("coordinates"); // [lon, lat, depth]
            if (c == null || c.length() < 2) continue;
            double lon = c.optDouble(0, Double.NaN), lat = c.optDouble(1, Double.NaN);
            double mag = props.optDouble("mag", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon) || Double.isNaN(mag)) continue;
            if (mag < minMagnitude || !AlertRules.inRegion(lat, lon)) continue;
            AlertItem a = new AlertItem();
            String place = props.optString("place", "near the region");
            a.title = String.format(Locale.US, "Magnitude %.1f earthquake", mag);
            a.body = place + String.format(Locale.US, ". Depth %.0f km. Reported by USGS; check official Bangladesh sources for local impact.",
                    c.optDouble(2, 0));
            a.source = "USGS Earthquake Hazards Program";
            a.kind = AlertItem.QUAKE;
            a.severity = AlertRules.quakeSeverity(mag);
            a.timeMs = props.optLong("time", 0);
            a.link = props.optString("url");
            a.area = place;
            out.add(a);
        }
        return out;
    }
}
