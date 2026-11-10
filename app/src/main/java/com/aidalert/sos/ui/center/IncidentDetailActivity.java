package com.aidalert.sos.ui.center;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.aidalert.sos.ui.BaseActivity;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityIncidentDetailBinding;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.ChatActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.CallHelper;
import com.aidalert.sos.util.Geo;
import com.aidalert.sos.util.TimeUtil;
import com.aidalert.sos.view.MapView;
import com.aidalert.sos.view.TileMapView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * One incident from the operator's side: live position and trail, helpers on the way and the
 * workflow acknowledge -> confirm with the user -> dispatch services -> resolve.
 * Dialing a number opens the dialer; a human operator presses call (no automatic dispatch).
 */
public class IncidentDetailActivity extends BaseActivity {
    public static final String EXTRA_ID = "incident_id";

    private ActivityIncidentDetailBinding b;
    private Prefs prefs;
    private String id;
    private Incident inc;
    private int trailPoints;
    private boolean mapFitted;
    private int helperCount;
    private final List<MapView.Dot> helperDots = new ArrayList<>();
    private final List<double[]> trail = new ArrayList<>();
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            load();
            AppExecutors.mainDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityIncidentDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = Prefs.of(this);
        id = getIntent().getStringExtra(EXTRA_ID);
        setTitle("Incident");

