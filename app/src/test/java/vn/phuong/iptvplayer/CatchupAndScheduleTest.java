package vn.phuong.iptvplayer;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class CatchupAndScheduleTest {

    @Test
    public void testM3uCatchupParsing() {
        String m3u = "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"vtv1\" tvg-name=\"VTV1 HD\" catchup=\"append\" catchup-days=\"7\" catchup-source=\"https://example.com/vtv1.m3u8?utc=${start}&lutc=${lutc}\",VTV1 HD\n" +
                "https://example.com/vtv1.m3u8\n";

        M3uParser.Result result = new M3uParser().parse(m3u, "https://example.com/playlist.m3u");
        assertEquals(1, result.channels.size());
        Channel channel = result.channels.get(0);

        assertEquals("VTV1 HD", channel.name());
        assertEquals("vtv1", channel.tvgId());
        assertEquals("append", channel.catchupType());
        assertEquals(7, channel.catchupDays());
        assertEquals("https://example.com/vtv1.m3u8?utc=${start}&lutc=${lutc}", channel.catchupSource());
        assertTrue(channel.hasCatchup());
    }

    @Test
    public void testCatchupUrlBuilderWithSource() {
        Channel channel = new Channel("VTV1 HD", "VTV", "https://example.com/vtv1/live.m3u8",
                "", "", "", "vtv1", Collections.emptyMap(),
                "append", 7, "https://example.com/vtv1/live.m3u8?utc=${start}&lutc=${lutc}");

        long startMs = 1761634800000L; // 2025-10-28 07:00:00 GMT
        long endMs = 1761638400000L;   // 2025-10-28 08:00:00 GMT
        EpgStore.Programme prog = new EpgStore.Programme("Thời sự 19h", startMs, endMs);

        String catchupUrl = CatchupUrlBuilder.build(channel, prog);
        assertTrue(catchupUrl.contains("utc=1761634800"));
        assertTrue(catchupUrl.contains("lutc="));
    }

    @Test
    public void testCatchupUrlBuilderFlussonic() {
        Channel channel = new Channel("VTV3 HD", "VTV", "https://flussonic.example.com/vtv3/index.m3u8",
                "", "", "", "vtv3", Collections.emptyMap(),
                "flussonic", 7, "");

        long startMs = 1761634800000L;
        long endMs = 1761638400000L;
        EpgStore.Programme prog = new EpgStore.Programme("Phim truyện", startMs, endMs);

        String catchupUrl = CatchupUrlBuilder.build(channel, prog);
        assertEquals("https://flussonic.example.com/vtv3/timeshift_abs-1761634800.m3u8", catchupUrl);
    }

    @Test
    public void testCatchupUrlBuilderDefaultAppend() {
        Channel channel = new Channel("VTV6", "VTV", "https://stream.example.com/vtv6.m3u8",
                "", "", "", "vtv6", Collections.emptyMap(),
                "append", 3, "");

        long startMs = 1761634800000L;
        long endMs = 1761638400000L;
        EpgStore.Programme prog = new EpgStore.Programme("Thể thao", startMs, endMs);

        String catchupUrl = CatchupUrlBuilder.build(channel, prog);
        assertTrue(catchupUrl.startsWith("https://stream.example.com/vtv6.m3u8?utc=1761634800&lutc="));
    }

    @Test
    public void testEpgStoreSchedules() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long now = cal.getTimeInMillis();

        // XMLTV with 3 programmes
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<tv>\n" +
                "  <channel id=\"vtv1\"><display-name>VTV1</display-name></channel>\n" +
                "  <programme start=\"20261028060000 +0700\" stop=\"20261028070000 +0700\" channel=\"vtv1\">\n" +
                "    <title>Chào buổi sáng</title>\n" +
                "    <desc>Bản tin buổi sáng</desc>\n" +
                "  </programme>\n" +
                "  <programme start=\"20261028070000 +0700\" stop=\"20261028090000 +0700\" channel=\"vtv1\">\n" +
                "    <title>Chương trình đang phát</title>\n" +
                "  </programme>\n" +
                "  <programme start=\"20261028090000 +0700\" stop=\"20261028100000 +0700\" channel=\"vtv1\">\n" +
                "    <title>Phim tài liệu</title>\n" +
                "  </programme>\n" +
                "</tv>";

        ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        EpgStore.Guide guide = EpgStore.parse(in, now);

        Channel channel = new Channel("VTV1", "VTV", "https://example.com/vtv1.m3u8",
                "", "", "", "vtv1", Collections.emptyMap());

        List<EpgStore.Programme> allPrograms = guide.findAllProgrammes(channel);
        assertEquals(3, allPrograms.size());
        assertEquals("Chào buổi sáng", allPrograms.get(0).title);
        assertEquals("Bản tin buổi sáng", allPrograms.get(0).desc);
        assertEquals("Chương trình đang phát", allPrograms.get(1).title);
        assertEquals("Phim tài liệu", allPrograms.get(2).title);

        assertTrue(allPrograms.get(0).isPast(now));
        assertTrue(allPrograms.get(1).isCurrent(now));
        assertTrue(allPrograms.get(2).isFuture(now));
    }
}
