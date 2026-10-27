"""Mobile 1.10.75: fast YouTube playback format path.

Root cause verified against the exact pinned SmartTube/MediaServiceCore source:
normal playback starts VideoInfoService with WEB_EMBED, which can require a web
PoToken and then run optional HLS/subtitle enrichment before MediaItemFormatInfo
is returned to VideoLoaderController. That work blocks ExoPlayer from receiving
its DASH/HLS source.

Patch the actual owner of that work (VideoInfoService), rather than copying its
private fields/methods into YouTubeMediaItemService. Normal playback uses the
existing TV_DOWNGRADED client and skips non-critical enrichment. If Extended HLS
is enabled, the original SmartTube path is retained. If the fast client returns
null/unplayable, the existing MediaItemService fallback to SmartTube's normal
multi-client path remains available.

Thumbnail handling is intentionally untouched and must remain maxresdefault.jpg.
"""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")


def only_match(name: str):
    matches = list(ROOT.rglob(name))
    if len(matches) != 1:
        raise SystemExit(f"v75: expected exactly one {name}, found {len(matches)}")
    return matches[0]


# 1) Add a correctly scoped fast playback API to VideoInfoService.
video_info = only_match("VideoInfoService.java")
s = video_info.read_text(encoding="utf-8")

anchor = """    public VideoInfo getAuthVideoInfo(String videoId, String clickTrackingParams) {
"""
method = """    /** NM7 fast playback path: skip WEB_EMBED/PoToken and non-critical enrichment. */
    public VideoInfo getFastPlaybackVideoInfo(String videoId, String clickTrackingParams) {
        if (videoId == null) {
            return null;
        }

        // Extended HLS deliberately keeps the original SmartTube path because
        // applyFixesIfNeeded() may need to fetch the iOS HLS manifest.
        if (getData().isFormatEnabled(MediaServiceData.FORMATS_EXTENDED_HLS)) {
            return getVideoInfo(videoId, clickTrackingParams);
        }

        AppService.instance().resetClientPlaybackNonce();
        mUseAuth = true;

        // TV_DOWNGRADED avoids the WEB PoToken path while still returning
        // adaptive playback formats for ordinary videos.
        VideoInfo result = getVideoInfo(TV_CLIENT, videoId, clickTrackingParams);
        if (result == null) {
            return null;
        }

        // The player needs transformed regular/adaptive formats. Do not call
        // applyFixesIfNeeded(): its optional HLS/subtitle requests are not on
        // the critical path to first video frame.
        transformFormats(result);
        mIsUnplayable = result.isUnplayable();
        return result;
    }

"""
if s.count(anchor) != 1:
    raise SystemExit("v75: getAuthVideoInfo anchor missing/ambiguous")
s = s.replace(anchor, method + anchor, 1)
video_info.write_text(s, encoding="utf-8")

# 2) Make MediaItemService use that API and keep the existing fallback.
media_item = only_match("YouTubeMediaItemService.java")
s = media_item.read_text(encoding="utf-8")

anchor = """    @Override
    public MediaItemFormatInfo getFormatInfo(String videoId, String clickTrackingParams) {
        return selectPlaybackFormatInfo(videoId, clickTrackingParams);
    }
"""
replacement = """    @Override
    public MediaItemFormatInfo getFormatInfo(String videoId, String clickTrackingParams) {
        // NM7 1.10.75: return a playable format without waiting for the
        // WEB_EMBED + web PoToken + optional enrichment critical path.
        MediaItemFormatInfo fast = getFastPlaybackFormatInfo(videoId, clickTrackingParams);
        if (fast != null && !fast.isUnplayable()) {
            return fast;
        }

        // Preserve SmartTube's original multi-client fallback for restricted,
        // broken, or otherwise unsupported videos.
        return selectPlaybackFormatInfo(videoId, clickTrackingParams);
    }

    private MediaItemFormatInfo getFastPlaybackFormatInfo(String videoId, String clickTrackingParams) {
        if (videoId == null) {
            return null;
        }

        MediaItemFormatInfo cachedFormatInfo = getCachedFormatInfo(videoId);
        if (cachedFormatInfo != null) {
            return cachedFormatInfo;
        }

        checkSigned();

        VideoInfo videoInfo = getVideoInfoService().getFastPlaybackVideoInfo(videoId, clickTrackingParams);
        MediaItemFormatInfo formatInfo = YouTubeMediaItemFormatInfo.from(videoInfo);

        if (formatInfo != null) {
            setCachedFormatInfo(formatInfo, clickTrackingParams);
        }

        return formatInfo;
    }
"""
if s.count(anchor) != 1:
    raise SystemExit("v75: getFormatInfo anchor missing/ambiguous")
s = s.replace(anchor, replacement, 1)
media_item.write_text(s, encoding="utf-8")

# 3) Thumbnail quality guard.
ui = Path("scripts/patch-mobile-ui.py")
if "maxresdefault.jpg" not in ui.read_text(encoding="utf-8"):
    raise SystemExit("v75: maxresdefault thumbnail target missing")


# CI retrigger after correcting the fast-path patch owner.
print("NM7 Mobile 1.10.75 fast playback format path applied to VideoInfoService + YouTubeMediaItemService")
