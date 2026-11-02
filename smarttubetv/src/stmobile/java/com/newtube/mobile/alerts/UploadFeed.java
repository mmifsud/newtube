package com.newtube.mobile.alerts;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.text.*;
import java.util.*;

/** Bounded public Atom input. Does not follow links or resolve images or media. */
public final class UploadFeed {
    static final int MAX_BYTES = 512 * 1024;
    static final String ATOM = "http://www.w3.org/2005/Atom";
    static final String YT = "http://www.youtube.com/xml/schemas/2015";
    public static final class Entry {
        public final String id, title;
        public final long published;
        Entry(String id, String title, long published) { this.id=id; this.title=title; this.published=published; }
    }
    public static boolean channelId(String id) { return id != null && id.startsWith("UC") && identifier(id,24); }
    static boolean identifier(String id,int size) {
        if(id==null || id.length()!=size) return false;
        for(char c:id.toCharArray()) if(!(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||c=='_'||c=='-'))return false;
        return true;
    }
    static List<Entry> parse(InputStream stream,String channel,long now) throws IOException {
        try {
            ByteArrayOutputStream data=new ByteArrayOutputStream(); byte[] buffer=new byte[4096];int count;
            while((count=stream.read(buffer))!=-1){if(data.size()+count>MAX_BYTES)throw new IOException("Feed too large");data.write(buffer,0,count);}
            XmlPullParser p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
            p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);p.setInput(new ByteArrayInputStream(data.toByteArray()),null);
            List<Entry> entries=new ArrayList<>();boolean root=false,closed=false;int depth=-1;
            String id=null,title=null,owner=null,published=null;
            for(int event=p.nextToken();event!=XmlPullParser.END_DOCUMENT;event=p.nextToken()){
                if(event==XmlPullParser.DOCDECL)throw new IOException("DTD not allowed");
                if(event==XmlPullParser.START_TAG){
                    if(p.getDepth()==1){if(root || !"feed".equals(p.getName())||!ATOM.equals(p.getNamespace()))throw new IOException("Not an Atom feed");root=true;}
                    else if("entry".equals(p.getName())&&ATOM.equals(p.getNamespace())){
                        if(depth!=-1||entries.size()>=64)throw new IOException("Invalid entries");
                        depth=p.getDepth();id=title=owner=published=null;
                    } else if(depth>0&&p.getDepth()==depth+1){
                        String name=p.getName(),ns=p.getNamespace();
                        if(YT.equals(ns)&&"videoId".equals(name))id=p.nextText();
                        else if(YT.equals(ns)&&"channelId".equals(name))owner=p.nextText();
                        else if(ATOM.equals(ns)&&"title".equals(name))title=p.nextText();
                        else if(ATOM.equals(ns)&&"published".equals(name))published=p.nextText();
                    }
                } else if(event==XmlPullParser.END_TAG&&p.getDepth()==1&&"feed".equals(p.getName())&&ATOM.equals(p.getNamespace()))closed=true;
                else if(event==XmlPullParser.END_TAG&&p.getDepth()==depth){
                    long time=timestamp(published);
                    if(!identifier(id,11)||!channel.equals(owner)||title==null||title.length()>512||time<=0||time>now+86_400_000L)throw new IOException("Invalid feed entry");
                    entries.add(new Entry(id,title,time));depth=-1;
                }
            }
            if(!root||!closed||depth!=-1)throw new IOException("Incomplete feed");
            return entries;
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid feed",e);}
    }
    private static long timestamp(String text){
        if(text==null||text.length()>40)return -1;
        for(String format:new String[]{"yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mm:ss.SSSXXX"}){
            SimpleDateFormat parser=new SimpleDateFormat(format,Locale.US);parser.setLenient(false);
            ParsePosition pos=new ParsePosition(0);Date date=parser.parse(text,pos);if(date!=null&&pos.getIndex()==text.length())return date.getTime();
        }return -1;
    }
}
