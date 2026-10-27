"""Measure real frame output; serialize recovery and invalidate obsolete callbacks."""
from pathlib import Path
import re

root = Path('third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui')
p = root / 'playback/PlaybackActivity.java'
s = p.read_text()

def once(text, old, new):
    assert text.count(old) == 1, old[:150]
    return text.replace(old, new, 1)

for signature in ('private void recoverNm7DecoderError(com.google.android.exoplayer2.ExoPlaybackException error)',
                  'private void ensureNm7PlaybackObserver()', 'private void armNm7RenderWatchdog()'):
    s, count = re.subn(r'(?ms)^    ' + re.escape(signature) + r' \{.*?^    \}\n', '', s)
    assert count == 1, signature
for field in ('private long mNm7LastFirstFrameMs;', 'private long mNm7LastErrorRetryMs;',
              'private int mNm7DecoderRecoveryAttempts;', 'private int mNm7RenderRecoveryAttempts;'):
    s = once(s, '    ' + field, '')
s = once(s, '    private static PlaybackActivity sNm7Active;',
         Path('scripts/smarttube-recovery-v67.java.inc').read_text() + '\n    private static PlaybackActivity sNm7Active;')

# Cancel the actual delayed tasks/listeners, not just an intent flag.
s = once(s, '    private void releasePlayer() {', '    private void releasePlayer() {\n        detachNm7PlaybackObserver();')
s = once(s, '    protected void onDestroy() {', '    protected void onDestroy() {\n        detachNm7PlaybackObserver();')
s = once(s, '        sNm7SuspendedForIptv = true;', '''        sNm7SuspendedForIptv = true;
        active.cancelNm7RenderWatchdog();
        active.cancelNm7DecoderRetry();
        active.mNm7RenderPolicy.resetObservation();''')
s = once(s, '        active.mNm7Stopped = true;', '        active.mNm7Stopped = true;\n        active.detachNm7PlaybackObserver();')
s = once(s, '        mNm7SessionVideo = item;', '''        String nm7VideoId = item == null ? null : item.videoId;
        if (!android.text.TextUtils.equals(mNm7SessionVideo == null ? null : mNm7SessionVideo.videoId, nm7VideoId)) {
            cancelNm7RenderWatchdog(); cancelNm7DecoderRetry();
            mNm7RenderPolicy.video(nm7VideoId);
        }
        mNm7SessionVideo = item;''')

# A new selector must retain the fallback limit through decoder recreation.
s = once(s, '        mNm7SavedVideoRenderers.clear();', '        mNm7SavedVideoRenderers.clear();\n        applyNm7RecoveryVideoLimit();')
s = once(s, '        mNm7OomRecoveryActive = true;', '        mNm7OomRecoveryActive = true;\n        mNm7RecoveryVideoLimited = true;')
s = once(s, '        if (changed) mNm7TrackSelector.setParameters(builder);', '''        if (changed) mNm7TrackSelector.setParameters(builder);
        mNm7RenderPolicy.resetObservation();
        if (mNm7AppBackground) cancelNm7RenderWatchdog(); else armNm7RenderWatchdog();''')

# Record/recheck the real target after each handoff. Do not seek a healthy stream.
# v60 has a second assignment for decoder-null transitions: append at the successful switch.
anchor = '        if (mini.getPlayer() != active.mPlayer) mini.setPlayer(active.mPlayer);\n        active.mNm7VideoTarget = mini;'
s = once(s, anchor, anchor + '\n        active.armNm7RenderWatchdog();')
s = once(s, '        mNm7VideoTarget = mPlayerView;\n        sNm7RestorePending',
         '        mNm7VideoTarget = mPlayerView;\n        armNm7RenderWatchdog();\n        sNm7RestorePending')

# Manual retry starts a new bounded attempt. A READY event never does.
s = once(s, '    private void retryNm7Mini() {', '''    private void retryNm7Mini() {
        if (mNm7RenderPolicy.exhausted() && !sNm7SuspendedForIptv && !mNm7Stopped) {
            mNm7RenderPolicy.manualRetry();
            mNm7RecoveryWantsPlay = mNm7RecoveryRestoreIntent = true;
            mNm7ErrorLabel = null;
            restartEngine();
            return;
        }''')
s = once(s, '        if (sNm7Active.mNm7RecoveryPending) return 2;',
         '        if (sNm7Active.mNm7RenderPolicy.exhausted()) return -1;\n        if (sNm7Active.mNm7RecoveryPending) return 2;')
s = once(s, '        mPlayer.addListener(new Player.EventListener() {',
         '        final com.google.android.exoplayer2.SimpleExoPlayer nm7CreatedPlayer = mPlayer;\n        mPlayer.addListener(new Player.EventListener() {')
s = once(s, '            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {',
         '            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {\n                if (mPlayer != nm7CreatedPlayer) return;')
s = once(s, '        mNm7ManualRetry = true;', '''        cancelNm7DecoderRetry();
        if (isNm7DecoderError(error)) {
            mPlayer.setPlayWhenReady(true);
            recoverNm7DecoderError(error);
            return;
        }
        mNm7ManualRetry = true;''')
s = once(s, 'if (playbackState == Player.STATE_READY && mPlayer.getPlaybackError() == null) {',
         'if (playbackState == Player.STATE_READY && mPlayer.getPlaybackError() == null && !mNm7RenderPolicy.exhausted()) {')
p.write_text(s)

# Remove the generated tautology test from v62. Real policy/lifecycle tests live in app/src/test.
test = Path('app/src/test/java/vn/phuong/iptvplayer/YouTubeMemoryRecoveryTest.java')
if test.exists():
    text = test.read_text()
    assert 'assertEquals(1280, 1280)' in text, 'Refuse to remove a non-generated test'
    test.unlink()
print('v67 frame-progress watchdog, single delayed task and bounded recovery applied')
