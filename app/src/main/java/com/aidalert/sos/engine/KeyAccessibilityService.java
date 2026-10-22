package com.aidalert.sos.engine;

import android.accessibilityservice.AccessibilityService;
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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int heldKey = -1;

    private final Runnable fire = new Runnable() {
        @Override
        public void run() {
            int k = heldKey;
            heldKey = -1;
            if (k == KeyEvent.KEYCODE_VOLUME_UP) SosEngine.get(getApplicationContext()).onVolumeHold(true);
            else if (k == KeyEvent.KEYCODE_VOLUME_DOWN) SosEngine.get(getApplicationContext()).onVolumeHold(false);
        }
    };

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
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
