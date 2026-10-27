package vn.phuong.iptvplayer;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaylistSourceStoreTest {
    @Test public void acceptsOnlyHttpPlaylistSources() {
        assertTrue(PlaylistSourceStore.isValid("https://example.test/list.m3u"));
        assertTrue(PlaylistSourceStore.isValid("http://example.test/list"));
        assertFalse(PlaylistSourceStore.isValid("file:///sdcard/list.m3u"));
        assertFalse(PlaylistSourceStore.isValid("javascript:alert(1)"));
        assertFalse(PlaylistSourceStore.isValid(null));
    }

    @Test public void recognizesOnlyBuiltInDefaultSources() {
        assertEquals("https://phuongnm7-playlist.phuongnm7-iptv.workers.dev/", PlaylistSourceStore.DEFAULT_URL);
        assertEquals("NM7 IPTV", PlaylistSourceStore.DEFAULT_NAME);
        assertTrue(PlaylistSourceStore.isDefault(PlaylistSourceStore.DEFAULT_URL));
        assertTrue(PlaylistSourceStore.isDefault("https://iptv-live-merge.phuongnm7-iptv.workers.dev/playlist.m3u"));
        assertTrue(PlaylistSourceStore.isLegacyDefault("https://iptv-live-merge.phuongnm7-iptv.workers.dev/playlist.m3u"));
        assertFalse(PlaylistSourceStore.isLegacyDefault(PlaylistSourceStore.DEFAULT_URL));
        assertFalse(PlaylistSourceStore.isDefault("https://example.test/list.m3u"));
    }

    @Test public void returnsToDefaultWhenCurrentCustomSourceWasDeleted() {
        String custom = "https://example.test/custom.m3u";
        assertFalse(PlaylistSourceStore.shouldReturnToDefault(
                custom, java.util.Arrays.asList(new PlaylistSourceStore.Source("Custom", custom))));
        assertTrue(PlaylistSourceStore.shouldReturnToDefault(
                custom, java.util.Collections.emptyList()));
        assertTrue(PlaylistSourceStore.shouldReturnToDefault(
                custom + "\nChuyển hướng: https://cdn.example.test/live.m3u",
                java.util.Collections.emptyList()));
    }

    @Test public void keepsDefaultAndLocalSessionsWhenAnotherSourceIsDeleted() {
        assertFalse(PlaylistSourceStore.shouldReturnToDefault(
                PlaylistSourceStore.DEFAULT_URL, java.util.Collections.emptyList()));
        assertFalse(PlaylistSourceStore.shouldReturnToDefault(
                "content://media/external/playlist.m3u", java.util.Collections.emptyList()));
    }


    @Test public void startupRefreshAcceptsNetworkSourceDescriptions() {
        assertTrue(PlaylistSourceStore.shouldRefreshOnStartup(PlaylistSourceStore.DEFAULT_URL));
        assertTrue(PlaylistSourceStore.shouldRefreshOnStartup("https://example.com/list.m3u"));
        assertTrue(PlaylistSourceStore.shouldRefreshOnStartup("https://example.com/list.m3u\nChuyển hướng: https://cdn.example.com/list.m3u"));
    }

    @Test public void startupRefreshSkipsLocalAndEmptySources() {
        assertFalse(PlaylistSourceStore.shouldRefreshOnStartup(""));
        assertFalse(PlaylistSourceStore.shouldRefreshOnStartup(null));
        assertFalse(PlaylistSourceStore.shouldRefreshOnStartup("content://documents/playlist.m3u"));
        assertFalse(PlaylistSourceStore.shouldRefreshOnStartup("file:///sdcard/playlist.m3u"));
    }

}
