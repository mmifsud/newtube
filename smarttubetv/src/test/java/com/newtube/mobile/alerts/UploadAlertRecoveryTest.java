package com.newtube.mobile.alerts;
import android.app.Application;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={24,28,35},manifest=Config.NONE,application=Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class UploadAlertRecoveryTest {
 static final String C="UCabcdefghijklmnopqrstuv",V="abcdefghijk";
 UploadAlertStore store;
 @Before public void setup(){Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("upload_alerts_v1",0).edit().clear().commit();store=new UploadAlertStore(c);store.select(C,"Channel",true);}
 UploadFeed.Entry e(String id,long t){return new UploadFeed.Entry(id,"Title",t);}
 List<UploadFeed.Entry> entries(){return Arrays.asList(e(V,1000));}
 UploadAlertSync.Feed feed(List<UploadFeed.Entry> e){return new UploadAlertSync.Feed(e,"tag","");}
 @Test public void automaticIsOptIn(){assertFalse(store.automatic());assertTrue(store.unmetered());assertEquals(3,store.hours());}
 @Test public void corruptPrefsFailClosed(){RuntimeEnvironment.getApplication().getSharedPreferences("upload_alerts_v1",0).edit().putString("automatic","bad").putString("channels","bad").commit();assertFalse(store.automatic());assertTrue(store.channels().isEmpty());}
 @Test public void invalidIdsNeverBecomeSelections(){assertFalse(store.select("https://example.invalid","x",true));}
 @Test public void baselineDoesNotNotify(){AtomicInteger calls=new AtomicInteger();UploadAlertSync.run(store,(c,s)->feed(entries()),(c,e)->{calls.incrementAndGet();return true;},()->true);assertEquals(0,calls.get());assertTrue(store.state(store.channels().get(0)).initialized);}
 @Test public void rateLimitStopsBatch(){store.select("UCABCDEFGHIJKLMNOPQRSTUV","Other",true);AtomicInteger calls=new AtomicInteger();UploadAlertSync.run(store,(c,s)->{calls.incrementAndGet();throw new UploadAlertSync.RateLimited();},(c,e)->true,()->true);assertEquals(1,calls.get());}
 @Test public void failureDoesNotAdvanceBaseline(){UploadAlertSync.run(store,(c,s)->{throw new IOException();},(c,e)->true,()->true);assertFalse(store.state(store.channels().get(0)).initialized);}
 @Test public void cancellationSkipsFetch(){AtomicInteger calls=new AtomicInteger();UploadAlertSync.run(store,(c,s)->{calls.incrementAndGet();return feed(entries());},(c,e)->true,()->false);assertEquals(0,calls.get());}
 @Test public void staleGenerationCannotSave(){UploadAlertStore.Channel old=store.channels().get(0);store.select(C,"",false);store.select(C,"Again",true);assertFalse(store.save(old,new UploadAlertStore.State()));}
 @Test public void deselectionDuringFetchDiscardsResult(){UploadAlertSync.run(store,(c,s)->{store.select(C,"",false);store.select(C,"Again",true);return feed(entries());},(c,e)->{fail("stale notification");return false;},()->true);assertFalse(store.state(store.channels().get(0)).initialized);}
 @Test public void notificationFailureCanRetry(){UploadAlertSync.run(store,(c,s)->feed(entries()),(c,e)->true,()->true);UploadAlertSync.run(store,(c,s)->feed(Arrays.asList(e("ABCDEFGHIJK",2000))),(c,e)->false,()->true);assertFalse(store.state(store.channels().get(0)).seen.contains("ABCDEFGHIJK"));}
 @Test public void headerInjectionRejected(){assertEquals("",UploadAlertWorker.validator("tag\r\nCookie: secret"));}
 String atom(String body){return "<feed xmlns='http://www.w3.org/2005/Atom' xmlns:yt='http://www.youtube.com/xml/schemas/2015'>"+body+"</feed>";}
 List<UploadFeed.Entry> parse(String s)throws IOException{return UploadFeed.parse(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)),C,System.currentTimeMillis());}
 @Test public void validFeedParses()throws Exception{assertEquals(1,parse(atom("<entry><yt:videoId>"+V+"</yt:videoId><yt:channelId>"+C+"</yt:channelId><title>Title</title><published>2026-01-01T00:00:00Z</published></entry>")).size());}
 @Test public void dtdRejected()throws Exception{try{parse("<!DOCTYPE feed [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+atom(""));fail();}catch(IOException expected){}}
 @Test public void truncatedFeedRejected()throws Exception{try{parse(atom("").replace("</feed>",""));fail();}catch(IOException expected){}}
 @Test public void oversizedFeedRejected()throws Exception{try{UploadFeed.parse(new ByteArrayInputStream(new byte[UploadFeed.MAX_BYTES+1]),C,0);fail();}catch(IOException expected){}}
}
