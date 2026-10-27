"""Only a real media end may close playback; a stream period is not a video end."""
from pathlib import Path
root=Path('third_party/SmartTube-droid')
common=root/'common/src/main/java/com/liskovsoft/smartyoutubetv2/common'
ui=root/'smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui'
def once(s,a,b):
    assert s.count(a)==1,a[:100]
    return s.replace(a,b,1)
contract='com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.Nm7PlaybackEndOwner'
(common/'exoplayer/controller/Nm7PlaybackEndOwner.java').write_text('''package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;
/** Mobile playback must distinguish a media end from source/period changes. */
public interface Nm7PlaybackEndOwner {
    boolean hasNm7MediaEnded();
    boolean keepNm7EndedMini();
}
''')
p=common/'exoplayer/controller/ExoPlayerController.java';s=p.read_text()
s=once(s,'        if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {','''        if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION
                && !(mPlayerView instanceof Nm7PlaybackEndOwner)) {''')
p.write_text(s)
p=common/'app/models/playback/controllers/ErrorFixerController.java';s=p.read_text()
s=once(s,'    private boolean isStreamEnded() {',f'''    private boolean isStreamEnded() {{
        // An expired source or stalled archived livestream is recoverable, not STATE_ENDED.
        if (getPlayer() instanceof {contract}) {{
            return (({contract}) getPlayer()).hasNm7MediaEnded();
        }}''');p.write_text(s)
p=common/'app/models/playback/controllers/VideoLoaderController.java';s=p.read_text()
s=once(s,'                        getPlayer().finishReally();',f'''                        if (getPlayer() instanceof {contract}
                                && (({contract}) getPlayer()).keepNm7EndedMini()) return;
                        getPlayer().finishReally();''');p.write_text(s)
p=ui/'playback/PlaybackActivity.java';s=p.read_text()
s=once(s,'com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.Nm7EngineErrorOwner {',f'''com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.Nm7EngineErrorOwner,
        {contract} {{''')
s=once(s,'    private boolean mNm7OwnsPlayback;','''    @Override public boolean hasNm7MediaEnded() {
        return mPlayer != null && mPlayer.getPlaybackError() == null
                && mPlayer.getPlaybackState() == Player.STATE_ENDED;
    }

    @Override public boolean keepNm7EndedMini() {
        if (!sNm7Mini || mNm7Stopped || sNm7SuspendedForIptv || !hasNm7MediaEnded()) return false;
        // Close-after-video means stop here. Keep the mini session available for replay.
        mPlayer.setPlayWhenReady(false);
        syncNm7KeepAlive();
        return true;
    }

    private boolean mNm7OwnsPlayback;''')
s=once(s,'        return isNm7SessionActive() && sNm7Active.mPlayer.getPlayWhenReady();','''        return isNm7SessionActive() && sNm7Active.mPlayer.getPlayWhenReady()
                && sNm7Active.mPlayer.getPlaybackState() != Player.STATE_ENDED;''')
s=once(s,'        if (error == null) {\n            if (mPlayer.getPlaybackState() == Player.STATE_IDLE', '''        if (error == null) {
            if (mPlayer.getPlaybackState() == Player.STATE_ENDED) {
                mPlayer.seekTo(0);
                mPlayer.setPlayWhenReady(true);
                return;
            }
            if (mPlayer.getPlaybackState() == Player.STATE_IDLE''')
# Safe lifecycle evidence if the device still ends the Activity unexpectedly.
s=once(s,'    public void finishReally() {','''    public void finishReally() {
        android.util.Log.i("NM7Playback", "finishReally mini=" + sNm7Mini + " explicit=" + mNm7Stopped
                + " state=" + (mPlayer == null ? -1 : mPlayer.getPlaybackState())
                + " position=" + (mPlayer == null ? -1 : mPlayer.getCurrentPosition())
                + " duration=" + (mPlayer == null ? -1 : mPlayer.getDuration()));''')
s=once(s,'    protected void onDestroy() {','''    protected void onDestroy() {
        android.util.Log.i("NM7Playback", "destroy mini=" + sNm7Mini + " finishing=" + isFinishing()
                + " changingConfig=" + isChangingConfigurations() + " explicit=" + mNm7Stopped);''')
p.write_text(s)
print('v61 mobile period transitions no longer close playback; real ended mini supports replay')
