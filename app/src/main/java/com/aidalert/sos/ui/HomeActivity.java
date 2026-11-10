package com.aidalert.sos.ui;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.aidalert.sos.ui.BaseActivity;
import androidx.core.app.ActivityCompat;

import com.aidalert.sos.community.CommunityActivity;
import com.aidalert.sos.data.Incident;
import com.aidalert.sos.directory.DirectoryActivity;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityHomeBinding;
import com.aidalert.sos.engine.SosEngine;
import com.aidalert.sos.engine.SosGuardService;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.Perms;

/** Citizen home: on-screen SOS button, trigger help, disaster toggles and navigation. */
public class HomeActivity extends BaseActivity {
    @Override
    protected boolean useShell() {
        return false;
    }

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
        b.holdSos.setTexts("SOS", "hold to send");

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
        b.btnForum.setOnClickListener(v -> startActivity(new Intent(this, CommunityActivity.class)));
        b.btnSearch.setOnClickListener(v -> startActivity(new Intent(this, SearchUsersActivity.class)));
        b.btnMissing.setOnClickListener(v -> startActivity(sourceIntent(false)));
        b.btnWanted.setOnClickListener(v -> startActivity(sourceIntent(true)));
        b.btnStats.setOnClickListener(v -> startActivity(new Intent(this,
                com.aidalert.sos.stats.CrimeStatsActivity.class)));
        b.btnNews.setOnClickListener(v -> startActivity(new Intent(this, NewsActivity.class)));
        b.btnSupport.setOnClickListener(v -> startActivity(new Intent(this,
                com.aidalert.sos.ai.AssistantActivity.class)));
        b.btnBlood.setOnClickListener(v -> startActivity(new Intent(this, com.aidalert.sos.blood.BloodActivity.class)));
        b.btnLearn.setOnClickListener(v -> startActivity(new Intent(this, com.aidalert.sos.learn.LearnActivity.class)));
        b.btnContacts.setOnClickListener(v -> startActivity(new Intent(this, ContactsActivity.class)));
        b.navProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        b.navHistory.setOnClickListener(v -> startActivity(new Intent(this, ReportsActivity.class)));
        b.navAlerts.setOnClickListener(v -> startActivity(new Intent(this, AlertsActivity.class)));
        b.btnAlerts.setOnClickListener(v -> startActivity(new Intent(this, AlertsActivity.class)));
        b.btnDirectory.setOnClickListener(v -> startActivity(new Intent(this, DirectoryActivity.class)));
        b.btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        b.btnUtilities.setOnClickListener(v -> startActivity(new Intent(this, com.aidalert.sos.tools.UtilitiesActivity.class)));
        b.btnMedicine.setOnClickListener(v -> startActivity(new Intent(this, com.aidalert.sos.medicine.MedicineActivity.class)));
        b.btnReport.setOnClickListener(v -> startActivity(new Intent(this, com.aidalert.sos.report.ReportIncidentActivity.class)));
        b.btnGuide.setOnClickListener(v -> startActivity(new Intent(this, GuideActivity.class)));
        b.cardAlert.setOnClickListener(v -> startActivity(new Intent(this, AlertsActivity.class)));
        b.navNear.setOnClickListener(v -> startActivity(new Intent(this, com.aidalert.sos.directory.MapActivity.class)));

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

    private final ActivityResultLauncher<Intent> unlockForEnd =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK) endSilent();
                else Ui.toast(this, "Not confirmed. SOS keeps running.");
            });

    private void endSilent() {
        SosEngine.get(this).endSos("CANCELLED");
        refresh();
    }

    private void askEnd() {
        final Incident cur = SosEngine.get(this).current();
        if (cur == null) {
            refresh();
            return;
        }
        if (cur.silent) {
            // A silent SOS may be ended only by someone who can unlock this phone (PIN / pattern /
            // fingerprint). This works offline and no password hash is stored by the app.
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            Intent confirm = km == null ? null : km.createConfirmDeviceCredentialIntent(
                    "End silent SOS", "Unlock your phone to confirm you are safe.");
            if (confirm == null) {
                new AlertDialog.Builder(this).setTitle("End silent SOS?")
                        .setMessage("This phone has no screen lock, so it cannot check who you are. Set a PIN in Android settings to protect silent SOS.")
                        .setPositiveButton("End SOS", (d, w) -> endSilent())
                        .setNegativeButton("Keep going", null).show();
            } else {
                unlockForEnd.launch(confirm);
            }
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

    /** Shows the most serious saved alert on the red card at the top. Reads the saved copy only (no network). */
    private void showLatestAlert() {
        AppExecutors.io(() -> {
            com.aidalert.sos.alerts.AlertRepo.Result r = com.aidalert.sos.alerts.AlertRepo.cached(prefs, null);
            com.aidalert.sos.alerts.AlertItem best = null;
            for (com.aidalert.sos.alerts.AlertItem a : r.items) {
                if (com.aidalert.sos.alerts.AlertItem.SCOPE_WORLD.equals(a.scope)) continue;
                if (best == null || a.severity > best.severity || (a.severity == best.severity && a.timeMs > best.timeMs)) best = a;
            }
            final com.aidalert.sos.alerts.AlertItem pick = best;
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                if (pick == null) {
                    b.txtAlertTitle.setText("No saved alerts yet");
                    b.txtAlertBody.setText("Tap to open Public alerts and refresh.");
                } else {
                    b.txtAlertTitle.setText((pick.severity >= 2 ? "Severe alert" : pick.severity == 1 ? "Alert" : "Notice")
                            + (pick.timeMs == 0 ? "" : "  \u00B7  " + com.aidalert.sos.util.TimeUtil.ago(pick.timeMs)));
                    b.txtAlertBody.setText(pick.title);
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showLatestAlert();
        if (prefs.guardOn()) SosGuardService.start(this);
        AppExecutors.cancelMain(refresher);
        refresher.run();
    }

    @Override
    protected void onPause() {
        AppExecutors.cancelMain(refresher);
        super.onPause();
    }

    /** Opens the official source pages for missing or wanted persons; the first one loads at once. */
    private Intent sourceIntent(boolean wanted) {
        com.aidalert.sos.news.FeedConfig cfg = com.aidalert.sos.news.FeedConfig.load(this);
        java.util.List<com.aidalert.sos.news.FeedConfig.Feed> l = wanted ? cfg.wanted : cfg.missing;
        String[] n = new String[l.size()];
        String[] u = new String[l.size()];
        for (int i = 0; i < n.length; i++) {
            n[i] = l.get(i).name;
            u[i] = l.get(i).url;
        }
        return SourceBrowserActivity.intent(this, wanted ? "Wanted notices" : "Missing persons", n, u,
                wanted ? "WANTED" : "MISSING");
    }
}
