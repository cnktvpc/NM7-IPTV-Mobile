#!/usr/bin/env python3
"""NM7 IPTV Mobile 1.10.123 — deterministic YouTube wordmark.

223390.jpg shows the PNG wordmark is visually tiny because the 150x33 asset has its
own blank canvas. The correct fix is to render the YouTube mark as actual UI:
vector play-button + native "YouTube" text. This also removes any possibility that
the white PNG background/canvas participates in drawing.

Reference geometry:
- header: 48dp
- left inset: 16dp
- play icon: 40x28dp
- word: 20sp sans-serif-medium, black
- no ImageView screenshot/canvas.
"""
from pathlib import Path

ROOT=Path("third_party/SmartTube-droid")
LAYOUT=ROOT/"smarttubedroid/src/main/res/layout/browse_activity.xml"
ICON_SRC=Path("scripts/mobile-ui/res/drawable-nodpi/nm7_youtube_icon.xml")
ICON_DST=ROOT/"smarttubedroid/src/main/res/drawable-nodpi/nm7_youtube_icon.xml"

if not LAYOUT.is_file() or not ICON_SRC.is_file():
    raise SystemExit("v123: required files missing")

import shutil
shutil.copyfile(ICON_SRC,ICON_DST)

s=LAYOUT.read_text(encoding="utf-8")

# Final v122 layout uses a 64dp header. Make the header 48dp.
s=s.replace('android:id="@+id/nm7_youtube_header"\n        android:layout_width="match_parent"\n        android:layout_height="64dp"',
            'android:id="@+id/nm7_youtube_header"\n        android:layout_width="match_parent"\n        android:layout_height="48dp"',1)

# Replace the entire old wordmark ImageView block, regardless of its exact dimensions.
start=s.find('            <ImageView\n                android:id="@+id/nm7_youtube_wordmark"')
if start<0:
    raise SystemExit("v123: old wordmark ImageView anchor not found")
end=s.find('            <Space',start)
if end<0:
    raise SystemExit("v123: Space anchor after wordmark not found")

new_block='''            <LinearLayout
                android:id="@+id/nm7_youtube_wordmark"
                android:layout_width="wrap_content"
                android:layout_height="match_parent"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:background="@android:color/transparent">

                <ImageView
                    android:id="@+id/nm7_youtube_logo"
                    android:layout_width="40dp"
                    android:layout_height="28dp"
                    android:src="@drawable/nm7_youtube_icon"
                    android:scaleType="fitCenter"
                    android:contentDescription="YouTube" />

                <TextView
                    android:id="@+id/nm7_youtube_text"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="3dp"
                    android:text="YouTube"
                    android:textColor="#FF0F0F0F"
                    android:textSize="20sp"
                    android:fontFamily="sans-serif-medium"
                    android:includeFontPadding="false"
                    android:gravity="center_vertical" />

            </LinearLayout>

'''
s=s[:start]+new_block+s[end:]

# Make the parent header opaque and remove any legacy Z-order trick.
s=s.replace('android:elevation="4dp"','android:elevation="2dp"',1)
s=s.replace('android:translationZ="8dp"','',1)

LAYOUT.write_text(s,encoding="utf-8")
print("NM7 Mobile 1.10.123 native YouTube wordmark applied")
