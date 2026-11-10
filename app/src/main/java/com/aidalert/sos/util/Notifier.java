package com.aidalert.sos.util;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.aidalert.sos.R;
import com.aidalert.sos.ui.HomeActivity;

public final class Notifier {
    private Notifier() {}

    public static final String CH_GUARD = "guard";
    public static final String CH_SOS = "sos";
    public static final String CH_ALERT = "alert";
    public static final String CH_MSG = "msg";
    public static final String CH_MISSING = "missing";

    public static final int ID_GUARD = 1001;
    public static final int ID_SOS_SCREEN = 1002;
    public static final int ID_STATUS = 1003;
    public static final int ID_DISASTER = 1004;
    public static final int ID_HELP = 1005;
    public static final int ID_CENTER_MSG = 1006;
    public static final int ID_BROADCAST = 1007;
    public static final int ID_RECORD = 1008;
    public static final int ID_NEW_INCIDENT = 1009;
    public static final int ID_BOOT = 1010;

    public static void createChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CH_GUARD, "SOS guard", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_SOS, "Active SOS", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_ALERT, "Disaster and help alerts", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_MSG, "Messages and records", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CH_MISSING, "Missing person reports", NotificationManager.IMPORTANCE_DEFAULT));
    }

    public static PendingIntent activity(Context c, Intent i, int requestCode) {
        return PendingIntent.getActivity(c, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Ongoing notification required for the foreground guard service. */
    public static Notification guard(Context c) {
        Intent open = new Intent(c, HomeActivity.class);
        return new NotificationCompat.Builder(c, CH_GUARD)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("AidAlert guard is on")
                .setContentText("Press power 5 times, or hold a volume key, to send an SOS")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(activity(c, open, 10))
                .build();
    }

    @SuppressLint("MissingPermission")
    public static void show(Context c, int id, String channel, String title, String text, Intent target) {
        PendingIntent pi = target == null ? null : activity(c, target, id);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(CH_MSG.equals(channel) ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_HIGH);
        if (pi != null) b.setContentIntent(pi);
        try {
            NotificationManagerCompat.from(c).notify(id, b.build());
        } catch (SecurityException ignored) {
            // notification permission not granted
        }
    }

    /** High priority notification that can light up the screen and open the type page. */
    @SuppressLint("MissingPermission")
    public static void fullScreen(Context c, Intent target, String title, String text) {
        PendingIntent pi = activity(c, target, ID_SOS_SCREEN);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_SOS)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true);
        try {
            NotificationManagerCompat.from(c).notify(ID_SOS_SCREEN, b.build());
        } catch (SecurityException ignored) {
            // notification permission not granted
        }
    }

    public static void cancel(Context c, int id) {
        NotificationManagerCompat.from(c).cancel(id);
    }
}
