"""NM7 Mobile 1.10.86: final device-video fixes for status bar, YouTube avatars, and poster-first switching."""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
PLAYBACK = PHONE / "playback/PlaybackActivity.java"
LAYOUT = ROOT / "smarttubedroid/src/main/res/layout/playback_activity.xml"
CARD = PHONE / "shared/VideoCardHolder.java"
APP = Path("app/src/main/java/vn/phuong/iptvplayer/MobileNm7Application.java")
MOTHER = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/misc/MotherActivity.java"

def once(s, old, new, label):
    if s.count(old) != 1:
        raise SystemExit(f"v86: expected exactly one {label}, found {s.count(old)}")
    return s.replace(old, new, 1)

def replace_method(text, signature, replacement, label):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f"v86: {label} signature not found")
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
        raise SystemExit(f"v86: {label} closing brace not found")
    return text[:method_start] + replacement + text[end:]

# ---------------------------------------------------------------------------
# 1) Status bar: stop MotherActivity from entering persistent fullscreen on Mobile.
# PlaybackActivity itself still hides both bars in landscape/PIP.
a = APP.read_text(encoding="utf-8")
anchor = """        System.setProperty("nm7.mobile.livechat", "true");
"""
repl = """        System.setProperty("nm7.mobile.livechat", "true");
        // NM7 1.10.86: tell SmartTube's own MotherActivity to skip persistent
        // fullscreen on the integrated phone UI. Avoid a cross-module Java dependency.
        System.setProperty("nm7.mobile.normalbars", "true");
"""
if "NM7 1.10.86: phone UI must use normal portrait system windows" not in a:
    a = once(a, anchor, repl, "Mobile fullscreen preference anchor")
APP.write_text(a, encoding="utf-8")


# Patch SmartTube common layer itself so its onResume() cannot hide the portrait status bar again.
m = MOTHER.read_text(encoding="utf-8")
mother_old = """    private void applyFullscreenModeIfNeeded() {
        if (mIsFullscreenModeEnabled) {
"""
mother_new = """    private void applyFullscreenModeIfNeeded() {
        if ("true".equals(System.getProperty("nm7.mobile.normalbars"))) {
            return;
        }
        if (mIsFullscreenModeEnabled) {
"""
if 'System.getProperty("nm7.mobile.normalbars")' not in m:
    m = once(m, mother_old, mother_new, "MotherActivity fullscreen bypass")
MOTHER.write_text(m, encoding="utf-8")

s = PLAYBACK.read_text(encoding="utf-8")
system_ui = """    private void applySystemUi(boolean fullscreen) {
        if (!fullscreen) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (VERSION.SDK_INT >= 30) {
                getWindow().setDecorFitsSystemWindows(true);
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars());
                    controller.show(WindowInsets.Type.navigationBars());
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            if (mRoot != null) {
                mRoot.post(() -> mRoot.requestApplyInsets());
                mRoot.postDelayed(this::nm7RestorePortraitBars, 120L);
                mRoot.postDelayed(this::nm7RestorePortraitBars, 420L);
            }
            return;
        }

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

system_bars = """    @Override
    protected void applySystemBars() {
        applySystemUi(isLandscape() || isInPIPMode());
    }
"""
s = replace_method(s, "    protected void applySystemBars() {", system_bars, "applySystemBars")

helper_anchor = """    private void toggleFullscreen() {
"""
helper = """    private void nm7RestorePortraitBars() {
        if (isLandscape() || isInPIPMode() || isFinishing() || isDestroyed()) return;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(true);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars());
                controller.show(WindowInsets.Type.navigationBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        if (mRoot != null) mRoot.requestApplyInsets();
    }

    private void toggleFullscreen() {
"""
if "private void nm7RestorePortraitBars()" not in s:
    s = once(s, helper_anchor, helper, "portrait bar helper anchor")

# Reassert after all resume work, not just inside DroidActivity.super.onResume().
resume_tail = """        showHideWidgets(true);
    }
"""
resume_repl = """        showHideWidgets(true);
        nm7RestorePortraitBars();
    }
