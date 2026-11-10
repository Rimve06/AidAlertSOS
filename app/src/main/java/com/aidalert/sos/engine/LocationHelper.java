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

    /** @return true when at least one provider is delivering updates. */
    @SuppressLint("MissingPermission")
    public boolean start(long intervalMs, Callback cb) {
        if (lm == null || !Perms.location(ctx)) return false;
        if (running) {
            callback = cb;
            return true;
        }
        callback = cb;
        boolean any = false;
        // Each provider on its own: with only "approximate" permission GPS throws, but the network provider still works.
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                if (!lm.isProviderEnabled(p)) continue;
                lm.requestLocationUpdates(p, intervalMs, 0f, listener, Looper.getMainLooper());
                any = true;
            } catch (SecurityException | IllegalArgumentException ignored) {
                // this provider is missing or not allowed; try the next one
            }
        }
        running = any;
        return any;
    }

    /** True when the phone's Location switch is on (GPS or network). */
    public boolean servicesOn() {
        if (lm == null) return false;
        try {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A plain-words reason why no position is available yet, for the screen. */
    public String whyNoFix() {
        if (!Perms.location(ctx)) return "Location permission is off. Allow it in the permission dialog or in Settings > Apps > AidAlert SOS.";
        if (!servicesOn()) return "The phone's Location switch is off. Turn Location on (quick settings) and try again.";
        if (!Perms.preciseLocation(ctx)) return "Only approximate location is allowed, so it can be slow. Choose Precise in Settings > Apps > AidAlert SOS > Permissions.";
        return "Waiting for GPS. Go near a window or outside; the first fix can take a minute with no internet.";
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
