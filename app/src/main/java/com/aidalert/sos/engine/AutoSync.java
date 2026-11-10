package com.aidalert.sos.engine;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

import com.aidalert.sos.blood.BloodSync;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;

import java.util.concurrent.TimeUnit;

/**
 * Sends everything that is waiting on the phone (alerts, relay packets, donor profile) by itself: about a minute
 * after the app starts, then every minute while the app process is alive, and at once when the internet comes back.
 * Nobody has to press a sync button.
 */
public final class AutoSync {
    private AutoSync() {}

    private static boolean started;
    private static long lastRun;

    public static synchronized void start(final Application app) {
        if (started) return;
        started = true;
        AppExecutors.scheduler().scheduleWithFixedDelay(() -> runSafely(app), 15, 60, TimeUnit.SECONDS);
        try {
            ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        AppExecutors.scheduler().schedule(() -> runSafely(app), 2, TimeUnit.SECONDS);
                    }
                });
            }
        } catch (RuntimeException ignored) {
            // the one-minute timer still works without the callback
        }
    }

    private static void runSafely(Context app) {
        try {
            runNow(app);
        } catch (RuntimeException ignored) {
            // never crash the app because a background upload failed
        }
    }

    /** Runs one round now (also used by the Sync button). Call from a background thread. */
    public static synchronized void runNow(Context app) {
        long now = System.currentTimeMillis();
        if (now - lastRun < 5000) return;
        lastRun = now;
        Prefs p = Prefs.of(app);
        if (!p.isLoggedIn() || !NetUtil.isOnline(app)) return;
        if (!SessionGuard.check(app)) return;           // account deleted in the console: signed out
        com.aidalert.sos.medicine.MedicineSync.sync(app);
        SyncManager.syncNow(app);
        BloodSync.pushPending(app);
        com.aidalert.sos.directory.RouteCache.warmFromLastKnown(app);   // road routes to nearby help, kept for offline use
    }
}
