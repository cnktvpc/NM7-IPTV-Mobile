"""NM7 Mobile 1.10.90: native-style thumbnail-to-player handoff only.

Capture the already-rendered card thumbnail synchronously at click time and show
that exact bitmap in PlaybackActivity until the decoder renders the new video.
This avoids the black/spinner gap without reintroducing the late-Glide flash.
No IPTV/status/avatar/chat behavior is changed here.
"""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
CARD = PHONE / "shared/VideoCardHolder.java"
PLAYBACK = PHONE / "playback/PlaybackActivity.java"

def once(s, old, new, label):
    if s.count(old) != 1:
        raise SystemExit(f"v90: expected exactly one {label}, found {s.count(old)}")
    return s.replace(old, new, 1)

# ---------------------------------------------------------------------------
# Capture the actual bitmap that is already visible in the tapped YouTube card.
c = CARD.read_text(encoding="utf-8")

field_anchor = """    private Video mVideo;
"""
field_repl = """    private Video mVideo;
    private static android.graphics.Bitmap sNm7TransitionPoster;
    private static String sNm7TransitionVideoId;
"""
if "sNm7TransitionPoster" not in c:
    c = once(c, field_anchor, field_repl, "transition poster fields")

old_click = """            itemView.setOnClickListener(v -> listener.onVideoClicked(video));
"""
new_click = """            itemView.setOnClickListener(v -> {
                nm7CaptureTransitionPoster(video);
                listener.onVideoClicked(video);
            });
"""
c = once(c, old_click, new_click, "video card click")

helper_anchor = """    private void bindBadges(Context context, Video video) {
"""
helper = """    private void nm7CaptureTransitionPoster(Video video) {
        if (video == null || TextUtils.isEmpty(video.videoId) || mThumbnail == null) return;
        android.graphics.drawable.Drawable drawable = mThumbnail.getDrawable();
        if (drawable == null) return;

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            width = mThumbnail.getWidth();
            height = mThumbnail.getHeight();
        }
        if (width <= 0 || height <= 0) return;

        // Keep the transient handoff bitmap bounded while preserving aspect ratio.
        final int maxWidth = 1280;
        if (width > maxWidth) {
            height = Math.max(1, Math.round(height * (maxWidth / (float) width)));
            width = maxWidth;
        }

        try {
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    width, height, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            android.graphics.Rect oldBounds = drawable.copyBounds();
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            drawable.setBounds(oldBounds);

            synchronized (VideoCardHolder.class) {
                sNm7TransitionPoster = bitmap;
                sNm7TransitionVideoId = video.videoId;
            }
        } catch (RuntimeException ignored) { }
    }

    public static android.graphics.Bitmap consumeNm7TransitionPoster(String videoId) {
        synchronized (VideoCardHolder.class) {
            if (TextUtils.isEmpty(videoId)
                    || !TextUtils.equals(videoId, sNm7TransitionVideoId)
                    || sNm7TransitionPoster == null) {
                return null;
            }
            android.graphics.Bitmap result = sNm7TransitionPoster;
            sNm7TransitionPoster = null;
            sNm7TransitionVideoId = null;
            return result;
        }
    }

    private void bindBadges(Context context, Video video) {
"""
if "consumeNm7TransitionPoster" not in c:
    c = once(c, helper_anchor, helper, "transition poster helpers")

CARD.write_text(c, encoding="utf-8")

# ---------------------------------------------------------------------------
# Consume that bitmap before any async Glide fallback. This makes the first
# visible player frame the same thumbnail the user tapped, like native YouTube.
s = PLAYBACK.read_text(encoding="utf-8")

clear_block = """        if (mNm7StartupPoster != null) {
            try {
                Glide.with(PlaybackActivity.this).clear(mNm7StartupPoster);
            } catch (RuntimeException ignored) { }
            mNm7StartupPoster.setImageDrawable(null);
            mNm7StartupPoster.setVisibility(View.GONE);
        }
        if (item != null && mNm7StartupPoster != null && !TextUtils.isEmpty(item.getCardImageUrl())) {
"""
handoff_block = """        if (mNm7StartupPoster != null) {
            try {
                Glide.with(PlaybackActivity.this).clear(mNm7StartupPoster);
            } catch (RuntimeException ignored) { }
            mNm7StartupPoster.setImageDrawable(null);
            mNm7StartupPoster.setVisibility(View.GONE);
        }

        android.graphics.Bitmap nm7TransitionPoster = item == null ? null :
                com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoCardHolder
                        .consumeNm7TransitionPoster(item.videoId);
        if (nm7TransitionPoster != null && mNm7StartupPoster != null) {
            mNm7StartupPoster.setImageBitmap(nm7TransitionPoster);
            mNm7StartupPoster.setVisibility(View.VISIBLE);
            mNm7StartupPoster.bringToFront();
            if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
            android.util.Log.i("NM7Playback", "startup_poster_source=clicked_card");
        }

        if (nm7TransitionPoster == null && item != null && mNm7StartupPoster != null
                && !TextUtils.isEmpty(item.getCardImageUrl())) {
"""
s = once(s, clear_block, handoff_block, "player transition poster handoff")

PLAYBACK.write_text(s, encoding="utf-8")
print("NM7 Mobile 1.10.90 clicked-card transition poster applied")
