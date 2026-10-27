import vn.phuong.iptvplayer.Channel;
import vn.phuong.iptvplayer.M3uParser;
import vn.phuong.iptvplayer.DrmSpec;
import vn.phuong.iptvplayer.FpsMeter;
import java.util.Collections;

/** Lightweight real-Java checks; does not replace Android build/device tests. */
public final class CoreCheck {
    private static int assertions;
    public static void main(String[] args) {
        M3uParser parser = new M3uParser();
        M3uParser.Result r = parser.parse("\uFEFF#EXTM3U\r\n#EXTINF:-1 group-title=\"A, B\",Name, One\r\n"
                + "#EXTVLCOPT:http-user-agent=Demo\r\n"
                + "../ch/live.m3u8|Origin=https%3A%2F%2Forigin.test&Cookie=a%3Db%2Bc\r\n",
                "https://example.test/list/channels.m3u");
        eq(1, r.channels.size());
        Channel c = r.channels.get(0);
        eq("Name, One", c.name());
        eq("A, B", c.group());
        eq("https://example.test/ch/live.m3u8", c.url());
        eq("Demo", c.headers().get("User-Agent"));
        eq("a=b+c", c.headers().get("Cookie"));
        Channel roundTrip = parser.parse(parser.export(r.channels), "").channels.get(0);
        eq(c.headers(), roundTrip.headers());
        eq(c.name(), roundTrip.name());
        eq(c.group(), roundTrip.group());
        eq(1, parser.parse("https://example.test/x\nhttps://example.test/x", "").duplicateCount);
        eq(4, parser.parse("https://x.test/A?key=Ab\nhttps://x.test/a?key=Ab\n"
                + "https://x.test/A?key=ab\nhttps://x.test/A?key=Ab|User-Agent=Other\n", "").channels.size());
        r = parser.parse("#EXTM3U\n#EXTINF:-1,A\n#EXTINF:-1,B\nnot a url\n", "");
        eq(0, r.channels.size()); eq(2, r.missingUrlCount);
        r = parser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg1.ts\n",
                "https://x.test/hls");
        eq(1, r.channels.size()); eq("https://x.test/hls", r.channels.get(0).url());
        eq("application/x-mpegURL", r.channels.get(0).mimeHint());
        r = parser.parse("#EXTM3U\n#EXTINF:-1,DRM\n"
                + "#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha\n"
                + "https://example.test/ch.mpd\n", "");
        eq(true, r.channels.get(0).needsDrm());
        eq(true, parser.parse(parser.export(r.channels), "").channels.get(0).needsDrm());
        r.channels.get(0).setSelected(false);
        eq("#EXTM3U\n", parser.export(r.channels));
        eq(1, parser.parse(parser.exportAll(r.channels), "").channels.size());
        eq(3, parser.parse("rtsp://192.0.2.1/cam\rudp://239.1.1.1:1234\rrtmp://example.test/live", "").channels.size());
        try { parser.parse("<html>Login</html>", "https://x.test"); throw new AssertionError("Accepted HTML"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { parser.parse("#EXTM3U\n#EXT-X-VERSION:3", ""); throw new AssertionError("Accepted local HLS"); }
        catch (IllegalArgumentException expected) { assertions++; }
        eq("#EXTM3U\n", parser.export(Collections.emptyList()));
        eq(1, parser.parse("https://x.test/a|Cookie=a%3Db\nhttps://x.test/a|cookie=a%3Db\n", "").channels.size());
        eq(2, parser.parse("https://x.test/a|X=a%2C%20Y%3Db\nhttps://x.test/a|X=a&Y=b\n", "").channels.size());
        eq(2, parser.parse("#EXTM3U\n#EXTINF:-1,A\n#KODIPROP:a=a, #KODIPROP:b=b\nhttps://x.test/a\n"
                + "#EXTINF:-1,B\n#KODIPROP:a=a\n#KODIPROP:b=b\nhttps://x.test/a\n", "").channels.size());
        r = parser.parse("#EXTM3U\n#EXTINF:-1,A\n#EXTVLCOPT:network-caching=1000\nhttps://x.test/a\n", "");
        eq(true, parser.export(r.channels).contains("#EXTVLCOPT:network-caching=1000\n"));
        DrmSpec drm = DrmSpec.fromOptions(java.util.Arrays.asList(
                "#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha",
                "#KODIPROP:inputstream.adaptive.license_key=https://license.test/wv|Authorization=Bearer%20demo|R{SSM}|"));
        eq("widevine", drm.system); eq("https://license.test/wv", drm.license);
        eq("Bearer demo", drm.headers.get("Authorization")); eq("", drm.problem);
        eq(true, DrmSpec.create("clearkey", "00112233445566778899aabbccddeeff:ffeeddccbbaa99887766554433221100").localClearKey());
        eq(true, !DrmSpec.create("widevine", "").problem.isEmpty());
        FpsMeter meter = new FpsMeter();
        eq(true, Double.isNaN(meter.sample(1000, 1, true)));
        eq(true, Math.abs(30.0 - meter.sample(3000, 61, true)) < 0.001);
        System.out.println("PASS: " + assertions + " core assertions");
    }
    private static void eq(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("Expected " + expected + " but got " + actual);
        assertions++;
    }
}
