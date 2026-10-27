"""Stable overlay chrome and bounded, source-aware mini error recovery."""
from pathlib import Path
import re
root = Path('third_party/SmartTube-droid')
ui = root / 'smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui'
res = root / 'smarttubedroid/src/main/res'
def once(s, old, new):
    assert s.count(old) == 1, old[:100]
    return s.replace(old, new, 1)
def method(s, signature, body):
    s, count = re.subn(r'(?ms)^    '+re.escape(signature)+r' \{.*?^    \}',
                       lambda _: '    '+signature+' {\n'+body+'\n    }', s)
    assert count == 1, signature
    return s
# Header/footer overlay a fixed-size content viewport. Never resize the RecyclerView on scroll.
p = res / 'layout/browse_activity.xml'
s = p.read_text().replace('<LinearLayout xmlns:', '<FrameLayout xmlns:', 1)
s = s.replace('    android:orientation="vertical"\n', '', 1).replace('    android:paddingBottom="64dp"', '', 1)
a = s.index('    <com.google.android.material.appbar.AppBarLayout')
b = s.index('    </com.google.android.material.appbar.AppBarLayout>', a)+len('    </com.google.android.material.appbar.AppBarLayout>')
header = s[a:b]; s = s[:a]+s[b:]
s = once(s, 'android:layout_height="0dp"\n        android:layout_weight="1"', 'android:layout_height="match_parent"')
pos = s.rindex('</LinearLayout>'); s = s[:pos]+header+'\n</FrameLayout>'+s[pos+len('</LinearLayout>'):]
p.write_text(s)
p = ui / 'browse/BrowseActivity.java'; s = p.read_text()
s = once(s, '    private int mNm7ScrollDistance;', '''    private int mNm7ScrollDistance;
    private final com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7ChromeGesture mNm7ChromeGesture =
            new com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7ChromeGesture();

    private boolean isNm7ChromeTouch(android.view.MotionEvent event) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        View header = findViewById(R.id.browse_app_bar);
        View footer = getWindow().getDecorView().findViewWithTag("nm7_home_tab_bar");
        for (View view : new View[]{header, footer}) {
            if (view != null && view.isShown() && view.getGlobalVisibleRect(bounds)
                    && bounds.contains((int) event.getRawX(), (int) event.getRawY())) return true;
        }
        return false;
    }''')
s = once(s, 'mNm7SwipeEligible = !isNm7MiniTouch(event) && mNm7BrowseContent != null',
         'mNm7SwipeEligible = !isNm7MiniTouch(event) && !isNm7ChromeTouch(event) && mNm7BrowseContent != null')
s = once(s, '        return consumed || super.dispatchTouchEvent(event);', '''        int chrome = mNm7ChromeGesture.update(event, mNm7SwipeEligible,
                24 * getResources().getDisplayMetrics().density);
        if (!consumed && chrome != 0) setNm7ChromeHidden(chrome > 0);
        return consumed || super.dispatchTouchEvent(event);''')
s = method(s, 'private void setNm7ChromeHidden(boolean hidden)', '''        mNm7ChromeHidden = hidden;
        // INVISIBLE preserves measurement. Content bounds and padding never change here.
        findViewById(R.id.browse_app_bar).setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
        try {
            Class.forName("vn.phuong.iptvplayer.HomeTabBar")
                    .getMethod("setVisible", android.app.Activity.class, boolean.class)
                    .invoke(null, this, !hidden);
        } catch (ReflectiveOperationException error) {
            android.util.Log.e("NM7Navigation", "Browse chrome", error);
        }''')
s = method(s, 'private void installNm7ScrollChrome(RecyclerView list)', '''        list.setItemViewCacheSize(2);
        list.setClipToPadding(false);
        View header = findViewById(R.id.browse_app_bar);
        header.post(() -> {
            // One-time inset, including the hidden settings list; no scroll listener/feedback loop.
            list.setPadding(list.getPaddingLeft(), header.getHeight(), list.getPaddingRight(),
                    Math.round(64 * getResources().getDisplayMetrics().density));
        });''')
