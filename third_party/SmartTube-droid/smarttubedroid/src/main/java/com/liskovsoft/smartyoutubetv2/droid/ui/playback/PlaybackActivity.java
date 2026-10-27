package com.liskovsoft.smartyoutubetv2.droid.ui.playback;

import android.annotation.TargetApi;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Rational;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.vkay94.dtpv.DoubleTapPlayerViewImpl;
import com.github.vkay94.dtpv.youtube.YouTubeOverlay;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.QueueNavigator;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.util.Util;
import com.google.android.material.button.MaterialButton;
import com.liskovsoft.mediaserviceinterfaces.LiveChatService;
import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.sharedutils.helpers.DateHelper;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoGroupAdapter;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7FeedAdapter;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import io.reactivex.disposables.Disposable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Touch player screen. Implements common's {@link PlaybackView} = {@code PlayerManager}
 * ({@code PlayerEngine} + {@code PlayerUI}), so every existing controller
 * (video loader, suggestions, SponsorBlock, HQ dialog, remote, state...) works unchanged.
 * <br/>
 * The engine half is pure delegation to {@link ExoPlayerController} — the wiring order is
 * copied from smarttubetv's {@code EmbedPlayerView} (the minimal non-Leanback host).
 * The UI half is re-implemented as touch UI: a controls overlay with a 3s auto-hide,
 * a portrait metadata panel with suggestions rows, and YouTube-like gestures
 * (see {@link PlaybackGestureHandler}).
 */
