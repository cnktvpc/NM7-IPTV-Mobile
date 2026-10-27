#!/usr/bin/env python3
"""NM7 Mobile 1.10.120 — fix the last status-bar overlap using the actual Browse root.

Video evidence: in 223380.mp4, the status icons are drawn above the first 55–70px of
the YouTube wordmark. The previous v119 listener was attached to the DECOR view and
therefore did not reliably dispatch the inset to browse_root after SmartTube changed
window flags. The v119 spacer consequently stayed 0px.

This patch attaches the inset listener directly to browse_root using ViewCompat, forces
an immediate and posted requestApplyInsets(), and reapplies after focus. The status
bar remains visible and white. No feed/playback behavior changes.
"""
from pathlib import Path
import re

ROOT=Path("third_party/SmartTube-droid")
BROWSE=ROOT/"smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"
if not BROWSE.is_file():
    raise SystemExit("v120: BrowseActivity missing")

s=BROWSE.read_text(encoding="utf-8")

sig="    private void applyNm7PortraitSystemBars() {"
pos=s.find(sig)
if pos<0:
    raise SystemExit("v120: applyNm7PortraitSystemBars missing")
brace=s.find("{",pos); depth=0; end=-1
for i in range(brace,len(s)):
    if s[i]=="{": depth+=1
    elif s[i]=="}":
        depth-=1
        if depth==0:
            end=i+1
            break
if end<0:
    raise SystemExit("v120: status bar method end missing")

method='''    private void applyNm7PortraitSystemBars() {
        if (isLandscape()) return;

        final android.view.Window window = getWindow();
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(android.graphics.Color.WHITE);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            // Android 15 target apps are edge-to-edge. Keep it, but reserve the actual
            // status bar region at the Browse ROOT itself.
            window.setDecorFitsSystemWindows(false);
            final android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.show(android.view.WindowInsets.Type.statusBars());
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    controller.show(android.view.WindowInsets.Type.navigationBars());
                }
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    controller.setSystemBarsAppearance(
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        } else {
            int flags=window.getDecorView().getSystemUiVisibility();
            flags &= ~(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }

        final View root = findViewById(R.id.browse_root);
        final View spacer = findViewById(R.id.nm7_status_bar_spacer);
        if (root == null || spacer == null) return;

        // IMPORTANT: listen on browse_root itself. Listening on DecorView is unreliable
        // after SmartTube/MotherActivity changes the Window flags.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (v, insets) -> {
                    final androidx.core.graphics.Insets bars =
                            insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars());
                    final int safeTop = Math.max(0, bars.top);

                    android.view.ViewGroup.LayoutParams lp = spacer.getLayoutParams();
                    if (lp.height != safeTop) {
                        lp.height = safeTop;
                        spacer.setLayoutParams(lp);
                    }
                    return insets;
                });

        // Apply synchronously when possible, then retry on the next UI frame. This covers
        // the case where the listener is installed before the window is attached.
        androidx.core.view.ViewCompat.requestApplyInsets(root);
        root.post(() -> androidx.core.view.ViewCompat.requestApplyInsets(root));
        root.postDelayed(() -> androidx.core.view.ViewCompat.requestApplyInsets(root), 120);
    }
'''
s=s[:pos]+method+s[end:]

BROWSE.write_text(s,encoding="utf-8")
print("NM7 Mobile 1.10.120 direct root WindowInsets fix applied")
