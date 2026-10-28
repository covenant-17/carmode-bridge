package com.carmode.bridge;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/** Persistent shortcuts grow from five cells. Drag changes assignments only, never installed packages. */
final class CamryPinnedApps {
    private final Activity activity;
    private final Prefs prefs;
    private final LinearLayout slots;
    private final ScrollView scroll;
    private final IntConsumer choose;
    private TextView trash;
    private WindowManager windows;
    private int dragging = -1;
    private String dragToken;
    private int scrollDirection;
    private final Runnable edgeScroll = new Runnable() {
        public void run() {
            if (dragging < 0 || scrollDirection == 0) return;
            int previous = scroll.getScrollY();
            scroll.scrollBy(0, scrollDirection * CamryUi.dp(activity, 8));
            if (previous != scroll.getScrollY()) scroll.postDelayed(this, 32);
            else scrollDirection = 0;
        }
    };
    CamryPinnedApps(Activity activity, LinearLayout side, IntConsumer choose) {
        this.activity = activity; this.choose = choose; prefs = CarModeApp.from(activity).prefs();
        View separator = new View(activity); separator.setBackgroundColor(0x5549D6CF);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, CamryUi.dp(activity, 1));
        line.setMargins(CamryUi.dp(activity, 8), CamryUi.dp(activity, 12), CamryUi.dp(activity, 8), CamryUi.dp(activity, 16));
        side.addView(separator, line);
        scroll = new ScrollView(activity) {
            @Override public boolean dispatchDragEvent(DragEvent event) {
                if (dragging >= 0) {
                    if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
                        int edge = CamryUi.dp(activity, 36);
                        autoScroll(event.getY() < edge ? -1 : event.getY() > getHeight() - edge ? 1 : 0);
                    } else if (event.getAction() == DragEvent.ACTION_DRAG_EXITED
                            || event.getAction() == DragEvent.ACTION_DRAG_ENDED) autoScroll(0);
                }
                return super.dispatchDragEvent(event);
            }
        };
        scroll.setVerticalScrollBarEnabled(false); scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        slots = new LinearLayout(activity); slots.setOrientation(LinearLayout.VERTICAL); scroll.addView(slots);
        side.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        refresh();
    }
    void refresh() {
        slots.removeAllViews(); String[] values = prefs.pinnedApps();
        for (int i = 0; i < values.length; i++) {
            final int slot = i; final String component = values[i];
            ImageButton cell = new ImageButton(activity); cell.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int padding = CamryUi.dp(activity, 9); cell.setPadding(padding, padding, padding, padding);
            GradientDrawable card = CamryUi.panel(activity, false); card.setColor(0xFF162D3A);
            cell.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x4649D6CF), card, null));
            String label = I18n.text(activity, R.string.camry_pin_add);
            if (component.isEmpty()) cell.setImageDrawable(new ShortcutSymbol(false));
            else {
                try {
                    android.content.ComponentName name = android.content.ComponentName.unflattenFromString(component);
                    cell.setImageDrawable(activity.getPackageManager().getActivityIcon(name));
                    label = activity.getPackageManager().getActivityInfo(name, 0).loadLabel(activity.getPackageManager()).toString();
                } catch (Exception unavailable) {
                    cell.setImageDrawable(activity.getPackageManager().getDefaultActivityIcon());
                    label = I18n.text(activity, R.string.camry_pin_unavailable);
                }
            }
            cell.setContentDescription(label);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(CamryUi.dp(activity, 52), CamryUi.dp(activity, 52));
            size.bottomMargin = CamryUi.dp(activity, 8); slots.addView(cell, size);
            cell.setOnClickListener(v -> {
                if (component.isEmpty()) { choose.accept(slot); return; }
                try {
                    Intent target = AppResolver.resolve(activity, component);
                    boolean cameraWindow = CamryNavigation.cameraWindow(activity, target);
                    if (CamryNavigation.isZlinkTarget(activity, target) || cameraWindow) CamryNavigation.launch(activity, target, null);
                    else activity.startActivity(target);
                    if ("ivicar.cn.ivicaravm".equals(target.getComponent().getPackageName()) && !cameraWindow) DockService.transition(activity);
                } catch (Exception unavailable) { CamryControl.error(activity, R.string.camry_pin_unavailable); }
            });
            cell.setOnLongClickListener(v -> {
                if (component.isEmpty()) return false;
                // The foreign freeform task sits above Home, so the temporary trash target is an overlay.
                if (!Settings.canDrawOverlays(activity)) {
                    CamryControl.error(activity, R.string.camry_pin_drag_permission); return true;
                }
                dragging = slot;
                dragToken = java.util.UUID.randomUUID().toString();
                if (!showTrash()) { dragging = -1; return true; }
                ClipData clip = new ClipData(dragToken, new String[]{"application/vnd.carmode.shortcut"}, new ClipData.Item(component));
                // Register the overlay's input window before starting the global drag.
                trash.postDelayed(() -> {
                    if (dragging == slot && trash != null && !v.startDragAndDrop(clip,
                            new View.DragShadowBuilder(v), this, View.DRAG_FLAG_GLOBAL)) close();
                }, 80);
                return true;
            });
            cell.setOnDragListener((v, event) -> {
                if (!ownDrag(event)) return false;
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED: return true;
                    case DragEvent.ACTION_DRAG_ENTERED: v.setAlpha(0.5f); return true;
                    case DragEvent.ACTION_DRAG_EXITED: v.setAlpha(1); return true;
                    case DragEvent.ACTION_DROP: prefs.movePin(dragging, slot); return true;
                    case DragEvent.ACTION_DRAG_ENDED: v.setAlpha(1); finishDrag(); return true;
                    default: return true;
                }
            });
        }
    }
    private boolean showTrash() {
        trash = new TextView(activity); trash.setGravity(Gravity.CENTER); trash.setTextColor(CamryUi.ACCENT);
        android.text.SpannableString label = new android.text.SpannableString("\uFFFC  " + I18n.text(activity, R.string.camry_pin_remove));
        ShortcutSymbol bin = new ShortcutSymbol(true);
        int iconSize = CamryUi.dp(activity, 24); bin.setBounds(0, 0, iconSize, iconSize);
        label.setSpan(new android.text.style.ImageSpan(bin, android.text.style.DynamicDrawableSpan.ALIGN_CENTER),
                0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        trash.setText(label); trash.setTextSize(18);
        trash.setContentDescription(I18n.text(activity, R.string.camry_pin_remove));
        trash.setPadding(CamryUi.dp(activity, 16), CamryUi.dp(activity, 8), CamryUi.dp(activity, 16), CamryUi.dp(activity, 8));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.TRANSPARENT);
        background.setCornerRadius(0);
        background.setStroke(CamryUi.dp(activity, 2), CamryUi.ACCENT); trash.setBackground(background);
        trash.setOnDragListener((v, event) -> {
            if (!ownDrag(event)) return false;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED: return true;
                case DragEvent.ACTION_DRAG_ENTERED: background.setStroke(CamryUi.dp(activity, 3), CamryUi.ACCENT); return true;
                case DragEvent.ACTION_DRAG_EXITED: background.setStroke(CamryUi.dp(activity, 2), CamryUi.ACCENT); return true;
                case DragEvent.ACTION_DROP: prefs.pinApp(dragging, ""); return true;
                case DragEvent.ACTION_DRAG_ENDED: finishDrag(); return true;
                default: return true;
            }
        });
        windows = activity.getSystemService(WindowManager.class);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(CamryUi.dp(activity, 176), CamryUi.dp(activity, 56),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; params.y = CamryUi.dp(activity, 8);
        params.setTitle("CarMode shortcut trash");
        try { windows.addView(trash, params); return true; }
        catch (RuntimeException rejected) { trash = null; CamryControl.error(activity, R.string.camry_pin_drag_permission); return false; }
    }
    private void finishDrag() {
        if (dragging < 0) return;
        dragging = -1;
        slots.post(() -> { close(); refresh(); });
    }
    private boolean ownDrag(DragEvent event) {
        if (dragging < 0) return false;
        // Only STARTED guarantees ClipDescription. Later events belong to the accepted session.
        return event.getAction() != DragEvent.ACTION_DRAG_STARTED || (dragToken != null
                && event.getClipDescription() != null && dragToken.contentEquals(event.getClipDescription().getLabel()));
    }
    void close() {
        autoScroll(0);
        dragging = -1;
        if (trash != null) { try { windows.removeViewImmediate(trash); } catch (RuntimeException ignored) {} trash = null; }
    }
    private void autoScroll(int direction) {
        if (scrollDirection == direction) return;
        scrollDirection = direction; scroll.removeCallbacks(edgeScroll);
        if (direction != 0) scroll.post(edgeScroll);
    }
    private final class ShortcutSymbol extends android.graphics.drawable.Drawable {
        private final boolean remove;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        ShortcutSymbol(boolean remove) { this.remove = remove; paint.setColor(CamryUi.ACCENT); paint.setStrokeWidth(2); paint.setStyle(android.graphics.Paint.Style.STROKE); }
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds(); canvas.save(); canvas.translate(b.left, b.top); canvas.scale(b.width()/32f, b.height()/32f);
            if (remove) {
                canvas.drawRect(8, 10, 24, 28, paint); canvas.drawLine(5, 7, 27, 7, paint); canvas.drawRect(12, 3, 20, 7, paint);
                canvas.drawLine(13, 14, 13, 24, paint); canvas.drawLine(19, 14, 19, 24, paint);
            } else { canvas.drawLine(6, 16, 26, 16, paint); canvas.drawLine(16, 6, 16, 26, paint); }
            canvas.restore();
        }
        public int getIntrinsicWidth() { return CamryUi.dp(activity, 32); }
        public int getIntrinsicHeight() { return CamryUi.dp(activity, 32); }
        public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
