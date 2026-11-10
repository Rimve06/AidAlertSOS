package com.aidalert.sos.ui;

import android.content.Intent;
import android.os.Bundle;

import com.aidalert.sos.ui.BaseActivity;

import com.aidalert.sos.auth.AuthManager;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivitySettingsBinding;
import com.aidalert.sos.engine.RelayManager;
import com.aidalert.sos.engine.SosEngine;
import com.aidalert.sos.engine.SosGuardService;
import com.aidalert.sos.engine.SyncManager;
import com.aidalert.sos.net.Crypto;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.google.firebase.auth.FirebaseUser;

public class SettingsActivity extends BaseActivity {
    private ActivitySettingsBinding b;
    private Prefs p;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        p = Prefs.of(this);
        setTitle("Settings");

        b.switchGuard.setChecked(p.guardOn());
        b.switchAutoCall.setChecked(p.autoCall());
        b.switchRelay.setChecked(p.relayOn());
        b.switchMissing.setChecked(p.missingWatch());
        String mPlace = com.aidalert.sos.missing.MissingWatcher.placeFor(p);
        b.txtMissingHint.setText("Checks the public missing-person sites listed in the app every few hours (when Android allows it) and "
                + "shows the photo if the page has one; tap opens the original page. Area: "
                + (mPlace.isEmpty() ? "all Bangladesh (choose your district in Alerts to narrow it)" : mPlace)
                + ". Reports are unverified and come from other websites; there is no partnership with them.");
        b.editCallNumber.setText(p.callNumberRaw());
        b.editServer.setText(p.dbUrl());
        b.editLan.setText(p.lanHost());
        b.txtCenterInfo.setText("Control center phone: " + (p.centerPhone().isEmpty() ? "not synced yet" : "+" + p.centerPhone())
                + "\nEncryption key for offline relay: " + (p.centerPub().isEmpty() ? "not synced yet" : "ready")
                + (p.syncError().isEmpty() ? "" : "\nLast upload problem: " + p.syncError()));

