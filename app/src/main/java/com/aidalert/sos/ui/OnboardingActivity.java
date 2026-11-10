package com.aidalert.sos.ui;

import android.content.Intent;
import android.os.Bundle;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityOnboardingBinding;

/** Three short intro pages shown once, before the first login. */
public class OnboardingActivity extends BaseActivity {
    @Override
    protected boolean useShell() {
        return false;
    }

    private static final String[][] PAGES = {
            {"Stay Safe Together", "Get instant alerts, find help, and keep your community secure."},
            {"Help in one press", "Hold the SOS button, or use the hardware shortcuts. Your location and emergency contacts are notified, even without internet."},
            {"Works when the network does not", "Hotlines, nearby hospitals, police and fire stations, and first-aid guides are saved on your phone."}
    };

    private ActivityOnboardingBinding b;
    private int page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.btnNext.setOnClickListener(v -> {
            if (page < PAGES.length - 1) {
                page++;
                show();
            } else {
                finishOnboarding();
            }
        });
        b.btnSkip.setOnClickListener(v -> finishOnboarding());
        show();
    }

    private void show() {
        b.txtTitle.setText(PAGES[page][0]);
        b.txtBody.setText(PAGES[page][1]);
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < PAGES.length; i++) d.append(i == page ? "● " : "○ ");
        b.txtDots.setText(d.toString().trim());
        b.btnNext.setText(page == PAGES.length - 1 ? "Get started" : "Next  →");
    }

    private void finishOnboarding() {
        Prefs.of(this).onboarded(true);
        startActivity(new Intent(this, AuthActivity.class));
        finish();
    }
}
