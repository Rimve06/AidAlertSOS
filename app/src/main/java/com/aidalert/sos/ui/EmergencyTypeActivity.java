package com.aidalert.sos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import com.aidalert.sos.ui.BaseActivity;

import com.aidalert.sos.Config;
import com.aidalert.sos.data.Db;
import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityEmergencyTypeBinding;
import com.aidalert.sos.engine.SosEngine;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.CallHelper;
import com.aidalert.sos.util.Geo;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.Notifier;

/**
 * Opens straight away after a screen-on trigger (power x5, volume up hold, SOS button).
 * The alert has ALREADY been sent; here the user says what kind of emergency it is. That choice is
 * forwarded to the control center, which then calls the right service. Contacts get a text and the
 * preselected number is called when the type is chosen (or after a short timeout).
 */
public class EmergencyTypeActivity extends BaseActivity {
    private ActivityEmergencyTypeBinding b;
    private Prefs prefs;
    private long openedAt;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            AppExecutors.mainDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        b = ActivityEmergencyTypeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = Prefs.of(this);
        openedAt = System.currentTimeMillis();
        setTitle("Emergency");
        Notifier.cancel(this, Notifier.ID_SOS_SCREEN);

        b.btnMedical.setOnClickListener(v -> choose("MEDICAL"));
        b.btnAccident.setOnClickListener(v -> choose("ACCIDENT"));
        b.btnFire.setOnClickListener(v -> choose("FIRE"));
        b.btnViolence.setOnClickListener(v -> choose("VIOLENCE"));
        b.btnOther.setOnClickListener(v -> choose("OTHER"));
        b.btnChat.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_CENTER)));
        b.btnForum.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_FORUM)));
        b.btnCallNow.setOnClickListener(v -> {
            if (!CallHelper.place(this, prefs.callNumber())) CallHelper.dial(this, prefs.callNumber());
        });
        b.btnCancel.setOnClickListener(v -> {
            SosEngine.get(this).endSos("CANCELLED");
            Ui.toast(this, "SOS ended. Stay safe.");
            finish();
        });
    }

    private void choose(String type) {
        SosEngine.get(this).setType(type, Ui.clean(b.editNote.getText()));
        Ui.toast(this, "Sent to control center: " + Incident.label(type));
        render();
    }

    private void render() {
        Incident i = SosEngine.get(this).current();
        if (i == null) {
            Incident last = prefs.activeId().isEmpty() ? null : Db.get(this).getIncident(prefs.activeId());
            b.txtState.setText(last == null ? "No active SOS." : "SOS finished: " + last.status.replace('_', ' '));
            b.txtDispatch.setText("");
            return;
        }
        StringBuilder s = new StringBuilder();
        s.append("Type: ").append(Incident.label(i.type)).append("\n");
        s.append(NetUtil.isOnline(this) ? "Network: online" : "Network: OFFLINE - using SMS and nearby phones").append("\n");
        s.append(i.hasFix() ? "Location: " + Geo.pretty(i.lat, i.lon) + " (tracking)" : "Location: waiting for GPS...").append("\n");
        s.append(i.synced ? "Control center: received" : "Control center: not yet by internet");
        if (i.type.equals("UNSPECIFIED") && !prefs.notifiedFor().equals(i.id)) {
            long left = Config.TYPE_PAGE_TIMEOUT_S - (System.currentTimeMillis() - openedAt) / 1000;
            if (left > 0) s.append("\nContacts and the call start in ").append(left).append("s (or as soon as you choose)");
        }
        b.txtState.setText(s.toString());

        StringBuilder d = new StringBuilder("Status: " + i.status.replace('_', ' '));
        if (!i.dispatched.isEmpty()) d.append("\nHelp dispatched: ").append(i.dispatched);
        if (!i.opsNote.isEmpty()) d.append("\nControl center: ").append(i.opsNote);
        b.txtDispatch.setText(d.toString());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openedAt = System.currentTimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppExecutors.cancelMain(tick);
        tick.run();
    }

    @Override
    protected void onPause() {
        AppExecutors.cancelMain(tick);
        super.onPause();
    }
}
