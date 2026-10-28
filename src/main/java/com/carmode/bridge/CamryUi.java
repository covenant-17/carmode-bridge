package com.carmode.bridge;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

/** Uses the existing MODE palette; native widgets keep focus and accessibility semantics. */
final class CamryUi {
    static final int BACKGROUND = Color.rgb(15, 23, 35), CARD = Color.rgb(28, 39, 55), ACCENT = Color.rgb(73, 214, 207);
    private CamryUi() {}
    static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static LinearLayout page(Context c) {
        LinearLayout page = new LinearLayout(c); page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BACKGROUND); int p = dp(c, 16); page.setPadding(p, p, p, p);
        page.setFitsSystemWindows(true); return page;
    }
    static TextView text(LinearLayout parent, String value, int size) {
        TextView label = new TextView(parent.getContext()); label.setText(value); label.setTextSize(size);
        label.setTextColor(Color.WHITE); label.setPadding(8, 8, 8, 8); parent.addView(label); return label;
    }
    static Button button(LinearLayout parent, int resource, Runnable action) {
        boolean illustrated = resource == R.string.camera || resource == R.string.camry_home || resource == R.string.camry_apps
                || resource == R.string.camry_android_auto || resource == R.string.camry_music || resource == R.string.camry_settings
                || resource == R.string.camry_restore_short || resource == R.string.camry_collapse_dock;
        return button(parent, I18n.text(parent.getContext(), resource), action,
                illustrated ? new Icon(parent.getContext(), resource) : null);
    }
    static Button button(LinearLayout parent, String title, Runnable action) {
        return button(parent, title, action, null);
    }
    static ImageButton iconButton(LinearLayout parent, int resource, Runnable action) {
        ImageButton button = new ImageButton(parent.getContext());
        String title = I18n.text(parent.getContext(), resource);
        button.setContentDescription(title); button.setTooltipText(title);
        Icon icon = new Icon(parent.getContext(), resource);
        button.setImageDrawable(icon); button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(0, dp(parent.getContext(), 13), 0, dp(parent.getContext(), 13));
        button.setMinimumWidth(0); button.setMinimumHeight(0);
        GradientDrawable background = panel(parent.getContext(), false); background.setColor(CARD);
        button.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x4649D6CF), background, null));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(parent.getContext(), 52), dp(parent.getContext(), 52));
        size.setMargins(0, 0, 0, dp(parent.getContext(), 8));
        parent.addView(button, size); button.setOnClickListener(v -> action.run()); return button;
    }
    private static Button button(LinearLayout parent, String title, Runnable action, Icon icon) {
        Context c = parent.getContext(); Button button = icon == null ? new Button(c) : new IconButton(c, icon);
        button.setText(title); button.setGravity(android.view.Gravity.CENTER);
        button.setAllCaps(false); button.setTextSize(18); button.setTextColor(Color.WHITE);
        button.setMinHeight(dp(c, 52));
        GradientDrawable background = panel(c, false); background.setColor(CARD);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Color.argb(70, 73, 214, 207)), background, null));
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spacing.setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4)); button.setLayoutParams(spacing);
        button.setOnClickListener(v -> action.run()); parent.addView(button); return button;
    }
    static void navigation(android.app.Activity activity) {
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        activity.getWindow().setNavigationBarColor(BACKGROUND);
        activity.getWindow().setStatusBarColor(BACKGROUND);
    }
    static GradientDrawable panel(Context c) {
        return panel(c, true);
    }
    static GradientDrawable panel(Context c, boolean outlined) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(BACKGROUND);
        shape.setCornerRadius(0);
        if (outlined) shape.setStroke(dp(c, 1), ACCENT);
        return shape;
    }
    /** Reserve icon space in measurement, then draw it beside the centred text block. */
    private static final class IconButton extends Button {
        private Icon icon;
        private int gap;
        IconButton(Context c, Icon icon) {
            super(c); this.icon = icon; gap = dp(c, 10);
            setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        }
        @Override public void setPadding(int left, int top, int right, int bottom) {
            super.setPadding(left + (icon == null ? 0 : icon.getIntrinsicWidth() + gap), top, right, bottom);
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            android.text.Layout layout = getLayout();
            if (layout == null) return;
            float textWidth = 0;
            for (int i = 0; i < layout.getLineCount(); i++) textWidth = Math.max(textWidth, layout.getLineWidth(i));
            int x = Math.round(getCompoundPaddingLeft()
                    + (getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight() - textWidth) / 2f
                    - gap - icon.getIntrinsicWidth());
            int y = (getHeight() - icon.getIntrinsicHeight()) / 2;
            icon.setBounds(x, y, x + icon.getIntrinsicWidth(), y + icon.getIntrinsicHeight());
            icon.draw(canvas);
        }
    }
    private static final class Icon extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int kind, size;
        Icon(Context c, int kind) { this.kind = kind; size = dp(c, 26); paint.setColor(ACCENT); }
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            canvas.save(); canvas.translate(bounds.left, bounds.top); canvas.scale(bounds.width() / 26f, bounds.height() / 26f);
            paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND); paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            if (kind == R.string.camera) {
                canvas.drawRoundRect(3, 7, 23, 23, 3, 3, paint); canvas.drawCircle(13, 15, 4, paint);
                canvas.drawLine(8, 4, 17, 4, paint);
            } else if (kind == R.string.camry_music) {
                canvas.drawLine(9, 7, 9, 20, paint); canvas.drawLine(9, 7, 21, 3, paint);
                canvas.drawLine(21, 3, 21, 16, paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);
                canvas.drawOval(2, 17, 10, 23, paint); canvas.drawOval(14, 13, 22, 19, paint);
            } else if (kind == R.string.camry_apps) {
                for (int x : new int[]{3, 15}) for (int y : new int[]{3, 15}) canvas.drawRoundRect(x, y, x + 8, y + 8, 2, 2, paint);
            } else if (kind == R.string.camry_home) {
                android.graphics.Path roof = new android.graphics.Path(); roof.moveTo(2, 12); roof.lineTo(13, 3); roof.lineTo(24, 12);
                canvas.drawPath(roof, paint); canvas.drawRoundRect(6, 11, 20, 23, 2, 2, paint);
            } else if (kind == R.string.camry_android_auto) {
                android.graphics.Path arrow = new android.graphics.Path(); arrow.moveTo(13, 2); arrow.lineTo(24, 24);
                arrow.lineTo(13, 18); arrow.lineTo(2, 24); arrow.close(); canvas.drawPath(arrow, paint);
            } else if (kind == R.string.camry_settings) {
                for (int y : new int[]{5, 13, 21}) canvas.drawLine(3, y, 23, y, paint);
                canvas.drawCircle(8, 5, 3, paint); canvas.drawCircle(18, 13, 3, paint); canvas.drawCircle(10, 21, 3, paint);
            } else if (kind == R.string.camry_system_settings) {
                canvas.drawCircle(13, 13, 7, paint); canvas.drawCircle(13, 13, 3, paint);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    canvas.drawLine(13 + (float)Math.cos(a) * 8, 13 + (float)Math.sin(a) * 8,
                            13 + (float)Math.cos(a) * 11, 13 + (float)Math.sin(a) * 11, paint);
                }
            } else {
                android.graphics.Path arrow = new android.graphics.Path();
                if (kind == R.string.camry_collapse_dock) { arrow.moveTo(5, 8); arrow.lineTo(13, 16); arrow.lineTo(21, 8); }
                else { arrow.moveTo(12, 5); arrow.lineTo(4, 13); arrow.lineTo(12, 21); canvas.drawLine(4, 13, 23, 13, paint); }
                canvas.drawPath(arrow, paint);
            }
            canvas.restore();
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
