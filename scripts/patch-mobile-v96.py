"""NM7 Mobile 1.10.96: remove the YouTube loading spinner and shorten startup wait.

Keep the tested 1.10.95 shutter/reset handoff. Only:
- never show the indeterminate loading spinner over the black player;
- lower ExoPlayer's initial playback threshold from 250 ms to 150 ms;
- keep the rebuffer threshold unchanged.
"""

from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PLAYBACK = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"
INIT = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/ExoPlayerInitializer.java"

s = PLAYBACK.read_text(encoding="utf-8")
old = '        if (mProgressBar != null) mProgressBar.setVisibility(View.VISIBLE);'
if s.count(old) != 1:
    raise SystemExit(f"v96: expected exactly one YouTube progress-spinner show, found {s.count(old)}")
s = s.replace(
    old,
    '''        // NM7 1.10.96: no indeterminate spinner during video startup.
        // Keep the player surface clean: black shutter -> thumbnail -> video.
        if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);''',
    1,
)
PLAYBACK.write_text(s, encoding="utf-8")

e = INIT.read_text(encoding="utf-8")
old_buf = "int bufferForPlaybackMs = 250; // NM7 1.10.91: faster initial start; rebuffer reserve unchanged."
new_buf = "int bufferForPlaybackMs = 150; // NM7 1.10.96: faster initial start; rebuffer reserve unchanged."
if e.count(old_buf) != 1:
    raise SystemExit(f"v96: expected exactly one startup buffer setting, found {e.count(old_buf)}")
e = e.replace(old_buf, new_buf, 1)
INIT.write_text(e, encoding="utf-8")

print("NM7 Mobile 1.10.96: startup spinner removed; initial playback threshold reduced to 150 ms")
