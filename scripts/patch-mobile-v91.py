"""NM7 Mobile 1.10.91: smooth YouTube open, sharper handoff, less feed contention.

Keeps all stable 1.10.90 features. Only:
- don't reveal a frozen first decoded frame; hold poster until several frames flow;
- reuse the original Glide BitmapDrawable bitmap when possible (no extra resample);
- remove Android activity animation when opening PlaybackActivity;
- defer/serialize metadata-only avatar fallbacks so feed thumbnails/API have priority;
- trim initial playback buffer conservatively.
"""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
CARD = PHONE / "shared/VideoCardHolder.java"
PLAYBACK = PHONE / "playback/PlaybackActivity.java"
VIEW_MANAGER = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/views/ViewManager.java"
INIT = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/ExoPlayerInitializer.java"

def once(s, old, new, label):
    if s.count(old) != 1:
        raise SystemExit(f"v91: expected exactly one {label}, found {s.count(old)}")
    return s.replace(old, new, 1)

# ---------------------------------------------------------------------------
# 1) Card -> player poster: use Glide's already-decoded bitmap directly when possible.
c = CARD.read_text(encoding="utf-8")
old_bitmap = """        try {
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
"""
new_bitmap = """        try {
            android.graphics.Bitmap bitmap = null;
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                // Preserve the exact high-quality bitmap Glide decoded for the card.
                // Re-drawing it into a new bitmap caused a visible softening on handoff.
                bitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            }
            if (bitmap == null) {
                bitmap = android.graphics.Bitmap.createBitmap(
                        width, height, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                android.graphics.Rect oldBounds = drawable.copyBounds();
                drawable.setBounds(0, 0, width, height);
                drawable.draw(canvas);
                drawable.setBounds(oldBounds);
            }

            synchronized (VideoCardHolder.class) {
                sNm7TransitionPoster = bitmap;
                sNm7TransitionVideoId = video.videoId;
            }
        } catch (RuntimeException ignored) { }
"""
c = once(c, old_bitmap, new_bitmap, "transition bitmap capture")

# 2) Metadata avatar fallback must not compete with the critical Home feed/card images.
old_avatar = """        com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance()
                .getMediaItemService().getMetadataObserve(videoId)
                .take(1)
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
"""
new_avatar = """        com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance()
                .getMediaItemService().getMetadataObserve(videoId)
                .delaySubscription(650, java.util.concurrent.TimeUnit.MILLISECONDS)
                .take(1)
                // Serialize optional avatar enrichment. Initial feed + thumbnails and
                // selected-video format requests keep the I/O/network priority.
                .subscribeOn(io.reactivex.schedulers.Schedulers.single())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
"""
c = once(c, old_avatar, new_avatar, "avatar metadata scheduler")
CARD.write_text(c, encoding="utf-8")

# ---------------------------------------------------------------------------
# 3) Don't expose the first frozen decoder frame. Let the decoder advance several
# output buffers and then reveal the moving video.
s = PLAYBACK.read_text(encoding="utf-8")

field_anchor = """    private String mNm7PosterVideoId;
"""
field_repl = """    private String mNm7PosterVideoId;
    private long mNm7FirstFrameSeenAt;
"""
if "mNm7FirstFrameSeenAt" not in s:
    s = once(s, field_anchor, field_repl, "first-frame timing field")

old_frame = """            @Override public void onRenderedFirstFrame() {
                if (!isNm7CurrentEngine(observed)) return;
                nm7HideStartupPoster("first_frame");
                if (mNm7VideoRequestedAt > 0) android.util.Log.i("NM7Playback",
                        "video_bind_to_frame_ms=" + (android.os.SystemClock.elapsedRealtime() - mNm7VideoRequestedAt));
            }
"""
new_frame = """            @Override public void onRenderedFirstFrame() {
                if (!isNm7CurrentEngine(observed)) return;
                mNm7FirstFrameSeenAt = android.os.SystemClock.elapsedRealtime();
                // Do not reveal a potentially frozen first frame. Reset the decoder
                // baseline here and let the render probe wait for a few moving frames.
                String currentVideoId = mNm7SessionVideo == null ? null : mNm7SessionVideo.videoId;
                if (!android.text.TextUtils.isEmpty(currentVideoId)) {
                    nm7ArmPosterRenderProbe(currentVideoId);
                }
                if (mNm7VideoRequestedAt > 0) android.util.Log.i("NM7Playback",
                        "video_bind_to_frame_ms=" + (android.os.SystemClock.elapsedRealtime() - mNm7VideoRequestedAt));
            }
"""
s = once(s, old_frame, new_frame, "first-frame callback")

old_rendered = """                boolean renderedNewFrame = rendered > mNm7PosterRenderedBaseline;
"""
new_rendered = """                boolean renderedNewFrame = rendered >= mNm7PosterRenderedBaseline + 3;
"""
s = once(s, old_rendered, new_rendered, "decoder frame threshold")

old_hide_cond = """                if (renderedNewFrame && elapsed >= 120L) {
                    nm7HideStartupPoster("decoder_frame");
                    return;
                }
"""
new_hide_cond = """                if (renderedNewFrame && elapsed >= 180L
                        && (mNm7FirstFrameSeenAt == 0L
                            || android.os.SystemClock.elapsedRealtime() - mNm7FirstFrameSeenAt >= 120L)) {
                    nm7HideStartupPoster("decoder_moving_frames");
                    return;
                }
"""
s = once(s, old_hide_cond, new_hide_cond, "moving-frame poster hide")

# Reset timing on each selection.
old_set = """        mNm7FirstFrameRendered = false;
        mNm7PosterVideoId = item == null ? null : item.videoId;
"""
new_set = """        mNm7FirstFrameRendered = false;
        mNm7FirstFrameSeenAt = 0L;
        mNm7PosterVideoId = item == null ? null : item.videoId;
"""
s = once(s, old_set, new_set, "first-frame selection reset")

PLAYBACK.write_text(s, encoding="utf-8")

# ---------------------------------------------------------------------------
# 4) ViewManager launches PlaybackActivity from application context. Suppress the OS
# activity animation there too, otherwise Browse can briefly become visible mid-handoff.
v = VIEW_MANAGER.read_text(encoding="utf-8")
old_start = """    private void safeStartActivityInt(Context context, Intent intent) {
        try {
            context.startActivity(intent);
"""
new_start = """    private void safeStartActivityInt(Context context, Intent intent) {
        try {
            if ("true".equals(System.getProperty("nm7.mobile.normalbars"))
                    && intent.getComponent() != null
                    && intent.getComponent().getClassName().endsWith(".playback.PlaybackActivity")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            }
            context.startActivity(intent);
"""
v = once(v, old_start, new_start, "ViewManager playback launch")
VIEW_MANAGER.write_text(v, encoding="utf-8")

# ---------------------------------------------------------------------------
# 5) Small startup-only buffer trim. Rebuffer reserve remains untouched.
e = INIT.read_text(encoding="utf-8")
e = e.replace(
    "int bufferForPlaybackMs = 350; // NM7 1.10.83: slightly faster first-frame threshold; retain forward buffer.",
    "int bufferForPlaybackMs = 250; // NM7 1.10.91: faster initial start; rebuffer reserve unchanged.",
)
INIT.write_text(e, encoding="utf-8")

print("NM7 Mobile 1.10.91 smooth-open/feed-priority patch applied")
