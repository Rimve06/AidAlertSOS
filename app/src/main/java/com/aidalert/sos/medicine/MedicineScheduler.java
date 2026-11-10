package com.aidalert.sos.medicine;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;
import java.util.TimeZone;

/** Sets one alarm per reminder time. Each alarm re-arms itself for the next day when it fires. */
public final class MedicineScheduler {
    private MedicineScheduler() {}

    static final String EXTRA_ID = "id", EXTRA_TIME = "time";

    private static PendingIntent pi(Context c, long id, int idx, String time, int flags) {
        Intent i = new Intent(c, MedicineReceiver.class).putExtra(EXTRA_ID, id).putExtra(EXTRA_TIME, time);
        return PendingIntent.getBroadcast(c, (int) (id * 10 + idx), i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c, MedicineStore.Med m) {
        if (!m.active) return;
        List<String> times = MedicineRules.parseTimes(m.times);
        for (int i = 0; i < times.size() && i < 10; i++) arm(c, m.id, i, times.get(i));
    }

    static void arm(Context c, long id, int idx, String time) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = MedicineRules.nextTrigger(System.currentTimeMillis(), time, TimeZone.getDefault());
        PendingIntent p = pi(c, id, idx, time, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    public static void cancel(Context c, MedicineStore.Med m) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        List<String> times = MedicineRules.parseTimes(m.times);
        for (int i = 0; i < times.size() && i < 10; i++) {
            PendingIntent p = pi(c, m.id, i, times.get(i), PendingIntent.FLAG_NO_CREATE);
            if (p != null) am.cancel(p);
        }
    }

    /** Cancels the alarms of every medicine stored on this phone (sign-out / account removed). */
    public static void cancelAll(Context c) {
        MedicineStore store = new MedicineStore(c);
        for (MedicineStore.Med m : store.every()) cancel(c, m);
    }

    /** After a reboot all alarms are gone: set them again. */
    public static void rescheduleAll(Context c) {
        MedicineStore store = new MedicineStore(c);
        for (MedicineStore.Med m : store.all()) {
            if (m.active && !MedicineRules.hasEnded(m.startMs, m.days, System.currentTimeMillis())) schedule(c, m);
        }
    }

    /** Index of the time inside the medicine's sorted list, or -1. */
    static int indexOf(MedicineStore.Med m, String time) {
        return MedicineRules.parseTimes(m.times).indexOf(time);
    }
}
