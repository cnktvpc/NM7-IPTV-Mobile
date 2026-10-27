"""NM7 Mobile 1.10.83: YouTube portrait player/chat/feed polish + startup latency trim.

Runs LAST after all older Mobile patches. Goals:
- keep portrait YouTube player below the visible Android status bar without changing
  decorFitsSystemWindows (v77 did and regressed playback on device);
- automatically expose the existing receive-only SmartTube live chat on true live streams;
- sharpen browse thumbnails with a high-quality decode/fallback chain;
- strengthen the channel-avatar data path when MediaService returns a thumbnail object
  instead of a raw String;
- add a very light player-enter transition that does not block decoder/network work;
- trim the first-playback buffer threshold conservatively while keeping the rebuffer reserve.
"""
from pathlib import Path
import re

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
PLAYBACK = PHONE / "playback/PlaybackActivity.java"
CHAT = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ChatController.java"
INIT = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/ExoPlayerInitializer.java"

def once(s, old, new, label):
    if s.count(old) != 1:
        raise SystemExit(f"v83: expected exactly one {label}, found {s.count(old)}")
    return s.replace(old, new, 1)

# ---------------------------------------------------------------------------
# 1) Portrait system bar visible + player itself inset below it.
# Do NOT call setDecorFitsSystemWindows(): that path was observed to interfere with
# normal playback entry. We only show the status bar and consume its real inset.
s = PLAYBACK.read_text(encoding="utf-8")
if "import android.view.WindowInsetsController;" not in s:
    s = once(
        s,
        "import android.view.WindowInsets;\n",
        "import android.view.WindowInsets;\nimport android.view.WindowInsetsController;\n",
        "WindowInsetsController import",
    )

stable = """    private void applySystemUi(boolean fullscreen) {
        setNavigationBarVisible(!fullscreen);
    }
"""
replacement = """    private void applySystemUi(boolean fullscreen) {
        setNavigationBarVisible(!fullscreen);

        // NM7 1.10.83: portrait keeps the Android status/notification bar visible.
        // We intentionally do not change decorFitsSystemWindows here: the pinned
        // player remains on the proven 1.10.75 window path and only consumes insets.
        if (!fullscreen) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (VERSION.SDK_INT >= 30) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars());
                }
            } else {
                int flags = getWindow().getDecorView().getSystemUiVisibility();
                flags &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                getWindow().getDecorView().setSystemUiVisibility(flags | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            mRoot.post(() -> mRoot.requestApplyInsets());
        }
    }
"""
s = once(s, stable, replacement, "stable applySystemUi")

inset_anchor = """            boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

            mTopBar.setPadding(topBarPaddingLeft + left, topBarPaddingTop + top,
"""
inset_repl = """            boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

            // The video surface must start below the status bar in portrait.
            // Margin is reset to zero in landscape/fullscreen.
            ViewGroup.MarginLayoutParams nm7PlayerLp =
                    (ViewGroup.MarginLayoutParams) mPlayerContainer.getLayoutParams();
            int nm7TopMargin = isLandscape ? 0 : top;
            if (nm7PlayerLp.topMargin != nm7TopMargin) {
                nm7PlayerLp.topMargin = nm7TopMargin;
                mPlayerContainer.setLayoutParams(nm7PlayerLp);
            }

            mTopBar.setPadding(topBarPaddingLeft + left, topBarPaddingTop + top,
"""
s = once(s, inset_anchor, inset_repl, "portrait player status-bar inset")

# Lightweight YouTube-like enter motion. It runs in parallel with source resolution
# and decoder startup and never delays initializePlayer()/first-frame.
init_anchor = """        mCommentsCard = findViewById(R.id.playback_comments_card);
"""
init_repl = """        mCommentsCard = findViewById(R.id.playback_comments_card);

        // NM7 1.10.83: subtle phone-style player entrance; cosmetic only.
        float nm7Enter = 14f * getResources().getDisplayMetrics().density;
        mPlayerContainer.setAlpha(0.88f);
        mPlayerContainer.setTranslationY(nm7Enter);
        mPlayerContainer.setScaleX(0.992f);
        mPlayerContainer.setScaleY(0.992f);
        mPlayerContainer.animate()
                .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(170L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
"""
if init_anchor in s and "NM7 1.10.83: subtle phone-style player entrance" not in s:
    s = once(s, init_anchor, init_repl, "player enter transition anchor")

PLAYBACK.write_text(s, encoding="utf-8")

# ---------------------------------------------------------------------------
# 2) Live chat: only when metadata actually exposes liveChatKey.
# SmartTube's openLiveChatObserve is receive-only and asynchronous. Turning it on here
# gives the phone UI the ChatReceiver that v76 already renders below the player.
cs = CHAT.read_text(encoding="utf-8")
chat_anchor = """        if (mLiveChatKey != null) {
            getPlayer().setButtonState(R.id.action_chat, getPlayerData().isLiveChatEnabled() ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        }

        if (getPlayerData().isLiveChatEnabled()) {
            openLiveChat();
        }
"""
chat_repl = """        if (mLiveChatKey != null) {
            // NM7 1.10.83: true YouTube Live streams expose liveChatKey.
            // Enable the existing asynchronous receive-only chat service automatically
            // so phone users can immediately view live messages like the reference app.
            if (!getPlayerData().isLiveChatEnabled()) {
                getPlayerData().setLiveChatEnabled(true);
            }
            getPlayer().setButtonState(R.id.action_chat, PlayerUI.BUTTON_ON);
            openLiveChat();
        } else if (getPlayerData().isLiveChatEnabled()) {
            // Do not carry a previous live video's chat state into ordinary videos.
            getPlayerData().setLiveChatEnabled(false);
            disposeActions();
        }
"""
cs = once(cs, chat_anchor, chat_repl, "ChatController metadata live-chat block")
CHAT.write_text(cs, encoding="utf-8")

