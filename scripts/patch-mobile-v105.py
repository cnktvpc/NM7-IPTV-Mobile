"""NM7 Mobile 1.10.105 — targeted YouTube network/Browse/4K performance pass.

This pass does not change avatar, portrait status-bar handling, spinner suppression,
IPTV ownership/navigation, or the tested poster lifecycle.

Changes:
- Remove the v104 post-start 4K hard cap; use decoder fallback support instead.
- Make Browse RecyclerView reuse cheaper: larger view cache, no item-change animations,
  explicit recycled view pool, and stronger initial prefetch.
- Keep format lookup on the existing fast path; add a very short in-memory format-info
  reuse window only for the same video to avoid duplicate requests during rapid
  open/reopen transitions. Signed URLs are never persisted to disk.
"""
from pathlib import Path
import re

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
COMMON = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common"

# 1) Remove the v104 4K post-start bitrate/fps constraint. It was not sufficient
# on the user's device and can force a poor representation after startup.
p = PHONE / "playback/PlaybackActivity.java"
s = p.read_text(encoding="utf-8")
s = re.sub(
    r"(?ms)^    private boolean mNm74kSmoothProfileApplied;\n",
    "",
    s,
)
s = s.replace("        mNm74kSmoothProfileApplied = false;\n", "")
block = re.compile(
    r"(?ms)^            @Override public void onVideoSizeChanged\(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio\) \{\n"
    r"                applyNm74kSmoothProfileIfNeeded\(width, height\);\n"
    r"            \}\n\n"
)
s, n = block.subn("", s, count=1)
if n == 0:
    raise SystemExit("v105: 4K video-size profile hook not found")
helper = re.compile(r"(?ms)^    private void applyNm74kSmoothProfileIfNeeded\(int width, int height\) \{.*?^    \}\n\n")
s, n = helper.subn("", s, count=1)
if n == 0:
    raise SystemExit("v105: 4K smooth helper not found")
p.write_text(s, encoding="utf-8")

# 2) Keep the upstream decoder factory unchanged. The v105 4K change is intentionally
# limited to removing the v104 post-start constraint; decoder selection is measured
# on-device rather than guessed from a codec/device model.

# 3) Browse: reduce RecyclerView main-thread animation/rebind work.
p = PHONE / "browse/BrowseActivity.java"
s = p.read_text(encoding="utf-8")
s = s.replace("mGridView.setItemViewCacheSize(10);", "mGridView.setItemViewCacheSize(12);")
s = s.replace("setInitialPrefetchItemCount(8);", "setInitialPrefetchItemCount(10);")
anchor = "mGridView.setHasFixedSize(true);"
if anchor not in s:
    raise SystemExit("v105: Browse RecyclerView anchor missing")
insert = """mGridView.setHasFixedSize(true);
mGridView.setItemAnimator(null);
androidx.recyclerview.widget.RecyclerView.RecycledViewPool nm7Pool =
        new androidx.recyclerview.widget.RecyclerView.RecycledViewPool();
nm7Pool.setMaxRecycledViews(0, 16);
mGridView.setRecycledViewPool(nm7Pool);"""
s = s.replace(anchor, insert, 1)
p.write_text(s, encoding="utf-8")

# 4) Add a tiny process-local format-info reuse layer in the existing fast path.
# This is deliberately 8 entries / 8 seconds: it is not a persistent signed-URL cache.
matches = list(ROOT.rglob("YouTubeMediaItemService.java"))
if len(matches) != 1:
    raise SystemExit(f"v105: expected one YouTubeMediaItemService.java, found {len(matches)}")
p = matches[0]
s = p.read_text(encoding="utf-8")
if "NM7_FORMAT_REUSE_MS" not in s:
    marker = "public class YouTubeMediaItemService"
    pos = s.find(marker)
    if pos < 0:
        raise SystemExit("v105: YouTubeMediaItemService class anchor missing")
    brace = s.find("{", pos)
    fields = """
    // NM7 1.10.105: tiny process-local reuse window for rapid open/reopen of the
    // same video. Never persisted; signed stream URLs are discarded after 8s.
    private static final long NM7_FORMAT_REUSE_MS = 8_000L;
    private static final int NM7_FORMAT_REUSE_MAX = 8;
    private final java.util.LinkedHashMap<String, Object[]> mNm7FormatReuse =
            new java.util.LinkedHashMap<String, Object[]>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Object[]> e) {
                    return size() > NM7_FORMAT_REUSE_MAX;
                }
            };

    private MediaItemFormatInfo nm7ReuseGet(String videoId) {
        synchronized (mNm7FormatReuse) {
            Object[] v = mNm7FormatReuse.get(videoId);
            if (v == null || android.os.SystemClock.elapsedRealtime() - ((Long) v[1]) > NM7_FORMAT_REUSE_MS) {
                if (v != null) mNm7FormatReuse.remove(videoId);
                return null;
            }
            return (MediaItemFormatInfo) v[0];
        }
    }

    private void nm7ReusePut(String videoId, MediaItemFormatInfo info) {
        if (videoId == null || info == null) return;
        synchronized (mNm7FormatReuse) {
            mNm7FormatReuse.put(videoId, new Object[] { info, android.os.SystemClock.elapsedRealtime() });
        }
    }

"""
    s=s[:brace+1]+fields+s[brace+1:]

old="""        MediaItemFormatInfo cachedFormatInfo = getCachedFormatInfo(videoId);
        if (cachedFormatInfo != null) {
            return cachedFormatInfo;
        }

        checkSigned();
"""
new="""        MediaItemFormatInfo cachedFormatInfo = getCachedFormatInfo(videoId);
        if (cachedFormatInfo != null) {
            return cachedFormatInfo;
        }
        MediaItemFormatInfo nm7Reuse = nm7ReuseGet(videoId);
        if (nm7Reuse != null) {
            return nm7Reuse;
        }

        checkSigned();
"""
if old not in s:
    raise SystemExit("v105: fast format cache anchor missing")
s=s.replace(old,new,1)
old2="""        if (formatInfo != null) {
            setCachedFormatInfo(formatInfo, clickTrackingParams);
        }

        return formatInfo;
"""
new2="""        if (formatInfo != null) {
            setCachedFormatInfo(formatInfo, clickTrackingParams);
            nm7ReusePut(videoId, formatInfo);
        }

        return formatInfo;
"""
if old2 not in s:
    raise SystemExit("v105: fast format store anchor missing")
s=s.replace(old2,new2,1)
p.write_text(s, encoding="utf-8")

print("NM7 Mobile 1.10.105 targeted performance patch applied")
