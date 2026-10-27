"""NM7 Mobile 1.10.104 — rollback unsafe v103 render/cache changes and apply conservative performance tuning.

Goals:
- Restore 1.10.102 playback/render behavior; do not alter avatar/status-bar/spinner.
- Remove v103 SurfaceView timestamp override and 4K seek/drop watchdog.
- Remove v103 Browse grid cache / cached-processor shortcut that regressed swipes.
- Keep Browse RecyclerView warm with modest cache/prefetch.
- For confirmed 4K playback, constrain frame rate to 30fps and bitrate to 24 Mbps
  after the first 4K frame, preserving 4K resolution when a suitable 4K30 track exists.
"""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
COMMON = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common"

# 1) Restore the exact 1.10.102 BrowsePresenter path by removing only v103 additions.
p = COMMON / "app/presenters/BrowsePresenter.java"
s = p.read_text(encoding="utf-8")
s = s.replace(
"""    private static final long NM7_GRID_CACHE_TTL_MS = 45_000L;
    private final java.util.Map<Integer, MediaGroup> mNm7GridCache = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> mNm7GridCacheTime = new java.util.HashMap<>();
""", "")
s = s.replace("""        mNm7GridCache.clear();
        mNm7GridCacheTime.clear();
""", "")
cached = """        MediaGroup cachedGrid = mNm7GridCache.get(section.getId());
        Long cachedGridAt = mNm7GridCacheTime.get(section.getId());
        if (cachedGrid != null && cachedGridAt != null
                && (cachedGrid.getNextPageKey() == null || cachedGrid.getNextPageKey().isEmpty())
                && System.currentTimeMillis() - cachedGridAt < NM7_GRID_CACHE_TTL_MS) {
            getView().showProgressBar(false);
            VideoGroup cachedGroup = VideoGroup.from(baseGroup, cachedGrid);
            getView().updateSection(cachedGroup);
            // First-load processor state is already established; don't repeat it on a swipe.
            return;
        }

"""
if cached in s:
    s = s.replace(cached, "")
s = s.replace("""                            mNm7GridCache.put(section.getId(), mediaGroup);
                            mNm7GridCacheTime.put(section.getId(), System.currentTimeMillis());
""", "")
s = s.replace("""                getView().updateSection(videoGroup);
                // NM7 1.10.103: cached sections were already processed on first load.
                // Do not repeat BrowseProcessorManager work during tab swipes.
            }
""", """                getView().updateSection(videoGroup);
                mBrowseProcessor.process(videoGroup);
            }
""")
p.write_text(s, encoding="utf-8")

# 2) Remove v103's poster-gate shortening; preserve the 1.10.102 timing.
p = PHONE / "playback/PlaybackActivity.java"
s = p.read_text(encoding="utf-8")
s = s.replace("rendered >= mNm7PosterRenderedBaseline + 1", "rendered >= mNm7PosterRenderedBaseline + 3")
s = s.replace("elapsed >= 100L", "elapsed >= 180L")
s = s.replace(">= 60L))", ">= 120L))")

# 3) Remove the v103 4K watchdog fields/helper and reset/arm hooks.
s = s.replace("""    private boolean mNm7HighResolutionVideo;
    private boolean mNm74kRecoveryApplied;
    private Runnable mNm74kDropWatchdog;
""", "")
s = s.replace("""        if (mNm74kDropWatchdog != null) {
            mHandler.removeCallbacks(mNm74kDropWatchdog);
            mNm74kDropWatchdog = null;
        }
        mNm7HighResolutionVideo = false;
        mNm74kRecoveryApplied = false;
""", "")
s = s.replace("""            @Override public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                mNm7HighResolutionVideo = width >= 3840 || height >= 2160;
            }

""", "")
s = s.replace("""                armNm74kDropWatchdog();
""", "")
start = s.find("    private void armNm74kDropWatchdog() {")
if start >= 0:
    end = s.find("    private void ensureNm7PlaybackObserver() {", start)
    if end < 0:
        raise SystemExit("v104: watchdog end anchor missing")
    s = s[:start] + s[end:]

