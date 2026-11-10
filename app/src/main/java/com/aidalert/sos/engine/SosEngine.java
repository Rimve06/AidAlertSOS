package com.aidalert.sos.engine;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.aidalert.sos.Config;
import com.aidalert.sos.data.Db;
import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.EmergencyTypeActivity;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.Notifier;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Heart of the app (one instance per process). The guard service, the accessibility service and the
 * Home screen all talk to this class:
 *
 *   power presses  -> onScreenToggle()          (5 in a row, nothing in between)
 *   other input    -> onOtherInput()            (breaks the sequence)
 *   volume hold    -> onVolumeHold(up)          (up = type page, down = silent violence mode)
 *
 * trigger() creates the incident, starts GPS tracking, sends it and shows the type page (or, in
 * silent mode, only places the call).
 */
public final class SosEngine {
    private static SosEngine instance;

    public static synchronized SosEngine get(Context c) {
        if (instance == null) instance = new SosEngine(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final PowerTapDetector detector = new PowerTapDetector(Config.REQUIRED_TAPS, Config.MAX_TAP_GAP_MS);
    private LocationHelper tracker;
    private LocationHelper distressTracker;
    private ScheduledFuture<?> opsPoll;
    private ScheduledFuture<?> finalizeTask;
    private String distressDisaster = "";

    private SosEngine(Context c) {
        app = c;
    }

    // ================================================================ input events

    private boolean armed() {
        Prefs p = Prefs.of(app);
        return p.isLoggedIn() && !p.isCenter() && p.guardOn();
    }

    /** True when a hardware trigger would start an SOS right now (used by the key diagnostics). */
    public boolean canTrigger() {
        return armed();
    }

    /** Every screen ON/OFF broadcast equals one power-button press. */
    public void onScreenToggle() {
        if (!armed()) return;
        if (detector.onPowerPress(SystemClock.elapsedRealtime())) {
            trigger("POWER_5X", false);
        }
    }

    public void onOtherInput() {
        detector.onOtherInput();
    }

    public void onVolumeHold(boolean up) {
        if (!armed()) return;
        detector.onOtherInput();
        if (up) trigger("VOLUME_UP_HOLD", false);
        else trigger("VOLUME_DOWN_HOLD", true);
    }

    // ================================================================ trigger

    /** The incident that is active right now, or null. */
    public Incident current() {
        Prefs p = Prefs.of(app);
        String id = p.activeId();
        if (id.isEmpty()) return null;
        Incident i = Db.get(app).getIncident(id);
        if (i == null || !i.isActive() || System.currentTimeMillis() - i.createdAt > Config.SOS_MAX_AGE_MS) {
            p.clearActive();
            stopTracking();
            return null;
        }
        return i;
    }

    public synchronized Incident trigger(String source, boolean silent) {
        Prefs p = Prefs.of(app);
        if (!p.isLoggedIn() || p.isCenter()) return null;

        Incident existing = current();
        if (existing != null) {
            if (!existing.silent && !silent) showTypePage();
            return existing;
        }

        long now = System.currentTimeMillis();
        Incident i = new Incident();
        i.id = p.uid() + "_" + now;
        i.userId = p.uid();
        i.userName = p.name();
        i.phone = p.phone();
        i.type = silent ? "VIOLENCE" : "UNSPECIFIED";
        i.source = source;
        i.silent = silent;
        i.status = "NEW";
        i.createdAt = now;
        i.updatedAt = now;
        i.mine = true;
        i.battery = batteryPercent();
        if (p.includeMedical()) {
            i.bloodGroup = p.blood();
            i.medical = p.medicalNote();
        }
        Location last = new LocationHelper(app).lastKnown();
        if (last != null && now - last.getTime() < 10 * 60 * 1000L) {
            i.lat = last.getLatitude();
            i.lon = last.getLongitude();
            i.acc = last.getAccuracy();
        }
        Db.get(app).saveIncident(i);
        p.setActive(i.id, silent);
        p.notifiedFor("");

        startTracking(i.id);
        final Incident snapshot = i;
        AppExecutors.io(() -> Dispatcher.sendInitial(app, snapshot));

        if (silent) {
            // Violence mode: no screen, no sound, no vibration. Contacts are texted and the call starts.
            AppExecutors.io(() -> finalizeDispatch(snapshot.id));
        } else {
            vibrate();
            showTypePage();
            scheduleFinalize(snapshot.id);
        }
        return i;
    }

    /**
     * Support companion escalation: the user (or a countdown they did not cancel) asked for a person to check on them.
     * No emergency-type page, no automatic phone call. The control center and the emergency contacts are told.
     * Only a short note goes out, never the conversation.
     */
    public synchronized Incident triggerSupport(String note) {
        Prefs p = Prefs.of(app);
        if (!p.isLoggedIn() || p.isCenter()) return null;
        Incident existing = current();
        if (existing != null) return existing;
        long now = System.currentTimeMillis();
        Incident i = new Incident();
        i.id = p.uid() + "_" + now;
        i.userId = p.uid();
        i.userName = p.name();
        i.phone = p.phone();
        i.type = "MENTAL_HEALTH";
        i.source = "SUPPORT";
        i.note = note == null ? "" : note;
        i.status = "NEW";
        i.createdAt = now;
        i.updatedAt = now;
        i.mine = true;
        i.battery = batteryPercent();
        Location last = new LocationHelper(app).lastKnown();
        if (last != null && now - last.getTime() < 10 * 60 * 1000L) {
            i.lat = last.getLatitude();
            i.lon = last.getLongitude();
            i.acc = last.getAccuracy();
        }
        Db.get(app).saveIncident(i);
        p.setActive(i.id, false);
        p.notifiedFor(i.id);
        startTracking(i.id);
        final Incident snapshot = i;
        AppExecutors.io(() -> {
            Dispatcher.sendInitial(app, snapshot);
            Dispatcher.notifyContacts(app, snapshot);
        });
        return i;
    }

    private void showTypePage() {
        wakeScreen();
        Intent it = new Intent(app, EmergencyTypeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            app.startActivity(it);
        } catch (RuntimeException ignored) {
            // blocked while in background: the full-screen notification below is the fallback
        }
        Notifier.fullScreen(app, it, "SOS activated", "Tap to choose the emergency type");
    }

    @SuppressWarnings("deprecation")
    private void wakeScreen() {
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "aidalert:sos");
            wl.acquire(10_000);
        } catch (RuntimeException ignored) {
            // not critical
        }
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(400);
        } catch (RuntimeException ignored) {
            // not critical
        }
    }

    private int batteryPercent() {
        BatteryManager bm = (BatteryManager) app.getSystemService(Context.BATTERY_SERVICE);
        return bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    // ================================================================ tracking

    private synchronized void startTracking(final String id) {
        stopTracking();
        tracker = new LocationHelper(app);
        tracker.start(Config.TRACK_INTERVAL_MS, loc -> AppExecutors.io(() -> onFix(id, loc)));
        opsPoll = AppExecutors.scheduler().scheduleWithFixedDelay(() -> {
            try {
                pollOps(id);
            } catch (RuntimeException ignored) {
                // keep the schedule alive
            }
        }, 5, 6, TimeUnit.SECONDS);
    }

    public synchronized void stopTracking() {
        if (tracker != null) {
            tracker.stop();
            tracker = null;
        }
        if (opsPoll != null) {
            opsPoll.cancel(false);
            opsPoll = null;
        }
    }

    private void onFix(String id, Location loc) {
        Db db = Db.get(app);
        Incident i = db.getIncident(id);
        if (i == null || !i.isActive()) return;
        boolean firstFix = !i.hasFix();
        i.lat = loc.getLatitude();
        i.lon = loc.getLongitude();
        i.acc = loc.getAccuracy();
        i.updatedAt = System.currentTimeMillis();
        db.saveIncident(i);
        Dispatcher.sendUpdate(app, i, firstFix);
    }

    /** Looks for control-center actions (acknowledged, ambulance dispatched ...). */
    private void pollOps(String id) {
        if (!NetUtil.isOnline(app)) return;
        JSONObject ops = new FirebaseRest(app).getObj("incidents/" + id + "/ops");
        if (ops == null) return;
        String st = ops.optString("status", "");
        if (st.isEmpty()) return;
        Db db = Db.get(app);
        Incident i = db.getIncident(id);
        if (i == null) return;
        String dispatched = ops.optString("dispatched", "");
        boolean changed = !st.equals(i.status) || !dispatched.equals(i.dispatched);
        if (!changed || "CANCELLED".equals(i.status)) return;
        i.status = st;
        i.dispatched = dispatched;
        i.opsNote = ops.optString("note", "");
        i.ackBy = ops.optString("ackBy", "");
        db.saveIncident(i);

        if (!i.silent) {
            String text = "Status: " + st.replace('_', ' ')
                    + (dispatched.isEmpty() ? "" : " - dispatched: " + dispatched)
                    + (i.opsNote.isEmpty() ? "" : " - " + i.opsNote);
            Notifier.show(app, Notifier.ID_STATUS, Notifier.CH_SOS, "Control center update", text,
                    new Intent(app, EmergencyTypeActivity.class));
        }
        if (!i.isActive()) {
            Prefs.of(app).clearActive();
            stopTracking();
        }
    }

    // ================================================================ user actions

    public void setType(final String type, final String note) {
        final String id = Prefs.of(app).activeId();
        if (id.isEmpty()) return;
        AppExecutors.io(() -> {
            Db db = Db.get(app);
            Incident i = db.getIncident(id);
            if (i == null) return;
            i.type = type;
            if (note != null && !note.trim().isEmpty()) i.note = note.trim();
            i.updatedAt = System.currentTimeMillis();
            db.saveIncident(i);
            Dispatcher.sendUpdate(app, i, true);
            boolean alreadyNotified = id.equals(Prefs.of(app).notifiedFor());
            finalizeDispatch(id);
            if (alreadyNotified) Dispatcher.notifyContacts(app, i);
        });
    }

    private void scheduleFinalize(final String id) {
        if (finalizeTask != null) finalizeTask.cancel(false);
        finalizeTask = AppExecutors.scheduler().schedule(() -> {
            try {
                finalizeDispatch(id);
            } catch (RuntimeException ignored) {
                // nothing more we can do
            }
        }, Config.TYPE_PAGE_TIMEOUT_S, TimeUnit.SECONDS);
    }

    /** SMS to contacts + automatic call. Runs once per incident (when the type is chosen or on timeout). */
    public void finalizeDispatch(String id) {
        Prefs p = Prefs.of(app);
        synchronized (this) {
            if (id.equals(p.notifiedFor())) return;
            p.notifiedFor(id);
        }
        Incident i = Db.get(app).getIncident(id);
        if (i == null || !i.isActive()) return;
        Dispatcher.notifyContacts(app, i);
        // A silent (violence) SOS always calls: the operator listens to the surroundings.
        if (p.autoCall() || i.silent) Dispatcher.autoCall(app);
    }

    /** User ends the SOS: status CANCELLED (false alarm / safe now). */
    public void endSos(final String status) {
        final String id = Prefs.of(app).activeId();
        if (id.isEmpty()) return;
        Prefs.of(app).clearActive();
        stopTracking();
        if (finalizeTask != null) finalizeTask.cancel(false);
        Notifier.cancel(app, Notifier.ID_SOS_SCREEN);
        Notifier.cancel(app, Notifier.ID_STATUS);
        AppExecutors.io(() -> {
            Db db = Db.get(app);
            Incident i = db.getIncident(id);
            if (i == null) return;
            i.status = status;
            i.updatedAt = System.currentTimeMillis();
            db.saveIncident(i);
            Dispatcher.sendUpdate(app, i, true);
        });
    }

    // ================================================================ disaster distress / check-in

    /** Distress toggle: while ON the location is sent once a minute. Turning it OFF deletes the record. */
    public void setDistress(boolean on, String disasterId) {
        Prefs p = Prefs.of(app);
        p.distressOn(on);
        stopDistressTracker();
        if (on) {
            distressDisaster = disasterId == null ? "" : disasterId;
            startDistressTracker();
        } else {
            final String uid = p.uid();
            AppExecutors.io(() -> new FirebaseRest(app).delete("checkins/" + uid));
        }
    }

    /** Called by the guard service after a process restart. */
    public void resumeDistressIfOn() {
        if (Prefs.of(app).distressOn() && distressTracker == null) startDistressTracker();
    }

    private synchronized void startDistressTracker() {
        distressTracker = new LocationHelper(app);
        distressTracker.start(60_000, loc ->
                AppExecutors.io(() -> pushCheckin("DISTRESSED", loc, distressDisaster)));
    }

    private synchronized void stopDistressTracker() {
        if (distressTracker != null) {
            distressTracker.stop();
            distressTracker = null;
        }
    }

    /** One-time "I'm safe" (or other status) check-in with a single position. */
    public void checkinOnce(final String status, final String disasterId) {
        AppExecutors.io(() -> {
            Location l = new LocationHelper(app).lastKnown();
            if (l != null && System.currentTimeMillis() - l.getTime() < 10 * 60 * 1000L) {
                pushCheckin(status, l, disasterId);
                return;
            }
            final LocationHelper h = new LocationHelper(app);
            h.start(1000, fix -> {
                h.stop();
                AppExecutors.io(() -> pushCheckin(status, fix, disasterId));
            });
            AppExecutors.scheduler().schedule(h::stop, 30, TimeUnit.SECONDS);
        });
    }

    private void pushCheckin(String status, Location l, String disasterId) {
        Prefs p = Prefs.of(app);
        if (!p.isLoggedIn() || l == null || !NetUtil.isOnline(app)) return;
        JSONObject o = new JSONObject();
        try {
            o.put("uid", p.uid());
            o.put("name", p.name());
            o.put("status", status);
            o.put("lat", l.getLatitude());
            o.put("lon", l.getLongitude());
            o.put("ts", System.currentTimeMillis());
            o.put("disasterId", disasterId == null ? "" : disasterId);
        } catch (JSONException e) {
            return;
        }
        new FirebaseRest(app).put("checkins/" + p.uid(), o);
    }
}
