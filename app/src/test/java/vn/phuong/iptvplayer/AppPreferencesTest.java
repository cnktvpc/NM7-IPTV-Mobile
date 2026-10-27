package vn.phuong.iptvplayer;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppPreferencesTest {
    @Test public void channelIdsAreStableHashesWithoutUrlText() {
        Channel channel = new Channel("Kênh", "Nhóm", "https://private.test/live?token=secret", "", "", Collections.emptyMap());
        String first = AppPreferences.id(channel);
        assertEquals(first, AppPreferences.id(channel));
        assertEquals(64, first.length());
        assertFalse(first.contains("token"));
        assertNotEquals(first, AppPreferences.id(new Channel("Kênh khác", "Nhóm", "https://private.test/2", "", "", Collections.emptyMap())));
    }
}
