package vn.phuong.iptvplayer;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class YouTubeRecoveryCancellationTest {
    @Test public void detachCancelsAllQueuedTasksAndKeepsBudget() throws Exception {
        Class<?> type = Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity");
        Object owner = type.getConstructor().newInstance();
        Field handlerField = type.getDeclaredField("mHandler"); handlerField.setAccessible(true);
        Handler handler = (Handler) handlerField.get(owner);
        int[] calls = {0};
        for (String name : new String[]{"mNm7RenderTick", "mNm7DecoderRetry", "mNm7VisibilityTask"}) {
            Field field = type.getDeclaredField(name); field.setAccessible(true);
            Runnable task = () -> calls[0]++;
            field.set(owner, task); handler.postDelayed(task, 1500);
        }
        Field policyField = type.getDeclaredField("mNm7RenderPolicy"); policyField.setAccessible(true);
        Object policy = policyField.get(owner);
        Method failure = policy.getClass().getMethod("failure");
        assertEquals(1, failure.invoke(policy));
        Method detach = type.getDeclaredMethod("detachNm7PlaybackObserver"); detach.setAccessible(true);
        detach.invoke(owner);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);
        assertEquals(0, calls[0]);
        assertEquals(2, failure.invoke(policy));
        for (String name : new String[]{"mNm7RenderTick", "mNm7DecoderRetry", "mNm7VisibilityTask"}) {
            Field field = type.getDeclaredField(name); field.setAccessible(true);
            assertNull(field.get(owner));
        }
    }
}
