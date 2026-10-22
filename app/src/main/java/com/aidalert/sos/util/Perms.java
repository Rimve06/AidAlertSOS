package com.aidalert.sos.util;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.aidalert.sos.engine.KeyAccessibilityService;

import java.util.ArrayList;
import java.util.List;

/** Small helpers for runtime and special permissions. */
public final class Perms {
    private Perms() {}

    public static boolean has(Context c, String permission) {
        return ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean location(Context c) {
        return has(c, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /** "Allow all the time" location, needed so a locked / background phone can still be tracked. */
    public static boolean backgroundLocation(Context c) {
        return Build.VERSION.SDK_INT < 29 || has(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    public static boolean sms(Context c) {
        return has(c, Manifest.permission.SEND_SMS);
    }

    public static boolean call(Context c) {
        return has(c, Manifest.permission.CALL_PHONE);
    }

    public static boolean notifications(Context c) {
        return Build.VERSION.SDK_INT < 33 || has(c, Manifest.permission.POST_NOTIFICATIONS);
    }

    public static String[] locationPerms() {
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    }

    public static String[] smsPerms() {
        return new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS};
    }

    public static String[] callPerms() {
        return new String[]{Manifest.permission.CALL_PHONE};
    }

    public static String[] notificationPerms() {
        if (Build.VERSION.SDK_INT >= 33) return new String[]{Manifest.permission.POST_NOTIFICATIONS};
        return new String[0];
    }

    /** Bluetooth + nearby-Wi-Fi permissions needed by the offline relay. */
    public static String[] nearbyPerms() {
        List<String> l = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            l.add(Manifest.permission.BLUETOOTH_SCAN);
            l.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            l.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        return l.toArray(new String[0]);
    }

    public static boolean allGranted(Context c, String[] perms) {
        for (String p : perms) if (!has(c, p)) return false;
        return true;
    }

    public static boolean nearby(Context c) {
        return allGranted(c, nearbyPerms());
    }

    public static boolean overlay(Context c) {
        return Settings.canDrawOverlays(c);
    }

    public static boolean batteryUnrestricted(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    /** True when the user enabled the AidAlert accessibility service in system settings. */
    public static boolean accessibilityEnabled(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        String mine = new ComponentName(c, KeyAccessibilityService.class).flattenToString();
        for (String s : enabled.split(":")) {
            if (s.equalsIgnoreCase(mine)) return true;
        }
        return false;
    }
}
