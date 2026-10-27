package vn.phuong.iptvplayer;

import android.app.Application;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Exercises real merged XML/PlayerView inflation; does not simulate video decoding. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class YouTubeSurfaceLayoutTest {
    private Context themedContext() {
        Application app = RuntimeEnvironment.getApplication();
        int style = app.getResources().getIdentifier("Theme.SmartTubeDroid", "style", app.getPackageName());
        assertTrue("SmartTube theme merged", style != 0);
        return new ContextThemeWrapper(app, style);
    }

    private void assertSurfaceTarget(View player) throws Exception {
        assertNotNull(player);
        Object surface = player.getClass().getMethod("getVideoSurfaceView").invoke(player);
        assertTrue("Mobile PlayerView must create SurfaceView, not TextureView", surface instanceof SurfaceView);
        assertEquals(Boolean.FALSE, player.getClass().getMethod("getUseController").invoke(player));
    }

    @Test public void miniUsesSurfaceAfterAllResourceMerges() throws Exception {
        View mini = LayoutInflater.from(themedContext()).inflate(R.layout.nm7_mini_player, null, false);
        assertSurfaceTarget(mini);
    }

    @Test public void fullscreenUsesMatchingSurfaceTarget() throws Exception {
        Context context = themedContext();
        int layout = context.getResources().getIdentifier("playback_activity", "layout", context.getPackageName());
        int playerId = context.getResources().getIdentifier("playback_player_view", "id", context.getPackageName());
        assertTrue(layout != 0 && playerId != 0);
        View root = LayoutInflater.from(context).inflate(layout, null, false);
        assertSurfaceTarget(root.findViewById(playerId));
    }
}