# 4) Remove the v103 renderer timestamp override and state.
p.write_text(s, encoding="utf-8")
p = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/versions/renderer/DebugInfoMediaCodecVideoRenderer.java"
r = p.read_text(encoding="utf-8")
r = r.replace("""    private boolean mNm7HighFps4k;
""", "")
r = r.replace("""    @Override
    protected void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
        super.onInputFormatChanged(newFormat);
        mNm7HighFps4k = newFormat != null
                && (newFormat.height >= 2160 || newFormat.width >= 3840)
                && newFormat.frameRate >= 50f;
    }

""", "")
render = """    @Override
    protected void renderOutputBufferV21(
            MediaCodec codec, int index, long presentationTimeUs, long releaseTimeNs) {
        if (mNm7HighFps4k) {
            // SurfaceView can pace high-FPS 4K frames too aggressively when a
            // timestamp is supplied. Let the surface present the next frame
            // at the earliest feasible VSYNC.
            super.renderOutputBufferV21(codec, index, presentationTimeUs, 0);
        } else {
            super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
        }
    }

"""
if render in r:
    r = r.replace(render, "")
p.write_text(r, encoding="utf-8")

# 5) Conservative Browse warm-cache tuning. No data cache; only RecyclerView reuse.
b = PHONE / "browse/BrowseActivity.java"
bs = b.read_text(encoding="utf-8")
bs = bs.replace("mGridView.setItemViewCacheSize(6);", "mGridView.setItemViewCacheSize(10);")
bs = bs.replace("setInitialPrefetchItemCount(6);", "setInitialPrefetchItemCount(8);")
b.write_text(bs, encoding="utf-8")

# 6) 4K smooth profile: preserve 4K resolution but avoid 4K60/very-high bitrate
# decoder/network pressure on phones. Apply only once the actual video is confirmed 4K.
# Do not seek, do not force a restart, and do not touch normal resolutions.
p = PHONE / "playback/PlaybackActivity.java"
s = p.read_text(encoding="utf-8")
if "mNm74kSmoothProfileApplied" not in s:
    anchor = "    private boolean mNm7OwnsPlayback;\n"
    if s.count(anchor) != 1:
        raise SystemExit("v104: playback field anchor missing")
    s = s.replace(anchor, anchor + "    private boolean mNm74kSmoothProfileApplied;\n", 1)

anchor = """        mNm7FirstFrameRendered = false;
"""
if s.count(anchor) < 1:
    raise SystemExit("v104: first-frame reset anchor missing")
s = s.replace(anchor, anchor + "        mNm74kSmoothProfileApplied = false;\n", 1)

anchor = """            @Override public void onRenderedFirstFrame() {
                if (!isNm7CurrentEngine(observed)) return;
"""
if s.count(anchor) != 1:
    raise SystemExit("v104: first-frame observer anchor missing")
replacement = """            @Override public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                applyNm74kSmoothProfileIfNeeded(width, height);
            }

            @Override public void onRenderedFirstFrame() {
                if (!isNm7CurrentEngine(observed)) return;
"""
s = s.replace(anchor, replacement, 1)

anchor = "    private void ensureNm7PlaybackObserver() {\n"
helper = """    private void applyNm74kSmoothProfileIfNeeded(int width, int height) {
        if (mNm74kSmoothProfileApplied || mNm7TrackSelector == null) return;
        if (width < 3840 && height < 2160) return;
        try {
            mNm7TrackSelector.setParameters(
                    mNm7TrackSelector.buildUponParameters()
                            .setMaxVideoFrameRate(30)
                            .setMaxVideoBitrate(24_000_000)
                            .setExceedVideoConstraintsIfNecessary(false));
            mNm74kSmoothProfileApplied = true;
            android.util.Log.i("NM7Playback",
                    "4k_smooth_profile=30fps,max24Mbps width=" + width + " height=" + height);
        } catch (RuntimeException error) {
            android.util.Log.w("NM7Playback", "4k smooth profile failed", error);
        }
    }

"""
if anchor not in s:
    raise SystemExit("v104: observer helper anchor missing")
s = s.replace(anchor, helper + anchor, 1)
p.write_text(s, encoding="utf-8")

print("NM7 Mobile 1.10.104 rollback-safe performance patch applied")
