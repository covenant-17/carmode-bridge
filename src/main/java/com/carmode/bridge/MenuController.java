package com.carmode.bridge;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;
import com.carmode.bridge.core.MenuSession;

public final class MenuController {
    private final Context context;
    private final CarModeApp app;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MenuSession session = new MenuSession();
    private final Actions actions;
    private MenuView view;
    private String source;
    public MenuController(Context context) {
        this.context = context; app = CarModeApp.from(context);
        windows = context.getSystemService(WindowManager.class); actions = new Actions(context);
    }
    public boolean isOpen() { return session.isOpen(); }
    public boolean press(String newSource) {
        expire();
        if (!Settings.canDrawOverlays(context)) {
            cancel("overlay_permission_missing");
            app.log().record("menu_error", "reason", "overlay_permission_missing");
            Toast.makeText(context, I18n.text(context, R.string.need_overlay), Toast.LENGTH_LONG).show();
            return false;
        }
        if (view == null) {
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int width = Math.min((int)(700 * metrics.density), (int)(metrics.widthPixels * .9f));
            width = Math.min(width, (int)(metrics.heightPixels * .85f * 700 / 290));
            MenuView candidate = new MenuView(context);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, width * 290 / 700,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.CENTER;
            params.setTitle("CarMode");
            try { windows.addView(candidate, params); }
            catch (RuntimeException e) {
                app.log().record("menu_error", "error", e.getClass().getSimpleName());
                Toast.makeText(context, I18n.text(context, R.string.menu_failed), Toast.LENGTH_LONG).show();
                return false;
            }
            view = candidate; source = newSource;
            actions.menu(true, source, "mode");
        }
        session.press(SystemClock.elapsedRealtime());
        app.log().record("menu_selection", "index", session.selected(), "source", source, "timeout_ms", MenuSession.TIMEOUT_MS);
        handler.removeCallbacks(tick); tick.run();
        return true;
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!session.isOpen()) return;
            if (!Settings.canDrawOverlays(context)) { cancel("overlay_permission_revoked"); return; }
            if (expire()) return;
            if (view != null) view.update(session.selected(), session.remaining(SystemClock.elapsedRealtime()));
            handler.postDelayed(this, 16);
        }
    };
    private boolean expire() {
        int action = session.poll(SystemClock.elapsedRealtime());
        if (action < 0) return false;
        String origin = source;
        close("timeout");
        app.log().record("action_selected", "index", action, "source", origin);
        actions.select(action, origin);
        return true;
    }
    public void cancel(String reason) { session.cancel(); close(reason); }
    private void close(String reason) {
        handler.removeCallbacks(tick);
        if (view == null) return;
        try { windows.removeViewImmediate(view); }
        catch (RuntimeException e) { app.log().record("menu_remove_error", "error", e.getClass().getSimpleName()); }
        view = null;
        actions.menu(false, source, reason);
    }
}
