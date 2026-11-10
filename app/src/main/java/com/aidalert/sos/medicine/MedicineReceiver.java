package com.aidalert.sos.medicine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.aidalert.sos.R;
import com.aidalert.sos.util.Notifier;

/** Fires at a reminder time: shows the notification and arms the same time for tomorrow. */
public class MedicineReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra(MedicineScheduler.EXTRA_ID, -1);
        String time = intent.getStringExtra(MedicineScheduler.EXTRA_TIME);
        if (id < 0 || time == null) return;
        MedicineStore store = new MedicineStore(ctx);
        MedicineStore.Med m = store.get(id);
        if (m == null || !m.active) return;
        // an alarm of another account that used this phone must never ring for the signed-in user
        if (!m.userId.equals(com.aidalert.sos.data.Prefs.of(ctx).uid())) return;
        if (MedicineRules.hasEnded(m.startMs, m.days, System.currentTimeMillis())) {
            store.setActive(id, false);
            return;
        }
        int idx = MedicineScheduler.indexOf(m, time);
        if (idx >= 0) MedicineScheduler.arm(ctx, id, idx, time);
        // a medicine whose start date is still ahead stays silent until that day
        if (!MedicineRules.isDueToday(m.startMs, m.days, System.currentTimeMillis(), java.util.TimeZone.getDefault())) return;
        String extra = m.food.isEmpty() || m.food.equals("No matter") ? "" : m.food + ". ";
        Notifier.createChannels(ctx);
        try {
            NotificationManagerCompat.from(ctx).notify((int) (id * 10 + Math.max(idx, 0)) + 5000,
                    new NotificationCompat.Builder(ctx, Notifier.CH_ALERT)
                            .setSmallIcon(R.drawable.ic_notif)
                            .setContentTitle("Time for " + m.name)
                            .setContentText((m.dose.isEmpty() ? "" : m.dose + ". ") + extra + "Check your prescription.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .setContentIntent(Notifier.activity(ctx, new Intent(ctx, MedicineActivity.class), 88))
                            .build());
        } catch (SecurityException ignored) {
            // notification permission not granted
        }
    }
}
