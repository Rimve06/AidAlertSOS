package com.aidalert.sos;

/** App-wide constants. Change them here, nowhere else. */
public final class Config {
    private Config() {}

    /** Registration code a control-center operator must enter (demo-level protection). */
    public static final String CENTER_CODE = "AIDCENTER-2026";

    /** Power button: number of presses, and the largest pause allowed between two presses. */
    public static final int REQUIRED_TAPS = 5;
    public static final long MAX_TAP_GAP_MS = 1500;

    /** Volume buttons must be held this long (prevents accidental alerts). */
    public static final long VOLUME_HOLD_MS = 2000;

    /** If the user does not choose an emergency type within this time, SMS/call go out anyway. */
    public static final int TYPE_PAGE_TIMEOUT_S = 15;

    public static final long TRACK_INTERVAL_MS = 8000;
    public static final int SYNC_INTERVAL_S = 20;
    public static final long SOS_MAX_AGE_MS = 6L * 60 * 60 * 1000;

    /** Offline relay (Nearby Connections). */
    public static final int MAX_RELAY_HOPS = 8;
    public static final String NEARBY_SERVICE_ID = "com.aidalert.sos.relay";

    public static final String DEFAULT_EMERGENCY_NUMBER = "999";
}
