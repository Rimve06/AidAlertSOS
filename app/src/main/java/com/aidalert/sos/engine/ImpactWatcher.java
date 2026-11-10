package com.aidalert.sos.engine;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.aidalert.sos.R;
import com.aidalert.sos.ui.ImpactCountdownActivity;
import com.aidalert.sos.util.Notifier;

/** Listens to the accelerometer while the guard service runs and the user turned accident detection on. */
public final class ImpactWatcher implements SensorEventListener {
    private final Context ctx;
    private final SensorManager sm;
    private final ImpactDetector detector = new ImpactDetector();
    private boolean on;

    public ImpactWatcher(Context c) {
        ctx = c.getApplicationContext();
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
    }

    public boolean start() {
        if (on || sm == null) return on;
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s == null) return false;
        detector.reset();
        on = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
        return on;
    }

    public void stop() {
        if (on && sm != null) sm.unregisterListener(this);
        on = false;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.values.length < 3) return;
        if (detector.onSample(System.currentTimeMillis(), e.values[0], e.values[1], e.values[2])) ask();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not needed
    }

    private void ask() {
        Intent i = new Intent(ctx, ImpactCountdownActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            Notification n = new NotificationCompat.Builder(ctx, Notifier.CH_SOS)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("Possible accident detected")
                    .setContentText("Tap to say you are OK. SOS will be sent when the countdown ends.")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(Notifier.activity(ctx, i, 77), true)
                    .setAutoCancel(true)
                    .build();
            NotificationManagerCompat.from(ctx).notify(Notifier.ID_STATUS + 50, n);
        } catch (SecurityException ignored) {
            // notifications not allowed; the activity start below may still work while the app is visible
        }
        try {
            ctx.startActivity(i);
        } catch (RuntimeException ignored) {
            // background start refused; the full-screen notification covers it
        }
    }
}
