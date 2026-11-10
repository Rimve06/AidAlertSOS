package com.aidalert.sos.ui.center;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.databinding.ActivityLiveMapBinding;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.TimeUtil;
import com.aidalert.sos.view.TileMapView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Control center live map: a real OpenStreetMap (TileMapView) with
 *  - every active SOS at its LATEST tracked position (not just the first fix), with its movement trail,
 *  - a "stale" marker (grey) when the last position is older than 5 minutes, so an old fix is never shown as live,
 *  - people who switched on disaster distress and are checking in.
 * Ordinary users' locations are never uploaded, so they cannot appear here. Refreshes every 10 seconds.
 */
public class LiveMapActivity extends BaseActivity {
    private static final long REFRESH_MS = 10_000;
    private static final String LAST_30 = "orderBy=%22%24key%22&limitToLast=30";
    private static final int STALE_COLOR = 0xFF8D96A3;

    /** One active SOS with its freshest known position. */
    private static final class Live {
        Incident inc;
        double lat, lon;
        long fixAt;
        final List<double[]> trail = new ArrayList<>();

        boolean hasPos() {
            return !Double.isNaN(lat) && !Double.isNaN(lon);
        }

        boolean stale() {
            return System.currentTimeMillis() - fixAt > CenterData.STALE_MS;
        }
    }

