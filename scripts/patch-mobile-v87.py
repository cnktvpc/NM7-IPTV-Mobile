"""NM7 Mobile 1.10.87: harden portrait status bar and never leave startup poster stuck over playing video."""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
PLAYBACK = PHONE / "playback/PlaybackActivity.java"

def once(s, old, new, label):
    if s.count(old) != 1:
        raise SystemExit(f"v87: expected exactly one {label}, found {s.count(old)}")
    return s.replace(old, new, 1)

def replace_method(text, signature, replacement, label):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f"v87: {label} signature not found")
    override = text.rfind("    @Override\n", 0, start)
    method_start = override if override >= 0 and start - override < 100 else start
    brace = text.find("{", start)
    depth = 0
    end = -1
    for i in range(brace, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        raise SystemExit(f"v87: {label} closing brace not found")
    return text[:method_start] + replacement + text[end:]

s = PLAYBACK.read_text(encoding="utf-8")

# ---------------------------------------------------------------------------
# 1) Status bar: some OEMs keep the window immersive after a previous fullscreen
# request even when WindowInsetsController.show(statusBars()) is called. In portrait,
# explicitly FORCE_NOT_FULLSCREEN and reassert after window focus is regained.
restore = """    private void nm7RestorePortraitBars() {
        if (isLandscape() || isInPIPMode() || isFinishing() || isDestroyed()) return;

        android.view.Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        window.setStatusBarColor(android.graphics.Color.BLACK);

        if (VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsAppearance(
                        0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        if (mRoot != null) mRoot.requestApplyInsets();
    }
"""
s = replace_method(s, "    private void nm7RestorePortraitBars() {", restore, "nm7RestorePortraitBars")

system_ui = """    private void applySystemUi(boolean fullscreen) {
        if (!fullscreen) {
            nm7RestorePortraitBars();
            if (mRoot != null) {
                mRoot.postDelayed(this::nm7RestorePortraitBars, 80L);
                mRoot.postDelayed(this::nm7RestorePortraitBars, 350L);
            }
            return;
        }

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        if (VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
"""
s = replace_method(s, "    private void applySystemUi(boolean fullscreen) {", system_ui, "applySystemUi")

focus_anchor = """    private void toggleFullscreen() {
"""
focus_block = """    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isLandscape() && !isInPIPMode()) {
            nm7RestorePortraitBars();
            if (mRoot != null) {
                mRoot.postDelayed(this::nm7RestorePortraitBars, 100L);
                mRoot.postDelayed(this::nm7RestorePortraitBars, 500L);
            }
        }
    }

    private void toggleFullscreen() {
"""
if "public void onWindowFocusChanged(boolean hasFocus)" not in s:
    s = once(s, focus_anchor, focus_block, "window focus status-bar anchor")

# ---------------------------------------------------------------------------
# 2) Poster: first-frame callbacks are not guaranteed on every same-surface media
# replacement on all ExoPlayer/OEM combinations. Keep the callback, but also clear
# the overlay shortly after a video source reaches READY with a real video format.
field_anchor = """    private ImageView mNm7StartupPoster;
"""
field_repl = """    private ImageView mNm7StartupPoster;
    private Runnable mNm7PosterReadyFallback;
"""
if "private Runnable mNm7PosterReadyFallback;" not in s:
    s = once(s, field_anchor, field_repl, "poster fallback field")

helper_anchor = """    private void ensureNm7PlaybackObserver() {
"""
helper_block = """    private void nm7CancelPosterReadyFallback() {
        if (mNm7PosterReadyFallback != null) {
            mHandler.removeCallbacks(mNm7PosterReadyFallback);
            mNm7PosterReadyFallback = null;
        }
    }

    private void nm7HideStartupPoster(String reason) {
        nm7CancelPosterReadyFallback();
        if (mNm7StartupPoster == null
                || mNm7StartupPoster.getVisibility() != View.VISIBLE) return;
        mNm7StartupFrame = true;
        mNm7FirstFrameRendered = true;
        try {
            Glide.with(PlaybackActivity.this).clear(mNm7StartupPoster);
        } catch (RuntimeException ignored) { }
        mNm7StartupPoster.setImageDrawable(null);
        mNm7StartupPoster.setVisibility(View.GONE);
        if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
        android.util.Log.i("NM7Playback", "startup_poster_hidden reason=" + reason);
        loadNm7CommentPreview();
    }

    private void nm7ArmPosterReadyFallback(final com.google.android.exoplayer2.SimpleExoPlayer observed) {
        nm7CancelPosterReadyFallback();
        if (mNm7StartupPoster == null || observed == null) return;
        final String expectedVideoId = mNm7SessionVideo == null ? null : mNm7SessionVideo.videoId;
        mNm7PosterReadyFallback = () -> {
            mNm7PosterReadyFallback = null;
            if (!isNm7CurrentEngine(observed) || mNm7Stopped || sNm7SuspendedForIptv
                    || isFinishing() || isDestroyed()) return;
            String currentVideoId = mNm7SessionVideo == null ? null : mNm7SessionVideo.videoId;
            if (!android.text.TextUtils.equals(expectedVideoId, currentVideoId)) return;
            if (observed.getPlaybackState() == Player.STATE_READY
                    && observed.getVideoFormat() != null
                    && observed.getPlayWhenReady()) {
                nm7HideStartupPoster("ready_fallback");
            }
        };
        mHandler.postDelayed(mNm7PosterReadyFallback, 350L);
    }

    private void ensureNm7PlaybackObserver() {
"""
if "private void nm7HideStartupPoster(String reason)" not in s:
    s = once(s, helper_anchor, helper_block, "poster helper anchor")

old_frame = """            @Override public void onRenderedFirstFrame() {
                if (!isNm7CurrentEngine(observed) || mNm7StartupFrame) return;
                mNm7StartupFrame = true;
                mNm7FirstFrameRendered = true;
                if (mNm7StartupPoster != null) {
                    Glide.with(PlaybackActivity.this).clear(mNm7StartupPoster);
                    mNm7StartupPoster.setImageDrawable(null);
                    mNm7StartupPoster.setVisibility(View.GONE);
                }
                if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
                if (mNm7VideoRequestedAt > 0) android.util.Log.i("NM7Playback",
                        "video_bind_to_frame_ms=" + (android.os.SystemClock.elapsedRealtime() - mNm7VideoRequestedAt));
                loadNm7CommentPreview();
            }
"""
new_frame = """            @Override public void onRenderedFirstFrame() {
                if (!isNm7CurrentEngine(observed)) return;
                nm7HideStartupPoster("first_frame");
                if (mNm7VideoRequestedAt > 0) android.util.Log.i("NM7Playback",
                        "video_bind_to_frame_ms=" + (android.os.SystemClock.elapsedRealtime() - mNm7VideoRequestedAt));
            }
"""
s = once(s, old_frame, new_frame, "first-frame poster callback")

ready_anchor = """                if (playbackState == Player.STATE_READY && mPlayer.getPlaybackError() == null && !mNm7RenderPolicy.exhausted()) {
"""
ready_repl = """                if (playbackState == Player.STATE_READY && mPlayer.getPlaybackError() == null && !mNm7RenderPolicy.exhausted()) {
                    if (mNm7StartupPoster != null
                            && mNm7StartupPoster.getVisibility() == View.VISIBLE) {
                        nm7ArmPosterReadyFallback(nm7CreatedPlayer);
                    }
"""
if "nm7ArmPosterReadyFallback(nm7CreatedPlayer);" not in s:
    s = once(s, ready_anchor, ready_repl, "READY poster fallback")

setvideo_anchor = """        // NM7 1.10.86: poster must be above SurfaceView, otherwise the SurfaceView's
"""
if setvideo_anchor not in s:
    raise SystemExit("v87: v86 poster setVideo block missing")
# Cancel stale fallback as soon as a new selection starts.
s = s.replace(setvideo_anchor,
"""        nm7CancelPosterReadyFallback();
        // NM7 1.10.86: poster must be above SurfaceView, otherwise the SurfaceView's
""", 1)

release_anchor = """    private void releasePlayer() {
        detachNm7PlaybackObserver();
"""
release_repl = """    private void releasePlayer() {
        nm7CancelPosterReadyFallback();
        detachNm7PlaybackObserver();
"""
s = once(s, release_anchor, release_repl, "release poster fallback")

PLAYBACK.write_text(s, encoding="utf-8")
print("NM7 Mobile 1.10.87 status-bar + non-sticky poster hardening applied")
