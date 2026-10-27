#!/usr/bin/env python3
"""NM7 Mobile 1.10.128 — YouTube logo/font appearance-only.

Accepted geometry = 1.10.127. Do not change any header/system-bar/chip/feed geometry.
Only replace the visual children inside the existing v1.10.123 wordmark container.

The exact raster is a tight crop from the supplied original YouTube screenshot:
223414.jpg. It contains the authentic logo shape and authentic "YouTube" glyphs,
so no Android font substitution is involved.
"""
from pathlib import Path
import shutil

LAYOUT=Path("third_party/SmartTube-droid/smarttubedroid/src/main/res/layout/browse_activity.xml")
ASSET=Path("scripts/mobile-ui/res/drawable-nodpi/nm7_youtube_wordmark_exact.jpg")
DST=Path("third_party/SmartTube-droid/smarttubedroid/src/main/res/drawable-nodpi/nm7_youtube_wordmark_exact.jpg")

if not LAYOUT.is_file() or not ASSET.is_file():
    raise SystemExit("v128: required files missing")

DST.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(ASSET,DST)

s=LAYOUT.read_text(encoding="utf-8")

# v1.10.123 renderer has this stable outer wordmark container.
start=s.find('''            <LinearLayout
                android:id="@+id/nm7_youtube_wordmark"''')
if start<0:
    raise SystemExit("v128: v1.10.123 wordmark container not found")

end=s.find("            <Space",start)
if end<0:
    raise SystemExit("v128: wordmark end anchor not found")

# Preserve the accepted left placement and 48dp header height. The current v1.10.127
# visible logo occupies approximately the same 76dp x 30dp box; keep that exact box
# and use the original screenshot crop inside it. Increase only the visible logo box slightly to 84dp x 32dp; leave all other chrome geometry unchanged.
new='''            <ImageView
                android:id="@+id/nm7_youtube_wordmark"
                android:layout_width="84dp"
                android:layout_height="32dp"
                android:src="@drawable/nm7_youtube_wordmark_exact"
                android:scaleType="fitCenter"
                android:adjustViewBounds="false"
                android:contentDescription="YouTube" />

'''
s=s[:start]+new+s[end:]

LAYOUT.write_text(s,encoding="utf-8")
print("v1.10.128: exact YouTube reference logo/font substituted; 84dp x 32dp visual box used")
