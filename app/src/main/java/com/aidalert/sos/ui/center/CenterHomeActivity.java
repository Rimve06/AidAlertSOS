package com.aidalert.sos.ui.center;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import com.aidalert.sos.ui.BaseActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityCenterHomeBinding;
import com.aidalert.sos.engine.SosGuardService;
import com.aidalert.sos.engine.SyncManager;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.AuthActivity;
import com.aidalert.sos.ui.RecordsActivity;
import com.aidalert.sos.ui.RowAdapter;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.TimeUtil;
import com.aidalert.sos.view.MapView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Control center dashboard: live incident list and map, newest first. Also the entry point to
 * disaster check-ins, messaging, records, chat monitoring and the dispatch phone numbers.
 */
public class CenterHomeActivity extends BaseActivity {
    private ActivityCenterHomeBinding b;
    private Prefs prefs;
    private final RowAdapter adapter = new RowAdapter();
    private final List<Incident> all = new ArrayList<>();
    private boolean activeOnly = true;
    private boolean showMap;
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            load();
            AppExecutors.mainDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityCenterHomeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = Prefs.of(this);
        setTitle("Control center");

        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> startActivity(new Intent(this, IncidentDetailActivity.class)
                .putExtra(IncidentDetailActivity.EXTRA_ID, ((Incident) r.tag).id)));

        b.btnDisaster.setOnClickListener(v -> startActivity(new Intent(this, DisasterAdminActivity.class)));
        b.btnBroadcast.setOnClickListener(v -> startActivity(new Intent(this, BroadcastActivity.class)));
        b.btnRecords.setOnClickListener(v -> startActivity(new Intent(this, RecordsActivity.class)));
        b.btnMonitor.setOnClickListener(v -> startActivity(new Intent(this, MonitorActivity.class)));
        b.btnCenterSettings.setOnClickListener(v -> numbersDialog());
        b.btnExport.setOnClickListener(v -> exportCsv());
        b.btnLogout.setOnClickListener(v -> logout());
        b.btnShowActive.setOnClickListener(v -> {
            activeOnly = true;
            render();
        });
        b.btnShowAll.setOnClickListener(v -> {
            activeOnly = false;
            render();
        });
        b.btnToggleMap.setOnClickListener(v -> {
            showMap = !showMap;
            render();
        });
        b.mapAll.setOnDotClickListener((index, dot) -> {
            if (dot.tag instanceof Incident) {
                startActivity(new Intent(this, IncidentDetailActivity.class)
                        .putExtra(IncidentDetailActivity.EXTRA_ID, ((Incident) dot.tag).id));
            }
        });
        // The center phone also receives SOS text messages and relay packets: keep the service alive.
        prefs.guardOn(true);
        SosGuardService.start(this);
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
        if (!NetUtil.isOnline(this)) {
            b.txtSub.setText("Offline: SOS text messages still arrive on this phone and upload when internet returns.");
            return;
        }
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            SyncManager.ingestRelay(getApplicationContext());
            JSONObject node = rest.getObj("incidents");
            final List<Incident> list = new ArrayList<>();
            if (node != null) {
                Iterator<String> it = node.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject o = node.optJSONObject(k);
                    if (o != null) list.add(Incident.fromFirebase(k, o));
                }
                Collections.sort(list, (x, y) -> Long.compare(y.createdAt, x.createdAt));
            }
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                all.clear();
                all.addAll(list);
                render();
            });
        });
    }

    private void render() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        List<MapView.Dot> dots = new ArrayList<>();
        int active = 0;
        for (Incident i : all) {
            if (i.isActive()) active++;
            if (activeOnly && !i.isActive()) continue;
            String sub = (i.userName.isEmpty() ? "Unknown" : i.userName) + "  " + i.phone + "\n"
                    + TimeUtil.ago(i.createdAt) + "  |  via " + i.via
                    + (i.hasFix() ? "" : "  |  NO GPS YET") + (i.silent ? "  |  SILENT" : "")
                    + (i.dispatched.isEmpty() ? "" : "\nDispatched: " + i.dispatched);
            rows.add(new RowAdapter.Row(Incident.label(i.type), sub, i.status.replace('_', ' '), Incident.color(i.type), i));
            if (i.hasFix()) {
                MapView.Dot d = new MapView.Dot();
                d.lat = i.lat;
                d.lon = i.lon;
                d.color = Incident.color(i.type);
                d.label = i.userName;
                d.tag = i;
                dots.add(d);
            }
        }
        b.txtSummary.setText(active + " active  |  " + all.size() + " total incidents");
        b.txtSub.setText("Logged in as " + prefs.name() + "  |  " + NetUtil.describe(this)
                + (NetUtil.localIp().isEmpty() ? "" : "  |  LAN socket " + NetUtil.localIp() + ":" + com.aidalert.sos.engine.LanServer.PORT)
                + (prefs.centerPriv().isEmpty() ? "  |  no relay decryption key on this phone" : ""));
        adapter.submit(rows);
        b.mapAll.setDots(dots);
        b.mapAll.setVisibility(showMap ? View.VISIBLE : View.GONE);
        b.list.setVisibility(showMap ? View.GONE : View.VISIBLE);
        b.txtEmpty.setVisibility(!showMap && rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.btnToggleMap.setText(showMap ? "List" : "Map");
    }

    /** Dispatch numbers shared with every citizen app and used by the "call" shortcuts. */
    private void numbersDialog() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 16, 48, 0);
        final EditText phone = num("Control center phone (receives SOS texts / calls)", prefs.centerPhone());
        final EditText amb = num("Ambulance", prefs.numAmbulance());
        final EditText fire = num("Fire service", prefs.numFire());
        final EditText police = num("Police", prefs.numPolice());
        final EditText rescue = num("National rescue", prefs.numRescue());
        final EditText feed = new EditText(this);
        feed.setHint("XML alert feed URL (RSS / Atom / CAP)");
        feed.setText(prefs.feedUrl());
        feed.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(phone);
        box.addView(amb);
        box.addView(fire);
        box.addView(police);
        box.addView(rescue);
        box.addView(feed);
        new AlertDialog.Builder(this).setTitle("Phone numbers").setView(box)
                .setPositiveButton("Publish", (d, w) -> {
                    final String p = Ui.clean(phone.getText()), a = Ui.clean(amb.getText()), f = Ui.clean(fire.getText()),
                            po = Ui.clean(police.getText()), r = Ui.clean(rescue.getText());
                    final String fu = Ui.clean(feed.getText());
                    prefs.feedUrl(fu);
                    prefs.centerPhone(p);
                    prefs.numAmbulance(a);
                    prefs.numFire(f);
                    prefs.numPolice(po);
                    prefs.numRescue(r);
                    AppExecutors.io(() -> {
                        FirebaseRest rest = new FirebaseRest(this);
                        boolean ok = true;
                        if (!p.isEmpty()) ok &= rest.putString("config/center/phone", p);
                        ok &= rest.putString("config/center/ambulance", a);
                        ok &= rest.putString("config/center/fire", f);
                        ok &= rest.putString("config/center/police", po);
                        ok &= rest.putString("config/center/rescue", r);
                        ok &= rest.putString("config/center/feedUrl", fu);
                        final boolean fin = ok;
                        AppExecutors.main(() -> Ui.toast(this, fin ? "Numbers published to all users" : "Failed (offline?)"));
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }

    private EditText num(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(InputType.TYPE_CLASS_PHONE);
        return e;
    }

    /** Incident records for authorized review: shared as CSV text through any app (implicit intent). */
    private void exportCsv() {
        StringBuilder sb = new StringBuilder(Incident.csvHeader()).append('\n');
        for (Incident i : all) sb.append(i.csvRow()).append('\n');
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "AidAlert incident report");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(send, "Share incident report"));
    }

    private void logout() {
        SosGuardService.stop(this);
        prefs.logout();
        Intent i = new Intent(this, AuthActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
