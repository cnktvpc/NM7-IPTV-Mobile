#!/usr/bin/env python3
"""NM7 Mobile 1.10.117: Android 15 status-bar correction.

The device screenshot shows the Browse header being drawn underneath the real
status bar. Android 15 edge-to-edge means legacy decor fitting is not sufficient.
This patch explicitly shows the status bar and reserves its real inset on the
Browse root. It reapplies the same state after onResume and window focus because
SmartTube/MotherActivity can touch fullscreen flags during lifecycle callbacks.

No playback, IPTV, data, avatar, spinner, live-chat or navigation behavior is changed.
"""
from pathlib import Path
import re
import shutil

ROOT = Path("third_party/SmartTube-droid")
SRC_LAYOUT = Path("scripts/mobile-ui/res/layout/browse_activity.xml")
SRC_LOGO = Path("scripts/mobile-ui/res/drawable-nodpi/nm7_youtube_wordmark.png")
DST_LAYOUT = ROOT / "smarttubedroid/src/main/res/layout/browse_activity.xml"
DST_LOGO = ROOT / "smarttubedroid/src/main/res/drawable-nodpi/nm7_youtube_wordmark.png"
BROWSE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"

for p in (SRC_LAYOUT, SRC_LOGO, BROWSE):
    if not p.is_file():
        raise SystemExit("v117: missing " + str(p))

shutil.copyfile(SRC_LAYOUT, DST_LAYOUT)
DST_LOGO.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(SRC_LOGO, DST_LOGO)

s = BROWSE.read_text(encoding="utf-8")

# Replace the portrait system-bar method produced by v114/v116.
sig = "    private void applyNm7PortraitSystemBars() {"
pos = s.find(sig)
if pos < 0:
    raise SystemExit("v117: portrait status-bar method missing")
brace = s.find("{", pos)
depth = 0
end = -1
for i in range(brace, len(s)):
    if s[i] == "{":
        depth += 1
    elif s[i] == "}":
        depth -= 1
        if depth == 0:
            end = i + 1
            break
if end < 0:
    raise SystemExit("v117: portrait status-bar method end missing")

method = """    private void applyNm7PortraitSystemBars() {
        if (isLandscape()) return;

        final android.view.Window window = getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(android.graphics.Color.WHITE);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            // Android 15+ enforces edge-to-edge for modern target SDKs. Keep edge-to-edge
            // but explicitly reserve the actual status-bar inset in Browse root.
            window.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.show(android.view.WindowInsets.Type.statusBars());
                controller.show(android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsAppearance(
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            int flags = window.getDecorView().getSystemUiVisibility();
            flags &= ~(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }

        final View root = findViewById(R.id.browse_root);
        if (root != null) {
            final int left = root.getPaddingLeft();
            final int right = root.getPaddingRight();
            final int bottom = root.getPaddingBottom();

            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int top;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    top = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                } else {
                    top = insets.getSystemWindowInsetTop();
                }

                // Reserve the status-bar area once at the root. The header can therefore
                // never overlap the clock/network/status icons.
                v.setPadding(left, Math.max(0, top), right, bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
    }"""
s = s[:pos] + method + s[end:]

BROWSE.write_text(s, encoding="utf-8")
print("NM7 Mobile 1.10.117 status-bar correction applied")
