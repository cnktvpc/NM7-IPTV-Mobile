package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class YouTubeNavigationOwnerTest {
    @Test public void selectionFromBrowseOrSearchReordersExistingPlayerWithoutClearingStack() throws Exception {
        Class<?> type = Class.forName("com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager");
        Constructor<?> constructor = type.getDeclaredConstructor(Context.class); constructor.setAccessible(true);
        Object manager = constructor.newInstance(RuntimeEnvironment.getApplication());
        Method create = type.getDeclaredMethod("createNm7LaunchIntent", Class.class); create.setAccessible(true);
        Class<?> player = Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity");
        Intent intent = (Intent) create.invoke(manager, player);
        assertEquals(player.getName(), intent.getComponent().getClassName());
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) != 0);
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
        assertEquals(0, intent.getFlags() & (Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Intent other = (Intent) create.invoke(manager, Activity.class);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, other.getFlags());
    }

    @Test public void retiredControllerCannotSendReadyEndedOrSeekToSharedPresenter() throws Exception {
        Class<?> controller = Class.forName("com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController");
        Class<?> listenerType = Class.forName("com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener");
        int[] plays = {0}, ends = {0}, seeks = {0}; boolean[] current = {false};
        Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class[]{listenerType}, (p,m,a) -> {
            if (m.getName().equals("onPlay")) plays[0]++;
            if (m.getName().equals("onPlayEnd")) ends[0]++;
            if (m.getName().equals("onSeekEnd")) seeks[0]++;
            return m.getReturnType() == boolean.class ? false : null;
        });
        Object engine = controller.getConstructor(Context.class, listenerType)
                .newInstance(RuntimeEnvironment.getApplication(), listener);
        Class<?> viewType = Class.forName("com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerView");
        Class<?> ownerType = Class.forName("com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.Nm7CurrentEngineOwner");
        Object view = Proxy.newProxyInstance(viewType.getClassLoader(), new Class[]{viewType, ownerType}, (p,m,a) -> {
            if (m.getName().equals("isNm7CurrentEngine")) return current[0];
            return m.getReturnType() == boolean.class ? false : null;
        });
        controller.getMethod("setPlayerView", viewType).invoke(engine, view);
        Method state = controller.getMethod("onPlayerStateChanged", boolean.class, int.class);
        Method seek = controller.getMethod("onSeekProcessed");
        state.invoke(engine, true, 3); state.invoke(engine, true, 4); seek.invoke(engine);
        assertEquals(0, plays[0]); assertEquals(0, ends[0]); assertEquals(0, seeks[0]);
        current[0] = true;
        state.invoke(engine, true, 3); seek.invoke(engine);
        assertEquals(1, plays[0]); assertEquals(1, seeks[0]);
        state.invoke(engine, true, 4); assertEquals(1, ends[0]);
    }
}
