package com.carmode.bridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Three square cards, deliberately no touch selection or navigation controls. */
public final class MenuView extends View {
    private final String[] cardLabels = new String[Actions.LABELS.length];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int selected;
    private float remaining = 1;
    private final int accent = Color.rgb(73, 214, 207);
    public MenuView(Context context) {
        super(context);
        Context localized = I18n.wrap(context);
        setContentDescription(localized.getString(R.string.menu_description));
        for (int i = 0; i < cardLabels.length; i++) cardLabels[i] = localized.getString(Actions.LABELS[i]);
    }
    public void update(int selected, float remaining) { this.selected = selected; this.remaining = remaining; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), unit = Math.min(w / 700f, h / 290f);
        canvas.save(); canvas.translate((w - 700 * unit) / 2, (h - 290 * unit) / 2); canvas.scale(unit, unit);
        fill(canvas, new RectF(0, 0, 700, 290), Color.rgb(15, 23, 35), 22);
        text(canvas, "CarMode", 28, 40, 24, Color.WHITE);
        fill(canvas, new RectF(28, 57, 672, 63), Color.rgb(45, 58, 75), 3);
        if (remaining > 0) fill(canvas, new RectF(28, 57, 28 + 644 * remaining, 63), accent, 3);
        for (int i = 0; i < 3; i++) {
            float x = 28 + i * 224;
            RectF card = new RectF(x, 82, x + 196, 278);
            fill(canvas, card, i == selected ? Color.rgb(26, 64, 73) : Color.rgb(28, 39, 55), 16);
            if (i == selected) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(accent);
                canvas.drawRoundRect(card, 16, 16, paint); paint.setStyle(Paint.Style.FILL);
            }
            canvas.save(); canvas.translate(x + 98, 158);
            icon(canvas, i, i == selected ? accent : Color.rgb(167, 183, 203)); canvas.restore();
            paint.setTextAlign(Paint.Align.CENTER);
            text(canvas, cardLabels[i], x + 98, 247, 18, i == selected ? Color.WHITE : Color.rgb(177, 190, 206));
            paint.setTextAlign(Paint.Align.LEFT);
        }
        canvas.restore();
    }
    private void icon(Canvas c, int index, int color) {
        paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        if (index == 0) {
            c.drawRoundRect(new RectF(-33, -20, 33, 25), 7, 7, paint);
            c.drawCircle(0, 2, 13, paint); c.drawLine(-17, -28, 9, -28, paint);
        } else if (index == 1) {
            c.drawLine(-12, 18, -12, -27, paint); c.drawLine(-12, -27, 25, -35, paint);
            c.drawLine(25, -35, 25, 9, paint); paint.setStyle(Paint.Style.FILL);
            c.drawOval(new RectF(-31, 10, -9, 26), paint); c.drawOval(new RectF(6, 1, 28, 17), paint);
        } else {
            Path path = new Path(); path.moveTo(0, -36); path.lineTo(30, 31);
            path.lineTo(0, 17); path.lineTo(-30, 31); path.close(); c.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
    private void fill(Canvas c, RectF rect, int color, float radius) {
        paint.setColor(color); paint.setStyle(Paint.Style.FILL); c.drawRoundRect(rect, radius, radius, paint);
    }
    private void text(Canvas c, String text, float x, float y, float size, int color) {
        paint.setColor(color); paint.setTextSize(size); c.drawText(text, x, y, paint);
    }
}
