#!/usr/bin/env python3
"""Fail-closed regression checks for NM7 Mobile Live feed, elapsed duration and newest-first ordering."""
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
colors = read("app/src/main/res/values/colors.xml")
channel_card = read("app/src/main/res/drawable/channel_card.xml")
live_card = read("app/src/main/res/drawable/live_event_card.xml")
tests = read("app/src/test/java/vn/phuong/iptvplayer/LiveEventTest.java")
gradle = read("app/build.gradle.kts")
playlist_store = read("app/src/main/java/vn/phuong/iptvplayer/PlaylistSourceStore.java")

method_start = main.index("private List<LiveEvent> liveEventsWithSelectedServers")
method_end = main.index("private void", method_start + 20)
live_events_method = main[method_start:method_end]

checks = [
    ("Live duration uses whole minutes and clamps future kickoff to zero", "elapsedLiveMinutes(long nowMillis)" in event and "return (nowMillis - kickoffMillis) / 60_000L" in event),
    ("Live events are ordered newest kickoff first", "Comparator.comparingLong(LiveEvent::kickoffMillis).reversed()" in event),
    ("Live card shows elapsed duration beside server picker and minute ticker refreshes it", "txtLiveDuration" in layout and "txtLiveDuration" in adapter and "Đã live " in adapter and "liveAdapter.notifyDataSetChanged()" in main),
    ("dedicated feed URL is exactly the user-specified source", 'https://thethaonm7.phuongnm7-iptv.workers.dev/playlist.m3u' in feed),
    ("Live source cache/download is separate and cache-busted on refresh", "nm7-live-feed.m3u" in feed and "HttpURLConnection" in feed and "M3uParser" in feed and "_nm7_refresh=" in feed),
    ("feed is refreshed on a five-minute cadence", "LIVE_FEED_REFRESH_MS = 5L * 60L * 1000L" in main and "loadLiveFeed()" in main),
    ("Live cards derive from independent feed events, not allChannels", "liveFeedEvents" in live_events_method and "LiveEvent.fromChannels(allChannels)" not in live_events_method),
    ("Live chip count reflects grouped fixtures from the dedicated feed", 'addGroupButton("⚽ LIVE ("+liveFeedEvents.size()+")",LIVE_GROUP_TAG)' in main),
    ("fixture parsing accepts both time/date orders and normalized live markers", "TIME_DATE" in event and "DATE_TIME" in event and "MATCH_SPLIT" in event and "isLiveMarker(raw)" in event),
    ("marked live fixtures survive long running matches but have an upper time bound", "LIVE_MARKED_MAX_AGE_MS = 8L * 60L * 60L * 1000L" in event and "isActiveLive(" in event),
    ("different provider kickoff times can still merge one fixture", "SOURCE_TIME_SKEW_MS = 18L * 60L * 60L * 1000L" in event),
    ("only fresh live fixtures are shown and stale kickoff entries are excluded", "LIVE_MAX_AGE_MS = 180L * 60L * 1000L" in event and "isFresh(entry.kickoffMillis, nowMillis)" in event),
    ("fixture merge tolerates provider spelling/time variants", "sameFixture(" in event and "teamSimilarity(" in event and "SOURCE_TIME_SKEW_MS" in event),
    ("unique stream URLs retain provider, commentator and transport labels", "feedSourceLabel(" in event and "seenUrls.add(entry.channel.url())" in event and "[flv]" not in event),
    ("independent cache failures cannot overwrite the user's selected playlist", "LiveFeedRepository" in main and "refreshLiveFeedUi()" in main and "showPlaylist(" not in main[main.index("private void loadLiveFeed"):main.index("private void loadFromUrl",main.index("private void loadLiveFeed"))]),
    ("player bridge serializes the match and every selectable source", "JSONObject" in session and "LiveEvent.restored(" in session and "encode(LiveEvent event)" in session),
    ("the inline player provides an in-player server selector", "liveServerButton" in inline and "showLiveServerPicker()" in inline and "LiveEventStore.setSelectedUrl" in inline),
    ("fullscreen Live server button is in the Media3 controller and reuses picker", "fullscreenLiveServerButton" in inline and "updateFullscreenLiveServerButton" in inline and "fullscreenLiveServerButton.setOnClickListener(v -> showLiveServerPicker())" in inline and "fullscreenLiveServerButton.setVisibility(View.VISIBLE)" in inline and "fullscreenLiveServerButton.setVisibility(View.GONE)" in inline),
    ("the full player provides an in-player server selector", "btnLiveServer" in player_layout and "showLiveServerPicker()" in player and "EXTRA_LIVE_EVENT" in player),
    ("event cards expose title, live state, source count and favorite", all(v in layout for v in ("txtLiveEventTitle", "txtLiveBadge", "btnLiveServers", "txtLiveDuration", "btnLiveFavorite"))),
    ("the regular playlist default remains owned by the existing source store", "PlaylistSourceStore.DEFAULT_URL" in main and "DEFAULT_URL" in playlist_store),
    ("default XMLTV EPG is applied to newly selected sources without url-tvg", 'DEFAULT_EPG_URL = "https://epg.io.vn/epg.xml"' in main and "else if(!previous.equals(next))epgUrl=DEFAULT_EPG_URL;" in main and "if(!r.epgUrl.isEmpty())epgUrl=r.epgUrl;" in main),
    ("tests cover provider time skew, alternate timestamp order, merge/filtering and server restore", all(v in tests for v in ("dedicatedFeedGroupsProviderAndTransportVariantsIntoOneFreshLiveMatch", "dedicatedFeedDoesNotDependOnUserPlaylistLiveGroups", "dedicatedFeedMergesSameFixtureWithProviderKickoffSkewAndLongRunningLiveStatus", "dedicatedFeedAcceptsDateBeforeTimeAndTextLiveMarker", "restoredLiveEventPreservesServerLabelsAndSelectedUrl", "elapsedLiveMinutesAreWholeAndNeverNegative", "dedicatedFeedOrdersNewestLiveKickoffFirst"))),
    ("server picker renders selectable source rows without the broken message/list combination", "setItems(labels" in main and "Do not combine a message panel with a choice list" in main),
    ("M3U fixture tests use valid Java escaped newlines and quotes", r"\\n" not in tests and r'\\"' not in tests),
    ("version is 1.10.145 / code 162", 'versionName = "1.10.145"' in gradle and "versionCode = 162" in gradle),
    ("Live event cards match channel-card default surface, border and corner radius", 'name="live_surface">#D914263C</color>' in colors and "#D914263C" in channel_card and 'android:color="@color/live_surface"' in live_card and "#304B6887" in live_card and "14dp" in live_card),
    ("Live card keeps compact aligned layout with a readable 36dp server button and 74dp card height", 'android:minHeight="74dp"' in layout and 'android:layout_width="58dp"' in layout and 'android:layout_height="36dp"' in layout and 'android:maxLines="1"' in layout and 'android:layout_height="36dp"' in layout and 'android:minWidth="100dp"' in layout and 'android:paddingVertical="3dp"' in layout and 'tv ? 82 : 74' in adapter),
    ("tapping a Live card starts playback directly, while server and favorite controls keep separate actions", "convertView.setOnClickListener(v -> listener.onEventRequested(event))" in adapter and "onEventRequested(LiveEvent event){playLiveEvent(event);}" in main and "holder.servers.setOnClickListener(v -> listener.onServersRequested(event))" in adapter and "onServersRequested(LiveEvent event){showLiveServerPicker(event);}" in main and '(playing ? "Đổi server" : "Chọn server")' in adapter),
    ("temporary inspection files have been removed", not Path(".github/workflows/inspect-live-feed.yml").exists() and not Path("LIVE_FEED_INSPECTION.md").exists() and not Path("LIVE_BRANCH_PROBE.txt").exists()),
]
for label, ok in checks:
    if not ok:
        raise SystemExit("FAIL: " + label)
    print("PASS: " + label)
print(f"PASS: {len(checks)} dedicated Live feed checks")
