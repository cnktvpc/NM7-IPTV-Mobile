package vn.phuong.iptvplayer;

import org.junit.Test;
import static org.junit.Assert.*;

public class YouTubePlaybackErrorTest {
    public static class HttpFailure extends Exception {
        public final int responseCode = 403;
        HttpFailure() { super("https://example.invalid/video?token=private"); }
    }
    private String describe(Throwable error, int type) throws Exception {
        return (String) Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7PlaybackError")
                .getMethod("describe", Throwable.class, int.class).invoke(null, error, type);
    }
    @Test public void reportsNestedCauseWithoutLeakingSourceUrl() throws Exception {
        assertEquals("Nguồn phát HTTP 403", describe(new RuntimeException(new HttpFailure()), 0));
        assertEquals("Thiếu bộ nhớ", describe(new RuntimeException(new OutOfMemoryError()), 2));
        assertEquals("Lỗi kết nối mạng", describe(new java.net.SocketTimeoutException(), 0));
        assertEquals("Lỗi giải mã", describe(new IllegalStateException("private"), 1));
    }
}