s = once(s, '        mSettingsView.setAdapter(mSettingsAdapter);', '        mSettingsView.setAdapter(mSettingsAdapter);\n        installNm7ScrollChrome(mSettingsView);')
p.write_text(s)
# ErrorFixer must be allowed to select a different transport after a failure.
p = ui / 'playback/PlaybackActivity.java'; s = p.read_text()
s = once(s, '    private boolean mNm7OwnsPlayback;', '''    private boolean mNm7OwnsPlayback;
    private boolean mNm7InitialTransportSelected;
    private com.liskovsoft.smartyoutubetv2.common.app.models.data.Video mNm7SessionVideo;
    private final com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7MiniRecoveryGate mNm7RecoveryGate =
            new com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7MiniRecoveryGate();
    private String mNm7ErrorLabel;
    private boolean mNm7RecoveryPending;
    private boolean mNm7ManualRetry;
    private boolean mNm7RecoveryWantsPlay;
    private boolean mNm7RecoveryRestoreIntent;
    private Runnable mNm7RecoveryTimeout;

    public boolean allowNm7MiniEngineRecovery(int type, int renderer, Throwable error) {
        if (mNm7Stopped || isFinishing() || isDestroyed() || sNm7SuspendedForIptv) return false;
        if (!sNm7Mini) return true;
        Throwable cause = error;
        for (int i = 0; i < 6 && cause != null && cause.getCause() != null; i++) cause = cause.getCause();
        String name = cause == null ? "Unknown" : cause.getClass().getSimpleName();
        // Only class/type codes: do not expose stream URLs, cookies or signed query strings.
        mNm7ErrorLabel = name.contains("OutOfMemory") ? "Thiếu bộ nhớ"
                : name.contains("Timeout") || name.contains("UnknownHost") || name.contains("Connect") ? "Lỗi kết nối mạng"
                : type == 0 ? "Lỗi nguồn phát" : type == 1 ? "Lỗi giải mã" : "Lỗi bộ phát";
        Throwable current = error;
        for (int i = 0; i < 6 && current != null; i++, current = current.getCause()) {
            try {
                Object code = current.getClass().getField("responseCode").get(current);
                if (code instanceof Integer) mNm7ErrorLabel = "Nguồn phát HTTP " + code;
            } catch (ReflectiveOperationException ignored) { }
        }
        android.util.Log.w("NM7Playback", "mini_error type=" + type + " renderer=" + renderer + " cause=" + name);
        boolean manual = mNm7ManualRetry;
        mNm7ManualRetry = false;
        if (mNm7RecoveryTimeout != null) mHandler.removeCallbacks(mNm7RecoveryTimeout);
        mNm7RecoveryPending = mNm7RecoveryGate.allow(
                mNm7SessionVideo == null ? null : mNm7SessionVideo.videoId,
                manual || (mPlayer != null && mPlayer.getPlayWhenReady()), sNm7SuspendedForIptv, manual);
        if (mNm7RecoveryPending) {
            mNm7RecoveryWantsPlay = true;
            mNm7RecoveryRestoreIntent = true;
            mNm7RecoveryTimeout = () -> mNm7RecoveryPending = false;
            mHandler.postDelayed(mNm7RecoveryTimeout, 15000);
        }
        return mNm7RecoveryPending;
    }

    public static String getNm7ErrorLabel() {
        return sNm7Active == null || sNm7Active.mNm7ErrorLabel == null
                ? "Không phát được video" : sNm7Active.mNm7ErrorLabel;
    }

    private void retryNm7Mini() {
        if (sNm7SuspendedForIptv || mNm7Stopped || mPlayer == null) return;
        if (mNm7RecoveryPending) {
            mNm7RecoveryWantsPlay = !mNm7RecoveryWantsPlay;
            mPlayer.setPlayWhenReady(mNm7RecoveryWantsPlay);
            return;
        }
        com.google.android.exoplayer2.ExoPlaybackException error = mPlayer.getPlaybackError();
        if (error == null) { mPlayer.setPlayWhenReady(!mPlayer.getPlayWhenReady()); return; }
        mNm7ManualRetry = true;
        mPlayer.setPlayWhenReady(true);
        // Go through the existing source/client/decoder recovery, not retry the same failed URL.
        mPlaybackPresenter.getController(
                com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ErrorFixerController.class)
                .onEngineError(error.type, error.rendererIndex, error.getCause() == null ? error : error.getCause());
    }''')
