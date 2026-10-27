#!/usr/bin/env python3
"""Fail-closed regression checks for the dedicated NM7 Mobile Live feed."""
from pathlib import Path

read = lambda p: Path(p).read_text(encoding="utf-8")
main = read("app/src/main/java/vn/phuong/iptvplayer/MainActivity.java")
event = read("app/src/main/java/vn/phuong/iptvplayer/LiveEvent.java")
feed = read("app/src/main/java/vn/phuong/iptvplayer/LiveFeedRepository.java")
session = read("app/src/main/java/vn/phuong/iptvplayer/LivePlaybackSession.java")
adapter = read("app/src/main/java/vn/phuong/iptvplayer/LiveEventAdapter.java")
inline = read("app/src/main/java/vn/phuong/iptvplayer/MobileInlinePlayerProviderV2.java")
player = read("app/src/main/java/vn/phuong/iptvplayer/PlayerActivity.java")
player_layout = read("app/src/main/res/layout/activity_player.xml")
layout = read("app/src/main/res/layout/item_live_event.xml")
tests = read("app/src/test/java/vn/phuong/iptvplayer/LiveEventTest.java")
gradle = read("app/build.gradle.kts")
playlist_store = read("app/src/main/java/vn/phuong/iptvplayer/PlaylistSourceStore.java")

method_start = main.index("private List<LiveEvent> liveEventsWithSelectedServers")
method_end = main.index("private void", method_start + 20)
live_events_method = main[method_start:method_end]

checks = [
    ("dedicated feed URL is exactly the user-specified source", 'https://thethaonm7.phuongnm7-iptv.workers.dev/playlist.m3u' in feed),
    ("Live source cache/download is a separate repository", "nm7-live-feed.m3u" in feed and "HttpURLConnection" in feed and "M3uParser" in feed),
    ("feed is refreshed on a five-minute cadence", "LIVE_FEED_REFRESH_MS = 5L * 60L * 1000L" in main and "loadLiveFeed()" in main),
    ("Live cards derive from independent feed events, not allChannels", "liveFeedEvents" in live_events_method and "LiveEvent.fromChannels(allChannels)" not in live_events_method),
    ("Live chip count reflects grouped fixtures from the dedicated feed", 'addGroupButton("⚽ LIVE ("+liveFeedEvents.size()+")",LIVE_GROUP_TAG)' in main),
    ("fixture parsing requires schedule, matchup and a live marker", "TIME_DATE" in event and "MATCH_SPLIT" in event and 'raw.contains("🟢")' in event),
    ("only fresh live fixtures are shown and stale kickoff entries are excluded", "LIVE_MAX_AGE_MS = 180L * 60L * 1000L" in event and "isFresh(entry.kickoffMillis, nowMillis)" in event),
    ("fixture merge tolerates provider spelling/time variants", "sameFixture(" in event and "teamSimilarity(" in event and "SOURCE_TIME_SKEW_MS" in event),
    ("unique stream URLs retain provider, commentator and transport labels", "feedSourceLabel(" in event and "seenUrls.add(entry.channel.url())" in event and "[flv]" not in event),
    ("independent cache failures cannot overwrite the user's selected playlist", "LiveFeedRepository" in main and "refreshLiveFeedUi()" in main and "showPlaylist(" not in main[main.index("private void loadLiveFeed"):main.index("private void loadFromUrl",main.index("private void loadLiveFeed"))]),
    ("player bridge serializes the match and every selectable source", "JSONObject" in session and "LiveEvent.restored(" in session and "encode(LiveEvent event)" in session),
    ("the inline player provides an in-player server selector", "liveServerButton" in inline and "showLiveServerPicker()" in inline and "LiveEventStore.setSelectedUrl" in inline),
    ("the full player provides an in-player server selector", "btnLiveServer" in player_layout and "showLiveServerPicker()" in player and "EXTRA_LIVE_EVENT" in player),
    ("event cards expose title, live state, source count and favorite", all(v in layout for v in ("txtLiveEventTitle", "txtLiveBadge", "btnLiveServers", "btnLiveFavorite"))),
    ("the regular playlist default remains owned by the existing source store", "PlaylistSourceStore.DEFAULT_URL" in main and "DEFAULT_URL" in playlist_store),
    ("tests cover merging/filtering and player server restore", all(v in tests for v in ("dedicatedFeedGroupsProviderAndTransportVariantsIntoOneFreshLiveMatch", "dedicatedFeedDoesNotDependOnUserPlaylistLiveGroups", "restoredLiveEventPreservesServerLabelsAndSelectedUrl"))),
    ("version is 1.10.133 / code 149", 'versionName = "1.10.133"' in gradle and "versionCode = 149" in gradle),
    ("temporary inspection files have been removed", not Path(".github/workflows/inspect-live-feed.yml").exists() and not Path("LIVE_FEED_INSPECTION.md").exists() and not Path("LIVE_BRANCH_PROBE.txt").exists()),
]
for label, ok in checks:
    if not ok:
        raise SystemExit("FAIL: " + label)
    print("PASS: " + label)
print(f"PASS: {len(checks)} dedicated Live feed checks")
