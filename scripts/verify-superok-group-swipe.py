#!/usr/bin/env python3
"""Regression guard for the Super OK 2.5-style horizontal group-swipe port."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
provider = (root / "app/src/main/java/vn/phuong/iptvplayer/MobileGroupSwipeProvider.java").read_text()
view = (root / "app/src/main/java/vn/phuong/iptvplayer/Nm7SuperOkGroupSwipeListView.java").read_text()
portrait = (root / "app/src/main/res/layout/activity_main.xml").read_text()
landscape = (root / "app/src/main/res/layout-land/activity_main.xml").read_text()
activity = (root / "app/src/main/java/vn/phuong/iptvplayer/MainActivity.java").read_text()
test = (root / "app/src/test/java/vn/phuong/iptvplayer/Nm7SuperOkGroupSwipeListViewTest.java").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
checks = {
    "both orientations use the custom ListView parent": all("Nm7SuperOkGroupSwipeListView" in x for x in (portrait, landscape)),
    "observer-only GestureDetector/OnTouchListener path is removed": "GestureDetector" not in provider and "setOnTouchListener" not in provider,
    "provider delegates to the custom group-swipe callback": "setOnGroupSwipeListener(this::switchGroup)" in provider,
    "dispatchTouchEvent arbitrates even after ListView claims ACTION_DOWN": "boolean dispatchTouchEvent(MotionEvent event)" in view and "super.dispatchTouchEvent(event)" in view and "swipeSequenceOwned" in view,
    "touch slop and fling velocity use Android configuration/VelocityTracker": "getScaledTouchSlop()" in view and "getScaledMinimumFlingVelocity()" in view and "VelocityTracker" in view,
    "horizontal claim cancels original tap/long-press stream": "claimHorizontalSequence(event)" in view and "cancelLongPress();" in view and "MotionEvent.ACTION_CANCEL" in view,
    "Super OK-style finger-follow and spring-back are present": "setTranslationX(clamp(dx * 0.45f" in view and "setDuration(180L)" in view and "DecelerateInterpolator(1.4f)" in view,
    "recognized swipe dispatches one adjacent-group change": "groupSwipeListener.onGroupSwipe(dx < 0f ? 1 : -1)" in view and "swipeDispatched" in view,
    "native item tap/long-press callbacks remain present": "list.setOnItemClickListener" in test and "list.setOnItemLongClickListener" in test and "channelList.setOnItemClickListener" in activity,
    "gesture tests drive dispatchTouchEvent directly in ListView local coordinates": "list.dispatchTouchEvent(e)" in test and "root.dispatchTouchEvent(e)" not in test,
    "tap uses native ListView item-click path after synthetic child actions are removed": "f.list.setOnItemLongClickListener(null)" in test and "Shadows.shadowOf(Looper.getMainLooper()).idleFor(80,TimeUnit.MILLISECONDS)" in test,
    "tests cover slow/fast/reverse/diagonal/tap/vertical/multitouch": all(name in test for name in ("slowSwipeCancelsChildLongPressAndChangesOneGroup", "fastFlickSwitchesExactlyOnce", "reverseSwipeMovesToPreviousGroup", "diagonalStartNoiseDoesNotBlockHorizontalSwipe", "verticalScrollDoesNotChangeCategoryOrTranslateList", "tapStillUsesTheCardClickHandler", "multitouchCancelsSwipe")),
    "candidate is 1.10.145 / code 162": 'versionName = "1.10.145"' in gradle and "versionCode = 162" in gradle,
}
for label, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f"PASS: {len(checks)} Super OK group-swipe regression checks")