s = once(s, '        if (getPlayerTweaksData().getPreferredDnsType()',
         '        if (!mNm7InitialTransportSelected && getPlayerTweaksData().getPreferredDnsType()')
s = once(s, '        createPlayerObjects();', '        mNm7InitialTransportSelected = true;\n        createPlayerObjects();')
s = once(s, '        mExoPlayerController.setPlayer(mPlayer);',
         '        mExoPlayerController.setPlayerView(this);\n        mExoPlayerController.setPlayer(mPlayer);')
s = method(s, 'public static void toggleNm7Playback()', '''        if (!isNm7SessionActive() || sNm7SuspendedForIptv) return;
        sNm7Active.retryNm7Mini();''')
s = once(s, '        if (sNm7Active.mPlayer.getPlaybackError() != null) return -1;',
         '        if (sNm7Active.mNm7RecoveryPending) return 2;\n        if (sNm7Active.mPlayer.getPlaybackError() != null) return -1;')
s = once(s, '        mExoPlayerController.setVideo(item);', '''        if (!android.text.TextUtils.equals(mNm7SessionVideo == null ? null : mNm7SessionVideo.videoId,
                item == null ? null : item.videoId)) {
            mNm7RecoveryPending = mNm7RecoveryRestoreIntent = false;
            mNm7ErrorLabel = null;
            if (mNm7RecoveryTimeout != null) mHandler.removeCallbacks(mNm7RecoveryTimeout);
        }
        mNm7SessionVideo = item;
        mNm7RecoveryGate.video(item == null ? null : item.videoId);
        mExoPlayerController.setVideo(item);''')
s = once(s, '        return mExoPlayerController != null ? mExoPlayerController.getVideo() : null;',
         '        return mNm7SessionVideo != null ? mNm7SessionVideo : mExoPlayerController != null ? mExoPlayerController.getVideo() : null;')
s = once(s, '            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {', '''            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (sNm7SuspendedForIptv && playWhenReady) {
                    mPlayer.setPlayWhenReady(false);
                    return;
                }
                if (playbackState == Player.STATE_READY) {
                    mNm7RecoveryPending = false;
                    mNm7ErrorLabel = null;
                    if (mNm7RecoveryRestoreIntent) {
                        mNm7RecoveryRestoreIntent = false;
                        mPlayer.setPlayWhenReady(mNm7RecoveryWantsPlay);
                        playWhenReady = mPlayer.getPlayWhenReady();
                    }
                    if (mNm7RecoveryTimeout != null) mHandler.removeCallbacks(mNm7RecoveryTimeout);
                }''')
s = once(s, '    protected void onDestroy() {', '''    protected void onDestroy() {
        if (mNm7RecoveryTimeout != null) mHandler.removeCallbacks(mNm7RecoveryTimeout);''')
s = once(s, '    public void restartEngine() {', '''    public void restartEngine() {
        if (sNm7SuspendedForIptv || mNm7Stopped) return;''')
p.write_text(s)
# Keep the native source-aware strategy, but never retry mini indefinitely or while IPTV owns playback.
p = root / 'common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ErrorFixerController.java'
s = p.read_text()
s = once(s, '        runEngineErrorAction(type, rendererIndex, error);', '''        if (getPlayer() != null) {
            try {
                Object allow = getPlayer().getClass()
                        .getMethod("allowNm7MiniEngineRecovery", int.class, int.class, Throwable.class)
                        .invoke(getPlayer(), type, rendererIndex, error);
                if (Boolean.FALSE.equals(allow)) return;
            } catch (NoSuchMethodException ignored) {
                // Other native playback implementations keep their existing recovery policy.
            } catch (ReflectiveOperationException failure) {
                android.util.Log.e("NM7Playback", "Mini recovery bridge", failure);
                return;
            }
        }
        runEngineErrorAction(type, rendererIndex, error);''')
p.write_text(s)
print('v57 fixed viewport, finger-only chrome and source-aware bounded mini recovery applied')
