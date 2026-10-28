package com.carmode.bridge;

import android.content.*;
import android.graphics.PixelFormat;
import android.view.*;
import android.widget.*;

/** One bounded, non-focusable overlay. No full-screen touch surface and no global Back listener. */
final class DockController {
    private final Context context;
    private final WindowManager windows;
    private View view;
    private boolean collapsed;
    DockController(Context context) { this.context = context; windows = context.getSystemService(WindowManager.class); }
    void show() { if (view == null) refresh(); }
    void refresh() {
        close();
        Context localized = I18n.wrap(context);
        Prefs prefs = new Prefs(context);
        LinearLayout panel = new LinearLayout(localized) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    CamryHome.cancelStartup(); CarModeApp.from(context).backSequence.reset();
                }
                return super.dispatchTouchEvent(event);
            }
        };
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if (collapsed) {
            CamryUi.button(panel, R.string.camry_expand_dock, () -> { collapsed = false; refresh(); });
        } else {
            CamryUi.button(panel, R.string.camera, () -> { DockService.hide(context); new Actions(context).select(0, "DOCK"); });
            CamryUi.button(panel, R.string.camry_music, () -> new Actions(context).select(1, "DOCK"));
            CamryUi.button(panel, R.string.camry_home, () -> open(false, false));
            CamryUi.button(panel, R.string.camry_apps, () -> open(true, false));
            CamryUi.button(panel, R.string.camry_restore_short, () -> open(false, true));
            CamryUi.button(panel, R.string.camry_collapse_dock, () -> { collapsed = true; refresh(); });
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        String corner = prefs.dockPosition();
        boolean top = corner.startsWith("top_"), left = corner.endsWith("_left");
        params.gravity = collapsed ? (top ? Gravity.TOP : Gravity.BOTTOM) | (left ? Gravity.LEFT : Gravity.RIGHT)
                : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        // No LAYOUT_IN_SCREEN/NO_LIMITS: WindowManager fits the overlay to the system's usable area.
        params.x = CamryUi.dp(context, 8);
        // Keep bottom controls above the head unit's physical bezel/overscan area.
        params.y = CamryUi.dp(context, collapsed && top ? 8 : 32);
        params.alpha = prefs.dockOpacity() / 100f; params.setTitle("CarMode Dock");
        android.graphics.Point available = new android.graphics.Point(); windows.getDefaultDisplay().getSize(available);
        int margin = CamryUi.dp(context, 16);
        panel.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        HorizontalScrollView bounded = new HorizontalScrollView(localized); bounded.addView(panel);
        params.width = Math.max(1, Math.min(panel.getMeasuredWidth(), available.x - margin));
        params.height = Math.max(1, Math.min(panel.getMeasuredHeight(), available.y - margin));
        try {
            windows.addView(bounded, params); view = bounded;
            CarModeApp.from(context).log().record("camry_dock_shown", "collapsed", collapsed,
                    "position", collapsed ? corner : "bottom");
        } catch (RuntimeException error) {
            CamryControl.error(context, R.string.menu_failed); DockService.hide(context);
        }
    }
    private void open(boolean apps, boolean restore) {
        try {
            if (apps) { CamryAppsActivity.open(context); return; }
            Intent intent = new Intent(context, CamryPreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("apps", apps).putExtra("restore", restore);
            context.startActivity(intent);
        } catch (RuntimeException error) { CamryControl.error(context, R.string.camry_launch_failed); }
    }
    void close() {
        if (view == null) return;
        try { windows.removeViewImmediate(view); }
        catch (RuntimeException error) {
            CarModeApp app = CarModeApp.from(context);
            if (!app.recovering()) app.log().record("camry_dock_remove_error", "error", error.toString());
        }
        view = null;
    }
}
