#!/usr/bin/env python3
"""NM7 Mobile 1.10.118: final YouTube-style top chrome architecture correction.

The supplied video demonstrates two persistent defects:
- the top YouTube wordmark is drawn into the Android status-bar area;
- when the top chrome is shown, a large blank region appears before the first video.

Root cause found in the existing patch chain:
v57 intentionally changed Browse to an overlay FrameLayout and its custom scroll chrome
adds a permanent top padding equal to the header height. Later v114 replaced that layout
with a normal LinearLayout/AppBarLayout, while v56/v57 BrowseActivity code still expected
the overlay model. That mismatch produces the blank region and lifecycle/inset glitches.

v118 restores a coherent architecture instead:
- CoordinatorLayout root;
- AppBarLayout is a real scrolling app bar;
- RecyclerView is AppBarLayout's scrolling sibling;
- no manual header-height RecyclerView padding;
- no manual hide/show of AppBarLayout;
- status-bar inset is reserved once at the Coordinator root.

Playback/IPTV/YouTube data, thumbnails, avatars, spinner, comments and bottom navigation
remain untouched.
"""
from pathlib import Path
import re
import shutil

ROOT=Path("third_party/SmartTube-droid")
SRC_LAYOUT=Path("scripts/mobile-ui/res/layout/browse_activity.xml")
DST_LAYOUT=ROOT/"smarttubedroid/src/main/res/layout/browse_activity.xml"
BROWSE=ROOT/"smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"

if not SRC_LAYOUT.is_file() or not BROWSE.is_file():
    raise SystemExit("v118: required source missing")
shutil.copyfile(SRC_LAYOUT,DST_LAYOUT)

s=BROWSE.read_text(encoding="utf-8")

# Replace v114/v117 portrait status-bar handling. The root owns the inset exactly once.
sig="    private void applyNm7PortraitSystemBars() {"
pos=s.find(sig)
if pos<0: raise SystemExit("v118: status-bar method missing")
brace=s.find("{",pos); depth=0; end=-1
for i in range(brace,len(s)):
    if s[i]=="{": depth+=1
    elif s[i]=="}":
        depth-=1
        if depth==0:
            end=i+1; break
if end<0: raise SystemExit("v118: status-bar method end missing")

method='''    private void applyNm7PortraitSystemBars() {
        if (isLandscape()) return;

        final android.view.Window window = getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(android.graphics.Color.WHITE);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
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
            final int left = 0;
            final int right = 0;
            final int bottom = root.getPaddingBottom();
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int top = 0;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    top = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                } else {
                    top = insets.getSystemWindowInsetTop();
                }
                v.setPadding(left, Math.max(0, top), right, bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
    }
'''
s=s[:pos]+method+s[end:]

# v56 installed scroll chrome that manually changes appbar visibility and feed padding.
# With CoordinatorLayout the framework owns this behavior; neutralize the old method
# without changing its call sites.
m=re.search(r'(?ms)^    private void setNm7ChromeHidden\(boolean hidden\) \{.*?^    \}',s)
if m:
    s=s[:m.start()]+'''    private void setNm7ChromeHidden(boolean hidden) {
        // v118: AppBarLayout + ScrollingViewBehavior own chrome collapse/expand.
        // Keep the state for compatibility, but never resize/hide the app bar or feed.
        mNm7ChromeHidden = hidden;
    }'''+s[m.end():]

m=re.search(r'(?ms)^    private void installNm7ScrollChrome\(RecyclerView list\) \{.*?^    \}',s)
if m:
    s=s[:m.start()]+'''    private void installNm7ScrollChrome(RecyclerView list) {
        // v118: no manual header-height padding and no visibility toggling.
        // The RecyclerView remains a normal scrolling sibling of AppBarLayout.
        if (list != null) {
            list.setClipToPadding(true);
            list.setPadding(list.getPaddingLeft(), 0, list.getPaddingRight(), list.getPaddingBottom());
        }
    }'''+s[m.end():]

# The old v56 gesture code calls setNm7ChromeHidden. It becomes a harmless state update;
# CoordinatorLayout still performs the actual collapse/expand from nested scrolling.
# Remove any extra top padding that v57 may reintroduce during initialization.
s=s.replace('list.setPadding(list.getPaddingLeft(), header.getHeight(), list.getPaddingRight(),\n                    Math.round(64 * getResources().getDisplayMetrics().density));',
            'list.setPadding(list.getPaddingLeft(), 0, list.getPaddingRight(), 0);')

BROWSE.write_text(s,encoding="utf-8")
print("NM7 Mobile 1.10.118 coherent CoordinatorLayout/AppBarLayout chrome architecture applied")
