"""Single Mobile Activity owner; one selected source request across cold/warm screen entry."""
from pathlib import Path
import re

root = Path('third_party/SmartTube-droid')
common = root / 'common/src/main/java/com/liskovsoft/smartyoutubetv2/common'
phone = root / 'smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui'

def once(s, old, new):
    assert s.count(old) == 1, old[:150]
    return s.replace(old, new, 1)

def method(s, signature, replacement):
    s, count = re.subn(r'(?ms)^    ' + re.escape(signature) + r' \{.*?^    \}',
                      lambda _: replacement.rstrip(), s)
    assert count == 1, signature
    return s

# singleTop only reuses the top Activity. A mini's Activity is BELOW Browse/Search.
p = common / 'app/views/ViewManager.java'
s = p.read_text()
s = once(s, '        Intent intent = new Intent(mContext, activityClass);', '        Intent intent = createNm7LaunchIntent(activityClass);')
s = once(s, '        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);', '')
s = once(s, '    private boolean doThrottle() {', '''    private Intent createNm7LaunchIntent(Class<?> activityClass) {
        Intent intent = new Intent(mContext, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if ("com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity".equals(activityClass.getName())) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        }
        return intent;
    }

    private boolean doThrottle() {''')
p.write_text(s)

# Independently protect singleton controllers from a late event/release of an obsolete Activity.
(common / 'exoplayer/controller/Nm7CurrentEngineOwner.java').write_text('''package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;
public interface Nm7CurrentEngineOwner {
    boolean isNm7CurrentEngine(Object engine);
}
''')
p = common / 'exoplayer/controller/ExoPlayerController.java'
s = p.read_text()
guard = '''        if (mPlayerView instanceof Nm7CurrentEngineOwner
                && !((Nm7CurrentEngineOwner) mPlayerView).isNm7CurrentEngine(mPlayer)) return;
'''
for signature in ('public void onPlayerStateChanged(boolean playWhenReady, int playbackState)',
                  'public void onPlayerError(ExoPlaybackException error)',
                  'public void onPositionDiscontinuity(int reason)',
                  'public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections)',
                  'public void onSeekProcessed()'):
    s = once(s, '    ' + signature + ' {', '    ' + signature + ' {\n' + guard)
p.write_text(s)

p = phone / 'playback/PlaybackActivity.java'
s = p.read_text()
s = once(s, 'com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.Nm7PlaybackEndOwner {',
         'com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.Nm7PlaybackEndOwner,\n        com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.Nm7CurrentEngineOwner {')
s = once(s, '    private boolean mNm7OwnsPlayback;', '''    @Override public boolean isNm7CurrentEngine(Object engine) {
        return sNm7Active == this && !mNm7Stopped && !isFinishing() && !isDestroyed()
                && engine != null && engine == mPlayer;
    }

    private Runnable mNm7VisibilityTask;
    private long mNm7VideoRequestedAt;
    private boolean mNm7StartupFrame;
    private boolean mNm7OwnsPlayback;''')
s = once(s, '\n        mPlaybackPresenter.onEngineReleased();', '''\n        if (mPlaybackPresenter.getPlayer() == this) mPlaybackPresenter.onEngineReleased();''')
s = once(s, '    public void reloadPlayback() {', '''    public void reloadPlayback() {
        if (!isNm7CurrentEngine(mPlayer)) return;''')
s = once(s, '        mPlaybackPresenter.onFinish();', '''        if (mPlaybackPresenter.getPlayer() == this) mPlaybackPresenter.onFinish();''')
s = once(s, '        mNm7LastEngineError = error;', '''        if (!isNm7CurrentEngine(mPlayer)) return;
        mNm7LastEngineError = error;''')
s = once(s, 'if (mPlayer != nm7CreatedPlayer) return;', 'if (!isNm7CurrentEngine(nm7CreatedPlayer)) return;')
s = once(s, 'if (mPlayer != observed || mNm7Stopped) return;', 'if (!isNm7CurrentEngine(observed)) return;')
s = once(s, '        observed.addListener(mNm7PlaybackObserver);', '''        observed.addListener(mNm7PlaybackObserver);
        mNm7FrameObserver = new com.google.android.exoplayer2.video.VideoListener() {
            @Override public void onRenderedFirstFrame() {
                if (!isNm7CurrentEngine(observed) || mNm7StartupFrame) return;
                mNm7StartupFrame = true;
                if (mNm7VideoRequestedAt > 0) android.util.Log.i("NM7Playback",
                        "video_bind_to_frame_ms=" + (android.os.SystemClock.elapsedRealtime() - mNm7VideoRequestedAt));
                loadNm7CommentPreview();
            }
        };
        observed.addVideoListener(mNm7FrameObserver);''')
