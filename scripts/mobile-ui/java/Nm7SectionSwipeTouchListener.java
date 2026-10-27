package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Detects horizontal section swipes at RecyclerView level so child cards cannot swallow them.
 * Vertical scrolling and taps remain owned by RecyclerView/cards.
 */
public final class Nm7SectionSwipeTouchListener implements RecyclerView.OnItemTouchListener {
    public interface Listener { void onSwipe(int direction); }

    private final Listener listener;
    private final int slop;
    private final float density;
    private float downX;
    private float downY;
    private boolean horizontal;
    private boolean vertical;
    private boolean multiple;

    public Nm7SectionSwipeTouchListener(Context context, Listener listener) {
        this.listener = listener;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        density = context.getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                horizontal = false;
                vertical = false;
                multiple = false;
                if (rv.getParent() != null) {
                    rv.getParent().requestDisallowInterceptTouchEvent(true);
                }
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiple = true;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (multiple || vertical) return false;
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (dy > slop && dy >= dx) {
                    vertical = true;
                    if (rv.getParent() != null) {
                        rv.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return false;
                }
                if (dx > slop * 2f && dx > dy * 1.25f) {
                    horizontal = true;
                    rv.stopScroll();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                releaseParent(rv);
                return false;
            default:
                return horizontal;
        }
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            multiple = true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            float threshold = Math.max(slop * 3f, Math.min(rv.getWidth() * 0.16f, 64f * density));
            if (!multiple && !vertical && horizontal
                    && Math.abs(dx) >= threshold
                    && Math.abs(dx) > Math.abs(dy) * 1.25f
                    && listener != null) {
                int direction = dx < 0 ? 1 : -1;
                if (rv.getLayoutDirection() == RecyclerView.LAYOUT_DIRECTION_RTL) {
                    direction = -direction;
                }
                listener.onSwipe(direction);
            }
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            releaseParent(rv);
            horizontal = false;
        }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // RecyclerView child requests are intentionally ignored here.
    }

    private void releaseParent(RecyclerView rv) {
        if (rv.getParent() != null) {
            rv.getParent().requestDisallowInterceptTouchEvent(false);
        }
    }
}
