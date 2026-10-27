package vn.phuong.iptvplayer;

import org.junit.Test;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class M3uParserTest {
    @Test public void parsesGroupsHeadersRelativeUrlsAndDuplicates() {
        String input = "#EXTM3U\n"
                + "#EXTINF:-1 tvg-id=\"v1\" group-title=\"Thể thao\",Kênh Một\n"
                + "#EXTVLCOPT:http-user-agent=DemoAgent\n"
                + "live/one.m3u8\n"
                + "#EXTINF:-1 group-title=\"Thể thao\",Bản trùng\n"
                + "#EXTVLCOPT:http-user-agent=DemoAgent\n"
                + "https://example.com/base/live/one.m3u8\n"
                + "#EXTINF:-1,Kênh thiếu\n";

        M3uParser.Result result = new M3uParser().parse(input, "https://example.com/base/list.m3u");

        assertEquals(1, result.channels.size());
        assertEquals(1, result.duplicateCount);
        assertEquals(1, result.missingUrlCount);
        assertEquals("Kênh Một", result.channels.get(0).name());
        assertEquals("Thể thao", result.channels.get(0).group());
        assertEquals("DemoAgent", result.channels.get(0).headers().get("User-Agent"));
    }

    @Test public void exportsOnlySelectedChannels() {
        M3uParser.Result result = new M3uParser().parse(
                "#EXTM3U\n#EXTINF:-1 group-title=\"TV\",A\nhttps://a.test/a.m3u8\n"
                        + "#EXTINF:-1,B\nhttps://b.test/b.m3u8\n", "");
        result.channels.get(1).setSelected(false);

        String output = new M3uParser().export(result.channels);

        assertTrue(output.contains("https://a.test/a.m3u8"));
        assertTrue(!output.contains("https://b.test/b.m3u8"));
    }

    @Test public void distinguishesCaseAndHeaderValues() {
        String text = "#EXTM3U\nhttps://x.test/A?key=Ab\nhttps://x.test/a?key=Ab\n"
                + "https://x.test/A?key=ab\nhttps://x.test/A?key=Ab|User-Agent=Other\n";
        assertEquals(4, new M3uParser().parse(text, "").channels.size());
    }

    @Test public void treatsHlsAsOneStreamNotSegments() {
        String text = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg1.ts\n#EXTINF:6,\nseg2.ts\n";
        M3uParser.Result r = new M3uParser().parse(text, "https://x.test/live");
        assertEquals(1, r.channels.size());
        assertEquals("https://x.test/live", r.channels.get(0).url());
    }

    @Test public void rejectsHtml() {
        try {
            new M3uParser().parse("<html>Access denied</html>", "https://x.test/list");
            throw new AssertionError("HTML must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void preservesHeadersAndKodiOptionsOnRoundTrip() {
        M3uParser parser = new M3uParser();
        String input = "\uFEFF#EXTM3U\r\n#EXTINF:-1 group-title=\"A, B\" tvg-name=\"Original\",Name, One\r\n"
                + "#KODIPROP:inputstream.adaptive.manifest_type=mpd\r\n"
                + "#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha\r\n"
                + "video.mpd|Origin=https%3A%2F%2Forigin.test&Cookie=a%3Db%2Bc&User-Agent=Demo%2F1\r\n";
        M3uParser.Result a = parser.parse(input, "https://example.test/base/list.m3u");
        M3uParser.Result b = parser.parse(parser.export(a.channels), "");
        assertEquals("Name, One", b.channels.get(0).name());
        assertEquals("A, B", b.channels.get(0).group());
        assertEquals(a.channels.get(0).headers(), b.channels.get(0).headers());
        assertEquals(a.channels.get(0).options(), b.channels.get(0).options());
        assertTrue(b.channels.get(0).needsDrm());
        assertEquals("application/dash+xml", b.channels.get(0).mimeHint());
    }

    @Test public void blankUrlAndInvalidTextAreNotChannels() {
        M3uParser.Result r = new M3uParser().parse("#EXTM3U\n#EXTINF:-1,Empty\n\n#EXTINF:-1,Invalid\nnot a URL\n", "");
        assertEquals(0, r.channels.size());
        assertEquals(2, r.missingUrlCount);
    }

    @Test public void parsesBareNetworkProtocolsAndCrLineEndings() {
        assertEquals(4, new M3uParser().parse("#EXTM3U\rrtsp://192.0.2.1/cam\rudp://239.1.1.1:1234\rrtmp://example.test/live\rhttps://example.test/ch\r", "").channels.size());
    }

    @Test public void preservesUnselectedChannelsForSession() {
        M3uParser parser = new M3uParser();
        M3uParser.Result r = parser.parse("https://example.test/a\nhttps://example.test/b", "");
        r.channels.get(0).setSelected(false);
        assertEquals(2, parser.parse(parser.exportAll(r.channels), "").channels.size());
        assertEquals(1, parser.parse(parser.export(r.channels), "").channels.size());
    }

    @Test public void relativeUrlWithHeadersResolvesBeforeExport() {
        M3uParser.Result r = new M3uParser().parse("#EXTM3U\n#EXTINF:-1,Relative\n../ch/live.m3u8|Referer=https%3A%2F%2Fexample.test\n",
                "https://example.test/folder/list.m3u");
        assertEquals("https://example.test/ch/live.m3u8", r.channels.get(0).url());
        assertEquals("https://example.test", r.channels.get(0).headers().get("Referer"));
    }

    @Test public void headerNamesAreCaseInsensitiveForDuplicates() {
        M3uParser.Result r = new M3uParser().parse(
                "https://example.test/a|Cookie=a%3Db\nhttps://example.test/a|cookie=a%3Db\n", "");
        assertEquals(1, r.channels.size());
        assertEquals(1, r.duplicateCount);
    }

    @Test public void headerValuesCannotCollideWithMapSeparators() {
        M3uParser.Result r = new M3uParser().parse(
                "https://example.test/a|X=a%2C%20Y%3Db\nhttps://example.test/a|X=a&Y=b\n", "");
        assertEquals(2, r.channels.size());
        assertEquals(0, r.duplicateCount);
    }

    @Test public void optionValuesCannotCollideWithListSeparators() {
        M3uParser.Result r = new M3uParser().parse("#EXTM3U\n#EXTINF:-1,A\n"
                + "#KODIPROP:a=a, #KODIPROP:b=b\nhttps://example.test/a\n"
                + "#EXTINF:-1,B\n#KODIPROP:a=a\n#KODIPROP:b=b\nhttps://example.test/a\n", "");
        assertEquals(2, r.channels.size());
    }

    @Test public void unknownVlcOptionsArePreservedOnExport() {
        M3uParser parser = new M3uParser();
        M3uParser.Result r = parser.parse("#EXTM3U\n#EXTINF:-1,A\n"
                + "#EXTVLCOPT:network-caching=1000\nhttps://example.test/a\n", "");
        String out = parser.export(r.channels);
        assertTrue(out.contains("#EXTVLCOPT:network-caching=1000\n"));
        assertEquals(r.channels.get(0).options(), parser.parse(out, "").channels.get(0).options());
    }

    @Test public void preservesPlaylistChannelAndGroupEncounterOrder() {
        M3uParser.Result r = new M3uParser().parse("#EXTM3U\n"
                + "#EXTINF:-1 group-title=\"Nhóm B\",Kênh B1\nhttps://example.test/b1\n"
                + "#EXTINF:-1 group-title=\"Nhóm A\",Kênh A1\nhttps://example.test/a1\n"
                + "#EXTINF:-1 group-title=\"Nhóm B\",Kênh B2\nhttps://example.test/b2\n", "");
        assertEquals("Kênh B1", r.channels.get(0).name());
        assertEquals("Nhóm B", r.channels.get(0).group());
        assertEquals("Kênh A1", r.channels.get(1).name());
        assertEquals("Kênh B2", r.channels.get(2).name());
    }

    @Test public void parsesSuperOkLogoAttributesAndRelativeUrls() {
        String input = "#EXTM3U\n"
                + "#EXTINF:-1 tvg-id=\"v1\" tvg-logo=\"logos/vtv1.webp\" logo=\"https://cdn.example/vtv1.png\",VTV1\n"
                + "https://stream.example/vtv1.m3u8\n"
                + "#EXTINF:-1 tvg-id=\"v2\" tvg-logo=\"https://cdn.example/vtv2.png\",VTV2\n"
                + "https://stream.example/vtv2.m3u8\n"
                + "#EXTINF:-1 tvg-id=\"v3\" icon_url=\"icons/vtv3.png\",VTV3\n"
                + "https://stream.example/vtv3.m3u8\n";

        M3uParser.Result result = new M3uParser().parse(
                input, "https://example.com/playlist/main.m3u");

        assertEquals(3, result.channels.size());

        assertEquals("https://example.com/playlist/logos/vtv1.webp",
                result.channels.get(0).tvgLogo());
        assertEquals("https://cdn.example/vtv1.png",
                result.channels.get(0).logoUrl());
        assertEquals("https://cdn.example/vtv1.png",
                result.channels.get(0).effectiveLogoUrl());

        assertEquals("https://cdn.example/vtv2.png",
                result.channels.get(1).effectiveLogoUrl());

        assertEquals("https://example.com/playlist/icons/vtv3.png",
                result.channels.get(2).iconUrl());
        assertEquals("https://example.com/playlist/icons/vtv3.png",
                result.channels.get(2).effectiveLogoUrl());
    }

    @Test public void parsesAlternateUrlAttributesForTheLiveServerPicker() {
        String input = "#EXTM3U\n"
                + "#EXTINF:-1 group-title=\"LIVE\" backup-url=\"https://backup.example/live.m3u8\" fallback_url=\"https://third.example/live.m3u8\"\n"
                + "https://primary.example/live.m3u8\n";
        M3uParser.Result parsed = new M3uParser().parse(input, "https://example.test/list.m3u");
        assertEquals(1, parsed.channels.size());
        Channel channel = parsed.channels.get(0);
        assertTrue(channel.options().contains("#NM7-ALT-URL:https://backup.example/live.m3u8"));
        assertTrue(channel.options().contains("#NM7-ALT-URL:https://third.example/live.m3u8"));
        List<LiveEvent> events = LiveEvent.fromChannels(parsed.channels);
        assertEquals(1, events.size());
        assertEquals(3, events.get(0).sources().size());
        assertEquals("https://backup.example/live.m3u8", events.get(0).sources().get(1).url());
        assertEquals("https://third.example/live.m3u8", events.get(0).sources().get(2).url());
    }

    @Test public void resolvesRelativeAlternateStreamUrlsAgainstPlaylistUrl() {
        String input = "#EXTM3U\n"
                + "#EXTINF:-1 group-title=\"LIVE\" alt-url=\"backup/stream.m3u8\",Club A vs Club B\n"
                + "https://example.test/live/main.m3u8\n";
        M3uParser.Result parsed = new M3uParser().parse(input, "https://example.test/live/list.m3u");
        assertEquals("#NM7-ALT-URL:https://example.test/live/backup/stream.m3u8",
                parsed.channels.get(0).options().get(0));
    }

    @Test public void readsXmlTvUrlFromPlaylistHeader() {
        M3uParser.Result r = new M3uParser().parse("#EXTM3U url-tvg=\"guide/epg.xml.gz\"\n"
                + "#EXTINF:-1 tvg-id=\"vtv1.vn\",VTV1 HD\nhttps://example.test/live/vtv1.m3u8\n",
                "https://example.test/list/playlist.m3u");
        assertEquals("https://example.test/list/guide/epg.xml.gz", r.epgUrl);
        assertEquals("vtv1.vn", r.channels.get(0).tvgId());
    }
}


