package com.aidalert.sos.engine;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.aidalert.sos.Config;

/**
 * Reads the volume keys. Holding Volume Up for 2 s = emergency with the type page;
 * holding Volume Down for 2 s = silent violence mode. Keys are NOT consumed, so normal volume
 * control keeps working. The service reads no screen content (canRetrieveWindowContent=false).
 *
 * It also reports every touch to the SOS engine, because any touch between two power presses
 * must reset the power-button sequence.
 */
public class KeyAccessibilityService extends AccessibilityService {

    // Diagnostics for Settings > "Test volume keys": proves the service is connected and receiving keys.
    private static volatile boolean connected;
    private static volatile String lastKey = "none yet";
    private static volatile long lastKeyAt;
    private static volatile String lastResult = "";

    public static boolean isConnected() { return connected; }
    public static String lastKey() { return lastKey; }
    public static long lastKeyAt() { return lastKeyAt; }
    public static String lastResult() { return lastResult; }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int heldKey = -1;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        connected = false;
        return super.onUnbind(intent);
    }

    /** Short buzz so the user knows the 2-second hold was accepted (skipped in silent mode). */
    private void buzz() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (RuntimeException ignored) {
            // no vibrator
        }
    }

    private final Runnable fire = new Runnable() {
        @Override
        public void run() {
            int k = heldKey;
            heldKey = -1;
            if (k == KeyEvent.KEYCODE_VOLUME_UP) {
                lastResult = "Volume Up held 2 s: " + (SosEngine.get(getApplicationContext()).canTrigger()
                        ? "SOS started" : "ignored (log in as a citizen and keep 'Power-button guard' on)");
                buzz();
                SosEngine.get(getApplicationContext()).onVolumeHold(true);
            } else if (k == KeyEvent.KEYCODE_VOLUME_DOWN) {
                lastResult = "Volume Down held 2 s: " + (SosEngine.get(getApplicationContext()).canTrigger()
                        ? "silent SOS started" : "ignored (log in as a citizen and keep 'Power-button guard' on)");
                SosEngine.get(getApplicationContext()).onVolumeHold(false); // silent: no buzz
            }
        }
    };

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        lastKey = KeyEvent.keyCodeToString(code) + (event.getAction() == KeyEvent.ACTION_DOWN ? " down" : " up");
        lastKeyAt = System.currentTimeMillis();
        boolean volume = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN;
        if (!volume) {
            SosEngine.get(getApplicationContext()).onOtherInput();
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                SosEngine.get(getApplicationContext()).onOtherInput();
                heldKey = code;
                handler.removeCallbacks(fire);
                handler.postDelayed(fire, Config.VOLUME_HOLD_MS);
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (heldKey == code) {
                heldKey = -1;
                handler.removeCallbacks(fire);
            }
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            SosEngine.get(getApplicationContext()).onOtherInput();
        }
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(fire);
        heldKey = -1;
    }
}
