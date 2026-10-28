package vn.phuong.iptvplayer;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Collections;

public final class CatchupUrlBuilderTest {
    private Channel channel(String extinf, String url) {
        Channel c = new Channel("Test TV", "Test", url, "", "test.tv", Collections.emptyMap());
        c.setOriginalExtInf(extinf);
        return c;
    }

    @Test public void parsesQuotedCatchupMetadata() {
        CatchupUrlBuilder.Config c = CatchupUrlBuilder.parseExtInf(
                "#EXTINF:-1 tvg-id=\"test.tv\" catchup=\"default\" catchup-days=\"3\" catchup-source=\"https://archive.example/{start}/{duration}.m3u8\",Test TV");
        assertEquals("default", c.mode);
        assertEquals(3, c.days);
        assertTrue(c.source.contains("{start}"));
    }

    @Test public void buildsArchiveUrlFromProgrammeTimesWithoutChangingLiveUrl() {
        String live = "https://live.example/stream.m3u8";
        Channel c = channel("#EXTINF:-1 catchup=\"default\" catchup-days=\"7\" catchup-source=\"https://archive.example/recording?start={start}&end={end}&duration={duration}\",Test TV", live);
        EpgProgram p = new EpgProgram("test.tv", "News", 1793178000000L, 1793179800000L, "");
        String url = CatchupUrlBuilder.build(c,p,p.endMs+1000L);
        assertTrue(url.startsWith("https://archive.example/recording?"));
        assertTrue(url.contains("start=1793178000"));
        assertTrue(url.contains("end=1793179800"));
        assertTrue(url.contains("duration=1800"));
        assertEquals(live,c.url());
    }

    @Test public void appendModeUsesLiveUrlAndAddsQuery() {
        Channel c = channel("#EXTINF:-1 catchup=\"append\" catchup-days=\"2\" catchup-source=\"utc={utc}&lutc={lutc}\",Test TV",
                "https://live.example/stream.m3u8?token=abc");
        EpgProgram p = new EpgProgram("test.tv","Old show",1793178000000L,1793179800000L,"");
        String url = CatchupUrlBuilder.build(c,p,p.endMs+1);
        assertTrue(url.startsWith("https://live.example/stream.m3u8?token=abc&utc="));
        assertTrue(url.contains("&lutc="));
    }

    @Test public void rejectsFutureAndExpiredProgramme() {
        Channel c = channel("#EXTINF:-1 catchup=\"default\" catchup-days=\"1\" catchup-source=\"https://archive.example/{start}\",Test TV",
                "https://live.example/stream.m3u8");
        EpgProgram future = new EpgProgram("test.tv","Future",2000000000000L,2000003600000L,"");
        assertFalse(CatchupUrlBuilder.canReplay(c,future,1900000000000L));
        EpgProgram old = new EpgProgram("test.tv","Old",1700000000000L,1700003600000L,"");
        assertFalse(CatchupUrlBuilder.canReplay(c,old,1900000000000L));
    }
}
