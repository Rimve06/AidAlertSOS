package com.aidalert.sos.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Parent class of every screen (inheritance). It observes the Activity lifecycle callbacks and keeps
 * a count of visible screens, so the rest of the app can tell whether the user is looking at it.
 * Subclasses must call super in their own lifecycle overrides.
 */
public abstract class BaseActivity extends AppCompatActivity {
    private static final String TAG = "AidLifecycle";
    private static int visibleScreens;

    /** True while at least one screen of this app is on the display. */
    public static boolean isAppVisible() {
        return visibleScreens > 0;
    }

    private TextView headerTitle;
    private ImageButton headerBack, headerAction;
    private CharSequence pendingTitle;

    /** Screens with their own full-screen design (home, splash, login) return false and skip the navy header. */
    protected boolean useShell() {
        return true;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    public void setContentView(int layoutResId) {
        setContentView(getLayoutInflater().inflate(layoutResId, null));
    }

    /** Wraps the screen in the app's look: navy header with a back arrow and title, content on a rounded light sheet. */
    @Override
    public void setContentView(View content) {
        if (!useShell()) {
            super.setContentView(content);
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(com.aidalert.sos.R.drawable.bg_navy);

        FrameLayout header = new FrameLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        headerBack = new ImageButton(this);
        headerBack.setImageResource(com.aidalert.sos.R.drawable.ic_back);
        headerBack.setBackground(null);
        headerBack.setContentDescription("Back");
        headerBack.setLayoutParams(new FrameLayout.LayoutParams(dp(52), dp(56), Gravity.START | Gravity.CENTER_VERTICAL));
        headerBack.setOnClickListener(v -> finish());
        header.addView(headerBack);

        headerTitle = new TextView(this);
        headerTitle.setTextColor(0xFFFFFFFF);
        headerTitle.setTextSize(18);
        headerTitle.setTypeface(null, Typeface.BOLD);
        headerTitle.setSingleLine(true);
        headerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tp.leftMargin = dp(56);
        tp.rightMargin = dp(56);
        headerTitle.setLayoutParams(tp);
        header.addView(headerTitle);

        headerAction = new ImageButton(this);
        headerAction.setBackground(null);
        headerAction.setVisibility(View.GONE);
        headerAction.setLayoutParams(new FrameLayout.LayoutParams(dp(52), dp(56), Gravity.END | Gravity.CENTER_VERTICAL));
        header.addView(headerAction);
        root.addView(header);

        FrameLayout sheet = new FrameLayout(this);
        sheet.setBackgroundResource(com.aidalert.sos.R.drawable.bg_sheet);
        sheet.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        sheet.setClipToOutline(true);
        sheet.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (content.getLayoutParams() == null) {
            content.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        sheet.addView(content);
        root.addView(sheet);
        super.setContentView(root);
        if (pendingTitle != null) headerTitle.setText(pendingTitle);
        else headerTitle.setText(getTitle());
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        pendingTitle = title;
        if (headerTitle != null) headerTitle.setText(title);
    }

    /** Puts an icon button on the right of the header (search, refresh, edit...). */
    protected void setHeaderAction(int iconRes, String description, Runnable onClick) {
        if (headerAction == null) return;
        headerAction.setImageResource(iconRes);
        headerAction.setColorFilter(0xFFFFFFFF);
        headerAction.setContentDescription(description);
        headerAction.setVisibility(View.VISIBLE);
        headerAction.setOnClickListener(v -> onClick.run());
    }

    protected void hideHeaderBack() {
        if (headerBack != null) headerBack.setVisibility(View.GONE);
    }

    /** Name used in log lines; subclasses may override. */
    protected String screenName() {
        return getClass().getSimpleName();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, screenName() + ".onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();
        visibleScreens++;
        Log.d(TAG, screenName() + ".onStart");
    }

    private void showLastCrash() {
        final android.content.SharedPreferences sp = getSharedPreferences("crash", MODE_PRIVATE);
        final String trace = sp.getString("trace", "");
        if (trace.isEmpty()) return;
        sp.edit().remove("trace").apply();
        final String shown = trace.length() > 1800 ? trace.substring(0, 1800) : trace;
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(shown);
        tv.setTextSize(11);
        tv.setTextIsSelectable(true);
        tv.setPadding(40, 20, 40, 20);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("The app crashed last time (send me this)")
                .setView(sv).setPositiveButton("OK", null).show();
    }

    private boolean signedInOnShow;
    private final Runnable sessionWatch = new Runnable() {
        @Override
        public void run() {
            if (signedInOnShow && !com.aidalert.sos.data.Prefs.of(BaseActivity.this).isLoggedIn()) {
                // the account was removed on the server (or signed out): back to the login screen
                Intent i = new Intent(BaseActivity.this, AuthActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
                return;
            }
            com.aidalert.sos.util.AppExecutors.mainDelayed(this, 4000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, screenName() + ".onResume");
        showLastCrash();
        signedInOnShow = com.aidalert.sos.data.Prefs.of(this).isLoggedIn();
        com.aidalert.sos.util.AppExecutors.cancelMain(sessionWatch);
        com.aidalert.sos.util.AppExecutors.mainDelayed(sessionWatch, 4000);
        // check the account against the server as soon as a screen is shown
        if (signedInOnShow && com.aidalert.sos.util.NetUtil.isOnline(this)) {
            final android.content.Context app = getApplicationContext();
            com.aidalert.sos.util.AppExecutors.io(() -> {
                try { com.aidalert.sos.engine.SessionGuard.check(app); } catch (RuntimeException ignored) { }
            });
        }
    }

    @Override
    protected void onPause() {
        com.aidalert.sos.util.AppExecutors.cancelMain(sessionWatch);
        Log.d(TAG, screenName() + ".onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        visibleScreens = Math.max(0, visibleScreens - 1);
        Log.d(TAG, screenName() + ".onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, screenName() + ".onDestroy");
        super.onDestroy();
    }
}
