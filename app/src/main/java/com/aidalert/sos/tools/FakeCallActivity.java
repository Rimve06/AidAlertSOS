package com.aidalert.sos.tools;

import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.util.AppExecutors;

/**
 * A fake incoming call. For the delay the screen stays dark like a phone that is idle, then it rings.
 * Nothing is dialled and no real call is made.
 */
public class FakeCallActivity extends BaseActivity {
    public static final String EXTRA_DELAY = "delay", EXTRA_NAME = "name";

    private LinearLayout root;
    private TextView txtName, txtState;
    private com.google.android.material.button.MaterialButton btnAccept, btnDecline;
    private Ringtone ringtone;
    private int seconds;
    private boolean connected, finished;
    private final Runnable ring = this::startRinging;
    private final Runnable clock = new Runnable() {
        @Override
        public void run() {
            if (finished) return;
            seconds++;
            txtState.setText(String.format(java.util.Locale.US, "%02d:%02d", seconds / 60, seconds % 60));
            AppExecutors.mainDelayed(this, 1000);
        }
    };

    @Override
    protected boolean useShell() {
        return false;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF000000);
        root.setPadding(dp(24), dp(96), dp(24), dp(64));
        txtName = new TextView(this);
        txtName.setTextColor(0xFFFFFFFF);
        txtName.setTextSize(34);
        txtName.setText(getIntent().getStringExtra(EXTRA_NAME));
        txtState = new TextView(this);
        txtState.setTextColor(0xFFB0B0B0);
        txtState.setTextSize(18);
        txtState.setText("mobile");
        android.view.View spacer = new android.view.View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        btnDecline = new com.google.android.material.button.MaterialButton(this);
        btnDecline.setText("Decline");
        btnDecline.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD32F2F));
        btnAccept = new com.google.android.material.button.MaterialButton(this);
        btnAccept.setText("Accept");
        btnAccept.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2E7D32));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(60), 1f);
        lp.setMargins(dp(8), 0, dp(8), 0);
        row.addView(btnDecline, lp);
        row.addView(btnAccept, lp);
        root.addView(txtName);
        root.addView(txtState);
        root.addView(spacer);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        txtName.setVisibility(android.view.View.INVISIBLE);
        txtState.setVisibility(android.view.View.INVISIBLE);
        row.setVisibility(android.view.View.INVISIBLE);
        btnDecline.setOnClickListener(v -> end());
        btnAccept.setOnClickListener(v -> accept(row));
        int delay = getIntent().getIntExtra(EXTRA_DELAY, 0);
        AppExecutors.mainDelayed(ring, delay * 1000L);
    }

    private void startRinging() {
        if (finished) return;
        txtName.setVisibility(android.view.View.VISIBLE);
        txtState.setVisibility(android.view.View.VISIBLE);
        ((android.view.View) btnAccept.getParent()).setVisibility(android.view.View.VISIBLE);
        txtState.setText("Incoming call...");
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
        if (ringtone != null) ringtone.play();
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib != null) vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 800}, 0));
    }

    private void accept(LinearLayout row) {
        connected = true;
        stopRinging();
        btnAccept.setVisibility(android.view.View.GONE);
        btnDecline.setText("End call");
        seconds = 0;
        AppExecutors.mainDelayed(clock, 1000);
        txtState.setText("00:00");
    }

    private void stopRinging() {
        if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib != null) vib.cancel();
    }

    private void end() {
        finished = true;
        stopRinging();
        finish();
    }

    @Override
    protected void onDestroy() {
        finished = true;
        AppExecutors.cancelMain(ring);
        AppExecutors.cancelMain(clock);
        stopRinging();
        super.onDestroy();
    }
}
