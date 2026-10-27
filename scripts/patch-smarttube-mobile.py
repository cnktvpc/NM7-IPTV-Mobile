from pathlib import Path
import re

root = Path("third_party/SmartTube-droid")

def require_replace(path, old, new, label):
    text = path.read_text()
    if old not in text:
        raise SystemExit(label + " not found")
    path.write_text(text.replace(old, new, 1))

browse = root / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"
require_replace(browse, "private static final int GRID_COLUMNS = 2;", "private static final int GRID_COLUMNS = 1;", "Browse GRID_COLUMNS")

uploads = root / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/channeluploads/ChannelUploadsActivity.java"
if uploads.is_file():
    u = uploads.read_text()
    uploads.write_text(u.replace("private static final int GRID_COLUMNS = 2;", "private static final int GRID_COLUMNS = 1;").replace("new GridLayoutManager(this, GRID_COLUMNS)", "new GridLayoutManager(this, 1)"))

playback = root / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"
t = playback.read_text()

if "import android.content.Intent;" not in t:
    if "import android.content.Context;" in t:
        t = t.replace("import android.content.Context;", "import android.content.Context;\nimport android.content.Intent;", 1)
    else:
        raise SystemExit("PlaybackActivity Context import not found")

stop_re = re.compile(r"(?ms)^    @Override\n    protected void onStop\(\) \{.*?^    \}\n\n    @Override\n    protected void onDestroy\(\)")
stop_new = """    @Override
    protected void onStop() {
        super.onStop();

        boolean backgroundRequested = getPlayerData().getBackgroundMode() != PlayerData.BACKGROUND_MODE_DEFAULT;
        if (VERSION.SDK_INT > 23 && !isNm7TabSwitch() && !backgroundRequested && !isEngineBlocked()) {
            maybeReleasePlayer();
        }
    }

    private boolean isNm7TabSwitch() {
        try {
            String until = System.getProperty("nm7.tab.switch.until", "0");
            return Long.parseLong(until) > System.currentTimeMillis();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    protected void onDestroy()"""
t, n = stop_re.subn(stop_new, t, count=1)
if n != 1:
    raise SystemExit("PlaybackActivity onStop block not found")

if "void onUserLeaveHint()" not in t:
    pip_marker = re.search(r"(?m)^    public boolean isInPIPMode\(\)\s*\{", t) or re.search(r"(?m)^    public boolean isInPipMode\(\)\s*\{", t)
    if not pip_marker:
        raise SystemExit("PlaybackActivity PIP method marker not found")
    leave = """    public void onUserLeaveHint() {
        if (mIsBackPressed || isFinishing()) {
            return;
        }
        getPlayerData().setBackgroundMode(PlayerData.BACKGROUND_MODE_PLAY_BEHIND);
        startNm7BackgroundService();
        enterBackgroundPlayMode();
    }

"""
    t = t[:pip_marker.start()] + leave + t[pip_marker.start():]

if "private void startNm7BackgroundService()" not in t:
    pip_marker = re.search(r"(?m)^    public boolean isInPIPMode\(\)\s*\{", t) or re.search(r"(?m)^    public boolean isInPipMode\(\)\s*\{", t)
    if not pip_marker:
        raise SystemExit("PlaybackActivity PIP method marker not found for helper")
    helper = """    private void startNm7BackgroundService() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new android.content.ComponentName(this,
                    "vn.phuong.iptvplayer.BackgroundPlaybackService"));
            intent.putExtra("youtube", true);
            intent.putExtra("channel_name", "YouTube");
            if (VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (RuntimeException ignored) {
        }
    }

"""
    t = t[:pip_marker.start()] + helper + t[pip_marker.start():]

back_re = re.compile(r"(?ms)^    @Override\n    public void onBackPressed\(\) \{.*?^    \}\n\n    @Override")
m = back_re.search(t)
if not m:
    raise SystemExit("PlaybackActivity onBackPressed block not found")
old = m.group(0)
if "startActivity(intent)" not in old:
    new_back = """    @Override
    public void onBackPressed() {
        if (mIsBackPressed) {
            return;
        }

        mIsBackPressed = true;
        try {
            Intent intent = new Intent(this,
                    Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.browse.BrowseActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            overridePendingTransition(0, 0);
        } catch (ReflectiveOperationException | RuntimeException e) {
            super.onBackPressed();
        }
    }

    @Override"""
    t = t[:m.start()] + new_back + t[m.end():]

marker = """        mPlayer.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {"""
hook = """                if (playWhenReady && playbackState == Player.STATE_READY) {
                    try {
                        Class.forName("vn.phuong.iptvplayer.MobileNm7Application")
                                .getMethod("pauseIptvForYoutube")
                                .invoke(null);
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                }

"""
if marker not in t:
    raise SystemExit("Verified SmartTube mPlayer listener insertion point not found")
if "MobileNm7Application.pauseIptvForYoutube()" not in t:
    t = t.replace(marker, marker + hook, 1)

t = t.replace("\n    @Override\n    private void startNm7BackgroundService()", "\n    private void startNm7BackgroundService()")

playback.write_text(t)
print("SmartTube Mobile lifecycle patch completed")

# Keep this phone fork compatible with the current SmartTube controller interface.
playback_text = playback.read_text()
lines = playback_text.splitlines(True)
for i, line in enumerate(lines):
    if "public float getPitch(" in line or "public void setPitch(" in line:
        if i > 0 and lines[i - 1].strip() == "@Override":
            lines[i - 1] = ""
playback_text = "".join(lines)
if "public float getPitch(" not in playback_text:
    raise SystemExit("getPitch declaration not found")
playback.write_text(playback_text)

# Keep this fork buildable when section_is_empty is absent from the phone resource table.
playback_text = playback.read_text()
playback_text = playback_text.replace('showDetailsMessage(getString(R.string.section_is_empty));', 'showDetailsMessage("No comments available");')
playback.write_text(playback_text)
