package com.newtube.mobile.ui.settings;
import android.app.Application;
import android.content.Context;
import com.newtube.mobile.alerts.UploadAlertStore;
import com.liskovsoft.smartyoutubetv2.tv.R;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={24,28,35},application=Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class UploadAlertSettingsRecoveryTest {
 Context c;
 @Before public void setup(){c=RuntimeEnvironment.getApplication();c.getSharedPreferences("upload_alerts_v1",0).edit().clear().commit();}
 @Test public void openingSettingsDoesNotEnableOrSelectAnything(){SettingsPages.Page page=SettingsPages.build(c,SettingsPages.UPLOAD_ALERTS);assertFalse(new UploadAlertStore(c).automatic());assertTrue(new UploadAlertStore(c).channels().isEmpty());assertEquals(c.getString(R.string.upload_alerts_title),page.title);assertFalse(page.rows.isEmpty());}
 @Test public void manualSyncIsReachableWithAutomaticOff(){SettingsPages.Page page=UploadAlertPages.build(c);assertTrue(page.rows.stream().anyMatch(row->c.getString(R.string.upload_alerts_sync).equals(row.title)));}
 @Test public void corruptedPreferencesDoNotBreakSettings(){c.getSharedPreferences("upload_alerts_v1",0).edit().putInt("channels",17).putString("hours","bad").putInt("last_time",9).commit();assertFalse(UploadAlertPages.build(c).rows.isEmpty());}
}
