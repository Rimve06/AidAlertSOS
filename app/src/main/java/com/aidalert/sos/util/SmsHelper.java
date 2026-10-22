package com.aidalert.sos.util;

import android.content.Context;
import android.os.Build;
import android.telephony.SmsManager;

import java.util.ArrayList;

/** Sends text messages over the SIM network (works with no internet). */
public final class SmsHelper {
    private SmsHelper() {}

    public static boolean send(Context c, String number, String text) {
        if (number == null || number.trim().isEmpty() || text == null || text.isEmpty()) return false;
        if (!Perms.sms(c)) return false;
        try {
            SmsManager sm;
            if (Build.VERSION.SDK_INT >= 31) {
                sm = c.getSystemService(SmsManager.class);
            } else {
                sm = SmsManager.getDefault();
            }
            if (sm == null) return false;
            ArrayList<String> parts = sm.divideMessage(text);
            sm.sendMultipartTextMessage(number.trim(), null, parts, null, null);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
