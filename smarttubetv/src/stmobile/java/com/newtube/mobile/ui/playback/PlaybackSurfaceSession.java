package com.newtube.mobile.ui.playback;

import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.Surface;
import android.view.TextureView;

/* loaded from: classes20.dex */
public final class PlaybackSurfaceSession {
    private Surface surface;
    private SurfaceTexture texture;

    interface Output {
        void setSurface(Surface surface);
    }

    public static final class Lease {
        private SurfaceTexture borrowed;

        public boolean shouldRelease(SurfaceTexture destroyed) {
            return destroyed != this.borrowed;
        }
    }

    SurfaceTexture texture() {
        return this.texture;
    }

    Surface surface() {
        if (this.texture == null || isReleased(this.texture) || this.surface == null || !this.surface.isValid()) {
            return null;
        }
        return this.surface;
    }

    boolean attach(TextureView target, SurfaceTexture created, Lease lease, Output output) {
        if (created == null) {
            return false;
        }
        if (surface() != null) {
            lease.borrowed = this.texture;
            if (created == this.texture) {
                return true;
            }
            try {
                target.setSurfaceTexture(this.texture);
                return true;
            } catch (IllegalArgumentException e) {
                lease.borrowed = null;
            }
        }
        if (isReleased(created)) {
            return false;
        }
        SurfaceTexture previousTexture = this.texture;
        Surface previousSurface = this.surface;
        Surface replacement = new Surface(created);
        try {
            output.setSurface(replacement);
            this.texture = created;
            this.surface = replacement;
            lease.borrowed = created;
            if (previousSurface != null) {
                previousSurface.release();
            }
            if (previousTexture != null && previousTexture != created) {
                previousTexture.release();
            }
            return true;
        } catch (RuntimeException failure) {
            replacement.release();
            throw failure;
        }
    }

    void release() {
        Surface oldSurface = this.surface;
        SurfaceTexture oldTexture = this.texture;
        this.surface = null;
        this.texture = null;
        if (oldSurface != null) {
            oldSurface.release();
        }
        if (oldTexture != null) {
            oldTexture.release();
        }
    }

    private static boolean isReleased(SurfaceTexture value) {
        return Build.VERSION.SDK_INT >= 26 && value.isReleased();
    }
}
