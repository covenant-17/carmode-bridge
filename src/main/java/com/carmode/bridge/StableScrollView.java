package com.carmode.bridge;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

/** Keeps background text updates from moving the viewport or interrupting a fling. */
final class StableScrollView extends ScrollView {
    private boolean touching, preserving;
    private long lastMovement;
    private int savedX, savedY;
    private final Runnable finishUpdate = () -> preserving = false;

    StableScrollView(Context context) { super(context); }

    boolean isScrolling() {
        return touching || SystemClock.uptimeMillis() - lastMovement < 300;
    }

    void updateInPlace(Runnable update) {
        if (!preserving) { savedX = getScrollX(); savedY = getScrollY(); }
        preserving = true;
        removeCallbacks(finishUpdate);
        update.run();
        if (!isLayoutRequested()) preserving = false;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touching = true;
            preserving = false; // A new gesture always takes priority over a pending update.
            removeCallbacks(finishUpdate);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            touching = false;
            lastMovement = SystemClock.uptimeMillis();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);
        if (!preserving) lastMovement = SystemClock.uptimeMillis();
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (preserving) {
            scrollTo(savedX, savedY); // ScrollView clamps if the content became shorter.
            // Keep the guard through this frame's TextView pre-draw / cursor reveal requests.
            post(finishUpdate);
        }
    }

    @Override public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        return !preserving && super.requestChildRectangleOnScreen(child, rectangle, immediate);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(finishUpdate);
        super.onDetachedFromWindow();
    }
}
