package com.aidalert.sos.stats;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Crime figures taken from the Bangladesh Police Headquarters monthly / annual PDF reports.
 * Reported cases per unit (metropolitan police, range / district), per crime type.
 * Pure data + sorting, no Android classes.
 */
public final class CrimeStats {
    public String title = "";
    public String period = "";
    public String source = "";
    public String sourceUrl = "";
    public final List<String> crimeTypes = new ArrayList<>();
    public final List<Region> regions = new ArrayList<>();

    public static final class Region {
        public String name = "";
        /** e.g. "Metropolitan" or "Range"; lets the chart show one group at a time. */
        public String group = "";
        public final List<Double> counts = new ArrayList<>(); // same order as crimeTypes
    }

    public static final class Bar {
        public final String label;
        public final double value;

        public Bar(String label, double value) {
            this.label = label;
            this.value = value;
        }
    }

    public boolean isEmpty() {
        return regions.isEmpty() || crimeTypes.isEmpty();
    }

    public static CrimeStats parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        CrimeStats s = new CrimeStats();
        s.title = o.optString("title");
        s.period = o.optString("period");
        s.source = o.optString("source");
        s.sourceUrl = o.optString("sourceUrl");
        JSONArray types = o.optJSONArray("crimeTypes");
        if (types != null) for (int i = 0; i < types.length(); i++) s.crimeTypes.add(types.optString(i));
        JSONArray regs = o.optJSONArray("regions");
        if (regs != null) {
            for (int i = 0; i < regs.length(); i++) {
                JSONObject r = regs.optJSONObject(i);
                if (r == null) continue;
                Region x = new Region();
                x.name = r.optString("name");
                x.group = r.optString("group");
                JSONArray c = r.optJSONArray("counts");
                for (int k = 0; k < s.crimeTypes.size(); k++) x.counts.add(c == null ? 0.0 : c.optDouble(k, 0.0));
                if (!x.name.isEmpty()) s.regions.add(x);
            }
        }
        return s;
    }

    public List<String> groups() {
        List<String> g = new ArrayList<>();
        for (Region r : regions) if (!g.contains(r.group)) g.add(r.group);
        return g;
    }

    /** Bars for one crime type (index into crimeTypes) and group ("" = all), largest first. */
    public List<Bar> bars(int crimeIndex, String group) {
        List<Bar> out = new ArrayList<>();
        if (crimeIndex < 0 || crimeIndex >= crimeTypes.size()) return out;
        for (Region r : regions) {
            if (group != null && !group.isEmpty() && !group.equals(r.group)) continue;
            out.add(new Bar(r.name, r.counts.get(crimeIndex)));
        }
        Collections.sort(out, new Comparator<Bar>() {
            @Override
            public int compare(Bar a, Bar b) {
                return Double.compare(b.value, a.value);
            }
        });
        return out;
    }

    /** Total reported cases for each crime type across all regions, in the order of crimeTypes. */
    public List<Bar> totalsByType() {
        List<Bar> out = new ArrayList<>();
        for (int k = 0; k < crimeTypes.size(); k++) {
            double sum = 0;
            for (Region r : regions) sum += r.counts.get(k);
            out.add(new Bar(crimeTypes.get(k), sum));
        }
        return out;
    }

    public double total() {
        double t = 0;
        for (Bar b : totalsByType()) t += b.value;
        return t;
    }

    /** Region with the most cases of all types together, or null. */
    public Bar topRegion() {
        Bar best = null;
        for (Region r : regions) {
            double sum = 0;
            for (double v : r.counts) sum += v;
            if (best == null || sum > best.value) best = new Bar(r.name, sum);
        }
        return best;
    }

    /** Largest crime types first, the rest merged into "Others", at most max slices. */
    public static List<Bar> topWithOthers(List<Bar> all, int max) {
        List<Bar> sorted = new ArrayList<>(all);
        Collections.sort(sorted, (a, b) -> Double.compare(b.value, a.value));
        List<Bar> out = new ArrayList<>();
        double rest = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (i < max - 1 || sorted.size() <= max) out.add(sorted.get(i));
            else rest += sorted.get(i).value;
        }
        if (rest > 0) out.add(new Bar("Others", rest));
        return out;
    }

    /**
     * Keeps one total per report period on the phone (period -> total), so a trend can be drawn once two or more
     * monthly reports have been seen. The cloud only holds the latest report.
     */
    public static String addToHistory(String historyJson, CrimeStats s) {
        try {
            JSONObject h = historyJson == null || historyJson.isEmpty() ? new JSONObject() : new JSONObject(historyJson);
            if (!s.period.isEmpty() && !s.isEmpty()) h.put(s.period, s.total());
            return h.toString();
        } catch (JSONException e) {
            return historyJson == null ? "{}" : historyJson;
        }
    }

    /** History as bars sorted by period (text order, so use YYYY-MM). */
    public static List<Bar> history(String historyJson) {
        List<Bar> out = new ArrayList<>();
        if (historyJson == null || historyJson.isEmpty()) return out;
        try {
            JSONObject h = new JSONObject(historyJson);
            List<String> keys = new ArrayList<>();
            java.util.Iterator<String> it = h.keys();
            while (it.hasNext()) keys.add(it.next());
            Collections.sort(keys);
            for (String k : keys) out.add(new Bar(k, h.optDouble(k, 0)));
        } catch (JSONException ignored) {
            // empty history
        }
        return out;
    }
}
