package vn.phuong.iptvplayer;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

/**
 * Official YouTube-style floating 16:9 PiP miniplayer window in the bottom-right corner.
 * Uses SurfaceView-backed PlayerView (matching PlaybackActivity) for zero-stutter,
 * zero-desync hardware MediaCodec surface handoff.
 */
public final class MobileMiniPlayer {
    private static FrameLayout host;
    private static View surface;
    private static final String PLAYER = "com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity";

    private MobileMiniPlayer() { }

    public static void attach(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            Class<?> bridge = Class.forName(PLAYER);
            if (!Boolean.TRUE.equals(bridge.getMethod("isNm7MiniPlayerActive").invoke(null))) {
                remove();
                return;
            }

            ViewGroup root = activity.findViewById(android.R.id.content);
            if (!(root instanceof FrameLayout)) return;

            // If already attached to this root (e.g., returning from IPTV tab to BrowseActivity),
            // rebind once on the UI queue to clear sNm7SuspendedForIptv and resume smoothly.
            if (host != null && host.getParent() == root && surface != null) {
                host.bringToFront();
                final View existingSurface = surface;
                existingSurface.post(() -> {
                    if (surface != existingSurface || activity.isFinishing() || activity.isDestroyed()) return;
                    try {
                        bridge.getMethod("attachNm7MiniPlayer", View.class, View.class)
                                .invoke(null, existingSurface, existingSurface);
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        android.util.Log.e("NM7Playback", "Rebind existing mini PlayerView", error);
                    }
                });
                return;
            }

            float d = activity.getResources().getDisplayMetrics().density;
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;

            final FrameLayout next = new FrameLayout(activity);
            next.setBackgroundColor(0xFF0F0F0F);
            next.setElevation(6 * d);

            int width = Math.min(Math.round(204 * d), Math.round(screenWidth * 0.48f));
            int height = Math.round(width * 9f / 16f);
            int margin = Math.round(12 * d);
            int bottomMargin = Math.round(72 * d); // Cleanly above the 64dp bottom navigation bar

            FrameLayout.LayoutParams box = new FrameLayout.LayoutParams(
                    width, height, Gravity.BOTTOM | Gravity.END);
            box.bottomMargin = bottomMargin;
            box.rightMargin = margin;

            // 1. 16:9 Video Surface (SurfaceView-backed PlayerView matching PlaybackActivity)
            View video = createPlayerView(activity);
            video.setClickable(false);
            video.setFocusable(false);
            next.addView(video, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // Elevate inner SurfaceView to media overlay so it renders smoothly above window content
            final View innerSurface = findVideoSurfaceView(video);
            if (innerSurface instanceof android.view.SurfaceView) {
                ((android.view.SurfaceView) innerSurface).setZOrderMediaOverlay(true);
            }

            // 2. Tap & Drag Shield (placed below the corner buttons so buttons get direct clicks)
            View tapShield = new View(activity);
            tapShield.setClickable(true);
            tapShield.setFocusable(true);
            tapShield.setOnClickListener(v -> {
                try {
                    bridge.getMethod("restoreNm7Player").invoke(null);
                } catch (ReflectiveOperationException error) {
                    android.util.Log.e("NM7Playback", "Restore mini", error);
                }
            });
            tapShield.setOnTouchListener(new View.OnTouchListener() {
                float downRawX, downRawY, startTransX, startTransY;
                boolean dragging;
                final int slop = ViewConfiguration.get(activity).getScaledTouchSlop();

                @Override public boolean onTouch(View view, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            next.animate().cancel();
                            downRawX = event.getRawX();
                            downRawY = event.getRawY();
                            startTransX = next.getTranslationX();
                            startTransY = next.getTranslationY();
                            dragging = false;
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - downRawX;
                            float dy = event.getRawY() - downRawY;
                            if (!dragging && Math.hypot(dx, dy) > slop) {
                                dragging = true;
                            }
                            if (dragging) {
                                float minX = margin - next.getLeft();
                                float maxX = root.getWidth() - next.getWidth() - margin - next.getLeft();
                                float minY = margin - next.getTop();
                                float maxY = root.getHeight() - next.getHeight() - bottomMargin - next.getTop();
                                float targetX = Math.max(minX, Math.min(maxX, startTransX + dx));
                                float targetY = Math.max(minY, Math.min(maxY, startTransY + dy));
                                next.setTranslationX(targetX);
                                next.setTranslationY(targetY);
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            if (!dragging) {
                                view.performClick();
                            } else {
                                snapToNearestEdge(root, next, margin);
                            }
                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            if (dragging) {
                                snapToNearestEdge(root, next, margin);
                            }
                            return true;

                        default:
                            return true;
                    }
                }
            });
            next.addView(tapShield, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 3. Subtle center buffering spinner
            ProgressBar buffering = new ProgressBar(activity);
            buffering.setIndeterminate(true);
            buffering.setClickable(false);
            buffering.setVisibility(View.GONE);
            next.addView(buffering, new FrameLayout.LayoutParams(
                    Math.round(24 * d), Math.round(24 * d), Gravity.CENTER));

            // 4. Top-Left Circular Play/Pause Button (matches official YouTube floating PiP)
            int btnSize = Math.round(32 * d);
            int btnMargin = Math.round(6 * d);
            int btnPad = Math.round(6 * d);

            ImageButton playPause = new ImageButton(activity);
            GradientDrawable playBg = new GradientDrawable();
            playBg.setShape(GradientDrawable.OVAL);
            playBg.setColor(0x99000000);
            playPause.setBackground(playBg);
            playPause.setColorFilter(Color.WHITE);
            playPause.setScaleType(ImageView.ScaleType.FIT_CENTER);
            playPause.setPadding(btnPad, btnPad, btnPad, btnPad);
            try {
                boolean isPlaying = Boolean.TRUE.equals(bridge.getMethod("isNm7Playing").invoke(null));
                playPause.setImageResource(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                playPause.setContentDescription(isPlaying ? "Tạm dừng YouTube" : "Phát YouTube");
            } catch (Throwable ignored) {
                playPause.setImageResource(android.R.drawable.ic_media_pause);
                playPause.setContentDescription("Tạm dừng / Phát");
            }
            FrameLayout.LayoutParams playLp = new FrameLayout.LayoutParams(
                    btnSize, btnSize, Gravity.TOP | Gravity.START);
            playLp.leftMargin = btnMargin;
            playLp.topMargin = btnMargin;
            next.addView(playPause, playLp);

            // 5. Top-Right Circular Close (X) Button (matches official YouTube floating PiP)
            ImageButton close = new ImageButton(activity);
            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setShape(GradientDrawable.OVAL);
            closeBg.setColor(0x99000000);
            close.setBackground(closeBg);
            close.setColorFilter(Color.WHITE);
            close.setScaleType(ImageView.ScaleType.FIT_CENTER);
            close.setPadding(btnPad, btnPad, btnPad, btnPad);
            close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            close.setContentDescription("Đóng video YouTube");
            FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                    btnSize, btnSize, Gravity.TOP | Gravity.END);
            closeLp.rightMargin = btnMargin;
            closeLp.topMargin = btnMargin;
            next.addView(close, closeLp);

            // 6. Thin YouTube Red Progress Bar along the bottom edge
            ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(false);
            progress.setMax(1000);
            progress.setProgress(0);
            progress.setProgressTintList(ColorStateList.valueOf(0xFFFF0033));
            progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x44FFFFFF));
            FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Math.round(2.5f * d)), Gravity.BOTTOM);
            next.addView(progress, progressLp);

            // Button click handlers
            playPause.setOnClickListener(v -> {
                try {
                    bridge.getMethod("toggleNm7Playback").invoke(null);
                    boolean playing = Boolean.TRUE.equals(bridge.getMethod("isNm7Playing").invoke(null));
                    playPause.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                } catch (ReflectiveOperationException error) {
                    android.util.Log.e("NM7Playback", "Toggle mini playback", error);
                }
            });

            close.setOnClickListener(v -> {
                remove();
                MobileNm7Application.stopYoutubeForIptv();
            });

            Runnable refreshControl = new Runnable() {
                @Override public void run() {
                    if (host != next || !next.isAttachedToWindow()) return;
                    try {
                        int state = ((Number) bridge.getMethod("getNm7PlaybackState").invoke(null)).intValue();
                        buffering.setVisibility(state == 2 ? View.VISIBLE : View.GONE);
                        boolean playing = state != -1 && Boolean.TRUE.equals(bridge.getMethod("isNm7Playing").invoke(null));
                        playPause.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                        playPause.setContentDescription(playing ? "Tạm dừng YouTube" : "Phát YouTube");

                        long pos = ((Number) bridge.getMethod("getNm7VideoPosition").invoke(null)).longValue();
                        long dur = ((Number) bridge.getMethod("getNm7VideoDuration").invoke(null)).longValue();
                        if (dur > 0 && pos >= 0) {
                            progress.setProgress((int) Math.min(1000L, (pos * 1000L) / dur));
                        }
                    } catch (Throwable ignored) { }
                    next.postDelayed(this, 600L);
                }
            };

            root.addView(next, box);

            final FrameLayout previousHost = host;
            final View previousSurface = surface;
            host = next;
            surface = video;

            next.post(refreshControl);

            // Seamless hardware MediaCodec surface handoff:
            // CRITICAL: NEVER call switchTargetView before the new Surface is created and valid!
            // If switchTargetView runs with an invalid surface, ExoPlayer sets surface=null,
            // destroying the MediaCodec video decoder and forcing a 1.5s freeze until the next keyframe.
            final Runnable switchTargetAction = new Runnable() {
                private boolean executed = false;
                @Override public void run() {
                    if (executed) return;
                    executed = true;
                    if (host != next || surface != video
                            || activity.isFinishing() || activity.isDestroyed()) return;
                    try {
                        bridge.getMethod("attachNm7MiniPlayer", View.class, View.class)
                                .invoke(null, previousSurface, video);
                        if (previousHost != null && previousHost != next && previousHost.getParent() instanceof ViewGroup) {
                            ((ViewGroup) previousHost.getParent()).removeView(previousHost);
                        }
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        android.util.Log.e("NM7Playback", "Switch mini PlayerView", error);
                    }
                }
            };

            if (innerSurface instanceof android.view.SurfaceView) {
                final android.view.SurfaceHolder holder = ((android.view.SurfaceView) innerSurface).getHolder();
                if (holder.getSurface() != null && holder.getSurface().isValid()) {
                    video.post(switchTargetAction);
                } else {
                    holder.addCallback(new android.view.SurfaceHolder.Callback() {
                        @Override public void surfaceCreated(android.view.SurfaceHolder h) {
                            try { holder.removeCallback(this); } catch (Throwable ignored) {}
                            video.post(switchTargetAction);
                        }
                        @Override public void surfaceChanged(android.view.SurfaceHolder h, int f, int w, int h2) {}
                        @Override public void surfaceDestroyed(android.view.SurfaceHolder h) {}
                    });
                    video.postDelayed(switchTargetAction, 300L);
                }
            } else {
                video.post(switchTargetAction);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("NM7Playback", "Attach mini", error);
        }
    }

    private static void snapToNearestEdge(ViewGroup root, View view, int margin) {
        if (root == null || view == null) return;
        float currentX = view.getLeft() + view.getTranslationX();
        float centerX = currentX + view.getWidth() * 0.5f;
        float targetLeft = (centerX < root.getWidth() * 0.5f)
                ? margin
                : (root.getWidth() - view.getWidth() - margin);
        float targetTransX = targetLeft - view.getLeft();
        view.animate()
                .translationX(targetTransX)
                .setDuration(160L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private static View createPlayerView(Activity activity) throws ReflectiveOperationException {
        // SurfaceView-backed PlayerView keeps decoder-to-surface rendering stable across
        // mini/fullscreen handoff.
        return android.view.LayoutInflater.from(activity).inflate(R.layout.nm7_mini_player, null, false);
    }

    private static View findVideoSurfaceView(View playerView) {
        if (playerView == null) return null;
        try {
            java.lang.reflect.Method m = playerView.getClass().getMethod("getVideoSurfaceView");
            View v = (View) m.invoke(playerView);
            if (v != null) return v;
        } catch (Throwable ignored) {}
        if (playerView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) playerView;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof android.view.SurfaceView || child instanceof android.view.TextureView) {
                    return child;
                }
                if (child instanceof ViewGroup) {
                    View found = findVideoSurfaceView(child);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    public static View surfaceView() {
        return surface;
    }

    public static boolean containsPoint(float rawX, float rawY) {
        if (host == null || !host.isShown()) return false;
        android.graphics.Rect bounds = new android.graphics.Rect();
        return host.getGlobalVisibleRect(bounds) && bounds.contains((int) rawX, (int) rawY);
    }

    public static void remove(Activity activity) {
        if (host != null) {
            if (activity != null && host.getContext() != activity) {
                return;
            }
            if (host.getParent() instanceof ViewGroup) {
                ((ViewGroup) host.getParent()).removeView(host);
            }
            host = null;
            surface = null;
        }
    }

    public static void remove() {
        remove(null);
    }
}