        b.btnSave.setOnClickListener(v -> save());
        b.btnPerms.setOnClickListener(v -> startActivity(new Intent(this, PermissionsActivity.class)));
        b.btnServerCheck.setOnClickListener(v -> startActivity(new android.content.Intent(this, ServerCheckActivity.class)));
        b.btnSync.setOnClickListener(v -> {
            Ui.toast(this, "Syncing...");
            AppExecutors.io(() -> {
                com.aidalert.sos.engine.AutoSync.runNow(getApplicationContext());
                AppExecutors.main(() -> {
                    String e = p.syncError();
                    if (!isFinishing()) Ui.toast(this, e.isEmpty() ? "Sync finished." : e);
                });
            });
        });
        b.btnTestKeys.setOnClickListener(v -> testKeys());
        b.btnLogout.setOnClickListener(v -> logout());
        b.btnDeleteAccount.setOnClickListener(v -> deleteAccount());
    }

    private void deleteAccount() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete account")
                .setMessage("Are you sure you want to permanently delete your account and remove all your data from the server? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    if (!NetUtil.isOnline(this)) {
                        Ui.toast(this, "You need internet to delete your account.");
                        return;
                    }
                    FirebaseUser u = AuthManager.user();
                    if (u == null) { logout(); return; }
                    AppExecutors.io(() -> {
                        FirebaseRest rest = new FirebaseRest(this);
                        String uid = u.getUid();
                        String phone = p.phone();
                        if (!phone.isEmpty()) {
                            String phoneKey = Crypto.phoneKey(phone);
                            rest.delete("phoneIndex/" + phoneKey);
                        }
                        rest.delete("users/" + uid);
                        rest.delete("directory/" + uid);
                        rest.delete("donors/" + uid);
                        rest.delete("medicines/" + uid);
                        com.aidalert.sos.net.FirestoreRest fs = new com.aidalert.sos.net.FirestoreRest(this);
                        fs.delete("donors", uid);
                        fs.delete("donorContacts", uid);

                        u.delete().addOnCompleteListener(task -> {
                            AppExecutors.main(() -> {
                                SosEngine.get(this).stopTracking();
                                SosGuardService.stop(this);
                                RelayManager.get(this).stop();
                                com.aidalert.sos.data.LocalData.wipeAccount(this);
                                AuthManager.signOut();
                                p.logout();
                                Ui.toast(this, "Account deleted.");
                                Intent i = new Intent(this, AuthActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(i);
                            });
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Live view of what the volume-key service sees, so a silent failure can be diagnosed. */
    private void testKeys() {
        final android.widget.TextView tv = new android.widget.TextView(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            boolean enabled = com.aidalert.sos.util.Perms.accessibilityEnabled(this);
            boolean connected = com.aidalert.sos.engine.KeyAccessibilityService.isConnected();
            long at = com.aidalert.sos.engine.KeyAccessibilityService.lastKeyAt();
            tv.setText("1. Enabled in Accessibility settings: " + (enabled ? "YES" : "NO  (turn on 'AidAlert SOS buttons')")
                    + "\n2. Service running: " + (connected ? "YES" : "NO  (switch it off and on again)")
                    + "\n3. Armed (citizen, guard on): " + (com.aidalert.sos.engine.SosEngine.get(this).canTrigger() ? "YES" : "NO")
                    + "\n\nPress any volume key now.\nLast key seen: " + com.aidalert.sos.engine.KeyAccessibilityService.lastKey()
                    + (at == 0 ? "" : " (" + (System.currentTimeMillis() - at) / 1000 + " s ago)")
                    + "\n\nHold Volume Up or Down for 2 s. Result: "
                    + (com.aidalert.sos.engine.KeyAccessibilityService.lastResult().isEmpty() ? "-" : com.aidalert.sos.engine.KeyAccessibilityService.lastResult())
                    + "\n\nIf step 1 is YES but step 2 is NO on Android 13+: Settings > Apps > AidAlert SOS > menu (3 dots) > Allow restricted settings, then enable the service again.");
            AppExecutors.mainDelayed(tick[0], 500);
        };
        tick[0].run();
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Volume key test").setView(tv)
                .setPositiveButton("Close", null)
                .setOnDismissListener(d -> AppExecutors.cancelMain(tick[0])).show();
    }

    private void save() {
        p.guardOn(b.switchGuard.isChecked());
        p.autoCall(b.switchAutoCall.isChecked());
        p.relayOn(b.switchRelay.isChecked());
        p.missingWatch(b.switchMissing.isChecked());
        com.aidalert.sos.missing.MissingSchedule.apply(this);
        p.callNumber(Ui.clean(b.editCallNumber.getText()));
        p.dbUrl(Ui.clean(b.editServer.getText()));
        p.lanHost(Ui.clean(b.editLan.getText()));
        if (p.guardOn()) SosGuardService.start(this);
        else SosGuardService.stop(this);
        if (p.relayOn() && p.guardOn()) RelayManager.get(this).start();
        else RelayManager.get(this).stop();
        Ui.toast(this, "Saved");
    }

    /** Sends anything still waiting (medicines, donor profile) before the phone forgets this account. */
    private void logout() {
        if (!NetUtil.isOnline(this) || p.isCenter()) {
            doLogout();
            return;
        }
        AppExecutors.io(() -> {
            try {
                com.aidalert.sos.medicine.MedicineSync.sync(this);
                com.aidalert.sos.blood.BloodSync.pushPending(this);
            } catch (RuntimeException ignored) {
                // sign out anyway
            }
            AppExecutors.main(this::doLogout);
        });
    }

    private void doLogout() {
        SosEngine.get(this).stopTracking();
        SosGuardService.stop(this);
        RelayManager.get(this).stop();
        com.aidalert.sos.data.LocalData.wipeAccount(this);
        AuthManager.signOut();
        p.logout();
        Intent i = new Intent(this, AuthActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
