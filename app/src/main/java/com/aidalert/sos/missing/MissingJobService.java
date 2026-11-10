package com.aidalert.sos.missing;

import android.app.job.JobParameters;
import android.app.job.JobService;

import com.aidalert.sos.util.AppExecutors;

/** Runs the missing-person check in the background when Android allows it (about every few hours, with a network). */
public class MissingJobService extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        AppExecutors.io(() -> {
            try {
                MissingWatcher.runOnce(getApplicationContext());
            } catch (RuntimeException ignored) {
                // a bad page must never crash the app
            }
            jobFinished(params, false);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;       // try again later
    }
}
