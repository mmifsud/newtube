package com.newtube.mobile.ui.playback;
import android.app.Application;
import android.os.Bundle;
import android.graphics.SurfaceTexture;
import android.view.*;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={24,28,35},manifest=Config.NONE,application=Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaybackRecoveryTest {
 @Test public void pausedPositionSurvives(){Bundle b=new Bundle();PlaybackRestoreState.save(b,Video.from("abcdefghijk"),12345,false,"signed-out",false,false);PlaybackRestoreState s=PlaybackRestoreState.read(b,"signed-out");assertNotNull(s);assertEquals(12345,s.positionMs);assertFalse(s.playWhenReady);assertEquals(12345,s.video().pendingPosMs);}
 @Test public void accountChangeRejectsState(){Bundle b=new Bundle();PlaybackRestoreState.save(b,Video.from("abcdefghijk"),0,false,"a",false,false);assertNull(PlaybackRestoreState.read(b,"b"));}
 @Test public void privateVideoIsNotSaved(){Bundle b=new Bundle();PlaybackRestoreState.save(b,Video.from("abcdefghijk"),0,false,"a",true,false);assertNull(PlaybackRestoreState.read(b,"a"));}
 @Test public void malformedBundleIgnored(){Bundle b=new Bundle(),s=new Bundle();s.putString("version","1");b.putBundle(PlaybackRestoreState.KEY,s);assertNull(PlaybackRestoreState.read(b,"a"));}
 @Test public void liveVideoIsNotSaved(){Bundle b=new Bundle();Video v=Video.from("abcdefghijk");v.isLive=true;PlaybackRestoreState.save(b,v,10,true,"a",false,false);assertNull(PlaybackRestoreState.read(b,"a"));}
 @Test public void leasesRetainBorrowedTextureUntilOwnerRelease(){PlaybackSurfaceSession s=new PlaybackSurfaceSession();PlaybackSurfaceSession.Lease l=new PlaybackSurfaceSession.Lease();SurfaceTexture t=new SurfaceTexture(0);TextureView v=new TextureView(RuntimeEnvironment.getApplication());assertTrue(s.attach(v,t,l,surface->{}));assertFalse(l.shouldRelease(t));assertTrue(l.shouldRelease(new SurfaceTexture(0)));s.release();s.release();assertNull(s.texture());assertNull(s.surface());}
 @Test public void failedOutputDoesNotAdoptOrRetainTexture(){PlaybackSurfaceSession s=new PlaybackSurfaceSession();SurfaceTexture t=new SurfaceTexture(0);try{s.attach(new TextureView(RuntimeEnvironment.getApplication()),t,new PlaybackSurfaceSession.Lease(),surface->{throw new IllegalStateException();});fail();}catch(IllegalStateException expected){}assertNull(s.texture());t.release();}
}
