package com.aidalert.sos.util;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;

import androidx.core.app.ActivityCompat;

/** Phone calls. place() starts a call at once (needs CALL_PHONE); dial() only opens the dialer. */
public final class CallHelper {
    private CallHelper() {}

    public static boolean place(Context c, String number) {
        if (number == null || number.trim().isEmpty()) return false;
        if (!Perms.call(c)) return false;
        Uri uri = Uri.fromParts("tel", number.trim(), null);
        try {
            TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null) {
                if (ActivityCompat.checkSelfPermission(c, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
                tm.placeCall(uri, new Bundle());
                return true;
            }
        } catch (RuntimeException ignored) {
            // fall through to the intent based call
        }
        try {
            Intent i = new Intent(Intent.ACTION_CALL, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void dial(Context c, String number) {
        try {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.trim(), null));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (RuntimeException ignored) {
            // no dialer available
        }
    }
}
