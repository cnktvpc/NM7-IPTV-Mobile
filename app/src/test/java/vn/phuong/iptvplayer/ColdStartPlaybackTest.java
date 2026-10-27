package vn.phuong.iptvplayer;

import android.app.Application;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ColdStartPlaybackTest {
    @Test public void coldLaunchDoesNotKeepDeadYoutubeOwner() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPlaybackSession.setTab(context, SharedPlaybackSession.TAB_YOUTUBE);
        SharedPlaybackSession.setYoutubeBackground(context, true);
        SharedPlaybackSession.clearTransientState(context);
        assertEquals(SharedPlaybackSession.TAB_IPTV, SharedPlaybackSession.tab(context));
        assertFalse(SharedPlaybackSession.isYoutubeBackground(context));
    }

    @Test public void coldLaunchPreservesSavedIptvChannel() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPlaybackSession.saveIptv(context, "Test", "https://example.test/live.m3u8",
                "", null, null, 123L, true);
        SharedPlaybackSession.clearTransientState(context);
        assertNotNull(SharedPlaybackSession.loadIptv(context));
        assertEquals(123L, SharedPlaybackSession.loadIptv(context).position);
    }
}
