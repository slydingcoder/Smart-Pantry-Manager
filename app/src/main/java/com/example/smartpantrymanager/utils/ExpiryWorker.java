package com.example.smartpantrymanager.utils;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.activities.PantryListActivity;
import com.example.smartpantrymanager.activities.SuggestedRecipesActivity;
import com.example.smartpantrymanager.database.DatabaseHelper;
import com.example.smartpantrymanager.models.Ingredient;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;

/** Runs about once a day (see ExpiryScheduler) and notifies about food that is expiring or expired. */
public class ExpiryWorker extends Worker {
    public static final String CHANNEL_ID = "expiry_alerts";
    public static final String EXTRA_FILTER_EXPIRING = "filter_expiring";
    private static final int NOTIFICATION_ID = 4001;
    private static final int EXPIRED_LOOKBACK_DAYS = 7;   // don't nag forever about long-expired items

    public ExpiryWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        // the toggle may have been switched off since this run was scheduled
        if (!ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("expiry_alerts", true)) {
            return Result.success();
        }
        List<Ingredient> items;
        DatabaseHelper db = new DatabaseHelper(ctx);
        try { items = db.getAllPantry(); } finally { db.close(); }

        long today = ExpiryDates.today();
        List<String> lines = new ArrayList<>();
        for (Ingredient p : items) {
            long days = ExpiryDates.daysUntil(p.expiry, today);
            if (days == ExpiryDates.NO_DATE) continue;
            if (days > RecipeMatcher.EXPIRING_SOON_DAYS || days < -EXPIRED_LOOKBACK_DAYS) continue;
            lines.add(p.name + " (" + describe(days) + ")");
        }
        if (lines.isEmpty()) return Result.success();
        notifyUser(ctx, lines);
        return Result.success();
    }

    private static String describe(long days) {
        if (days < 0) return "expired";
        if (days == 0) return "expires today";
        if (days == 1) return "expires tomorrow";
        return "expires in " + days + " days";
    }

    @SuppressLint("MissingPermission")   // guarded by areNotificationsEnabled(), which includes POST_NOTIFICATIONS
    private void notifyUser(Context ctx, List<String> lines) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        if (!nm.areNotificationsEnabled()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Expiry reminders",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Daily reminder about food that is about to expire");
            ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }

        // Tapping opens Suggestions with the "uses expiring items" filter on; Back goes to the pantry.
        Intent suggestions = new Intent(ctx, SuggestedRecipesActivity.class).putExtra(EXTRA_FILTER_EXPIRING, true);
        PendingIntent pi = TaskStackBuilder.create(ctx)
                .addNextIntent(new Intent(ctx, PantryListActivity.class))
                .addNextIntent(suggestions)
                .getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String title = lines.size() == 1 ? "1 item needs using up" : lines.size() + " items need using up";
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(android.text.TextUtils.join(", ", lines))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(android.text.TextUtils.join("\n", lines)))
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify(NOTIFICATION_ID, b.build());
    }
}
