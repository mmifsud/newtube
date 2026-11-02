package com.newtube.mobile.ui.playback;

import android.os.BadParcelableException;
import android.os.Bundle;


import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/* loaded from: classes20.dex */
final class PlaybackRestoreState {
    static final String KEY = "mobile.playback.restore.v1";
    private static final long MAX_POSITION_MS = 604800000;
    final boolean playWhenReady;
    final long positionMs;
    final String videoId;

    private PlaybackRestoreState(String id, long position, boolean play) {
        this.videoId = id;
        this.positionMs = position;
        this.playWhenReady = play;
    }

    static void save(Bundle target, Video video, long position, boolean play, String accountScope, boolean privateSession, boolean externalSession) {
        target.remove(KEY);
        if (video == null || !validId(video.videoId) || video.isLocal() || video.isRemote || video.isLive || video.isUpcoming || video.incognito || privateSession || externalSession || accountScope == null || position < 0 || position > MAX_POSITION_MS) {
            return;
        }
        Bundle state = new Bundle();
        state.putInt("version", 1);
        state.putString("video", video.videoId);
        state.putLong("position", position);
        state.putBoolean("play", play);
        state.putString("scope", accountScope);
        target.putBundle(KEY, state);
    }

    static PlaybackRestoreState read(Bundle source, String accountScope) {
        if (source == null || accountScope == null) {
            return null;
        }
        try {
            Bundle state = source.getBundle(KEY);
            if (state != null) {
                Integer num = 1;
                if (num.equals(state.get("version")) && accountScope.equals(state.get("scope"))) {
                    Object id = state.get("video");
                    Object position = state.get("position");
                    Object play = state.get("play");
                    if ((id instanceof String) && validId((String) id) && (position instanceof Long) && (play instanceof Boolean)) {
                        long time = ((Long) position).longValue();
                        if (time >= 0 && time <= MAX_POSITION_MS) {
                            return new PlaybackRestoreState((String) id, time, ((Boolean) play).booleanValue());
                        }
                        return null;
                    }
                    return null;
                }
            }
            return null;
        } catch (BadParcelableException | ClassCastException e) {
            return null;
        }
    }

    Video video() {
        Video video = Video.from(this.videoId);
        video.pendingPosMs = Math.max(1L, this.positionMs);
        video.fromCrashRestorer = true;
        return video;
    }

    static String accountScope(String name, String email, String selection) {
        if ((email == null || email.isEmpty()) && (selection == null || selection.isEmpty())) {
            return null;
        }
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            String[] strArr = {name, email, selection};
            for (int i = 0; i < 3; i++) {
                String part = strArr[i];
                if (part != null) {
                    hash.update(part.getBytes(StandardCharsets.UTF_8));
                }
                hash.update((byte) 0);
            }
            StringBuilder result = new StringBuilder("account:");
            for (byte value : hash.digest()) {
                result.append(Character.forDigit((value & 255) >>> 4, 16));
                result.append(Character.forDigit(value & 15, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static boolean validId(String id) {
        if (id == null || id.length() != 11) {
            return false;
        }
        for (char c : id.toCharArray()) {
            if ((c < 'a' || c > 'z') && ((c < 'A' || c > 'Z') && ((c < '0' || c > '9') && c != '_' && c != '-'))) {
                return false;
            }
        }
        return true;
    }
}
