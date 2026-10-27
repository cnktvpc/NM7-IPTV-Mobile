package vn.phuong.iptvplayer;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class LiveEventTest {
    private Channel channel(String name, String group, String url) {
        return new Channel(name, group, url, "", "", Collections.emptyMap());
    }

    @Test public void explicitLiveAndEventGroupsAreCollectedButOrdinaryChannelsAreNot() {
        Channel live = channel("Real Sociedad vs Dep. La Coruna", "LIVE", "https://one.example/live.m3u8");
        Channel event = channel("Rio Ave vs CD Nacional", "Sự kiện FPT PLAY", "https://two.example/live.m3u8");
        Channel television = channel("VTV1 HD", "VTV", "https://tv.example/vtv1.m3u8");
        Channel sportsChannel = channel("ON Sports HD", "Thể Thao", "https://tv.example/sport.m3u8");

        List<LiveEvent> events = LiveEvent.fromChannels(Arrays.asList(live, event, television, sportsChannel));

        assertEquals(2, events.size());
        assertEquals("Real Sociedad vs Dep. La Coruna", events.get(0).title());
        assertEquals("Rio Ave vs CD Nacional", events.get(1).title());
    }

    @Test public void duplicateMatchEntriesBecomeOneEventWithTwoServers() {
        Channel main = channel("Real Sociedad vs Dep. La Coruna - Server 1", "LIVE", "https://one.example/live.m3u8");
        Channel backup = channel("Real Sociedad vs Dep. La Coruna - Server 2", "LIVE", "https://two.example/live.m3u8");

        List<LiveEvent> events = LiveEvent.fromChannels(Arrays.asList(main, backup));

        assertEquals(1, events.size());
        assertEquals("Real Sociedad vs Dep. La Coruna", events.get(0).title());
        assertEquals(2, events.get(0).sources().size());
        assertEquals("https://one.example/live.m3u8", events.get(0).primary().url());
    }

    @Test public void nm7AlternateUrlMetadataAddsServerWithoutLeakingDirectiveIntoPlaybackOptions() {
        Channel channel = channel("Rio Ave vs CD Nacional", "LIVE", "https://one.example/live.m3u8");
        channel.options().add("#NM7-ALT-URL:https://two.example/live.m3u8");
        channel.options().add("#KODIPROP:inputstream.adaptive.manifest_type=hls");

        List<LiveEvent> events = LiveEvent.fromChannels(Collections.singletonList(channel));

        assertEquals(1, events.size());
        assertEquals(2, events.get(0).sources().size());
        Channel backup = events.get(0).sources().get(1);
        assertEquals("https://two.example/live.m3u8", backup.url());
        assertFalse(backup.options().stream().anyMatch(o -> o.toLowerCase().startsWith("#nm7-alt-url")));
        assertTrue(backup.options().contains("#KODIPROP:inputstream.adaptive.manifest_type=hls"));
    }

    @Test public void sportsGroupRequiresMatchupLikeNameUnlessExplicitlyMarkedLive() {
        assertFalse(LiveEvent.isLiveChannel(channel("ON Sports HD", "Thể Thao", "https://one.example/a.m3u8")));
        assertTrue(LiveEvent.isLiveChannel(channel("Club A vs Club B", "Thể Thao", "https://one.example/a.m3u8")));
        assertTrue(LiveEvent.isLiveChannel(channel("ON Sports HD", "LIVE", "https://one.example/a.m3u8")));
    }

    @Test public void selectedServerUrlCanBeRestored() {
        Channel one = channel("Club A vs Club B", "LIVE", "https://one.example/a.m3u8");
        Channel two = channel("Club A vs Club B", "LIVE", "https://two.example/a.m3u8");
        LiveEvent event = LiveEvent.fromChannels(Arrays.asList(one, two)).get(0).withPreferredUrl(two.url());

        assertEquals(1, event.selectedIndex());
        assertEquals(two.url(), event.primary().url());
        LiveEvent fresh = LiveEvent.fromChannels(Arrays.asList(one, two)).get(0);
        assertEquals(0, fresh.withPreferredUrl("https://unknown.example/a.m3u8").selectedIndex());
    }

    @Test public void elapsedLiveMinutesAreWholeAndNeverNegative() {
        Channel source = channel("Team A vs Team B", "Provider", "https://duration.example/live.m3u8");
        LiveEvent event = LiveEvent.restored("duration", "Team A vs Team B", "Bóng đá",
                Collections.singletonList(source), Collections.singletonList("Provider"),
                1_000L, "Bóng đá", source.url());

        assertNotNull(event);
        assertEquals(0L, event.elapsedLiveMinutes(1_000L + 59_999L));
        assertEquals(30L, event.elapsedLiveMinutes(1_000L + 30L * 60_000L));
        assertEquals(125L, event.elapsedLiveMinutes(1_000L + 125L * 60_000L));
        assertEquals(0L, event.elapsedLiveMinutes(500L));
    }

    @Test public void dedicatedFeedOrdersNewestLiveKickoffFirst() {
        String m3u = "#EXTM3U\n"
                + "#EXTINF:-1 group-title=\"Provider A\", 🟢 00:10 12/10 ⚽ Match Earlier vs Opponent A [hls]\n"
                + "https://stream.example/earlier.m3u8\n"
                + "#EXTINF:-1 group-title=\"Provider B\", 🟢 00:15 12/10 ⚽ Match Newest vs Opponent B [hls]\n"
                + "https://stream.example/newest.m3u8\n";
        long now = java.time.LocalDateTime.of(2026, 10, 12, 0, 30)
                .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        List<Channel> channels = new M3uParser().parse(m3u, LiveFeedRepository.FEED_URL).channels;

        List<LiveEvent> events = LiveEvent.fromDedicatedFeed(channels, now);

        assertEquals(2, events.size());
        assertTrue("The match with the latest kickoff should be first",
                events.get(0).title().contains("Match Newest"));
        assertTrue(events.get(0).kickoffMillis() > events.get(1).kickoffMillis());
        assertTrue(events.get(1).title().contains("Match Earlier"));
    }

    @Test public void dedicatedFeedGroupsProviderAndTransportVariantsIntoOneFreshLiveMatch() {
        String m3u = "#EXTM3U\n"
                + "#EXTINF:-1 tvg-logo=\"https://logo.example/rennes.png\" group-title=\"Vua Sân Cỏ TV\", 🟢 22:15 11/10 ⚽ Stade Rennais FC vs AJ Auxerre (Trần Hoàng)\n"
                + "https://stream.example/rennes-primary.m3u8\n"
                + "#EXTINF:-1 tvg-logo=\"https://logo.example/rennes2.png\" group-title=\"Cola TV\", 🟡 22:15 11/10 ⚽ Rennes vs Auxerre (BLV POCARI) [hls]\n"
                + "https://stream.example/rennes-hls.m3u8\n"
                + "#EXTINF:-1 group-title=\"Cola TV\", 🟡 22:15 11/10 ⚽ Rennes vs Auxerre (BLV POCARI) [flv]\n"
                + "https://stream.example/rennes-flv.m3u8\n"
                + "#EXTINF:-1 group-title=\"Vua Sân Cỏ TV\", 🟢 20:00 10/10 ⚽ Old Team vs Old Opponent (BLV A)\n"
                + "https://stream.example/old-match.m3u8\n"
                + "#EXTINF:-1 group-title=\"Sút Bóng TV\", 🟡 02:00 12/10 ⚽ Future Team vs Future Opponent (BLV B)\n"
                + "https://stream.example/future-match.m3u8\n";
        long now = java.time.LocalDateTime.of(2026, 10, 12, 0, 30)
                .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        List<Channel> channels = new M3uParser().parse(m3u, LiveFeedRepository.FEED_URL).channels;

        List<LiveEvent> events = LiveEvent.fromDedicatedFeed(channels, now);

        assertEquals(1, events.size());
        LiveEvent match = events.get(0);
        assertTrue(match.title().contains("vs"));
        assertEquals(3, match.sources().size());
        assertTrue(match.sourceLabel(1).contains("Cola TV"));
        assertTrue(match.sourceLabel(1).contains("HLS"));
        assertTrue(match.hasSourceUrl("https://stream.example/rennes-flv.m3u8"));
        assertFalse(match.hasSourceUrl("https://stream.example/old-match.m3u8"));
        assertFalse(match.hasSourceUrl("https://stream.example/future-match.m3u8"));
    }

    @Test public void dedicatedFeedDoesNotDependOnUserPlaylistLiveGroups() {
        String m3u = "#EXTM3U\n"
                + "#EXTINF:-1 group-title=\"Provider A\", 🟢 23:30 11/10 ⚽ Liverpool vs Manchester City (BLV C2) [hls]\n"
                + "https://stream.example/liverpool-hls.m3u8\n"
                + "#EXTINF:-1 group-title=\"Provider B\", 🟢 23:30 11/10 ⚽ Liverpool vs Manchester City (BLV GIÀ LÀNG) [flv]\n"
                + "https://stream.example/liverpool-flv.m3u8\n";
        long now = java.time.LocalDateTime.of(2026, 10, 12, 0, 20)
                .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        List<Channel> channels = new M3uParser().parse(m3u, LiveFeedRepository.FEED_URL).channels;

        List<LiveEvent> events = LiveEvent.fromDedicatedFeed(channels, now);

        assertEquals(1, events.size());
        assertEquals(2, events.get(0).sources().size());
        assertEquals("Bóng đá", events.get(0).group());
    }

    @Test public void dedicatedFeedRecognizesBasketballEvenWhenProviderUsesFootballEmoji() {
        String m3u = "#EXTM3U\n"
                + "#EXTINF:-1 group-title=\"Cola TV\", 🟢 00:10 12/10 ⚽ Cleveland Cavaliers vs Orlando Magic (BLV A) [hls]\n"
                + "https://stream.example/cavs.m3u8\n"
                + "#EXTINF:-1 group-title=\"Vua Sân Cỏ TV\", 🟢 00:15 12/10 ⚽ Charlotte Hornets vs Milwaukee Bucks (BLV B) [hls]\n"
                + "https://stream.example/hornets.m3u8\n";
        long now = java.time.LocalDateTime.of(2026, 10, 12, 0, 30)
                .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        List<Channel> channels = new M3uParser().parse(m3u, LiveFeedRepository.FEED_URL).channels;

        List<LiveEvent> events = LiveEvent.fromDedicatedFeed(channels, now);

        assertEquals(2, events.size());
        assertEquals("Bóng rổ", events.get(0).sport());
        assertEquals("Bóng rổ", events.get(1).group());
    }

    @Test public void dedicatedFeedMergesAbbreviatedAndFullAtleticoSanLuisNames() {
        String m3u = "#EXTM3U\n"
                + "#EXTINF:-1 group-title=\"Provider A\", 🟢 00:10 12/10 ⚽ Atl. San Luis vs Santos Laguna (BLV A) [hls]\n"
                + "https://stream.example/atlsanluis-a.m3u8\n"
                + "#EXTINF:-1 group-title=\"Provider B\", 🟡 00:15 12/10 ⚽ Atletico San Luis vs Santos Laguna (BLV B) [flv]\n"
                + "https://stream.example/atlsanluis-b.m3u8\n";
        long now = java.time.LocalDateTime.of(2026, 10, 12, 0, 30)
                .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        List<Channel> channels = new M3uParser().parse(m3u, LiveFeedRepository.FEED_URL).channels;

        List<LiveEvent> events = LiveEvent.fromDedicatedFeed(channels, now);

        assertEquals(1, events.size());
        assertEquals(2, events.get(0).sources().size());
    }

    @Test public void dedicatedFeedMergesSameFixtureWithProviderKickoffSkewAndLongRunningLiveStatus() {
        String m3u = "#EXTM3U\n"
                + "#EXTINF:-1 group-title=\"Provider A\", 🟢 00:10 12/10 ⚽ Atl. San Luis vs Santos Laguna (BLV A) [hls]\n"
                + "https://stream.example/atlsanluis-a.m3u8\n"
                + "#EXTINF:-1 group-title=\"Provider B\", 🟡 06:00 12/10 ⚽ Atletico San Luis vs Santos Laguna (BLV B) [flv]\n"
                + "https://stream.example/atlsanluis-b.m3u8\n";
        long now = java.time.LocalDateTime.of(2026, 10, 12, 6, 30)
                .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        List<Channel> channels = new M3uParser().parse(m3u, LiveFeedRepository.FEED_URL).channels;

        List<LiveEvent> events = LiveEvent.fromDedicatedFeed(channels, now);

        assertEquals("Provider schedule skew should not split the same fixture", 1, events.size());
        assertEquals("The explicit active source and the fresh backup must both remain selectable",
                2, events.get(0).sources().size());
        assertTrue(events.get(0).hasSourceUrl("https://stream.example/atlsanluis-a.m3u8"));
        assertTrue(events.get(0).hasSourceUrl("https://stream.example/atlsanluis-b.m3u8"));
    }

    @Test public void dedicatedFeedAcceptsDateBeforeTimeAndTextLiveMarker() {
        String m3u = "#EXTM3U\n"
                + "#EXTINF:-1 group-title=\"Provider A\", [LIVE] 12/10 06:10 ⚽ Team North vs Team South [hls]\n"
                + "https://stream.example/north-south.m3u8\n";
        long now = java.time.LocalDateTime.of(2026, 10, 12, 6, 20)
                .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        List<Channel> channels = new M3uParser().parse(m3u, LiveFeedRepository.FEED_URL).channels;

        List<LiveEvent> events = LiveEvent.fromDedicatedFeed(channels, now);

        assertEquals(1, events.size());
        assertEquals("Team North vs Team South", events.get(0).title());
    }

    @Test public void restoredLiveEventPreservesServerLabelsAndSelectedUrl() {
        Channel first = channel("Real Sociedad vs Dep. La Coruna", "Vua Sân Cỏ TV", "https://one.example/live.m3u8");
        Channel second = channel("Real Sociedad vs Dep. La Coruna", "Cola TV", "https://two.example/live.m3u8");
        LiveEvent original = LiveEvent.restored("id", "Real Sociedad vs Dep. La Coruna", "Bóng đá",
                Arrays.asList(first, second), Arrays.asList("Vua Sân Cỏ TV", "Cola TV • HLS"),
                12345L, "Bóng đá", second.url());
        LiveEvent restored = LivePlaybackSession.decode(LivePlaybackSession.encode(original));

        assertNotNull(restored);
        assertEquals(1, restored.selectedIndex());
        assertEquals("Cola TV • HLS", restored.sourceLabel(1));
        assertEquals(second.url(), restored.primary().url());
    }
}
