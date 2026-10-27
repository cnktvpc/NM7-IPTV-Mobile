#!/usr/bin/env python3
"""NM7 Mobile 1.10.122 — eliminate header overdraw completely.

Device evidence (223388.jpg / 223383.mp4) shows the logo artwork itself is intact;
the remaining visual defect occurs because Browse uses a scrolling/overlay hierarchy
where a later full-size content container can occupy the same coordinate space as the
header. The fix is to stop overlaying the header at all.

Final Browse structure:
root LinearLayout
  header FrameLayout (opaque white, topmost inside normal flow)
  chips HorizontalScrollView
  content FrameLayout (weighted remainder)

Android 15 status-bar placement is already handled natively by v121 theme opt-out.
This patch intentionally removes every manual status-bar spacer/inset and every
Coordinator/AppBar scrolling behavior from the Browse layout. This makes it impossible
for the feed/background layer to draw over the YouTube wordmark.

Only Browse chrome/layout is changed. Playback, IPTV, data, avatars, spinner,
comments, background playback and bottom navigation remain unchanged.
"""
from pathlib import Path
import shutil

SRC=Path("scripts/mobile-ui/res/layout/browse_activity.xml")
DST=Path("third_party/SmartTube-droid/smarttubedroid/src/main/res/layout/browse_activity.xml")
if not SRC.is_file(): raise SystemExit("v122: source layout missing")

xml=SRC.read_text(encoding="utf-8")

# Normalize root.
xml=xml.replace(
    '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"',
    '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"',
    1)
xml=xml.replace('    android:paddingBottom="64dp"\n','    android:paddingBottom="0dp"\n',1)

# Replace the whole AppBarLayout block with a normal, opaque header + chip row.
start=xml.find('    <com.google.android.material.appbar.AppBarLayout')
end_marker='    <FrameLayout\n        android:id="@+id/browse_content"'
end=xml.find(end_marker,start)
if start<0 or end<0: raise SystemExit("v122: appbar/content anchors missing")

header='''    <FrameLayout
        android:id="@+id/browse_app_bar"
        android:layout_width="match_parent"
        android:layout_height="64dp"
        android:background="#FFFFFFFF"
        android:elevation="4dp"
        android:clipChildren="false"
        android:clipToPadding="false">

        <LinearLayout
            android:id="@+id/nm7_youtube_header"
            android:layout_width="match_parent"
            android:layout_height="64dp"
            android:paddingStart="16dp"
            android:paddingEnd="4dp"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:background="#FFFFFFFF">

            <ImageView
                android:id="@+id/nm7_youtube_wordmark"
                android:layout_width="92dp"
                android:layout_height="36dp"
                android:src="@drawable/nm7_youtube_wordmark"
                android:scaleType="centerInside"
                android:adjustViewBounds="false"
                android:contentDescription="YouTube"
                android:elevation="8dp"
                android:translationZ="8dp" />

            <Space
                android:layout_width="0dp"
                android:layout_height="1dp"
                android:layout_weight="1" />

            <ImageButton
                android:id="@+id/nm7_video"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@drawable/nm7_video_camera"
                android:tint="#0F0F0F"
                android:contentDescription="Tạo video" />

            <ImageButton
                android:id="@+id/nm7_search"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@drawable/browse_ic_search"
                android:tint="#0F0F0F"
                android:contentDescription="Tìm kiếm" />
        </LinearLayout>
    </FrameLayout>

    <HorizontalScrollView
        android:id="@+id/nm7_chip_scroll"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:scrollbars="none"
        android:fillViewport="false"
        android:paddingStart="8dp"
        android:paddingEnd="8dp"
        android:background="#FFFFFFFF"
        android:elevation="3dp">

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="match_parent"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageButton
                android:id="@+id/nm7_explore"
                android:layout_width="48dp"
                android:layout_height="40dp"
                android:layout_marginEnd="4dp"
                android:background="@drawable/nm7_explore_bg"
                android:src="@drawable/nm7_explore"
                android:tint="#0F0F0F"
                android:contentDescription="Khám phá" />

            <com.google.android.material.tabs.TabLayout
                android:id="@+id/browse_tabs"
                android:layout_width="wrap_content"
                android:layout_height="48dp"
                android:clipToPadding="false"
                app:tabMode="scrollable"
                app:tabGravity="center"
                app:tabIndicatorHeight="0dp"
                app:tabBackground="@drawable/nm7_tab_bg"
                app:tabTextAppearance="@style/Nm7YoutubeChipText"
                app:tabTextColor="#0F0F0F"
                app:tabSelectedTextColor="#FFFFFF"
                app:tabMinWidth="0dp"
                app:tabPaddingStart="14dp"
                app:tabPaddingEnd="14dp"
                app:tabRippleColor="#E5E5E5" />
        </LinearLayout>
    </HorizontalScrollView>

    <ImageButton
        android:id="@+id/nm7_voice"
        android:layout_width="1dp"
        android:layout_height="1dp"
        android:visibility="gone" />

    <ImageButton
        android:id="@+id/nm7_account"
        android:layout_width="1dp"
        android:layout_height="1dp"
        android:visibility="gone" />

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/browse_toolbar"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:visibility="gone" />

'''
xml=xml[:start]+header+xml[end:]

