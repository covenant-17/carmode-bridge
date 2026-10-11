package com.carmode.bridge;

import android.os.Handler;
import android.os.Looper;
import com.carmode.bridge.core.MusicHttpRequest;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** App-owned delayed request, not tied to settings Activity lifetime. */
final class MusicHttpAction {
    private final CarModeApp app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private Runnable pending;
    MusicHttpAction(CarModeApp app) { this.app = app; }
    void cancel(String reason) {
        if (pending == null) return;
        handler.removeCallbacks(pending); pending = null;
        app.log().record("music_http_cancelled", "reason", reason);
    }
    void afterMusicLaunch() {
        cancel("new_music_selection");
        if (!app.prefs().musicHttpEnabled()) return;
        final String configured = app.prefs().musicHttpUrl();
        final URL url;
        try { url = MusicHttpRequest.validate(configured); }
        catch (IllegalArgumentException e) { app.log().record("music_http_error", "error", "invalid_url"); return; }
        final String id = UUID.randomUUID().toString();
        app.log().record("music_http_scheduled", "request_id", id, "host", url.getHost(), "delay_ms", MusicHttpRequest.DELAY_MS);
        pending = () -> {
            pending = null;
            if (!allowed(configured)) { app.log().record("music_http_cancelled", "request_id", id, "reason", "settings_or_runtime_changed"); return; }
            if (!busy.compareAndSet(false, true)) {
                app.log().record("music_http_skipped", "request_id", id, "reason", "request_in_flight"); return;
            }
            worker.execute(() -> {
                try {
                    if (!allowed(configured)) { app.log().record("music_http_cancelled", "request_id", id, "reason", "disabled_before_send"); return; }
                    app.log().record("music_http_started", "request_id", id, "host", url.getHost(), "method", "GET");
                    int code = MusicHttpRequest.send(url);
                    app.log().record("music_http_result", "request_id", id, "status_code", code, "http_success", code >= 200 && code < 300,
                            "playback_confirmed", false);
                } catch (Exception e) { app.log().record("music_http_error", "request_id", id, "error", e.getClass().getSimpleName()); }
                finally { busy.set(false); }
            });
        };
        handler.postDelayed(pending, MusicHttpRequest.DELAY_MS);
    }
    private boolean allowed(String url) {
        return app.ready() && app.prefs().musicHttpEnabled() && url.equals(app.prefs().musicHttpUrl());
    }
}
