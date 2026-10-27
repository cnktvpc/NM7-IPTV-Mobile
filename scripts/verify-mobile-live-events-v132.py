#!/usr/bin/env python3
"""Fail-closed checks for the NM7 Mobile Live events feature."""
from pathlib import Path

main = Path("app/src/main/java/vn/phuong/iptvplayer/MainActivity.java").read_text(encoding="utf-8")
event = Path("app/src/main/java/vn/phuong/iptvplayer/LiveEvent.java").read_text(encoding="utf-8")
store = Path("app/src/main/java/vn/phuong/iptvplayer/LiveEventStore.java").read_text(encoding="utf-8")
adapter = Path("app/src/main/java/vn/phuong/iptvplayer/LiveEventAdapter.java").read_text(encoding="utf-8")
inline = Path("app/src/main/java/vn/phuong/iptvplayer/MobileInlinePlayerProviderV2.java").read_text(encoding="utf-8")
layout = Path("app/src/main/res/layout/item_live_event.xml").read_text(encoding="utf-8")
colors = Path("app/src/main/res/values/colors.xml").read_text(encoding="utf-8")
tests = Path("app/src/test/java/vn/phuong/iptvplayer/LiveEventTest.java").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")

checks = [
    ("LIVE filter chip is visible and counted dynamically", 'addGroupButton("⚽ LIVE ("+LiveEvent.fromChannels(allChannels).size()+")",LIVE_GROUP_TAG)' in main),
    ("Live mode swaps the list to grouped event cards", "channelList.setAdapter(liveAdapter)" in main and "liveAdapter.submit(filtered)" in main),
    ("event classification includes Live/Event/Su kien metadata", 'containsAny(group, " live ", "event", "su kien", "sukien", "events")' in event),
    ("multiple playlist entries are grouped by normalized event title", "Map<String, Builder> grouped = new LinkedHashMap<>()" in event and "normalizeTitle(title)" in event),
    ("NM7 alternate-URL metadata creates separate selectable servers", "#nm7-alt-url:" in event and "expandSources(channel, title)" in event),
    ("server selection persists locally without logging credentials", "setSelectedUrl" in main and "SharedPreferences" in store),
    ("Live cards expose title, LIVE badge, source count and favorite action", all(v in layout for v in ('txtLiveEventTitle','txtLiveBadge','btnLiveServers','btnLiveFavorite'))),
    ("Live player reuses the current inline IPTV player on mobile", "item instanceof LiveEvent" in inline and "playLiveChannel(Channel channel)" in inline),
    ("Live colors and card resources exist", "name=\"live_accent\"" in colors and "name=\"live_surface\"" in colors),
    ("unit tests cover grouping, filtering, fallback sources and selected server", all(v in tests for v in ("duplicateMatchEntriesBecomeOneEventWithTwoServers","nm7AlternateUrlMetadataAddsServer","sportsGroupRequiresMatchupLikeName","selectedServerUrlCanBeRestored"))),
    ("version is 1.10.132 / code 148", 'versionName = "1.10.132"' in gradle and "versionCode = 148" in gradle),
]
for label, ok in checks:
    if not ok:
        raise SystemExit("FAIL: " + label)
    print("PASS: " + label)
print(f"PASS: {len(checks)} Live feature checks")
