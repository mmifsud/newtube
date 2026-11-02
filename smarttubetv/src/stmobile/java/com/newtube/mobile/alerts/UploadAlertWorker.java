package com.newtube.mobile.alerts;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ListenableWorker;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.common.net.HttpHeaders;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity;
import com.newtube.mobile.alerts.UploadAlertStore;
import com.newtube.mobile.alerts.UploadAlertSync;
import com.newtube.mobile.alerts.UploadFeed;
import com.newtube.mobile.player.MediaHttpClient;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/* loaded from: classes22.dex */
public final class UploadAlertWorker extends Worker {
    private static final ReentrantLock RUN = new ReentrantLock();
    private volatile Call activeCall;

    public UploadAlertWorker(Context c, WorkerParameters p) {
        super(c, p);
    }

    @Override // androidx.work.ListenableWorker
    public void onStopped() {
        Call call = this.activeCall;
        if (call != null) {
            call.cancel();
        }
    }

    @Override // androidx.work.Worker
    public ListenableWorker.Result doWork() {
        final Context c = getApplicationContext();
        final UploadAlertStore store = new UploadAlertStore(c);
        if (isStopped() || store.channels().isEmpty() || (getInputData().getBoolean("automatic", false) && !store.automatic())) {
            return ListenableWorker.Result.success();
        }
        if (!UploadAlerts.canNotify(c)) {
            WorkManager.getInstance(c).cancelUniqueWork(UploadAlerts.PERIODIC);
            return ListenableWorker.Result.success();
        }
        if (!RUN.tryLock()) {
            return getRunAttemptCount() < 4 ? ListenableWorker.Result.retry() : ListenableWorker.Result.failure();
        }
        try {
            UploadAlerts.createChannel(c);
            final OkHttpClient client = MediaHttpClient.create(OkHttpManager.instance().getClient()).newBuilder().pingInterval(0L, TimeUnit.SECONDS).callTimeout(8L, TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build();
            UploadAlertSync.Outcome outcome = UploadAlertSync.run(store,
                    (channel, state) -> fetch(client, channel, state),
                    (channel, entries) -> notifyUploads(c, channel, entries),
                    () -> !isStopped() && UploadAlerts.canNotify(c)
                            && UploadAlerts.allowedNetwork(c, store.unmetered())
                            && (!getInputData().getBoolean("automatic", false) || store.automatic()));
            if (!isStopped()) {
                store.completed(outcome.checked, outcome.failed, outcome.alerts);
            }
            return ListenableWorker.Result.success();
        } finally {
            this.activeCall = null;
            RUN.unlock();
        }
    }

    private UploadAlertSync.Feed fetch(OkHttpClient client, UploadAlertStore.Channel channel,
            UploadAlertStore.State state) throws IOException {
        Request.Builder request = new Request.Builder().url("https://www.youtube.com/feeds/videos.xml?channel_id=" + channel.id);
        if (state.initialized) {
            header(request, "If-None-Match", state.etag);
            header(request, "If-Modified-Since", state.modified);
        }
        Call call = client.newCall(request.build()); activeCall = call;
        if (isStopped()) { call.cancel(); activeCall = null; throw new IOException("Stopped"); }
        try (Response response = call.execute()) {
            if (response.code() == 429) throw new UploadAlertSync.RateLimited();
            if (response.code() == 304 && state.initialized) return new UploadAlertSync.Feed(null, state.etag, state.modified);
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Feed unavailable");
            return new UploadAlertSync.Feed(UploadFeed.parse(response.body().byteStream(), channel.id, System.currentTimeMillis()),
                    validator(response.header("ETag")), validator(response.header("Last-Modified")));
        } finally { activeCall = null; }
    }

    static String validator(String value) {
        if (value == null || value.length() > 512) {
            return "";
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < ' ' || value.charAt(i) > '~') {
                return "";
            }
        }
        return value;
    }

    private static void header(Request.Builder builder, String name, String value) {
        String safe = validator(value);
        if (!safe.isEmpty()) {
            builder.header(name, safe);
        }
    }
    public static boolean notifyUploads(Context c, UploadAlertStore.Channel channel, List<UploadFeed.Entry> entries) {
        if ((Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, "android.permission.POST_NOTIFICATIONS") != 0) || !UploadAlerts.canNotify(c)) {
            return false;
        }
        if (entries.isEmpty()) return false;
        UploadFeed.Entry first = entries.get(0);
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + first.id), c, SplashActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(entries.size(), 5); i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(entries.get(i).title);
        }
        try {
            NotificationManagerCompat.from(c).notify("channel-upload:" + channel.id, 0, new NotificationCompat.Builder(c, "channel_uploads_v1").setSmallIcon(R.drawable.ic_upload_alerts).setContentTitle(channel.name).setContentText(first.title).setStyle(new NotificationCompat.BigTextStyle().bigText(text)).setNumber(entries.size()).setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true).build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
