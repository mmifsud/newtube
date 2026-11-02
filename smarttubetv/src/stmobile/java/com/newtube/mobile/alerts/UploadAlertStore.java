package com.newtube.mobile.alerts;

import android.content.Context;
import android.content.SharedPreferences;


import com.newtube.mobile.alerts.UploadAlertStore;
import com.newtube.mobile.alerts.UploadFeed;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Reconstructed from private R3 APK; reviewed against its bytecode. */
public final class UploadAlertStore {
    static final Object LOCK = new Object();
    public static final int MAX_CHANNELS = 50;
    private final SharedPreferences prefs;

    public UploadAlertStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences("upload_alerts_v1", 0);
    }

    public static final class Channel {
        public final String id;
        public final String name;
        public final String token;

        Channel(String id, String name, String token) {
            this.id = id;
            this.name = name;
            this.token = token;
        }
    }

    static final class State {
        boolean initialized;
        long watermark;
        String etag = "";
        String modified = "";
        final Set<String> seen = new LinkedHashSet();

        State() {
        }
    }

    private String string(String key, String fallback) {
        try {
            String value = this.prefs.getString(key, fallback);
            return value != null ? value : fallback;
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    private boolean flag(String key, boolean fallback) {
        try {
            return this.prefs.getBoolean(key, fallback);
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    private int number(String key, int fallback) {
        try {
            return this.prefs.getInt(key, fallback);
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    public boolean automatic() {
        return flag("automatic", false);
    }

    public boolean unmetered() {
        return flag("unmetered", true);
    }

    public int hours() {
        int value = number("hours", 3);
        if (value == 1 || value == 3 || value == 6 || value == 12) {
            return value;
        }
        return 3;
    }

    public void automatic(boolean value) {
        this.prefs.edit().putBoolean("automatic", value).apply();
    }

    public void unmetered(boolean value) {
        this.prefs.edit().putBoolean("unmetered", value).apply();
    }

    public void hours(int value) {
        this.prefs.edit().putInt("hours", value).apply();
    }

    public List<Channel> channels() {
        List<Channel> list;
        synchronized (LOCK) {
            list = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(string("channels", "[]"));
                Set<String> ids = new LinkedHashSet<>();
                for (int i = 0; i < Math.min(a.length(), 50); i++) {
                    JSONObject o = a.getJSONObject(i);
                    String id = o.getString("id");
                    String name = o.getString("name");
                    String token = o.getString("token");
                    if (UploadFeed.channelId(id) && name.length() <= 256 && !token.isEmpty() && token.length() <= 64 && ids.add(id)) {
                        list.add(new Channel(id, name, token));
                    }
                }
            } catch (JSONException e) {
            }
        }
        return list;
    }

    public boolean selected(final String id) {
        return channels().stream().anyMatch(new Predicate() {
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return ((UploadAlertStore.Channel) obj).id.equals(id);
            }
        });
    }

    public boolean select(final String id, String name, boolean on) {
        if (!UploadFeed.channelId(id)) {
            return false;
        }
        synchronized (LOCK) {
            List<Channel> list = channels();
            if (on && selected(id)) {
                return true;
            }
            if (on && list.size() >= 50) {
                return false;
            }
            list.removeIf(new Predicate() {
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    return ((UploadAlertStore.Channel) obj).id.equals(id);
                }
            });
            if (on) {
                list.add(new Channel(id, name == null ? id : name.substring(0, Math.min(name.length(), 256)), UUID.randomUUID().toString()));
            }
            JSONArray a = new JSONArray();
            try {
                for (Channel c : list) {
                    a.put(new JSONObject().put("id", c.id).put("name", c.name).put("token", c.token));
                }
                this.prefs.edit().putString("channels", a.toString()).remove("state_" + id).apply();
                return true;
            } catch (JSONException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    static /* synthetic */ boolean sameSelection(Channel channel, Channel c) {
        return c.id.equals(channel.id) && c.token.equals(channel.token);
    }

    boolean current(final Channel channel) {
        return channels().stream().anyMatch(new Predicate() {
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return UploadAlertStore.sameSelection(channel, (UploadAlertStore.Channel) obj);
            }
        });
    }

    State state(Channel channel) {
        synchronized (LOCK) {
            State s = new State();
            try {
                JSONObject o = new JSONObject(string("state_" + channel.id, "{}"));
                if (!channel.token.equals(o.optString("token"))) {
                    return s;
                }
                s.initialized = o.getBoolean("initialized");
                s.watermark = o.getLong("watermark");
                if (s.watermark >= 0 && s.watermark <= System.currentTimeMillis() + 86400000) {
                    s.etag = o.optString("etag");
                    s.modified = o.optString("modified");
                    JSONArray a = o.getJSONArray("seen");
                    for (int i = Math.max(0, a.length() - 128); i < a.length(); i++) {
                        String id = a.getString(i);
                        if (UploadFeed.identifier(id, 11)) {
                            s.seen.add(id);
                        }
                    }
                    return s;
                }
                return new State();
            } catch (JSONException e) {
                return new State();
            }
        }
    }

    boolean save(Channel c, State s) {
        synchronized (LOCK) {
            if (!current(c)) {
                return false;
            }
            try {
                JSONObject o = new JSONObject().put("token", c.token).put("initialized", s.initialized).put("watermark", s.watermark).put("etag", s.etag).put("modified", s.modified).put("seen", new JSONArray((Collection) s.seen));
                return this.prefs.edit().putString("state_" + c.id, o.toString()).commit();
            } catch (JSONException e) {
                return false;
            }
        }
    }

    void completed(int checked, int failed, int alerts) {
        this.prefs.edit().putLong("last_time", System.currentTimeMillis()).putInt("checked", checked).putInt("failed", failed).putInt("alerts", alerts).apply();
    }

    public long lastTime() {
        try {
            return this.prefs.getLong("last_time", 0L);
        } catch (ClassCastException e) {
            return 0L;
        }
    }

    public int checked() {
        return number("checked", 0);
    }

    public int failed() {
        return number("failed", 0);
    }

    public int alerts() {
        return number("alerts", 0);
    }

    static List<UploadFeed.Entry> updates(State state, List<UploadFeed.Entry> entries) {
        List<UploadFeed.Entry> result = new ArrayList<>();
        long before = state.watermark;
        for (UploadFeed.Entry entry : entries) {
            if (state.initialized && entry.published >= before && !state.seen.contains(entry.id)) {
                result.add(entry);
            }
            state.seen.add(entry.id);
            state.watermark = Math.max(state.watermark, entry.published);
        }
        while (state.seen.size() > 128) {
            state.seen.remove(state.seen.iterator().next());
        }
        state.initialized = true;
        result.sort(new Comparator() {
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                return Long.compare(((UploadFeed.Entry) obj2).published, ((UploadFeed.Entry) obj).published);
            }
        });
        return result;
    }
}
