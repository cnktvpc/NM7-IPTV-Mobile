"""Mobile 1.10.74: reduce selected-video load stalls and hide the bottom tabs in fullscreen playback."""
from pathlib import Path
import re

# 1) Preserve an in-flight selected-video format lookup across controller/owner
# transitions. The previous cleanup path could dispose the very request that
# was already started on the user's click, forcing a second network round-trip.
root = Path("third_party/SmartTube-droid")
matches = list(root.rglob("VideoLoaderController.java"))
if len(matches) != 1:
    raise SystemExit(f"v74: expected exactly one VideoLoaderController.java, found {len(matches)}")
p = matches[0]
s = p.read_text(encoding="utf-8")

old = '''    private void disposeActions() {
        mNm7SelectedRequest.cancel();
        Utils.removeCallbacks(mShowProgressBar);'''
new = '''    private void disposeActions() {
        // NM7 1.10.74: keep the currently selected video's in-flight format lookup
        // alive while the PlaybackView owner is being rebound. Cancelling it here
        // forces a second network lookup and is visible as a long spinner on first play.
        boolean keepSelectedRequest = mPendingVideo != null
                && mNm7SelectedRequest.pendingFor(mPendingVideo.videoId);
        if (!keepSelectedRequest) {
            mNm7SelectedRequest.cancel();
            if (mFormatInfoAction != null) mFormatInfoAction.dispose();
            mFormatInfoAction = null;
        }
        Utils.removeCallbacks(mShowProgressBar);'''
if s.count(old) != 1:
    raise SystemExit("v74: disposeActions anchor missing/ambiguous")
s = s.replace(old, new, 1)
p.write_text(s, encoding="utf-8")

# 2) Keep the same 1.10.72 thumbnail quality. Never substitute mqdefault.
ui_patch = Path("scripts/patch-mobile-ui.py")
if not ui_patch.is_file() or "maxresdefault.jpg" not in ui_patch.read_text(encoding="utf-8"):
    raise SystemExit("v74: YouTube thumbnail target must remain maxresdefault.jpg")

# 3) Hide NM7's two bottom tabs while the SmartTube phone player is in the
# landscape/fullscreen state, then restore them when returning to portrait.
p = Path("app/src/main/java/vn/phuong/iptvplayer/MobileNm7Application.java")
s = p.read_text(encoding="utf-8")
anchor = '''        } else if (SMARTTUBE_PLAYBACK.equals(name)) {
            activity.getWindow().getDecorView().post(() -> HomeTabBar.attach(activity, true));'''
replacement = '''        } else if (SMARTTUBE_PLAYBACK.equals(name)) {
            activity.getWindow().getDecorView().post(() -> {
                HomeTabBar.attach(activity, true);
                final android.view.View root = activity.findViewById(android.R.id.content);
                if (root != null) {
                    final Runnable syncNm7PlayerTabs = () -> {
                        boolean fullscreen = activity.getResources().getConfiguration().orientation
                                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                        HomeTabBar.setVisible(activity, !fullscreen);
                    };
                    root.addOnLayoutChangeListener((v, left, top, right, bottom,
                            oldLeft, oldTop, oldRight, oldBottom) -> syncNm7PlayerTabs.run());
                    syncNm7PlayerTabs.run();
                }
            });'''
if s.count(anchor) != 1:
    raise SystemExit("v74: PlaybackActivity tab attach anchor missing/ambiguous")
s = s.replace(anchor, replacement, 1)
p.write_text(s, encoding="utf-8")

print("NM7 1.10.74 optimization/fullscreen patch applied")