    private ActivityLiveMapBinding b;
    private final List<Live> live = new ArrayList<>();
    private final List<String[]> checkins = new ArrayList<>(); // name, status, lat, lon, ago
    private Object selected;
    private boolean fitted;
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            load();
            AppExecutors.mainDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLiveMapBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Live map");
        b.map.setCenter(23.80, 90.40);   // Bangladesh until the first SOS arrives
        b.map.setZoom(6.5);
        b.chkSos.setOnCheckedChangeListener((v, c) -> render());
        b.chkDistress.setOnCheckedChangeListener((v, c) -> render());
        b.chkTrail.setOnCheckedChangeListener((v, c) -> render());
        b.map.setOnMarkerClick(m -> select(m.tag));
        b.btnOpenIncident.setOnClickListener(v -> {
            if (selected instanceof Live) {
                startActivity(new Intent(this, IncidentDetailActivity.class)
                        .putExtra(IncidentDetailActivity.EXTRA_ID, ((Live) selected).inc.id));
            }
        });
        b.btnFit.setOnClickListener(v -> fitAll());
        b.btnMaps.setOnClickListener(v -> openNavigation());
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppExecutors.cancelMain(refresher);
        refresher.run();
    }

    @Override
    protected void onPause() {
        AppExecutors.cancelMain(refresher);
        super.onPause();
    }

    private void load() {
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            JSONObject inc = rest.getObj("incidents");
            JSONObject chk = rest.getObj("checkins");
            final List<Live> ll = new ArrayList<>();
            for (Incident i : CenterData.parseIncidents(inc)) {
                if (!i.isActive() || ll.size() >= 40) continue;
                Live l = new Live();
                l.inc = i;
                l.lat = i.lat;
                l.lon = i.lon;
                l.fixAt = Math.max(i.updatedAt, i.createdAt);
                // Latest tracked points: the newest one is the person's current position.
                JSONObject tr = rest.getQuery("tracks/" + i.id, LAST_30);
                if (tr != null) {
                    List<double[]> pts = new ArrayList<>();
                    Iterator<String> it = tr.keys();
                    while (it.hasNext()) {
                        JSONObject p = tr.optJSONObject(it.next());
                        if (p != null && p.has("lat") && p.has("lon")) {
                            pts.add(new double[]{p.optDouble("lat"), p.optDouble("lon"), p.optLong("t")});
                        }
                    }
                    Collections.sort(pts, (x, y) -> Double.compare(x[2], y[2]));
                    if (!pts.isEmpty()) {
                        double[] last = pts.get(pts.size() - 1);
                        l.lat = last[0];
                        l.lon = last[1];
                        if (last[2] > 0) l.fixAt = (long) last[2];
                        l.trail.addAll(pts);
                    }
                }
                ll.add(l);
            }
            final List<String[]> lc = new ArrayList<>();
            if (chk != null) {
                Iterator<String> it = chk.keys();
                while (it.hasNext()) {
                    JSONObject c = chk.optJSONObject(it.next());
                    if (c == null || !c.has("lat")) continue;
                    lc.add(new String[]{c.optString("name", "?"), c.optString("status", ""),
                            String.valueOf(c.optDouble("lat")), String.valueOf(c.optDouble("lon")),
                            TimeUtil.ago(c.optLong("ts"))});
                }
            }
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                live.clear();
                live.addAll(ll);
                checkins.clear();
                checkins.addAll(lc);
                render();
                if (!fitted && b.map.getWidth() > 0) fitAll();
            });
        });
    }

    private void render() {
        List<TileMapView.Marker> markers = new ArrayList<>();
        List<TileMapView.Line> lines = new ArrayList<>();
        int noFix = 0, stale = 0;
        for (Live l : live) {
            if (!l.hasPos()) {
                noFix++;
                continue;
            }
            if (l.stale()) stale++;
            if (!b.chkSos.isChecked()) continue;
            TileMapView.Marker m = new TileMapView.Marker();
            m.lat = l.lat;
            m.lon = l.lon;
            m.color = l.stale() ? STALE_COLOR : Incident.color(l.inc.type);
            m.label = (l.inc.userName.isEmpty() ? "SOS" : l.inc.userName) + (l.stale() ? " (old fix)" : "");
            m.tag = l;
            markers.add(m);
            if (b.chkTrail.isChecked() && l.trail.size() > 1) {
                TileMapView.Line ln = new TileMapView.Line();
                ln.color = Incident.color(l.inc.type);
                ln.widthDp = 3;
                for (double[] p : l.trail) ln.points.add(new double[]{p[0], p[1]});
                lines.add(ln);
            }
        }
        int distressed = 0;
        if (b.chkDistress.isChecked()) {
            for (String[] c : checkins) {
                boolean help = "DISTRESSED".equals(c[1]);
                if (help) distressed++;
                TileMapView.Marker m = new TileMapView.Marker();
                m.lat = Double.parseDouble(c[2]);
                m.lon = Double.parseDouble(c[3]);
                m.color = help ? 0xFFC62828 : 0xFF2E7D32;
                m.label = help ? c[0] : "";
                m.tag = c;
                markers.add(m);
            }
        }
        b.map.setMarkers(markers);
        b.map.setLines(lines);
        b.txtCounts.setText(live.size() + " active SOS (" + noFix + " no GPS yet, " + stale + " old fix)  |  "
                + checkins.size() + " check-ins, " + distressed + " need rescue");
    }

    private void fitAll() {
        List<double[]> pts = new ArrayList<>();
        for (Live l : live) if (l.hasPos()) pts.add(new double[]{l.lat, l.lon});
        for (String[] c : checkins) pts.add(new double[]{Double.parseDouble(c[2]), Double.parseDouble(c[3])});
        if (pts.isEmpty()) return;
        fitted = true;
        if (pts.size() == 1) {
            b.map.setCenter(pts.get(0)[0], pts.get(0)[1]);
            b.map.setZoom(15);
        } else {
            b.map.fitTo(pts);
        }
    }

    private void select(Object tag) {
        selected = tag;
        if (tag instanceof Live) {
            Live l = (Live) tag;
            Incident i = l.inc;
            b.txtSelected.setText(Incident.label(i.type) + "  |  " + i.status.replace('_', ' ') + "\n"
                    + (i.userName.isEmpty() ? "Unknown" : i.userName) + "  " + i.phone + "\n"
                    + "SOS " + TimeUtil.ago(i.createdAt) + "  |  location " + (l.stale() ? "OLD, " : "live, ") + TimeUtil.ago(l.fixAt)
                    + (i.acc > 0 ? "  |  accuracy " + Math.round(i.acc) + " m" : "")
                    + "\nvia " + i.via
                    + (i.battery >= 0 ? "  |  battery " + i.battery + "%" : "")
                    + (i.silent ? "  |  SILENT" : "")
                    + (i.dispatched.isEmpty() ? "" : "\nDispatched: " + i.dispatched));
            b.btnOpenIncident.setEnabled(true);
            b.btnMaps.setEnabled(true);
        } else if (tag instanceof String[]) {
            String[] c = (String[]) tag;
            b.txtSelected.setText(c[0] + "  |  " + c[1] + "\nLast check-in " + c[4]);
            b.btnOpenIncident.setEnabled(false);
            b.btnMaps.setEnabled(true);
        }
    }

    private void openNavigation() {
        double lat, lon;
        if (selected instanceof Live) {
            lat = ((Live) selected).lat;
            lon = ((Live) selected).lon;
        } else if (selected instanceof String[]) {
            lat = Double.parseDouble(((String[]) selected)[2]);
            lon = Double.parseDouble(((String[]) selected)[3]);
        } else {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f", lat, lon, lat, lon))));
        } catch (RuntimeException e) {
            Ui.toast(this, "No map app found.");
        }
    }
}
