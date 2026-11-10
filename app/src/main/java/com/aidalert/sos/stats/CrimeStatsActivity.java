package com.aidalert.sos.stats;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.aidalert.sos.databinding.ActivityCrimeStatsBinding;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Bar chart of official crime statistics by city / range. The newest data is read from Firebase (stats/latest),
 * saved on the phone for offline use, and falls back to assets/crime_stats.json. The control center replaces
 * stats/latest with each new report, so only the latest report exists in the cloud.
 */
public class CrimeStatsActivity extends BaseActivity {
    private ActivityCrimeStatsBinding b;
    private CrimeStats stats = new CrimeStats();
    private int crime = 0;
    private String group = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityCrimeStatsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Crime statistics");
        try (InputStream in = getAssets().open("crime_stats.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            stats = CrimeStats.parse(out.toString("UTF-8"));
        } catch (Exception e) {
            stats = new CrimeStats();
        }
        String cached = Prefs.of(this).statsCache();
        if (!cached.isEmpty()) {
            try {
                CrimeStats c = CrimeStats.parse(cached);
                // a saved copy only replaces the bundled one when it is a newer month
                if (!c.isEmpty() && c.period.compareTo(stats.period) >= 0) stats = c;
            } catch (Exception ignored) {
                // use the bundled copy
            }
        }
        if (Prefs.of(this).isCenter()) {
            b.btnPublish.setVisibility(View.VISIBLE);
            b.btnPublish.setOnClickListener(v -> publishDialog());
        }
        refreshFromCloud();
        b.btnCrimeType.setOnClickListener(v -> pickCrime());
        b.btnGroup.setOnClickListener(v -> pickGroup());
        render();
    }

    private void refreshFromCloud() {
        if (!NetUtil.isOnline(this)) return;
        AppExecutors.io(() -> {
            JSONObject o = new FirebaseRest(this).getObj("stats/latest");
            if (o == null) return;
            try {
                final CrimeStats c = CrimeStats.parse(o.toString());
                if (c.isEmpty()) return;
                if (c.period.compareTo(stats.period) < 0) return; // never replace newer data with an older month
                Prefs.of(this).statsCache(o.toString());
                AppExecutors.main(() -> {
                    if (isFinishing()) return;
                    stats = c;
                    crime = 0;
                    group = "";
                    render();
                });
            } catch (Exception ignored) {
                // keep what is on screen
            }
        });
    }

