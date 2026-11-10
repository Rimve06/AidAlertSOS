package com.aidalert.sos;

import android.app.Application;

import com.aidalert.sos.util.Notifier;

public class App extends Application {
    /** Saves the stack trace of a crash so the next screen can show it (and a crash loop signs out instead of repeating). */
    private void installCrashReporter() {
        final android.content.SharedPreferences sp = getSharedPreferences("crash", MODE_PRIVATE);
        long at = sp.getLong("at", 0);
        if (sp.getInt("count", 0) >= 2 && System.currentTimeMillis() - at < 120000) {
            com.aidalert.sos.data.Prefs.of(this).logout();           // break a crash loop: back to the login screen
            sp.edit().putInt("count", 0).apply();
        }
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                boolean recent = System.currentTimeMillis() - sp.getLong("at", 0) < 120000;
                sp.edit().putString("trace", sw.toString()).putLong("at", System.currentTimeMillis())
                        .putInt("count", recent ? sp.getInt("count", 0) + 1 : 1).commit();
            } catch (Throwable ignored) {
                // nothing more can be done
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        installCrashReporter();
        Notifier.createChannels(this);
        com.aidalert.sos.engine.AutoSync.start(this);
        com.aidalert.sos.missing.MissingSchedule.apply(this);
    }
}
