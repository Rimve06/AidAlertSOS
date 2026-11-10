package com.aidalert.sos.engine;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.aidalert.sos.Config;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.Notifier;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Foreground service that keeps the app alive so it can count power-button presses.
 *
 * Android never gives apps the power key itself. Each press turns the screen on or off, and those
 * broadcasts (ACTION_SCREEN_ON / OFF) can only be registered at runtime - which is why this long
 * running service exists. It also runs the periodic sync and the offline relay.
 */
public class SosGuardService extends Service {

    private ScheduledFuture<?> syncTask;
    private boolean receiversOn;
    private ConnectivityManager cm;
    private ImpactWatcher impact;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(a) || Intent.ACTION_SCREEN_OFF.equals(a)) {
                SosEngine.get(context).onScreenToggle();
            } else if (Intent.ACTION_USER_PRESENT.equals(a)) {
                // Unlocking is "something else": it breaks a half-finished press sequence.
                SosEngine.get(context).onOtherInput();
            }
        }
    };

    private final ConnectivityManager.NetworkCallback netCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            // Internet is back: upload unsent SOS reports and relay packets right away.
            AppExecutors.io(() -> {
                Dispatcher.flushPending(getApplicationContext());
                SyncManager.syncNow(getApplicationContext());
            });
        }
    };

    public static void start(Context c) {
        Prefs p = Prefs.of(c);
        if (!p.isLoggedIn() || !p.guardOn()) return;
        Intent i = new Intent(c, SosGuardService.class);
        try {
            ContextCompat.startForegroundService(c, i);
        } catch (RuntimeException ignored) {
            // background start refused; it will start next time the app is opened
        }
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, SosGuardService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // Android 14+ needs an explicit type; "specialUse" is declared in the manifest.
                ServiceCompat.startForeground(this, Notifier.ID_GUARD, Notifier.guard(this),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(Notifier.ID_GUARD, Notifier.guard(this));
            }
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }

        registerAll();
        if (Prefs.of(this).impactOn()) {
            if (impact == null) impact = new ImpactWatcher(this);
            impact.start();
        } else if (impact != null) {
            impact.stop();
        }
        if (syncTask == null) {
            syncTask = AppExecutors.scheduler().scheduleWithFixedDelay(() -> {
                try {
                    SyncManager.syncNow(getApplicationContext());
                } catch (RuntimeException ignored) {
                    // keep the schedule alive
                }
            }, 3, Config.SYNC_INTERVAL_S, TimeUnit.SECONDS);
        }
        SosEngine.get(this).resumeDistressIfOn();
        if (Prefs.of(this).isCenter()) LanServer.get(this).start();
        if (Prefs.of(this).relayOn()) RelayManager.get(this).start();
        return START_STICKY;
    }

    private void registerAll() {
        if (receiversOn) return;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
        try {
            cm.registerDefaultNetworkCallback(netCallback);
        } catch (RuntimeException ignored) {
            // periodic sync still covers this
        }
        receiversOn = true;
    }

    @Override
    public void onDestroy() {
        if (receiversOn) {
            try {
                unregisterReceiver(screenReceiver);
                cm.unregisterNetworkCallback(netCallback);
            } catch (RuntimeException ignored) {
                // already unregistered
            }
            receiversOn = false;
        }
        if (syncTask != null) {
            syncTask.cancel(false);
            syncTask = null;
        }
        if (impact != null) impact.stop();
        RelayManager.get(this).stop();
        LanServer.get(this).stop();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
