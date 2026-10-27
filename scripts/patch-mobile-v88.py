"""NM7 Mobile 1.10.88: decoder-backed startup poster lifecycle.

1.10.87 proved that some devices don't reliably deliver a per-media
onRenderedFirstFrame()/READY callback on a reused SurfaceView. Probe ExoPlayer's
renderedOutputBufferCount directly and keep a bounded safety timeout so the
thumbnail can never stay on top of a playing video indefinitely.
"""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PLAYBACK = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"

def once(s, old, new, label):
    if s.count(old) != 1:
        raise SystemExit(f"v88: expected exactly one {label}, found {s.count(old)}")
    return s.replace(old, new, 1)

s = PLAYBACK.read_text(encoding="utf-8")

# ---------------------------------------------------------------------------
# State for a polling probe that is independent of ExoPlayer first-frame events.
field = """    private Runnable mNm7PosterReadyFallback;
"""
field2 = """    private Runnable mNm7PosterReadyFallback;
    private Runnable mNm7PosterRenderProbe;
    private int mNm7PosterRenderedBaseline;
    private long mNm7PosterProbeStartedAt;
"""
if "private Runnable mNm7PosterRenderProbe;" not in s:
    s = once(s, field, field2, "poster probe fields")

cancel_anchor = """    private void nm7CancelPosterReadyFallback() {
        if (mNm7PosterReadyFallback != null) {
            mHandler.removeCallbacks(mNm7PosterReadyFallback);
            mNm7PosterReadyFallback = null;
        }
    }

"""
cancel_block = cancel_anchor + """    private void nm7CancelPosterRenderProbe() {
        if (mNm7PosterRenderProbe != null) {
            mHandler.removeCallbacks(mNm7PosterRenderProbe);
            mNm7PosterRenderProbe = null;
        }
    }

"""
if "private void nm7CancelPosterRenderProbe()" not in s:
    s = once(s, cancel_anchor, cancel_block, "poster probe cancel helper")

# Any successful hide must cancel both old and new fallbacks.
old_hide_start = """    private void nm7HideStartupPoster(String reason) {
        nm7CancelPosterReadyFallback();
"""
new_hide_start = """    private void nm7HideStartupPoster(String reason) {
        nm7CancelPosterReadyFallback();
        nm7CancelPosterRenderProbe();
"""
s = once(s, old_hide_start, new_hide_start, "poster hide probe cancellation")

# Probe decoder counters. A rendered buffer is stronger evidence than STATE_READY:
# it means ExoPlayer actually sent video output to the renderer/surface.
helper_anchor = """    private void nm7ArmPosterReadyFallback(final com.google.android.exoplayer2.SimpleExoPlayer observed) {
"""
probe_helper = """    private int nm7RenderedVideoBufferCount() {
        if (mPlayer == null) return 0;
        com.google.android.exoplayer2.decoder.DecoderCounters counters =
                mPlayer.getVideoDecoderCounters();
        if (counters == null) return 0;
        counters.ensureUpdated();
        return counters.renderedOutputBufferCount;
    }

    private void nm7ArmPosterRenderProbe(final String expectedVideoId) {
        nm7CancelPosterRenderProbe();
        if (mNm7StartupPoster == null || mPlayer == null) return;

        mNm7PosterRenderedBaseline = nm7RenderedVideoBufferCount();
        mNm7PosterProbeStartedAt = android.os.SystemClock.elapsedRealtime();

        mNm7PosterRenderProbe = new Runnable() {
            @Override public void run() {
                if (mNm7PosterRenderProbe != this) return;
                if (mNm7StartupPoster == null
                        || mNm7StartupPoster.getVisibility() != View.VISIBLE
                        || mPlayer == null || mNm7Stopped || sNm7SuspendedForIptv
                        || isFinishing() || isDestroyed()) {
                    nm7CancelPosterRenderProbe();
                    return;
                }

                String currentVideoId = mNm7SessionVideo == null
                        ? null : mNm7SessionVideo.videoId;
                if (!android.text.TextUtils.equals(expectedVideoId, currentVideoId)) {
                    nm7CancelPosterRenderProbe();
                    return;
                }

                int rendered = nm7RenderedVideoBufferCount();
                if (rendered < mNm7PosterRenderedBaseline) {
                    // Some renderer recreations reset DecoderCounters.
                    mNm7PosterRenderedBaseline = rendered;
                }

                long elapsed = android.os.SystemClock.elapsedRealtime()
                        - mNm7PosterProbeStartedAt;
                boolean renderedNewFrame = rendered > mNm7PosterRenderedBaseline;
                boolean readyAndPlaying = mPlayer.getPlaybackState() == Player.STATE_READY
                        && mPlayer.getPlayWhenReady()
                        && mPlayer.getPlaybackError() == null;

                if (renderedNewFrame && elapsed >= 120L) {
                    nm7HideStartupPoster("decoder_frame");
                    return;
                }

                // Safety path: never allow a thumbnail overlay to freeze the visible
                // player indefinitely. Usually decoder_frame wins long before this.
                if (readyAndPlaying && elapsed >= 1800L) {
                    nm7HideStartupPoster("ready_safety_timeout");
                    return;
                }
                if (elapsed >= 3200L) {
                    nm7HideStartupPoster("absolute_safety_timeout");
                    return;
                }

                mHandler.postDelayed(this, 80L);
            }
        };
        mHandler.postDelayed(mNm7PosterRenderProbe, 80L);
    }

    private void nm7ArmPosterReadyFallback(final com.google.android.exoplayer2.SimpleExoPlayer observed) {
"""
if "private void nm7ArmPosterRenderProbe(" not in s:
    s = once(s, helper_anchor, probe_helper, "decoder poster probe helper")

# Start the probe for every newly selected video after the session video id is committed.
session_anchor = """        mNm7StartupFrame = false;
        mNm7SessionVideo = item;
        mNm7RecoveryGate.video(item == null ? null : item.videoId);
"""
session_repl = """        mNm7StartupFrame = false;
        mNm7SessionVideo = item;
        if (item != null && mNm7StartupPoster != null
                && mNm7StartupPoster.getVisibility() == View.VISIBLE) {
            nm7ArmPosterRenderProbe(item.videoId);
        }
        mNm7RecoveryGate.video(item == null ? null : item.videoId);
"""
s = once(s, session_anchor, session_repl, "setVideo poster probe start")

# A new selection or engine release must cancel any stale probe.
setvideo_anchor = """    @Override
    public void setVideo(Video item) {
        nm7CancelPosterReadyFallback();
"""
setvideo_repl = """    @Override
    public void setVideo(Video item) {
        nm7CancelPosterReadyFallback();
        nm7CancelPosterRenderProbe();
"""
s = once(s, setvideo_anchor, setvideo_repl, "setVideo probe cancellation")

release_anchor = """    private void releasePlayer() {
        nm7CancelPosterReadyFallback();
"""
release_repl = """    private void releasePlayer() {
        nm7CancelPosterReadyFallback();
        nm7CancelPosterRenderProbe();
"""
s = once(s, release_anchor, release_repl, "release probe cancellation")

PLAYBACK.write_text(s, encoding="utf-8")
print("NM7 Mobile 1.10.88 decoder-backed poster lifecycle applied")
