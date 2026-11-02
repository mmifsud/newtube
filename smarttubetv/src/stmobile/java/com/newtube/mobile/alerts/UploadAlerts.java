package com.newtube.mobile.alerts;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.concurrent.TimeUnit;

/** Reconstructed from private R3 APK; reviewed against its bytecode. */
public final class UploadAlerts {
    static final String CHANNEL = "channel_uploads_v1";
    public static final String MANUAL = "channel-upload-alerts-manual-v1";
    public static final String PERIODIC = "channel-upload-alerts-periodic-v1";

    private UploadAlerts() {
    }

    public static void createChannel(Context c) {
        NotificationManager manager;
        if (Build.VERSION.SDK_INT >= 26 && (manager = (NotificationManager) c.getSystemService(NotificationManager.class)) != null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.upload_alerts_title), NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    public static boolean canNotify(Context c) {
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT < 26) {
            return true;
        }
        NotificationManager manager = (NotificationManager) c.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager != null ? manager.getNotificationChannel(CHANNEL) : null;
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    public static void requestPermission(Activity activity) {
        Intent settings;
        createChannel(activity);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, "android.permission.POST_NOTIFICATIONS") != 0) {
            ActivityCompat.requestPermissions(activity, new String[]{"android.permission.POST_NOTIFICATIONS"}, 7461);
        } else if (!canNotify(activity)) {
            if (Build.VERSION.SDK_INT >= 26) {
                settings = new Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", activity.getPackageName());
            } else {
                settings = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + activity.getPackageName()));
            }
            activity.startActivity(settings);
        }
    }

    static Constraints constraints(UploadAlertStore store) {
        return constraints(store, false);
    }

    private static Constraints constraints(UploadAlertStore store, boolean automatic) {
        return new Constraints.Builder().setRequiredNetworkType(store.unmetered() ? NetworkType.UNMETERED : NetworkType.CONNECTED).setRequiresBatteryNotLow(automatic).build();
    }

    public static void reconcile(Context c, boolean changed) {
        UploadAlertStore store = new UploadAlertStore(c);
        WorkManager work = WorkManager.getInstance(c);
        if (!store.automatic() || store.channels().isEmpty() || !canNotify(c)) {
            work.cancelUniqueWork(PERIODIC);
        } else {
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder((Class<? extends ListenableWorker>) UploadAlertWorker.class, store.hours(), TimeUnit.HOURS).setInitialDelay(store.hours(), TimeUnit.HOURS).setConstraints(constraints(store, true)).setInputData(new Data.Builder().putBoolean("automatic", true).build()).build();
            work.enqueueUniquePeriodicWork(PERIODIC, changed ? ExistingPeriodicWorkPolicy.UPDATE : ExistingPeriodicWorkPolicy.KEEP, request);
        }
    }

    public static void syncNow(Context c) {
        UploadAlertStore store = new UploadAlertStore(c);
        if (store.channels().isEmpty() || !canNotify(c)) {
            return;
        }
        WorkManager.getInstance(c).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, new OneTimeWorkRequest.Builder((Class<? extends ListenableWorker>) UploadAlertWorker.class).setConstraints(constraints(store)).build());
    }

    public static void networkChanged(Context c) {
        WorkManager.getInstance(c).cancelUniqueWork(MANUAL);
        reconcile(c, true);
    }

    static boolean allowedNetwork(Context c, boolean unmetered) {
        NetworkCapabilities caps;
        ConnectivityManager manager = (ConnectivityManager) c.getSystemService(ConnectivityManager.class);
        if (manager != null && (caps = manager.getNetworkCapabilities(manager.getActiveNetwork())) != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return (unmetered && manager.isActiveNetworkMetered()) ? false : true;
        }
        return false;
    }
}
