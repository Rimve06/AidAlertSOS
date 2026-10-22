package com.aidalert.sos.engine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.aidalert.sos.util.Perms;

/**
 * Wrapper around the platform LocationManager (GPS + network provider).
 * Location is only ever requested from code paths the user triggered: SOS, distress toggle,
 * "I'm safe" check-in or accepting a help request.
 */
public class LocationHelper {

    public interface Callback {
        void onFix(Location location);
    }

    private final Context ctx;
    private final LocationManager lm;
    private Callback callback;
    private boolean running;
    private Location best;

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location l) {
            boolean accept = best == null
                    || l.getAccuracy() <= best.getAccuracy() + 20f
                    || l.getTime() - best.getTime() > 30_000;
            if (!accept) return;
            best = l;
            Callback cb = callback;
            if (cb != null) cb.onFix(l);
        }

        // Required on Android 8-10 where these were still abstract.
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
        }
    };

    public LocationHelper(Context c) {
        ctx = c.getApplicationContext();
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    @SuppressLint("MissingPermission")
    public void start(long intervalMs, Callback cb) {
        if (running || lm == null || !Perms.location(ctx)) return;
        callback = cb;
        running = true;
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, listener, Looper.getMainLooper());
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, intervalMs, 0f, listener, Looper.getMainLooper());
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // provider missing or permission revoked
        }
    }

    public void stop() {
        running = false;
        callback = null;
        if (lm == null) return;
        try {
            lm.removeUpdates(listener);
        } catch (SecurityException ignored) {
            // nothing to remove
        }
    }

    /** Newest position any provider remembers (may be old: check Location.getTime()). */
    @SuppressLint("MissingPermission")
    public Location lastKnown() {
        if (lm == null || !Perms.location(ctx)) return null;
        Location newest = null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (newest == null || l.getTime() > newest.getTime())) newest = l;
            } catch (SecurityException ignored) {
                // permission revoked
            }
        }
        return newest;
    }
}
