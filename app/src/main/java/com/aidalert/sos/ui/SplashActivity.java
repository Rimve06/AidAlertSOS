package com.aidalert.sos.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.aidalert.sos.R;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.engine.SosGuardService;
import com.aidalert.sos.ui.center.CenterHomeActivity;
import com.aidalert.sos.util.AppExecutors;

/** Launcher: picks the first screen from the saved session (explicit Intents). */
public class SplashActivity extends AppCompatActivity {
    private final Runnable go = this::route;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        AppExecutors.mainDelayed(go, 700);
    }

    private void route() {
        if (isFinishing()) return;
        Prefs p = Prefs.of(this);
        Intent next;
        if (!p.isLoggedIn()) {
            next = new Intent(this, AuthActivity.class);
        } else if (p.isCenter()) {
            next = new Intent(this, CenterHomeActivity.class);
        } else {
            SosGuardService.start(this);
            next = new Intent(this, HomeActivity.class);
        }
        startActivity(next);
        finish();
    }

    @Override
    protected void onDestroy() {
        AppExecutors.cancelMain(go);
        super.onDestroy();
    }
}
