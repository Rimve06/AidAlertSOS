package com.aidalert.sos.engine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the guard service after the phone reboots. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SosGuardService.start(context);
            com.aidalert.sos.medicine.MedicineScheduler.rescheduleAll(context);
        }
    }
}
