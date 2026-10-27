    private static PlaybackActivity sNm7Active;
    private static boolean sNm7Mini;
    private static boolean sNm7RestorePending;
    private static boolean sNm7ResumeAfterIptv;
    private static boolean sNm7SuspendedForIptv;
    private boolean mNm7Stopped;
    private boolean mNm7LeavingForMini;
    private boolean mNm7TargetRestoreWaiting;
    private boolean mNm7ResumeAfterTarget;
    private com.google.android.exoplayer2.video.VideoListener mNm7FirstFrameListener;
    private Runnable mNm7TargetRestoreTimeout;
    private long mNm7TargetRestoreStarted;
    private com.google.android.exoplayer2.ui.PlayerView mNm7VideoTarget;
    private boolean mNm7OwnsPlayback;
    private Object mNm7BackCallback;
    private com.google.android.exoplayer2.SimpleExoPlayer mNm7ObservedPlayer;
    private com.google.android.exoplayer2.Player.EventListener mNm7PlaybackObserver;
    private com.google.android.exoplayer2.video.VideoListener mNm7FrameObserver;
    private long mNm7LastFirstFrameMs;
    private long mNm7LastErrorRetryMs;
    private int mNm7DecoderRecoveryAttempts;
    private int mNm7RenderRecoveryAttempts;

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

    private void recoverNm7DecoderError(com.google.android.exoplayer2.ExoPlaybackException error) {
        if (mPlayer == null || mNm7Stopped || sNm7SuspendedForIptv || isFinishing()) return;
        if (!isNm7DecoderError(error)) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - mNm7LastErrorRetryMs < 1500L) return;
        mNm7LastErrorRetryMs = now;
        mNm7DecoderRecoveryAttempts++;
        long position = Math.max(0L, mPlayer.getCurrentPosition());
        try {
            android.util.Log.w("NM7Playback",
                    "decoder_recovery attempt=" + mNm7DecoderRecoveryAttempts
                            + " state=" + mPlayer.getPlaybackState()
                            + " position=" + position);

            if (mNm7DecoderRecoveryAttempts == 3 && mNm7TrackSelector != null) {
                try {
                    mNm7TrackSelector.setParameters(
                            mNm7TrackSelector.buildUponParameters()
                                    .setMaxVideoSize(1280, 720)
                                    .setMaxVideoBitrate(4_000_000)
                                    .setExceedVideoConstraintsIfNecessary(false));
                } catch (RuntimeException ignored) { }
            }

            if (mNm7DecoderRecoveryAttempts <= 3) {
                mPlayer.retry();
                mPlayer.seekTo(position);
                mPlayer.setPlayWhenReady(true);
                bindNm7PlayerTarget();
            } else {
                mNm7DecoderRecoveryAttempts = 0;
                restartEngine();
            }
        } catch (RuntimeException recoveryError) {
            android.util.Log.w("NM7Playback", "decoder recovery failed", recoveryError);
        }
    }

    private void ensureNm7PlaybackObserver() {
        if (mPlayer == null || mPlayer == mNm7ObservedPlayer) return;
        if (mNm7ObservedPlayer != null && mNm7PlaybackObserver != null) {
            try { mNm7ObservedPlayer.removeListener(mNm7PlaybackObserver); } catch (RuntimeException ignored) { }
        }
        if (mNm7ObservedPlayer != null && mNm7FrameObserver != null) {
            try { mNm7ObservedPlayer.removeVideoListener(mNm7FrameObserver); } catch (RuntimeException ignored) { }
        }

        mNm7ObservedPlayer = mPlayer;
        mNm7LastFirstFrameMs = 0L;
        mNm7DecoderRecoveryAttempts = 0;
        mNm7RenderRecoveryAttempts = 0;

        mNm7PlaybackObserver = new com.google.android.exoplayer2.Player.EventListener() {
            // Decoder errors are routed through Nm7EngineErrorOwner so the presenter and
            // decoder recovery never race each other.
            @Override public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == com.google.android.exoplayer2.Player.STATE_READY) {
                    mNm7DecoderRecoveryAttempts = 0;
                    if (playWhenReady) armNm7RenderWatchdog();
                }
            }
        };
        mNm7FrameObserver = new com.google.android.exoplayer2.video.VideoListener() {
            @Override public void onRenderedFirstFrame() {
                mNm7LastFirstFrameMs = android.os.SystemClock.elapsedRealtime();
                mNm7RenderRecoveryAttempts = 0;
                mNm7DecoderRecoveryAttempts = 0;
            }
        };
        mPlayer.addListener(mNm7PlaybackObserver);
        mPlayer.addVideoListener(mNm7FrameObserver);
    }

    private void armNm7RenderWatchdog() {
        if (mPlayer == null || mNm7Stopped) return;
        final PlaybackActivity owner = this;
        final long started = android.os.SystemClock.elapsedRealtime();
        mHandler.postDelayed(() -> {
            if (owner.mPlayer == null || owner.mNm7Stopped || owner.mPlayer != mPlayer) return;
            int state = owner.mPlayer.getPlaybackState();
            boolean wantsPlay = owner.mPlayer.getPlayWhenReady();
            if (wantsPlay && (state == Player.STATE_READY || state == Player.STATE_BUFFERING)
                    && owner.mNm7LastFirstFrameMs < started) {
                owner.mNm7RenderRecoveryAttempts++;
                android.util.Log.w("NM7Playback", "render_watchdog_no_first_frame_ms="
                        + (android.os.SystemClock.elapsedRealtime() - started)
                        + " attempt=" + owner.mNm7RenderRecoveryAttempts);
                try {
                    long position = Math.max(0L, owner.mPlayer.getCurrentPosition());
                    owner.bindNm7PlayerTarget();
                    owner.mPlayer.seekTo(position);
                    owner.mPlayer.setPlayWhenReady(true);
                    if (owner.mNm7RenderRecoveryAttempts >= 3) {
                        owner.mNm7RenderRecoveryAttempts = 0;
                        owner.restartEngine();
                    }
                } catch (RuntimeException error) {
                    android.util.Log.w("NM7Playback", "render watchdog recovery failed", error);
                }
            }
        }, 1800L);
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

    public static boolean isNm7Playing() {
        return isNm7SessionActive() && sNm7Active.mPlayer.getPlayWhenReady();
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
        if (!isNm7SessionActive() || sNm7SuspendedForIptv) return;
        PlaybackActivity active = sNm7Active;
        if (active.mPlayer.getPlaybackError() != null) {
            active.mPlayer.retry();
            active.mPlayer.setPlayWhenReady(true);
        } else if (active.mPlayer.getPlaybackState() == Player.STATE_ENDED) {
            recoverNm7EndedPlayback(active);
            active.mPlayer.setPlayWhenReady(true);
        } else active.mPlayer.setPlayWhenReady(!active.mPlayer.getPlayWhenReady());
    }

    public static int getNm7PlaybackState() {
        if (!isNm7SessionActive()) return -1;
        if (sNm7Active.mPlayer.getPlaybackError() != null) return -1;
        return sNm7Active.mPlayer.getPlaybackState();
    }

    public static boolean isNm7SessionActive() {
        return sNm7Active != null && !sNm7Active.mNm7Stopped && !sNm7Active.isDestroyed()
                && !sNm7Active.isFinishing() && sNm7Active.mPlayer != null;
    }

    public static boolean isNm7MiniPlayerActive() {
        return sNm7Mini && sNm7Active != null && !sNm7Active.isDestroyed()
                && !sNm7Active.isFinishing() && sNm7Active.mPlayer != null;
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
        // A delayed MainActivity attach must not take playback back from IPTV.
        if (sNm7SuspendedForIptv) {
            try {
                Object tab = Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                        .getMethod("tab", android.content.Context.class).invoke(null, active);
                if (!"youtube".equals(tab)) return;
            } catch (ReflectiveOperationException error) { return; }
            sNm7SuspendedForIptv = false;
        }

        active.blockEngine(false);
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
        // Track the target in the player owner, not in a potentially stale overlay host.
        com.google.android.exoplayer2.ui.PlayerView oldTarget = active.mNm7VideoTarget;
        if (oldTarget == null) oldTarget = active.mPlayerView;
        com.google.android.exoplayer2.ui.PlayerView.switchTargetView(active.mPlayer, oldTarget, mini);
        active.mNm7VideoTarget = mini;
        android.util.Log.i("NM7Playback", "target=mini state=" + active.mPlayer.getPlaybackState());

        if (sNm7ResumeAfterIptv) {
            sNm7ResumeAfterIptv = false;
            active.mPlayer.setPlayWhenReady(true);
        }
    }

    public static void restoreNm7Player() {
        PlaybackActivity active = sNm7Active;
        if (active == null || active.isDestroyed() || active.isFinishing() || active.mPlayer == null) return;
        try {
            Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("setTab", android.content.Context.class, String.class)
                    .invoke(null, active, "youtube");
        } catch (Throwable ignored) {}
        boolean ended = active.mPlayer.getPlaybackState() == Player.STATE_ENDED;
        if (ended) recoverNm7EndedPlayback(active);

        // Do not switch the video target while PlaybackActivity is still stopped.
        // Bring it to foreground first; onResume completes the surface handoff.
        if (sNm7RestorePending) return;
        active.beginNm7TargetRestore();
        sNm7RestorePending = true;
        active.startActivity(new Intent(active, active.getClass())
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
    }

    private void completeNm7RestoreOnResume() {
        if (mPlayer == null) return;


        sNm7SuspendedForIptv = false;
        com.google.android.exoplayer2.ui.PlayerView oldTarget = mNm7VideoTarget;
        if (oldTarget == null) {
            try {
                android.view.View view = (android.view.View) Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer")
                        .getMethod("surfaceView").invoke(null);
                if (view instanceof com.google.android.exoplayer2.ui.PlayerView)
                    oldTarget = (com.google.android.exoplayer2.ui.PlayerView) view;
            } catch (ReflectiveOperationException error) {
                android.util.Log.e("NM7Playback", "Read mini target", error);
            }
        }
        if (oldTarget != mPlayerView && !mNm7TargetRestoreWaiting) beginNm7TargetRestore();
        if (mNm7TargetRestoreWaiting) armNm7FirstFrameResume();
        if (oldTarget != mPlayerView) {
            com.google.android.exoplayer2.ui.PlayerView.switchTargetView(mPlayer, oldTarget, mPlayerView);
        } else if (mPlayerView.getPlayer() != mPlayer) {
            mPlayerView.setPlayer(mPlayer);
        }
        mNm7VideoTarget = mPlayerView;
        sNm7RestorePending = false;
        sNm7Mini = false;
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
        if (sNm7SuspendedForIptv) return; // Do not overwrite the saved playing intent.
        sNm7SuspendedForIptv = true;

        // IPTV may own playback now, but keep the YouTube session and exact video/position alive.
        sNm7Mini = true;
        sNm7ResumeAfterIptv = active.mPlayer.getPlayWhenReady() || active.mNm7ResumeAfterTarget;
        active.cancelNm7TargetRestore(false);
        active.mNm7Stopped = false;
        active.mNm7OwnsPlayback = false;
        active.blockEngine(true);
        active.mPlayer.setPlayWhenReady(false);
        if (active.mNm7VideoTarget != null) active.mNm7VideoTarget.setPlayer(null);
        active.mNm7VideoTarget = null;
        active.mPlayerView.setPlayer(null);
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
        System.setProperty("nm7.youtube.background", enabled ? "1" : "0");
        try {
            Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("setYoutubeBackground", android.content.Context.class, boolean.class)
                    .invoke(null, this, enabled);
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("NM7Playback", "Background session bridge failed", error);
        }
        if (enabled && mNm7OwnsPlayback) {
            try {
                Intent service = new Intent().setClassName(this, "vn.phuong.iptvplayer.BackgroundPlaybackService");
                service.putExtra("youtube", true);
                service.putExtra("channel_name", "YouTube");
                if (VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            } catch (RuntimeException error) {
                android.util.Log.e("NM7Playback", "Background service failed", error);
            }
        }
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

