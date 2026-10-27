"""NM7 Mobile 1.10.94: 1.10.91 baseline with playback-position-gated poster handoff.

Start from the user-tested 1.10.91 behavior. Do not change the YouTube
transport, buffering policy, chat, avatar, IPTV ownership, or navigation.
Only change the poster reveal decision: a decoded frame is not enough; the
actual playback position must advance after the first rendered frame.
"""
from pathlib import Path
ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
PLAYBACK = PHONE / "playback/PlaybackActivity.java"
s = PLAYBACK.read_text(encoding="utf-8")
field = "    private long mNm7FirstFrameSeenAt;\n"
if "mNm7FirstFramePositionMs" not in s:
    if s.count(field) != 1: raise SystemExit("v94: first-frame timing field anchor not found")
    s = s.replace(field, field + "    private long mNm7FirstFramePositionMs;\n", 1)
old_frame = """                mNm7FirstFrameSeenAt = android.os.SystemClock.elapsedRealtime();
                // Do not reveal a potentially frozen first frame. Reset the decoder
"""
new_frame = """                mNm7FirstFrameSeenAt = android.os.SystemClock.elapsedRealtime();
                mNm7FirstFramePositionMs = observed.getCurrentPosition();
                // Do not reveal a potentially frozen first frame. Reset the decoder
"""
if s.count(old_frame) != 1: raise SystemExit("v94: first-frame position anchor not found")
s = s.replace(old_frame, new_frame, 1)
marker = "boolean renderedNewFrame = rendered >= mNm7PosterRenderedBaseline + 3;"
start = s.find(marker)
if start < 0: raise SystemExit("v94: v91 moving-frame marker not found")
if_start = s.find("if (renderedNewFrame", start)
if if_start < 0: raise SystemExit("v94: v91 poster decision not found")
brace_start = s.find("{", if_start)
if brace_start < 0: raise SystemExit("v94: poster decision brace not found")
depth = 0; end = -1
for i in range(brace_start, len(s)):
    if s[i] == "{": depth += 1
    elif s[i] == "}":
        depth -= 1
        if depth == 0: end = i + 1; break
if end < 0: raise SystemExit("v94: poster decision closing brace not found")
line_start = s.rfind("\n", 0, start) + 1
old_block = s[line_start:end]
indent = old_block[:len(old_block)-len(old_block.lstrip())]
new_block = f'''{indent}boolean renderedNewFrame = rendered >= mNm7PosterRenderedBaseline + 3;
{indent}long playbackProgressMs = mPlayer.getCurrentPosition() - mNm7FirstFramePositionMs;
{indent}if (renderedNewFrame && elapsed >= 180L
{indent}        && mNm7FirstFrameSeenAt > 0L
{indent}        && playbackProgressMs >= 120L) {{
{indent}    nm7HideStartupPoster("decoder_playback_progress");
{indent}    return;
{indent}}}
{indent}boolean readyAndPlaying = mPlayer.getPlaybackState() == com.google.android.exoplayer2.Player.STATE_READY
{indent}        && mPlayer.getPlayWhenReady()
{indent}        && mPlayer.getPlaybackError() == null;'''
s = s[:line_start] + new_block + s[end:]
reset = """        mNm7FirstFrameRendered = false;
        mNm7FirstFrameSeenAt = 0L;
"""
if s.count(reset) == 1 and "mNm7FirstFramePositionMs = 0L;" not in s:
    s = s.replace(reset, reset + "        mNm7FirstFramePositionMs = 0L;\n", 1)
PLAYBACK.write_text(s, encoding="utf-8")
print("NM7 Mobile 1.10.94: 1.10.91 baseline + playback-position poster gate applied")