        b.btnAck.setOnClickListener(v -> setStatus("ACKNOWLEDGED", null));
        b.btnConfirm.setOnClickListener(v -> setStatus("CONFIRMED", null));
        b.btnResolve.setOnClickListener(v -> setStatus("RESOLVED", null));
        b.btnFalse.setOnClickListener(v -> setStatus("FALSE_ALARM", null));
        b.btnCallUser.setOnClickListener(v -> {
            if (inc != null) CallHelper.dial(this, "+" + inc.phone.replace("+", ""));
        });
        b.btnChatUser.setOnClickListener(v -> {
            if (inc == null) return;
            startActivity(new Intent(this, ChatActivity.class)
                    .putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_CENTER)
                    .putExtra(ChatActivity.EXTRA_TARGET_UID, inc.userId)
                    .putExtra(ChatActivity.EXTRA_TARGET_NAME, inc.userName));
        });
        b.btnAmbulance.setOnClickListener(v -> dispatch("Ambulance", prefs.numAmbulance()));
        b.btnFireSvc.setOnClickListener(v -> dispatch("Fire service", prefs.numFire()));
        b.btnPolice.setOnClickListener(v -> dispatch("Police", prefs.numPolice()));
        b.btnRescue.setOnClickListener(v -> dispatch("National rescue", prefs.numRescue()));
        b.btnHelpers.setOnClickListener(v -> askHelpers());
        b.btnMapApp.setOnClickListener(v -> {
            if (inc != null && inc.hasFix()) Ui.openMap(this, inc.lat, inc.lon, inc.userName);
            else Ui.toast(this, "No GPS fix yet");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppExecutors.cancelMain(poll);
        poll.run();
    }

    @Override
    protected void onPause() {
        AppExecutors.cancelMain(poll);
        super.onPause();
    }

    private void load() {
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            JSONObject node = rest.getObj("incidents/" + id);
            if (node == null) return;
            final Incident i = Incident.fromFirebase(id, node);

            final List<double[]> pts = new ArrayList<>();
            JSONObject tr = rest.getObj("tracks/" + id);
            if (tr != null) {
                List<double[]> tmp = new ArrayList<>();
                Iterator<String> it = tr.keys();
                while (it.hasNext()) {
                    JSONObject p = tr.optJSONObject(it.next());
                    if (p != null) tmp.add(new double[]{p.optDouble("lat"), p.optDouble("lon"), p.optLong("t")});
                }
                Collections.sort(tmp, (x, y) -> Double.compare(x[2], y[2]));
                pts.addAll(tmp);
            }
            final List<MapView.Dot> helpers = new ArrayList<>();
            JSONObject hr = rest.getObj("helpResponses/" + id);
            if (hr != null) {
                Iterator<String> it = hr.keys();
                while (it.hasNext()) {
                    JSONObject h = hr.optJSONObject(it.next());
                    if (h == null || !h.has("lat")) continue;
                    MapView.Dot d = new MapView.Dot();
                    d.lat = h.optDouble("lat");
                    d.lon = h.optDouble("lon");
                    d.color = 0xFF2E7D32;
                    d.label = "Helper: " + h.optString("name") + " " + h.optString("phone");
                    helpers.add(d);
                }
            }
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                inc = i;
                trail.clear();
                trail.addAll(pts);
                helperDots.clear();
                helperDots.addAll(helpers);
                trailPoints = pts.size();
                helperCount = helpers.size();
                render();
            });
        });
    }

    private void render() {
        if (inc == null) return;
        setTitle(Incident.label(inc.type));
        b.txtHead.setText(Incident.label(inc.type) + "  -  " + inc.status.replace('_', ' '));

        StringBuilder s = new StringBuilder();
        s.append("Name: ").append(inc.userName).append("\nPhone: +").append(inc.phone.replace("+", "")).append("\n");
        s.append("Time: ").append(TimeUtil.clock(inc.createdAt)).append(" (").append(TimeUtil.ago(inc.createdAt)).append(")\n");
        s.append("Trigger: ").append(inc.source).append("  |  channel: ").append(inc.via).append("\n");
        s.append(inc.hasFix() ? "Location: " + Geo.pretty(inc.lat, inc.lon) + " (+/-" + (int) inc.acc + " m), updated "
                + TimeUtil.ago(inc.updatedAt) : "Location: not available yet").append("\n");
        if (inc.battery >= 0) s.append("Battery: ").append(inc.battery).append("%\n");
        if (!inc.bloodGroup.isEmpty()) s.append("Blood group: ").append(inc.bloodGroup).append("\n");
        if (!inc.medical.isEmpty()) s.append("Medical: ").append(inc.medical).append("\n");
        if (!inc.note.isEmpty()) s.append("User note: ").append(inc.note).append("\n");
        if (!inc.dispatched.isEmpty()) s.append("Dispatched: ").append(inc.dispatched).append("\n");
        s.append("Trail points: ").append(trailPoints).append("  |  helpers on the way: ").append(helperCount);
        b.txtInfo.setText(s.toString());

        if (inc.silent) {
            b.txtHint.setVisibility(View.VISIBLE);
            b.txtHint.setText("SILENT SOS (violence): the phone's screen stayed off and it called the preselected number. "
                    + "If that number is this control center, answer the incoming call and listen to the surroundings. "
                    + "Do not call back loudly: use Chat or dispatch police directly.");
        } else {
            b.txtHint.setVisibility(View.GONE);
        }

        // Real OpenStreetMap: the latest tracked point is the person's current position.
        List<TileMapView.Marker> markers = new ArrayList<>();
        List<double[]> fit = new ArrayList<>();
        double lat = inc.lat, lon = inc.lon;
        boolean pos = inc.hasFix();
        if (!trail.isEmpty()) {
            double[] last = trail.get(trail.size() - 1);
            lat = last[0];
            lon = last[1];
            pos = true;
        }
        if (pos) {
            TileMapView.Marker m = new TileMapView.Marker();
            m.lat = lat;
            m.lon = lon;
            m.color = Incident.color(inc.type);
            m.label = inc.userName.isEmpty() ? "SOS" : inc.userName;
            markers.add(m);
            fit.add(new double[]{lat, lon});
        }
        for (MapView.Dot d : helperDots) {
            TileMapView.Marker m = new TileMapView.Marker();
            m.lat = d.lat;
            m.lon = d.lon;
            m.color = d.color;
            m.label = d.label;
            markers.add(m);
            fit.add(new double[]{d.lat, d.lon});
        }
        List<TileMapView.Line> lines = new ArrayList<>();
        if (trail.size() > 1) {
            TileMapView.Line ln = new TileMapView.Line();
            ln.color = Incident.color(inc.type);
            ln.widthDp = 3;
            for (double[] p : trail) ln.points.add(new double[]{p[0], p[1]});
            lines.add(ln);
        }
        b.mapOne.setMarkers(markers);
        b.mapOne.setLines(lines);
        if (!mapFitted && !fit.isEmpty() && b.mapOne.getWidth() > 0) {
            mapFitted = true;
            if (fit.size() == 1) {
                b.mapOne.setCenter(fit.get(0)[0], fit.get(0)[1]);
                b.mapOne.setZoom(15);
            } else {
                b.mapOne.fitTo(fit);
            }
        }
    }

    private void setStatus(String status, String dispatchedAdd) {
        if (inc == null) return;
        inc.status = status;
        inc.ackBy = prefs.name();
        inc.opsAt = System.currentTimeMillis();
        String note = Ui.clean(b.editNote.getText());
        if (!note.isEmpty()) inc.opsNote = note;
        if (dispatchedAdd != null) {
            inc.dispatched = inc.dispatched.isEmpty() ? dispatchedAdd : inc.dispatched + ", " + dispatchedAdd;
        }
        final JSONObject ops = inc.toOpsJson();
        AppExecutors.io(() -> {
            boolean ok = new FirebaseRest(this).put("incidents/" + id + "/ops", ops);
            AppExecutors.main(() -> {
                Ui.toast(this, ok ? "Updated: " + status.replace('_', ' ') : "Failed to update (offline?)");
                render();
            });
        });
    }

    private void dispatch(String service, String number) {
        if (inc == null) return;
        if (!inc.hasFix()) Ui.toast(this, "No GPS fix yet: ask the user for the address first.");
        String when = TimeUtil.clock(System.currentTimeMillis());
        setStatus("DISPATCHED", service + " (" + when + ")");
        CallHelper.dial(this, number);
    }

    private void askHelpers() {
        if (inc == null) return;
        if (!inc.hasFix()) {
            Ui.toast(this, "Need a GPS position first");
            return;
        }
        final JSONObject req = new JSONObject();
        try {
            req.put("incidentId", inc.id);
            req.put("requesterUid", inc.userId);
            req.put("type", inc.type);
            req.put("lat", inc.lat);
            req.put("lon", inc.lon);
            req.put("radiusKm", 3);
            req.put("active", true);
            req.put("ts", System.currentTimeMillis());
        } catch (JSONException e) {
            return;
        }
        // Helpers' phones compare the distance themselves; nobody's position is uploaded until they accept.
        AppExecutors.io(() -> {
            boolean ok = new FirebaseRest(this).put("helpRequests/" + id, req);
            AppExecutors.main(() -> Ui.toast(this, ok ? "Request sent to volunteer helpers within 3 km" : "Failed (offline?)"));
        });
    }
}
