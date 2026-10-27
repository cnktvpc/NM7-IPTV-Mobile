"""Keep the playback session foreground across lock, buffering and decoder replacement."""
from pathlib import Path
import re
root=Path('third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui')
def once(s,a,b):
    assert s.count(a)==1,a[:100]
    return s.replace(a,b,1)
def method(s,signature,body):
    s,n=re.subn(r'(?ms)^    '+re.escape(signature)+r' \{.*?^    \}',lambda _: '    '+signature+' {\n'+body+'\n    }',s)
    assert n==1,signature
    return s
p=root/'playback/PlaybackActivity.java';s=p.read_text()
s=once(s,'    private boolean mNm7OwnsPlayback;','''    private boolean mNm7OwnsPlayback;
    private boolean mNm7AppBackground;
    private boolean mNm7KeepAliveStarted;
    private com.google.android.exoplayer2.trackselection.DefaultTrackSelector mNm7TrackSelector;
    private final android.util.SparseBooleanArray mNm7SavedVideoRenderers = new android.util.SparseBooleanArray();

    private boolean nm7ScreenInteractive() {
        android.os.PowerManager power = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        return power == null || power.isInteractive();
    }

    private void applyNm7VideoVisibility() {
        if (mPlayer == null || mNm7TrackSelector == null) return;
        com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder builder =
                mNm7TrackSelector.buildUponParameters();
        boolean changed = false;
        for (int i = 0; i < mPlayer.getRendererCount(); i++) {
            if (mPlayer.getRendererType(i) != com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO) continue;
            if (mNm7AppBackground && mNm7SavedVideoRenderers.indexOfKey(i) < 0) {
                mNm7SavedVideoRenderers.put(i, mNm7TrackSelector.getParameters().getRendererDisabled(i));
                builder.setRendererDisabled(i, true);
                changed = true;
            } else if (!mNm7AppBackground && mNm7SavedVideoRenderers.indexOfKey(i) >= 0) {
                builder.setRendererDisabled(i, mNm7SavedVideoRenderers.get(i));
                mNm7SavedVideoRenderers.delete(i);
                changed = true;
            }
        }
        if (changed) mNm7TrackSelector.setParameters(builder);
    }

    private void syncNm7KeepAlive() {
        boolean keep = com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7KeepAlivePolicy.keep(
                mNm7OwnsPlayback, mNm7Stopped, sNm7SuspendedForIptv, sNm7Mini, mNm7AppBackground);
        Intent service = new Intent().setClassName(this, "vn.phuong.iptvplayer.BackgroundPlaybackService");
        if (!keep) {
            if (mNm7KeepAliveStarted) stopService(service);
            mNm7KeepAliveStarted = false;
            return;
        }
        boolean wake = com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7KeepAlivePolicy.wake(
                mPlayer != null && mPlayer.getPlayWhenReady(),
                mNm7RecoveryPending || (mNm7RecoveryRestoreIntent && mNm7RecoveryWantsPlay)
                        || (mNm7TargetRestoreWaiting && mNm7ResumeAfterTarget),
                mPlayer != null && mPlayer.getPlaybackState() == Player.STATE_ENDED);
        service.putExtra("youtube", true);
        service.putExtra("youtube_active", wake);
        service.putExtra("channel_name", wake ? "YouTube đang phát" : "YouTube đã tạm dừng");
        try {
            if (VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            mNm7KeepAliveStarted = true;
        } catch (RuntimeException error) {
            android.util.Log.e("NM7Playback", "Keep-alive service start failed", error);
        }
    }''')
s=method(s,'public static void prepareNm7Background()', '''        PlaybackActivity owner = sNm7Active;
        if (owner == null || owner.mNm7Stopped || owner.isDestroyed() || sNm7SuspendedForIptv) return;
        owner.blockEngine(true);
        owner.nm7SetBackground(true);''')
s=method(s,'public static void resumeNm7Foreground()', '''        PlaybackActivity owner = sNm7Active;
        if (owner == null || owner.mNm7Stopped || owner.isDestroyed() || sNm7SuspendedForIptv) return;
        if (!owner.nm7ScreenInteractive()) { prepareNm7Background(); return; }
        owner.blockEngine(false);
        owner.nm7SetBackground(false);''')
s=method(s,'private void nm7SetBackground(boolean enabled)', '''        mNm7AppBackground = !mNm7Stopped && !sNm7SuspendedForIptv && (enabled || !nm7ScreenInteractive());
        System.setProperty("nm7.youtube.background", mNm7AppBackground ? "1" : "0");
        try {
            Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("setYoutubeBackground", android.content.Context.class, boolean.class)
                    .invoke(null, this, mNm7AppBackground);
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("NM7Playback", "Background session bridge failed", error);
        }
        if (!mNm7Stopped && !sNm7SuspendedForIptv) applyNm7VideoVisibility();
        syncNm7KeepAlive();''')
old='''                if (mNm7OwnsPlayback && (!playWhenReady || playbackState == Player.STATE_ENDED)) {
                    stopService(new Intent().setClassName(PlaybackActivity.this,
                            "vn.phuong.iptvplayer.BackgroundPlaybackService"));
                } else if (mNm7OwnsPlayback && isEngineBlocked() && !mNm7Stopped) {
                    nm7SetBackground(true);
                }'''
s=once(s,old,'''                // Neither BUFFERING/IDLE nor a temporary recovery pause ends the mini session.
                syncNm7KeepAlive();''')
s=once(s,'        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new AdaptiveTrackSelection.Factory());','''        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new AdaptiveTrackSelection.Factory());
        mNm7TrackSelector = trackSelector;
        mNm7SavedVideoRenderers.clear();''')
s=once(s,'        mPlayer = mPlayerInitializer.createPlayer(this, renderersFactory, trackSelector);',
       '        mPlayer = mPlayerInitializer.createPlayer(this, renderersFactory, trackSelector);\n        applyNm7VideoVisibility();')
s=once(s,'        sNm7Mini = true;\n        // First Back', '        sNm7Mini = true;\n        syncNm7KeepAlive();\n        // First Back')
s=method(s,'public static boolean isNm7MiniPlayerActive()', '''        // A decoder rebuild is not an explicit close of the session.
        return sNm7Mini && sNm7Active != null && !sNm7Active.mNm7Stopped && !sNm7Active.isDestroyed()
                && !sNm7Active.isFinishing()
                && (sNm7Active.mPlayer != null || sNm7Active.mNm7SessionVideo != null);''')
s=once(s,'        mini.setUseController(false);','''        mini.setUseController(false);
        if (active.mPlayer == null) {
            active.mNm7VideoTarget = mini;
            active.syncNm7KeepAlive();
            return;
        }''')
s=once(s,'        active.blockEngine(false);\n        com.google.android.exoplayer2.ui.PlayerView mini',
       '        active.blockEngine(active.mNm7AppBackground);\n        com.google.android.exoplayer2.ui.PlayerView mini')
s=once(s,'        android.util.Log.i("NM7Playback", "target=mini state=" + active.mPlayer.getPlaybackState());',
       '        active.syncNm7KeepAlive();\n        android.util.Log.i("NM7Playback", "target=mini state=" + active.mPlayer.getPlaybackState());')
p.write_text(s)
print('v60 persistent mini service, screen-off audio and decoder-independent mini session applied')
