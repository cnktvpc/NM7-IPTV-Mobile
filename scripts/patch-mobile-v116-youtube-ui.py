#!/usr/bin/env python3
"""NM7 Mobile 1.10.116: fix the two remaining visual defects from device screenshot.

Scope is Browse chrome only:
1. Real Android status bar + proper non-edge-to-edge layout so the YouTube wordmark
   is fully visible below the status bar.
2. Remove stale RecyclerView decorations/offsets and force the first feed item to
   start immediately below the chip row (no large white gap).
3. Use the tight wordmark crop extracted from the supplied YouTube reference image.

All playback/IPTV/YouTube data and navigation behavior is unchanged.
"""
from pathlib import Path
import re
import shutil

ROOT=Path("third_party/SmartTube-droid")
LAYOUT_SRC=Path("scripts/mobile-ui/res/layout/browse_activity.xml")
LOGO_SRC=Path("scripts/mobile-ui/res/drawable-nodpi/nm7_youtube_wordmark.png")
LAYOUT_DST=ROOT/"smarttubedroid/src/main/res/layout/browse_activity.xml"
LOGO_DST=ROOT/"smarttubedroid/src/main/res/drawable-nodpi/nm7_youtube_wordmark.png"
BROWSE=ROOT/"smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"

for p in (LAYOUT_SRC,LOGO_SRC,BROWSE):
    if not p.is_file():
        raise SystemExit("v116: missing "+str(p))

shutil.copyfile(LAYOUT_SRC,LAYOUT_DST)
LOGO_DST.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(LOGO_SRC,LOGO_DST)

s=BROWSE.read_text(encoding="utf-8")

# Tight wordmark sizing: reference is approximately 125px wide on the user's device.
s=s.replace(
'''            android:layout_width="52dp"
            android:layout_height="20dp"
            android:src="@drawable/nm7_youtube_wordmark"''',
'''            android:layout_width="76dp"
            android:layout_height="30dp"
            android:src="@drawable/nm7_youtube_wordmark"''',1
) if False else s

# Replace the existing v114 portrait bar method. The key is to use the platform's
# normal fitting behavior rather than adding the status-bar inset twice.
sig="    private void applyNm7PortraitSystemBars() {"
pos=s.find(sig)
if pos<0: raise SystemExit("v116: portrait-system-bar method missing")
brace=s.find("{",pos); depth=0; end=-1
for i in range(brace,len(s)):
    if s[i]=="{": depth+=1
    elif s[i]=="}":
        depth-=1
        if depth==0:
            end=i+1; break
if end<0: raise SystemExit("v116: portrait-system-bar method end missing")
method='''    private void applyNm7PortraitSystemBars() {
        if (isLandscape()) {
            return;
        }

        android.view.Window window = getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(android.graphics.Color.WHITE);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true);
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

        // With decor fitting enabled the system places the content below the status bar.
        // Do not add a second top inset here.
        View appBar = findViewById(R.id.browse_app_bar);
        if (appBar != null) {
            appBar.setPadding(appBar.getPaddingLeft(), 0,
                    appBar.getPaddingRight(), appBar.getPaddingBottom());
            appBar.setOnApplyWindowInsetsListener(null);
        }
    }
'''
s=s[:pos]+method+s[end:]

# Install a first-frame/first-data feed normalization pass.
field="    private boolean mJustCreated;"
if "private boolean mNm7FeedPositionNormalized;" not in s:
    if field not in s: raise SystemExit("v116: JustCreated field missing")
    s=s.replace(field,field+"\n    private boolean mNm7FeedPositionNormalized;",1)

helper_anchor="    // ------------------------------------------------------------------ init\n"
helper='''    private void normalizeNm7FeedRecyclerView(RecyclerView view) {
        if (view == null) return;
        view.setClipToPadding(true);
        view.setPadding(view.getPaddingLeft(), 0, view.getPaddingRight(), view.getPaddingBottom());
        while (view.getItemDecorationCount() > 0) {
            view.removeItemDecorationAt(view.getItemDecorationCount() - 1);
        }
        if (view.getLayoutManager() instanceof androidx.recyclerview.widget.LinearLayoutManager) {
            androidx.recyclerview.widget.LinearLayoutManager lm =
                    (androidx.recyclerview.widget.LinearLayoutManager) view.getLayoutManager();
            lm.setReverseLayout(false);
            lm.setStackFromEnd(false);
            lm.scrollToPositionWithOffset(0, 0);
        } else {
            view.scrollToPosition(0);
        }
        view.setTranslationY(0f);
    }

    private void normalizeNm7FeedPositionOnce() {
        if (mNm7FeedPositionNormalized) return;
        mNm7FeedPositionNormalized = true;
        if (mGridView != null) {
            normalizeNm7FeedRecyclerView(mGridView);
            mGridView.post(() -> normalizeNm7FeedRecyclerView(mGridView));
        }
        if (mRowsView != null) {
            normalizeNm7FeedRecyclerView(mRowsView);
            mRowsView.post(() -> normalizeNm7FeedRecyclerView(mRowsView));
        }
    }

'''
if "private void normalizeNm7FeedRecyclerView" not in s:
    if helper_anchor not in s: raise SystemExit("v116: init anchor missing")
    s=s.replace(helper_anchor,helper_anchor+helper,1)

# Call normalization after content views/adapters are initialized.
anchor="""        mSettingsAdapter = new BrowseSettingsAdapter();
"""
if "normalizeNm7FeedRecyclerView(mGridView);" not in s:
    if anchor not in s: raise SystemExit("v116: content setup anchor missing")
    s=s.replace(anchor,"""        mSettingsAdapter = new BrowseSettingsAdapter();
        normalizeNm7FeedRecyclerView(mGridView);
        normalizeNm7FeedRecyclerView(mRowsView);
""",1)

# Force first data batch to start at absolute offset 0, not a restored offset.
old='''        // Content arrived — make sure the error state is gone (TV's restoreMainFragment analog)
        showContentForType(type);

        if (type == BrowseSection.TYPE_ROW) {
'''
new='''        // Content arrived — make sure the error state is gone (TV's restoreMainFragment analog)
        showContentForType(type);
        normalizeNm7FeedPositionOnce();

        if (type == BrowseSection.TYPE_ROW) {
'''
if old not in s: raise SystemExit("v116: updateSection anchor missing")
s=s.replace(old,new,1)

BROWSE.write_text(s,encoding="utf-8")
print("NM7 Mobile 1.10.116 final status-bar/gap correction applied")
