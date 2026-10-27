package vn.phuong.iptvplayer;

import androidx.media3.common.MimeTypes;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class DrmAndFpsTest {
    @Test public void streamMimeUsesKodiManifestHintForExtensionlessPhpUrl() {
        assertEquals(MimeTypes.APPLICATION_MPD, StreamSpec.inferMime(
                "https://example.test/tv360.php?id=1", Arrays.asList(
                        "#KODIPROP:inputstream.adaptive.manifest_type=mpd")));
        assertEquals(MimeTypes.APPLICATION_M3U8, StreamSpec.inferMime(
                "https://example.test/live.php", Arrays.asList(
                        "#KODIPROP:inputstream.adaptive.manifest_type=hls")));
    }

    @Test public void streamMimeCanUseUrlAndLeavesUnknownPhpAutomatic() {
        assertEquals(MimeTypes.APPLICATION_M3U8,
                StreamSpec.inferMime("https://example.test/live.m3u8?token=x", Collections.emptyList()));
        assertNull(StreamSpec.inferMime("https://example.test/live.php?id=1", Collections.emptyList()));
    }
    @Test public void parsesWidevineLicenseAndHeaders() {
        DrmSpec spec = DrmSpec.fromOptions(Arrays.asList(
                "#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha",
                "#KODIPROP:inputstream.adaptive.license_key=https://license.test/wv|Authorization=Bearer%20abc|R{SSM}|"));
        assertEquals("widevine", spec.system);
        assertEquals("https://license.test/wv", spec.license);
        assertEquals("Bearer abc", spec.headers.get("Authorization"));
        assertEquals("", spec.problem);
    }

    @Test public void clearKeyAndIncompleteDrmAreReportedWithoutEchoingSecrets() {
        String key = "00112233445566778899aabbccddeeff:ffeeddccbbaa99887766554433221100";
        assertTrue(DrmSpec.create("clearkey", key).localClearKey());
        DrmSpec missing = DrmSpec.create("widevine", "");
        assertFalse(missing.problem.isEmpty());
        assertFalse(missing.problem.contains(key));
        assertEquals("unknown", DrmSpec.normalizeSystem("made-up-drm"));
    }

    @Test public void clearKeyUrlAndGetJsonAreRecognized() throws Exception {
        DrmSpec remote = DrmSpec.create("clearkey", "https://license.test/key.php?id=1");
        assertTrue(remote.remoteClearKey());
        assertFalse(remote.localClearKey());
        String response = new String(DrmPlayback.clearKeyResponse(
                "{\"kid\":\"00112233445566778899aabbccddeeff\",\"key\":\"ffeeddccbbaa99887766554433221100\"}"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(response.contains("\"keys\""));
        assertTrue(response.contains("\"type\":\"temporary\""));
    }

    @Test public void clearKeyAcceptsProviderArrayMapAndBase64Forms() throws Exception {
        String kid = "ABEiM0RVZneImaq7zN3u_w";
        String key = "_-7dzLuqmYh3ZlVEMyIRAA";

        String array = new String(DrmPlayback.clearKeyResponse(
                "[{\"kid\":\"" + kid + "\",\"key\":\"" + key + "\"}]"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(array.contains(kid));
        assertTrue(DrmSpec.create("clearkey", "[{\"kid\":\"" + kid + "\",\"key\":\"" + key + "\"}]").localClearKey());

        String map = new String(DrmPlayback.clearKeyResponse(
                "{\"keys\":{\"00112233445566778899aabbccddeeff\":\"ffeeddccbbaa99887766554433221100\"}}"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(map.contains("\"keys\""));

        String named = new String(DrmPlayback.clearKeyResponse(
                "kid=" + kid + "&key=" + key), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(named.contains(kid));
    }

    @Test public void rejectsUnsafeLicenseHeadersAndCustomTemplates() {
        assertFalse(DrmSpec.create("widevine", "https://license.test|Bad%0AName=x").problem.isEmpty());
        assertFalse(DrmSpec.create("widevine", "https://license.test||CUSTOM{SSM}|").problem.isEmpty());
    }

    @Test public void fpsUsesRenderedFrameDeltaAndWallClock() {
        FpsMeter meter = new FpsMeter();
        assertTrue(Double.isNaN(meter.sample(1000, 10, true)));
        assertEquals(30.0, meter.sample(3000, 70, true), 0.001);
        assertEquals(0.0, meter.sample(4000, 70, false), 0.001);
        assertTrue(Double.isNaN(meter.sample(5000, 90, true)));
    }
}
