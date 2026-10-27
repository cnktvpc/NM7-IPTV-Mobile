"""NM7 Mobile v1.10.78 final compile guard.

This runs LAST in the patch chain so no earlier SmartTube patch can reintroduce
the missing section_is_empty resource reference.
"""
from pathlib import Path
import re

p = Path("third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java")
s = p.read_text(encoding="utf-8")

# The pinned Mobile SmartTube resources do not define this resource. Replace
# every form of the stale reference, regardless of which earlier patch produced it.
s = re.sub(
    r'getString\(R\.string\.section_is_empty\)',
    '"No comments available"',
    s,
)
p.write_text(s, encoding="utf-8")

print("v78: removed all stale section_is_empty references")
