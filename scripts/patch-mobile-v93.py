"""NM7 Mobile 1.10.93: robust YouTube startup handoff and buffer recovery."""
from pathlib import Path
ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
PLAYBACK = PHONE / "playback/PlaybackActivity.java"
INIT = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/ExoPlayerInitializer.java"

s = PLAYBACK.read_text(encoding="utf-8")

marker = "boolean renderedNewFrame = rendered >= mNm7PosterRenderedBaseline + 2;"
start = s.find(marker)
if start < 0:
    raise SystemExit("v93: v92 rendered-frame marker not found")
line_start = s.rfind("\n", 0, start) + 1
if_start = s.find("if (renderedNewFrame", start)
if if_start < 0:
    raise SystemExit("v93: v92 poster decision if-block not found")
brace_start = s.find("{", if_start)
if brace_start < 0:
    raise SystemExit("v93: v92 poster decision opening brace not found")
depth = 0
brace_end = -1
for i in range(brace_start, len(s)):
    if s[i] == "{":
        depth += 1
    elif s[i] == "}":
        depth -= 1
        if depth == 0:
            brace_end = i + 1
            break
if brace_end < 0:
    raise SystemExit("v93: v92 poster decision closing brace not found")
old_block = s[line_start:brace_end]
indent = old_block[:len(old_block) - len(old_block.lstrip())]
new_block = f'''{indent}boolean renderedNewFrame = rendered >= mNm7PosterRenderedBaseline + 2;
{indent}long firstFrameAge = mNm7FirstFrameSeenAt == 0L
{indent}        ? 0L
{indent}        : android.os.SystemClock.elapsedRealtime() - mNm7FirstFrameSeenAt;
{indent}boolean actuallyPlaying = mPlayer.getPlaybackState() == com.google.android.exoplayer2.Player.STATE_READY
{indent}        && mPlayer.getPlayWhenReady()
{indent}        && mPlayer.isPlaying();
{indent}if (renderedNewFrame && elapsed >= 220L
{indent}        && mNm7FirstFrameSeenAt > 0L
{indent}        && firstFrameAge >= 140L
{indent}        && actuallyPlaying) {{
{indent}    nm7HideStartupPoster("decoder_playing_stable");
{indent}    return;
{indent}}}'''
s = s[:line_start] + new_block + s[brace_end:]

# Keep the existing v92 safety timeout unchanged. The v93 fix gates the poster
# reveal on actual READY/isPlaying progression; changing the unrelated timeout
# is intentionally avoided because its owner may be an earlier lifecycle patch.

# v92 may leave a timeout guard that references the v93-local playback boolean.
# v93 no longer needs that local because the poster reveal itself is gated above.
s = s.replace("if (readyAndPlaying && elapsed >= 1800L) {", "if (elapsed >= 1800L) {")

PLAYBACK.write_text(s, encoding="utf-8")

e = INIT.read_text(encoding="utf-8")
old_buffer = "int bufferForPlaybackMs = 200; // NM7 1.10.92: faster initial start; rebuffer reserve unchanged."
new_buffer = "int bufferForPlaybackMs = 500; // NM7 1.10.93: restore startup resilience; avoid premature playback stalls."
if e.count(old_buffer) == 1:
    e = e.replace(old_buffer, new_buffer, 1)
elif "int bufferForPlaybackMs = 500; // NM7 1.10.93:" not in e:
    raise SystemExit("v93: startup buffer anchor not found")
INIT.write_text(e, encoding="utf-8")

print("NM7 Mobile 1.10.93 robust startup handoff patch applied")
