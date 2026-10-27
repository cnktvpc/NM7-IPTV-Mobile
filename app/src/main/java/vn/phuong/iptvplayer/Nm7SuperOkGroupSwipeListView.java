package vn.phuong.iptvplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.ListView;

/**
 * Super OK-style horizontal swipe arbitration for NM7's native ListView.
 *
 * Like RecyclerView.OnItemTouchListener, dispatchTouchEvent sees the complete stream
 * even when AbsListView has already taken ACTION_DOWN. Once horizontal intent is clear,
 * it cancels the previous stream and owns the rest, following the finger then springing
 * back. Tap and vertical-scroll streams stay on the native ListView path.
 */
public final class Nm7SuperOkGroupSwipeListView extends ListView {
    public interface OnGroupSwipeListener {
        boolean onGroupSwipe(int direction); // +1 next group, -1 previous group
    }

    private final int touchSlop;
    private final int minimumFlingVelocity;
    private final float density;
    private OnGroupSwipeListener groupSwipeListener;
    private VelocityTracker velocityTracker;
    private long lastEventTime = Long.MIN_VALUE;
    private int lastAction = -1;
    private float lastTrackedX;
    private float lastTrackedY;
    private float downX;
    private float downY;
    private boolean draggingHorizontally;
    private boolean swipeSequenceOwned;
    private boolean verticalScroll;
    private boolean multiplePointers;
    private boolean swipeDispatched;

