package com.example.smartpantrymanager.utils;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the daily expiry reminder in step with the Settings toggle (SharedPreferences "prefs" /
 * "expiry_alerts", default on). Call sync() whenever the toggle may have changed; PantryListActivity
 * does so in onResume, which covers coming back from the Settings screen.
 */
public final class ExpiryScheduler {
    static final String WORK_NAME = "daily_expiry_reminder";
    private static final int REMINDER_HOUR = 9;   // local time, first run at the next 09:00

    private ExpiryScheduler() {}

    public static void sync(Context context) {
        Context app = context.getApplicationContext();
        boolean enabled = app.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("expiry_alerts", true);
        WorkManager wm = WorkManager.getInstance(app);
        if (enabled) {
            PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(ExpiryWorker.class, 24, TimeUnit.HOURS)
                    .setInitialDelay(millisUntilNext(REMINDER_HOUR), TimeUnit.MILLISECONDS)
                    .build();
            // KEEP: re-syncing on every resume must not reset the schedule
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req);
        } else {
            wm.cancelUniqueWork(WORK_NAME);
        }
    }

    private static long millisUntilNext(int hour) {
        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_MONTH, 1);
        return next.getTimeInMillis() - now.getTimeInMillis();
    }
}