"""
if "showHideWidgets(true);\n        nm7RestorePortraitBars();" not in s:
    s = once(s, resume_tail, resume_repl, "onResume status bar tail")

# ---------------------------------------------------------------------------
# 2) Poster overlay ABOVE SurfaceView. The old playback_background sits below
# SurfaceView and is therefore invisible during decoder/source startup.
layout = LAYOUT.read_text(encoding="utf-8")
if 'android:id="@+id/nm7_startup_poster"' not in layout:
    player_id = 'android:id="@+id/playback_player_view"'
    pid = layout.find(player_id)
    if pid < 0:
        raise SystemExit("v86: playback_player_view id not found")
    tag_end = layout.find("/>", pid)
    if tag_end < 0:
        raise SystemExit("v86: playback_player_view closing tag not found")
    insert_at = tag_end + 2
    poster_tag = """
        <!-- NM7 1.10.86: selected-video poster sits above SurfaceView until first frame. -->
        <ImageView
            android:id="@+id/nm7_startup_poster"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:contentDescription="@null"
            android:scaleType="centerCrop"
            android:visibility="gone" />
"""
    layout = layout[:insert_at] + poster_tag + layout[insert_at:]
LAYOUT.write_text(layout, encoding="utf-8")

field_anchor = """    private ImageView mBackgroundView;
"""
field_repl = """    private ImageView mBackgroundView;
    private ImageView mNm7StartupPoster;
"""
if "private ImageView mNm7StartupPoster;" not in s:
    s = once(s, field_anchor, field_repl, "startup poster field")

init_anchor = """        mBackgroundView = findViewById(R.id.playback_background);
"""
init_repl = """        mBackgroundView = findViewById(R.id.playback_background);
        mNm7StartupPoster = findViewById(R.id.nm7_startup_poster);
"""
if "mNm7StartupPoster = findViewById" not in s:
    s = once(s, init_anchor, init_repl, "startup poster init")

# Replace 1.10.84 poster block so it loads the overlay above SurfaceView.
old_start = """        // NM7 1.10.84: show the selected video's poster immediately, before network
        // format resolution/decoder startup. It is removed only on first rendered frame.
        mNm7FirstFrameRendered = false;
        if (item != null && mBackgroundView != null && !TextUtils.isEmpty(item.getCardImageUrl())) {
            String nm7PosterUrl = item.getCardImageUrl();
            if (nm7PosterUrl.contains("ytimg.com/")) {
                nm7PosterUrl = nm7PosterUrl
                        .replace("/default.jpg", "/maxresdefault.jpg")
                        .replace("/mqdefault.jpg", "/maxresdefault.jpg")
                        .replace("/hqdefault.jpg", "/maxresdefault.jpg")
                        .replace("/sddefault.jpg", "/maxresdefault.jpg");
            }
            mBackgroundView.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(nm7PosterUrl)
                    .error(Glide.with(this).load(item.getCardImageUrl()))
                    .into(mBackgroundView);
        }
"""
new_start = """        // NM7 1.10.86: poster must be above SurfaceView, otherwise the SurfaceView's
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
s = once(s, old_start, new_start, "old poster block")

# Existing v84 spinner suppression tracked the background BELOW SurfaceView.
old_progress = """            boolean nm7PosterVisible = !mNm7FirstFrameRendered && mBackgroundView != null
                    && mBackgroundView.getVisibility() == View.VISIBLE;
"""
new_progress = """            boolean nm7PosterVisible = !mNm7FirstFrameRendered && mNm7StartupPoster != null
                    && mNm7StartupPoster.getVisibility() == View.VISIBLE;
"""
s = once(s, old_progress, new_progress, "poster spinner visibility")

