package com.aidalert.sos.engine;

/**
 * Pure Java crash / hard-fall detector. A hard impact (a spike of several g) followed by several seconds of the phone
 * lying still is treated as a possible accident. It only ASKS: the app then shows a countdown the user can cancel.
 * A dropped phone also looks like this, which is why nothing is sent without the countdown.
 */
public final class ImpactDetector {
    public static final double PEAK_G = 3.5;          // impact strength
    public static final long GRACE_MS = 1000;         // bouncing right after the impact is ignored
    public static final long STILL_MS = 6000;         // how long the phone must then stay still
    public static final double STILL_BAND_G = 0.30;   // |magnitude - 1 g| must stay below this
    public static final long COOLDOWN_MS = 60_000;

    private static final double G = 9.80665;

    private long impactAt = -1;
    private long cooldownUntil;

    /** Feed one accelerometer sample (m/s^2). Returns true once, when a possible accident is confirmed. */
    public boolean onSample(long tMs, float ax, float ay, float az) {
        if (tMs < cooldownUntil) return false;
        double g = Math.sqrt((double) ax * ax + (double) ay * ay + (double) az * az) / G;
        if (impactAt < 0) {
            if (g >= PEAK_G) impactAt = tMs;
            return false;
        }
        long since = tMs - impactAt;
        if (since < GRACE_MS) return false;
        if (Math.abs(g - 1.0) > STILL_BAND_G) {
            // moving again (walking, picking the phone up): a conscious person, not an accident
            impactAt = g >= PEAK_G ? tMs : -1;
            return false;
        }
        if (since >= GRACE_MS + STILL_MS) {
            impactAt = -1;
            cooldownUntil = tMs + COOLDOWN_MS;
            return true;
        }
        return false;
    }

    public void reset() {
        impactAt = -1;
        cooldownUntil = 0;
    }
}
