package com.aidalert.sos.data;

import android.content.Context;

import com.aidalert.sos.medicine.MedicineScheduler;
import com.aidalert.sos.medicine.MedicineStore;

/** Removes what belongs to the signed-in account from this phone when it signs out or the account disappears. */
public final class LocalData {
    private LocalData() {}

    public static void wipeAccount(Context c) {
        Context app = c.getApplicationContext();
        String uid = Prefs.of(app).uid();
        try {
            MedicineScheduler.cancelAll(app);
            MedicineStore m = new MedicineStore(app);
            if (!uid.isEmpty()) m.wipeFor(uid);
            app.getSharedPreferences("med_sync", Context.MODE_PRIVATE).edit().clear().apply();
        } catch (RuntimeException ignored) {
            // nothing stored yet
        }
        try {
            new com.aidalert.sos.ai.ChatStore(app).clear();          // this account's assistant chat
        } catch (RuntimeException ignored) {
            // nothing stored yet
        }
        try {
            new com.aidalert.sos.blood.BloodStore(app).clearMine();
        } catch (RuntimeException ignored) {
            // nothing stored yet
        }
    }
}
