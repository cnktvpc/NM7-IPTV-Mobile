"""NM7 Mobile 1.10.85: device-video corrections for status bar, visible chat composer, and feed avatars."""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
PLAYBACK = PHONE / "playback/PlaybackActivity.java"
LAYOUT = ROOT / "smarttubedroid/src/main/res/layout/playback_activity.xml"
MEDIA = ROOT / "MediaServiceCore"

def replace_method(text, signature, replacement, label):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f"v85: {label} signature not found")
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
        raise SystemExit(f"v85: {label} closing brace not found")
    return text[:method_start] + replacement + text[end:]

# 1) Status bar: do not call DroidActivity's portrait system-bar path because its
# legacy implementation re-applies fullscreen/layout-fullscreen flags.
p = PLAYBACK
s = p.read_text(encoding="utf-8")
system_bars = """    @Override
    protected void applySystemBars() {
        if (!isLandscape()) {
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
            if (mRoot != null) mRoot.post(() -> mRoot.requestApplyInsets());
            return;
        }
        super.applySystemBars();
    }
"""
s = replace_method(s, "    protected void applySystemBars() {", system_bars, "applySystemBars")

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
            if (mRoot != null) mRoot.post(() -> mRoot.requestApplyInsets());
            return;
        }
        setNavigationBarVisible(false);
    }
"""
s = replace_method(s, "    private void applySystemUi(boolean fullscreen) {", system_ui, "applySystemUi")
p.write_text(s, encoding="utf-8")

# 2) The permanent NM7 YouTube/IPTV bar overlays the bottom of PlaybackActivity.
# Reserve explicit space inside the live-chat panel so the composer sits above it.
x = LAYOUT.read_text(encoding="utf-8")
panel = 'android:id="@+id/nm7_live_chat_panel"'
pos = x.find(panel)
if pos < 0:
    raise SystemExit("v85: live chat panel missing")
tag_start = x.rfind("<FrameLayout", 0, pos)
tag_end = x.find(">", pos)
tag = x[tag_start:tag_end+1]
if 'android:paddingBottom="64dp"' not in tag:
    if 'android:elevation="8dp"' not in tag:
        raise SystemExit("v85: live chat panel elevation anchor missing")
    tag2 = tag.replace('android:elevation="8dp"', 'android:elevation="8dp"\n        android:paddingBottom="64dp"', 1)
    x = x[:tag_start] + tag2 + x[tag_end+1:]
if 'android:id="@+id/nm7_live_chat_input"' not in x or 'android:id="@+id/nm7_live_chat_send"' not in x:
    raise SystemExit("v85: live chat composer controls missing")
LAYOUT.write_text(x, encoding="utf-8")

# 3) Avatar propagation bug: patch-mobile-ui inserted the thumbnail assignment into
# from(TileItem) because it replaced the first channelId occurrence. Add the real,
# direct assignment inside from(VideoItem), where home/search video cards are built.
media_item = MEDIA / "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/data/YouTubeMediaItem.java"
m = media_item.read_text(encoding="utf-8")
start = m.find("    public static YouTubeMediaItem from(VideoItem item) {")
end = m.find("    public static YouTubeMediaItem from(MusicItem item) {", start)
if start < 0 or end < 0:
    raise SystemExit("v85: YouTubeMediaItem.from(VideoItem) block missing")
block = m[start:end]
avatar_code = """        video.mChannelId = item.getChannelId();
        String nm7ChannelThumbnail = item.getChannelThumbnail();
        if (nm7ChannelThumbnail != null && nm7ChannelThumbnail.startsWith("http")) {
            video.mChannelThumbnailUrl = nm7ChannelThumbnail;
        }
"""
if "String nm7ChannelThumbnail = item.getChannelThumbnail();" not in block:
    old = "        video.mChannelId = item.getChannelId();\n"
    if block.count(old) != 1:
        raise SystemExit(f"v85: expected one VideoItem channelId assignment, found {block.count(old)}")
    block = block.replace(old, avatar_code, 1)
    m = m[:start] + block + m[end:]
if "public String getChannelThumbnailUrl()" not in m:
    raise SystemExit("v85: generated channel thumbnail getter missing")
media_item.write_text(m, encoding="utf-8")

print("NM7 Mobile 1.10.85 status-bar/chat-composer/avatar correction applied")
