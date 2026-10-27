#!/usr/bin/env python3
"""Regression checks for the in-player Live server selector in fullscreen mode."""
from pathlib import Path

read = lambda path: Path(path).read_text(encoding="utf-8")
player = read("app/src/main/java/vn/phuong/iptvplayer/MobileInlinePlayerProviderV2.java")
gradle = read("app/build.gradle.kts")
workflow = read(".github/workflows/nm7-mobile-live-events-v132.yml")

checks = [
    ("fullscreen selector is installed within the Media3 controller row",
     "private android.widget.Button fullscreenLiveServerButton;" in player
     and "fullscreenLiveServerButton = new android.widget.Button(activity);" in player
     and "parent.addView(fullscreenLiveServerButton" in player),
    ("fullscreen selector invokes the existing Live picker",
     "fullscreenLiveServerButton.setOnClickListener(v -> showLiveServerPicker())" in player
     and "private void showLiveServerPicker()" in player),
    ("selector is hidden unless fullscreen Live has multiple server sources",
     "if (!fullscreen || channel == null || event == null || event.sources().size() < 2)" in player
     and "fullscreenLiveServerButton.setVisibility(View.VISIBLE)" in player
     and "fullscreenLiveServerButton.setVisibility(View.GONE)" in player),
    ("enter and exit fullscreen refresh selector visibility",
     "fullscreen = true;\n        updateFullscreenLiveServerButton(currentChannel);" in player
     and "fullscreen = false;\n        updateFullscreenLiveServerButton(currentChannel);" in player),
    ("the ordinary inline selector is preserved",
     "private android.widget.Button liveServerButton;" in player
     and "liveServerButton.setOnClickListener(v -> showLiveServerPicker())" in player),
    ("candidate version is 1.10.136 / code 152",
     'versionName = "1.10.136"' in gradle and "versionCode = 152" in gradle),
    ("dedicated CI runs this check on the candidate branch",
     "python3 scripts/verify-mobile-live-fullscreen-server-v136.py" in workflow
     and "fix/mobile-1.10.136-live-fullscreen-server" in workflow),
]
for label, passed in checks:
    if not passed:
        raise SystemExit("FAIL: " + label)
    print("PASS: " + label)
print(f"PASS: {len(checks)} fullscreen Live server checks")
