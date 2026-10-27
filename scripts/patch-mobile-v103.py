"""NM7 Mobile 1.10.103 — targeted YouTube performance / 4K playback correction.

Scope:
- Keep every user-confirmed 1.10.102 UI/lifecycle behavior unchanged.
- Reduce repeated work when switching YouTube tabs by reusing already-loaded section data.
- Reduce SurfaceView timestamp-induced stutter for 4K/high-FPS video on the pinned ExoPlayer 2.10.x renderer.
- Add a guarded 4K decoder-drop watchdog: only after sustained dropped frames, relax the
  manual quality ceiling to 1440p so playback remains usable instead of freezing.
- No IPTV, avatar, status-bar, spinner, mini-player, navigation, or YouTube UI redesign changes.
"""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
COMMON = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common"

def once(s, old, new, label):
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"v103: expected exactly one {label}, found {count}")
    return s.replace(old, new, 1)

# ---------------------------------------------------------------------------
# 1) Browse/tab responsiveness.
# 1.10.102 already caches raw MediaGroups for rows. On a cache hit it still
# re-ran BrowseProcessorManager, which is unnecessary after the first load and
# can make a fast tab swipe feel slow. Keep the same data and rendering, but do
# not repeat the processor work on cache hits.
p = COMMON / "app/presenters/BrowsePresenter.java"
s = p.read_text(encoding="utf-8")
old = """                getView().updateSection(videoGroup);
                mBrowseProcessor.process(videoGroup);
            }
            return;
        }

        Disposable updateAction = groups"""
new = """                getView().updateSection(videoGroup);
                // NM7 1.10.103: cached sections were already processed on first load.
                // Do not repeat BrowseProcessorManager work during tab swipes.
            }
            return;
        }

        Disposable updateAction = groups"""
s = once(s, old, new, "cached-row processor block")
p.write_text(s, encoding="utf-8")

# Add a lightweight grid cache as well. It is only used for sections whose
# first page has already arrived; the existing network path remains unchanged.
s = p.read_text(encoding="utf-8")
anchor = """    private final java.util.Map<Integer, java.util.List<MediaGroup>> mNm7RowsCache = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> mNm7RowsCacheTime = new java.util.HashMap<>();
"""
replacement = """    private final java.util.Map<Integer, java.util.List<MediaGroup>> mNm7RowsCache = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> mNm7RowsCacheTime = new java.util.HashMap<>();
    private static final long NM7_GRID_CACHE_TTL_MS = 45_000L;
    private final java.util.Map<Integer, MediaGroup> mNm7GridCache = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> mNm7GridCacheTime = new java.util.HashMap<>();
"""
s = once(s, anchor, replacement, "BrowsePresenter cache fields")
s = once(s, """        mNm7RowsCache.clear();
        mNm7RowsCacheTime.clear();
        updateCurrentSection();""",
        """        mNm7RowsCache.clear();
        mNm7RowsCacheTime.clear();
        mNm7GridCache.clear();
        mNm7GridCacheTime.clear();
        updateCurrentSection();""",
        "refresh cache clear")
old = """        VideoGroup baseGroup = VideoGroup.from(section, column);
        baseGroup.setAction(VideoGroup.ACTION_REPLACE);
        getView().updateSection(baseGroup);

        if (group == null) {"""
new = """        VideoGroup baseGroup = VideoGroup.from(section, column);
        baseGroup.setAction(VideoGroup.ACTION_REPLACE);

        MediaGroup cachedGrid = mNm7GridCache.get(section.getId());
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

        getView().updateSection(baseGroup);

        if (group == null) {"""
s = once(s, old, new, "grid cache insertion")
old = """                            VideoGroup videoGroup = VideoGroup.from(baseGroup, mediaGroup);
                            appendLocalHistory(videoGroup);
                            getView().updateSection(videoGroup);"""
new = """                            VideoGroup videoGroup = VideoGroup.from(baseGroup, mediaGroup);
                            appendLocalHistory(videoGroup);
                            mNm7GridCache.put(section.getId(), mediaGroup);
                            mNm7GridCacheTime.put(section.getId(), System.currentTimeMillis());
                            getView().updateSection(videoGroup);"""
s = once(s, old, new, "grid cache store")
p.write_text(s, encoding="utf-8")

# ---------------------------------------------------------------------------
# 1b) Faster startup handoff.
# 1.10.102 is functionally correct here; only shorten the poster reveal gate.
# The decoder still has to render a new frame before the poster is removed.
p = PHONE / "playback/PlaybackActivity.java"
s = p.read_text(encoding="utf-8")
s = s.replace("rendered >= mNm7PosterRenderedBaseline + 3", "rendered >= mNm7PosterRenderedBaseline + 1")
s = s.replace("elapsed >= 180L", "elapsed >= 100L")
s = s.replace(">= 120L))", ">= 60L))")
p.write_text(s, encoding="utf-8")

# ---------------------------------------------------------------------------
# 2) 4K/high-FPS SurfaceView timing correction.
# ExoPlayer 2.10.x uses releaseOutputBuffer(timestamp) for SurfaceView. On
# high-FPS 4K streams the Surface can become the pacing bottleneck. For only
# 4K streams at >=50fps, pass 0 so the Surface displays at the earliest feasible
# VSYNC. Other resolutions/frame-rates keep the 1.10.102 path untouched.
p = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/versions/renderer/DebugInfoMediaCodecVideoRenderer.java"
s = p.read_text(encoding="utf-8")
anchor = """    private int mFrameIndex;
    private boolean mIsSetOutputSurfaceWorkaroundEnabled;
"""
replacement = """    private int mFrameIndex;
    private boolean mIsSetOutputSurfaceWorkaroundEnabled;
    private boolean mNm7HighFps4k;
"""
s = once(s, anchor, replacement, "4K renderer fields")
anchor = """    @Override
    protected CodecMaxValues getCodecMaxValues(
"""
method = """    @Override
    protected void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
        super.onInputFormatChanged(newFormat);
        mNm7HighFps4k = newFormat != null
                && (newFormat.height >= 2160 || newFormat.width >= 3840)
                && newFormat.frameRate >= 50f;
    }

"""
s = once(s, anchor, method + anchor, "renderer format hook")
anchor = """    @Override
    protected boolean codecNeedsSetOutputSurfaceWorkaround(String name) {"""
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
s = once(s, anchor, render + anchor, "4K renderer output hook")
p.write_text(s, encoding="utf-8")