s = once(s, '        mNm7SessionVideo = item;', '''        mNm7VideoRequestedAt = android.os.SystemClock.elapsedRealtime();
        mNm7StartupFrame = false;
        mNm7SessionVideo = item;''')
s = once(s, '    private void loadNm7CommentPreview() {', '''    private void loadNm7CommentPreview() {
        if (!mNm7StartupFrame) return; // The selected video's first frame has network priority.''')
s = once(s, '    private void detachNm7PlaybackObserver() {', '''    private void detachNm7PlaybackObserver() {
        if (mNm7VisibilityTask != null) mHandler.removeCallbacks(mNm7VisibilityTask);
        mNm7VisibilityTask = null;''')
s = once(s, '        if (!mNm7Stopped && !sNm7SuspendedForIptv) applyNm7VideoVisibility();', '''        if (mNm7VisibilityTask != null) mHandler.removeCallbacks(mNm7VisibilityTask);
        mNm7VisibilityTask = null;
        if (!mNm7Stopped && !sNm7SuspendedForIptv) {
            if (mNm7AppBackground) {
                // Pause/resume also occurs between two app screens. Do not rebuild video
                // renderers during that short transition; keep-alive starts immediately below.
                final com.google.android.exoplayer2.SimpleExoPlayer observed = mPlayer;
                mNm7VisibilityTask = () -> {
                    mNm7VisibilityTask = null;
                    if (isNm7CurrentEngine(observed) && mNm7AppBackground) applyNm7VideoVisibility();
                };
                mHandler.postDelayed(mNm7VisibilityTask, 300L);
            } else applyNm7VideoVisibility();
        }''')
p.write_text(s)

# A click starts exactly one lookup while the cold Activity/decoder initializes.
# One pending result in RAM only. No cache of signed URLs and no speculative feed prefetch.
(common / 'misc/Nm7SelectedRequest.java').write_text(Path('scripts/mobile-core/Nm7SelectedRequest.java').read_text())
p = common / 'app/models/playback/controllers/VideoLoaderController.java'
s = p.read_text()
s = once(s, '    private Disposable mFormatInfoAction;', '''    private Disposable mFormatInfoAction;
    private final com.liskovsoft.smartyoutubetv2.common.misc.Nm7SelectedRequest<MediaItemFormatInfo> mNm7SelectedRequest =
            new com.liskovsoft.smartyoutubetv2.common.misc.Nm7SelectedRequest<>();''')
s = once(s, '            mPendingVideo = item;', '''            mPendingVideo = item;
            disposeActions();
            startNm7SelectedFormat(item, null);''')
s = method(s, 'private void loadFormatInfo(Video video)', '''    private void loadFormatInfo(Video video) {
        PlaybackView owner = getPlayer();
        if (owner == null) return;
        owner.showProgressBar(true);
        if (mNm7SelectedRequest.attach(video.videoId, owner)) {
            deliverNm7SelectedFormat();
            return;
        }
        disposeActions();
        startNm7SelectedFormat(video, owner);
    }

    private void startNm7SelectedFormat(Video video, PlaybackView owner) {
        final long token = mNm7SelectedRequest.start(video.videoId);
        if (owner != null) mNm7SelectedRequest.attach(video.videoId, owner);
        final long started = android.os.SystemClock.elapsedRealtime();
        android.util.Log.i("NM7Playback", "format_request_start seq=" + token);
        mFormatInfoAction = YouTubeServiceManager.instance().getMediaItemService()
                .getFormatInfoObserve(video.videoId)
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    if (!mNm7SelectedRequest.complete(token, info, null)) return;
                    android.util.Log.i("NM7Playback", "format_ready_ms="
                            + (android.os.SystemClock.elapsedRealtime() - started) + " seq=" + token);
                    deliverNm7SelectedFormat();
                }, error -> {
                    if (!mNm7SelectedRequest.complete(token, null, error)) return;
                    deliverNm7SelectedFormat();
                });
    }

    private void deliverNm7SelectedFormat() {
        PlaybackView owner = getPlayer();
        Video video = getVideo();
        if (owner == null || video == null) return;
        com.liskovsoft.smartyoutubetv2.common.misc.Nm7SelectedRequest.Result<MediaItemFormatInfo> result =
                mNm7SelectedRequest.take(owner, video.videoId);
        if (result == null) return;
        if (result.error != null) {
            owner.showProgressBar(false);
            mErrorFixerController.runFormatErrorAction(result.error);
        } else processFormatInfo(result.value);
    }''')
s = once(s, '    private void disposeActions() {', '''    private void disposeActions() {
        mNm7SelectedRequest.cancel();
        Utils.removeCallbacks(mShowProgressBar);''')
p.write_text(s)
print('v68 single Activity owner, selected-source pipeline and transition renderer debounce applied')