# Make content an ordinary weighted remainder. No Coordinator behavior, no overlap.
content_start=xml.find(end_marker)
if content_start<0: raise SystemExit("v122: content marker lost")
content_end=xml.find('    </FrameLayout>', content_start)
if content_end<0: raise SystemExit("v122: content close missing")
old=xml[content_start:content_end+len('    </FrameLayout>')]
new=old.replace(
    'android:layout_height="0dp"\n        android:layout_weight="1"',
    'android:layout_height="0dp"\n        android:layout_weight="1"',
    1).replace(
    'app:layout_behavior="@string/appbar_scrolling_view_behavior"\n','',1)
xml=xml.replace(old,new,1)

DST.parent.mkdir(parents=True,exist_ok=True)
DST.write_text(xml,encoding="utf-8")

# Remove legacy runtime chrome modifications so the normal-flow layout remains normal.
BROWSE=Path("third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java")
s=BROWSE.read_text(encoding="utf-8")

for name in ["applyNm7PortraitSystemBars","setNm7ChromeHidden","installNm7ScrollChrome","nm7FixInitialFeedPosition"]:
    sig="    private void "+name+"("
    pos=s.find(sig)
    if pos>=0:
        brace=s.find("{",pos); depth=0; end=-1
        for i in range(brace,len(s)):
            if s[i]=="{": depth+=1
            elif s[i]=="}":
                depth-=1
                if depth==0: end=i+1; break
        if end>0:
            # Leave a compact no-op only for call-site compatibility.
            args=s[s.find("(",pos)+1:s.find(")",s.find("(",pos))]
            if name=="applyNm7PortraitSystemBars":
                repl='''    private void applyNm7PortraitSystemBars() {
        // Android 15 placement is owned by Theme.SmartTubeDroid (v121).
    }'''
            elif name=="setNm7ChromeHidden":
                repl='''    private void setNm7ChromeHidden(boolean hidden) {
        // v122: normal-flow header; no overlay transition.
    }'''
            elif name=="installNm7ScrollChrome":
                repl='''    private void installNm7ScrollChrome(RecyclerView list) {
        // v122: normal-flow feed; no synthetic header padding.
    }'''
            else:
                repl='''    private void nm7FixInitialFeedPosition() {
        // v122: initial position is supplied by RecyclerView's normal layout.
    }'''
            s=s[:pos]+repl+s[end:]

# Remove Coordinator-specific behavior assignments if still present.
s=s.replace('list.setTranslationY(0f);','')
BROWSE.write_text(s,encoding="utf-8")
print("NM7 Mobile 1.10.122 normal-flow YouTube header/content architecture applied")
