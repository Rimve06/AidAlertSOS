package com.aidalert.sos.ui;

import android.os.Bundle;
import android.util.Log;

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

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, screenName() + ".onResume");
    }

    @Override
    protected void onPause() {
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
