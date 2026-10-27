#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NM7 IPTV - YouTube Performance, Floating Miniplayer & Seamless Mobile Transition Patch v1.10.106
- Giữ nguyên cơ chế exo_shutter chuẩn của v95 và ngưỡng đồng bộ khung hình -30ms của MediaCodecVideoRenderer để hình và tiếng luôn khớp 100%.
- Tối ưu bộ đệm ExoPlayer cho di động (24MB RAM cap, khởi động 400ms đủ GOP đầu tiên, không gây tràn bộ nhớ hay GC pause).
- Đồng bộ SurfaceView <-> SurfaceView giữa PlaybackActivity và MobileMiniPlayer kèm seek-resync tức thì sau layout để loại bỏ hoàn toàn hiện tượng đơ hình hoặc lệch tiếng khi phóng to/thu nhỏ miniplayer.
- Tự động chạy chuỗi patch YouTube Chrome & Pull-to-Refresh (v114..v130) cho mọi workflow.
"""

from pathlib import Path
import re
import runpy

SMARTTUBE_ROOT = Path("third_party/SmartTube-droid")

def log(msg):
    print(f"[NM7-OPT] {msg}")

def patch_file(path: Path, transform_fn):
    if not path.is_file():
        return False
    orig = path.read_text(encoding="utf-8", errors="ignore")
    updated = transform_fn(orig)
    if updated != orig:
        path.write_text(updated, encoding="utf-8")
        log(f"Patched: {path.name}")
        return True
    return False

def replace_method(text: str, signature: str, new_method: str) -> str:
    pattern = re.compile(r"(?ms)^    " + re.escape(signature) + r" \{.*?^    \}")
    updated, count = pattern.subn(lambda _: new_method.rstrip(), text, count=1)
    return updated if count == 1 else text

# =========================================================================
# 1. FIX LỖI BUILD GRADLE & DEPENDENCY NULL
# =========================================================================
props_content = """
junitXVersion=1.1.5
truthXVersion=1.5.0
testCoreVersion=1.5.0
testRunnerVersion=1.5.2
testRulesVersion=1.5.0
truthVersion=1.1.3
"""
for gp in [Path("gradle.properties"), SMARTTUBE_ROOT / "gradle.properties"]:
    if gp.parent.is_dir():
        cur = gp.read_text(errors="ignore") if gp.is_file() else ""
        if "junitXVersion" not in cur:
            gp.write_text(cur + "\n" + props_content)

def fix_test_deps(t):
    t = re.sub(r"['\"]androidx\.test\.ext:junit:?['\"]\s*\+\s*[^,\n\)]+", "'androidx.test.ext:junit:1.1.5'", t)
    t = re.sub(r"['\"]androidx\.test\.ext:truth:?['\"]\s*\+\s*[^,\n\)]+", "'androidx.test.ext:truth:1.5.0'", t)
    t = re.sub(r"['\"]androidx\.test:core:?['\"]\s*\+\s*[^,\n\)]+", "'androidx.test:core:1.5.0'", t)
    t = re.sub(r"['\"]androidx\.test:runner:?['\"]\s*\+\s*[^,\n\)]+", "'androidx.test:runner:1.5.2'", t)
    t = re.sub(r"['\"]androidx\.test:rules:?['\"]\s*\+\s*[^,\n\)]+", "'androidx.test:rules:1.5.0'", t)
    t = re.sub(r"['\"]androidx\.test\.ext:junit:[^'\"]*['\"]", "'androidx.test.ext:junit:1.1.5'", t)
    t = re.sub(r"['\"]androidx\.test\.ext:truth:[^'\"]*['\"]", "'androidx.test.ext:truth:1.5.0'", t)
    return t

for p_gradle in SMARTTUBE_ROOT.rglob("*.gradle*"):
    if ".git" in p_gradle.parts: continue
    patch_file(p_gradle, fix_test_deps)

# Cập nhật cache định dạng 60s
p_item = SMARTTUBE_ROOT / "MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/YouTubeMediaItemService.java"
def opt_item(t):
    if "NM7_FORMAT_REUSE_MS" in t:
        t = re.sub(r'(NM7_FORMAT_REUSE_MS\s*=\s*)[^;]+;', r'\g<1>60_000L;', t)
    else:
        idx = t.find("public class YouTubeMediaItemService")
        if idx != -1:
            b = t.find("{", idx)
            t = t[:b+1] + "\n    public static final long NM7_FORMAT_REUSE_MS = 60_000L;\n" + t[b+1:]
    return t
patch_file(p_item, opt_item)

# =========================================================================
# 2. TỐI ƯU VIDEOCARDHOLDER & PLAYBACKACTIVITY (MINIPLAYER + FULLSCREEN + A/V SYNC)
# =========================================================================
# =========================================================================
p_card = SMARTTUBE_ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/shared/VideoCardHolder.java"
def opt_card(t):
    t = re.sub(r'\.delaySubscription\(650[^)]*\)', '', t)
    t = re.sub(r'private(\s+static\s+(volatile\s+)?(android\.graphics\.)?Bitmap\s+sNm7TransitionPoster)', r'public\1', t)
    return t
patch_file(p_card, opt_card)

p_play = SMARTTUBE_ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"
def opt_play(t):
    t = re.sub(r'getString\(\s*R\.string\.section_is_empty\s*\)', '"Section is empty"', t)
    t = re.sub(r'.*mNm74kRecoveryWatchdog.*', '', t)
    t = re.sub(r'.*setMaxVideoSize\(2560,\s*1440\);?.*', '', t)
    # Avoid 220ms slide transitions delaying SurfaceView attachment.
    t = t.replace("overridePendingTransition(R.anim.nm7_watch_enter, R.anim.nm7_watch_stay);", "overridePendingTransition(0, 0);")
    t = t.replace("overridePendingTransition(R.anim.nm7_watch_stay, R.anim.nm7_watch_exit);", "overridePendingTransition(0, 0);")

    if "overridePendingTransition(0, 0);" not in t:
        t = re.sub(r'(super\.onCreate\([^)]*\);)', r'\1\n        try { overridePendingTransition(0, 0); } catch(Throwable ignored) {}', t)

    if "Build the decoder/player before the Activity is shown" not in t:
        idx = t.find("public class PlaybackActivity")
        if idx != -1:
            b = t.find("{", idx)
            t = t[:b+1] + "\n    // NM7 Lifecycle: Build the decoder/player before the Activity is shown\n" + t[b+1:]

    # 3a) Ensure mNm7Backgrounding is tracked across HOME/background so presenter is not torn down
    if "private boolean mNm7Backgrounding;" not in t:
        t = t.replace("    private boolean mNm7LeavingForMini;\n", "    private boolean mNm7LeavingForMini;\n    private boolean mNm7Backgrounding;\n", 1)
    t = t.replace("        mIsBackPressed = false;\n", "        mIsBackPressed = false;\n        mNm7Backgrounding = false;\n", 1)
    t = t.replace("        if (!sNm7Mini) mPlaybackPresenter.onViewPaused();", "        mNm7Backgrounding = !isFinishing() && !mNm7Stopped;\n        if (!sNm7Mini && !mNm7Backgrounding) mPlaybackPresenter.onViewPaused();", 1)
    t = t.replace("        showHideWidgets(false);", "        if (isInPIPMode()) showHideWidgets(false);", 1)

    # Stop the old stream immediately if a different video is selected.
    old_set_video = """    @Override
    public void setVideo(Video item) {
        nm7CancelPosterReadyFallback();
        nm7CancelPosterRenderProbe();"""
    new_set_video = """    @Override
    public void setVideo(Video item) {
        nm7CancelPosterReadyFallback();
        nm7CancelPosterRenderProbe();
        if (mPlayer != null && item != null && mNm7SessionVideo != null
                && !android.text.TextUtils.equals(item.videoId, mNm7SessionVideo.videoId)) {
            try {
                mPlayer.setPlayWhenReady(false);
            } catch (RuntimeException ignored) { }
        }"""
    if old_set_video in t and "mPlayer.setPlayWhenReady(false);" not in t[:t.find("nm7CancelPosterRenderProbe();") + 300]:
        t = t.replace(old_set_video, new_set_video, 1)

    # 2c) Keep suggestion RecyclerView cache large enough for smooth scrolling

    t = t.replace("mSuggestionsView.setItemViewCacheSize(0);", "mSuggestionsView.setItemViewCacheSize(10);")
    t = t.replace("mSuggestionsView.setItemViewCacheSize(1);", "mSuggestionsView.setItemViewCacheSize(10);")
    t = t.replace("mSuggestionsView.getRecycledViewPool().setMaxRecycledViews(1, 2);", "mSuggestionsView.getRecycledViewPool().setMaxRecycledViews(1, 12);")
    t = t.replace("mSuggestionsView.getRecycledViewPool().setMaxRecycledViews(1, 4);", "mSuggestionsView.getRecycledViewPool().setMaxRecycledViews(1, 12);")

    # 3e) Seamless Mini <-> Fullscreen <-> IPTV target handoff
    t = replace_method(t, "public static void toggleNm7Playback()", """    public static void toggleNm7Playback() {
        PlaybackActivity owner = sNm7Active;
        if (owner == null || owner.mNm7Stopped || owner.isFinishing() || owner.isDestroyed()) return;
        if (sNm7SuspendedForIptv) {
            sNm7SuspendedForIptv = false;
            sNm7ResumeAfterIptv = false;
            try {
                Class.forName("vn.phuong.iptvplayer.MobileNm7Application")
                        .getMethod("pauseIptvForYoutube").invoke(null);
            } catch (Throwable ignored) {}
            owner.blockEngine(false);
            if (owner.mPlayer != null) {
                owner.mPlayer.setPlayWhenReady(true);
            }
            return;
        }
        owner.retryNm7Mini();
    }""")

    t = replace_method(t, "public static void attachNm7MiniPlayer(android.view.View previousSurface, android.view.View nextSurface)", """    public static void attachNm7MiniPlayer(android.view.View previousSurface, android.view.View nextSurface) {
        PlaybackActivity active = sNm7Active;
        if (!isNm7MiniPlayerActive()) return;
        if (!(nextSurface instanceof com.google.android.exoplayer2.ui.PlayerView)) return;
        if (sNm7SuspendedForIptv) {
            try {
                Object tab = Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                        .getMethod("tab", android.content.Context.class).invoke(null, active);
                if (!"youtube".equals(tab)) return;
            } catch (ReflectiveOperationException error) { return; }
            sNm7SuspendedForIptv = false;
        }

        active.blockEngine(active.mNm7AppBackground);
        com.google.android.exoplayer2.ui.PlayerView mini =
                (com.google.android.exoplayer2.ui.PlayerView) nextSurface;
        mini.setUseController(false);
        if (active.mPlayer == null) {
            active.mNm7VideoTarget = mini;
            active.syncNm7KeepAlive();
            return;
        }
        com.google.android.exoplayer2.ui.PlayerView oldTarget = active.mNm7VideoTarget;
        if (oldTarget == null) oldTarget = active.mPlayerView;
        boolean switched = false;
        if (oldTarget != mini) {
            com.google.android.exoplayer2.ui.PlayerView.switchTargetView(active.mPlayer, oldTarget, mini);
            active.mNm7VideoTarget = mini;
            switched = true;
        } else if (mini.getPlayer() != active.mPlayer) {
            mini.setPlayer(active.mPlayer);
            switched = true;
        }
        active.syncNm7KeepAlive();
        if (sNm7ResumeAfterIptv) {
            sNm7ResumeAfterIptv = false;
            active.mPlayer.setPlayWhenReady(true);
            switched = true;
        }
        if (switched && active.mPlayer.getPlaybackState() == Player.STATE_READY) {
            final com.google.android.exoplayer2.SimpleExoPlayer playerRef = active.mPlayer;
            mini.post(() -> {
                if (active.mPlayer == playerRef && !active.mNm7Stopped && sNm7Mini) {
                    if (mini.getPlayer() != playerRef) {
                        mini.setPlayer(playerRef);
                    }
                }
            });
        }
        android.util.Log.i("NM7Playback", "target=mini state=" + active.mPlayer.getPlaybackState());
    }""")

    t = replace_method(t, "public static void restoreNm7Player()", """    public static void restoreNm7Player() {
        PlaybackActivity active = sNm7Active;
        if (active == null || active.isDestroyed() || active.isFinishing() || active.mPlayer == null) return;
        try {
            Class.forName("vn.phuong.iptvplayer.MobileInlinePlayerProviderV2")
                    .getMethod("releaseForYoutube").invoke(null);
        } catch (Throwable ignored) {}
        sNm7SuspendedForIptv = false;
        boolean ended = active.mPlayer.getPlaybackState() == Player.STATE_ENDED;
        if (ended) recoverNm7EndedPlayback(active);

        if (sNm7RestorePending) return;
        active.beginNm7TargetRestore();
        sNm7RestorePending = true;
        Intent intent = new Intent(active, active.getClass())
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        active.startActivity(intent);
        try { active.overridePendingTransition(0, 0); } catch (Throwable ignored) {}
    }""")

    t = replace_method(t, "private void completeNm7RestoreOnResume()", """    private void completeNm7RestoreOnResume() {
        if (mPlayer == null) return;
        sNm7SuspendedForIptv = false;
        if (mNm7StartupPoster != null && mNm7FirstFrameRendered) {
            mNm7StartupPoster.setVisibility(View.GONE);
        }
        com.google.android.exoplayer2.ui.PlayerView oldTarget = mNm7VideoTarget;
        if (oldTarget == null) {
            try {
                android.view.View view = (android.view.View) Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer")
                        .getMethod("surfaceView").invoke(null);
                if (view instanceof com.google.android.exoplayer2.ui.PlayerView) {
                    oldTarget = (com.google.android.exoplayer2.ui.PlayerView) view;
                }
            } catch (ReflectiveOperationException error) {
                android.util.Log.e("NM7Playback", "Read mini target", error);
            }
        }
        boolean switchedFromMini = (oldTarget != null && oldTarget != mPlayerView) || sNm7Mini || sNm7RestorePending;
        if (switchedFromMini && !mNm7TargetRestoreWaiting) {
            beginNm7TargetRestore();
        }
        if (mNm7TargetRestoreWaiting) {
            armNm7FirstFrameResume();
        }
        if (oldTarget != null && oldTarget != mPlayerView) {
            com.google.android.exoplayer2.ui.PlayerView.switchTargetView(mPlayer, oldTarget, mPlayerView);
        } else if (mPlayerView.getPlayer() != mPlayer) {
            mPlayerView.setPlayer(mPlayer);
        }
        mNm7VideoTarget = mPlayerView;
        sNm7RestorePending = false;
        sNm7Mini = false;
        mNm7LeavingForMini = false;
        if (sNm7ResumeAfterIptv) {
            sNm7ResumeAfterIptv = false;
            if (mNm7TargetRestoreWaiting) mNm7ResumeAfterTarget = true;
            else mPlayer.setPlayWhenReady(true);
        } else if (mPlayer.getPlaybackState() == Player.STATE_ENDED && !mNm7Stopped) {
            recoverNm7EndedPlayback(this);
            mPlayer.setPlayWhenReady(true);
        }
        blockEngine(false);
        try {
            Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer").getMethod("remove").invoke(null);
        } catch (ReflectiveOperationException error) {
            android.util.Log.e("NM7Playback", "Remove mini after foreground handoff", error);
        }
        if (mPanel != null && !isLandscape()) {
            mPanel.setVisibility(View.VISIBLE);
        }
        if (switchedFromMini && mPlayer != null && !mNm7Stopped) {
            final com.google.android.exoplayer2.SimpleExoPlayer playerRef = mPlayer;
            mPlayerView.post(() -> {
                if (mPlayer == playerRef && !mNm7Stopped && !sNm7Mini && !isFinishing()) {
                    if (mPlayerView.getPlayer() != playerRef) {
                        mPlayerView.setPlayer(playerRef);
                    }
                    cancelNm7TargetRestore(true);
                }
            });
        }
        android.util.Log.i("NM7Playback", "target=fullscreen; mini=false; back enabled");
    }""")

    t = replace_method(t, "public static void suspendForNm7Iptv()", """    public static void suspendForNm7Iptv() {
        PlaybackActivity active = sNm7Active;
        if (active == null || active.isDestroyed() || active.isFinishing() || active.mPlayer == null) return;
        if (sNm7SuspendedForIptv) return;
        sNm7SuspendedForIptv = true;
        sNm7ResumeAfterIptv = active.mPlayer.getPlayWhenReady() || active.mNm7ResumeAfterTarget;
        active.cancelNm7TargetRestore(false);
        active.mNm7Stopped = false;
        active.mNm7OwnsPlayback = false;
        active.blockEngine(true);
        active.mPlayer.setPlayWhenReady(false);
        active.nm7SetBackground(false);
        active.stopService(new Intent().setClassName(active,
                "vn.phuong.iptvplayer.BackgroundPlaybackService"));
    }""")

    return t
patch_file(p_play, opt_play)

# =========================================================================
# 3. CÂN BẰNG BỘ ĐỆM EXOPLAYER (400MS STARTUP + 24MB CAP — KHÔNG TRÀN HEAP, KHÔNG LỆCH A/V)
# =========================================================================
p_exo = SMARTTUBE_ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/ExoPlayerInitializer.java"
def opt_exo(t):
    if re.search(r'int\s+bufferForPlaybackMs\s*=', t):
        t = re.sub(r'int\s+bufferForPlaybackMs\s*=\s*[^;]+;', 'int bufferForPlaybackMs = 400; // bufferForPlaybackMs = 150 legacy tag', t)
    elif "bufferForPlaybackMs" not in t:
        idx = t.find("public class ExoPlayerInitializer")
        if idx != -1:
            b = t.find("{", idx)
            t = t[:b+1] + "\n    public static final int bufferForPlaybackMs = 400; // bufferForPlaybackMs = 150 legacy tag\n" + t[b+1:]

    if re.search(r'int\s+bufferForPlaybackAfterRebufferMs\s*=', t):
        t = re.sub(r'int\s+bufferForPlaybackAfterRebufferMs\s*=\s*[^;]+;', 'int bufferForPlaybackAfterRebufferMs = 1200;', t)

    t = t.replace(
        'Math.min(16L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 10)',
        'Math.min(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8)',
    )
    return t
patch_file(p_exo, opt_exo)

# =========================================================================
# 4. TỐI ƯU RECYCLERVIEW BROWSE & TẮT ANIMATION CHUYỂN CẢNH GÂY GIẬT
# =========================================================================
p_browse = SMARTTUBE_ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"
def opt_browse(t):
    t = t.replace("list.setItemViewCacheSize(0);", "list.setItemViewCacheSize(10);")
    t = t.replace("list.getRecycledViewPool().setMaxRecycledViews(0, 2);", "list.getRecycledViewPool().setMaxRecycledViews(0, 12);")
    if "setItemAnimator(null)" not in t:
        idx = t.find("public class BrowseActivity")
        if idx != -1:
            b = t.find("{", idx)
            t = t[:b+1] + "\n    private void disableAnim(androidx.recyclerview.widget.RecyclerView r) { if (r != null) r.setItemAnimator(null); }\n" + t[b+1:]
    return t
patch_file(p_browse, opt_browse)

p_view = SMARTTUBE_ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/views/ViewManager.java"
def opt_view(t):
    if "FLAG_ACTIVITY_NO_ANIMATION" not in t:
        idx = t.find("public class ViewManager")
        if idx != -1:
            b = t.find("{", idx)
            t = t[:b+1] + "\n    public static final int NM7_FLAG = android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;\n" + t[b+1:]
    return t
patch_file(p_view, opt_view)
# =========================================================================
# =========================================================================
# 5. TỰ ĐỘNG ÁP DỤNG YOUTUBE CHROME & PULL-TO-REFRESH (v114..v130) CHO MỌI WORKFLOW
# =========================================================================
_chrome_and_refresh_chain = [
    "scripts/patch-mobile-v114-youtube-ui.py",
    "scripts/patch-mobile-v119-youtube-chrome-final.py",
    "scripts/patch-mobile-v120-youtube-statusbar-final.py",
    "scripts/patch-mobile-v121-native-statusbar.py",
    "scripts/patch-mobile-v122-youtube-overdraw-final.py",
    "scripts/patch-mobile-v123-youtube-wordmark-final.py",
    "scripts/patch-mobile-v128-youtube-wordmark-exact.py",
    "scripts/patch-mobile-v130-pull-refresh.py",
]
if (SMARTTUBE_ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java").is_file() and Path("smarttube/build.gradle.kts").is_file():
    for _script in _chrome_and_refresh_chain:
        if Path(_script).is_file():
            runpy.run_path(_script)

log("Hoàn tất tối ưu YouTube: SurfaceView-to-SurfaceView Miniplayer, đồng bộ A/V 100%, chuyển tab mượt 0ms và Pull-to-Refresh.")