    /** Control center: paste the JSON made from the newest police report. It replaces the previous report. */
    private void publishDialog() {
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setHint("Paste the statistics JSON (see docs/CRIME_STATS.md)");
        in.setMinLines(6);
        in.setGravity(android.view.Gravity.TOP);
        new AlertDialog.Builder(this).setTitle("Replace the published statistics")
                .setMessage("This deletes the previous report from the cloud and publishes this one.")
                .setView(in)
                .setPositiveButton("Publish", (d, w) -> {
                    final String text = in.getText().toString().trim();
                    final CrimeStats c;
                    try {
                        c = CrimeStats.parse(text);
                    } catch (Exception e) {
                        Ui.toast(this, "That is not valid JSON.");
                        return;
                    }
                    if (c.isEmpty() || c.source.isEmpty() || c.period.isEmpty()) {
                        Ui.toast(this, "It needs source, period, crimeTypes and regions.");
                        return;
                    }
                    AppExecutors.io(() -> {
                        boolean ok;
                        try {
                            ok = new FirebaseRest(this).put("stats/latest", new JSONObject(text));
                        } catch (Exception e) {
                            ok = false;
                        }
                        final boolean done = ok;
                        AppExecutors.main(() -> {
                            Ui.toast(this, done ? "Published. The previous report was replaced." : "Could not publish. Check the internet and your login.");
                            if (done) {
                                Prefs.of(this).statsCache(text);
                                stats = c;
                                crime = 0;
                                group = "";
                                render();
                            }
                        });
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void pickCrime() {
        final CharSequence[] items = stats.crimeTypes.toArray(new CharSequence[0]);
        new AlertDialog.Builder(this).setTitle("Crime type").setItems(items, (d, i) -> {
            crime = i;
            render();
        }).show();
    }

    private void pickGroup() {
        final List<String> g = new ArrayList<>(stats.groups());
        g.add(0, "All");
        new AlertDialog.Builder(this).setTitle("Area group").setItems(g.toArray(new CharSequence[0]), (d, i) -> {
            group = i == 0 ? "" : g.get(i);
            render();
        }).show();
    }

    private void renderOverview() {
        boolean has = !stats.isEmpty();
        b.cardDistricts.setVisibility(has ? View.VISIBLE : View.GONE);
        b.cardTypes.setVisibility(has ? View.VISIBLE : View.GONE);
        b.cardTrend.setVisibility(has ? View.VISIBLE : View.GONE);
        b.kpiTotal.setText(has ? String.format(java.util.Locale.US, "%,.0f", stats.total()) : "-");
        List<CrimeStats.Bar> top = has ? CrimeStats.topWithOthers(stats.totalsByType(), 5) : new ArrayList<CrimeStats.Bar>();
        CrimeStats.Bar big = null;
        for (CrimeStats.Bar x : stats.totalsByType()) if (big == null || x.value > big.value) big = x;
        b.kpiTopType.setText(big == null ? "-" : big.label);
        CrimeStats.Bar area = stats.topRegion();
        b.kpiTopArea.setText(area == null ? "-" : area.label);
        // blue column chart: every police unit with its total, highest first, scroll sideways for all of them
        List<String> dl = new ArrayList<>();
        List<Double> dv = new ArrayList<>();
        if (has) {
            List<CrimeStats.Region> byTotal = new ArrayList<>(stats.regions);
            java.util.Collections.sort(byTotal, (x, y) -> Double.compare(total(y), total(x)));
            for (CrimeStats.Region r : byTotal) {
                dl.add(r.name);
                dv.add(total(r));
            }
        }
        b.districtChart.setData(dl, dv);
        b.txtDistrictNote.setText(has ? stats.period + " · " + dl.size() + " police units, highest first. Swipe sideways to see all. "
                + "The police publish figures per police unit (metropolitan police and ranges), not per district." : "");
        List<Double> vals = new ArrayList<>();
        b.legend.removeAllViews();
        double sum = 0;
        for (CrimeStats.Bar x : top) sum += x.value;
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < top.size(); i++) {
            vals.add(top.get(i).value);
            android.widget.TextView t = new android.widget.TextView(this);
            int pct = sum <= 0 ? 0 : (int) Math.round(top.get(i).value * 100.0 / sum);
            t.setText("\u25CF " + top.get(i).label + "  " + pct + "%");
            t.setTextColor(com.aidalert.sos.view.DonutChartView.COLORS[i % com.aidalert.sos.view.DonutChartView.COLORS.length]);
            t.setTextSize(13);
            t.setPadding(0, (int) (3 * d), 0, (int) (3 * d));
            b.legend.addView(t);
        }
        b.donut.setData(vals, has ? String.format(java.util.Locale.US, "%,.0f", stats.total()) : "");
        if (has) {
            Prefs.of(this).statsHistory(CrimeStats.addToHistory(Prefs.of(this).statsHistory(), stats));
        }
        List<CrimeStats.Bar> hist = CrimeStats.history(Prefs.of(this).statsHistory());
        List<String> hl = new ArrayList<>();
        List<Double> hv = new ArrayList<>();
        for (CrimeStats.Bar x : hist) {
            hl.add(x.label);
            hv.add(x.value);
        }
        b.trend.setData(hl, hv);
        b.trend.setVisibility(hist.size() >= 2 ? View.VISIBLE : View.GONE);
        b.txtTrendNote.setText(hist.size() >= 2 ? "Each point is one monthly police report seen on this phone."
                : "A trend line appears after this phone has seen two or more monthly reports. The next report is loaded around the 10th of the month.");
    }

    /** Every police unit with its total, highest first; tapping one shows its cases by crime type. */
    private void renderAreas() {
        b.areaList.removeAllViews();
        if (stats.isEmpty()) return;
        List<CrimeStats.Region> sorted = new ArrayList<>(stats.regions);
        java.util.Collections.sort(sorted, (x, y) -> Double.compare(total(y), total(x)));
        double max = sorted.isEmpty() ? 1 : Math.max(1, total(sorted.get(0)));
        float d = getResources().getDisplayMetrics().density;
        for (final CrimeStats.Region r : sorted) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.VERTICAL);
            row.setBackgroundResource(com.aidalert.sos.R.drawable.bg_card);
            row.setPadding((int) (14 * d), (int) (10 * d), (int) (14 * d), (int) (10 * d));
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (6 * d);
            row.setLayoutParams(lp);
            android.widget.TextView name = new android.widget.TextView(this);
            name.setText(r.name + "   " + String.format(java.util.Locale.US, "%,.0f", total(r)) + " cases");
            name.setTextColor(0xFF111827);
            name.setTextSize(14);
            name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
            row.addView(name);
            android.widget.TextView sub = new android.widget.TextView(this);
            sub.setText(r.group);
            sub.setTextColor(0xFF6B7280);
            sub.setTextSize(11);
            row.addView(sub);
            android.view.View bar = new android.view.View(this);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(6 * d);
            g.setColor(total(r) / max > 0.66 ? 0xFFC62828 : total(r) / max > 0.33 ? 0xFFEF6C00 : 0xFF2E7D32);
            bar.setBackground(g);
            android.widget.LinearLayout.LayoutParams bp = new android.widget.LinearLayout.LayoutParams(
                    Math.max((int) (6 * d), (int) (240 * d * total(r) / max)), (int) (8 * d));
            bp.topMargin = (int) (6 * d);
            bar.setLayoutParams(bp);
            row.addView(bar);
            row.setOnClickListener(v -> showArea(r));
            b.areaList.addView(row);
        }
    }

    private static double total(CrimeStats.Region r) {
        double t = 0;
        for (Double v : r.counts) t += v == null ? 0 : v;
        return t;
    }

    private void showArea(CrimeStats.Region r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stats.crimeTypes.size() && i < r.counts.size(); i++) {
            sb.append(stats.crimeTypes.get(i)).append(": ")
                    .append(String.format(java.util.Locale.US, "%,.0f", r.counts.get(i))).append('\n');
        }
        sb.append("\nTotal: ").append(String.format(java.util.Locale.US, "%,.0f", total(r)));
        sb.append("\n\nThe police publish these figures per police unit (metropolitan police and ranges), not per district.");
        new AlertDialog.Builder(this).setTitle(r.name + " \u00B7 " + stats.period).setMessage(sb.toString())
                .setPositiveButton("Close", null).show();
    }

    private void render() {
        renderOverview();
        renderAreas();
        if (stats.isEmpty()) {
            b.chart.setVisibility(View.GONE);
            b.txtEmpty.setVisibility(View.VISIBLE);
            b.txtEmpty.setText("No official statistics are bundled in this build yet.\n\nThe control center publishes them from the Bangladesh Police Headquarters crime statistics reports (see docs/CRIME_STATS.md). Nothing is estimated.");
            b.txtSource.setText("");
            b.btnCrimeType.setEnabled(false);
            b.btnGroup.setEnabled(false);
            return;
        }
        b.chart.setVisibility(View.VISIBLE);
        b.txtEmpty.setVisibility(View.GONE);
        b.txtSource.setText("Source: " + stats.source + (stats.period.isEmpty() ? "" : " · " + stats.period));
        b.btnCrimeType.setText(stats.crimeTypes.get(crime));
        b.btnGroup.setText(group.isEmpty() ? "All areas" : group);
        List<CrimeStats.Bar> bars = stats.bars(crime, group);
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (CrimeStats.Bar x : bars) {
            labels.add(x.label);
            values.add(x.value);
        }
        b.txtChartTitle.setText(stats.crimeTypes.get(crime) + " (reported cases)");
        b.chart.setData(labels, values);
    }
}
