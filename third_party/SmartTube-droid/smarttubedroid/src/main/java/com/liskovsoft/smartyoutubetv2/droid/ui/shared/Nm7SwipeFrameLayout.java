package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Horizontal section gestures belong only to browse content, never to the mini-player. */
public final class Nm7SwipeFrameLayout extends FrameLayout {
    public interface Listener { void onSwipe(int direction); }
    private Listener listener;
    private float startX, startY;
    private boolean horizontal, vertical, multiple;
    private final int slop;

    public Nm7SwipeFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setListener(Listener listener) { this.listener = listener; }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX(); startY = event.getY();
                horizontal = vertical = multiple = false;
                // Prevent the inherited edge-to-exit gesture from taking this sequence.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiple = true;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (multiple || vertical) return false;
                float dx = Math.abs(event.getX() - startX);
                float dy = Math.abs(event.getY() - startY);
                if (dy > slop && dy >= dx) vertical = true;
                if (dx > slop * 2 && dx > dy * 1.5f) horizontal = true;
                return horizontal;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return false;
            default: return horizontal;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) multiple = true;
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - startX;
            float dy = event.getY() - startY;
            float threshold = Math.max(slop * 3, Math.min(getWidth() * 0.18f,
                    72 * getResources().getDisplayMetrics().density));
            if (!multiple && !vertical && Math.abs(dx) >= threshold && Math.abs(dx) > Math.abs(dy) * 1.5f
                    && listener != null) {
                int direction = dx < 0 ? 1 : -1;
                if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) direction = -direction;
                listener.onSwipe(direction);
            }
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            horizontal = false;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        return true;
    }
}