# ---------------------------------------------------------------------------
# 3) 4K decoder-drop watchdog in PlaybackActivity.
# Do not lower quality proactively. Only after a real high-resolution stream
# has rendered and the decoder has dropped >=12 frames during the watch window.
# Then cap video at 2560x1440 and clear a stale explicit video override.
p = PHONE / "playback/PlaybackActivity.java"
s = p.read_text(encoding="utf-8")
anchor = """    private boolean mNm7OwnsPlayback;
"""
replacement = """    private boolean mNm7OwnsPlayback;
    private boolean mNm7HighResolutionVideo;
    private boolean mNm74kRecoveryApplied;
    private Runnable mNm74kDropWatchdog;
"""
s = once(s, anchor, replacement, "4K watchdog fields")

anchor = """        mExoPlayerController.setTrackSelector(trackSelector);
"""
replacement = """        mNm7TrackSelector = trackSelector;
        mExoPlayerController.setTrackSelector(trackSelector);
"""
s = once(s, anchor, replacement, "track selector capture")

# Reset watchdog state when a new video is selected.
anchor = """    public void setVideo(Video item) {
        nm7CancelPosterReadyFallback();
"""
replacement = """    public void setVideo(Video item) {
        if (mNm74kDropWatchdog != null) {
            mHandler.removeCallbacks(mNm74kDropWatchdog);
            mNm74kDropWatchdog = null;
        }
        mNm7HighResolutionVideo = false;
        mNm74kRecoveryApplied = false;
        nm7CancelPosterReadyFallback();
"""
s = once(s, anchor, replacement, "4K watchdog reset")

# Add a size listener beside the existing first-frame listener.
anchor = """        mNm7FrameObserver = new com.google.android.exoplayer2.video.VideoListener() {
            @Override public void onRenderedFirstFrame() {
"""
replacement = """        mNm7FrameObserver = new com.google.android.exoplayer2.video.VideoListener() {
            @Override public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                mNm7HighResolutionVideo = width >= 3840 || height >= 2160;
            }

            @Override public void onRenderedFirstFrame() {
"""
s = once(s, anchor, replacement, "4K video-size observer")

anchor = """                mNm7FirstFrameSeenAt = android.os.SystemClock.elapsedRealtime();
"""
replacement = """                mNm7FirstFrameSeenAt = android.os.SystemClock.elapsedRealtime();
                armNm74kDropWatchdog();
"""
s = once(s, anchor, replacement, "4K watchdog arm")

# Insert watchdog helper immediately before ensureNm7PlaybackObserver().
anchor = """    private void ensureNm7PlaybackObserver() {
"""
helper = """    private void armNm74kDropWatchdog() {
        if (mPlayer == null || mNm7Stopped || !mNm7HighResolutionVideo || mNm74kRecoveryApplied) return;
        if (mNm74kDropWatchdog != null) mHandler.removeCallbacks(mNm74kDropWatchdog);
        final PlaybackActivity owner = this;
        final int baseline;
        com.google.android.exoplayer2.decoder.DecoderCounters counters = mPlayer.getVideoDecoderCounters();
        if (counters == null) return;
        counters.ensureUpdated();
        baseline = counters.droppedBufferCount;
        mNm74kDropWatchdog = () -> {
            if (owner.mPlayer == null || owner.mNm7Stopped || owner.mNm74kRecoveryApplied
                    || !owner.mNm7HighResolutionVideo || owner.mNm7TrackSelector == null) return;

            com.google.android.exoplayer2.decoder.DecoderCounters current =
                    owner.mPlayer.getVideoDecoderCounters();
            if (current == null) return;
            current.ensureUpdated();
            int dropped = current.droppedBufferCount - baseline;

            if (dropped >= 12) {
                try {
                    long position = Math.max(0L, owner.mPlayer.getCurrentPosition());
                    owner.mNm7TrackSelector.setParameters(
                            owner.mNm7TrackSelector.buildUponParameters()
                                    .clearSelectionOverrides(com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO)
                                    .setMaxVideoSize(2560, 1440)
                                    .setExceedVideoConstraintsIfNecessary(false));
                    owner.mNm74kRecoveryApplied = true;
                    android.util.Log.w("NM7Playback",
                            "4k_drop_recovery dropped=" + dropped
                                    + " position=" + position
                                    + " -> max 1440p");
                    owner.mPlayer.seekTo(position);
                    owner.mPlayer.setPlayWhenReady(true);
                } catch (RuntimeException error) {
                    android.util.Log.w("NM7Playback", "4k drop recovery failed", error);
                }
            } else {
                // Give a difficult stream one additional observation window.
                owner.mHandler.postDelayed(owner::armNm74kDropWatchdog, 2500L);
            }
        };
        mHandler.postDelayed(mNm74kDropWatchdog, 2500L);
    }

"""
s = once(s, anchor, helper + anchor, "4K watchdog helper")
p.write_text(s, encoding="utf-8")

print("NM7 Mobile 1.10.103 performance/4K patch applied")
