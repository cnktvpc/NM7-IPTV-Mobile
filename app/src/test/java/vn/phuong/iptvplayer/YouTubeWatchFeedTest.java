package vn.phuong.iptvplayer;

import android.app.Application;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Array;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class YouTubeWatchFeedTest {
    private int id(Context c, String name) {
        return c.getResources().getIdentifier(name, "id", c.getPackageName());
    }
    private Context context() {
        Application app = RuntimeEnvironment.getApplication();
        int theme = app.getResources().getIdentifier("Theme.SmartTubeDroid", "style", app.getPackageName());
        return new ContextThemeWrapper(app, theme);
    }
    private void measure(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1080, 1920);
    }
    @Test public void watchMetadataScrollsWithFeedAndVideoBoundsStayFixed() throws Exception {
        Context c = context();
        int layout = c.getResources().getIdentifier("playback_activity", "layout", c.getPackageName());
        View root = LayoutInflater.from(c).inflate(layout, null, false);
        View video = root.findViewById(id(c, "playback_player_container"));
        View header = root.findViewById(id(c, "nm7_watch_metadata"));
        TextView title = root.findViewById(id(c, "playback_title"));
        title.setText("Video đang phát — kiểm tra cuộn mô tả");
        View button = root.findViewById(id(c, "playback_chip_like"));
        final int[] clicks = {0};
        button.setOnClickListener(v -> clicks[0]++);
        ((ViewGroup) header.getParent()).removeView(header);
        Class<?> headerType = Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7WatchHeaderAdapter");
        Object first = headerType.getConstructor(View.class).newInstance(header);
        View tail = new View(c); tail.setMinimumHeight(6000);
        Object second = headerType.getConstructor(View.class).newInstance(tail);
        Class<?> adapterType = Class.forName("androidx.recyclerview.widget.RecyclerView$Adapter");
        Object adapters = Array.newInstance(adapterType, 2);
        Array.set(adapters, 0, first); Array.set(adapters, 1, second);
        Class<?> concatType = Class.forName("androidx.recyclerview.widget.ConcatAdapter");
        Object concat = concatType.getConstructor(adapters.getClass()).newInstance(adapters);
        View list = root.findViewById(id(c, "playback_suggestions"));
        Class<?> layoutManager = Class.forName("androidx.recyclerview.widget.RecyclerView$LayoutManager");
        Object manager = Class.forName("androidx.recyclerview.widget.LinearLayoutManager")
                .getConstructor(Context.class).newInstance(c);
        list.getClass().getMethod("setLayoutManager", layoutManager).invoke(list, manager);
        list.getClass().getMethod("setAdapter", adapterType).invoke(list, concat);
        measure(root);
        int videoTop = video.getTop(), videoHeight = video.getHeight();
        assertTrue(videoHeight > 0);
        int oldHeaderTop = header.getTop();
        // Keep part of the compact header visible: detached/recycled views retain stale bounds.
        int scrollDistance = Math.max(1, Math.min(80, header.getHeight() / 2));
        list.scrollBy(0, scrollDistance);
        assertTrue("metadata must move with recommendations", header.getTop() < oldHeaderTop);
        assertEquals(videoTop, video.getTop());
        assertEquals(videoHeight, video.getHeight());
        list.scrollBy(0, -scrollDistance);
        button.performClick();
        assertEquals("native action listener survives scrolling", 1, clicks[0]);
        assertEquals(2, concatType.getMethod("getItemCount").invoke(concat));
    }
    @Test public void controlsAndCommentsRemainInMetadataAndDoNotBecomeFakeInputs() {
        Context c = context();
        View root = LayoutInflater.from(c).inflate(c.getResources().getIdentifier(
                "playback_activity", "layout", c.getPackageName()), null, false);
        ViewGroup header = root.findViewById(id(c, "nm7_watch_metadata"));
        assertNotNull(header.findViewById(id(c, "playback_chip_subscribe")));
        assertNotNull(header.findViewById(id(c, "playback_chip_like")));
        assertNotNull(header.findViewById(id(c, "playback_chip_dislike")));
        assertNotNull(header.findViewById(id(c, "playback_comments_card")));
        assertEquals("Đang tải bình luận…", ((TextView) header.findViewById(id(c, "nm7_comment_preview"))).getText().toString());
        assertEquals(View.GONE, header.findViewById(id(c, "playback_description_card")).getVisibility());
        assertNotNull(header.findViewById(id(c, "nm7_details_more")));
        assertEquals(1, ((TextView) header.findViewById(id(c, "playback_title"))).getMaxLines());
        assertEquals(1, ((TextView) header.findViewById(id(c, "playback_second_title"))).getMaxLines());
    }
}
