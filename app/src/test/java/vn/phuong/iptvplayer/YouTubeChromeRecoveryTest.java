package vn.phuong.iptvplayer;

import android.app.Application;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class YouTubeChromeRecoveryTest {
    private Object helper(String name) throws Exception {
        return Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.shared." + name).getConstructor().newInstance();
    }
    private int move(Object gesture, int action, float x, float y, boolean hit) throws Exception {
        MotionEvent e = MotionEvent.obtain(0, 16, action, x, y, 0);
        try {
            return (Integer) gesture.getClass().getMethod("update", MotionEvent.class, boolean.class, float.class)
                    .invoke(gesture, e, hit, 24f);
        } finally { e.recycle(); }
    }
    @Test public void stationaryFingerCannotToggleChromeAfterLayoutChanges() throws Exception {
        Object g = helper("Nm7ChromeGesture");
        move(g, 0, 100, 400, true);
        assertEquals(1, move(g, 2, 100, 350, true));
        for (int i = 0; i < 20; i++) assertEquals(0, move(g, 2, 100, 350, true));
        assertEquals(0, move(g, 1, 100, 350, true));
        assertEquals(0, move(g, 2, 100, 250, true));
    }
    @Test public void deliberateDownwardDragShowsChromeAgain() throws Exception {
        Object g = helper("Nm7ChromeGesture");
        move(g, 0, 100, 400, true);
        assertEquals(1, move(g, 2, 100, 350, true));
        assertEquals(-1, move(g, 2, 100, 390, true));
    }
    @Test public void miniAndHorizontalSwipesDoNotHideNavigation() throws Exception {
        Object g = helper("Nm7ChromeGesture");
        move(g, 0, 100, 400, false);
        assertEquals(0, move(g, 2, 100, 250, true));
        move(g, 0, 100, 400, true);
        assertEquals(0, move(g, 2, 220, 390, true));
    }
    @Test public void hiddenHeaderDoesNotResizeActualBrowseViewport() {
        Application app = RuntimeEnvironment.getApplication();
        int theme = app.getResources().getIdentifier("Theme.SmartTubeDroid", "style", app.getPackageName());
        Context c = new ContextThemeWrapper(app, theme);
        int layout = app.getResources().getIdentifier("browse_activity", "layout", app.getPackageName());
        int contentId = app.getResources().getIdentifier("browse_content", "id", app.getPackageName());
        int headerId = app.getResources().getIdentifier("browse_app_bar", "id", app.getPackageName());
        View root = LayoutInflater.from(c).inflate(layout, null, false);
        assertTrue(root instanceof ViewGroup);
        int w = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        int h = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
        root.measure(w, h); root.layout(0, 0, 1080, 1920);
        View content = root.findViewById(contentId), header = root.findViewById(headerId);
        int originalTop = content.getTop(), originalHeight = content.getHeight();
        for (int i = 0; i < 10; i++) {
            header.setVisibility(i % 2 == 0 ? View.INVISIBLE : View.VISIBLE);
            root.measure(w, h); root.layout(0, 0, 1080, 1920);
            assertEquals(originalTop, content.getTop());
            assertEquals(originalHeight, content.getHeight());
        }
        assertTrue("Browse content must have a usable viewport", originalHeight > 0);
        assertTrue("Browse viewport must stay within the measured root",
                originalTop >= 0 && originalTop + originalHeight <= root.getHeight());
    }
    private boolean allow(Object gate, String id, boolean play, boolean iptv, boolean manual) throws Exception {
        return (Boolean) gate.getClass().getMethod("allow", String.class, boolean.class, boolean.class, boolean.class)
                .invoke(gate, id, play, iptv, manual);
    }
    @Test public void automaticRecoveryIsBoundedEvenIfSameVideoIsRebound() throws Exception {
        Object gate = helper("Nm7MiniRecoveryGate");
        assertTrue(allow(gate, "videoA", true, false, false));
        gate.getClass().getMethod("video", String.class).invoke(gate, "videoA");
        assertFalse(allow(gate, "videoA", true, false, false));
    }
    @Test public void pauseAndIptvNeverTriggerAutomaticResume() throws Exception {
        Object gate = helper("Nm7MiniRecoveryGate");
        assertFalse(allow(gate, "videoA", false, false, false));
        assertFalse(allow(gate, "videoA", true, true, false));
        assertTrue(allow(gate, "videoA", true, false, false));
    }
    @Test public void manualRetryAndNewVideoHaveExplicitBudgets() throws Exception {
        Object gate = helper("Nm7MiniRecoveryGate");
        assertTrue(allow(gate, "videoA", true, false, false));
        assertTrue(allow(gate, "videoA", true, false, true));
        assertFalse(allow(gate, "videoA", true, false, false));
        assertTrue(allow(gate, "videoB", true, false, false));
        assertFalse(allow(gate, null, true, false, false));
    }
}
