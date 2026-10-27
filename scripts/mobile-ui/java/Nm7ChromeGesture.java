package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.view.MotionEvent;

/** Only real finger movement controls chrome, never RecyclerView layout callbacks. */
public final class Nm7ChromeGesture {
    private float startX, anchorY;
    private boolean eligible;
    public int update(MotionEvent event, boolean hitFeed, float threshold) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startX = event.getRawX(); anchorY = event.getRawY(); eligible = hitFeed;
        } else if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_UP) eligible = false;
        else if (action == MotionEvent.ACTION_MOVE && eligible) {
            float dx = event.getRawX() - startX, dy = event.getRawY() - anchorY;
            if (Math.abs(dy) >= threshold && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                anchorY = event.getRawY(); startX = event.getRawX();
                return dy < 0 ? 1 : -1;
            }
        }
        return 0;
    }
}
