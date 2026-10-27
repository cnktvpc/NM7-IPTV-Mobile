"""Mobile 1.10.73: prioritize YouTube network path without reducing thumbnail quality."""
from pathlib import Path
import re

phone = Path("third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui")

# 1) Do not force the playback stack to OkHttp merely because a custom DNS
# preference is enabled. The pinned SmartTube network stack can then select
# its native/Cronet/HTTP path when available. Custom DNS preferences remain
# intact; only the transport override is removed.
p = phone / "playback/PlaybackActivity.java"
t = p.read_text(encoding="utf-8")

# Remove the optional DNS-triggered OkHttp transport override injected by v37.
# Match the semantic block rather than whitespace-sensitive source text.
pattern = re.compile(
    r"(?ms)^        // The upstream fallback switches to OkHttp.*?"
    r"^        createPlayerObjects\(\);"
)
t, removed = pattern.subn(
    """        // NM7 1.10.73: keep the user's DNS preference but do not force
        // playback onto OkHttp.
        createPlayerObjects();""",
    t,
    count=1,
)

if removed == 0 and "PLAYER_DATA_SOURCE_OKHTTP" in t:
    # Fallback: remove the exact v37-injected block without a fragile regex.
    start = t.find("        // The upstream fallback switches to OkHttp")
    end = t.find("        createPlayerObjects();", start)
    if start >= 0 and end >= start:
        end += len("        createPlayerObjects();")
        t = t[:start] + """        // NM7 1.10.73: keep the user's DNS preference but do not force
        // playback onto OkHttp.
        createPlayerObjects();""" + t[end:]
        removed2 = 1
    else:
        removed2 = 0
    if removed2 == 0 and "PLAYER_DATA_SOURCE_OKHTTP" in t:
        raise SystemExit("Unable to remove OkHttp transport override")

if "PLAYER_DATA_SOURCE_OKHTTP" in t:
    raise SystemExit("OkHttp transport override remains after v73 patch")
p.write_text(t, encoding="utf-8")

# 2) Keep the exact YouTube thumbnail quality/resolution used by 1.10.72.
# The shared UI patch is the actual owner of the card URL. Verify that it still
# uses the same max-resolution endpoint before allowing the v73 optimization.
ui_patch = Path("scripts/patch-mobile-ui.py")
if not ui_patch.is_file() or "maxresdefault.jpg" not in ui_patch.read_text(encoding="utf-8"):
    raise SystemExit("YouTube thumbnail target must remain maxresdefault.jpg")
# Do not modify the thumbnail URL or substitute a lower-resolution target.


# 3) Keep the selected-video format request on the background I/O scheduler.
# Add an explicit diagnostic marker so device tests can distinguish format
# lookup latency from decoder/frame latency without logging URLs/tokens.
p = phone / "playback/PlaybackActivity.java"
s = p.read_text(encoding="utf-8")
anchor = '        mNm7VideoRequestedAt = android.os.SystemClock.elapsedRealtime();'
if s.count(anchor) != 1:
    raise SystemExit("selected video timing anchor missing/ambiguous")
if 'android.util.Log.i("NM7Playback", "selected_video_start");' not in s:
    s = s.replace(
        anchor,
        anchor + '\n        android.util.Log.i("NM7Playback", "selected_video_start");',
        1,
    )
p.write_text(s, encoding="utf-8")

print("NM7 1.10.73 network/list loading optimization applied")
