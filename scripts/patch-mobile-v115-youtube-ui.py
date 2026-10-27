#!/usr/bin/env python3
"""NM7 Mobile 1.10.115: final YouTube browse visual alignment pass.

Built on v1.10.114 only. Scope:
- use the exact YouTube wordmark crop from the supplied reference screenshot;
- remove the initial white gap caused by restored RecyclerView scroll state;
- use YouTube-like chip typography/casing/spacing;
- keep all playback, IPTV, avatar, spinner, live-chat and lifecycle behavior untouched.
"""
from pathlib import Path
import re
import shutil

ROOT = Path("third_party/SmartTube-droid")
SRC_LAYOUT = Path("scripts/mobile-ui/res/layout/browse_activity.xml")
SRC_LOGO = Path("scripts/mobile-ui/res/drawable-nodpi/nm7_youtube_wordmark.jpg")
DST_LAYOUT = ROOT / "smarttubedroid/src/main/res/layout/browse_activity.xml"
DST_LOGO = ROOT / "smarttubedroid/src/main/res/drawable-nodpi/nm7_youtube_wordmark.jpg"
BROWSE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"

for p in (SRC_LAYOUT, SRC_LOGO, BROWSE):
    if not p.is_file():
        raise SystemExit("v115: missing required source: " + str(p))

shutil.copyfile(SRC_LAYOUT, DST_LAYOUT)
DST_LOGO.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(SRC_LOGO, DST_LOGO)

s = BROWSE.read_text(encoding="utf-8")

# Remove the large blank area seen under the topic chips: the phone RecyclerView can
# restore a stale scroll position from the preceding section/activity state. YouTube's
# Browse sections open at the top, so explicitly reset when a section is first focused.
focus_old = """        BrowseSection section = mSections.get(position);
        mCurrentSection = section;
        showContentForType(section.getType());
"""
focus_new = """        BrowseSection section = mSections.get(position);
        BrowseSection previousSection = mCurrentSection;
        mCurrentSection = section;
        showContentForType(section.getType());

        if (previousSection == null || previousSection.getId() != section.getId()) {
            if (mGridView != null) {
                mGridView.stopScroll();
                mGridView.scrollToPosition(0);
            }
            if (mRowsView != null) {
                mRowsView.stopScroll();
                mRowsView.scrollToPosition(0);
            }
        }
"""
if focus_old not in s:
    raise SystemExit("v115: focusSection anchor missing")
s=s.replace(focus_old, focus_new, 1)

# When the first data batch arrives after Activity recreation, make the zero-position
# reset happen after the adapter has items and before Android can restore a stale offset.
grid_sig = """        if (type == BrowseSection.TYPE_ROW) {
            mRowsAdapter.update(group);
        } else {
"""
grid_repl = """        if (type == BrowseSection.TYPE_ROW) {
            boolean wasEmpty = mRowsAdapter.isEmpty();
            mRowsAdapter.update(group);
            if (wasEmpty) {
                mRowsView.post(() -> {
                    mRowsView.stopScroll();
                    mRowsView.scrollToPosition(0);
                });
            }
        } else {
"""
if grid_sig not in s:
    raise SystemExit("v115: row update anchor missing")
s=s.replace(grid_sig,grid_repl,1)

grid_tail = """            mGridAdapter.update(group);
        }
"""
grid_tail_repl = """            boolean wasEmpty = mGridAdapter.isEmpty();
            mGridAdapter.update(group);
            if (wasEmpty) {
                mGridView.post(() -> {
                    mGridView.stopScroll();
                    mGridView.scrollToPosition(0);
                });
            }
        }
"""
if grid_tail not in s:
    raise SystemExit("v115: grid update anchor missing")
s=s.replace(grid_tail,grid_tail_repl,1)

# Match the reference chip labels visually: uppercase, medium system sans, no font padding.
tab_old = """        TabLayout.Tab tab = mTabLayout.newTab();
        tab.setText(section.getTitle());
"""
tab_new = """        TabLayout.Tab tab = mTabLayout.newTab();
        String nm7Title = section.getTitle();
        if (nm7Title != null) {
            nm7Title = nm7Title.toUpperCase(java.util.Locale.getDefault());
        }
        tab.setText(nm7Title);
        tab.view.setMinimumHeight((int) (40f * getResources().getDisplayMetrics().density));
"""
if tab_old not in s:
    raise SystemExit("v115: tab creation anchor missing")
s=s.replace(tab_old,tab_new,1)

BROWSE.write_text(s,encoding="utf-8")
print("NM7 Mobile 1.10.115 final YouTube reference UI patch applied")
