package com.newtube.mobile.alerts;

import com.newtube.mobile.alerts.UploadAlertStore;
import com.newtube.mobile.alerts.UploadFeed;
import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Reconstructed from private R3 APK; reviewed against its bytecode. */
final class UploadAlertSync {

    interface Notifier {
        boolean show(UploadAlertStore.Channel channel, List<UploadFeed.Entry> list);
    }

    interface Source {
        Feed fetch(UploadAlertStore.Channel channel, UploadAlertStore.State state) throws IOException;
    }

    UploadAlertSync() {
    }

    static final class Feed {
        final List<UploadFeed.Entry> entries;
        final String etag;
        final String modified;

        Feed(List<UploadFeed.Entry> entries, String etag, String modified) {
            this.entries = entries;
            this.etag = etag;
            this.modified = modified;
        }
    }

    static final class RateLimited extends IOException {
        RateLimited() {
        }
    }

    static final class Outcome {
        int alerts;
        int checked;
        int failed;

        Outcome() {
        }
    }

    static Outcome run(UploadAlertStore store, Source source, Notifier notify, BooleanSupplier active) {
        Outcome result = new Outcome();
        List<UploadAlertStore.Channel> channels = store.channels();
        for (UploadAlertStore.Channel channel : channels) {
            if (!active.getAsBoolean()) {
                break;
            }
            if (store.current(channel)) {
                UploadAlertStore.State state = store.state(channel);
                try {
                    Feed feed = source.fetch(channel, state);
                    if (!active.getAsBoolean()) {
                        break;
                    }
                    synchronized (UploadAlertStore.LOCK) {
                        if (active.getAsBoolean() && store.current(channel)) {
                            if (feed.entries == null && state.initialized) {
                                result.checked++;
                            } else {
                                if (feed.entries == null) {
                                    throw new IOException("Uninitialized conditional response");
                                }
                                List<UploadFeed.Entry> updates = UploadAlertStore.updates(state, feed.entries);
                                state.etag = feed.etag;
                                state.modified = feed.modified;
                                if (updates.isEmpty() || notify.show(channel, updates)) {
                                    if (store.save(channel, state)) {
                                        result.checked++;
                                        result.alerts += updates.size();
                                    }
                                }
                            }
                        }
                    }
                } catch (RateLimited e) {
                    break;
                } catch (IOException e2) {
                }
            }
        }
        result.failed = Math.max(0, store.channels().size() - result.checked);
        return result;
    }
}
