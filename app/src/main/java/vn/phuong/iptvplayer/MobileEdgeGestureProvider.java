package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.media3.ui.PlayerView;

/**
 * Mobile-only edge gestures for the inline player.
 * Left edge vertical swipe controls screen brightness; right edge controls media volume.
 */
@androidx.media3.common.util.UnstableApi
public final class MobileEdgeGestureProvider extends ContentProvider implements Application.ActivityLifecycleCallbacks {
    private static final int EDGE_NONE = 0;
    private static final int EDGE_BRIGHTNESS = 1;
    private static final int EDGE_VOLUME = 2;
    private static final float EDGE_FRACTION = 0.18f;

    private static MobileEdgeGestureProvider instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MainActivity activity;
    private PlayerView playerView;
    private TextView feedback;
    private int activeEdge = EDGE_NONE;
    private float downY;
    private float startBrightness;
    private int startVolume;
    private int maxVolume;
    private AudioManager audioManager;
    private int watchAttempts;

    private final Runnable attachWatch = new Runnable() {
        @Override public void run() {
            MainActivity current = activity;
            if (current == null || current.isFinishing() || current.isDestroyed()) return;
            if (AppPreferences.isTvInterface(current)) {
                detachPlayerView();
                return;
            }
            PlayerView found = findPlayerView(current.findViewById(android.R.id.content));
            if (found != null) {
                if (found != playerView) attachTo(found);
                return; // Stop polling once PlayerView is attached.
            }
            if (++watchAttempts < 6) {
                main.postDelayed(this, 300L);
            }
        }
    };

    static void onPlayerViewCreated(MainActivity mainActivity, PlayerView view) {
        MobileEdgeGestureProvider owner = instance;
        if (owner == null || mainActivity == null || view == null || AppPreferences.isTvInterface(mainActivity)) return;
        owner.activity = mainActivity;
        if (owner.audioManager == null) {
            owner.audioManager = (AudioManager) mainActivity.getSystemService(Context.AUDIO_SERVICE);
        }
        owner.main.removeCallbacks(owner.attachWatch);
        owner.attachTo(view);
    }

        private static boolean lifecycleRegistered;

    static synchronized void init(Application app) {
        if (lifecycleRegistered || app == null) return;
        
        lifecycleRegistered = true;
        MobileEdgeGestureProvider owner = new MobileEdgeGestureProvider();
        instance = owner;
        app.registerActivityLifecycleCallbacks(owner);
    }

    @Override public boolean onCreate() {
        if (getContext() != null) {
            init((Application) getContext().getApplicationContext());
        }
        return true;
    }

    @Override public void onActivityResumed(Activity resumed) {
        if (!(resumed instanceof MainActivity)) return;
        activity = (MainActivity) resumed;
        audioManager = (AudioManager) resumed.getSystemService(Context.AUDIO_SERVICE);
        watchAttempts = 0;
        main.removeCallbacks(attachWatch);
        main.post(attachWatch);
    }

    @Override public void onActivityPaused(Activity paused) {
        if (paused != activity) return;
        main.removeCallbacks(attachWatch);
        activeEdge = EDGE_NONE;
    }

    @Override public void onActivityDestroyed(Activity destroyed) {
        if (destroyed != activity) return;
        main.removeCallbacks(attachWatch);
        detachPlayerView();
        activity = null;
        audioManager = null;
    }

    private void attachTo(PlayerView view) {
        detachPlayerView();
        playerView = view;
        ensureFeedback(view);
        view.setOnTouchListener((v, event) -> handleTouch((PlayerView) v, event));
    }

    private boolean handleTouch(PlayerView view, MotionEvent event) {
        MainActivity current = activity;
        if (current == null || AppPreferences.isTvInterface(current)) return false;
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (event.getX() <= width * EDGE_FRACTION) {
                    activeEdge = EDGE_BRIGHTNESS;
                    downY = event.getY();
                    startBrightness = currentBrightness(current);
                    return true;
                }
                if (event.getX() >= width * (1f - EDGE_FRACTION)) {
                    activeEdge = EDGE_VOLUME;
                    downY = event.getY();
                    if (audioManager != null) {
                        maxVolume = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    }
                    return true;
                }
                activeEdge = EDGE_NONE;
                return false;

            case MotionEvent.ACTION_MOVE:
                if (activeEdge == EDGE_NONE) return false;
                float delta = (downY - event.getY()) / Math.max(1f, height);
                if (activeEdge == EDGE_BRIGHTNESS) {
                    float value = clamp(startBrightness + delta * 1.25f, 0.02f, 1f);
                    WindowManager.LayoutParams params = current.getWindow().getAttributes();
                    params.screenBrightness = value;
                    current.getWindow().setAttributes(params);
                    showFeedback("Độ sáng  " + Math.round(value * 100f) + "%");
                } else if (audioManager != null) {
                    int value = Math.round(startVolume + delta * maxVolume * 1.25f);
                    value = Math.max(0, Math.min(maxVolume, value));
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
                    showFeedback("Âm lượng  " + Math.round(value * 100f / maxVolume) + "%");
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeEdge == EDGE_NONE) return false;
                activeEdge = EDGE_NONE;
                hideFeedbackLater();
                return true;

            default:
                return activeEdge != EDGE_NONE;
        }
    }

    private static float currentBrightness(Activity activity) {
        float value = activity.getWindow().getAttributes().screenBrightness;
        if (value >= 0f) return clamp(value, 0.02f, 1f);
        try {
            int system = Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            return clamp(system / 255f, 0.02f, 1f);
        } catch (Exception ignored) {
            return 0.5f;
        }
    }

    private void ensureFeedback(PlayerView view) {
        if (!(view.getParent() instanceof FrameLayout) || activity == null) return;
        FrameLayout parent = (FrameLayout) view.getParent();
        feedback = new TextView(activity);
        feedback.setTextColor(Color.WHITE);
        feedback.setTextSize(16f);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(18), dp(10), dp(18), dp(10));
        feedback.setVisibility(View.GONE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xC9000000);
        background.setCornerRadius(dp(12));
        feedback.setBackground(background);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        parent.addView(feedback, params);
    }

    private void showFeedback(String text) {
        if (feedback == null) return;
        main.removeCallbacks(hideFeedback);
        feedback.setText(text);
        feedback.setVisibility(View.VISIBLE);
        feedback.bringToFront();
    }

    private final Runnable hideFeedback = () -> {
        if (feedback != null) feedback.setVisibility(View.GONE);
    };

    private void hideFeedbackLater() {
        main.removeCallbacks(hideFeedback);
        main.postDelayed(hideFeedback, 650L);
    }

    private void detachPlayerView() {
        main.removeCallbacks(hideFeedback);
        activeEdge = EDGE_NONE;
        if (playerView != null) playerView.setOnTouchListener(null);
        if (feedback != null && feedback.getParent() instanceof ViewGroup) {
            ((ViewGroup) feedback.getParent()).removeView(feedback);
        }
        playerView = null;
        feedback = null;
    }

    private static PlayerView findPlayerView(View view) {
        if (view instanceof PlayerView) return (PlayerView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            PlayerView found = findPlayerView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private int dp(int value) {
        MainActivity current = activity;
        if (current == null) return value;
        return Math.round(value * current.getResources().getDisplayMetrics().density);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityStarted(Activity a) { }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String sort) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
