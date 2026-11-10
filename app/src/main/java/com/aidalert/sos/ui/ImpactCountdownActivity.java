package com.aidalert.sos.ui;

import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;

import com.aidalert.sos.databinding.ActivityImpactBinding;
import com.aidalert.sos.engine.SosEngine;
import com.aidalert.sos.util.AppExecutors;

/** "Are you OK?" after a detected impact. Sends an SOS when the countdown ends, unless the user cancels. */
public class ImpactCountdownActivity extends BaseActivity {
    private static final int SECONDS = 30;
    private ActivityImpactBinding b;
    private int left = SECONDS;
    private boolean done;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (done) return;
            left--;
            if (left <= 0) {
                sendSos();
                return;
            }
            show();
            AppExecutors.mainDelayed(this, 1000);
        }
    };

    @Override
    protected boolean useShell() {
        return false;
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
        b = ActivityImpactBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.btnOk.setOnClickListener(v -> {
            done = true;
            AppExecutors.cancelMain(tick);
            finish();
        });
        b.btnHelp.setOnClickListener(v -> sendSos());
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib != null) vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 600, 400}, 0));
        show();
        AppExecutors.mainDelayed(tick, 1000);
    }

    private void show() {
        b.txtCount.setText(String.valueOf(left));
    }

    private void sendSos() {
        if (done) return;
        done = true;
        AppExecutors.cancelMain(tick);
        AppExecutors.io(() -> SosEngine.get(getApplicationContext()).trigger("CRASH_DETECTED", false));
        b.txtCount.setText("SOS sent");
        AppExecutors.mainDelayed(this::finish, 1500);
    }

    @Override
    protected void onDestroy() {
        done = true;
        AppExecutors.cancelMain(tick);
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib != null) vib.cancel();
        super.onDestroy();
    }
}
