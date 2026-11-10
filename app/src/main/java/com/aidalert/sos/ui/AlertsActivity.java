package com.aidalert.sos.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.alerts.AlertItem;
import com.aidalert.sos.alerts.AlertRepo;
import com.aidalert.sos.alerts.AlertRules;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityAlertsBinding;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.TimeUtil;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Public alerts: earthquakes (USGS), disasters (GDACS), the control center's XML feed and its messages.
 * Works offline from the last saved result. Each alert shows where it came from. These are information
 * feeds, not a guarantee: the local authority (BMD, FFWC, DDM) is always the official voice.
 */
public class AlertsActivity extends BaseActivity {
    private ActivityAlertsBinding b;
    private final RowAdapter adapter = new RowAdapter();
    private AlertRepo.Result result = new AlertRepo.Result();
    private String filter = AlertRules.F_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAlertsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Public alerts");
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> detail((AlertItem) r.tag));
        b.fAll.setOnClickListener(v -> setFilter(AlertRules.F_ALL));
        b.fEmergency.setOnClickListener(v -> setFilter(AlertRules.F_DISASTER));
        b.fWeather.setOnClickListener(v -> setFilter(AlertRules.F_QUAKE));
        b.fHealth.setOnClickListener(v -> setFilter(AlertRules.F_HEALTH));
        b.fLaw.setOnClickListener(v -> setFilter(AlertRules.F_LAW));
        b.fNews.setOnClickListener(v -> setFilter(AlertRules.F_NEWS));
        b.fIntl.setOnClickListener(v -> setFilter(AlertRules.F_WORLD));
        b.btnRefresh.setOnClickListener(v -> load());
        b.btnSources.setOnClickListener(v -> sources());
        b.btnRegion.setOnClickListener(v -> pickRegion());
        updateRegionButton();
        // show the saved alerts at once, then update
        result = AlertRepo.cached(Prefs.of(this), null);
        render();
        load();
    }

    private void setFilter(String f) {
        filter = f;
        render();
    }

    private void load() {
        b.btnRefresh.setEnabled(false);
        b.txtStatus.setText("Updating...");
        AppExecutors.io(() -> {
            final AlertRepo.Result r = AlertRepo.fetch(getApplicationContext());
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                result = r;
                b.btnRefresh.setEnabled(true);
                render();
            });
        });
    }

    /** How far to trust an alert: control center and agency feeds are not the same as a news headline. */
    private static String trust(AlertItem a) {
        if (a.official) return "Official";
        String s = a.source == null ? "" : a.source;
        if (s.startsWith("USGS") || s.startsWith("GDACS")) return "Agency";
        return "News report";
    }

    private void pickRegion() {
        final java.util.List<String> c = com.aidalert.sos.alerts.Regions.choicesWithDistricts();
        new AlertDialog.Builder(this).setTitle("Show alerts for")
                .setItems(c.toArray(new CharSequence[0]), (d, i) -> {
                    Prefs.of(this).alertRegion(i == 0 ? "" : c.get(i));
                    updateRegionButton();
                    render();
                }).show();
    }

    private void updateRegionButton() {
        String r = Prefs.of(this).alertRegion();
        b.btnRegion.setText(r.isEmpty() ? "All Bangladesh" : r);
    }

    private void render() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        List<AlertItem> ordered = new ArrayList<>(result.items);
        java.util.Collections.sort(ordered, AlertRules::order);       // Bangladesh first, then international
        for (AlertItem a : ordered) {
            boolean world = AlertItem.SCOPE_WORLD.equals(a.scope);
            if (!AlertRules.matchesFilter(filter, a.kind, a.category, a.scope)) continue;
            // The area picker applies to Bangladesh items; international alerts are never hidden by it.
            if (!world && !com.aidalert.sos.alerts.Regions.relevant(a.title + " " + a.body + " " + a.area + " " + a.district,
                    Prefs.of(this).alertRegion())) continue;
            String when = a.timeMs == 0 ? "" : TimeUtil.ago(a.timeMs) + " · ";
            int color = a.severity >= 2 ? 0xFFC62828 : a.severity == 1 ? 0xFFEF6C00 : 0xFF2E7D32;
            String level = a.severity >= 2 ? "Severe" : a.severity == 1 ? "Caution" : "Info";
            String tag = AlertRules.tagFor(a.kind, a.category, a.scope);
            String badge = level + "\n" + trust(a);
            String where = world ? "International" : a.district.isEmpty() ? "Nationwide" : a.district;
            rows.add(new RowAdapter.Row(a.title, tag + " · " + where + " · " + when + a.source, badge, color, a));
        }
        adapter.submit(rows);
        b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.txtEmpty.setText(result.items.isEmpty()
                ? "No alerts saved yet. Connect to the internet and tap Refresh."
                : filter.equals(AlertRules.F_LAW)
                ? "No law-and-order alerts found for this area. That is not proof that there are none: check the Official sources button."
                : "No alerts of this type right now.");
        StringBuilder s = new StringBuilder();
        s.append(result.fromCache ? "Saved copy" : "Updated");
        if (result.fetchedAt > 0) {
            s.append(": ").append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(result.fetchedAt)));
        }
        for (String e : result.errors) s.append("\n").append(e);
        b.txtStatus.setText(s.toString());
    }

    private void detail(AlertItem a) {
        AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle(a.title)
                .setMessage(a.body + "\n\n" + (a.official ? "OFFICIAL notice from the control center."
                        : AlertItem.CAT_LAW.equals(a.category) || AlertItem.CAT_NEWS.equals(a.category)
                        ? "NEWS REPORT. This is not an official order. Only the police or district administration can impose restrictions."
                        : "") + "\n\nSource: " + a.source
                        + (a.timeMs == 0 ? "" : "\nTime: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(a.timeMs)))
                        + "\n\nCheck the official Bangladesh authority for instructions in your area.")
                .setNegativeButton("Close", null);
        if (a.link != null && a.link.startsWith("http")) d.setPositiveButton("Open source", (x, w) -> open(a.link, a.source));
        d.show();
    }

    private void sources() {
        final java.util.List<com.aidalert.sos.news.FeedConfig.Feed> pages = com.aidalert.sos.news.FeedConfig.load(this).pages;
        final String[] names = new String[pages.size()];
        for (int i = 0; i < names.length; i++) names[i] = pages.get(i).name;
        new AlertDialog.Builder(this).setTitle("Official sources (opens inside the app)")
                .setItems(names, (d, i) -> open(pages.get(i).url, pages.get(i).name)).show();
    }

    private void open(String url, String title) {
        startActivity(WebActivity.open(this, url, title));
    }
}
