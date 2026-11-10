package com.aidalert.sos.missing;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import com.aidalert.sos.data.Prefs;

import java.util.concurrent.TimeUnit;

/** Turns the periodic missing-person check on or off. */
public final class MissingSchedule {
    private MissingSchedule() {}

    private static final int JOB_ID = 7301;

    /** Schedules when the setting is on, cancels when it is off. Safe to call often. */
    public static void apply(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!Prefs.of(ctx).missingWatch()) {
            js.cancel(JOB_ID);
            return;
        }
        for (JobInfo j : js.getAllPendingJobs()) {
            if (j.getId() == JOB_ID) return;       // already scheduled
        }
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, MissingJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(TimeUnit.HOURS.toMillis(3))
                .build();
        try {
            js.schedule(info);
        } catch (RuntimeException ignored) {
            // the system refused (too many jobs); it is tried again at the next app start
        }
    }
}
