#!/usr/bin/env python3
"""NM7 Mobile 1.10.119 — final video-driven YouTube Browse chrome fix."""
from pathlib import Path
import re
import shutil

ROOT = Path("third_party/SmartTube-droid")
SRC = Path("scripts/mobile-ui/res/layout/browse_activity.xml")
DST = ROOT / "smarttubedroid/src/main/res/layout/browse_activity.xml"
BROWSE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"

if not SRC.is_file() or not BROWSE.is_file():
    raise SystemExit("v119: required source file missing")

# Start from the v114-compatible LinearLayout, then convert it AFTER v114.
xml = SRC.read_text(encoding="utf-8")
xml = xml.replace(
    '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"',
    '<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"',
    1)
xml = xml.replace('    android:orientation="vertical"\n', '', 1)

# Dedicated status-bar spacer. It is a real layout child, not root padding, so the header
# can never be drawn into the status-bar region even when Android 15 forces edge-to-edge.
if 'android:id="@+id/nm7_status_bar_spacer"' not in xml:
    marker = '    <com.google.android.material.appbar.AppBarLayout\n'
    if marker not in xml:
        raise SystemExit("v119: AppBarLayout marker missing")
    spacer = '''    <View
        android:id="@+id/nm7_status_bar_spacer"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:background="#FFFFFF" />

'''
    xml = xml.replace(marker, spacer + marker, 1)

xml = xml.replace(
    '''        android:background="#FFFFFF"
        android:elevation="2dp">''',
    '''        android:background="#FFFFFF"
        android:elevation="2dp"
        app:liftOnScroll="false">''', 1)

xml = xml.replace(
    '''            android:gravity="center_vertical"
            android:orientation="horizontal">''',
    '''            android:gravity="center_vertical"
            android:orientation="horizontal"
            app:layout_scrollFlags="scroll|enterAlways">''', 1)

xml = xml.replace(
    '''            android:fillViewport="false"
            android:paddingStart="8dp"''',
    '''            android:fillViewport="false"
            app:layout_scrollFlags="scroll|enterAlways"
            android:paddingStart="8dp"''', 1)

# Match the YouTube reference wordmark scale.
xml = xml.replace(
    '''                android:layout_width="76dp"
                android:layout_height="30dp"''',
    '''                android:layout_width="92dp"
                android:layout_height="36dp"''', 1)

xml = xml.replace(
    '''        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">''',
    '''        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">''', 1)

# Remove all artificial feed top gaps. Keep horizontal card margins and bottom room.
xml = xml.replace('android:paddingTop="8dp"\n            android:paddingStart="12dp"',
                  'android:paddingTop="0dp"\n            android:paddingStart="12dp"')
xml = xml.replace('android:paddingTop="0dp"\n            android:paddingBottom="12dp"',
                  'android:paddingTop="0dp"\n            android:paddingBottom="0dp"')

if not xml.rstrip().endswith('</LinearLayout>'):
    raise SystemExit("v119: root closing LinearLayout missing")
head, tail = xml.rsplit('</LinearLayout>', 1)
xml = head + '</androidx.coordinatorlayout.widget.CoordinatorLayout>' + tail
DST.parent.mkdir(parents=True, exist_ok=True)
DST.write_text(xml, encoding="utf-8")

s = BROWSE.read_text(encoding="utf-8")

# --- Status bar: dedicated spacer ---
sig = "    private void applyNm7PortraitSystemBars() {"
pos = s.find(sig)
if pos < 0:
    raise SystemExit("v119: status-bar method missing")
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
    raise SystemExit("v119: status-bar method end missing")

method = '''    private void applyNm7PortraitSystemBars() {
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

        final View spacer = findViewById(R.id.nm7_status_bar_spacer);
        if (spacer != null) {
            getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
                int top;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    top = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                } else {
                    top = insets.getSystemWindowInsetTop();
                }
                android.view.ViewGroup.LayoutParams lp = spacer.getLayoutParams();
                int safeTop = Math.max(0, top);
                if (lp.height != safeTop) {
                    lp.height = safeTop;
                    spacer.setLayoutParams(lp);
                }
                return insets;
            });
            getWindow().getDecorView().requestApplyInsets();
        }
    }
'''
s = s[:pos] + method + s[end:]

# --- Disable old manual chrome resizing. CoordinatorLayout owns the transition. ---
m = re.search(r'(?ms)^    private void setNm7ChromeHidden\(boolean hidden\) \{.*?^    \}', s)
if m:
    s = s[:m.start()] + '''    private void setNm7ChromeHidden(boolean hidden) {
        mNm7ChromeHidden = hidden;
    }''' + s[m.end():]

m = re.search(r'(?ms)^    private void installNm7ScrollChrome\(RecyclerView list\) \{.*?^    \}', s)
if m:
    s = s[:m.start()] + '''    private void installNm7ScrollChrome(RecyclerView list) {
        if (list != null) {
            list.setClipToPadding(true);
            list.setPadding(list.getPaddingLeft(), 0, list.getPaddingRight(), 0);
        }
    }''' + s[m.end():]

# --- Correct stale initial scroll offset without fighting user scrolling later. ---
if "mNm7FirstFeedLayoutFixed" not in s:
    anchor = "    private boolean mJustCreated;"
    if anchor not in s:
        raise SystemExit("v119: field anchor missing")
    s = s.replace(anchor, anchor + "\n    private boolean mNm7FirstFeedLayoutFixed;", 1)

if "private void nm7FixInitialFeedPosition" not in s:
    anchor = "    private void initContent() {"
    helper = '''    private void nm7FixInitialFeedPosition() {
        if (mNm7FirstFeedLayoutFixed) return;
        mNm7FirstFeedLayoutFixed = true;
        RecyclerView view = getCurrentContentView();
        if (view != null) {
            view.stopScroll();
            if (view.getLayoutManager() != null) {
                view.getLayoutManager().scrollToPosition(0);
            }
            view.post(() -> {
                if (view.getLayoutManager() != null) {
                    view.getLayoutManager().scrollToPosition(0);
                }
            });
        }
    }

'''
    if anchor not in s:
        raise SystemExit("v119: initContent anchor missing")
    s = s.replace(anchor, helper + anchor, 1)

focus_old = '''        BrowseSection section = mSections.get(position);
        mCurrentSection = section;
        showContentForType(section.getType());

        mBrowsePresenter.onSectionFocused(section.getId());'''
focus_new = '''        BrowseSection section = mSections.get(position);
        mCurrentSection = section;
        mNm7FirstFeedLayoutFixed = false;
        showContentForType(section.getType());
        nm7FixInitialFeedPosition();

        mBrowsePresenter.onSectionFocused(section.getId());'''
if focus_old in s:
    s = s.replace(focus_old, focus_new, 1)

update_anchor = '''        showContentForType(type);

        if (type == BrowseSection.TYPE_ROW) {'''
if update_anchor in s:
    s = s.replace(update_anchor,
                  '''        showContentForType(type);
        nm7FixInitialFeedPosition();

        if (type == BrowseSection.TYPE_ROW) {''', 1)

BROWSE.write_text(s, encoding="utf-8")
print("NM7 Mobile 1.10.119 final video-driven YouTube chrome correction applied")