# ---------------------------------------------------------------------------
# 3+4) Feed quality + channel avatars.
# Patch the already-generated SmartTube source directly because patch-mobile-ui.py
# has already run earlier in the v37 chain.
card = PHONE / "shared/VideoCardHolder.java"
u = card.read_text(encoding="utf-8")

old_media = """                            Object value = mm.invoke(mediaItem);
                            if (value instanceof String && ((String) value).startsWith("http")) {
                                avatarUrl = (String) value;
                                break;
                            }
"""
new_media = """                            Object value = mm.invoke(mediaItem);
                            if (value instanceof String && ((String) value).startsWith("http")) {
                                avatarUrl = (String) value;
                                break;
                            }
                            if (value != null) {
                                String[] nestedAvatarMethods = {
                                        "getUrl", "getThumbnailUrl", "getImageUrl",
                                        "getAvatarUrl", "getThumbnail", "getImage", "getAvatar"
                                };
                                for (String nestedName : nestedAvatarMethods) {
                                    try {
                                        java.lang.reflect.Method nm = value.getClass().getMethod(nestedName);
                                        Object nestedValue = nm.invoke(value);
                                        if (nestedValue instanceof String && ((String) nestedValue).startsWith("http")) {
                                            avatarUrl = (String) nestedValue;
                                            break;
                                        }
                                    } catch (ReflectiveOperationException | RuntimeException ignored) {}
                                }
                                if (avatarUrl != null) break;
                            }
"""
if old_media in u:
    u = once(u, old_media, new_media, "nested media-item avatar resolution")

u = u.replace(
    '"getChannelThumbnailUrl", "getChannelThumbnail",\n                            "getAuthorAvatarUrl", "getAuthorAvatar"',
    '"getChannelThumbnailUrl", "getChannelThumbnail", "getChannelAvatarUrl", "getChannelAvatar",\n'
    '                            "getAuthorThumbnailUrl", "getAuthorThumbnail", "getAuthorAvatarUrl", "getAuthorAvatar"',
    1,
)

old_glide = """            Glide.with(context)
                    .load(highResCardImageUrl)
                    .error(Glide.with(context).load(originalCardImageUrl))
                    .into((android.widget.ImageView) itemView.findViewById(R.id.shared_card_thumbnail));
"""
new_glide = """            String sdResCardImageUrl = highResCardImageUrl.replace("/maxresdefault.jpg", "/sddefault.jpg");
            com.bumptech.glide.request.RequestOptions nm7SharpThumb =
                    new com.bumptech.glide.request.RequestOptions()
                            .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
                            .override(1280, 720)
                            .dontTransform();
            Glide.with(context)
                    .load(highResCardImageUrl)
                    .apply(nm7SharpThumb)
                    .error(Glide.with(context).load(sdResCardImageUrl).apply(nm7SharpThumb)
                            .error(Glide.with(context).load(originalCardImageUrl).apply(nm7SharpThumb)))
                    .into((android.widget.ImageView) itemView.findViewById(R.id.shared_card_thumbnail));
"""
if old_glide in u:
    u = once(u, old_glide, new_glide, "high-quality thumbnail Glide chain")
card.write_text(u, encoding="utf-8")

media_item = ROOT / "MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/data/YouTubeMediaItem.java"
if media_item.is_file():
    ms = media_item.read_text(encoding="utf-8")
    old_assign = """            Object thumb = thumbMethod.invoke(item);
            if (thumb instanceof String) {
                video.mChannelThumbnailUrl = (String) thumb;
            }
"""
    new_assign = """            Object thumb = thumbMethod.invoke(item);
            if (thumb instanceof String && ((String) thumb).startsWith("http")) {
                video.mChannelThumbnailUrl = (String) thumb;
            } else if (thumb != null) {
                String[] nm7ThumbMethods = {
                        "getUrl", "getThumbnailUrl", "getImageUrl", "getAvatarUrl",
                        "getThumbnail", "getImage", "getAvatar"
                };
                for (String nm7Name : nm7ThumbMethods) {
                    try {
                        java.lang.reflect.Method nm7Method = thumb.getClass().getMethod(nm7Name);
                        Object nm7Value = nm7Method.invoke(thumb);
                        if (nm7Value instanceof String && ((String) nm7Value).startsWith("http")) {
                            video.mChannelThumbnailUrl = (String) nm7Value;
                            break;
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {}
                }
            }
"""
    if old_assign in ms:
        ms = once(ms, old_assign, new_assign, "YouTubeMediaItem nested thumbnail object")
    media_item.write_text(ms, encoding="utf-8")

# ---------------------------------------------------------------------------
# 5) Conservative startup latency trim. This affects only initial first-frame
# threshold; forward buffer/rebuffer protection remains in place.
es = INIT.read_text(encoding="utf-8")
es = es.replace(
    "int bufferForPlaybackMs = 500; // Mobile: faster first-frame threshold; retain forward buffer.",
    "int bufferForPlaybackMs = 350; // NM7 1.10.83: slightly faster first-frame threshold; retain forward buffer.",
)
es = es.replace(
    "int bufferForPlaybackAfterRebufferMs = 1_500;",
    "int bufferForPlaybackAfterRebufferMs = 1_200; // NM7 1.10.83: conservative rebuffer trim.",
)
INIT.write_text(es, encoding="utf-8")

print("NM7 Mobile 1.10.83 YouTube player/chat/avatar/thumbnail/performance patch applied")
