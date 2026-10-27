"""NM7 Mobile 1.10.92: faster, stable YouTube thumbnail-to-video handoff.

Builds directly on 1.10.91. Scope is only the opening transition:
- remove the 170ms cosmetic player entrance animation that can expose a flash during SurfaceView handoff;
- keep the exact high-quality clicked thumbnail as the poster;
- reveal the decoder after two new rendered buffers instead of three;
- reduce the poster safety wait from 180ms/120ms to 90ms/60ms;
- trim only the initial playback threshold slightly, leaving rebuffer behavior unchanged.

Do not touch IPTV, status bar, avatar, live chat, background playback, or thumbnail quality.
"""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
PLAYBACK = PHONE / "playback/PlaybackActivity.java"
INIT = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/ExoPlayerInitializer.java"

def once(s, old, new, label):
    if s.count(old) != 1:
        raise SystemExit(f"v92: expected exactly one {label}, found {s.count(old)}")
    return s.replace(old, new, 1)

s = PLAYBACK.read_text(encoding="utf-8")

# v83 cosmetic enter animation is counterproductive for a SurfaceView-backed player:
# it can expose an intermediate composition while the decoder surface is attaching.
old_anim = """        // NM7 1.10.83: subtle phone-style player entrance; cosmetic only.
        float nm7Enter = 14f * getResources().getDisplayMetrics().density;
        mPlayerContainer.setAlpha(0.88f);
        mPlayerContainer.setTranslationY(nm7Enter);
        mPlayerContainer.setScaleX(0.992f);
        mPlayerContainer.setScaleY(0.992f);
        mPlayerContainer.animate()
                .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(170L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
"""
new_anim = """        // NM7 1.10.92: no cosmetic container animation during SurfaceView handoff.
        // Keep the player fully opaque/at rest; the exact clicked-card poster is held
        // until decoder output is moving, which prevents a thumbnail flash.
        mPlayerContainer.clearAnimation();
        mPlayerContainer.setAlpha(1f);
        mPlayerContainer.setTranslationY(0f);
        mPlayerContainer.setScaleX(1f);
        mPlayerContainer.setScaleY(1f);
"""
s = once(s, old_anim, new_anim, "v83 player entrance animation")

old_rendered = """                boolean renderedNewFrame = rendered >= mNm7PosterRenderedBaseline + 3;
"""
new_rendered = """                boolean renderedNewFrame = rendered >= mNm7PosterRenderedBaseline + 2;
"""
s = once(s, old_rendered, new_rendered, "decoder frame threshold")

old_hide = """                if (renderedNewFrame && elapsed >= 180L
                        && (mNm7FirstFrameSeenAt == 0L
                            || android.os.SystemClock.elapsedRealtime() - mNm7FirstFrameSeenAt >= 120L)) {
                    nm7HideStartupPoster("decoder_moving_frames");
                    return;
                }
"""
new_hide = """                if (renderedNewFrame && elapsed >= 90L
                        && (mNm7FirstFrameSeenAt == 0L
                            || android.os.SystemClock.elapsedRealtime() - mNm7FirstFrameSeenAt >= 60L)) {
                    nm7HideStartupPoster("decoder_moving_frames_fast");
                    return;
                }
"""
s = once(s, old_hide, new_hide, "poster hide timing")

PLAYBACK.write_text(s, encoding="utf-8")

e = INIT.read_text(encoding="utf-8")
old_buffer = "int bufferForPlaybackMs = 250; // NM7 1.10.91: faster initial start; rebuffer reserve unchanged."
new_buffer = "int bufferForPlaybackMs = 200; // NM7 1.10.92: faster initial start; rebuffer reserve unchanged."
e = once(e, old_buffer, new_buffer, "initial playback buffer")
INIT.write_text(e, encoding="utf-8")

print("NM7 Mobile 1.10.92 smooth-open patch applied")
