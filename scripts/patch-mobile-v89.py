"""NM7 Mobile 1.10.89: prevent late Glide poster flash after video already rendered.

The 1.10.88 overlay itself no longer sticks, but a fast decoder can render before
Glide finishes loading the poster. Because the ImageView was already VISIBLE, a
late Glide completion could briefly cover the playing video once. Gate poster
visibility on both image readiness and first-frame state.
"""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PLAYBACK = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"

def once(s, old, new, label):
    if s.count(old) != 1:
        raise SystemExit(f"v89: expected exactly one {label}, found {s.count(old)}")
    return s.replace(old, new, 1)

s = PLAYBACK.read_text(encoding="utf-8")

field = """    private long mNm7PosterProbeStartedAt;
"""
field2 = """    private long mNm7PosterProbeStartedAt;
    private String mNm7PosterVideoId;
"""
if "private String mNm7PosterVideoId;" not in s:
    s = once(s, field, field2, "poster video id field")

# First-frame state must be committed even if the image has not loaded yet.
old_hide = """    private void nm7HideStartupPoster(String reason) {
        nm7CancelPosterReadyFallback();
        nm7CancelPosterRenderProbe();
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
"""
new_hide = """    private void nm7HideStartupPoster(String reason) {
        nm7CancelPosterReadyFallback();
        nm7CancelPosterRenderProbe();
        mNm7StartupFrame = true;
        mNm7FirstFrameRendered = true;
        mNm7PosterVideoId = null;
        if (mNm7StartupPoster != null) {
            try {
                Glide.with(PlaybackActivity.this).clear(mNm7StartupPoster);
            } catch (RuntimeException ignored) { }
            mNm7StartupPoster.setImageDrawable(null);
            mNm7StartupPoster.setVisibility(View.GONE);
        }
        if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
        android.util.Log.i("NM7Playback", "startup_poster_hidden reason=" + reason);
        loadNm7CommentPreview();
    }
"""
s = once(s, old_hide, new_hide, "poster hide method")

# The decoder probe must run even while the poster bitmap is still loading.
old_probe_start = """    private void nm7ArmPosterRenderProbe(final String expectedVideoId) {
        nm7CancelPosterRenderProbe();
        if (mNm7StartupPoster == null || mPlayer == null) return;
"""
new_probe_start = """    private void nm7ArmPosterRenderProbe(final String expectedVideoId) {
        nm7CancelPosterRenderProbe();
        if (mPlayer == null) return;
"""
s = once(s, old_probe_start, new_probe_start, "poster probe start")

old_probe_guard = """                if (mNm7StartupPoster == null
                        || mNm7StartupPoster.getVisibility() != View.VISIBLE
                        || mPlayer == null || mNm7Stopped || sNm7SuspendedForIptv
"""
new_probe_guard = """                if (mPlayer == null || mNm7Stopped || sNm7SuspendedForIptv
"""
s = once(s, old_probe_guard, new_probe_guard, "poster probe visibility guard")

# Replace the eager-visible Glide block. Load the exact card URL already used by the feed
# and only expose the ImageView if this video still has no rendered frame.
old_block = """        // NM7 1.10.86: poster must be above SurfaceView, otherwise the SurfaceView's
        // black surface hides it even though Glide loaded the bitmap successfully.
        mNm7FirstFrameRendered = false;
        if (item != null && mNm7StartupPoster != null && !TextUtils.isEmpty(item.getCardImageUrl())) {
            String nm7PosterUrl = item.getCardImageUrl();
            if (nm7PosterUrl.contains("ytimg.com/")) {
                nm7PosterUrl = nm7PosterUrl
                        .replace("/default.jpg", "/maxresdefault.jpg")
                        .replace("/mqdefault.jpg", "/maxresdefault.jpg")
                        .replace("/hqdefault.jpg", "/maxresdefault.jpg")
                        .replace("/sddefault.jpg", "/maxresdefault.jpg");
            }
            mNm7StartupPoster.setVisibility(View.VISIBLE);
            mNm7StartupPoster.bringToFront();
            Glide.with(this)
                    .load(nm7PosterUrl)
                    .error(Glide.with(this).load(item.getCardImageUrl()))
                    .into(mNm7StartupPoster);
        }
"""
new_block = """        // NM7 1.10.89: never expose an empty/late poster request. A late Glide result
        // must not flash over video that has already produced its first decoder frame.
        mNm7FirstFrameRendered = false;
        mNm7PosterVideoId = item == null ? null : item.videoId;
        if (mNm7StartupPoster != null) {
            try {
                Glide.with(PlaybackActivity.this).clear(mNm7StartupPoster);
            } catch (RuntimeException ignored) { }
            mNm7StartupPoster.setImageDrawable(null);
            mNm7StartupPoster.setVisibility(View.GONE);
        }
        if (item != null && mNm7StartupPoster != null && !TextUtils.isEmpty(item.getCardImageUrl())) {
            final String nm7ExpectedPosterVideoId = item.videoId;
            Glide.with(this)
                    .load(item.getCardImageUrl())
                    .dontAnimate()
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(
                                com.bumptech.glide.load.engine.GlideException e,
                                Object model,
                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                boolean isFirstResource) {
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(
                                android.graphics.drawable.Drawable resource,
                                Object model,
                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                com.bumptech.glide.load.DataSource dataSource,
                                boolean isFirstResource) {
                            if (mNm7FirstFrameRendered
                                    || !android.text.TextUtils.equals(
                                            mNm7PosterVideoId, nm7ExpectedPosterVideoId)
                                    || isFinishing() || isDestroyed()) {
                                return true;
                            }
                            mNm7StartupPoster.post(() -> {
                                if (!mNm7FirstFrameRendered
                                        && android.text.TextUtils.equals(
                                                mNm7PosterVideoId, nm7ExpectedPosterVideoId)
                                        && mNm7StartupPoster != null) {
                                    mNm7StartupPoster.setVisibility(View.VISIBLE);
                                    mNm7StartupPoster.bringToFront();
                                }
                            });
                            return false;
                        }
                    })
                    .into(mNm7StartupPoster);
        }
"""
s = once(s, old_block, new_block, "eager poster Glide block")

# Probe for every new video, not only after the bitmap became visible.
old_arm = """        if (item != null && mNm7StartupPoster != null
                && mNm7StartupPoster.getVisibility() == View.VISIBLE) {
            nm7ArmPosterRenderProbe(item.videoId);
        }
"""
new_arm = """        if (item != null) {
            nm7ArmPosterRenderProbe(item.videoId);
        }
"""
s = once(s, old_arm, new_arm, "poster probe arm condition")

PLAYBACK.write_text(s, encoding="utf-8")
print("NM7 Mobile 1.10.89 late-poster-flash fix applied")
