#!/usr/bin/env python3
"""Fail-closed verification for YouTube Home pull-to-refresh across Mobile candidates."""
from pathlib import Path
import re

root = Path("third_party/SmartTube-droid")
java = (root / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java").read_text(encoding="utf-8")
layout = (root / "smarttubedroid/src/main/res/layout/browse_activity.xml").read_text(encoding="utf-8")
upstream_gradle = (root / "smarttubedroid/build.gradle").read_text(encoding="utf-8")
module_gradle = Path("smarttube/build.gradle.kts").read_text(encoding="utf-8")

checks = [
    ("exactly one Activity dispatcher contains pull observer", len(re.findall(r"(?m)^\s*public\s+boolean\s+dispatchTouchEvent\s*\(", java)) == 1 and bool(re.search(r"(?m)^\s*public\s+boolean\s+dispatchTouchEvent\s*\([^)]*MotionEvent\s+(\w+)\s*\)\s*\{\s*trackPullDownGesture\(\1\);", java))),
    ("touch start requires Home, inside content, and feed top", "isHomeSection()" in java and "isCurrentFeedAtTop()" in java and "isTouchInsidePullArea(event)" in java),
    ("downward threshold and horizontal-swipe guard are present", "density * 96f" in java and "dy > Math.abs(dx) * 1.2f" in java and "Math.abs(dx) > Math.abs(dy) * 1.25f" in java),
    ("native refresh receives first chance before fallback with duplicate guard", "mPullToRefresh.post(() ->" in java and "!mPullToRefresh.isRefreshing()" in java and "!mNativeRefreshHandledThisGesture" in java and "mNativeRefreshHandledThisGesture = true;" in java),
    ("reload uses existing presenter", "mBrowsePresenter.refresh(false);" in java),
    ("pull indicator is limited to Home", "mPullToRefresh.setEnabled(isHomeSection())" in java),
    ("refresh indicator cleans up on load/error and timeout", "finishPullRefresh();" in java and "20_000L" in java and "mPullToRefresh.setRefreshing(false)" in java),
    ("layout wraps actual Browse content", 'android:id="@+id/browse_pull_refresh"' in layout and 'android:id="@+id/browse_content"' in layout),
    ("upstream SwipeRefreshLayout dependency is declared", "androidx.swiperefreshlayout:swiperefreshlayout:1.1.0" in upstream_gradle),
    ("compiled :smarttube module has SwipeRefreshLayout dependency", 'implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")' in module_gradle),
]
for label, ok in checks:
    if not ok:
        raise SystemExit("FAIL: " + label)
    print("PASS: " + label)
print(f"PASS: {len(checks)} pull-to-refresh checks")
