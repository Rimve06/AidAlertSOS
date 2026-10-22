package com.aidalert.sos.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.aidalert.sos.databinding.ActivityDisasterRespondBinding;
import com.aidalert.sos.engine.SosEngine;
import com.aidalert.sos.util.Notifier;
import com.aidalert.sos.util.Perms;

/**
 * Opened from the control center's disaster check-in query. Nothing is sent until the user taps
 * "I am safe" (one position) or "I need rescue" (distress mode: position every minute, can be turned
 * off again on the home screen).
 */
public class DisasterRespondActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "disaster_id";
    public static final String EXTRA_TITLE = "disaster_title";
    public static final String EXTRA_MESSAGE = "disaster_message";

    private ActivityDisasterRespondBinding b;
    private String id = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDisasterRespondBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Disaster check-in");
        Notifier.cancel(this, Notifier.ID_DISASTER);
        id = getIntent().getStringExtra(EXTRA_ID) == null ? "" : getIntent().getStringExtra(EXTRA_ID);
        b.txtTitle.setText(getIntent().getStringExtra(EXTRA_TITLE));
        b.txtMessage.setText(getIntent().getStringExtra(EXTRA_MESSAGE));

        b.btnSafe.setOnClickListener(v -> {
            if (!ensureLocation()) return;
            SosEngine.get(this).checkinOnce("SAFE", id);
            b.txtResult.setText("Thank you. You are marked SAFE on the control center map.");
        });
        b.btnDistress.setOnClickListener(v -> {
            if (!ensureLocation()) return;
            SosEngine.get(this).setDistress(true, id);
            b.txtResult.setText("Distress mode ON: rescuers can see your location. Turn it off any time from the home screen.");
        });
        b.btnDismiss.setOnClickListener(v -> finish());
    }

    private boolean ensureLocation() {
        if (Perms.location(this)) return true;
        ActivityCompat.requestPermissions(this, Perms.locationPerms(), 9);
        Ui.toast(this, "Allow location, then tap the button again.");
        return false;
    }
}