    public Nm7SuperOkGroupSwipeListView(Context context) { this(context, null); }
    public Nm7SuperOkGroupSwipeListView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.listViewStyle);
    }
    public Nm7SuperOkGroupSwipeListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ViewConfiguration config = ViewConfiguration.get(context);
        touchSlop = config.getScaledTouchSlop();
        minimumFlingVelocity = config.getScaledMinimumFlingVelocity();
        density = context.getResources().getDisplayMetrics().density;
    }

    public void setOnGroupSwipeListener(OnGroupSwipeListener listener) {
        groupSwipeListener = listener;
    }

    /**
     * A row may call requestDisallowInterceptTouchEvent(true). As with RecyclerView's
     * item-touch listener, do not let that suppress this recognizer; forward only to
     * ancestors, while the recognizer arbitrates the stream in dispatchTouchEvent.
     */
    @Override public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (!disallowIntercept) {
            super.requestDisallowInterceptTouchEvent(false);
            return;
        }
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            beginTouch(event);
            return super.dispatchTouchEvent(event);
        }

        track(event);

        // Once the horizontal recognizer has claimed this pointer stream, do not
        // hand UP/MOVE back to AbsListView; otherwise it can interpret it as a tap.
        if (swipeSequenceOwned) {
            switch (action) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    multiplePointers = true;
                    draggingHorizontally = false;
                    springBack();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (draggingHorizontally && !multiplePointers) followFinger(event);
                    return true;
                case MotionEvent.ACTION_UP:
                    finishSwipe(event);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    springBack();
                    finishTracking();
                    return true;
                default:
                    return true;
            }
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            multiplePointers = true;
        }

        if (action == MotionEvent.ACTION_MOVE && !multiplePointers && !verticalScroll) {
            float dx = gestureX(event) - downX;
            float dy = gestureY(event) - downY;
            float absDx = Math.abs(dx);
            float absDy = Math.abs(dy);

            // Ignore a little diagonal noise, but preserve the native vertical path
            // when vertical movement has a clear lead.
            if (absDy > touchSlop * 1.45f && absDy > absDx * 1.18f) {
                verticalScroll = true;
            }

            if (!verticalScroll && absDx > touchSlop && absDx > absDy * 1.12f) {
                claimHorizontalSequence(event);
                followFinger(event);
                return true;
            }
        }

        if (action == MotionEvent.ACTION_UP && !multiplePointers && !verticalScroll
                && isQualifiedSwipe(event, true)) {
            // Some devices coalesce MOVE events. A quick flick may only qualify at UP;
            // cancel the old tap/long-press path before dispatching the group callback.
            claimHorizontalSequence(event);
            finishSwipe(event);
            return true;
        }

        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            finishTracking();
        }
        return handled;
    }

    private void beginTouch(MotionEvent event) {
        finishTracking();
        animate().cancel();
        setTranslationX(0f);
        setAlpha(1f);
        downX = gestureX(event);
        downY = gestureY(event);
        draggingHorizontally = false;
        swipeSequenceOwned = false;
        verticalScroll = false;
        multiplePointers = false;
        swipeDispatched = false;
        velocityTracker = VelocityTracker.obtain();
        lastEventTime = Long.MIN_VALUE;
        lastAction = -1;
        track(event);
    }

    private void claimHorizontalSequence(MotionEvent currentEvent) {
        draggingHorizontally = true;
        swipeSequenceOwned = true;
        cancelLongPress();
        setPressed(false);

        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);

        // End the original AbsListView/row stream explicitly so neither click nor
        // delayed long-press can survive after horizontal intent wins.
        MotionEvent cancel = MotionEvent.obtain(currentEvent);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    private void followFinger(MotionEvent event) {
        float dx = gestureX(event) - downX;
        float width = Math.max(1f, getWidth());
        float limit = Math.max(48f * density, Math.min(width * 0.20f, 180f * density));
        setTranslationX(clamp(dx * 0.45f, -limit, limit));
    }

    private boolean isQualifiedSwipe(MotionEvent event, boolean allowFling) {
        if (velocityTracker == null || multiplePointers || verticalScroll) return false;
        float dx = gestureX(event) - downX;
        float dy = gestureY(event) - downY;
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);
        if (absDx <= absDy * 1.20f) return false;

        float width = Math.max(1f, getWidth());
        float distanceLimit = Math.max(touchSlop * 2.5f,
                Math.min(width * 0.14f, 56f * density));

        velocityTracker.computeCurrentVelocity(1000);
        float vx = velocityTracker.getXVelocity();
        float vy = velocityTracker.getYVelocity();
        boolean enoughDistance = absDx >= distanceLimit;
        boolean fastFling = allowFling && absDx >= touchSlop * 1.5f
                && Math.abs(vx) >= minimumFlingVelocity
                && Math.abs(vx) > Math.abs(vy) * 1.12f;
        return enoughDistance || fastFling;
    }

    private void finishSwipe(MotionEvent event) {
        if (!swipeDispatched && !multiplePointers && isQualifiedSwipe(event, true)
                && groupSwipeListener != null) {
            swipeDispatched = true;
            float dx = gestureX(event) - downX;
            groupSwipeListener.onGroupSwipe(dx < 0f ? 1 : -1);
        }
        springBack();
        finishTracking();
    }

    private void springBack() {
        animate().cancel();
        animate().translationX(0f).alpha(1f).setDuration(180L)
                .setInterpolator(new DecelerateInterpolator(1.4f)).start();
    }

    // Incoming coordinates are local to the translated view; add translation back
    // to keep the gesture coordinate system fixed while the view follows the finger.
    private float gestureX(MotionEvent event) { return event.getX() + getTranslationX(); }
    private float gestureY(MotionEvent event) { return event.getY() + getTranslationY(); }

    private void track(MotionEvent event) {
        if (velocityTracker == null) return;
        float x = gestureX(event);
        float y = gestureY(event);
        if (event.getEventTime() == lastEventTime && event.getActionMasked() == lastAction
                && x == lastTrackedX && y == lastTrackedY) return;
        MotionEvent normalized = MotionEvent.obtain(event);
        normalized.offsetLocation(getTranslationX(), getTranslationY());
        velocityTracker.addMovement(normalized);
        normalized.recycle();
        lastEventTime = event.getEventTime();
        lastAction = event.getActionMasked();
        lastTrackedX = x;
        lastTrackedY = y;
    }

    private void finishTracking() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        lastEventTime = Long.MIN_VALUE;
        lastAction = -1;
        draggingHorizontally = false;
        swipeSequenceOwned = false;
        verticalScroll = false;
        multiplePointers = false;
        swipeDispatched = false;
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
}
