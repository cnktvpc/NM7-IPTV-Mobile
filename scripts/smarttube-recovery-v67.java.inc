    private final com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7RenderRecoveryPolicy mNm7RenderPolicy =
            new com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7RenderRecoveryPolicy();
    private Runnable mNm7RenderTick, mNm7DecoderRetry;
    private int mNm7RenderGeneration;
    private boolean mNm7RecoveryVideoLimited;

    private void cancelNm7RenderWatchdog() {
        ++mNm7RenderGeneration;
        if (mNm7RenderTick != null) mHandler.removeCallbacks(mNm7RenderTick);
        mNm7RenderTick = null;
    }

    private void cancelNm7DecoderRetry() {
        if (mNm7DecoderRetry != null) mHandler.removeCallbacks(mNm7DecoderRetry);
        mNm7DecoderRetry = null;
    }

    private void detachNm7PlaybackObserver() {
        cancelNm7RenderWatchdog();
        cancelNm7DecoderRetry();
        if (mNm7ObservedPlayer != null) {
            if (mNm7PlaybackObserver != null) mNm7ObservedPlayer.removeListener(mNm7PlaybackObserver);
            if (mNm7FrameObserver != null) mNm7ObservedPlayer.removeVideoListener(mNm7FrameObserver);
        }
        mNm7ObservedPlayer = null;
        mNm7PlaybackObserver = null;
        mNm7FrameObserver = null;
        mNm7RenderPolicy.resetObservation(); // Keep the recovery budget through recreation.
    }

    private boolean nm7VisibleVideoTarget() {
        if (mNm7AppBackground || !nm7ScreenInteractive() || mNm7Stopped || sNm7SuspendedForIptv
                || mNm7TargetRestoreWaiting || isFinishing() || isDestroyed()
                || mPlayer == null || mPlayer.getVideoFormat() == null
                || mNm7VideoTarget == null || !mNm7VideoTarget.isAttachedToWindow()
                || !mNm7VideoTarget.isShown() || mNm7VideoTarget.getWindowVisibility() != android.view.View.VISIBLE) return false;
        android.view.View surface = mNm7VideoTarget.getVideoSurfaceView();
        if (surface instanceof android.view.SurfaceView) {
            android.view.Surface output = ((android.view.SurfaceView) surface).getHolder().getSurface();
            return output != null && output.isValid();
        }
        return surface instanceof android.view.TextureView && ((android.view.TextureView) surface).isAvailable();
    }

    private void ensureNm7PlaybackObserver() {
        if (mPlayer == null || mPlayer == mNm7ObservedPlayer) return;
        detachNm7PlaybackObserver();
        final com.google.android.exoplayer2.SimpleExoPlayer observed = mPlayer;
        mNm7ObservedPlayer = observed;
        mNm7PlaybackObserver = new com.google.android.exoplayer2.Player.EventListener() {
            @Override public void onPlayerStateChanged(boolean playWhenReady, int state) {
                if (mPlayer != observed || mNm7Stopped) return;
                // READY is not a new first-frame event, and must never reset retry budgets.
                armNm7RenderWatchdog();
            }
        };
        observed.addListener(mNm7PlaybackObserver);
    }

    private void armNm7RenderWatchdog() {
        cancelNm7RenderWatchdog();
        if (mPlayer == null || mNm7Stopped || sNm7SuspendedForIptv || mNm7AppBackground
                || isFinishing() || isDestroyed() || mNm7RenderPolicy.exhausted()) return;
        final com.google.android.exoplayer2.SimpleExoPlayer observed = mPlayer;
        final int generation = mNm7RenderGeneration;
        mNm7RenderTick = new Runnable() {
            @Override public void run() {
                if (generation != mNm7RenderGeneration || mPlayer != observed || mNm7Stopped
                        || sNm7SuspendedForIptv || mNm7AppBackground || isFinishing() || isDestroyed()) return;
                com.google.android.exoplayer2.decoder.DecoderCounters counters = observed.getVideoDecoderCounters();
                if (counters != null) counters.ensureUpdated();
                boolean eligible = observed.getPlayWhenReady() && observed.getPlaybackState() == Player.STATE_READY
                        && observed.getPlaybackSuppressionReason() == Player.PLAYBACK_SUPPRESSION_REASON_NONE
                        && observed.getPlaybackError() == null && counters != null && nm7VisibleVideoTarget()
                        && !mNm7RecoveryPending && mNm7DecoderRetry == null;
                int action = mNm7RenderPolicy.sample(android.os.SystemClock.elapsedRealtime(), observed,
                        mNm7VideoTarget, counters == null ? 0 : counters.renderedOutputBufferCount, eligible);
                if (action != 0) performNm7RenderRecovery(action, false);
                if (generation == mNm7RenderGeneration && mPlayer == observed && !mNm7RenderPolicy.exhausted())
                    mHandler.postDelayed(this, 1000L);
            }
        };
        mHandler.postDelayed(mNm7RenderTick, 1000L);
    }

    private void applyNm7RecoveryVideoLimit() {
        if (!mNm7RecoveryVideoLimited || mNm7TrackSelector == null) return;
        mNm7TrackSelector.setParameters(mNm7TrackSelector.buildUponParameters()
                .setMaxVideoSize(1280, 720).setMaxVideoBitrate(4_000_000)
                .setExceedVideoConstraintsIfNecessary(false));
    }

    private void performNm7RenderRecovery(int action, boolean decoderError) {
        if (mPlayer == null || mNm7Stopped || sNm7SuspendedForIptv || isFinishing() || isDestroyed()) return;
        if (!mPlayer.getPlayWhenReady()) return; // Never turn an explicit pause into playback.
        android.util.Log.w("NM7Playback", "render_recovery action=" + action + " decoder=" + decoderError);
        if (action == 4) {
            cancelNm7RenderWatchdog(); cancelNm7DecoderRetry();
            mNm7RecoveryPending = mNm7RecoveryRestoreIntent = false;
            mNm7ErrorLabel = "Không thể khôi phục video • chạm phát để thử lại";
            mPlayer.setPlayWhenReady(false);
            showProgressBar(false);
            syncNm7KeepAlive();
            return;
        }
        if (action == 3) { mNm7RecoveryVideoLimited = true; applyNm7RecoveryVideoLimit(); }
        if (action == 1) {
            if (decoderError) mPlayer.retry();
            else if (mNm7VideoTarget != null) {
                // Same-target switchTargetView is a no-op. Reattach only after measured stall.
                mNm7VideoTarget.setPlayer(null);
                mNm7VideoTarget.setPlayer(mPlayer);
            }
            armNm7RenderWatchdog();
        } else {
            mNm7RecoveryWantsPlay = mPlayer.getPlayWhenReady();
            mNm7RecoveryRestoreIntent = true;
            restartEngine(); // Upstream VideoStateController retains position across this call.
        }
    }

    private void recoverNm7DecoderError(com.google.android.exoplayer2.ExoPlaybackException error) {
        if (mPlayer == null || mNm7Stopped || sNm7SuspendedForIptv || isFinishing() || isDestroyed()
                || !isNm7DecoderError(error) || !mPlayer.getPlayWhenReady() || mNm7DecoderRetry != null) return;
        cancelNm7RenderWatchdog();
        final com.google.android.exoplayer2.SimpleExoPlayer observed = mPlayer;
        // Queue fast repeated errors; do not silently drop an error during a cooldown.
        mNm7DecoderRetry = () -> {
            mNm7DecoderRetry = null;
            if (mPlayer != observed || mNm7Stopped || sNm7SuspendedForIptv || isFinishing() || isDestroyed()
                    || !observed.getPlayWhenReady() || !isNm7DecoderError(observed.getPlaybackError())) return;
            performNm7RenderRecovery(mNm7RenderPolicy.failure(), true);
        };
        mHandler.postDelayed(mNm7DecoderRetry, 1500L);
    }
