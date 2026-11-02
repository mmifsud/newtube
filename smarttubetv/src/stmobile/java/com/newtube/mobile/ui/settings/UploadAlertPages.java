package com.newtube.mobile.ui.settings;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;
import androidx.core.app.NotificationManagerCompat;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.alerts.UploadAlertStore;
import com.newtube.mobile.alerts.UploadAlerts;
import java.text.DateFormat;
import java.util.*;

/** Device-local upload alerts; no YouTube account or remote bell settings are changed. */
final class UploadAlertPages {
    static SettingsPages.Page build(Context c) {
        UploadAlertStore store = new UploadAlertStore(c);
        List<SettingsRow> rows = new ArrayList<>();
        rows.add(SettingsRow.note(c.getString(R.string.upload_alerts_explanation)));
        rows.add(SettingsRow.toggle(c.getString(R.string.upload_alerts_auto), c.getString(R.string.upload_alerts_auto_summary),
                store::automatic, on -> {
                    store.automatic(on);
                    if (on && !UploadAlerts.canNotify(c) && c instanceof Activity) UploadAlerts.requestPermission((Activity)c);
                    UploadAlerts.reconcile(c, true);
                    if (on) UploadAlerts.syncNow(c);
                }));
        rows.add(SettingsRow.choice(c.getString(R.string.upload_alerts_interval))
                .option(c.getString(R.string.upload_alerts_hourly),1)
                .option(c.getString(R.string.upload_alerts_three_hours),3)
                .option(c.getString(R.string.upload_alerts_six_hours),6)
                .option(c.getString(R.string.upload_alerts_twelve_hours),12)
                .bind(store::hours, value -> { store.hours((Integer)value); UploadAlerts.reconcile(c,true); }));
        rows.add(SettingsRow.toggle(c.getString(R.string.upload_alerts_unmetered),c.getString(R.string.upload_alerts_unmetered_summary),
                store::unmetered, on -> {store.unmetered(on);UploadAlerts.networkChanged(c);}));
        rows.add(SettingsRow.action(c.getString(R.string.upload_alerts_sync),c.getString(R.string.upload_alerts_sync_summary),page -> {
            int message;
            if(store.channels().isEmpty()) message=R.string.upload_alerts_select_first;
            else if(!UploadAlerts.canNotify(c)) {UploadAlerts.requestPermission(page.requireActivity());message=R.string.upload_alerts_permission_then_sync;}
            else {UploadAlerts.syncNow(c);message=R.string.upload_alerts_queued;}
            Toast.makeText(c,message,Toast.LENGTH_LONG).show();
        }));
        rows.add(SettingsRow.note(store.lastTime()==0?c.getString(R.string.upload_alerts_never):c.getString(R.string.upload_alerts_last,
                DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(store.lastTime())),store.checked(),store.failed(),store.alerts())));
        if(!UploadAlerts.canNotify(c)) rows.add(SettingsRow.action(c.getString(R.string.upload_alerts_permission),c.getString(R.string.upload_alerts_permission_summary),page->UploadAlerts.requestPermission(page.requireActivity())));
        rows.add(SettingsRow.header(c.getString(R.string.upload_alerts_channels)));
        rows.add(SettingsRow.note(c.getString(R.string.upload_alerts_select_help,UploadAlertStore.MAX_CHANNELS)));
        for(UploadAlertStore.Channel channel:store.channels()) rows.add(SettingsRow.toggle(channel.name,null,()->store.selected(channel.id),on->{
            store.select(channel.id,channel.name,on);
            if(!on)NotificationManagerCompat.from(c).cancel("channel-upload:"+channel.id,0);
            UploadAlerts.reconcile(c,false);
        }));
        return new SettingsPages.Page(c.getString(R.string.upload_alerts_title),rows);
    }
}
