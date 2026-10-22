package com.aidalert.sos.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Shared thread pools (java.util.concurrent): network and database work never runs on the UI thread.
 */
public final class AppExecutors {
    private AppExecutors() {}

    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Run on a background worker thread. */
    public static void io(Runnable r) {
        IO.execute(r);
    }

    /** Run on the UI thread. */
    public static void main(Runnable r) {
        MAIN.post(r);
    }

    public static void mainDelayed(Runnable r, long delayMs) {
        MAIN.postDelayed(r, delayMs);
    }

    public static void cancelMain(Runnable r) {
        MAIN.removeCallbacks(r);
    }

    public static ScheduledExecutorService scheduler() {
        return SCHEDULER;
    }
}
