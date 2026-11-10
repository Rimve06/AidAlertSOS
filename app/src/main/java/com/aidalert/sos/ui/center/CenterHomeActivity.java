package com.aidalert.sos.ui.center;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.R;
import com.aidalert.sos.auth.AuthManager;
import com.aidalert.sos.community.CommunityActivity;
import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityCenterHomeBinding;
import com.aidalert.sos.engine.SosGuardService;
import com.aidalert.sos.engine.SyncManager;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.AuthActivity;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.RecordsActivity;
import com.aidalert.sos.ui.RowAdapter;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Control Center home: a dashboard instead of one long button row.
 *  - live counters and the SOS cases that need attention first,
 *  - everything else grouped in five sections: Operations, Communication, Community,
 *    Analytics and safety, Administration.
 */
public class CenterHomeActivity extends BaseActivity {
    /** An unanswered SOS older than this turns the red banner on. */
    private static final long UNANSWERED_MS = 2 * 60_000L;

    private ActivityCenterHomeBinding b;
    private Prefs prefs;
    private final RowAdapter adapter = new RowAdapter();
    private final List<Incident> all = new ArrayList<>();
    private View inboxTile;
    private int tick;
    private int unreadChats;
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
        adapter.setOnClick(r -> open((Incident) r.tag));
        b.txtAlert.setOnClickListener(v -> startActivity(new Intent(this, IncidentsActivity.class)));

        // OPERATIONS
        addRow(b.secOps,
                tile(R.drawable.ic_location, "Live map", () -> go(LiveMapActivity.class)),
                tile(R.drawable.ic_history, "All incidents", () -> go(IncidentsActivity.class)),
                tile(R.drawable.ic_shield, "Disaster & safety check-in", () -> go(DisasterAdminActivity.class)));
        // COMMUNICATION
        addRow(b.secComm,
                inboxTile = tile(R.drawable.ic_chat, "Citizen inbox", () -> go(InboxActivity.class)),
                tile(R.drawable.ic_bell, "Broadcast & direct message", () -> go(BroadcastActivity.class)),
                tile(R.drawable.ic_person, "Monitor user chats", () -> go(MonitorActivity.class)));
        // COMMUNITY
        addRow(b.secCommunity,
                tile(R.drawable.ic_group, "Community board (post, comment, moderate)", () -> go(CommunityActivity.class)),
                tile(R.drawable.ic_search, "Missing persons", () -> startActivity(new Intent(this, RecordsActivity.class)
                        .putExtra(RecordsActivity.EXTRA_KIND, RecordsActivity.MISSING))),
                tile(R.drawable.ic_search, "Wanted persons", () -> startActivity(new Intent(this, RecordsActivity.class)
                        .putExtra(RecordsActivity.EXTRA_KIND, RecordsActivity.WANTED))));
        // ANALYTICS
        addRow(b.secAnalytics,
                tile(R.drawable.ic_chart, "Monthly report & safety analysis", () -> go(AnalyticsActivity.class)));
        // ADMIN
        addRow(b.secAdmin,
                tile(R.drawable.ic_phone, "Phone numbers & alert feed", this::numbersDialog),
                tile(R.drawable.ic_tools, "Export CSV", this::exportCsv),
                tile(R.drawable.ic_more, "Log out", this::logout));

        // The center phone also receives SOS text messages and relay packets: keep the service alive.
        try {
            prefs.guardOn(true);
            SosGuardService.start(this);
        } catch (RuntimeException e) {
            android.util.Log.w("AidCenter", "guard service not started", e);
        }
    }

    private void go(Class<?> c) {
        startActivity(new Intent(this, c));
    }

    private void open(Incident i) {
        startActivity(new Intent(this, IncidentDetailActivity.class)
                .putExtra(IncidentDetailActivity.EXTRA_ID, i.id));
    }

    private View tile(int icon, String label, Runnable action) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_center_tile, null, false);
        ((ImageView) v.findViewById(R.id.tileIcon)).setImageResource(icon);
        ((TextView) v.findViewById(R.id.tileLabel)).setText(label);
        v.setOnClickListener(x -> action.run());
        return v;
    }

    /** One row of equal-width tiles (empty cells keep the widths even). */
    private void addRow(LinearLayout section, View... tiles) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (View t : tiles) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(4, 4, 4, 4);
            row.addView(t, lp);
        }
        for (int k = tiles.length; k < 3; k++) {
            View filler = new View(this);
            row.addView(filler, new LinearLayout.LayoutParams(0, 1, 1f));
        }
        section.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
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
            final List<Incident> list = CenterData.parseIncidents(node);
            // Unread citizen messages: checked every 3rd refresh (about every 15 seconds).
            final int chats = (tick++ % 3 == 0) ? InboxData.fetch(getApplicationContext(), rest).unreadChats() : -1;
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                if (chats >= 0) unreadChats = chats;
                all.clear();
                all.addAll(list);
                render();
            });
        });
    }

    private void render() {
        int active = 0, awaiting = 0, dispatched = 0, stale = 0;
        long now = System.currentTimeMillis();
        List<Incident> attention = new ArrayList<>();
        for (Incident i : all) {
            if (!i.isActive()) continue;
            active++;
            if ("NEW".equals(i.status)) {
                awaiting++;
                if (now - i.createdAt > UNANSWERED_MS) stale++;
            }
            if ("DISPATCHED".equals(i.status)) dispatched++;
            attention.add(i);
        }
        // Unanswered first, oldest waiting first; then the rest, newest first.
        Collections.sort(attention, (x, y) -> {
            boolean nx = "NEW".equals(x.status), ny = "NEW".equals(y.status);
            if (nx != ny) return nx ? -1 : 1;
            return nx ? Long.compare(x.createdAt, y.createdAt) : Long.compare(y.createdAt, x.createdAt);
        });
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (int k = 0; k < attention.size() && k < 5; k++) rows.add(CenterData.row(attention.get(k)));
        adapter.submit(rows);
        b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);

        b.kpiActive.setText(String.valueOf(active));
        b.kpiAwaiting.setText(String.valueOf(awaiting));
        b.kpiDispatched.setText(String.valueOf(dispatched));
        b.kpiTotal.setText(String.valueOf(all.size()));
        TextView badge = inboxTile == null ? null : (TextView) inboxTile.findViewById(R.id.tileBadge);
        if (badge != null) {
            badge.setText(unreadChats + " unread");
            badge.setVisibility(unreadChats > 0 ? View.VISIBLE : View.GONE);
        }
        if (stale > 0) {
            b.txtAlert.setText(stale + " SOS unanswered for over 2 minutes. Tap to open the incident list.");
            b.txtAlert.setOnClickListener(v -> go(IncidentsActivity.class));
            b.txtAlert.setVisibility(View.VISIBLE);
        } else if (unreadChats > 0) {
            b.txtAlert.setText(unreadChats + " citizen chat(s) with unread messages. Tap to open the inbox.");
            b.txtAlert.setOnClickListener(v -> go(InboxActivity.class));
            b.txtAlert.setVisibility(View.VISIBLE);
        } else {
            b.txtAlert.setVisibility(View.GONE);
        }
        b.txtSub.setText("Logged in as " + prefs.name() + "  |  " + NetUtil.describe(this)
                + (NetUtil.localIp().isEmpty() ? "" : "  |  LAN socket " + NetUtil.localIp() + ":" + com.aidalert.sos.engine.LanServer.PORT)
                + (prefs.centerPriv().isEmpty() ? "  |  no relay decryption key on this phone" : ""));
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
        AuthManager.signOut();
        prefs.logout();
        Intent i = new Intent(this, AuthActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