# Hide the overlay on the actual first decoded frame. This callback already owns the
# final active engine and therefore survives player recreation/retry.
frame_anchor = """                mNm7StartupFrame = true;
                if (mNm7VideoRequestedAt > 0) android.util.Log.i("NM7Playback",
"""
frame_repl = """                mNm7StartupFrame = true;
                mNm7FirstFrameRendered = true;
                if (mNm7StartupPoster != null) {
                    Glide.with(PlaybackActivity.this).clear(mNm7StartupPoster);
                    mNm7StartupPoster.setImageDrawable(null);
                    mNm7StartupPoster.setVisibility(View.GONE);
                }
                if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
                if (mNm7VideoRequestedAt > 0) android.util.Log.i("NM7Playback",
"""
if "mNm7FirstFrameRendered = true;" not in s:
    s = once(s, frame_anchor, frame_repl, "active first-frame poster removal")

PLAYBACK.write_text(s, encoding="utf-8")

# ---------------------------------------------------------------------------
# 3) Avatar fallback: modern feed renderers often omit channelThumbnail on cards.
# Resolve the author image lazily from video metadata for visible cards and cache it.
c = CARD.read_text(encoding="utf-8")
cache_anchor = """    private Video mVideo;
"""
cache_repl = """    private Video mVideo;
    private static final java.util.concurrent.ConcurrentHashMap<String, String> NM7_AVATAR_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> NM7_AVATAR_LOADING =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
"""
if "NM7_AVATAR_CACHE" not in c:
    c = once(c, cache_anchor, cache_repl, "avatar cache field")

old_else = """            } else {
                avatar.setImageResource(R.drawable.browse_ic_account);
            }
        }

        String cardImageUrl = video.getCardImageUrl();
"""
new_else = """            } else {
                avatar.setImageResource(R.drawable.browse_ic_account);
                nm7ResolveAvatar(context, video, avatar);
            }
        }

        String cardImageUrl = video.getCardImageUrl();
"""
if "nm7ResolveAvatar(context, video, avatar);" not in c:
    c = once(c, old_else, new_else, "avatar fallback hook")

helper_anchor = """    private static RequestOptions glideOptions() {
"""
avatar_helper = """    private void nm7ResolveAvatar(Context context, Video video, android.widget.ImageView avatar) {
        if (video == null || TextUtils.isEmpty(video.videoId) || avatar == null) return;
        final String videoId = video.videoId;
        final String cacheKey = !TextUtils.isEmpty(video.channelId) ? video.channelId : videoId;
        String cached = NM7_AVATAR_CACHE.get(cacheKey);
        if (!TextUtils.isEmpty(cached)) {
            Glide.with(context).load(cached).circleCrop()
                    .placeholder(R.drawable.browse_ic_account)
                    .error(R.drawable.browse_ic_account).into(avatar);
            return;
        }
        if (!NM7_AVATAR_LOADING.add(cacheKey)) return;

        com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance()
                .getMediaItemService().getMetadataObserve(videoId)
                .take(1)
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(metadata -> {
                    NM7_AVATAR_LOADING.remove(cacheKey);
                    String url = metadata != null ? metadata.getAuthorImageUrl() : null;
                    if (TextUtils.isEmpty(url)) return;
                    NM7_AVATAR_CACHE.put(cacheKey, url);
                    if (mVideo != null && TextUtils.equals(mVideo.videoId, videoId)
                            && !((context instanceof Activity) && ((Activity) context).isDestroyed())) {
                        Glide.with(context).load(url).circleCrop()
                                .placeholder(R.drawable.browse_ic_account)
                                .error(R.drawable.browse_ic_account).into(avatar);
                    }
                }, error -> NM7_AVATAR_LOADING.remove(cacheKey));
    }

    private static RequestOptions glideOptions() {
"""
if "private void nm7ResolveAvatar(" not in c:
    c = once(c, helper_anchor, avatar_helper, "avatar metadata helper anchor")
CARD.write_text(c, encoding="utf-8")

print("NM7 Mobile 1.10.86 status/avatar/poster transition patch applied")
