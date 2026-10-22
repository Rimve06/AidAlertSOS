package com.aidalert.sos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityHomeBinding;
import com.aidalert.sos.engine.SosEngine;
import com.aidalert.sos.engine.SosGuardService;
import com.aidalert.sos.net.Crypto;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.Perms;

/** Citizen home: on-screen SOS button, trigger help, disaster toggles and navigation. */
public class HomeActivity extends AppCompatActivity {
    private ActivityHomeBinding b;
    private Prefs prefs;
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refresh();
            AppExecutors.mainDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = Prefs.of(this);
        setTitle("AidAlert SOS");

        b.holdSos.setHoldMs(1500);
        b.holdSos.setOnHoldCompleteListener(() -> {
            if (!Perms.location(this)) {
                ActivityCompat.requestPermissions(this, Perms.locationPerms(), 5);
            }
            AppExecutors.io(() -> SosEngine.get(getApplicationContext()).trigger("SCREEN_BUTTON", false));
            AppExecutors.mainDelayed(this::openTypePage, 300);
        });

        b.btnOpenSos.setOnClickListener(v -> openTypePage());
        b.btnEndSos.setOnClickListener(v -> askEnd());
        b.btnCenterChat.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_CENTER)));
        b.btnForum.setOnClickListener(v -> {
            if (!prefs.sosActive()) {
                Ui.toast(this, "Community chat unlocks only while your own SOS is active.");
                return;
            }
            startActivity(new Intent(this, ChatActivity.class).putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_FORUM));
        });
        b.btnSearch.setOnClickListener(v -> startActivity(new Intent(this, SearchUsersActivity.class)));
        b.btnRecords.setOnClickListener(v -> startActivity(new Intent(this, RecordsActivity.class)));
        b.btnContacts.setOnClickListener(v -> startActivity(new Intent(this, ContactsActivity.class)));
        b.btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        b.btnHistory.setOnClickListener(v -> startActivity(new Intent(this, ReportsActivity.class)));
        b.btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        b.switchDistress.setOnCheckedChangeListener((btn, on) -> {
            if (!btn.isPressed()) return; // ignore programmatic changes from refresh()
            if (on && !Perms.location(this)) {
                ActivityCompat.requestPermissions(this, Perms.locationPerms(), 6);
            }
            SosEngine.get(this).setDistress(on, "");
            Ui.toast(this, on ? "Your location is now shared with rescuers." : "Location sharing stopped and your record deleted.");
        });
        b.switchHelper.setOnCheckedChangeListener((btn, on) -> {
            if (!btn.isPressed()) return;
            if (on && !Perms.location(this)) {
                ActivityCompat.requestPermissions(this, Perms.locationPerms(), 7);
            }
            prefs.helperOn(on);
        });
        b.btnSafe.setOnClickListener(v -> {
            if (!Perms.location(this)) {
                ActivityCompat.requestPermissions(this, Perms.locationPerms(), 8);
                return;
            }
            SosEngine.get(this).checkinOnce("SAFE", "");
            Ui.toast(this, "Check-in sent: you are marked safe.");
        });
    }

    private void openTypePage() {
        startActivity(new Intent(this, EmergencyTypeActivity.class));
    }

    private void askEnd() {
        final Incident cur = SosEngine.get(this).current();
        if (cur == null) {
            refresh();
            return;
        }
        if (cur.silent) {
            // A silent SOS is ended only with the account password, so an attacker cannot stop it.
            final EditText pw = new EditText(this);
            pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            pw.setHint("Account password");
            new AlertDialog.Builder(this)
                    .setTitle("End silent SOS")
                    .setView(pw)
                    .setPositiveButton("End SOS", (d, w) -> {
                        if (Crypto.verifyPassword(pw.getText().toString(), prefs.pwSalt(), prefs.pwHash())) {
                            SosEngine.get(this).endSos("CANCELLED");
                            refresh();
                        } else {
                            Ui.toast(this, "Wrong password");
                        }
                    })
                    .setNegativeButton("Keep going", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("End SOS?")
                    .setMessage("Tell the control center you are safe / it was a false alarm.")
                    .setPositiveButton("I'm safe", (d, w) -> {
                        SosEngine.get(this).endSos("CANCELLED");
                        refresh();
                    })
                    .setNegativeButton("Keep SOS", null)
                    .show();
        }
    }

    private void refresh() {
        b.txtHello.setText("Hello, " + prefs.name());
        b.txtStatus.setText(NetUtil.describe(this) + (prefs.guardOn() ? "  |  Guard ON" : "  |  Guard OFF"));
        Incident cur = SosEngine.get(this).current();
        b.bannerActive.setVisibility(cur == null ? android.view.View.GONE : android.view.View.VISIBLE);
        if (cur != null) {
            b.txtActive.setText("SOS ACTIVE - " + Incident.label(cur.type) + (cur.silent ? " (silent)" : "")
                    + "\nStatus: " + cur.status.replace('_', ' ')
                    + (cur.dispatched.isEmpty() ? "" : "\nDispatched: " + cur.dispatched)
                    + (cur.synced ? "\nControl center has your report." : "\nNot delivered by internet yet - using SMS / nearby relay."));
        }
        b.switchDistress.setChecked(prefs.distressOn());
        b.switchHelper.setChecked(prefs.helperOn());

        StringBuilder w = new StringBuilder();
        if (!Perms.accessibilityEnabled(this)) w.append("Volume buttons are off: enable the accessibility service in Settings > Permissions.\n");
        if (!Perms.location(this)) w.append("Location permission missing.\n");
        if (!Perms.sms(this) || !Perms.call(this)) w.append("SMS / call permission missing.\n");
        if (prefs.centerPhone().isEmpty()) w.append("Control center phone not known yet (needs one online sync).\n");
        b.txtWarn.setText(w.toString().trim());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.guardOn()) SosGuardService.start(this);
        AppExecutors.cancelMain(refresher);
        refresher.run();
    }

    @Override
    protected void onPause() {
        AppExecutors.cancelMain(refresher);
        super.onPause();
    }
}
