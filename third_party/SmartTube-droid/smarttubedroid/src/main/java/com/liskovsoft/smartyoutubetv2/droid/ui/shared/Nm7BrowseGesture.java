package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.view.MotionEvent;

/** Own the whole horizontal sequence: the child receives CANCEL, never a swipe UP. */
public final class Nm7BrowseGesture {
    private float x, y;
    private boolean eligible, vertical, multiple, claimed, cancel;
    private int direction;
    private boolean switched;
    public boolean update(MotionEvent event, boolean hitContent, int slop, float threshold) {
        int action = event.getActionMasked();
        direction = 0;
        cancel = false;
        if (action == MotionEvent.ACTION_DOWN) {
            x = event.getRawX(); y = event.getRawY();
            eligible = hitContent;
            vertical = multiple = claimed = switched = false;
            return false;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) multiple = true;
        float dx = event.getRawX() - x, dy = event.getRawY() - y;
        if (eligible && !multiple && !vertical && !claimed
                && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)) {
            if (Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) vertical = true;
            else if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                claimed = true;
                cancel = true;
            }
        }
        boolean consume = claimed;
        if ((action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)
                && claimed && !multiple && !switched && Math.abs(dx) >= threshold
                && Math.abs(dx) > Math.abs(dy) * 1.2f) {
            direction = dx < 0 ? 1 : -1;
            switched = true;
        }
        if (action == MotionEvent.ACTION_UP) {
            eligible = claimed = false;
        } else if (action == MotionEvent.ACTION_CANCEL) eligible = claimed = false;
        return consume;
    }
    public boolean cancelChild() { return cancel; }
    public int direction() { return direction; }
}