public class PlaybackActivity extends DroidActivity implements PlaybackView,
        com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerView,
        PlaybackGestureHandler.Listener, VideoGroupAdapter.Listener,
        com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.Nm7EngineErrorOwner,
        com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.Nm7PlaybackEndOwner,
        com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.Nm7CurrentEngineOwner {
    private static final String TAG = PlaybackActivity.class.getSimpleName();
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
        if (mNm7VisibilityTask != null) mHandler.removeCallbacks(mNm7VisibilityTask);
        mNm7VisibilityTask = null;
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

    private void nm7CancelPosterReadyFallback() {
        if (mNm7PosterReadyFallback != null) {
            mHandler.removeCallbacks(mNm7PosterReadyFallback);
            mNm7PosterReadyFallback = null;
        }
    }

    private void nm7CancelPosterRenderProbe() {
        if (mNm7PosterRenderProbe != null) {
            mHandler.removeCallbacks(mNm7PosterRenderProbe);
            mNm7PosterRenderProbe = null;
        }
    }

    private void nm7HideStartupPoster(String reason) {
        nm7CancelPosterReadyFallback();
        nm7CancelPosterRenderProbe();
        mNm7StartupFrame = true;
        mNm7FirstFrameRendered = true;
        mNm7PosterVideoId = null;
        if (mNm7StartupPoster != null) {
            try {
                Glide.with(PlaybackActivity.this).clear(mNm7StartupPoster);
            } catch (RuntimeException ignored) { }
            mNm7StartupPoster.setImageDrawable(null);
            mNm7StartupPoster.setVisibility(View.GONE);
        }
        if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
        android.util.Log.i("NM7Playback", "startup_poster_hidden reason=" + reason);
        loadNm7CommentPreview();
    }

    private int nm7RenderedVideoBufferCount() {
        if (mPlayer == null) return 0;
        com.google.android.exoplayer2.decoder.DecoderCounters counters =
                mPlayer.getVideoDecoderCounters();
        if (counters == null) return 0;
        counters.ensureUpdated();
        return counters.renderedOutputBufferCount;
    }

    private void nm7ArmPosterRenderProbe(final String expectedVideoId) {
        nm7CancelPosterRenderProbe();
        if (mPlayer == null) return;

        mNm7PosterRenderedBaseline = nm7RenderedVideoBufferCount();
        mNm7PosterProbeStartedAt = android.os.SystemClock.elapsedRealtime();

        mNm7PosterRenderProbe = new Runnable() {
            @Override public void run() {
                if (mNm7PosterRenderProbe != this) return;
                if (mPlayer == null || mNm7Stopped || sNm7SuspendedForIptv
                        || isFinishing() || isDestroyed()) {
                    nm7CancelPosterRenderProbe();
                    return;
                }

                String currentVideoId = mNm7SessionVideo == null
                        ? null : mNm7SessionVideo.videoId;
                if (!android.text.TextUtils.equals(expectedVideoId, currentVideoId)) {
                    nm7CancelPosterRenderProbe();
                    return;
                }

                int rendered = nm7RenderedVideoBufferCount();
                if (rendered < mNm7PosterRenderedBaseline) {
                    // Some renderer recreations reset DecoderCounters.
                    mNm7PosterRenderedBaseline = rendered;
                }

                long elapsed = android.os.SystemClock.elapsedRealtime()
                        - mNm7PosterProbeStartedAt;
                if (mNm7FirstFrameRendered || (mNm7PosterRenderedBaseline >= 0 && rendered > mNm7PosterRenderedBaseline)) {
                    nm7HideStartupPoster("decoder_frame_rendered");
                    return;
                }
                boolean readyAndPlaying = mPlayer.getPlaybackState() == com.google.android.exoplayer2.Player.STATE_READY
                        && mPlayer.getPlayWhenReady()
                        && mPlayer.getPlaybackError() == null;

                // Safety path: never allow a thumbnail overlay to freeze the visible
                // player indefinitely.
                if (readyAndPlaying && elapsed >= 400L) {
                    nm7HideStartupPoster("ready_safety_timeout");
                    return;
                }
                if (elapsed >= 1500L) {
                    nm7HideStartupPoster("absolute_safety_timeout");
                    return;
                }

                mHandler.postDelayed(this, 80L);
            }
        };
        mHandler.postDelayed(mNm7PosterRenderProbe, 80L);
    }

    private void nm7ArmPosterReadyFallback(final com.google.android.exoplayer2.SimpleExoPlayer observed) {
        nm7CancelPosterReadyFallback();
        if (mNm7StartupPoster == null || observed == null) return;
        final String expectedVideoId = mNm7SessionVideo == null ? null : mNm7SessionVideo.videoId;
        mNm7PosterReadyFallback = () -> {
            mNm7PosterReadyFallback = null;
            if (!isNm7CurrentEngine(observed) || mNm7Stopped || sNm7SuspendedForIptv
                    || isFinishing() || isDestroyed()) return;
            String currentVideoId = mNm7SessionVideo == null ? null : mNm7SessionVideo.videoId;
            if (!android.text.TextUtils.equals(expectedVideoId, currentVideoId)) return;
            if (observed.getPlaybackState() == Player.STATE_READY
                    && observed.getVideoFormat() != null
                    && observed.getPlayWhenReady()) {
                nm7HideStartupPoster("ready_fallback");
            }
        };
        mHandler.postDelayed(mNm7PosterReadyFallback, 350L);
    }

    private void ensureNm7PlaybackObserver() {
        if (mPlayer == null || mPlayer == mNm7ObservedPlayer) return;
        detachNm7PlaybackObserver();
        final com.google.android.exoplayer2.SimpleExoPlayer observed = mPlayer;
        mNm7ObservedPlayer = observed;
        mNm7PlaybackObserver = new com.google.android.exoplayer2.Player.EventListener() {
            @Override public void onPlayerStateChanged(boolean playWhenReady, int state) {
                if (!isNm7CurrentEngine(observed)) return;
                // READY is not a new first-frame event, and must never reset retry budgets.
                armNm7RenderWatchdog();
            }
        };
        observed.addListener(mNm7PlaybackObserver);
        mNm7FrameObserver = new com.google.android.exoplayer2.video.VideoListener() {
            @Override public void onRenderedFirstFrame() {
                if (!isNm7CurrentEngine(observed)) return;
                mNm7FirstFrameSeenAt = android.os.SystemClock.elapsedRealtime();
                mNm7FirstFramePositionMs = observed.getCurrentPosition();
                // Immediately reveal video as soon as the first frame is rendered
                nm7HideStartupPoster("first_frame_rendered");
                if (mNm7VideoRequestedAt > 0) android.util.Log.i("NM7Playback",
                        "video_bind_to_frame_ms=" + (android.os.SystemClock.elapsedRealtime() - mNm7VideoRequestedAt));
            }
        };
        observed.addVideoListener(mNm7FrameObserver);
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

    private static PlaybackActivity sNm7Active;
    private static boolean sNm7Mini;
    private static boolean sNm7RestorePending;
    private static boolean sNm7ResumeAfterIptv;
    private static boolean sNm7SuspendedForIptv;
    private boolean mNm7Stopped;
    private boolean mNm7LeavingForMini;
    private boolean mNm7Backgrounding;
    private boolean mNm7TargetRestoreWaiting;
    private boolean mNm7ResumeAfterTarget;
    private com.google.android.exoplayer2.video.VideoListener mNm7FirstFrameListener;
    private Runnable mNm7TargetRestoreTimeout;
    private long mNm7TargetRestoreStarted;
    private com.google.android.exoplayer2.ui.PlayerView mNm7VideoTarget;
    @Override public boolean hasNm7MediaEnded() {
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

    @Override public boolean isNm7CurrentEngine(Object engine) {
        return sNm7Active == this && !mNm7Stopped && !isFinishing() && !isDestroyed()
                && engine != null && engine == mPlayer;
    }

    private Runnable mNm7VisibilityTask;
    private long mNm7VideoRequestedAt;
    private boolean mNm7StartupFrame;
    private boolean mNm7OwnsPlayback;
    private boolean mNm7OomRecoveryActive;
    private long mNm7LastOomRecoveryMs;

    private void trimNm7MemoryForRecovery(boolean oom) {
        if (!oom) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - mNm7LastOomRecoveryMs < 5000L) return;
        mNm7LastOomRecoveryMs = now;
        mNm7OomRecoveryActive = true;
        mNm7RecoveryVideoLimited = true;
        try {
            com.bumptech.glide.Glide.get(getApplicationContext()).clearMemory();
        } catch (RuntimeException ignored) { }
        if (mSuggestionsView != null) {
            mSuggestionsView.setItemViewCacheSize(10);
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
    }
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
        mNm7RenderPolicy.resetObservation();
        if (mNm7AppBackground) cancelNm7RenderWatchdog(); else armNm7RenderWatchdog();
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
    }
    private boolean mNm7InitialTransportSelected;
    private com.liskovsoft.smartyoutubetv2.common.app.models.data.Video mNm7SessionVideo;
    private final com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7MiniRecoveryGate mNm7RecoveryGate =
            new com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7MiniRecoveryGate();
    private String mNm7ErrorLabel;
    private com.google.android.exoplayer2.ExoPlaybackException mNm7LastEngineError;

    @Override public void onNm7OwnedEngineError(com.google.android.exoplayer2.ExoPlaybackException error) {
        if (!isNm7CurrentEngine(mPlayer)) return;
        mNm7LastEngineError = error;
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
                : com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7PlaybackError.describe(error, error.type);
        if (mNm7Stopped || isFinishing() || isDestroyed() || sNm7SuspendedForIptv) return;
        // Decoder recovery is owned here. Do not also send the same decoder error into
        // ErrorFixerController, otherwise retry/restart paths can race and break playback.
        if (isNm7DecoderError(error)) {
            recoverNm7DecoderError(error);
            return;
        }
        mPlaybackPresenter.setView(this);
        mPlaybackPresenter.onEngineError(error.type, error.rendererIndex,
                error.getCause() == null ? error : error.getCause());
    }
    private boolean mNm7RecoveryPending;
    private boolean mNm7ManualRetry;
    private boolean mNm7RecoveryWantsPlay;
    private boolean mNm7RecoveryRestoreIntent;
    private Runnable mNm7RecoveryTimeout;

    public boolean allowNm7MiniEngineRecovery(int type, int renderer, Throwable error) {
        if (mNm7Stopped || isFinishing() || isDestroyed() || sNm7SuspendedForIptv) return false;

        Throwable cause = error;
        for (int i = 0; i < 6 && cause != null && cause.getCause() != null; i++) cause = cause.getCause();
        String name = cause == null ? "Unknown" : cause.getClass().getSimpleName();
        if (name.contains("OutOfMemory")) { Glide.get(getApplicationContext()).clearMemory(); if (mSuggestionsView != null) mSuggestionsView.getRecycledViewPool().clear(); }
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
        if (!sNm7Mini && !name.contains("OutOfMemory")) return true;
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
        PlaybackActivity owner = sNm7Active;
        if (owner == null || owner.mNm7Stopped || owner.isDestroyed()) return "Phiên phát đã kết thúc";
        if (owner.mPlayer == null) return "Bộ phát cần khởi tạo lại";
        com.google.android.exoplayer2.ExoPlaybackException error = owner.mPlayer.getPlaybackError();
        if (error != null) return com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7PlaybackError.describe(error, error.type);
        return owner.mNm7ErrorLabel == null ? "Nguồn phát chưa sẵn sàng" : owner.mNm7ErrorLabel;
    }

    private void retryNm7Mini() {
        if (mNm7RenderPolicy.exhausted() && !sNm7SuspendedForIptv && !mNm7Stopped) {
            mNm7RenderPolicy.manualRetry();
            mNm7RecoveryWantsPlay = mNm7RecoveryRestoreIntent = true;
            mNm7ErrorLabel = null;
            restartEngine();
            return;
        }
        if (sNm7SuspendedForIptv || mNm7Stopped) return;
        mPlaybackPresenter.setView(this);
        if (mPlayer == null) {
            if (mNm7SessionVideo != null) {
                mNm7RecoveryWantsPlay = mNm7RecoveryRestoreIntent = true;
                initializePlayer();
            }
            return;
        }
        if (mNm7RecoveryPending || (mNm7RecoveryRestoreIntent && mPlayer.getPlaybackError() == null && mPlayer.getPlaybackState() != Player.STATE_IDLE)) {
            mNm7RecoveryWantsPlay = !mNm7RecoveryWantsPlay;
            mPlayer.setPlayWhenReady(mNm7RecoveryWantsPlay);
            return;
        }
        com.google.android.exoplayer2.ExoPlaybackException error = mPlayer.getPlaybackError();
        if (error == null) {
            if (mPlayer.getPlaybackState() == Player.STATE_ENDED) {
                mPlayer.seekTo(0);
                mPlayer.setPlayWhenReady(true);
                return;
            }
            if (mPlayer.getPlaybackState() == Player.STATE_IDLE && mNm7SessionVideo != null) {
                // A failed source reload can leave IDLE with no retained ExoPlayer exception.
                mNm7RecoveryWantsPlay = mNm7RecoveryRestoreIntent = true;
                restartEngine();
            } else mPlayer.setPlayWhenReady(!mPlayer.getPlayWhenReady());
            return;
        }
        cancelNm7DecoderRetry();
        if (isNm7DecoderError(error)) {
            mPlayer.setPlayWhenReady(true);
            recoverNm7DecoderError(error);
            return;
        }
        mNm7ManualRetry = true;
        mPlayer.setPlayWhenReady(true);
        // Go through the existing source/client/decoder recovery, not retry the same failed URL.
        mPlaybackPresenter.getController(
                com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ErrorFixerController.class)
                .onEngineError(error.type, error.rendererIndex, error.getCause() == null ? error : error.getCause());
    }
    private Object mNm7BackCallback;
    private com.google.android.exoplayer2.SimpleExoPlayer mNm7ObservedPlayer;
    private com.google.android.exoplayer2.Player.EventListener mNm7PlaybackObserver;
    private com.google.android.exoplayer2.video.VideoListener mNm7FrameObserver;





    private boolean isNm7DecoderError(Throwable error) {
        Throwable cause = error;
        for (int i = 0; i < 12 && cause != null; i++, cause = cause.getCause()) {
            String name = cause.getClass().getName();
            if (name.contains("MediaCodec")
                    || name.contains("DecoderInitializationException")
                    || name.contains("DecoderException")
                    || name.contains("VideoDecoder")) {
                return true;
            }
        }
        return false;
    }




    private void bindNm7PlayerTarget() {
        // ErrorFixerController may rebuild the engine while PlaybackActivity is stopped.
        // Binding the new player to its hidden full view leaves the visible mini black.
        if (sNm7SuspendedForIptv) {
            mPlayerView.setPlayer(null);
            mPlayer.setPlayWhenReady(false);
            return;
        }
        com.google.android.exoplayer2.ui.PlayerView target = mPlayerView;
        if (sNm7Mini) {
            try {
                android.view.View view = (android.view.View) Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer")
                        .getMethod("surfaceView").invoke(null);
                if (view instanceof com.google.android.exoplayer2.ui.PlayerView && view.isAttachedToWindow()) {
                    target = (com.google.android.exoplayer2.ui.PlayerView) view;
                }
            } catch (ReflectiveOperationException error) {
                android.util.Log.e("NM7Playback", "Read target after engine restart", error);
            }
        }
        ensureNm7PlaybackObserver();
        com.google.android.exoplayer2.ui.PlayerView.switchTargetView(mPlayer, mNm7VideoTarget, target);
        if (target.getPlayer() != mPlayer) target.setPlayer(mPlayer);
        mNm7VideoTarget = target;
        armNm7RenderWatchdog();
    }

    public static void prepareNm7Background() {
        PlaybackActivity owner = sNm7Active;
        if (owner == null || owner.mNm7Stopped || owner.isDestroyed() || sNm7SuspendedForIptv) return;
        owner.blockEngine(true);
        owner.nm7SetBackground(true);
    }
    public static void resumeNm7Foreground() {
        PlaybackActivity owner = sNm7Active;
        if (owner == null || owner.mNm7Stopped || owner.isDestroyed() || sNm7SuspendedForIptv) return;
        if (!owner.nm7ScreenInteractive()) { prepareNm7Background(); return; }
        owner.blockEngine(false);
        owner.nm7SetBackground(false);
    }
    public static boolean isNm7Playing() {
        return isNm7SessionActive() && sNm7Active.mPlayer.getPlayWhenReady()
                && sNm7Active.mPlayer.getPlaybackState() != Player.STATE_ENDED;
    }

    private static void recoverNm7EndedPlayback(PlaybackActivity active) {
        if (active == null || active.mPlayer == null || sNm7SuspendedForIptv) return;
        if (active.mPlayer.getPlaybackState() != Player.STATE_ENDED) return;
        try {
            android.util.Log.i("NM7Playback", "recover ended player: seek to start");
            active.mPlayer.seekTo(0);
            active.mPlayer.setPlayWhenReady(true);
        } catch (RuntimeException error) {
            android.util.Log.w("NM7Playback", "ended player prepare failed", error);
        }
    }

    public static void toggleNm7Playback() {
        PlaybackActivity owner = sNm7Active;
        if (owner == null || owner.mNm7Stopped || owner.isFinishing() || owner.isDestroyed()) return;
        if (sNm7SuspendedForIptv) {
            sNm7SuspendedForIptv = false;
            sNm7ResumeAfterIptv = false;
            try {
                Class.forName("vn.phuong.iptvplayer.MobileNm7Application")
                        .getMethod("pauseIptvForYoutube").invoke(null);
            } catch (Throwable ignored) {}
            owner.blockEngine(false);
            if (owner.mPlayer != null) {
                owner.mPlayer.setPlayWhenReady(true);
            }
            return;
        }
        owner.retryNm7Mini();
    }

    public static int getNm7PlaybackState() {
        if (!isNm7SessionActive()) return -1;
        if (sNm7Active.mNm7RenderPolicy.exhausted()) return -1;
        if (sNm7Active.mNm7RecoveryPending) return 2;
        if (sNm7Active.mPlayer.getPlaybackError() != null) return -1;
        return sNm7Active.mPlayer.getPlaybackState();
    }

    public static boolean isNm7SessionActive() {
        return sNm7Active != null && !sNm7Active.mNm7Stopped && !sNm7Active.isDestroyed()
                && !sNm7Active.isFinishing() && sNm7Active.mPlayer != null;
    }

    public static boolean isNm7MiniPlayerActive() {
        // A decoder rebuild is not an explicit close of the session.
        return sNm7Mini && sNm7Active != null && !sNm7Active.mNm7Stopped && !sNm7Active.isDestroyed()
                && !sNm7Active.isFinishing()
                && (sNm7Active.mPlayer != null || sNm7Active.mNm7SessionVideo != null);
    }

    public static String getNm7VideoTitle() {
        if (sNm7Active == null || sNm7Active.mNm7SessionVideo == null) return null;
        try {
            return sNm7Active.mNm7SessionVideo.getTitle();
        } catch (Throwable error) {
            try {
                return (String) sNm7Active.mNm7SessionVideo.getClass().getField("title").get(sNm7Active.mNm7SessionVideo);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static String getNm7VideoAuthor() {
        if (sNm7Active == null || sNm7Active.mNm7SessionVideo == null) return null;
        try {
            return sNm7Active.mNm7SessionVideo.getAuthor();
        } catch (Throwable error) {
            try {
                return (String) sNm7Active.mNm7SessionVideo.getClass().getField("author").get(sNm7Active.mNm7SessionVideo);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static long getNm7VideoPosition() {
        if (sNm7Active == null || sNm7Active.mPlayer == null) return 0L;
        try {
            return sNm7Active.mPlayer.getCurrentPosition();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static long getNm7VideoDuration() {
        if (sNm7Active == null || sNm7Active.mPlayer == null) return 0L;
        try {
            return sNm7Active.mPlayer.getDuration();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static void attachNm7MiniPlayer(android.view.View previousSurface, android.view.View nextSurface) {
        PlaybackActivity active = sNm7Active;
        if (!isNm7MiniPlayerActive()) return;
        if (!(nextSurface instanceof com.google.android.exoplayer2.ui.PlayerView)) return;
        if (sNm7SuspendedForIptv) {
            try {
                Object tab = Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                        .getMethod("tab", android.content.Context.class).invoke(null, active);
                if (!"youtube".equals(tab)) return;
            } catch (ReflectiveOperationException error) { return; }
            sNm7SuspendedForIptv = false;
        }

        active.blockEngine(active.mNm7AppBackground);
        com.google.android.exoplayer2.ui.PlayerView mini =
                (com.google.android.exoplayer2.ui.PlayerView) nextSurface;
        mini.setUseController(false);

        // Guard: Verify that mini\'s surface is valid before detaching oldTarget.
        // If not valid yet, wait for surfaceCreated so ExoPlayer never sets surface to null!
        android.view.View miniSurface = mini.getVideoSurfaceView();
        if (miniSurface instanceof android.view.SurfaceView) {
            final android.view.SurfaceHolder holder = ((android.view.SurfaceView) miniSurface).getHolder();
            if (holder.getSurface() == null || !holder.getSurface().isValid()) {
                holder.addCallback(new android.view.SurfaceHolder.Callback() {
                    private boolean done = false;
                    @Override public void surfaceCreated(android.view.SurfaceHolder h) {
                        if (done) return;
                        done = true;
                        try { holder.removeCallback(this); } catch (Throwable ignored) {}
                        mini.post(() -> attachNm7MiniPlayer(previousSurface, nextSurface));
                    }
                    @Override public void surfaceChanged(android.view.SurfaceHolder h, int f, int w, int h2) {}
                    @Override public void surfaceDestroyed(android.view.SurfaceHolder h) {}
                });
                return;
            }
        }
        if (active.mPlayer == null) {
            active.mNm7VideoTarget = mini;
            active.syncNm7KeepAlive();
            return;
        }
        com.google.android.exoplayer2.ui.PlayerView oldTarget = active.mNm7VideoTarget;
        if (oldTarget == null) oldTarget = active.mPlayerView;
        boolean switched = false;
        if (oldTarget != mini) {
            com.google.android.exoplayer2.ui.PlayerView.switchTargetView(active.mPlayer, oldTarget, mini);
            active.mNm7VideoTarget = mini;
            switched = true;
        } else if (mini.getPlayer() != active.mPlayer) {
            mini.setPlayer(active.mPlayer);
            switched = true;
        }
        active.syncNm7KeepAlive();
        if (sNm7ResumeAfterIptv) {
            sNm7ResumeAfterIptv = false;
            active.mPlayer.setPlayWhenReady(true);
            switched = true;
        }
        if (switched && active.mPlayer.getPlaybackState() == Player.STATE_READY) {
            final com.google.android.exoplayer2.SimpleExoPlayer playerRef = active.mPlayer;
            mini.post(() -> {
                if (active.mPlayer == playerRef && !active.mNm7Stopped && sNm7Mini) {
                    if (mini.getPlayer() != playerRef) {
                        mini.setPlayer(playerRef);
                    }
                }
            });
        }
        android.util.Log.i("NM7Playback", "target=mini state=" + active.mPlayer.getPlaybackState());
    }

    public static void restoreNm7Player() {
        PlaybackActivity active = sNm7Active;
        if (active == null || active.isDestroyed() || active.isFinishing() || active.mPlayer == null) return;
        try {
            Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("setTab", android.content.Context.class, String.class)
                    .invoke(null, active, "youtube");
        } catch (Throwable ignored) {}
        try {
            Class.forName("vn.phuong.iptvplayer.MobileInlinePlayerProviderV2")
                    .getMethod("releaseForYoutube").invoke(null);
        } catch (Throwable ignored) {}
        sNm7SuspendedForIptv = false;
        boolean ended = active.mPlayer.getPlaybackState() == Player.STATE_ENDED;
        if (ended) recoverNm7EndedPlayback(active);

        if (sNm7RestorePending) return;
        active.beginNm7TargetRestore();
        sNm7RestorePending = true;
        Intent intent = new Intent(active, active.getClass())
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        active.startActivity(intent);
        try { active.overridePendingTransition(0, 0); } catch (Throwable ignored) {}
    }

    private void completeNm7RestoreOnResume() {
        if (mPlayer == null) return;
        sNm7SuspendedForIptv = false;
        if (mNm7StartupPoster != null && mNm7FirstFrameRendered) {
            mNm7StartupPoster.setVisibility(View.GONE);
        }
        com.google.android.exoplayer2.ui.PlayerView oldTarget = mNm7VideoTarget;
        if (oldTarget == null) {
            try {
                android.view.View view = (android.view.View) Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer")
                        .getMethod("surfaceView").invoke(null);
                if (view instanceof com.google.android.exoplayer2.ui.PlayerView) {
                    oldTarget = (com.google.android.exoplayer2.ui.PlayerView) view;
                }
            } catch (ReflectiveOperationException error) {
                android.util.Log.e("NM7Playback", "Read mini target", error);
            }
        }
        boolean switchedFromMini = (oldTarget != null && oldTarget != mPlayerView) || sNm7Mini || sNm7RestorePending;
        if (switchedFromMini && !mNm7TargetRestoreWaiting) {
            beginNm7TargetRestore();
        }
        if (mNm7TargetRestoreWaiting) {
            armNm7FirstFrameResume();
        }
        if (oldTarget != null && oldTarget != mPlayerView) {
            com.google.android.exoplayer2.ui.PlayerView.switchTargetView(mPlayer, oldTarget, mPlayerView);
        } else if (mPlayerView.getPlayer() != mPlayer) {
            mPlayerView.setPlayer(mPlayer);
        }
        mNm7VideoTarget = mPlayerView;
        sNm7RestorePending = false;
        sNm7Mini = false;
        mNm7LeavingForMini = false;
        if (sNm7ResumeAfterIptv) {
            sNm7ResumeAfterIptv = false;
            if (mNm7TargetRestoreWaiting) mNm7ResumeAfterTarget = true;
            else mPlayer.setPlayWhenReady(true);
        } else if (mPlayer.getPlaybackState() == Player.STATE_ENDED && !mNm7Stopped) {
            recoverNm7EndedPlayback(this);
            mPlayer.setPlayWhenReady(true);
        }
        blockEngine(false);
        try {
            Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer").getMethod("remove").invoke(null);
        } catch (ReflectiveOperationException error) {
            android.util.Log.e("NM7Playback", "Remove mini after foreground handoff", error);
        }
        if (mPanel != null && !isLandscape()) {
            mPanel.setVisibility(View.VISIBLE);
        }
        if (switchedFromMini && mPlayer != null && !mNm7Stopped) {
            final com.google.android.exoplayer2.SimpleExoPlayer playerRef = mPlayer;
            mPlayerView.post(() -> {
                if (mPlayer == playerRef && !mNm7Stopped && !sNm7Mini && !isFinishing()) {
                    if (mPlayerView.getPlayer() != playerRef) {
                        mPlayerView.setPlayer(playerRef);
                    }
                    cancelNm7TargetRestore(true);
                }
            });
        }
        android.util.Log.i("NM7Playback", "target=fullscreen; mini=false; back enabled");
    }

    private void beginNm7TargetRestore() {
        if (mPlayer == null || mNm7TargetRestoreWaiting) return;
        mNm7TargetRestoreWaiting = true;
        mNm7ResumeAfterTarget = mPlayer.getPlayWhenReady() || sNm7ResumeAfterIptv;
        mNm7TargetRestoreStarted = android.os.SystemClock.elapsedRealtime();
        if (sNm7ResumeAfterIptv && mNm7ResumeAfterTarget) mPlayer.setPlayWhenReady(false);
    }

    private void armNm7FirstFrameResume() {
        if (mPlayer == null || !mNm7TargetRestoreWaiting || mNm7FirstFrameListener != null) return;
        mNm7FirstFrameListener = new com.google.android.exoplayer2.video.VideoListener() {
            @Override public void onRenderedFirstFrame() {
                if (mNm7VideoTarget != mPlayerView || sNm7Mini || mNm7Stopped) return;
                android.util.Log.i("NM7Playback", "restore_first_frame_ms="
                        + (android.os.SystemClock.elapsedRealtime() - mNm7TargetRestoreStarted));
                cancelNm7TargetRestore(true);
            }
        };
        mPlayer.addVideoListener(mNm7FirstFrameListener);
        mNm7TargetRestoreTimeout = () -> {
            android.util.Log.w("NM7Playback", "restore first-frame timeout; releasing transition gate");
            cancelNm7TargetRestore(!mNm7Stopped && !sNm7Mini && !isFinishing());
        };
        // Fast fallback: 200ms instead of 750ms to ensure zero perceived delay
        mHandler.postDelayed(mNm7TargetRestoreTimeout, 200);
    }

    private void cancelNm7TargetRestore(boolean resume) {
        if (mNm7TargetRestoreTimeout != null) mHandler.removeCallbacks(mNm7TargetRestoreTimeout);
        if (mNm7FirstFrameListener != null && mPlayer != null) mPlayer.removeVideoListener(mNm7FirstFrameListener);
        boolean play = mNm7TargetRestoreWaiting && mNm7ResumeAfterTarget;
        mNm7TargetRestoreTimeout = null;
        mNm7FirstFrameListener = null;
        mNm7TargetRestoreWaiting = false;
        mNm7ResumeAfterTarget = false;
        if (resume && play && mPlayer != null && !mNm7Stopped) mPlayer.setPlayWhenReady(true);
    }

    public static boolean consumeNm7BrowseBack() {
        if (!isNm7MiniPlayerActive()) return false;
        sNm7RestorePending = false;
        try {
            Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer").getMethod("remove").invoke(null);
        } catch (ReflectiveOperationException error) {
            android.util.Log.e("NM7Playback", "Remove mini from Browse Back", error);
        }
        stopForNm7Iptv();
        return true;
    }

    public static void enterNm7MiniPlayer() {
        PlaybackActivity active = sNm7Active;
        if (active == null || active.isDestroyed() || active.isFinishing() || active.mPlayer == null) return;
        sNm7SuspendedForIptv = false;
        active.mNm7LeavingForMini = true;
        active.cancelNm7TargetRestore(true);
        if (active.mNm7StartupPoster != null) active.mNm7StartupPoster.setVisibility(android.view.View.GONE);
        active.mIsBackPressed = true;
        sNm7RestorePending = false;
        sNm7Mini = true;
        active.syncNm7KeepAlive();
        active.blockEngine(false);
    }

    public static void suspendForNm7Iptv() {
        PlaybackActivity active = sNm7Active;
        if (active == null || active.isDestroyed() || active.isFinishing() || active.mPlayer == null) return;
        if (sNm7SuspendedForIptv) return;
        sNm7SuspendedForIptv = true;
        sNm7ResumeAfterIptv = active.mPlayer.getPlayWhenReady() || active.mNm7ResumeAfterTarget;
        active.cancelNm7TargetRestore(false);
        active.mNm7Stopped = false;
        active.mNm7OwnsPlayback = false;
        active.blockEngine(true);
        active.mPlayer.setPlayWhenReady(false);
        active.nm7SetBackground(false);
        active.stopService(new Intent().setClassName(active,
                "vn.phuong.iptvplayer.BackgroundPlaybackService"));
    }

    public static void stopForNm7Iptv() {
        PlaybackActivity active = sNm7Active;
        sNm7Mini = false;
        sNm7RestorePending = false;
        sNm7ResumeAfterIptv = false;
        sNm7SuspendedForIptv = false;
        if (active == null || active.isDestroyed()) return;
        active.mNm7Stopped = true;
        active.detachNm7PlaybackObserver();
        active.cancelNm7TargetRestore(false);
        active.blockEngine(false);
        active.nm7SetBackground(false);
        if (active.mPlayer != null && active.mNm7PlaybackObserver != null) {
            try { active.mPlayer.removeListener(active.mNm7PlaybackObserver); } catch (RuntimeException ignored) { }
        }
        if (active.mPlayer != null && active.mNm7FrameObserver != null) {
            try { active.mPlayer.removeVideoListener(active.mNm7FrameObserver); } catch (RuntimeException ignored) { }
        }
        active.mNm7ObservedPlayer = null;
        active.mNm7PlaybackObserver = null;
        active.mNm7FrameObserver = null;
        active.stopService(new Intent().setClassName(active, "vn.phuong.iptvplayer.BackgroundPlaybackService"));
        active.finishReally();
        sNm7Active = null;
    }

    private void nm7SetBackground(boolean enabled) {
        mNm7AppBackground = !mNm7Stopped && !sNm7SuspendedForIptv && (enabled || !nm7ScreenInteractive());
        System.setProperty("nm7.youtube.background", mNm7AppBackground ? "1" : "0");
        try {
            Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("setYoutubeBackground", android.content.Context.class, boolean.class)
                    .invoke(null, this, mNm7AppBackground);
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("NM7Playback", "Background session bridge failed", error);
        }
        if (mNm7VisibilityTask != null) mHandler.removeCallbacks(mNm7VisibilityTask);
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
        }
        syncNm7KeepAlive();
    }

    // API 33 types isolated from class loading on Android 6-12.
    private static final class Nm7BackApi {
        static void register(PlaybackActivity activity) {
            android.window.OnBackInvokedCallback callback = activity::onBackPressed;
            activity.mNm7BackCallback = callback;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        }
        static void unregister(PlaybackActivity activity) {
            if (activity.mNm7BackCallback != null) {
                activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                        (android.window.OnBackInvokedCallback) activity.mNm7BackCallback);
                activity.mNm7BackCallback = null;
            }
        }
    }


    private static final long OVERLAY_AUTO_HIDE_MS = 3_000;
    private static final long PROGRESS_UPDATE_MS = 500;
    private static final int SEEK_BAR_MAX = 1_000;
    private static final float SPEED_BOOST = 2f;
    /** A full-width horizontal swipe scrubs this share of the video... */
    private static final float SEEK_SWIPE_FRACTION = 0.5f;
    /** ...but never more than this, so long videos stay controllable. */
    private static final long MAX_SEEK_SWIPE_MS = 10 * 60 * 1_000L;
    private static final long INDICATOR_HIDE_DELAY_MS = 900;
    /** {@code CaptionTrack.TYPE_ASR} — autogenerated captions. The constant itself is private. */
    private static final String SUBTITLE_TYPE_ASR = "asr";
    /** Details sheet states. */
    private static final int DETAILS_NONE = 0;
    private static final int DETAILS_DESCRIPTION = 1;
    private static final int DETAILS_COMMENTS = 2;

    // Button ids of common's vocabulary. The two lb_control_* ids live in leanback-1.0.0 and
    // reach common's R transitively (see report result13.md, correction C1).
    private static final int BUTTON_QUALITY = com.liskovsoft.smartyoutubetv2.common.R.id.lb_control_high_quality;
    private static final int BUTTON_CAPTIONS = com.liskovsoft.smartyoutubetv2.common.R.id.lb_control_closed_captioning;
    private static final int BUTTON_ROTATE = com.liskovsoft.smartyoutubetv2.common.R.id.action_rotate;
    private static final int BUTTON_PIP = com.liskovsoft.smartyoutubetv2.common.R.id.action_pip;
    private static final int BUTTON_LIKE = com.liskovsoft.smartyoutubetv2.common.R.id.action_thumbs_up;
    private static final int BUTTON_DISLIKE = com.liskovsoft.smartyoutubetv2.common.R.id.action_thumbs_down;
    private static final int BUTTON_SHARE = com.liskovsoft.smartyoutubetv2.common.R.id.action_share;
    private static final int BUTTON_SPEED = com.liskovsoft.smartyoutubetv2.common.R.id.action_video_speed;
    private static final int BUTTON_SUBSCRIBE = com.liskovsoft.smartyoutubetv2.common.R.id.action_subscribe;
    private static final int BUTTON_CHANNEL = com.liskovsoft.smartyoutubetv2.common.R.id.action_channel;
    private static final int BUTTON_MUTE = com.liskovsoft.smartyoutubetv2.common.R.id.action_sound_off;
    private static final int BUTTON_PLAYLIST_ADD = com.liskovsoft.smartyoutubetv2.common.R.id.action_playlist_add;

    private PlaybackPresenter mPlaybackPresenter;
    private ExoPlayerInitializer mPlayerInitializer;
    private ExoPlayerController mExoPlayerController;
    private SimpleExoPlayer mPlayer;
    private SubtitleManager mSubtitleManager;
    /** Kept for the subtitle track metadata the player's own format list doesn't carry. */
    private MediaItemFormatInfo mFormatInfo;
    private MediaSessionCompat mMediaSession;
    private MediaSessionConnector mMediaSessionConnector;
    private PlaybackGestureHandler mGestureHandler;

    // Views
    private ConstraintLayout mRoot;
    private FrameLayout mPlayerContainer;
    private DoubleTapPlayerViewImpl mPlayerView;
    private SubtitleView mSubtitleView;
    private YouTubeOverlay mYouTubeOverlay;
    private ImageView mBackgroundView;
    private ImageView mNm7StartupPoster;
    private boolean mNm7PortraitInsetsInstalled;
    private int mNm7BaseRootPaddingLeft;
    private int mNm7BaseRootPaddingTop;
    private int mNm7BaseRootPaddingRight;
    private int mNm7BaseRootPaddingBottom;
    private Runnable mNm7PosterReadyFallback;
    private Runnable mNm7PosterRenderProbe;
    private int mNm7PosterRenderedBaseline;
    private long mNm7PosterProbeStartedAt;
    private String mNm7PosterVideoId;
    private long mNm7FirstFrameSeenAt;
    private long mNm7FirstFramePositionMs;
    private ProgressBar mProgressBar;
    private boolean mNm7FirstFrameRendered;
    /** A hide arrived while the player was buffering; applied once buffering ends. */
    private boolean mProgressHidePending;
    private View mOverlay;
    private View mTopBar;
    private View mCenterBar;
    private View mBottomBar;
    private View mPanel;
    private ImageButton mPlayButton;
    private ImageButton mFullscreenButton;
    private TextView mOverlayTitle;
    private TextView mPositionLabel;
    private TextView mDurationLabel;
    private TextView mEndingTimeLabel;
    private TextView mSeekPreviewLabel;
    private TextView mNextTitleLabel;
    private PlaybackSeekBar mSeekBar;
    private TextView mPanelTitle;
    private TextView mPanelSecondTitle;
    private TextView mChannelName;
    private ImageView mChannelIcon;
    private MaterialButton mQualityChip;
    private RecyclerView mSuggestionsView;
    private LinearLayout mIndicator;
    private ImageView mIndicatorIcon;
    private TextView mIndicatorText;

    // Description / comments: two collapsed rows in the panel that expand into one shared sheet
    private View mDescriptionCard;
    private View mDescriptionDivider;
    private TextView mDescriptionPreview;
    private View mCommentsCard;
    private View mCommentsDivider;
    private View mDetailsSheet;
    private TextView mDetailsTitle;
    private ScrollView mDescriptionScroll;
    private TextView mDetailsDescription;
    private RecyclerView mDetailsComments;
    private TextView mDetailsMessage;
    private ProgressBar mDetailsProgress;
    private CommentsAdapter mCommentsAdapter;
    private View mNm7LiveChatPanel;
    private RecyclerView mNm7LiveChatList;
    private TextView mNm7LiveChatStatus;
    private Nm7LiveChatAdapter mNm7LiveChatAdapter;
    private ChatReceiver mNm7ChatReceiver;
    private EditText mNm7LiveChatInput;
    private TextView mNm7LiveChatSend;
    private io.reactivex.disposables.Disposable mNm7LiveChatSendAction;

    private Nm7FeedAdapter mSuggestionsAdapter;
    /** Mirrors the adapter's row order so getSuggestionsIndex/getSuggestionsByIndex can answer. */
    private final List<VideoGroup> mSuggestionGroups = new ArrayList<>();

    /** What the details sheet is showing, if anything. */
    private int mDetailsMode = DETAILS_NONE;
    /** Video the details on screen belong to — a new one wipes them. */
    private String mDetailsVideoId;
    private String mDescription;
    private String mCommentsKey;
    /** Continuation of the comments page currently listed; null when the list is complete. */
    private String mNextCommentsKey;
    private Disposable mCommentsAction;
    /** Top-level comments parked while the replies to one of them are on screen. */
    private List<CommentItem> mStashedComments;
    private String mStashedNextCommentsKey;

    private final Map<Integer, Integer> mButtonStates = new HashMap<>();
    /** buttonId -> views representing it (a button may live both in the overlay and in the chips row). */
    private final Map<Integer, List<View>> mButtonViews = new HashMap<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideOverlay = new Runnable() {
        @Override
        public void run() {
            if (isPlaying() && !mIsScrubbing) {
                showOverlay(false);
            } else {
                scheduleAutoHide(); // still paused: check again later
            }
        }
    };
    private final Runnable mUpdateProgress = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            mHandler.postDelayed(this, PROGRESS_UPDATE_MS);
        }
    };

    private boolean mIsEngineBlocked;
    private boolean mIsScrubbing;
    private boolean mIsBackPressed;
    private boolean mIsOverlayShown;
    private boolean mIsLandscape;
    private float mNm7DragX, mNm7DragY;
    private boolean mNm7DragEligible;
    private boolean mNm7DragConsumed;

    private boolean handleNm7MinimizeGesture(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) mNm7DragConsumed = false;
        if (mNm7DragConsumed) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                mNm7DragConsumed = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mNm7DragX = event.getRawX();
            mNm7DragY = event.getRawY();
            int[] location = new int[2];
            mPlayerContainer.getLocationOnScreen(location);
            // Portrait only; do not steal fullscreen brightness/volume or seek controls.
            mNm7DragEligible = !mIsLandscape && event.getPointerCount() == 1
                    && mNm7DragX >= location[0]
                    && mNm7DragX <= location[0] + mPlayerContainer.getWidth()
                    && mNm7DragY > location[1] + mPlayerContainer.getHeight() * .25f
                    && mNm7DragY < location[1] + mPlayerContainer.getHeight() * .75f;
        } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            mNm7DragEligible = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && mNm7DragEligible) {
            float dx = Math.abs(event.getRawX() - mNm7DragX);
            float dy = event.getRawY() - mNm7DragY;
            if (dy > 64 * getResources().getDisplayMetrics().density && dy > dx * 1.5f) {
                mNm7DragEligible = false;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                if (mGestureHandler != null) mGestureHandler.onTouchEvent(cancel);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                mNm7DragConsumed = true;
                onBackPressed();
                return true;
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mNm7DragEligible = false;
        }
        return false;
    }
    private int mZoomPercents = 100;
    private boolean mFlipEnabled;
    private int mRotationAngle;
    private float mSavedSpeed = 1f;
    private boolean mSpeedBoosted;
    private float mDragVolume = -1;
    /** Position the current horizontal scrub started from; -1 when no scrub is in progress. */
    private long mSeekAnchorMs = -1;
    private long mSeekTargetMs;
    private final Runnable mHideIndicator = this::hideIndicator;

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sNm7Active = this;
        overridePendingTransition(0, 0);
        if (VERSION.SDK_INT >= 33) Nm7BackApi.register(this);

        setContentView(R.layout.playback_activity);

        initViews();

        mPlayerInitializer = new ExoPlayerInitializer(this);
        mPlaybackPresenter = PlaybackPresenter.instance(this);
        mPlaybackPresenter.setView(this);
        mExoPlayerController = new ExoPlayerController(this, mPlaybackPresenter);
        mExoPlayerController.setPlayerView(this);

        mGestureHandler = new PlaybackGestureHandler(this, mPlayerView, mYouTubeOverlay, this);
        mGestureHandler.setSeekSeconds(getPlayerData().getSeekIncrementMs() / 1_000);

        applyOrientation();

        mPlaybackPresenter.onViewInitialized(); // init all controllers
        // Build the decoder/player before the Activity is shown so the selected video only waits for its stream.
        initializePlayer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        sNm7Active = this;
        if (mPlayer == null) initializePlayer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        nm7InstallPortraitInsets();
        mIsBackPressed = false;
        mNm7Backgrounding = false;
        mNm7Backgrounding = false;
        nm7SetBackground(false);
        if (mPlayer == null) initializePlayer();
        blockEngine(false);
        // Every foreground entry owns the full target, including opening another video
        // while a mini session exists (not only a tap on the mini overlay).
        mNm7LeavingForMini = false;
        completeNm7RestoreOnResume();
        mPlaybackPresenter.onViewResumed();
        if (sNm7ResumeAfterIptv && mNm7TargetRestoreWaiting && mPlayer != null) mPlayer.setPlayWhenReady(false);
        showHideWidgets(true);
        nm7RestorePortraitBars();
    }

    @Override
    protected void onPause() {
        cancelNm7TargetRestore(true);
        // When Back enters NM7 mini, playback remains foreground in BrowseActivity.
        // Do not block the decoder/renderers or notify presenter of a full playback pause.
        if (sNm7Mini && !mNm7Stopped && !isFinishing() && mPlayer != null) {
            blockEngine(false);
            nm7SetBackground(false);
        } else if (!mNm7Stopped && !isFinishing() && mPlayer != null) {
            blockEngine(true);
            if (mPlayer.getPlayWhenReady()) nm7SetBackground(true);
        }
        super.onPause();
        mNm7Backgrounding = !isFinishing() && !mNm7Stopped;
        if (!sNm7Mini && !mNm7Backgrounding) mPlaybackPresenter.onViewPaused();
        if (isInPIPMode()) showHideWidgets(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mNm7Stopped || isFinishing()) maybeReleasePlayer();
    }

    @Override
    protected void onDestroy() {
        detachNm7PlaybackObserver();
        android.util.Log.i("NM7Playback", "destroy mini=" + sNm7Mini + " finishing=" + isFinishing()
                + " changingConfig=" + isChangingConfigurations() + " explicit=" + mNm7Stopped);
        RxHelper.disposeActions(mNm7CommentPreviewAction);
        if (mNm7RecoveryTimeout != null) mHandler.removeCallbacks(mNm7RecoveryTimeout);
        cancelNm7TargetRestore(false);
        if (VERSION.SDK_INT >= 33) Nm7BackApi.unregister(this);
        if (sNm7Active == this) {
            sNm7Active = null;
            sNm7Mini = false;
            nm7SetBackground(false);
            stopService(new Intent().setClassName(this, "vn.phuong.iptvplayer.BackgroundPlaybackService"));
        }
        super.onDestroy();

        mHandler.removeCallbacksAndMessages(null);

        RxHelper.disposeActions(mCommentsAction);

        // Fix situations when the engine wasn't properly destroyed (e.g. after closing dialogs)
        releasePlayer();

        if (mGestureHandler != null) {
            mGestureHandler.release();
        }

        if (mPlaybackPresenter.getView() == this) {
            mPlaybackPresenter.onViewDestroyed();
        }
    }

    @Override
    public void onBackPressed() {
        if (onDetailsBack()) return;
        if (mPlayer == null || mNm7Stopped) { super.onBackPressed(); return; }
        if (mNm7LeavingForMini) {
            consumeNm7BrowseBack();
            return;
        }
        mNm7LeavingForMini = true;
        cancelNm7TargetRestore(true);
        if (mNm7StartupPoster != null) {
            mNm7StartupPoster.setVisibility(View.GONE);
        }
        mIsBackPressed = true;
        sNm7RestorePending = false;
        sNm7Mini = true;
        syncNm7KeepAlive();
        blockEngine(false);
        if (mPanel != null && !isLandscape()) {
            mPanel.setVisibility(View.VISIBLE);
        }
        Intent intent = new Intent(this,
                com.liskovsoft.smartyoutubetv2.droid.ui.browse.BrowseActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        startActivity(intent);
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (handleNm7MinimizeGesture(event)) return true;
        // Observe only: the event keeps travelling to the controls, so buttons keep working
        if (mGestureHandler != null) {
            mGestureHandler.onTouchEvent(event);
        }

        return super.dispatchTouchEvent(event);
    }

    // ------------------------------------------------------------ view setup

    private void initViews() {
        mRoot = findViewById(R.id.playback_root);
        mPlayerContainer = findViewById(R.id.playback_player_container);
        mPlayerView = findViewById(R.id.playback_player_view);
        mSubtitleView = findViewById(R.id.playback_subtitles);
        mYouTubeOverlay = findViewById(R.id.playback_youtube_overlay);
        mBackgroundView = findViewById(R.id.playback_background);
        mNm7StartupPoster = findViewById(R.id.nm7_startup_poster);
        mProgressBar = findViewById(R.id.playback_progress);
        mIndicator = findViewById(R.id.playback_indicator);
        mIndicatorIcon = findViewById(R.id.playback_indicator_icon);
        mIndicatorText = findViewById(R.id.playback_indicator_text);

        mOverlay = findViewById(R.id.playback_overlay_root);
        mTopBar = findViewById(R.id.playback_top_bar);
        mCenterBar = findViewById(R.id.playback_center_bar);
        mBottomBar = findViewById(R.id.playback_bottom_bar);
        mOverlayTitle = findViewById(R.id.playback_overlay_title);
        mPlayButton = findViewById(R.id.playback_btn_play);
        mFullscreenButton = findViewById(R.id.playback_btn_fullscreen);
        mPositionLabel = findViewById(R.id.playback_position);
        mDurationLabel = findViewById(R.id.playback_duration);
        mEndingTimeLabel = findViewById(R.id.playback_ending_time);
        mSeekPreviewLabel = findViewById(R.id.playback_seek_preview);
        mNextTitleLabel = findViewById(R.id.playback_next_title);
        mSeekBar = findViewById(R.id.playback_seekbar);

        mPanel = findViewById(R.id.playback_panel);
        mPanelTitle = findViewById(R.id.playback_title);
        mPanelSecondTitle = findViewById(R.id.playback_second_title);
        mChannelName = findViewById(R.id.playback_channel_name);
        mChannelIcon = findViewById(R.id.playback_channel_icon);
        mQualityChip = findViewById(R.id.playback_chip_quality);
        mSuggestionsView = findViewById(R.id.playback_suggestions);

        mDescriptionCard = findViewById(R.id.playback_description_card);
        mDescriptionDivider = findViewById(R.id.playback_description_divider);
        mDescriptionPreview = findViewById(R.id.playback_description_preview);
        mCommentsCard = findViewById(R.id.playback_comments_card);

        // NM7 1.10.83: subtle phone-style player entrance; cosmetic only.
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

        mNm7LiveChatPanel = findViewById(R.id.nm7_live_chat_panel);
        mNm7LiveChatList = findViewById(R.id.nm7_live_chat_list);
        mNm7LiveChatStatus = findViewById(R.id.nm7_live_chat_status);
        mNm7LiveChatInput = findViewById(R.id.nm7_live_chat_input);
        mNm7LiveChatSend = findViewById(R.id.nm7_live_chat_send);
        View.OnClickListener nm7SendChat = v -> nm7SendLiveChatMessage();
        mNm7LiveChatSend.setOnClickListener(nm7SendChat);
        mNm7LiveChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                nm7SendLiveChatMessage();
                return true;
            }
            return false;
        });
        TextView nm7LiveChatClose = findViewById(R.id.nm7_live_chat_close);
        nm7LiveChatClose.setOnClickListener(v -> {
            mNm7LiveChatPanel.setVisibility(View.GONE);
            if (mPanel != null) mPanel.setVisibility(View.VISIBLE);
            updateDetailsVisibility();
        });
        mNm7LiveChatAdapter = new Nm7LiveChatAdapter(this);
        mNm7LiveChatList.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mNm7LiveChatList.setAdapter(mNm7LiveChatAdapter);
        mCommentsDivider = findViewById(R.id.playback_comments_divider);
        mDetailsSheet = findViewById(R.id.playback_details_sheet);
        mDetailsTitle = findViewById(R.id.playback_details_title);
        mDescriptionScroll = findViewById(R.id.playback_details_description_scroll);
        mDetailsDescription = findViewById(R.id.playback_details_description);
        mDetailsComments = findViewById(R.id.playback_details_comments);
        mDetailsMessage = findViewById(R.id.playback_details_message);
        mDetailsProgress = findViewById(R.id.playback_details_progress);

        // The player view hosts a subtitle view of its own: hide it, we render subs
        // through common's SubtitleManager (styling, position, autogenerated subs fix)
        if (mPlayerView.getSubtitleView() != null) {
            mPlayerView.getSubtitleView().setVisibility(View.GONE);
        }
        // Double tapping is driven by PlaybackGestureHandler (it also owns drag + long press)
        mPlayerView.setDoubleTapEnabled(false);
        mPlayerView.setUseController(false);

        mSuggestionsAdapter = new Nm7FeedAdapter(this);
        mSuggestionsView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mSuggestionsView.setAdapter(mSuggestionsAdapter.adapter);

        mEndingTimeLabel.setVisibility(getPlayerData().isGlobalEndingTimeEnabled() ? View.VISIBLE : View.GONE);

        initDetails();
        initControlListeners();
        initButtons();
        mPanelTitle.setOnClickListener(v -> openDescription());
        findViewById(R.id.nm7_details_more).setOnClickListener(v -> openDescription());
        mChannelIcon.setOnClickListener(v -> dispatchButton(BUTTON_CHANNEL));
        initWindowInsets();
        installNm7WatchFeed();
    }

    /**
     * The app draws edge to edge (Helpers.makeActivityFullscreen2), and since the notch fix in
     * SharedModules it draws into the display cutout too. Without this the top row of player
     * buttons ends up under the status bar / notch in portrait and can't be tapped.
     *
     * Padding is added on top of what the layout already declares, so the bars keep their look.
     * The overlay only needs the bottom inset in landscape — in portrait it sits inside the 16:9
     * video box at the top of the screen. Down there it's the suggestions list that has to clear
     * the navigation bar instead.
     */
    private void updateNm7RatingCounts() {
        Video video = getVideo();
        if (mNm7CommentsHeading != null) mNm7CommentsHeading.setText("Bình luận" +
                (video == null || TextUtils.isEmpty(video.commentsCount) ? "" : " " + video.commentsCount));
        int[] ids = {BUTTON_LIKE, BUTTON_DISLIKE};
        String[] counts = {video == null ? null : video.likeCount, video == null ? null : video.dislikeCount};
        for (int i=0;i<ids.length;i++) {
            List<View> buttons = mButtonViews.get(ids[i]); if (buttons == null) continue;
            for (View view : buttons) if (view instanceof MaterialButton) {
                MaterialButton button=(MaterialButton)view; button.setText(counts[i]);
                android.view.ViewGroup.LayoutParams params=button.getLayoutParams();
                params.width=TextUtils.isEmpty(counts[i]) ? Math.round(48*getResources().getDisplayMetrics().density) : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                button.setLayoutParams(params);
            }
        }
    }

    private void installNm7WatchFeed() {
        View metadata = findViewById(R.id.nm7_watch_metadata);
        mNm7CommentPreview = findViewById(R.id.nm7_comment_preview);
        mNm7CommentsHeading = findViewById(R.id.nm7_comments_heading);
        mNm7CommentAvatar = findViewById(R.id.nm7_comment_avatar);
        ((android.view.ViewGroup) metadata.getParent()).removeView(metadata);
        com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7WatchHeaderAdapter header =
                new com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7WatchHeaderAdapter(metadata);
        mSuggestionsView.setAdapter(new androidx.recyclerview.widget.ConcatAdapter(header, mSuggestionsAdapter.adapter));
        mSuggestionsView.setItemAnimator(null); mSuggestionsView.setItemViewCacheSize(10);
        mSuggestionsView.getRecycledViewPool().setMaxRecycledViews(1, 12);
    }

    private TextView mNm7CommentPreview;
    private TextView mNm7CommentsHeading;
    private Disposable mNm7CommentPreviewAction;
    private CommentGroup mNm7PreviewGroup;
    private String mNm7PreviewKey;

    private void loadNm7CommentPreview() {
        if (!mNm7StartupFrame) return; // The selected video's first frame has network priority.
        if (mPlayer == null || mPlayer.getPlaybackState() != Player.STATE_READY
                || mNm7Stopped || sNm7SuspendedForIptv || mCommentsKey == null
                || TextUtils.equals(mNm7PreviewKey, mCommentsKey)) return;
        final String key = mCommentsKey;
        final String videoId = getVideo() == null ? null : getVideo().videoId;
        if (videoId == null) return;
        RxHelper.disposeActions(mNm7CommentPreviewAction);
        mNm7PreviewKey = key;
        mNm7CommentPreviewAction = YouTubeServiceManager.instance().getCommentsService()
                .getCommentsObserve(key).subscribe(group -> {
                    if (isDestroyed() || isFinishing() || !TextUtils.equals(key, mCommentsKey)
                            || getVideo() == null || !TextUtils.equals(videoId, getVideo().videoId)) return;
                    mNm7PreviewGroup = group;
                    showNm7CommentPreview(group);
                }, error -> {
                    if (TextUtils.equals(key, mCommentsKey) && mNm7CommentPreview != null)
                        mNm7CommentPreview.setText("Chạm để tải bình luận");
                });
    }

    private void showNm7CommentPreview(CommentGroup group) {
        if (group == null || group.getComments() == null || mNm7CommentPreview == null) return;
        for (CommentItem item : group.getComments()) {
            if (item == null || item.isEmpty()) continue;
            String author = item.getAuthorName();
            mNm7CommentPreview.setText((TextUtils.isEmpty(author) ? "" : author + "  ") + item.getMessage());
            Glide.with(this).load(item.getAuthorPhoto()).apply(RequestOptions.circleCropTransform())
                    .override(84, 84).into(mNm7CommentAvatar);
            return;
        }
        mNm7CommentPreview.setText("Chưa có bình luận");
    }
    private android.widget.ImageView mNm7CommentAvatar;

    private void initWindowInsets() {
        final int topBarPaddingTop = mTopBar.getPaddingTop();
        final int topBarPaddingLeft = mTopBar.getPaddingLeft();
        final int topBarPaddingRight = mTopBar.getPaddingRight();
        final int bottomBarPaddingLeft = mBottomBar.getPaddingLeft();
        final int bottomBarPaddingRight = mBottomBar.getPaddingRight();
        final int bottomBarPaddingBottom = mBottomBar.getPaddingBottom();
        final int suggestionsPaddingBottom = mSuggestionsView.getPaddingBottom();
        final int commentsPaddingBottom = mDetailsComments.getPaddingBottom();
        final int descriptionPaddingBottom = mDetailsDescription.getPaddingBottom();
        final int chatPaddingBottom = mNm7LiveChatList.getPaddingBottom();

        mRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int left = insets.getSystemWindowInsetLeft();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();

            // System bars are hidden in fullscreen mode, so the cutout is the only inset left
            if (VERSION.SDK_INT >= 28) {
                DisplayCutout cutout = insets.getDisplayCutout();

                if (cutout != null) {
                    top = Math.max(top, cutout.getSafeInsetTop());
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
            }

            boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

            // The video surface must start below the status bar in portrait.
            // Margin is reset to zero in landscape/fullscreen.
            ViewGroup.MarginLayoutParams nm7PlayerLp =
                    (ViewGroup.MarginLayoutParams) mPlayerContainer.getLayoutParams();
            int nm7TopMargin = isLandscape ? 0 : top;
            if (nm7PlayerLp.topMargin != nm7TopMargin) {
                nm7PlayerLp.topMargin = nm7TopMargin;
                mPlayerContainer.setLayoutParams(nm7PlayerLp);
            }

            mTopBar.setPadding(topBarPaddingLeft + left, topBarPaddingTop + top,
                    topBarPaddingRight + right, mTopBar.getPaddingBottom());
            mBottomBar.setPadding(bottomBarPaddingLeft + left, mBottomBar.getPaddingTop(),
                    bottomBarPaddingRight + right, bottomBarPaddingBottom + (isLandscape ? bottom : 0));

            // In portrait the navigation bar stays on screen (see applySystemUi) and the panel
            // reaches the bottom edge, so the suggestions have to clear it. clipToPadding is off,
            // so the rows still scroll underneath.
            mSuggestionsView.setPadding(mSuggestionsView.getPaddingLeft(), mSuggestionsView.getPaddingTop(),
                    mSuggestionsView.getPaddingRight(), suggestionsPaddingBottom + (isLandscape ? 0 : bottom));

            // The details sheet covers the same box as the panel, so its two scrollers
            // have to clear the navigation bar the same way
            mDetailsComments.setPadding(mDetailsComments.getPaddingLeft(), mDetailsComments.getPaddingTop(),
                    mDetailsComments.getPaddingRight(), commentsPaddingBottom + (isLandscape ? 0 : bottom));
            mDetailsDescription.setPadding(mDetailsDescription.getPaddingLeft(), mDetailsDescription.getPaddingTop(),
                    mDetailsDescription.getPaddingRight(), descriptionPaddingBottom + (isLandscape ? 0 : bottom));

            mNm7LiveChatList.setPadding(mNm7LiveChatList.getPaddingLeft(), mNm7LiveChatList.getPaddingTop(),
                    mNm7LiveChatList.getPaddingRight(), chatPaddingBottom + (isLandscape ? 0 : bottom));

            return insets;
        });

        mRoot.requestApplyInsets();
    }

    // ------------------------------------------------ description / comments

    private void initDetails() {
        mCommentsAdapter = new CommentsAdapter(new CommentsAdapter.Listener() {
            @Override
            public void onCommentClicked(CommentItem item) {
                openReplies(item);
            }

            @Override
            public void onLoadMore() {
                loadComments(mNextCommentsKey, false);
            }
        });

        mDetailsComments.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mDetailsComments.setAdapter(mCommentsAdapter);

        mDescriptionCard.setOnClickListener(v -> openDescription());
        mCommentsCard.setOnClickListener(v -> openComments());
        findViewById(R.id.playback_details_close).setOnClickListener(v -> onDetailsBack());
    }

    private void openDescription() {
        if (TextUtils.isEmpty(mDescription)) {
            return;
        }

        RxHelper.disposeActions(mCommentsAction);

        mDetailsMode = DETAILS_DESCRIPTION;
        mDetailsTitle.setText(R.string.playback_description);
        mDetailsDescription.setText(mDescription);
        mDescriptionScroll.setVisibility(View.VISIBLE);
        mDescriptionScroll.post(() -> mDescriptionScroll.scrollTo(0, 0));
        mDetailsComments.setVisibility(View.GONE);
        mDetailsMessage.setVisibility(View.GONE);
        mDetailsProgress.setVisibility(View.GONE);

        updateDetailsVisibility();
    }

    private void openComments() {
        if (mCommentsKey == null) {
            return;
        }

        mDetailsMode = DETAILS_COMMENTS;
        mStashedComments = null;
        mStashedNextCommentsKey = null;

        mDetailsTitle.setText(R.string.playback_comments);
        mDescriptionScroll.setVisibility(View.GONE);
        mDetailsComments.setVisibility(View.VISIBLE);

        updateDetailsVisibility();

        if (mNm7PreviewGroup != null && TextUtils.equals(mCommentsKey, mNm7PreviewKey)) {
            mCommentsAdapter.clear();
            mDetailsMessage.setVisibility(View.GONE);
            onCommentGroup(mNm7PreviewGroup);
        } else loadComments(mCommentsKey, true);
    }

    /**
     * One level of replies is as deep as the phone panel goes — enough for the usual
     * "see the thread" case without turning the sheet into a navigation stack.
     */
    private void openReplies(CommentItem item) {
        if (item == null || item.getNestedCommentsKey() == null || mStashedComments != null) {
            return;
        }

        mStashedComments = mCommentsAdapter.getItems();
        mStashedNextCommentsKey = mNextCommentsKey;

        mDetailsTitle.setText(item.getAuthorName());

        loadComments(item.getNestedCommentsKey(), true);
    }

    /**
     * @param reset true starts a fresh list, false appends the next page
     */
    private void loadComments(String key, boolean reset) {
        if (key == null) {
            return;
        }

        if (reset) {
            RxHelper.disposeActions(mCommentsAction);

            mCommentsAdapter.clear();
            mNextCommentsKey = null;
            mDetailsMessage.setVisibility(View.GONE);
            mDetailsProgress.setVisibility(View.VISIBLE);
        } else if (isLoadingComments()) {
            // The footer is bound repeatedly while it's on screen
            return;
        }

        mCommentsAction = YouTubeServiceManager.instance().getCommentsService()
                .getCommentsObserve(key)
                .subscribe(this::onCommentGroup, this::onCommentsError);
    }

    private boolean isLoadingComments() {
        return mCommentsAction != null && !mCommentsAction.isDisposed();
    }

    private void onCommentGroup(CommentGroup group) {
        if (mDetailsMode != DETAILS_COMMENTS) {
            return;
        }

        mDetailsProgress.setVisibility(View.GONE);

        if (mStashedComments == null && mCommentsAdapter.isEmpty()) showNm7CommentPreview(group);
        mNextCommentsKey = group != null ? group.getNextCommentsKey() : null;
        mCommentsAdapter.append(group != null ? group.getComments() : null, mNextCommentsKey != null);

        if (mCommentsAdapter.isEmpty()) {
            showDetailsMessage("Chưa có bình luận để hiển thị");
        }
    }

    private void onCommentsError(Throwable error) {
        Log.e(TAG, "Comments load error: %s", error != null ? error.getMessage() : null);

        if (mDetailsMode != DETAILS_COMMENTS) {
            return;
        }

        mDetailsProgress.setVisibility(View.GONE);
        mNextCommentsKey = null;
        mCommentsAdapter.append(null, false); // drops the spinner footer

        if (mCommentsAdapter.isEmpty()) {
            showDetailsMessage("Chưa có bình luận để hiển thị");
        }
    }

    private void showDetailsMessage(String message) {
        mDetailsMessage.setText(message);
        mDetailsMessage.setVisibility(View.VISIBLE);
    }

    /**
     * Close button and system back: leave the replies first, then the sheet itself.
     *
     * @return true when the press was consumed
     */
    private boolean onDetailsBack() {
        if (mDetailsSheet == null || mDetailsSheet.getVisibility() != View.VISIBLE) {
            return false;
        }

        if (mDetailsMode == DETAILS_COMMENTS && mStashedComments != null) {
            RxHelper.disposeActions(mCommentsAction);

            mDetailsProgress.setVisibility(View.GONE);
            mDetailsMessage.setVisibility(View.GONE);
            mDetailsTitle.setText(R.string.playback_comments);

            mNextCommentsKey = mStashedNextCommentsKey;
            mCommentsAdapter.clear();
            mCommentsAdapter.append(mStashedComments, mNextCommentsKey != null);

            mStashedComments = null;
            mStashedNextCommentsKey = null;

            return true;
        }

        closeDetails();

        return true;
    }

    private void closeDetails() {
        RxHelper.disposeActions(mCommentsAction);
        mCommentsAction = null;

        mDetailsMode = DETAILS_NONE;
        mStashedComments = null;
        mStashedNextCommentsKey = null;
        mNextCommentsKey = null;

        if (mCommentsAdapter != null) {
            mCommentsAdapter.clear();
        }

        if (mDetailsMessage != null) {
            mDetailsMessage.setVisibility(View.GONE);
        }

        if (mDetailsProgress != null) {
            mDetailsProgress.setVisibility(View.GONE);
        }

        updateDetailsVisibility();
    }

    /**
     * The sheet lives and dies with the metadata panel: fullscreen and PIP have no room for it.
     * It never reaches above the panel, so the video keeps playing and stays visible.
     */
    private void updateDetailsVisibility() {
        if (mDetailsSheet == null) {
            return;
        }

        boolean show = mDetailsMode != DETAILS_NONE && mPanel != null && mPanel.getVisibility() == View.VISIBLE;

        mDetailsSheet.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** New video on screen: drop the previous one's description and comments. */
    private void resetVideoDetails() {
        closeDetails();

        mDescription = null;
        mCommentsKey = null;
        RxHelper.disposeActions(mNm7CommentPreviewAction);
        mNm7CommentPreviewAction = null;
        mNm7PreviewGroup = null;
        mNm7PreviewKey = null;
        if (mNm7CommentPreview != null) { mNm7CommentPreview.setText("Xem bình luận…"); Glide.with(this).clear(mNm7CommentAvatar); }
        mDetailsVideoId = null;

        if (mDescriptionCard != null) {
            mDescriptionPreview.setText(null);
            mDescriptionCard.setVisibility(View.GONE);
            mDescriptionDivider.setVisibility(View.GONE);
            mCommentsCard.setVisibility(View.GONE);
            mCommentsDivider.setVisibility(View.GONE);
        }
    }

    private void initControlListeners() {
        mPlayButton.setOnClickListener(v -> togglePlayPause());
        findViewById(R.id.playback_btn_prev).setOnClickListener(v -> {
            resetAutoHide();
            mPlaybackPresenter.onPreviousClicked();
        });
        findViewById(R.id.playback_btn_next).setOnClickListener(v -> {
            resetAutoHide();
            mPlaybackPresenter.onNextClicked();
        });
        findViewById(R.id.playback_btn_back).setOnClickListener(v -> onBackPressed());
        mFullscreenButton.setOnClickListener(v -> toggleFullscreen());

        findViewById(R.id.playback_btn_more).setOnClickListener(this::showOverflowMenu);
        findViewById(R.id.playback_chip_more).setOnClickListener(this::showOverflowMenu);

        findViewById(R.id.playback_channel_row).setOnClickListener(v -> dispatchButton(BUTTON_CHANNEL));

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }

                long positionMs = progressToPositionMs(progress);
                mPositionLabel.setText(formatTime(positionMs));
                // Feeds chapter/segment titles (PlayerUIController.updateSeekPreviewTitle)
                mPlaybackPresenter.onSeekPositionChanged(positionMs);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsScrubbing = true;
                cancelAutoHide();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mIsScrubbing = false;
                setPositionMs(progressToPositionMs(seekBar.getProgress()));
                resetAutoHide();
            }
        });
    }

    /**
     * Binds every control that maps onto one of common's {@code R.id.action_*} button ids.
     * Clicks report {@code presenter.onButtonClicked(buttonId, currentState)} and long clicks
     * {@code onButtonLongClicked} — exactly like TV's action buttons.
     */
    private void initButtons() {
        registerButton(BUTTON_QUALITY, findViewById(R.id.playback_chip_quality));
        registerButton(BUTTON_SPEED, findViewById(R.id.playback_btn_speed));
        registerButton(BUTTON_CAPTIONS, findViewById(R.id.playback_btn_captions));
        registerButton(BUTTON_MUTE, findViewById(R.id.playback_btn_mute));
        registerButton(BUTTON_PLAYLIST_ADD, findViewById(R.id.playback_btn_playlist));
        registerButton(BUTTON_PLAYLIST_ADD, findViewById(R.id.playback_chip_playlist));
        registerButton(BUTTON_LIKE, findViewById(R.id.playback_chip_like));
        registerButton(BUTTON_DISLIKE, findViewById(R.id.playback_chip_dislike));
        registerButton(BUTTON_SHARE, findViewById(R.id.playback_chip_share));
        registerButton(BUTTON_SPEED, findViewById(R.id.playback_chip_speed));
        registerButton(BUTTON_SUBSCRIBE, findViewById(R.id.playback_chip_subscribe));

        // The tap goes straight to the autogenerated subtitles instead of common's
        // "restore the last used track" behaviour. Long press is untouched: it still opens
        // the whole track list through PlayerUIController.
        View captionsButton = findViewById(R.id.playback_btn_captions);

        if (captionsButton != null) {
            captionsButton.setOnClickListener(v -> toggleSubtitles());
        }
    }

    /**
     * Tap: switch the autogenerated subtitles on, or off again when they're already showing.
     */
    private void toggleSubtitles() {
        resetAutoHide();

        List<FormatItem> formats = getSubtitleFormats();

        if (formats == null || formats.isEmpty()) {
            return;
        }

        if (getButtonState(BUTTON_CAPTIONS) == BUTTON_ON) {
            applySubtitleFormat(FormatItem.SUBTITLE_NONE);
            return;
        }

        FormatItem format = findAutoSubtitleFormat(formats);

        if (format == null) {
            // No captions of the video's own, or the source carried no track metadata:
            // fall back to the full list, same as a long press
            mPlaybackPresenter.onButtonLongClicked(BUTTON_CAPTIONS, getButtonState(BUTTON_CAPTIONS));
            return;
        }

        applySubtitleFormat(format);
    }

    /**
     * The autogenerated track in the video's own language.
     *
     * The player's own subtitle list can't answer this: YouTube marks autogenerated and auto
     * translated tracks alike with a trailing '*', and the tracks are sorted by name before they
     * reach us, so neither the marker nor the position identifies the original. The format info
     * behind the manifest does — every translation is the origin track's url plus a 'tlang' query.
     */
    private FormatItem findAutoSubtitleFormat(List<FormatItem> formats) {
        MediaSubtitle origin = findOriginSubtitle();

        if (origin == null) {
            return null;
        }

        String name = origin.getName() != null ? origin.getName() : origin.getLanguageCode();
        // Both manifest builders hand this string to ExoPlayer as the track language, and the
        // Format constructor normalizes it (lowercase, among other things) before storing it.
        // Run it through the very same call or the comparison never matches.
        String language = Util.normalizeLanguageCode(name);

        for (FormatItem format : formats) {
            if (format != null && !format.isDefault() && Helpers.equals(language, format.getLanguage())) {
                return format;
            }
        }

        return null;
    }

    /**
     * The autogenerated captions, or the video's own captions when it has none. Translations are
     * listed after the real caption tracks, so the first untranslated one is the origin — which
     * also covers the translation into the origin's own language, the one case where a
     * translation carries no 'tlang' of its own.
     */
    private MediaSubtitle findOriginSubtitle() {
        if (mFormatInfo == null || mFormatInfo.getSubtitles() == null) {
            return null;
        }

        MediaSubtitle firstOrigin = null;

        for (MediaSubtitle sub : mFormatInfo.getSubtitles()) {
            if (sub == null || isTranslatedSubtitle(sub)) {
                continue;
            }

            if (SUBTITLE_TYPE_ASR.equals(sub.getType())) {
                return sub;
            }

            if (firstOrigin == null) {
                firstOrigin = sub;
            }
        }

        return firstOrigin;
    }

    private boolean isTranslatedSubtitle(MediaSubtitle sub) {
        return sub.getBaseUrl() != null && sub.getBaseUrl().contains("tlang=");
    }

    private void applySubtitleFormat(FormatItem format) {
        boolean enabled = !format.isDefault();

        setFormat(format);
        getPlayerData().setFormat(format);

        // Mirrors PlayerUIController.enableSubtitleForChannel(): the per channel memory is what
        // decides whether subtitles come back on the next video of the same channel
        if (getPlayerData().isSubtitlesPerChannelEnabled() && getVideo() != null) {
            if (enabled) {
                getPlayerData().enableSubtitlesPerChannel(getVideo().channelId);
            } else {
                getPlayerData().disableSubtitlesPerChannel(getVideo().channelId);
            }
        }

        setButtonState(BUTTON_CAPTIONS, enabled ? BUTTON_ON : BUTTON_OFF);
    }

    private void registerButton(int buttonId, View view) {
        if (view == null) {
            return;
        }

        List<View> views = mButtonViews.get(buttonId);

        if (views == null) {
            views = new ArrayList<>();
            mButtonViews.put(buttonId, views);
        }

        views.add(view);

        view.setOnClickListener(v -> dispatchButton(buttonId));
        view.setOnLongClickListener(v -> {
            resetAutoHide();
            mPlaybackPresenter.onButtonLongClicked(buttonId, getButtonState(buttonId));
            return true;
        });

        applyButtonStyle(view, getButtonState(buttonId));
    }

    private void dispatchButton(int buttonId) {
        resetAutoHide();
        mPlaybackPresenter.onButtonClicked(buttonId, getButtonState(buttonId));
    }

    /**
     * Actions that have no dedicated control on the phone UI are collected in an overflow menu.
     * Menu item ids ARE common's action ids, so the click handler is a single dispatch call.
     */
    private void showOverflowMenu(View anchor) {
        resetAutoHide();

        PopupMenu menu = new PopupMenu(this, anchor);
        Menu m = menu.getMenu();

        // The top bar slot went to the speed toggle, so quality lives here now
        addMenuItem(m, BUTTON_QUALITY,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_high_quality);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_playback_queue,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_playback_queue);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_repeat,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_repeat_mode);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_content_block,
                com.liskovsoft.smartyoutubetv2.common.R.string.content_block_categories);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_video_zoom,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_video_zoom);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_flip,
                com.liskovsoft.smartyoutubetv2.common.R.string.video_flip);
        // Mute and add-to-playlist have their own buttons in the top bar, so they aren't repeated here
        addMenuItem(m, BUTTON_ROTATE,
                com.liskovsoft.smartyoutubetv2.common.R.string.video_rotate);
        addMenuItem(m, BUTTON_PIP,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_pip);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_seek_interval,
                com.liskovsoft.smartyoutubetv2.common.R.string.seek_interval);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_info,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_video_info);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_video_stats,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_video_stats);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_search,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_search);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_screen_dimming,
                com.liskovsoft.smartyoutubetv2.common.R.string.screen_dimming);

        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                dispatchButton(item.getItemId());
                return true;
            }
        });

        menu.show();
    }

    private void addMenuItem(Menu menu, int buttonId, int titleResId) {
        MenuItem item = menu.add(Menu.NONE, buttonId, Menu.NONE, titleResId);
        item.setCheckable(true);
        item.setChecked(getButtonState(buttonId) == BUTTON_ON);
    }

    // ---------------------------------------------------------- engine setup

    private void initializePlayer() {
        if (mPlayer != null) {
            Log.d(TAG, "Skip player initialization.");
            return;
        }

        // NM7 1.10.73: keep the user's DNS preference but do not force
        // playback onto OkHttp.
        createPlayerObjects();

        mPlaybackPresenter.setView(this); // may have been replaced by the embed player
        mPlaybackPresenter.onEngineInitialized();
    }

    /**
     * NOTE: the order is copied from EmbedPlayerView/PlaybackFragment. Don't reshuffle.
     */
    private void createPlayerObjects() {
        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new AdaptiveTrackSelection.Factory());
        mNm7TrackSelector = trackSelector;
        mNm7SavedVideoRenderers.clear();
        applyNm7RecoveryVideoLimit();
        mNm7TrackSelector = trackSelector;
        mExoPlayerController.setTrackSelector(trackSelector);

        DefaultRenderersFactory renderersFactory = new CustomOverridesRenderersFactory(this);
        mPlayer = mPlayerInitializer.createPlayer(this, renderersFactory, trackSelector);
        applyNm7VideoVisibility();

        mExoPlayerController.setPlayerView(this);
        mExoPlayerController.setPlayer(mPlayer);
        mNm7VideoTarget = mPlayerView;
        bindNm7PlayerTarget();

        // Releases a hide that arrived while the player was still buffering (see showProgressBar)
        final com.google.android.exoplayer2.SimpleExoPlayer nm7CreatedPlayer = mPlayer;
        mPlayer.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (!isNm7CurrentEngine(nm7CreatedPlayer)) return;
                if (sNm7SuspendedForIptv && playWhenReady) {
                    mPlayer.setPlayWhenReady(false);
                    return;
                }
                if (playbackState == Player.STATE_READY && mPlayer.getPlaybackError() == null && !mNm7RenderPolicy.exhausted()) {
                    if (mNm7StartupPoster != null
                            && mNm7StartupPoster.getVisibility() == View.VISIBLE) {
                        nm7ArmPosterReadyFallback(nm7CreatedPlayer);
                    }
                    if (sNm7Mini) {
                        try { bindNm7PlayerTarget(); } catch (RuntimeException error) {
                            android.util.Log.w("NM7Playback", "mini rebind after recovery", error);
                        }
                    }
                    mNm7OomRecoveryActive = false;
                    mNm7LastEngineError = null;
                    loadNm7CommentPreview();
                    mNm7RecoveryPending = false;
                    mNm7ErrorLabel = null;
                    if (mNm7RecoveryRestoreIntent) {
                        mNm7RecoveryRestoreIntent = false;
                        mPlayer.setPlayWhenReady(mNm7RecoveryWantsPlay);
                        playWhenReady = mPlayer.getPlayWhenReady();
                    }
                    if (mNm7RecoveryTimeout != null) mHandler.removeCallbacks(mNm7RecoveryTimeout);
                }
                if (playWhenReady && playbackState == Player.STATE_READY && !mNm7Stopped && !sNm7SuspendedForIptv && !mNm7OwnsPlayback) {
                    try {
                        Class.forName("vn.phuong.iptvplayer.MobileNm7Application")
                                .getMethod("pauseIptvForYoutube").invoke(null);
                        mNm7OwnsPlayback = true;
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        android.util.Log.e("NM7Playback", "IPTV ownership handoff failed", error);
                    }
                }
                // Neither BUFFERING/IDLE nor a temporary recovery pause ends the mini session.
                syncNm7KeepAlive();
                if (mProgressHidePending && !isPlayerBuffering()) {
                    showProgressBar(false);
                }
            }
        });

        createMediaSession();

        mGestureHandler.setPlayer(mPlayer);

        applyVideoTransform();
    }

    private void maybeReleasePlayer() {
        if (isEngineBlocked()) {
            Log.d(TAG, "releasePlayer: engine is blocked (background playback). Exiting...");
            return;
        }

        if (isInPIPMode()) {
            Log.d(TAG, "releasePlayer: running in PIP. Exiting...");
            return;
        }

        releasePlayer();
    }

    private void releasePlayer() {
        nm7CancelPosterReadyFallback();
        nm7CancelPosterRenderProbe();
        detachNm7PlaybackObserver();
        if (mPlayer == null) {
            return;
        }

        Log.d(TAG, "releasePlayer: Start releasing player engine...");

        if (mPlaybackPresenter.getPlayer() == this) mPlaybackPresenter.onEngineReleased();

        releaseMediaSession();

        cancelNm7TargetRestore(false);
        if (mNm7VideoTarget != null) mNm7VideoTarget.setPlayer(null);
        mPlayerInitializer.release();
        mExoPlayerController.release();
        mPlayer = null;
        mSubtitleManager = null;
        mFormatInfo = null;

        mGestureHandler.setPlayer(null);
        mPlayerView.setPlayer(null);
    }

    private void createMediaSession() {
        if (VERSION.SDK_INT <= 19) {
            // Fix Android 4.4 bug: MediaButtonReceiver component may not be null
            return;
        }

        boolean disableNotifications = getPlayerTweaksData().isPlaybackNotificationsDisabled();
        mMediaSession = new MediaSessionCompat(getApplicationContext(), getPackageName());
        mMediaSession.setActive(!disableNotifications);
        mMediaSessionConnector = new MediaSessionConnector(mMediaSession);

        try {
            mMediaSessionConnector.setPlayer(mPlayer);
        } catch (NoSuchMethodError e) {
            // Android 9, Sony: No virtual method setState(IJFJ) in PlaybackState$Builder
            return;
        }

        // NOTE: Don't set to null. This won't disable notifications but makes them empty.
        mMediaSessionConnector.setMediaMetadataProvider(player -> {
            Video video = getVideo();

            if (video == null) {
                return null;
            }

            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, video.getTitleFull());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, video.getTitleFull());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, video.getAuthor());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, Helpers.toString(video.getSecondTitleFull()));
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, video.getCardImageUrl());
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDurationMs());

            return metadataBuilder.build();
        });

        mMediaSessionConnector.setQueueNavigator(new QueueNavigator() {
            @Override
            public long getSupportedQueueNavigatorActions(Player player) {
                return PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
            }

            @Override
            public void onTimelineChanged(Player player) {
                // NOP: the queue lives in common's Playlist, not in the exo timeline
            }

            @Override
            public void onCurrentWindowIndexChanged(Player player) {
                // NOP
            }

            @Override
            public long getActiveQueueItemId(@Nullable Player player) {
                return 0;
            }

            @Override
            public void onSkipToPrevious(Player player, ControlDispatcher controlDispatcher) {
                mPlaybackPresenter.onPreviousClicked();
            }

            @Override
            public void onSkipToQueueItem(Player player, ControlDispatcher controlDispatcher, long id) {
                // NOP
            }

            @Override
            public void onSkipToNext(Player player, ControlDispatcher controlDispatcher) {
                mPlaybackPresenter.onNextClicked();
            }

            @Override
            public boolean onCommand(Player player, ControlDispatcher controlDispatcher, String command,
                                     Bundle extras, android.os.ResultReceiver cb) {
                return false;
            }
        });
    }

    private void releaseMediaSession() {
        if (mMediaSessionConnector != null) {
            mMediaSessionConnector.setPlayer(null);
            mMediaSessionConnector.setMediaMetadataProvider(null);
            mMediaSessionConnector.setQueueNavigator(null);
            mMediaSessionConnector = null;
        }

        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }
    }

    private void createSubtitleManager() {
        if (mSubtitleManager == null && mPlayer != null && mSubtitleView != null) {
            mSubtitleManager = new SubtitleManager(mSubtitleView);

            if (mPlayer.getTextComponent() != null) {
                mPlayer.getTextComponent().addTextOutput(mSubtitleManager);
            }
        }
    }

    // ---------------------------------------------------- orientation / PIP

    private void applyOrientation() {
        mIsLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        boolean fullscreen = mIsLandscape || isInPIPMode();

        // Fullscreen: the video fills the screen. Portrait: 16:9 pinned to the top, panel below.
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(mRoot);

        if (fullscreen) {
            constraintSet.setDimensionRatio(R.id.playback_player_container, null);
            constraintSet.connect(R.id.playback_player_container, ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        } else {
            constraintSet.setDimensionRatio(R.id.playback_player_container, "H,16:9");
            constraintSet.clear(R.id.playback_player_container, ConstraintSet.BOTTOM);
        }

        constraintSet.applyTo(mRoot);

        mPanel.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        updateDetailsVisibility();

        if (!fullscreen && VERSION.SDK_INT < 30) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        mFullscreenButton.setImageResource(fullscreen
                ? R.drawable.playback_ic_fullscreen_exit : R.drawable.playback_ic_fullscreen);

        applySystemUi(fullscreen);
    }

    /**
     * Fullscreen (landscape/PIP) hides the navigation bar; portrait keeps it on screen so the
     * back/home/recents buttons don't have to be swiped up first. The status bar stays hidden
     * either way — the overlay's top row sits where it would be.
     */
    private void applySystemUi(boolean fullscreen) {
        if (!fullscreen) {
            nm7RestorePortraitBars();
            if (mRoot != null) {
                mRoot.postDelayed(this::nm7RestorePortraitBars, 80L);
                mRoot.postDelayed(this::nm7RestorePortraitBars, 350L);
            }
            return;
        }

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        if (VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }




    /**
     * Called on every resume — MotherActivity hides the bars again each time, and coming back
     * from a dialog or from PIP must not leave portrait without its navigation bar. Overridden
     * because PIP counts as fullscreen here, and because the insets are handled by
     * {@link #initWindowInsets()} rather than by the base class.
     */
    @Override
    protected void applySystemBars() {
        applySystemUi(isLandscape() || isInPIPMode());
    }




    private void nm7InstallPortraitInsets() {
        if (mNm7PortraitInsetsInstalled || mRoot == null) return;
        mNm7PortraitInsetsInstalled = true;
        mNm7BaseRootPaddingLeft = mRoot.getPaddingLeft();
        mNm7BaseRootPaddingTop = mRoot.getPaddingTop();
        mNm7BaseRootPaddingRight = mRoot.getPaddingRight();
        mNm7BaseRootPaddingBottom = mRoot.getPaddingBottom();
        if (android.os.Build.VERSION.SDK_INT >= 20) {
            mRoot.setOnApplyWindowInsetsListener((v, insets) -> {
                if (!isLandscape() && !isInPIPMode()) {
                    int top = 0;
                    int bottom = 0;
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets bars = insets.getInsets(
                                android.view.WindowInsets.Type.statusBars()
                                        | android.view.WindowInsets.Type.navigationBars()
                                        | android.view.WindowInsets.Type.displayCutout());
                        top = bars.top;
                        bottom = bars.bottom;
                    } else {
                        top = insets.getSystemWindowInsetTop();
                        bottom = insets.getSystemWindowInsetBottom();
                    }
                    mRoot.setPadding(
                            mNm7BaseRootPaddingLeft,
                            Math.max(mNm7BaseRootPaddingTop, top),
                            mNm7BaseRootPaddingRight,
                            Math.max(mNm7BaseRootPaddingBottom, bottom));
                } else {
                    mRoot.setPadding(mNm7BaseRootPaddingLeft, mNm7BaseRootPaddingTop,
                            mNm7BaseRootPaddingRight, mNm7BaseRootPaddingBottom);
                }
                return insets;
            });
            mRoot.requestApplyInsets();
        }
    }

    private void nm7RestorePortraitBars() {
        if (isLandscape() || isInPIPMode() || isFinishing() || isDestroyed()) return;

        android.view.Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        window.setStatusBarColor(android.graphics.Color.BLACK);

        if (VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsAppearance(
                        0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        nm7InstallPortraitInsets();
        if (mRoot != null) mRoot.requestApplyInsets();
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isLandscape() && !isInPIPMode()) {
            nm7RestorePortraitBars();
            if (mRoot != null) {
                mRoot.postDelayed(this::nm7RestorePortraitBars, 80L);
                mRoot.postDelayed(this::nm7RestorePortraitBars, 350L);
                mRoot.postDelayed(this::nm7RestorePortraitBars, 700L);
            }
        }
    }

    private void toggleFullscreen() {
        resetAutoHide();

        // The activity handles orientation changes itself (see manifest configChanges),
        // so this only flips the requested orientation; onConfigurationChanged does the rest.
        setRequestedOrientation(mIsLandscape
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // The activity is NOT recreated (configChanges in the manifest): the engine keeps running
        applyOrientation();

        // Status bar and cutout sit on different edges per orientation; recompute the bar padding
        mRoot.requestApplyInsets();
    }

    @TargetApi(24)
    @SuppressWarnings("deprecation")
    private void enterPipMode() {
        if (!Helpers.isPictureInPictureSupported(this)) {
            return;
        }

        if (!wannaEnterToPip()) {
            return;
        }

        Log.d(TAG, "Entering PIP mode...");

        try {
            if (VERSION.SDK_INT >= 26) {
                enterPictureInPictureMode(createPipParams());
            } else {
                enterPictureInPictureMode();
            }
        } catch (Exception e) {
            // Device doesn't support picture-in-picture mode
            Log.e(TAG, e.getMessage());
        }
    }

    @TargetApi(26)
    private PictureInPictureParams createPipParams() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();

        builder.setAspectRatio(createPipRatio());

        return builder.build();
    }

    @TargetApi(26)
    private Rational createPipRatio() {
        int width = 16;
        int height = 9;

        if (mPlayer != null && mPlayer.getVideoFormat() != null
                && mPlayer.getVideoFormat().width > 0 && mPlayer.getVideoFormat().height > 0) {
            width = mPlayer.getVideoFormat().width;
            height = mPlayer.getVideoFormat().height;
        }

        // Android rejects ratios outside 0.418..2.39
        float ratio = (float) width / height;

        if (ratio < 0.5f || ratio > 2.3f) {
            width = 16;
            height = 9;
        }

        return new Rational(width, height);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);

        sIsInPipMode = isInPictureInPictureMode;

        showHideWidgets(!isInPictureInPictureMode);
        applyOrientation();
    }

    /**
     * HOME pressed (or the app is otherwise left). Port of TV's background-mode logic.
     */
    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!mNm7Stopped && !isFinishing() && mPlayer != null) {
            blockEngine(true);
            if (mPlayer.getPlayWhenReady()) nm7SetBackground(true);
        }
    }

    /**
     * BACK pressed, or the PIP window's close button.
     */
    @Override
    public void finish() {
        Log.d(TAG, "Finishing activity...");

        if (!skipPip()) {
            enterPipMode(); // without this call the app hangs when pressing the PIP button
        }

        if (doNotDestroy() && !skipPip()) {
            blockEngine(true);
            // Ensure this activity is reopened when the user returns to the app
            getViewManager().blockTop(this);
            getViewManager().startParentView(this);
        } else {
            if (getPlayerTweaksData().isKeepFinishedActivityEnabled()) {
                getViewManager().startParentView(this);
                maybeReleasePlayer();
            } else {
                super.finish();
            }
        }
    }

    /**
     * Force finish (PIP, app exit etc).
     */
    @Override
    public void finishReally() {
        android.util.Log.i("NM7Playback", "finishReally mini=" + sNm7Mini + " explicit=" + mNm7Stopped
                + " state=" + (mPlayer == null ? -1 : mPlayer.getPlaybackState())
                + " position=" + (mPlayer == null ? -1 : mPlayer.getCurrentPosition())
                + " duration=" + (mPlayer == null ? -1 : mPlayer.getDuration()));
        super.finishReally();

        releasePlayer();

        if (mPlaybackPresenter.getPlayer() == this) mPlaybackPresenter.onFinish();
    }

    private boolean skipPip() {
        return mIsBackPressed
                && getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME;
    }

    @TargetApi(24)
    private boolean wannaEnterToPip() {
        boolean isPip = getPlayerData().getBackgroundMode() == PlayerData.BACKGROUND_MODE_PIP || isEngineBlocked();

        return isPip && !isInPIPMode();
    }

    private boolean doNotDestroy() {
        sIsInPipMode = isInPIPMode();

        boolean isBackground = getPlayerData().getBackgroundMode() == BACKGROUND_MODE_SOUND || isEngineBlocked();

        return sIsInPipMode || isBackground;
    }

    /**
     * PIP mode fix: the overlay and the panel can't be shown inside the small window.
     */
    private void showHideWidgets(boolean show) {
        if (!show) {
            showOverlay(false);
        }

        mPanel.setVisibility(show && !mIsLandscape && !isInPIPMode() ? View.VISIBLE : View.GONE);
        updateDetailsVisibility();
    }

    // ------------------------------------------------------ overlay handling

    private void togglePlayPause() {
        resetAutoHide();

        boolean playing = getPlayWhenReady();

        setPlayWhenReady(!playing);

        if (playing) {
            mPlaybackPresenter.onPauseClicked();
        } else {
            mPlaybackPresenter.onPlayClicked();
        }

        updatePlayButton();
    }

    private void updatePlayButton() {
        mPlayButton.setImageResource(getPlayWhenReady()
                ? R.drawable.playback_ic_pause : R.drawable.playback_ic_play);
    }

    private void scheduleAutoHide() {
        mHandler.removeCallbacks(mHideOverlay);
        mHandler.postDelayed(mHideOverlay, OVERLAY_AUTO_HIDE_MS);
    }

    private void cancelAutoHide() {
        mHandler.removeCallbacks(mHideOverlay);
    }

    /** Keeps the overlay alive for another auto-hide period (any user interaction). */
    private void resetAutoHide() {
        if (mIsOverlayShown) {
            scheduleAutoHide();
        }
    }

    private void updateProgress() {
        long positionMs = getPositionMs();
        long durationMs = getDurationMs();

        if (positionMs < 0) {
            positionMs = 0;
        }

        if (!mIsScrubbing) {
            mSeekBar.setProgress(durationMs > 0 ? (int) (positionMs * SEEK_BAR_MAX / durationMs) : 0);
            mPositionLabel.setText(formatTime(positionMs));
        }

        mSeekBar.setSecondaryProgress(mPlayer != null ? mPlayer.getBufferedPercentage() * (SEEK_BAR_MAX / 100) : 0);
        mDurationLabel.setText(formatTime(durationMs));

        updatePlayButton();
        updateEndingTime();
    }

    private long progressToPositionMs(int progress) {
        long durationMs = getDurationMs();

        return durationMs > 0 ? durationMs * progress / SEEK_BAR_MAX : 0;
    }

    private static String formatTime(long timeMs) {
        if (timeMs < 0) {
            timeMs = 0;
        }

        long totalSec = timeMs / 1_000;
        long seconds = totalSec % 60;
        long minutes = (totalSec / 60) % 60;
        long hours = totalSec / 3_600;

        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    // --------------------------------------------------------- gestures

    @Override
    public void onTap() {
        // Reset common's ui timers (the same trick TV's applyTickle does)
        mPlaybackPresenter.onKeyDown(-1);

        showOverlay(!mIsOverlayShown);
    }

    @Override
    public void onVolumeDelta(float delta) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (audioManager == null) {
            return;
        }

        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        if (mDragVolume < 0) {
            mDragVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }

        mDragVolume = clamp(mDragVolume + delta * max, 0, max);

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(mDragVolume), 0);
        } catch (SecurityException e) {
            // Do not disturb mode
            return;
        }

        showIndicator(R.drawable.playback_ic_volume, percent(mDragVolume / max));
    }

    @Override
    public void onBrightnessDelta(float delta) {
        WindowManager.LayoutParams params = getWindow().getAttributes();

        float current = params.screenBrightness;

        if (current < 0) { // BRIGHTNESS_OVERRIDE_NONE: take the system value as a start
            current = getSystemBrightness();
        }

        float brightness = clamp(current + delta, 0.01f, 1f);
        params.screenBrightness = brightness;
        getWindow().setAttributes(params);

        showIndicator(R.drawable.playback_ic_brightness, percent(brightness));
    }

    private float getSystemBrightness() {
        try {
            int value = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            return value / 255f;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    @Override
    public void onSeekStart() {
        mSeekAnchorMs = getPositionMs();
        mSeekTargetMs = mSeekAnchorMs;
        // Stop the periodic updater from overwriting the preview position
        mIsScrubbing = true;
    }

    @Override
    public void onSeekDelta(float totalDelta) {
        long durationMs = getDurationMs();

        if (durationMs <= 0 || mSeekAnchorMs < 0) {
            return;
        }

        // A full-width swipe covers SEEK_SWIPE_FRACTION of the video, capped so that very long
        // videos stay controllable and very short ones don't jump wildly
        long spanMs = Math.min((long) (durationMs * SEEK_SWIPE_FRACTION), MAX_SEEK_SWIPE_MS);
        mSeekTargetMs = clampLong(mSeekAnchorMs + (long) (totalDelta * spanMs), 0, durationMs);

        long diffMs = mSeekTargetMs - mSeekAnchorMs;
        String label = String.format(Locale.US, "%s  [%s%s]",
                formatTime(mSeekTargetMs), diffMs >= 0 ? "+" : "-", formatTime(Math.abs(diffMs)));

        showIndicator(diffMs >= 0 ? R.drawable.playback_ic_next : R.drawable.playback_ic_prev, label);

        // Move the scrubber and the clock along so the controls agree with the preview
        if (mSeekBar != null) {
            mSeekBar.setProgress((int) (mSeekTargetMs * SEEK_BAR_MAX / durationMs));
        }

        if (mPositionLabel != null) {
            mPositionLabel.setText(formatTime(mSeekTargetMs));
        }
    }

    /**
     * Pinch out fills the screen (crops the sides/top of the frame), pinch in fits the whole
     * frame inside the screen. Persisted through PlayerData like the TV zoom setting, so the
     * choice survives to the next video.
     */
    @Override
    public void onZoomChanged(boolean fillScreen) {
        int mode = fillScreen ? PlayerConstants.RESIZE_MODE_FIT_BOTH : PlayerConstants.RESIZE_MODE_DEFAULT;

        setResizeMode(mode);
        getPlayerData().setResizeMode(mode);

        showIndicator(fillScreen ? R.drawable.playback_ic_fullscreen : R.drawable.playback_ic_fullscreen_exit,
                getString(fillScreen ? R.string.playback_zoom_fill : R.string.playback_zoom_fit));

        mHandler.removeCallbacks(mHideIndicator);
        mHandler.postDelayed(mHideIndicator, INDICATOR_HIDE_DELAY_MS);
    }

    @Override
    public void onDragEnd() {
        mDragVolume = -1;

        // Commit the scrub only now: seeking on every touch move stutters playback
        if (mSeekAnchorMs >= 0) {
            if (mSeekTargetMs != mSeekAnchorMs) {
                setPositionMs(mSeekTargetMs);
            }

            mSeekAnchorMs = -1;
            mIsScrubbing = false;
        }

        hideIndicator();
    }

    @Override
    public void onSpeedBoostStart() {
        if (mPlayer == null || mSpeedBoosted) {
            return;
        }

        float speed = mExoPlayerController.getSpeed();
        mSavedSpeed = speed > 0 ? speed : 1f;
        mSpeedBoosted = true;

        // Bypass setSpeed(): the boost is temporary and must not be persisted or reported
        mExoPlayerController.setSpeed(SPEED_BOOST);

        showIndicator(R.drawable.playback_ic_speed, getString(R.string.playback_speed_boost));
    }

    @Override
    public void onSpeedBoostEnd() {
        if (!mSpeedBoosted) {
            return;
        }

        mSpeedBoosted = false;

        if (mPlayer != null) {
            mExoPlayerController.setSpeed(mSavedSpeed);
        }

        hideIndicator();
    }

    @Override
    public boolean isDragEnabled() {
        // Volume/brightness swipes are fullscreen-only (in portrait the panel owns the drag)
        return mIsLandscape && !isInPIPMode();
    }

    @Override
    public boolean isGestureAllowedAt(float playerX, float playerY) {
        if (!mIsOverlayShown) {
            return true;
        }

        // Don't fight with the visible controls
        return !isInside(mTopBar, playerX, playerY)
                && !isInside(mCenterBar, playerX, playerY)
                && !isInside(mBottomBar, playerX, playerY);
    }

    private static boolean isInside(View view, float x, float y) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }

        return x >= view.getLeft() && x <= view.getRight() && y >= view.getTop() && y <= view.getBottom();
    }

    private void showIndicator(int iconResId, String text) {
        mIndicatorIcon.setImageResource(iconResId);
        mIndicatorText.setText(text);
        mIndicator.setVisibility(View.VISIBLE);
    }

    private void hideIndicator() {
        mIndicator.setVisibility(View.GONE);
    }

    private static String percent(float value) {
        return Math.round(value * 100) + "%";
    }

    private static long clampLong(long value, long min, long max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }

        return value > max ? max : value;
    }

    // ------------------------------------------- VideoGroupAdapter.Listener

    @Override
    public void onVideoClicked(Video item) {
        mPlaybackPresenter.onSuggestionItemClicked(item);
    }

    @Override
    public void onVideoLongClicked(Video item) {
        mPlaybackPresenter.onSuggestionItemLongClicked(item);
    }

    @Override
    public void onScrollEnd(Video item) {
        mPlaybackPresenter.onScrollEnd(item);
    }

    // ------------------------------------------------- PlayerView (engine)

    /**
     * Quality label produced by the engine (e.g. "1080p 60fps avc"). The chips are icon-only,
     * so it becomes the chip's tooltip/description rather than a visible label.
     */
    @Override
    public void setQualityInfo(String info) {
        if (mQualityChip != null) {
            CharSequence label = TextUtils.isEmpty(info) ? getString(R.string.playback_quality) : info;
            mQualityChip.setContentDescription(label);
            TooltipCompat.setTooltipText(mQualityChip, label);
        }
    }

    // ------------------------------------------------------------ PlayerUI

    @Override
    public void updateSuggestions(VideoGroup group) {
        if (group == null) {
            return;
        }

        mSuggestionsAdapter.update(group);

        trackSuggestionGroup(group);
    }

    @Override
    public void removeSuggestions(VideoGroup group) {
        if (group == null) {
            return;
        }

        // The caller builds a fresh group holding the videos to drop; the shared adapter
        // dispatches on the action, so state it explicitly.
        group.setAction(VideoGroup.ACTION_REMOVE);
        mSuggestionsAdapter.update(group);
    }

    @Override
    public int getSuggestionsIndex(VideoGroup group) {
        return group != null ? indexOfSuggestionGroup(group.getId()) : -1;
    }

    @Override
    public VideoGroup getSuggestionsByIndex(int index) {
        return index >= 0 && index < mSuggestionGroups.size() ? mSuggestionGroups.get(index) : null;
    }

    @Override
    public void focusSuggestedItem(int index) {
        // TV-only: moves DPAD focus inside a suggestions row. Phones have no card focus.
    }

    @Override
    public void focusSuggestedItem(Video video) {
        // TV-only (see above).
    }

    @Override
    public void resetSuggestedPosition() {
        if (mSuggestionsView != null) {
            mSuggestionsView.scrollToPosition(0);
        }
    }

    @Override
    public boolean isSuggestionsEmpty() {
        return mSuggestionsAdapter == null || mSuggestionsAdapter.isEmpty();
    }

    @Override
    public void clearSuggestions() {
        if (mSuggestionsAdapter != null) {
            mSuggestionsAdapter.clear();
        }

        mSuggestionGroups.clear();
    }

    private void trackSuggestionGroup(VideoGroup group) {
        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE && group.getPosition() == -1) {
            mSuggestionGroups.clear();
            return;
        }

        if (action == VideoGroup.ACTION_REMOVE || action == VideoGroup.ACTION_REMOVE_AUTHOR
                || action == VideoGroup.ACTION_SYNC || group.isEmpty()) {
            return;
        }

        int index = indexOfSuggestionGroup(group.getId());

        if (index != -1) {
            mSuggestionGroups.set(index, group);
            return;
        }

        int position = group.getPosition();

        if (position < 0 || position > mSuggestionGroups.size()) {
            mSuggestionGroups.add(group);
        } else {
            mSuggestionGroups.add(position, group);
        }
    }

    private int indexOfSuggestionGroup(int groupId) {
        for (int i = 0; i < mSuggestionGroups.size(); i++) {
            if (mSuggestionGroups.get(i).getId() == groupId) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public void showOverlay(boolean show) {
        if (show && isInPIPMode()) {
            return; // the UI can't be displayed inside the PIP window
        }

        if (!show && mIsScrubbing) {
            return; // never hide the controls while the user drags the seek bar
        }

        boolean changed = mIsOverlayShown != show;
        mIsOverlayShown = show;
        mOverlay.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            updateProgress();
            mHandler.removeCallbacks(mUpdateProgress);
            mHandler.post(mUpdateProgress);
            scheduleAutoHide();
        } else {
            mHandler.removeCallbacks(mUpdateProgress);
            cancelAutoHide();
        }

        if (changed) {
            mPlaybackPresenter.onControlsShown(show);
        }
    }

    @Override
    public boolean isOverlayShown() {
        return mIsOverlayShown;
    }

    @Override
    public void showSuggestions(boolean show) {
        // In portrait the suggestions list is always on screen below the video; in fullscreen
        // it's hidden. All this can do is bring the controls up and scroll the list to the top.
        showOverlay(show);

        if (show) {
            resetSuggestedPosition();
        }
    }

    @Override
    public boolean isSuggestionsShown() {
        // On TV this means "the user left the controls and is browsing the suggestions row",
        // which makes SuggestionsController SKIP reloading them (see appendSuggestions()).
        // The phone panel is always visible, so answering true would break suggestions entirely.
        return false;
    }

    @Override
    public void showControls(boolean show) {
        showOverlay(show);
    }

    @Override
    public boolean isControlsShown() {
        return mIsOverlayShown;
    }

    @Override
    public int getButtonState(int buttonId) {
        Integer state = mButtonStates.get(buttonId);

        return state != null ? state : BUTTON_OFF;
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        updateNm7RatingCounts();
        mButtonStates.put(buttonId, buttonState);

        List<View> views = mButtonViews.get(buttonId);

        if (views == null) {
            return; // an id with no control on this UI (overflow-only action): silently stored
        }

        for (View view : views) {
            applyButtonStyle(view, buttonState);
        }
    }

    /**
     * ON (or any non-zero mode, e.g. the repeat modes) is accented, DISABLED is dimmed.
     */
    private void applyButtonStyle(View view, int buttonState) {
        if (buttonState == BUTTON_DISABLED) {
            view.setEnabled(false);
            view.setAlpha(0.4f);
            return;
        }

        view.setEnabled(true);
        view.setAlpha(1f);

        int color = androidx.core.content.ContextCompat.getColor(this,
                buttonState == BUTTON_OFF ? R.color.playback_control : R.color.playback_accent);

        if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            if (buttonState == BUTTON_OFF) color = android.graphics.Color.rgb(15, 15, 15);
            if (view.getId() == R.id.playback_chip_subscribe) {
                boolean subscribed = buttonState != BUTTON_OFF;
                button.setText(subscribed ? "Đã đăng ký" : "Đăng ký");
                button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(subscribed ? 0xFFF1F1F1 : 0xFF0F0F0F));
                color = subscribed ? 0xFF0F0F0F : 0xFFFFFFFF;
            }
            button.setIconTint(android.content.res.ColorStateList.valueOf(color));
            button.setTextColor(color);
        } else if (view instanceof ImageView) {
            ((ImageView) view).setColorFilter(color);
        }
    }

    @Override
    public void setChannelIcon(String iconUrl) {
        if (mChannelIcon == null) {
            return;
        }

        if (TextUtils.isEmpty(iconUrl)) {
            mChannelIcon.setImageDrawable(null);
            return;
        }

        if (isFinishing() || isDestroyed()) {
            return;
        }

        Glide.with(this)
                .load(iconUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.playback_channel_placeholder)
                .error(R.drawable.playback_channel_placeholder)
                .into(mChannelIcon);
    }

    @Override
    public void setSeekPreviewTitle(String title) {
        if (mSeekPreviewLabel == null) {
            return;
        }

        mSeekPreviewLabel.setText(title);
        mSeekPreviewLabel.setVisibility(TextUtils.isEmpty(title) ? View.GONE : View.VISIBLE);
    }

    @Override
    public void setNextTitle(Video nextVideo) {
        if (mNextTitleLabel == null) {
            return;
        }

        if (nextVideo == null || nextVideo.getTitle() == null) {
            mNextTitleLabel.setText(null);
            mNextTitleLabel.setVisibility(View.GONE);
            return;
        }

        String info = nextVideo.getAuthor() != null
                ? nextVideo.getTitle() + " " + Video.TERTIARY_TEXT_DELIM + " " + nextVideo.getAuthor()
                : nextVideo.getTitle();

        mNextTitleLabel.setText(getString(R.string.playback_next_format, info));
        mNextTitleLabel.setVisibility(View.VISIBLE);
    }

    @Override
    public void showDebugInfo(boolean show) {
        // v1: no debug overlay on the phone UI. common's DebugInfoManager needs a dedicated
        // ViewGroup; wire it here when the stats screen is designed.
    }

    @Override
    public void showSubtitles(boolean show) {
        createSubtitleManager();

        if (mSubtitleManager != null) {
            mSubtitleManager.show(show);
        }
    }

    @Override
    public void loadStoryboard() {
        // v1: no seek-preview thumbnails (TV uses Leanback's StoryboardSeekDataProvider).
    }

    @Override
    public void setTitle(String title) {
        if (mOverlayTitle != null) {
            mOverlayTitle.setText(title);
        }

        if (mPanelTitle != null) {
            mPanelTitle.setText(title);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        // NM7 final spinner hard-hide: use the native shutter/poster lifecycle.
        if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
        mProgressHidePending = false;
    }

    /**
     * Only STATE_BUFFERING counts as stalled. Don't fold isLoading() in here: ExoPlayer reports
     * loading whenever it tops the buffer up, which is most of a normal playback.
     */
    private boolean isPlayerBuffering() {
        return mExoPlayerController != null && mExoPlayerController.isBuffering();
    }

    @Override
    public void setSeekBarSegments(List<SeekBarSegment> segments) {
        if (mSeekBar != null) {
            mSeekBar.setSegments(segments);
        }
    }

    @Override
    public void updateEndingTime() {
        if (mEndingTimeLabel == null || mEndingTimeLabel.getVisibility() != View.VISIBLE) {
            return;
        }

        Video video = getVideo();
        long remainingMs = 0;

        if (video != null && !video.isLive) {
            remainingMs = getDurationMs() - getPositionMs();

            float speed = getSpeed();
            if (speed > 0) {
                remainingMs = (long) (remainingMs / speed);
            }

            if (remainingMs < 0) {
                remainingMs = 0;
            }
        }

        mEndingTimeLabel.setText(remainingMs > 0
                ? String.format("%s %s", Helpers.HOURGLASS, DateHelper.toShortTime(System.currentTimeMillis() + remainingMs))
                : null);
    }

    private void nm7SendLiveChatMessage() {
        if (mNm7LiveChatInput == null || mNm7LiveChatSend == null) return;
        String message = mNm7LiveChatInput.getText().toString().trim();
        if (message.isEmpty()) return;

        LiveChatService service = YouTubeServiceManager.instance().getLiveChatService();
        if (service == null || !service.canSendLiveChatMessage()) {
            mNm7LiveChatStatus.setText("Chưa thể gửi chat - kiểm tra đăng nhập/quyền chat");
            return;
        }

        if (mNm7LiveChatSendAction != null && !mNm7LiveChatSendAction.isDisposed()) {
            mNm7LiveChatSendAction.dispose();
        }
        mNm7LiveChatSend.setEnabled(false);
        mNm7LiveChatStatus.setText("Đang gửi...");
        mNm7LiveChatSendAction = service.sendLiveChatMessageObserve(message)
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(success -> {
                    mNm7LiveChatSend.setEnabled(true);
                    if (Boolean.TRUE.equals(success)) {
                        mNm7LiveChatInput.setText("");
                        mNm7LiveChatStatus.setText("Đã gửi");
                    } else {
                        mNm7LiveChatStatus.setText("Không gửi được - kiểm tra tài khoản YouTube");
                    }
                }, error -> {
                    mNm7LiveChatSend.setEnabled(true);
                    mNm7LiveChatStatus.setText("Gửi chat thất bại");
                    android.util.Log.e("NM7LiveChat", "send failed", error);
                });
    }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
        runOnUiThread(() -> {
            if (mNm7ChatReceiver != null) {
                mNm7ChatReceiver.setCallback(null);
            }

            mNm7ChatReceiver = chatReceiver;
            mNm7LiveChatAdapter.clear();

            if (chatReceiver == null) {
                mNm7LiveChatPanel.setVisibility(View.GONE);
                mNm7LiveChatStatus.setText("Đang kết nối...");
                if (mPanel != null) mPanel.setVisibility(View.VISIBLE);
                return;
            }

            // A live chat stream replaces the metadata/suggestions panel in
            // portrait mode, matching the native YouTube phone layout.
            if (mPanel != null) mPanel.setVisibility(View.GONE);
            mNm7LiveChatPanel.setVisibility(View.VISIBLE);
            mNm7LiveChatStatus.setText("Đang xem trực tiếp");
            mNm7ChatReceiver.setCallback(chatItem -> runOnUiThread(() -> {
                mNm7LiveChatAdapter.add(chatItem);
                int last = mNm7LiveChatAdapter.getItemCount() - 1;
                if (last >= 0) mNm7LiveChatList.scrollToPosition(last);
                mNm7LiveChatStatus.setText("Đang xem trực tiếp");
            }));
        });
    }


    @Override
    public void setVideoDetails(String description, String commentsKey) {
        // Metadata is delivered on the main thread, but so is every other UI call here
        runOnUiThread(() -> applyVideoDetails(description, commentsKey));
    }

    private void applyVideoDetails(String description, String commentsKey) {
        if (mDescriptionCard == null) {
            return;
        }

        Video video = getVideo();
        String videoId = video != null ? video.videoId : null;

        if (!Helpers.equals(mDetailsVideoId, videoId)) {
            // Late callback from the video before this one: its sheet content is stale
            closeDetails();
            mDetailsVideoId = videoId;
        }

        // The description also arrives with the format info, which can beat the metadata
        mDescription = !TextUtils.isEmpty(description) ? description
                : video != null ? video.description : null;
        mCommentsKey = commentsKey;
        updateNm7RatingCounts();
        loadNm7CommentPreview();

        boolean hasDescription = !TextUtils.isEmpty(mDescription);
        // The teaser is two lines tall: paragraph breaks would waste both of them
        mDescriptionPreview.setText(hasDescription ? mDescription.replace('\n', ' ') : null);
        mDescriptionCard.setVisibility(View.GONE);
        mDescriptionDivider.setVisibility(View.GONE);

        boolean hasComments = mCommentsKey != null;
        mCommentsCard.setVisibility(hasComments ? View.VISIBLE : View.GONE);
        mCommentsDivider.setVisibility(hasComments ? View.VISIBLE : View.GONE);

        if (mDetailsMode == DETAILS_DESCRIPTION) {
            mDetailsDescription.setText(mDescription);
        }
    }

    private static final class Nm7LiveChatAdapter
            extends RecyclerView.Adapter<Nm7LiveChatAdapter.Holder> {
        private static final int MAX_ITEMS = 80;
        private final android.content.Context mContext;
        private final java.util.List<ChatItem> mItems = new java.util.ArrayList<>();

        Nm7LiveChatAdapter(android.content.Context context) {
            mContext = context;
        }

        void clear() {
            mItems.clear();
            notifyDataSetChanged();
        }

        void add(ChatItem item) {
            if (item == null || TextUtils.isEmpty(item.getMessage())) return;
            mItems.add(item);
            if (mItems.size() > MAX_ITEMS) {
                mItems.remove(0);
                notifyDataSetChanged();
            } else {
                notifyItemInserted(mItems.size() - 1);
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(mContext);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(12, 6, 12, 6);

            ImageView avatar = new ImageView(mContext);
            avatar.setLayoutParams(new LinearLayout.LayoutParams(34, 34));
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(avatar);

            LinearLayout textBox = new LinearLayout(mContext);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setPadding(10, 0, 0, 0);
            textBox.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView author = new TextView(mContext);
            author.setTextColor(0xFF555555);
            author.setTextSize(12);
            author.setMaxLines(1);

            TextView message = new TextView(mContext);
            message.setTextColor(0xFF222222);
            message.setTextSize(14);
            message.setPadding(0, 1, 0, 0);

            textBox.addView(author);
            textBox.addView(message);
            row.addView(textBox);

            return new Holder(row, avatar, author, message);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ChatItem item = mItems.get(position);
            holder.author.setText(item.getAuthorName());
            holder.message.setText(item.getMessage());

            String photo = item.getAuthorPhoto();
            if (!TextUtils.isEmpty(photo)) {
                Glide.with(mContext).load(photo).circleCrop()
                        .placeholder(R.drawable.playback_channel_placeholder)
                        .error(R.drawable.playback_channel_placeholder)
                        .into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.playback_channel_placeholder);
            }
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView author;
            final TextView message;

            Holder(View itemView, ImageView avatar, TextView author, TextView message) {
                super(itemView);
                this.avatar = avatar;
                this.author = author;
                this.message = message;
            }
        }
    }

    // -------------------------------------------------------- PlayerManager

    @Override
    public void setVideo(Video item) {
        nm7CancelPosterReadyFallback();
        // NM7 1.10.96: no indeterminate spinner during video startup.
        // Keep the player surface clean: black shutter -> thumbnail -> video.
        if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
        nm7CancelPosterRenderProbe();
        // NM7 1.10.89: never expose an empty/late poster request. A late Glide result
        // must not flash over video that has already produced its first decoder frame.
        mNm7FirstFrameRendered = false;
        mNm7FirstFrameSeenAt = 0L;
        mNm7FirstFramePositionMs = 0L;
        mNm7PosterVideoId = item == null ? null : item.videoId;
        if (mNm7StartupPoster != null) {
            try {
                Glide.with(PlaybackActivity.this).clear(mNm7StartupPoster);
            } catch (RuntimeException ignored) { }
            mNm7StartupPoster.setImageDrawable(null);
            mNm7StartupPoster.setVisibility(View.GONE);
        }

        // NM7 1.10.95: do not use the synchronous clicked-card poster.
        // The reference flow is black shutter -> selected thumbnail -> playback.
        android.graphics.Bitmap nm7TransitionPoster = null;
        if (nm7TransitionPoster != null && mNm7StartupPoster != null) {
            mNm7StartupPoster.setImageBitmap(nm7TransitionPoster);
            mNm7StartupPoster.setVisibility(View.VISIBLE);
            mNm7StartupPoster.bringToFront();
            if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
            android.util.Log.i("NM7Playback", "startup_poster_source=clicked_card");
        }

        if (nm7TransitionPoster == null && item != null && mNm7StartupPoster != null
                && !TextUtils.isEmpty(item.getCardImageUrl())) {
            final String nm7ExpectedPosterVideoId = item.videoId;
            Glide.with(this)
                    .load(item.getCardImageUrl())
                    .dontAnimate()
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(
                                com.bumptech.glide.load.engine.GlideException e,
                                Object model,
                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                boolean isFirstResource) {
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(
                                android.graphics.drawable.Drawable resource,
                                Object model,
                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                com.bumptech.glide.load.DataSource dataSource,
                                boolean isFirstResource) {
                            if (mNm7FirstFrameRendered
                                    || !android.text.TextUtils.equals(
                                            mNm7PosterVideoId, nm7ExpectedPosterVideoId)
                                    || isFinishing() || isDestroyed()) {
                                return true;
                            }
                            mNm7StartupPoster.post(() -> {
                                if (!mNm7FirstFrameRendered
                                        && android.text.TextUtils.equals(
                                                mNm7PosterVideoId, nm7ExpectedPosterVideoId)
                                        && mNm7StartupPoster != null) {
                                    mNm7StartupPoster.setVisibility(View.VISIBLE);
                                    mNm7StartupPoster.bringToFront();
                                }
                            });
                            return false;
                        }
                    })
                    .into(mNm7StartupPoster);
        }
        if (!android.text.TextUtils.equals(mNm7SessionVideo == null ? null : mNm7SessionVideo.videoId,
                item == null ? null : item.videoId)) {
            mNm7RecoveryPending = mNm7RecoveryRestoreIntent = false;
            mNm7ErrorLabel = null;
            if (mNm7RecoveryTimeout != null) mHandler.removeCallbacks(mNm7RecoveryTimeout);
        }
        String nm7VideoId = item == null ? null : item.videoId;
        if (!android.text.TextUtils.equals(mNm7SessionVideo == null ? null : mNm7SessionVideo.videoId, nm7VideoId)) {
            cancelNm7RenderWatchdog(); cancelNm7DecoderRetry();
            mNm7RenderPolicy.video(nm7VideoId);
        }
        mNm7VideoRequestedAt = android.os.SystemClock.elapsedRealtime();
        android.util.Log.i("NM7Playback", "selected_video_start");
        mNm7StartupFrame = false;
        mNm7SessionVideo = item;
        if (item != null) {
            nm7ArmPosterRenderProbe(item.videoId);
        }
        mNm7RecoveryGate.video(item == null ? null : item.videoId);
        mExoPlayerController.setVideo(item);

        if (item == null) {
            return;
        }

        if (!Helpers.equals(mDetailsVideoId, item.videoId)) {
            // Hide the previous video's description/comments right away; the new ones
            // arrive with its metadata (see setVideoDetails)
            resetVideoDetails();
        }

        setTitle(item.getTitleFull() != null ? item.getTitleFull() : item.getTitle());

        if (mPanelSecondTitle != null) {
            CharSequence secondTitle = item.getSecondTitleFull();
            mPanelSecondTitle.setText(secondTitle);
            mPanelSecondTitle.setVisibility(TextUtils.isEmpty(secondTitle) ? View.GONE : View.VISIBLE);
        }

        if (mChannelName != null) {
            mChannelName.setText(item.getAuthor());
        }
    }

    @Override
    public Video getVideo() {
        return mNm7SessionVideo != null ? mNm7SessionVideo : mExoPlayerController != null ? mExoPlayerController.getVideo() : null;
    }

    @Override
    public void showBackground(String url) {
        if (mBackgroundView == null) {
            return;
        }

        if (TextUtils.isEmpty(url)) {
            Glide.with(getApplicationContext()).clear(mBackgroundView); mBackgroundView.setImageDrawable(null); mBackgroundView.setVisibility(View.GONE); return;
        }
        if (isFinishing() || isDestroyed()) {
            return;
        }

        mBackgroundView.setVisibility(View.VISIBLE);
        Glide.with(this).load(url).override(960, 540).into(mBackgroundView);
    }

    @Override
    public void showBackgroundColor(int colorResId) {
        if (mBackgroundView == null) {
            return;
        }

        Glide.with(this).clear(mBackgroundView);
        mBackgroundView.setImageDrawable(null);
        mBackgroundView.setVisibility(View.GONE);

        try {
            mRoot.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, colorResId));
        } catch (Exception e) {
            // Resource may come from another module's R: fall back to plain black
            mRoot.setBackgroundColor(0xFF000000);
        }
    }

    @Override
    public void resetPlayerState() {
        mExoPlayerController.resetPlayerState();

        setSeekBarSegments(null);
        setSeekPreviewTitle(null);
        setNextTitle(null);
    }

    @Override
    public boolean isEmbed() {
        return false;
    }

    // ---------------------------------------------------------- PlayerEngine

    @Override
    public void openSabr(MediaItemFormatInfo formatInfo) {
        mFormatInfo = formatInfo;
        mExoPlayerController.openSabr(formatInfo);
    }

    @Override
    public void openDash(MediaItemFormatInfo formatInfo) {
        mFormatInfo = formatInfo;
        mExoPlayerController.openDash(formatInfo);
    }

    @Override
    public void openDash(InputStream dashManifest) {
        mFormatInfo = null;
        mExoPlayerController.openDash(dashManifest);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        mFormatInfo = null;
        mExoPlayerController.openDashUrl(dashManifestUrl);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        mFormatInfo = null;
        mExoPlayerController.openHlsUrl(hlsPlaylistUrl);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        mFormatInfo = null;
        mExoPlayerController.openUrlList(urlList);
    }

    @Override
    public void openMerged(MediaItemFormatInfo formatInfo, String hlsPlaylistUrl) {
        mFormatInfo = formatInfo;
        mExoPlayerController.openMerged(formatInfo, hlsPlaylistUrl);
    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        mFormatInfo = null;
        mExoPlayerController.openMerged(dashManifest, hlsPlaylistUrl);
    }

    @Override
    public long getPositionMs() {
        return mExoPlayerController.getPositionMs();
    }

    @Override
    public void setPositionMs(long positionMs) {
        mExoPlayerController.setPositionMs(positionMs);
    }

    @Override
    public long getDurationMs() {
        long durationMs = mExoPlayerController.getDurationMs();

        long liveDurationMs = getVideo() != null ? getVideo().getLiveDurationMs() : 0;

        if (durationMs > Video.MAX_LIVE_DURATION_MS && liveDurationMs != 0) {
            durationMs = liveDurationMs;
        }

        return durationMs;
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        mExoPlayerController.setPlayWhenReady(play);
    }

    @Override
    public boolean getPlayWhenReady() {
        return mExoPlayerController.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return mExoPlayerController.isPlaying();
    }

    @Override
    public boolean isLoading() {
        return mExoPlayerController.isLoading();
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return mExoPlayerController.getVideoFormats();
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return mExoPlayerController.getAudioFormats();
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return mExoPlayerController.getSubtitleFormats();
    }

    @Override
    public void setFormat(FormatItem option) {
        mExoPlayerController.selectFormat(option);
    }

    @Override
    public FormatItem getVideoFormat() {
        return mExoPlayerController.getVideoFormat();
    }

    @Override
    public FormatItem getAudioFormat() {
        return mExoPlayerController.getAudioFormat();
    }

    @Override
    public FormatItem getSubtitleFormat() {
        return mExoPlayerController.getSubtitleFormat();
    }

    @Override
    public boolean isEngineInitialized() {
        return mPlayer != null;
    }

    @Override
    public void restartEngine() {
        if (sNm7SuspendedForIptv || mNm7Stopped) return;
        if (isFinishing() || isDestroyed()) {
            Log.e(TAG, "Can't restart engine. The player activity is being destroyed.");
            return;
        }

        releasePlayer();
        initializePlayer();
    }

    @Override
    public void reloadPlayback() {
        if (!isNm7CurrentEngine(mPlayer)) return;
        if (mPlayer != null) {
            mPlaybackPresenter.onEngineReleased();
            mPlaybackPresenter.onEngineInitialized();
        }
    }

    @Override
    public void blockEngine(boolean block) {
        mIsEngineBlocked = block;
    }

    @Override
    public boolean isEngineBlocked() {
        return mIsEngineBlocked;
    }

    @Override
    public boolean isInPIPMode() {
        return VERSION.SDK_INT >= 24 && isInPictureInPictureMode();
    }

    @Override
    public boolean containsMedia() {
        return mExoPlayerController.containsMedia();
    }

    @Override
    public void setSpeed(float speed) {
        mExoPlayerController.setSpeed(speed);
        // NOTE: the real speed isn't applied immediately, so use the supplied value
        setButtonState(BUTTON_SPEED, speed != 1.0f ? BUTTON_ON : BUTTON_OFF);
    }

    @Override
    public float getSpeed() {
        return mExoPlayerController.getSpeed();
    }

    public void setPitch(float pitch) {
        mExoPlayerController.setPitch(pitch);
    }

    public float getPitch() {
        return mExoPlayerController.getPitch();
    }

    @Override
    public void setVolume(float volume) {
        mExoPlayerController.setVolume(volume);
    }

    @Override
    public float getVolume() {
        return mExoPlayerController.getVolume();
    }

    @Override
    public void setResizeMode(int mode) {
        mPlayerView.setResizeMode(mode);
    }

    @Override
    public int getResizeMode() {
        return mPlayerView.getResizeMode();
    }

    @Override
    public void setZoomPercents(int percents) {
        mZoomPercents = percents > 0 ? percents : 100;

        applyVideoTransform();
    }

    @Override
    public void setAspectRatio(float ratio) {
        AspectRatioFrameLayout contentFrame = getContentFrame();

        if (contentFrame != null && ratio > 0) {
            // NOTE: overwritten by the engine on the next video size change (2.10 has no
            // dedicated 'user aspect ratio' hook on PlayerView)
            contentFrame.setAspectRatio(ratio);
        }
    }

    @Override
    public void setRotationAngle(int angle) {
        mRotationAngle = angle;

        applyVideoTransform();
    }

    @Override
    public void setVideoFlipEnabled(boolean enabled) {
        mFlipEnabled = enabled;

        applyVideoTransform();
    }

    @Override
    public void setVideoGravity(int gravity) {
        AspectRatioFrameLayout contentFrame = getContentFrame();

        if (contentFrame == null) {
            return;
        }

        ViewGroup.LayoutParams params = contentFrame.getLayoutParams();

        if (params instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) params).gravity = gravity != 0 ? gravity : Gravity.CENTER;
            contentFrame.setLayoutParams(params);
        }
    }

    /** Zoom + flip + rotation are applied together to the video surface. */
    private void applyVideoTransform() {
        if (mPlayerView == null) {
            return;
        }

        float scale = mZoomPercents / 100f;

        mPlayerView.setScaleX(mFlipEnabled ? -scale : scale);
        mPlayerView.setScaleY(scale);
        mPlayerView.setRotation(mRotationAngle);
    }

    private AspectRatioFrameLayout getContentFrame() {
        View view = mPlayerView != null
                ? mPlayerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_content_frame) : null;

        return view instanceof AspectRatioFrameLayout ? (AspectRatioFrameLayout) view : null;
    }
}
