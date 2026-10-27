package vn.phuong.iptvplayer;

import android.app.Application;
import android.view.MotionEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Exercise event sequences including fast UP-only swipes and mini exclusion. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class YouTubeBrowseGestureTest {
    private Object gesture() throws Exception {
        return Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7BrowseGesture")
                .getConstructor().newInstance();
    }
    private boolean event(Object g, int action, float x, float y, boolean hit) throws Exception {
        MotionEvent event = MotionEvent.obtain(0, 20, action, x, y, 0);
        try {
            return (Boolean) g.getClass().getMethod("update", MotionEvent.class, boolean.class, int.class, float.class)
                    .invoke(g, event, hit, 8, 56f);
        } finally { event.recycle(); }
    }
    private int direction(Object g) throws Exception {
        return (Integer) g.getClass().getMethod("direction").invoke(g);
    }
    private boolean cancel(Object g) throws Exception {
        return (Boolean) g.getClass().getMethod("cancelChild").invoke(g);
    }
    @Test public void swipeCancelsCardBeforeUpAndChangesExactlyOnce() throws Exception {
        Object g = gesture();
        assertFalse(event(g, 0, 200, 200, true));
        assertTrue(event(g, 2, 170, 200, true));
        assertTrue(cancel(g));
        assertEquals(0, direction(g));
        assertTrue(event(g, 2, 120, 200, true));
        assertFalse(cancel(g));
        assertEquals(1, direction(g)); // respond before the finger is lifted
        assertTrue(event(g, 2, 90, 200, true));
        assertEquals(0, direction(g));
        assertTrue(event(g, 1, 100, 200, true));
        assertEquals(0, direction(g));
        assertFalse(event(g, 0, 200, 200, true));
        assertEquals(0, direction(g));
    }
    @Test public void fastSwipeWithoutMoveCannotClickCard() throws Exception {
        Object g = gesture();
        event(g, 0, 200, 200, true);
        assertTrue(event(g, 1, 100, 200, true));
        assertTrue(cancel(g));
        assertEquals(1, direction(g));
    }
    @Test public void ordinaryTapAndVerticalScrollRemainAvailable() throws Exception {
        Object g = gesture();
        event(g, 0, 200, 200, true);
        assertFalse(event(g, 1, 202, 202, true));
        assertEquals(0, direction(g));
        event(g, 0, 200, 200, true);
        assertFalse(event(g, 2, 202, 160, true));
        assertFalse(event(g, 1, 100, 130, true));
        assertEquals(0, direction(g));
    }
    @Test public void shortDragCancelsClickWithoutChangingSection() throws Exception {
        Object g = gesture();
        event(g, 0, 200, 200, true);
        assertTrue(event(g, 2, 180, 200, true));
        assertTrue(event(g, 1, 175, 200, true));
        assertEquals(0, direction(g));
    }
    @Test public void miniAndToolbarNeverBecomeSectionGestures() throws Exception {
        Object g = gesture();
        event(g, 0, 200, 200, false);
        assertFalse(event(g, 2, 100, 200, true));
        assertFalse(event(g, 1, 80, 200, true));
        assertEquals(0, direction(g));
    }
    @Test public void cancellationCannotChangeSectionOrPoisonNextTap() throws Exception {
        Object g = gesture();
        event(g, 0, 200, 200, true);
        event(g, 2, 100, 200, true);
        assertTrue(event(g, 3, 100, 200, true));
        assertEquals(0, direction(g));
        event(g, 0, 200, 200, true);
        assertFalse(event(g, 1, 200, 200, true));
    }
}
