package vn.phuong.iptvplayer;

import android.app.Application;
import android.content.Intent;
import android.os.PowerManager;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class YouTubeKeepAliveTest {
    private boolean keep(boolean owns, boolean stopped, boolean iptv, boolean mini, boolean background) throws Exception {
        return (Boolean) Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7KeepAlivePolicy")
                .getMethod("keep", boolean.class, boolean.class, boolean.class, boolean.class, boolean.class)
                .invoke(null, owns, stopped, iptv, mini, background);
    }
    @Test public void miniRemainsForegroundAcrossLockAndUnlock() throws Exception {
        assertTrue(keep(true, false, false, true, false));
        assertTrue(keep(true, false, false, true, true));
        assertTrue(keep(true, false, false, true, false));
    }
    @Test public void fullPlayerOnlyNeedsServiceInBackground() throws Exception {
        assertFalse(keep(true, false, false, false, false));
        assertTrue(keep(true, false, false, false, true));
    }
    @Test public void closeAndIptvAlwaysReleaseSession() throws Exception {
        assertFalse(keep(true, true, false, true, true));
        assertFalse(keep(true, false, true, true, true));
        assertFalse(keep(false, false, false, true, true));
    }
    @Test public void temporaryPauseKeepsRecoveryAwakeButUserPauseDoesNot() throws Exception {
        Class<?> c = Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7KeepAlivePolicy");
        java.lang.reflect.Method wake = c.getMethod("wake", boolean.class, boolean.class, boolean.class);
        assertEquals(true, wake.invoke(null, false, true, false));
        assertEquals(false, wake.invoke(null, false, false, false));
        assertEquals(false, wake.invoke(null, true, false, true));
    }
    @Test public void serviceRetainsNotificationWhilePauseReleasesWakeLock() throws Exception {
        org.robolectric.android.controller.ServiceController<BackgroundPlaybackService> controller =
                Robolectric.buildService(BackgroundPlaybackService.class).create();
        BackgroundPlaybackService service = controller.get();
        Intent playing = new Intent().putExtra("youtube", true).putExtra("youtube_active", true);
        Intent paused = new Intent().putExtra("youtube", true).putExtra("youtube_active", false);
        Field field = BackgroundPlaybackService.class.getDeclaredField("youtubeWakeLock");
        field.setAccessible(true);
        try {
            service.onStartCommand(playing, 0, 1);
            assertTrue(((PowerManager.WakeLock) field.get(service)).isHeld());
            service.onStartCommand(paused, 0, 2);
            assertNull(field.get(service));
            assertNotNull(org.robolectric.Shadows.shadowOf(service).getLastForegroundNotification());
            assertFalse(org.robolectric.Shadows.shadowOf(service).isStoppedBySelf());
            service.onStartCommand(playing, 0, 3);
            assertTrue(((PowerManager.WakeLock) field.get(service)).isHeld());
        } finally { controller.destroy(); }
        assertNull(field.get(service));
    }
    @Test public void decoderRebuildDoesNotMakeLiveMiniDisappear() throws Exception {
        Class<?> c = Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity");
        Field active = c.getDeclaredField("sNm7Active"), mini = c.getDeclaredField("sNm7Mini");
        Field video = c.getDeclaredField("mNm7SessionVideo"), stopped = c.getDeclaredField("mNm7Stopped");
        for (Field f : new Field[]{active, mini, video, stopped}) f.setAccessible(true);
        Object oldOwner = active.get(null); boolean oldMini = mini.getBoolean(null);
        Object owner = c.getConstructor().newInstance();
        try {
            active.set(null, owner); mini.setBoolean(null, true);
            video.set(owner, Class.forName("com.liskovsoft.smartyoutubetv2.common.app.models.data.Video").getConstructor().newInstance());
            assertEquals(true, c.getMethod("isNm7MiniPlayerActive").invoke(null));
            stopped.setBoolean(owner, true);
            assertEquals(false, c.getMethod("isNm7MiniPlayerActive").invoke(null));
        } finally { active.set(null, oldOwner); mini.setBoolean(null, oldMini); }
    }
}
