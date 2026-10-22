package com.aidalert.sos.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivitySettingsBinding;
import com.aidalert.sos.engine.RelayManager;
import com.aidalert.sos.engine.SosEngine;
import com.aidalert.sos.engine.SosGuardService;
import com.aidalert.sos.engine.SyncManager;
import com.aidalert.sos.util.AppExecutors;

public class SettingsActivity extends AppCompatActivity {
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
        b.editCallNumber.setText(p.callNumberRaw());
        b.editServer.setText(p.dbUrl());
        b.txtCenterInfo.setText("Control center phone: " + (p.centerPhone().isEmpty() ? "not synced yet" : "+" + p.centerPhone())
                + "\nEncryption key for offline relay: " + (p.centerPub().isEmpty() ? "not synced yet" : "ready"));

        b.btnSave.setOnClickListener(v -> save());
        b.btnPerms.setOnClickListener(v -> startActivity(new Intent(this, PermissionsActivity.class)));
        b.btnSync.setOnClickListener(v -> {
            Ui.toast(this, "Syncing...");
            AppExecutors.io(() -> SyncManager.syncNow(getApplicationContext()));
        });
        b.btnLogout.setOnClickListener(v -> logout());
    }

    private void save() {
        p.guardOn(b.switchGuard.isChecked());
        p.autoCall(b.switchAutoCall.isChecked());
        p.relayOn(b.switchRelay.isChecked());
        p.callNumber(Ui.clean(b.editCallNumber.getText()));
        p.dbUrl(Ui.clean(b.editServer.getText()));
        if (p.guardOn()) SosGuardService.start(this);
        else SosGuardService.stop(this);
        if (p.relayOn() && p.guardOn()) RelayManager.get(this).start();
        else RelayManager.get(this).stop();
        Ui.toast(this, "Saved");
    }

    private void logout() {
        SosEngine.get(this).stopTracking();
        SosGuardService.stop(this);
        RelayManager.get(this).stop();
        p.logout();
        Intent i = new Intent(this, AuthActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
