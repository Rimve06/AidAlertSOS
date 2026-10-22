package com.aidalert.sos.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class TimeUtil {
    private TimeUtil() {}

    public static String clock(long ms) {
        if (ms <= 0) return "-";
        return new SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()).format(new Date(ms));
    }

    public static String ago(long ms) {
        if (ms <= 0) return "-";
        long s = Math.max(0, (System.currentTimeMillis() - ms) / 1000);
        if (s < 60) return s + "s ago";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        return (s / 86400) + " d ago";
    }
}
