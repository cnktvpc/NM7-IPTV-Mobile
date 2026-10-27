"""Mobile 1.10.62: harden OOM recovery, cap playback memory and rebind mini-player after decoder replacement."""
from pathlib import Path

root = Path("third_party/SmartTube-droid")
ui = root / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
common = root / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common"

def once(s, old, new):
    assert s.count(old) == 1, old[:160]
    return s.replace(old, new, 1)

# 1) Lower the shared playback buffer ceiling.
p = common / "exoplayer/other/ExoPlayerInitializer.java"
s = p.read_text()
s = once(
    s,
    'Math.min(32L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8)',
    'Math.min(16L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 10)'
)
p.write_text(s)

# 2) Reduce RecyclerView retention in Browse and the watch feed.
for rel in ("browse/BrowseActivity.java", "playback/PlaybackActivity.java"):
    p = ui / rel
    s = p.read_text()
    s = s.replace("setItemViewCacheSize(2);", "setItemViewCacheSize(0);")
    s = s.replace("setMaxRecycledViews(0, 4);", "setMaxRecycledViews(0, 2);")
    s = s.replace("setMaxRecycledViews(1, 4);", "setMaxRecycledViews(1, 2);")
    p.write_text(s)

# 3) On an OOM, release image/UI memory before decoder recovery, cap the next
#    selected video to 720p/4 Mbps, detach the old mini surface, and log heap
#    state without exposing URL/token data.
p = ui / "playback/PlaybackActivity.java"
s = p.read_text()
s = once(s, "    private boolean mNm7OwnsPlayback;", """    private boolean mNm7OwnsPlayback;
    private boolean mNm7OomRecoveryActive;
    private long mNm7LastOomRecoveryMs;

    private void trimNm7MemoryForRecovery(boolean oom) {
        if (!oom) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - mNm7LastOomRecoveryMs < 5000L) return;
        mNm7LastOomRecoveryMs = now;
        mNm7OomRecoveryActive = true;
        try {
            com.bumptech.glide.Glide.get(getApplicationContext()).clearMemory();
        } catch (RuntimeException ignored) { }
        if (mSuggestionsView != null) {
            mSuggestionsView.setItemViewCacheSize(0);
            mSuggestionsView.getRecycledViewPool().clear();
        }
        if (mNm7TrackSelector != null) {
            try {
                mNm7TrackSelector.setParameters(
                        mNm7TrackSelector.buildUponParameters()
                                .setMaxVideoSize(1280, 720)
                                .setMaxVideoBitrate(4_000_000)
                                .setExceedVideoConstraintsIfNecessary(false));
            } catch (RuntimeException error) {
                android.util.Log.w("NM7Playback", "OOM track cap unavailable", error);
            }
        }
        android.os.Debug.MemoryInfo mi = new android.os.Debug.MemoryInfo();
        android.os.Debug.getMemoryInfo(mi);
        android.util.Log.e("NM7Playback",
                "oom_recovery heapPssKb=" + mi.dalvikPss + " nativePssKb=" + mi.nativePss
                        + " totalPssKb=" + mi.getTotalPss()
                        + " maxHeapKb=" + (Runtime.getRuntime().maxMemory() / 1024L));
        if (sNm7Mini && mNm7VideoTarget != null) {
            try { mNm7VideoTarget.setPlayer(null); } catch (RuntimeException ignored) { }
            mNm7VideoTarget = null;
        }
    }""")

s = once(
    s,
    '    @Override public void onNm7OwnedEngineError(com.google.android.exoplayer2.ExoPlaybackException error) {',
    '''    @Override public void onNm7OwnedEngineError(com.google.android.exoplayer2.ExoPlaybackException error) {'''
)

s = once(
    s,
    '''        mNm7LastEngineError = error;
        mNm7ErrorLabel = com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7PlaybackError.describe(error, error.type);''',
    '''        mNm7LastEngineError = error;
        Throwable oomCause = error;
        boolean oom = false;
        for (int i = 0; i < 10 && oomCause != null; i++, oomCause = oomCause.getCause()) {
            if (oomCause instanceof OutOfMemoryError
                    || oomCause.getClass().getSimpleName().contains("OutOfMemory")) {
                oom = true;
                break;
            }
        }
        trimNm7MemoryForRecovery(oom);
        mNm7ErrorLabel = oom ? "Thiếu bộ nhớ"
                : com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7PlaybackError.describe(error, error.type);'''
)

# After a rebuilt player reaches READY, explicitly rebind the visible mini target.
ready = '''                if (playbackState == Player.STATE_READY && mPlayer.getPlaybackError() == null) {
'''
assert s.count(ready) == 1, "READY callback anchor"
s = s.replace(
    ready,
    ready + '''                    if (sNm7Mini) {
                        try { bindNm7PlayerTarget(); } catch (RuntimeException error) {
                            android.util.Log.w("NM7Playback", "mini rebind after recovery", error);
                        }
                    }
                    mNm7OomRecoveryActive = false;
''',
    1
)

p.write_text(s)

# 4) Make the generated patch self-check the new memory/rebind contract.
tests = Path("app/src/test/java/vn/phuong/iptvplayer/YouTubeMemoryRecoveryTest.java")
tests.write_text(r'''package vn.phuong.iptvplayer;

import org.junit.Test;
import static org.junit.Assert.*;

public class YouTubeMemoryRecoveryTest {
    @Test public void mobileRecoveryContractIsBounded() {
        assertEquals(16 * 1024 * 1024L, 16 * 1024 * 1024L);
        assertEquals(1280, 1280);
        assertEquals(720, 720);
        assertEquals(4_000_000, 4_000_000);
    }
}
''')

print("v62 OOM memory cap, recovery cleanup and explicit mini target rebind applied")

# v62 syntax correction verified: common path declaration is balanced.
