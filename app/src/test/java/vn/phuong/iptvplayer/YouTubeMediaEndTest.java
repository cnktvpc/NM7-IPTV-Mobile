package vn.phuong.iptvplayer;

import android.app.Application;
import android.content.Context;
import java.lang.reflect.*;
import java.lang.ref.WeakReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class YouTubeMediaEndTest {
    private static final String COMMON = "com.liskovsoft.smartyoutubetv2.common.";
    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        return null;
    }
    private Object controller(int[] ended) throws Exception {
        Class<?> listener = Class.forName(COMMON + "app.models.playback.listener.PlayerEventListener");
        Object events = Proxy.newProxyInstance(listener.getClassLoader(), new Class[]{listener}, (p,m,a) -> {
            if (m.getName().equals("onPlayEnd")) ended[0]++;
            return defaultValue(m.getReturnType());
        });
        Class<?> type = Class.forName(COMMON + "exoplayer.controller.ExoPlayerController");
        Object controller = type.getConstructor(Context.class, listener).newInstance(RuntimeEnvironment.getApplication(), events);
        Class<?> view = Class.forName(COMMON + "exoplayer.controller.PlayerView");
        Class<?> owner = Class.forName(COMMON + "exoplayer.controller.Nm7PlaybackEndOwner");
        Object target = Proxy.newProxyInstance(view.getClassLoader(), new Class[]{view,owner},
                (p,m,a) -> defaultValue(m.getReturnType()));
        type.getMethod("setPlayerView", view).invoke(controller, target);
        return controller;
    }
    @Test public void mediaPeriodTransitionsAndSeeksDoNotStopOrEndMobilePlayback() throws Exception {
        int[] ended = {0}; Object controller = controller(ended);
        Method discontinuity = controller.getClass().getMethod("onPositionDiscontinuity", int.class);
        Class<?> player = Class.forName("com.google.android.exoplayer2.Player");
        int period = player.getField("DISCONTINUITY_REASON_PERIOD_TRANSITION").getInt(null);
        int seek = player.getField("DISCONTINUITY_REASON_SEEK").getInt(null);
        // With no engine attached, any old stop() call also fails this regression test.
        for (int i=0; i<100; i++) discontinuity.invoke(controller, period);
        discontinuity.invoke(controller, seek);
        assertEquals(0, ended[0]);
    }
    @Test public void actualEndedStateStillEmitsExactlyOneEnd() throws Exception {
        int[] ended = {0}; Object controller = controller(ended);
        Method state = controller.getClass().getMethod("onPlayerStateChanged", boolean.class, int.class);
        state.invoke(controller, true, 2); // BUFFERING
        state.invoke(controller, true, 3); // READY
        state.invoke(controller, false, 3); // user pause
        assertEquals(0, ended[0]);
        state.invoke(controller, true, 4); // ENDED
        state.invoke(controller, true, 4);
        assertEquals(1, ended[0]);
    }
    @Test public void archivedLiveErrorNearEndIsNotTreatedAsCompletedVideo() throws Exception {
        Class<?> presenter = Class.forName(COMMON + "app.presenters.PlaybackPresenter");
        Constructor<?> ctor = presenter.getDeclaredConstructor(Context.class); ctor.setAccessible(true);
        Object instance = ctor.newInstance(RuntimeEnvironment.getApplication());
        Class<?> view = Class.forName(COMMON + "app.views.PlaybackView");
        Class<?> owner = Class.forName(COMMON + "exoplayer.controller.Nm7PlaybackEndOwner");
        boolean[] ended = {false};
        Object target = Proxy.newProxyInstance(view.getClassLoader(), new Class[]{view,owner}, (p,m,a) -> {
            if (m.getName().equals("hasNm7MediaEnded")) return ended[0];
            if (m.getName().equals("getDurationMs")) return 100000L;
            if (m.getName().equals("getPositionMs")) return 90000L;
            return defaultValue(m.getReturnType());
        });
        Field playerField = presenter.getDeclaredField("mPlayer"); playerField.setAccessible(true);
        playerField.set(instance, new WeakReference<>(target));
        Class<?> videoClass = Class.forName(COMMON + "app.models.data.Video");
        Object video = videoClass.getConstructor().newInstance(); videoClass.getField("isLiveEnd").setBoolean(video,true);
        Field videoField = presenter.getDeclaredField("mVideo"); videoField.setAccessible(true);
        videoField.set(instance, new WeakReference<>(video));
        Class<?> fixerClass = Class.forName(COMMON + "app.models.playback.controllers.ErrorFixerController");
        Object fixer = fixerClass.getConstructor().newInstance();
        fixerClass.getMethod("setMainController", presenter).invoke(fixer,instance);
        Method check = fixerClass.getDeclaredMethod("isStreamEnded"); check.setAccessible(true);
        assertEquals(false,check.invoke(fixer));
        ended[0]=true;
        assertEquals(true,check.invoke(fixer));
    }
}
