"""NM7 Mobile 1.10.77: correct portrait system bars and live-chat close control.

Fixes 1.10.76 based on user video comparison:
- Portrait YouTube must show the Android status/notification bar and content must begin below it.
- Remove the ineffective player-margin workaround from v76; use the actual WindowInsets/system-bar mode.
- Live chat remains a full-width panel below the 16:9 player, but now has a visible X close control.
- Closing chat restores the normal details/suggestions panel without stopping playback.
"""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PLAYBACK = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"
LAYOUT = ROOT / "smarttubedroid/src/main/res/layout/playback_activity.xml"


def once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"v77: expected exactly one {label}")
    return text.replace(old, new, 1)


s = PLAYBACK.read_text(encoding="utf-8")

# Android 11+ controller API used by the portrait/fullscreen fix.
if "import android.view.WindowInsetsController;" not in s:
    if "import android.view.WindowInsets;\n" in s:
        s = s.replace("import android.view.WindowInsets;\n", "import android.view.WindowInsets;\nimport android.view.WindowInsetsController;\n", 1)
    else:
        raise SystemExit("v77: expected WindowInsets import not found")

# Keep the upstream details-empty message; the phone resource namespace does not
# expose section_is_empty in this pinned SmartTube flavor.
s = s.replace("showDetailsMessage(getString(R.string.section_is_empty));", 'showDetailsMessage("No comments available");')

# v76 tried to compensate with a player top margin. That cannot work while the
# activity is edge-to-edge/fullscreen. Remove that workaround.
old = """            // NM7 1.10.76: keep portrait playback below the visible Android
            // status/notification bar. The player remains exactly 16:9; only
            // its top edge is inset. In fullscreen/landscape the system bars
            // are hidden and the original edge-to-edge behavior remains.
            if (!isLandscape) {
                ViewGroup.MarginLayoutParams playerLp =
                        (ViewGroup.MarginLayoutParams) mPlayerContainer.getLayoutParams();
                if (playerLp.topMargin != top) {
                    playerLp.topMargin = top;
                    mPlayerContainer.setLayoutParams(playerLp);
                }
            }

"""
count = s.count(old)
if count == 1:
    s = s.replace(old, "", 1)
elif count == 0:
    print("v77: v76 player-margin workaround already absent")
else:
    raise SystemExit("v77: expected at most one v76 player-margin workaround, found %s" % count)

# The pinned SmartTube source deliberately starts edge-to-edge. For Mobile portrait
# we instead use normal decor fitting and explicitly show status bars. Landscape keeps
# the existing fullscreen behavior.
old = """    private void applySystemUi(boolean fullscreen) {
        setNavigationBarVisible(!fullscreen);
    }
"""
new = """    private void applySystemUi(boolean fullscreen) {
        if (!fullscreen) {
            // NM7 1.10.77: portrait must NOT be edge-to-edge. The Android status
            // bar/notification icons remain visible, and the ConstraintLayout
            // content starts below it. This is the actual system-window fix;
            // a player top-margin is not sufficient when the activity is fullscreen.
            if (VERSION.SDK_INT >= 30) {
                getWindow().setDecorFitsSystemWindows(true);
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars());
                    controller.show(WindowInsets.Type.navigationBars());
                }
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            setNavigationBarVisible(true);
            mRoot.post(() -> mRoot.requestApplyInsets());
            return;
        }

        // Keep the existing 1.10.75 fullscreen behavior for landscape/PIP.
        if (VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        setNavigationBarVisible(false);
    }
"""
s = once(s, old, new, "applySystemUi")

# Explicitly clear the legacy fullscreen flag whenever portrait is entered.
old = """        mPanel.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        updateDetailsVisibility();
"""
new = """        mPanel.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        updateDetailsVisibility();

        if (!fullscreen && VERSION.SDK_INT < 30) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
"""
s = once(s, old, new, "portrait fullscreen clear")

# The X button controls the chat panel only; playback is untouched.
old = """        mNm7LiveChatStatus = findViewById(R.id.nm7_live_chat_status);
        mNm7LiveChatAdapter = new Nm7LiveChatAdapter(this);
"""
new = """        mNm7LiveChatStatus = findViewById(R.id.nm7_live_chat_status);
        TextView nm7LiveChatClose = findViewById(R.id.nm7_live_chat_close);
        nm7LiveChatClose.setOnClickListener(v -> {
            mNm7LiveChatPanel.setVisibility(View.GONE);
            if (mPanel != null) mPanel.setVisibility(View.VISIBLE);
            updateDetailsVisibility();
        });
        mNm7LiveChatAdapter = new Nm7LiveChatAdapter(this);
"""
s = once(s, old, new, "chat close listener")

PLAYBACK.write_text(s, encoding="utf-8")

# Replace the chat header's status-only right side with status + X.
s = LAYOUT.read_text(encoding="utf-8")
old = """                <TextView
                    android:id="@+id/nm7_live_chat_status"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginEnd="8dp"
                    android:text="Đang kết nối..."
                    android:textColor="#777777"
                    android:textSize="12sp" />
"""
new = """                <TextView
                    android:id="@+id/nm7_live_chat_status"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginEnd="10dp"
                    android:text="Đang kết nối..."
                    android:textColor="#777777"
                    android:textSize="12sp" />

                <TextView
                    android:id="@+id/nm7_live_chat_close"
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:text="×"
                    android:textColor="#222222"
                    android:textSize="32sp"
                    android:textStyle="normal"
                    android:contentDescription="Đóng trò chuyện trực tiếp" />
"""
s = once(s, old, new, "chat header close button")
LAYOUT.write_text(s, encoding="utf-8")

print("NM7 Mobile v1.10.77 UI correction applied")
