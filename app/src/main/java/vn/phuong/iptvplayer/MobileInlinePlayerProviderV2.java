package vn.phuong.iptvplayer;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

/** Mobile inline player with DVR seekbar, clean tap controls, fullscreen icons and background playback. */
@UnstableApi
public final class MobileInlinePlayerProviderV2 extends ContentProvider implements Application.ActivityLifecycleCallbacks {
    private MainActivity currentActivity;
    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout videoContainer;
    private FrameLayout fullscreenHost;
    private LinearLayout panel;
    private TextView title;
    private TextView programme;
    private TextView stats;
    private android.widget.Button liveServerButton;
    private android.widget.Button scheduleButton;
    private android.widget.Button returnToLiveButton;
    private EpgStore.Programme activeCatchupProgramme;
    private android.widget.Button fullscreenLiveServerButton;
    private ImageButton fullscreenButton;
    private ListView channelList;
    private AdapterView.OnItemClickListener originalClick;
    private Channel currentChannel;
    private DecoderCounters videoCounters;
    private final FpsMeter fpsMeter = new FpsMeter();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WifiManager.MulticastLock multicastLock;
    private int playGeneration;
    private int recoveryAttempts;
    private String resolutionText = "Độ phân giải: —";
    private Window wrappedWindow;
    private Window.Callback originalWindowCallback;
    private Object backDispatcher33;
    private Object backCallback33;
    private boolean backgroundActive;
    private boolean resumeAfterLifecyclePause;
    private boolean lifecyclePauseInProgress;
    private boolean userPaused;
    private long bufferingSinceMs;
    private int lastRenderedBufferCount = -1;
    private long lastFrameProgressMs;
    private long lastNetworkProgressMs;
    private long stablePlaybackSinceMs;
    private boolean fullscreen;
    private long requestedResumePositionMs;
    private boolean requestedResumePlaying = true;
    private View.OnLayoutChangeListener rootLayoutListener;

    private static final OkHttpClient STREAM_HTTP = NetworkClient.STREAM_HTTP;

    private final Runnable statsTick = new Runnable() {
        @Override public void run() {
            updateStatsNow();
            updateSeekUi();
            monitorPlaybackHealth();
            if (player != null) mainHandler.postDelayed(this, 2000);
        }
    };

        private static boolean lifecycleRegistered;

    static synchronized void init(Application app) {
        if (lifecycleRegistered || app == null) return;
        
        lifecycleRegistered = true;
        MobileInlinePlayerProviderV2 owner = new MobileInlinePlayerProviderV2();
        instance = owner;
        app.registerActivityLifecycleCallbacks(owner);
    }

    @Override public boolean onCreate() {
        if (getContext() != null) {
            init((Application) getContext().getApplicationContext());
        }
        return true;
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        if (SharedPlaybackSession.TAB_YOUTUBE.equals(SharedPlaybackSession.tab(activity))) return;
        MainActivity main = (MainActivity) activity;
        if (AppPreferences.isTvInterface(main)) {
            if (currentActivity == main) detach();
            return;
        }
        if (currentActivity != main || panel == null || panel.getParent() == null) attach(main);
        // Returning from YouTube is a handoff, not a fresh navigation. Recreate the
        // IPTV player after MainActivity is actually resumed so the old decoder cannot
        // race the Activity transition.
        if (resumeAfterYoutube && currentChannel != null) {
            resumeAfterYoutube = false;
            mainHandler.postDelayed(() -> {
                if (currentActivity == main
                        && SharedPlaybackSession.TAB_IPTV.equals(SharedPlaybackSession.tab(main))
                        && currentChannel != null) {
                    long position = pendingResumePositionMs;
                    boolean playing = pendingResumePlaying;
                    pendingResumePositionMs = 0L;
                    pendingResumePlaying = false;
                    playInline(currentChannel, position, playing);
                }
            }, 120L);
        }
        if (!MobileNm7Application.isYoutubeSessionActive()) stopBackgroundService(main);
        backgroundActive = false;
        if (player != null) {
            player.setWakeMode(C.WAKE_MODE_NONE);
            if (playerView != null && playerView.getPlayer() != player) {
                playerView.setPlayer(player);
            }
            if (resumeAfterLifecyclePause && !userPaused) {
                if (player.isCurrentMediaItemLive()) {
                    player.seekToDefaultPosition();
                }
                player.play();
            }
        }
        resumeAfterLifecyclePause = false;
        mainHandler.postDelayed(() -> lifecyclePauseInProgress = false, 1_000);
        if (panel != null && panel.getVisibility() == View.VISIBLE) keepScreenAwake(true);
        mainHandler.post(this::syncOrientationUi);
    }

    private void attach(MainActivity activity) {
        detach();
        currentActivity = activity;
        LinearLayout root = activity.findViewById(R.id.mainRoot);
        channelList = activity.findViewById(R.id.listChannels);
        fullscreenHost = activity.findViewById(android.R.id.content);
        if (root == null || channelList == null || fullscreenHost == null) return;

        panel = new LinearLayout(activity);
        panel.setTag("nm7_inline_player");
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(View.GONE);
        panel.setBackgroundResource(R.drawable.panel);
        panel.setPadding(0, 0, 0, dp(activity, 7));

        videoContainer = new FrameLayout(activity);
        videoContainer.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(activity);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(false);
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerShowTimeoutMs(4500);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        playerView.setKeepContentOnPlayerReset(true);
        videoContainer.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        hideControllerView("exo_prev");
        hideControllerView("exo_next");
        hideControllerView("exo_rew");
        hideControllerView("exo_ffwd");
        installFullscreenButton(activity);

        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            if (visibility == View.VISIBLE) {
                updateSeekUi();
                updateFullscreenIcon();
            }
        });

        int videoHeight = calculateVideoHeight(activity, activity.getResources().getDisplayMetrics().widthPixels);
        panel.addView(videoContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, videoHeight));
        videoContainer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (fullscreen) return;
            int width = right - left;
            if (width <= 0) return;
            int wanted = calculateVideoHeight(activity, width);
            ViewGroup.LayoutParams params = videoContainer.getLayoutParams();
            if (params != null && params.height != wanted) {
                params.height = wanted;
                videoContainer.setLayoutParams(params);
            }
        });

        title = new TextView(activity);
        title.setTextSize(17);
        title.setTextColor(activity.getColor(R.color.text_primary));
        title.setMaxLines(1);
        title.setPadding(dp(activity, 12), dp(activity, 7), dp(activity, 12), 0);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        programme = new TextView(activity);
        programme.setTextSize(14);
        programme.setTextColor(activity.getColor(R.color.text_secondary));
        programme.setMaxLines(2);
        programme.setPadding(dp(activity, 12), dp(activity, 2), dp(activity, 12), 0);
        panel.addView(programme, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        stats = new TextView(activity);
        stats.setTextSize(13);
        stats.setTextColor(activity.getColor(R.color.accent));
        stats.setText("Độ phân giải: —  •  FPS: —");
        stats.setPadding(dp(activity, 12), dp(activity, 2), dp(activity, 12), 0);
        panel.addView(stats, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionsRow = new LinearLayout(activity);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsParams.setMargins(dp(activity, 10), dp(activity, 4), dp(activity, 10), 0);

        scheduleButton = new android.widget.Button(activity);
        scheduleButton.setAllCaps(false);
        scheduleButton.setTextSize(12);
        scheduleButton.setTextColor(activity.getColor(R.color.accent));
        scheduleButton.setBackgroundResource(R.drawable.button_secondary);
        scheduleButton.setText("📅 Lịch phát sóng");
        scheduleButton.setOnClickListener(v -> {
            if (currentActivity != null) currentActivity.toggleSchedulePanel();
        });
        LinearLayout.LayoutParams schedParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(activity, 38));
        schedParams.setMarginEnd(dp(activity, 8));
        actionsRow.addView(scheduleButton, schedParams);

        returnToLiveButton = new android.widget.Button(activity);
        returnToLiveButton.setAllCaps(false);
        returnToLiveButton.setTextSize(12);
        returnToLiveButton.setTextColor(activity.getColor(R.color.live_accent));
        returnToLiveButton.setBackgroundResource(R.drawable.button_secondary);
        returnToLiveButton.setText("🔴 Về trực tiếp");
        returnToLiveButton.setVisibility(View.GONE);
        returnToLiveButton.setOnClickListener(v -> returnToLiveInternal());
        LinearLayout.LayoutParams liveReturnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(activity, 38));
        liveReturnParams.setMarginEnd(dp(activity, 8));
        actionsRow.addView(returnToLiveButton, liveReturnParams);

        liveServerButton = new android.widget.Button(activity);
        liveServerButton.setAllCaps(false);
        liveServerButton.setTextSize(12);
        liveServerButton.setTextColor(activity.getColor(R.color.live_accent));
        liveServerButton.setBackgroundResource(R.drawable.button_secondary);
        liveServerButton.setText("Đổi máy chủ");
        liveServerButton.setVisibility(View.GONE);
        liveServerButton.setOnClickListener(v -> showLiveServerPicker());
        LinearLayout.LayoutParams serverParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(activity, 38));
        actionsRow.addView(liveServerButton, serverParams);

        panel.addView(actionsRow, actionsParams);

        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panelParams.setMarginStart(-root.getPaddingLeft());
        panelParams.setMarginEnd(-root.getPaddingRight());
        root.setClipToPadding(false);
        root.addView(panel, Math.min(1, root.getChildCount()), panelParams);

        originalClick = channelList.getOnItemClickListener();
        channelList.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getAdapter().getItem(position);
            if (item instanceof Channel) playInline((Channel) item);
            else if (item instanceof LiveEvent && originalClick != null) {
                originalClick.onItemClick(parent, view, position, id);
            } else if (item instanceof LiveEvent) {
                playInline(((LiveEvent) item).primary());
            } else if (originalClick != null) {
                originalClick.onItemClick(parent, view, position, id);
            }
        });

        rootLayoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                mainHandler.post(this::syncOrientationUi);
        fullscreenHost.addOnLayoutChangeListener(rootLayoutListener);
        installBackHandling(activity);
        MobileEdgeGestureProvider.onPlayerViewCreated(activity, playerView);
    }

    private void hideControllerView(String resourceName) {
        if (currentActivity == null || playerView == null) return;
        int id = currentActivity.getResources().getIdentifier(resourceName, "id", currentActivity.getPackageName());
        if (id != 0) {
            View view = playerView.findViewById(id);
            if (view != null) view.setVisibility(View.GONE);
        }
    }

    private void setControllerViewVisible(String resourceName, boolean visible) {
        if (currentActivity == null || playerView == null) return;
        int id = currentActivity.getResources().getIdentifier(resourceName, "id", currentActivity.getPackageName());
        if (id != 0) {
            View view = playerView.findViewById(id);
            if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void installFullscreenButton(Activity activity) {
        if (playerView == null) return;
        int settingsId = activity.getResources().getIdentifier("exo_settings", "id", activity.getPackageName());
        if (settingsId == 0) return;
        View settings = playerView.findViewById(settingsId);
        if (settings == null || !(settings.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) settings.getParent();

        fullscreenButton = new ImageButton(activity);
        fullscreenButton.setBackgroundColor(Color.TRANSPARENT);
        fullscreenButton.setImageResource(R.drawable.ic_nm7_fullscreen);
        fullscreenButton.setColorFilter(Color.WHITE);
        fullscreenButton.setContentDescription("Toàn màn hình");
        fullscreenButton.setPadding(dp(activity, 11), dp(activity, 11), dp(activity, 11), dp(activity, 11));
        fullscreenButton.setOnClickListener(v -> rotateScreen());

        ViewGroup.LayoutParams params;
        if (parent instanceof LinearLayout) {
            params = new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48));
        } else {
            params = new ViewGroup.LayoutParams(dp(activity, 48), dp(activity, 48));
        }
        int index = parent.indexOfChild(settings);
        parent.addView(fullscreenButton, Math.max(0, index), params);

        // Overlay the Live selector in the Media3 controller so it remains available
        // over the video in fullscreen and follows the controller's show/hide behavior.
        fullscreenLiveServerButton = new android.widget.Button(activity);
        fullscreenLiveServerButton.setAllCaps(false);
        fullscreenLiveServerButton.setSingleLine(true);
        fullscreenLiveServerButton.setTextSize(11);
        fullscreenLiveServerButton.setTextColor(activity.getColor(R.color.live_accent));
        fullscreenLiveServerButton.setBackgroundResource(R.drawable.button_secondary);
        fullscreenLiveServerButton.setText("Server ▾");
        fullscreenLiveServerButton.setContentDescription("Đổi máy chủ phát sóng");
        fullscreenLiveServerButton.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        fullscreenLiveServerButton.setVisibility(View.GONE);
        fullscreenLiveServerButton.setOnClickListener(v -> showLiveServerPicker());
        ViewGroup.LayoutParams serverParams;
        if (parent instanceof LinearLayout) {
            serverParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 48));
        } else {
            serverParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 48));
        }
        int serverIndex = parent.indexOfChild(settings);
        parent.addView(fullscreenLiveServerButton, Math.max(0, serverIndex), serverParams);
    }

    private void updateFullscreenIcon() {
        if (fullscreenButton == null) return;
        fullscreenButton.setImageResource(fullscreen ? R.drawable.ic_nm7_fullscreen_exit : R.drawable.ic_nm7_fullscreen);
        fullscreenButton.setContentDescription(fullscreen ? "Thu nhỏ" : "Toàn màn hình");
    }

    private void updateLiveServerButton(Channel channel) {
        if (liveServerButton == null) return;
        LiveEvent event = LivePlaybackSession.activeFor(channel);
        if (event == null || event.sources().size() < 2) {
            LivePlaybackSession.clearIfNotLiveSource(channel);
            liveServerButton.setVisibility(View.GONE);
            updateFullscreenLiveServerButton(channel);
            return;
        }
        LivePlaybackSession.setActive(event);
        int selected = event.indexOfUrl(channel.url());
        liveServerButton.setText("Server " + (selected + 1) + "/" + event.sources().size() + "  ▾");
        liveServerButton.setContentDescription("Đang phát " + event.sourceLabel(selected) + ". Nhấn để đổi máy chủ");
        liveServerButton.setVisibility(View.VISIBLE);
        updateFullscreenLiveServerButton(channel);
    }

    private void updateFullscreenLiveServerButton(Channel channel) {
        if (fullscreenLiveServerButton == null) return;
        LiveEvent event = LivePlaybackSession.activeFor(channel);
        if (!fullscreen || channel == null || event == null || event.sources().size() < 2) {
            fullscreenLiveServerButton.setVisibility(View.GONE);
            return;
        }
        int selected = event.indexOfUrl(channel.url());
        if (selected < 0) {
            fullscreenLiveServerButton.setVisibility(View.GONE);
            return;
        }
        fullscreenLiveServerButton.setText(
                "Server " + (selected + 1) + "/" + event.sources().size() + "  ▾");
        fullscreenLiveServerButton.setContentDescription(
                "Đang phát " + event.sourceLabel(selected) + ". Nhấn để đổi máy chủ");
        fullscreenLiveServerButton.setVisibility(View.VISIBLE);
    }

    private void showLiveServerPicker() {
        LiveEvent event = LivePlaybackSession.activeFor(currentChannel);
        if (event == null || event.sources().size() < 2 || currentActivity == null) return;
        String[] labels = new String[event.sources().size()];
        int selected = event.indexOfUrl(currentChannel.url());
        for (int i = 0; i < labels.length; i++) {
            labels[i] = event.sourceLabel(i) + (i == selected ? "  ✓ Đang phát" : "");
        }
        new android.app.AlertDialog.Builder(currentActivity)
                .setTitle("Chọn máy chủ • " + event.title())
                .setSingleChoiceItems(labels, Math.max(0, selected), (dialog, which) -> {
                    if (which < 0 || which >= event.sources().size()) return;
                    Channel source = event.sources().get(which);
                    LiveEventStore.setSelectedUrl(currentActivity, event.id(), source.url());
                    LivePlaybackSession.setActive(event.withPreferredUrl(source.url()));
                    dialog.dismiss();
                    playInline(source);
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void updateSeekUi() {
        boolean seekable = player != null
                && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                && player.isCurrentMediaItemSeekable();
        setControllerViewVisible("exo_progress", seekable);
        setControllerViewVisible("exo_position", seekable);
        setControllerViewVisible("exo_duration", seekable);
    }

    private int calculateVideoHeight(Activity activity, int width) {
        return Math.max(dp(activity, 180), width * 9 / 16);
    }

    private void rotateScreen() {
        if (currentActivity == null || videoContainer == null) return;
        if (fullscreen) exitFullscreen(true);
        else enterFullscreen(true);
    }

    private void enterFullscreen(boolean requestLandscape) {
        if (currentActivity == null || videoContainer == null || fullscreenHost == null || fullscreen) return;
        ViewGroup parent = (ViewGroup) videoContainer.getParent();
        if (parent != null) parent.removeView(videoContainer);
        fullscreen = true;
        updateFullscreenLiveServerButton(currentChannel);
        FrameLayout.LayoutParams full = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        fullscreenHost.addView(videoContainer, full);
        videoContainer.bringToFront();
        HomeTabBar.setVisible(currentActivity, false);
        hideSystemBars();
        updateFullscreenIcon();
        if (requestLandscape) currentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void exitFullscreen(boolean requestPortrait) {
        if (currentActivity == null || videoContainer == null || panel == null || !fullscreen) return;
        ViewGroup parent = (ViewGroup) videoContainer.getParent();
        if (parent != null) parent.removeView(videoContainer);
        fullscreen = false;
        updateFullscreenLiveServerButton(currentChannel);
        int width = currentActivity.getResources().getDisplayMetrics().widthPixels;
        panel.addView(videoContainer, 0, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, calculateVideoHeight(currentActivity, width)));
        HomeTabBar.setVisible(currentActivity, true);
        showSystemBars();
        updateFullscreenIcon();
        videoContainer.post(() -> {
            if (videoContainer == null || fullscreen) return;
            int actualWidth = videoContainer.getWidth();
            if (actualWidth <= 0) return;
            ViewGroup.LayoutParams params = videoContainer.getLayoutParams();
            if (params != null) {
                params.height = calculateVideoHeight(currentActivity, actualWidth);
                videoContainer.setLayoutParams(params);
            }
        });
        if (requestPortrait) currentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void syncOrientationUi() {
        if (currentActivity == null || panel == null || panel.getVisibility() != View.VISIBLE) return;
        int orientation = currentActivity.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE && !fullscreen) {
            enterFullscreen(false);
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT && fullscreen
                && currentActivity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            exitFullscreen(false);
        }
        updateFullscreenIcon();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (currentActivity == null) return;
        Window window = currentActivity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 30) {
            Api30Insets.hide(window);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @SuppressWarnings("deprecation")
    private void showSystemBars() {
        if (currentActivity == null) return;
        Window window = currentActivity.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 30) {
            Api30Insets.show(window);
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private static DefaultLoadControl stableLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_500, 10_000, 400, 1_200)
                .setBackBuffer(0, false)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    private void playInline(Channel channel) {
        playInline(channel, 0L, true);
    }

    private void playInline(Channel channel, long resumePositionMs, boolean resumePlaying) {
        if (currentActivity == null || playerView == null) return;
        MobileMiniPlayer.remove();
        MobileNm7Application.suspendYoutubeForIptv();
        SharedPlaybackSession.setTab(currentActivity, SharedPlaybackSession.TAB_IPTV);
        requestedResumePositionMs = Math.max(0L, resumePositionMs);
        requestedResumePlaying = resumePlaying;
        java.util.ArrayList<String> savedOptions = new java.util.ArrayList<>(channel.options());
        android.os.Bundle savedHeaders = new android.os.Bundle();
        for (java.util.Map.Entry<String,String> entry : channel.headers().entrySet()) {
            if (entry.getValue() != null) savedHeaders.putString(entry.getKey(), entry.getValue());
        }
        SharedPlaybackSession.saveIptv(currentActivity, channel.name(), channel.url(),
                channel.mimeHint(), savedHeaders, savedOptions, requestedResumePositionMs, requestedResumePlaying);
        int generation = ++playGeneration;
        recoveryAttempts = 0;
        resumeAfterLifecyclePause = false;
        userPaused = false;
        bufferingSinceMs = 0;
        lastRenderedBufferCount = -1;
        lastFrameProgressMs = android.os.SystemClock.elapsedRealtime();
        lastNetworkProgressMs = lastFrameProgressMs;
        stablePlaybackSinceMs = 0;
        currentChannel = channel;
        updateLiveServerButton(channel);
        if (currentActivity != null && currentActivity.isScheduleTabOpen()) {
            currentActivity.loadScheduleForCurrentChannel();
        }
        panel.setVisibility(View.VISIBLE);
        playerView.hideController();
        title.setText(channel.name());
        resolutionText = "Độ phân giải: —";
        if (stats != null) stats.setText("Độ phân giải: —  •  FPS: —");
        updateProgramme(channel);
        AppPreferences.recordRecent(currentActivity, channel);
        keepScreenAwake(true);
        mainHandler.post(this::syncOrientationUi);
        releasePlayer();

        String scheme = Uri.parse(channel.url()).getScheme();
        if (scheme == null || !scheme.matches("(?i)https?|rtsp|udp|rtmp")) {
            programme.setText("Nguồn này chưa hỗ trợ trong khung Mobile: " + (scheme == null ? "không rõ giao thức" : scheme));
            return;
        }
        DrmSpec drm = DrmSpec.fromOptions(channel.options());
        if (drm.remoteClearKey()) {
            programme.setText("Đang lấy giấy phép ClearKey…");
            resolveRemoteClearKey(channel, drm, generation);
        } else startInlinePlayer(channel, drm, generation, "");
    }

    private void startInlinePlayer(Channel channel, DrmSpec drm, int generation, String forcedMime) {
        if (currentActivity == null || currentChannel != channel || generation != playGeneration) return;
        try {
            Map<String, String> headers = new LinkedHashMap<>(channel.headers());
            String ua = headers.containsKey("User-Agent") ? headers.get("User-Agent") : "Nm7-IPTV/1.10.23 Android";
            // Reuse sockets across manifests and media segments. OkHttp keeps the
            // live session warm and retries a broken pooled connection before Media3
            // has to rebuild the whole source.
            OkHttpDataSource.Factory http = new OkHttpDataSource.Factory(STREAM_HTTP)
                    .setUserAgent(ua)
                    .setDefaultRequestProperties(headers);
            DefaultDataSource.Factory data = new DefaultDataSource.Factory(currentActivity, http);
            DefaultMediaSourceFactory mediaFactory = new DefaultMediaSourceFactory(data)
                    .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(8));

            MediaItem.Builder media = new MediaItem.Builder().setUri(channel.url());
            if (activeCatchupProgramme == null) {
                media.setLiveConfiguration(new MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(3_500)
                        .setMinPlaybackSpeed(1.0f)
                        .setMaxPlaybackSpeed(1.0f)
                        .build());
            }
            String mime = forcedMime;
            if (mime == null || mime.isEmpty()) mime = channel.mimeHint();
            if (mime == null || mime.isEmpty()) mime = StreamSpec.inferMime(channel.url(), channel.options());
            if (mime != null && !mime.isEmpty()) media.setMimeType(mime);
            DrmPlayback.configure(drm, media, mediaFactory);

            ExoPlayer next = new ExoPlayer.Builder(currentActivity,
                    new DefaultRenderersFactory(currentActivity).setEnableDecoderFallback(true))
                    .setLoadControl(stableLoadControl())
                    .setMediaSourceFactory(mediaFactory).build();
            next.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), false);
            next.setVolume(0f); // Prepare silently; retain YouTube until IPTV is READY.
            next.setHandleAudioBecomingNoisy(true);
            next.setWakeMode(C.WAKE_MODE_NONE);
            player = next;
            playerView.setPlayer(next);
            videoCounters = null;
            fpsMeter.reset();
            updateSeekUi();

            next.addAnalyticsListener(new AnalyticsListener() {
                @Override public void onVideoEnabled(EventTime eventTime, DecoderCounters counters) {
                    if (generation != playGeneration) return;
                    videoCounters = counters;
                    lastRenderedBufferCount = counters.renderedOutputBufferCount;
                    lastFrameProgressMs = android.os.SystemClock.elapsedRealtime();
                    fpsMeter.reset();
                }
                @Override public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs,
                                                           long totalBytesLoaded, long bitrateEstimate) {
                    if (generation == playGeneration && totalBytesLoaded > 0) {
                        lastNetworkProgressMs = android.os.SystemClock.elapsedRealtime();
                    }
                }
            });
            next.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (generation != playGeneration || next != player) return;
                    if (state == Player.STATE_BUFFERING) {
                        programme.setText("Đang ổn định bộ đệm…");
                        stablePlaybackSinceMs = 0;
                        if (bufferingSinceMs == 0) bufferingSinceMs = android.os.SystemClock.elapsedRealtime();
                    }
                    if (state == Player.STATE_READY) {
                        if (next.getPlayWhenReady() && next.getVolume() == 0f) {
                            MobileNm7Application.suspendYoutubeForIptv();
                            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
                            next.setVolume(1f);
                        }
                        bufferingSinceMs = 0;
                        updateProgramme(channel);
                        updateSeekUi();
                        keepScreenAwake(true);
                    }
                    if (state == Player.STATE_ENDED && !userPaused) {
                        recoverPlayback(channel, drm, generation, forcedMime, "Luồng đã kết thúc");
                    }
                }
                @Override public void onIsPlayingChanged(boolean isPlaying) {
                    if (generation != playGeneration || next != player) return;
                    if (isPlaying) {
                        bufferingSinceMs = 0;
                        lastNetworkProgressMs = android.os.SystemClock.elapsedRealtime();
                        if (stablePlaybackSinceMs == 0) stablePlaybackSinceMs = lastNetworkProgressMs;
                        if (!backgroundActive) keepScreenAwake(true);
                    } else {
                        stablePlaybackSinceMs = 0;
                    }
                }
                @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                    if (generation != playGeneration || next != player) return;
                    if (playWhenReady && next.getPlaybackState() == Player.STATE_READY && next.getVolume() == 0f) {
                        MobileNm7Application.suspendYoutubeForIptv();
                        next.setVolume(1f);
                        next.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
                    }
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST && !lifecyclePauseInProgress) {
                        userPaused = !playWhenReady;
                    }
                }
                @Override public void onAvailableCommandsChanged(Player.Commands availableCommands) {
                    if (generation != playGeneration || next != player) return;
                    updateSeekUi();
                }
                @Override public void onVideoSizeChanged(VideoSize size) {
                    if (generation != playGeneration || next != player) return;
                    resolutionText = size.width > 0 && size.height > 0
                            ? "Độ phân giải: " + size.width + " × " + size.height : "Độ phân giải: —";
                    updateStatsNow();
                }
                @Override public void onPlayerError(PlaybackException error) {
                    if (generation != playGeneration || next != player) return;
                    handlePlaybackError(channel, drm, generation, forcedMime, error);
                }
            });

            MediaItem item = media.build();
            String scheme = Uri.parse(channel.url()).getScheme();
            if ("rtsp".equalsIgnoreCase(scheme)) {
                next.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true)
                        .setUserAgent(ua).createMediaSource(item));
            } else {
                if ("udp".equalsIgnoreCase(scheme)) acquireMulticast();
                next.setMediaItem(item);
            }
            next.prepare();
            if (requestedResumePositionMs > 0L) next.seekTo(requestedResumePositionMs);
            next.setPlayWhenReady(requestedResumePlaying);
            mainHandler.removeCallbacks(statsTick);
            mainHandler.post(statsTick);
            playerView.hideController();
            updateFullscreenIcon();
        } catch (Exception error) {
            releasePlayer();
            programme.setText("Không phát được kênh này • " + readable(error));
        }
    }

    private void monitorPlaybackHealth() {
        ExoPlayer active = player;
        Channel channel = currentChannel;
        if (active == null || channel == null || userPaused || backgroundActive || currentActivity == null
                || panel == null || panel.getVisibility() != View.VISIBLE) return;

        int state = active.getPlaybackState();
        long now = android.os.SystemClock.elapsedRealtime();
        if (active.isPlaying() && stablePlaybackSinceMs > 0
                && now - stablePlaybackSinceMs >= 120_000) {
            // Short successful bursts must not erase the stall history. Reset only
            // after two continuous minutes so repeated CDN failures escalate.
            recoveryAttempts = 0;
            stablePlaybackSinceMs = now;
        }

        if (state == Player.STATE_BUFFERING) {
            stablePlaybackSinceMs = 0;
            if (bufferingSinceMs == 0) bufferingSinceMs = now;
            long stalledFor = now - bufferingSinceMs;
            long bufferedAhead = Math.max(0L, active.getBufferedPosition() - active.getCurrentPosition());
            boolean networkRecentlyMoved = now - lastNetworkProgressMs < 8_000;
            // Do not restart a slow-but-progressing transfer. A request that is still
            // moving receives a longer grace period; a dead socket is recovered sooner.
            long limit = (networkRecentlyMoved || (active.isLoading() && bufferedAhead > 0))
                    ? 35_000L : 18_000L;
            if (stalledFor >= limit) {
                bufferingSinceMs = now;
                recoverPlayback(channel, DrmSpec.fromOptions(channel.options()), playGeneration, "",
                        "Luồng ngừng nhận dữ liệu, đang phục hồi kết nối");
            }
            return;
        }

        if (state == Player.STATE_READY && !active.getPlayWhenReady()
                && active.getPlaybackSuppressionReason() == Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
            active.play();
            programme.setText("Đang tự tiếp tục phát…");
            return;
        }

        if (state == Player.STATE_READY && active.getPlayWhenReady()
                && active.getPlaybackSuppressionReason() == Player.PLAYBACK_SUPPRESSION_REASON_NONE
                && videoCounters != null) {
            int rendered = videoCounters.renderedOutputBufferCount;
            if (rendered != lastRenderedBufferCount) {
                lastRenderedBufferCount = rendered;
                lastFrameProgressMs = now;
            } else if (lastFrameProgressMs > 0 && now - lastFrameProgressMs >= 4_000) {
                lastFrameProgressMs = now;
                recoverPlayback(channel, DrmSpec.fromOptions(channel.options()), playGeneration, "",
                        "Tự động phục hồi khung hình");
            }
        } else {
            lastRenderedBufferCount = videoCounters == null ? -1 : videoCounters.renderedOutputBufferCount;
            lastFrameProgressMs = now;
        }
    }

    private void recoverPlayback(Channel channel, DrmSpec drm, int generation,
                                 String forcedMime, String reason) {
        if (generation != playGeneration || currentChannel != channel || userPaused) return;
        int attempt = Math.min(++recoveryAttempts, 8);
        long delay = attempt == 1 ? 400L : attempt == 2 ? 900L
                : attempt <= 4 ? 2_000L : attempt <= 6 ? 4_000L : 7_000L;
        programme.setText(reason + "…");

        ExoPlayer active = player;
        if (attempt == 1 && active != null) {
            // First tier: retain the connection, timeline and decoder. Seek only when
            // the live position is clearly stale; unconditional live-edge seeks caused repeats.
            mainHandler.postDelayed(() -> {
                if (generation != playGeneration || player != active
                        || currentChannel != channel || userPaused) return;
                try {
                    long liveOffset = active.getCurrentLiveOffset();
                    if (active.getPlaybackState() == Player.STATE_ENDED
                            || (liveOffset != C.TIME_UNSET && liveOffset > 45_000)) {
                        active.seekToDefaultPosition();
                    }
                    active.prepare();
                    active.play();
                } catch (RuntimeException ignored) {
                    releasePlayer();
                    startInlinePlayer(channel, drm, generation, forcedMime);
                }
            }, delay);
        } else {
            // Higher tiers cancel a genuinely stuck request and rebuild it using the
            // shared HTTP connection pool. Backoff is capped, so recovery remains automatic.
            releasePlayer();
            mainHandler.postDelayed(() -> {
                if (generation == playGeneration && currentChannel == channel && !userPaused) {
                    startInlinePlayer(channel, drm, generation, forcedMime);
                }
            }, delay);
        }
    }

    private void handlePlaybackError(Channel channel, DrmSpec drm, int generation, String forcedMime, PlaybackException error) {
        if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                && (forcedMime == null || forcedMime.isEmpty())) {
            programme.setText("Đang nhận diện lại định dạng luồng…");
            probeRedirectedMime(channel, drm, generation);
            return;
        }
        boolean drmSystem = error.errorCode == PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR;
        boolean transientError = isTransientPlaybackError(error);
        if (transientError) {
            recoverPlayback(channel, drm, generation, forcedMime,
                    "Kết nối tạm gián đoạn, đang tự nối lại");
            return;
        }
        if (drmSystem && recoveryAttempts < 3) {
            int attempt = ++recoveryAttempts;
            long delay = attempt == 1 ? 450 : attempt == 2 ? 1000 : 1800;
            programme.setText("DRM đang khởi tạo lại…");
            releasePlayer();
            mainHandler.postDelayed(() -> {
                if (generation == playGeneration && currentChannel == channel) startInlinePlayer(channel, drm, generation, forcedMime);
            }, delay);
            return;
        }
        programme.setText("Không phát được kênh này • " + error.getErrorCodeName());
        updateStatsNow();
    }

    private boolean isTransientPlaybackError(PlaybackException error) {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                || error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT
                || error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return true;
        Throwable cause = error.getCause();
        while (cause != null) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
                int code = ((HttpDataSource.InvalidResponseCodeException) cause).responseCode;
                return code == 401 || code == 403 || code == 408 || code == 429 || code >= 500;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void probeRedirectedMime(Channel channel, DrmSpec drm, int generation) {
        Map<String, String> headers = new LinkedHashMap<>(channel.headers());
        io.execute(() -> {
            String current = channel.url();
            String detected = "";
            try {
                for (int hop = 0; hop < 5; hop++) {
                    HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
                    try {
                        connection.setConnectTimeout(10_000);
                        connection.setReadTimeout(10_000);
                        connection.setInstanceFollowRedirects(false);
                        connection.setRequestMethod("GET");
                        for (Map.Entry<String, String> header : headers.entrySet()) connection.setRequestProperty(header.getKey(), header.getValue());
                        if (!headers.containsKey("User-Agent")) connection.setRequestProperty("User-Agent", "Nm7-IPTV/1.10.23 Android");
                        int code = connection.getResponseCode();
                        if (code >= 300 && code < 400) {
                            String location = connection.getHeaderField("Location");
                            if (location == null || location.isEmpty()) break;
                            current = new URL(new URL(current), location).toString();
                            String hint = StreamSpec.inferMime(current, channel.options());
                            if (hint != null && !hint.isEmpty()) { detected = hint; break; }
                            continue;
                        }
                        String type = connection.getContentType();
                        if (type != null) {
                            String lower = type.toLowerCase(Locale.ROOT);
                            if (lower.contains("mpegurl") || lower.contains("m3u8")) detected = MimeTypes.APPLICATION_M3U8;
                            else if (lower.contains("dash+xml")) detected = MimeTypes.APPLICATION_MPD;
                        }
                        break;
                    } finally { connection.disconnect(); }
                }
            } catch (Exception ignored) { }
            final String mime = detected;
            if (currentActivity != null) currentActivity.runOnUiThread(() -> {
                if (generation != playGeneration || currentChannel != channel) return;
                if (!mime.isEmpty()) {
                    releasePlayer();
                    startInlinePlayer(channel, drm, generation, mime);
                } else programme.setText("Không nhận diện được định dạng nguồn phát của kênh này");
            });
        });
    }

    private void resolveRemoteClearKey(Channel channel, DrmSpec drm, int generation) {
        Map<String, String> streamHeaders = new LinkedHashMap<>(channel.headers());
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(drm.license).openConnection();
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(20_000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                for (Map.Entry<String, String> entry : drm.headers.entrySet()) connection.setRequestProperty(entry.getKey(), entry.getValue());
                String streamHost = Uri.parse(channel.url()).getHost();
                String licenseHost = Uri.parse(drm.license).getHost();
                if (streamHost != null && streamHost.equalsIgnoreCase(licenseHost)) {
                    for (String name : new String[]{"User-Agent", "Referer", "Origin", "Cookie"}) {
                        String value = streamHeaders.get(name);
                        if (value != null && !value.isEmpty() && !drm.headers.containsKey(name)) connection.setRequestProperty(name, value);
                    }
                }
                if (connection.getRequestProperty("User-Agent") == null) connection.setRequestProperty("User-Agent", "Dalvik/2.1.0");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalArgumentException("HTTP " + code);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int total = 0, count;
                    while ((count = input.read(buffer)) >= 0) {
                        total += count;
                        if (total > 65_536) throw new IllegalArgumentException("phản hồi giấy phép quá lớn");
                        output.write(buffer, 0, count);
                    }
                }
                String response = output.toString("UTF-8").trim();
                DrmPlayback.clearKeyResponse(response);
                DrmSpec local = DrmSpec.create("clearkey", response);
                if (currentActivity != null) currentActivity.runOnUiThread(() -> {
                    if (generation == playGeneration && currentChannel == channel) startInlinePlayer(channel, local, generation, "");
                });
            } catch (Exception error) {
                if (currentActivity != null) currentActivity.runOnUiThread(() -> {
                    if (generation == playGeneration && programme != null) programme.setText("Không lấy được giấy phép ClearKey cho kênh này");
                });
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void updateProgramme(Channel channel) {
        if (programme == null) return;
        if (activeCatchupProgramme != null) {
            programme.setText("↺ Đang xem lại: " + activeCatchupProgramme.title + " (" + activeCatchupProgramme.timeRangeText() + ")");
            if (title != null) {
                title.setText(channel.name() + "  [DVR]");
            }
            return;
        }
        if (channelList == null) return;
        try {
            Object adapter = channelList.getAdapter();
            if (!(adapter instanceof ChannelAdapter)) { programme.setText(channel.group()); return; }
            EpgStore.Guide guide = ((ChannelAdapter) adapter).guide();
            EpgStore.Programme now = guide == null ? null : guide.find(channel);
            if (now == null) programme.setText(channel.group());
            else programme.setText(now.title + "  •  " + now.startText() + " - " + now.endText());
        } catch (Exception ignored) { programme.setText(channel.group()); }
    }

    private void updateStatsNow() {
        if (stats == null) return;
        String fpsText = "FPS: —";
        if (player != null && videoCounters != null) {
            double value = fpsMeter.sample(android.os.SystemClock.elapsedRealtime(), videoCounters.renderedOutputBufferCount, player.isPlaying());
            if (!Double.isNaN(value)) fpsText = String.format(Locale.ROOT, "FPS: %.1f", value);
            else if (player.isPlaying()) fpsText = "FPS: đang đo…";
        }
        stats.setText(resolutionText + "  •  " + fpsText);
    }

    private void keepScreenAwake(boolean awake) {
        if (currentActivity == null) return;
        if (awake) currentActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else currentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (playerView != null) playerView.setKeepScreenOn(awake);
    }

    private void startBackgroundService(Activity activity) {
        if (currentChannel == null) return;
        Intent service = new Intent(activity, BackgroundPlaybackService.class)
                .putExtra(BackgroundPlaybackService.EXTRA_CHANNEL_NAME, currentChannel.name())
                .putExtra(BackgroundPlaybackService.EXTRA_RETURN_MAIN, true);
        if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(service);
        else activity.startService(service);
    }

    private void stopBackgroundService(Activity activity) {
        activity.stopService(new Intent(activity, BackgroundPlaybackService.class));
    }

    private boolean consumeBack() {
        if (fullscreen) {
            exitFullscreen(true);
            return true;
        }
        if (currentActivity != null && currentActivity.isScheduleTabOpen()) {
            currentActivity.showChannelListTab();
            return true;
        }
        if (panel == null || panel.getVisibility() != View.VISIBLE) return false;
        ++playGeneration;
        releasePlayer();
        currentChannel = null;
        resumeAfterYoutube = false;
        pendingResumePlaying = false;
        pendingResumePositionMs = 0L;
        if (currentActivity != null) SharedPlaybackSession.clearIptv(currentActivity);
        backgroundActive = false;
        resumeAfterLifecyclePause = false;
        panel.setVisibility(View.GONE);
        keepScreenAwake(false);
        if (currentActivity != null) stopBackgroundService(currentActivity);
        if (channelList != null) channelList.requestFocus();
        return true;
    }

    private void installBackHandling(Activity activity) {
        if (Build.VERSION.SDK_INT >= 26) {
            wrappedWindow = activity.getWindow();
            originalWindowCallback = wrappedWindow.getCallback();
            wrappedWindow.setCallback(new BackWindowCallback(this, originalWindowCallback));
        }
        if (Build.VERSION.SDK_INT >= 33) Api33Back.register(this, activity);
    }

    private void uninstallBackHandling() {
        if (Build.VERSION.SDK_INT >= 33) Api33Back.unregister(this);
        if (wrappedWindow != null && originalWindowCallback != null && wrappedWindow.getCallback() instanceof BackWindowCallback) {
            wrappedWindow.setCallback(originalWindowCallback);
        }
        wrappedWindow = null;
        originalWindowCallback = null;
    }

    private void acquireMulticast() {
        if (currentActivity == null || multicastLock != null) return;
        WifiManager wifi = (WifiManager) currentActivity.getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("Nm7InlineUdpV2");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        }
    }

    private void releasePlayer() {
        mainHandler.removeCallbacks(statsTick);
        bufferingSinceMs = 0;
        lastRenderedBufferCount = -1;
        lastFrameProgressMs = 0;
        lastNetworkProgressMs = 0;
        stablePlaybackSinceMs = 0;
        videoCounters = null;
        fpsMeter.reset();
        if (player != null) {
            ExoPlayer old = player;
            player = null;
            if (playerView != null) playerView.setPlayer(null);
            old.release();
        }
        updateSeekUi();
        if (multicastLock != null) {
            try { if (multicastLock.isHeld()) multicastLock.release(); } catch (RuntimeException ignored) { }
            multicastLock = null;
        }
    }

    private void detach() {
        ++playGeneration;
        if (fullscreen && currentActivity != null && videoContainer != null && panel != null) exitFullscreen(false);
        keepScreenAwake(false);
        if (currentActivity != null) {
            stopBackgroundService(currentActivity);
            showSystemBars();
            currentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        releasePlayer();
        uninstallBackHandling();
        if (fullscreenHost != null && rootLayoutListener != null) fullscreenHost.removeOnLayoutChangeListener(rootLayoutListener);
        if (channelList != null && originalClick != null) channelList.setOnItemClickListener(originalClick);
        if (panel != null && panel.getParent() instanceof LinearLayout) ((LinearLayout) panel.getParent()).removeView(panel);
        currentActivity = null;
        currentChannel = null;
        channelList = null;
        originalClick = null;
        panel = null;
        videoContainer = null;
        fullscreenHost = null;
        playerView = null;
        fullscreenButton = null;
        title = null;
        programme = null;
        stats = null;
        liveServerButton = null;
        scheduleButton = null;
        returnToLiveButton = null;
        activeCatchupProgramme = null;
        backgroundActive = false;
        resumeAfterLifecyclePause = false;
        fullscreen = false;
        rootLayoutListener = null;
    }

    private static String readable(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @TargetApi(30)
    private static final class Api30Insets {
        static void hide(Window window) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        static void show(Window window) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.show(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
        }
    }

    @TargetApi(33)
    private static final class Api33Back {
        static void register(MobileInlinePlayerProviderV2 provider, Activity activity) {
            android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback = () -> { if (!provider.consumeBack()) activity.finish(); };
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            provider.backDispatcher33 = dispatcher;
            provider.backCallback33 = callback;
        }
        static void unregister(MobileInlinePlayerProviderV2 provider) {
            if (provider.backDispatcher33 instanceof android.window.OnBackInvokedDispatcher
                    && provider.backCallback33 instanceof android.window.OnBackInvokedCallback) {
                ((android.window.OnBackInvokedDispatcher) provider.backDispatcher33)
                        .unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) provider.backCallback33);
            }
            provider.backDispatcher33 = null;
            provider.backCallback33 = null;
        }
    }

    @TargetApi(26)
    private static final class BackWindowCallback implements Window.Callback {
        private final MobileInlinePlayerProviderV2 provider;
        private final Window.Callback base;
        BackWindowCallback(MobileInlinePlayerProviderV2 provider, Window.Callback base) { this.provider = provider; this.base = base; }
        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && provider.panel != null && provider.panel.getVisibility() == View.VISIBLE) {
                if (event.getAction() == KeyEvent.ACTION_UP) provider.consumeBack();
                return true;
            }
            return base.dispatchKeyEvent(event);
        }
        @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) { return base.dispatchKeyShortcutEvent(event); }
        @Override public boolean dispatchTouchEvent(MotionEvent event) { return base.dispatchTouchEvent(event); }
        @Override public boolean dispatchTrackballEvent(MotionEvent event) { return base.dispatchTrackballEvent(event); }
        @Override public boolean dispatchGenericMotionEvent(MotionEvent event) { return base.dispatchGenericMotionEvent(event); }
        @Override public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) { return base.dispatchPopulateAccessibilityEvent(event); }
        @Override public View onCreatePanelView(int featureId) { return base.onCreatePanelView(featureId); }
        @Override public boolean onCreatePanelMenu(int featureId, Menu menu) { return base.onCreatePanelMenu(featureId, menu); }
        @Override public boolean onPreparePanel(int featureId, View view, Menu menu) { return base.onPreparePanel(featureId, view, menu); }
        @Override public boolean onMenuOpened(int featureId, Menu menu) { return base.onMenuOpened(featureId, menu); }
        @Override public boolean onMenuItemSelected(int featureId, MenuItem item) { return base.onMenuItemSelected(featureId, item); }
        @Override public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) { base.onWindowAttributesChanged(attrs); }
        @Override public void onContentChanged() { base.onContentChanged(); }
        @Override public void onWindowFocusChanged(boolean hasFocus) { base.onWindowFocusChanged(hasFocus); }
        @Override public void onAttachedToWindow() { base.onAttachedToWindow(); }
        @Override public void onDetachedFromWindow() { base.onDetachedFromWindow(); }
        @Override public void onPanelClosed(int featureId, Menu menu) { base.onPanelClosed(featureId, menu); }
        @Override public boolean onSearchRequested() { return base.onSearchRequested(); }
        @Override public boolean onSearchRequested(SearchEvent searchEvent) { return base.onSearchRequested(searchEvent); }
        @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) { return base.onWindowStartingActionMode(callback); }
        @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) { return base.onWindowStartingActionMode(callback, type); }
        @Override public void onActionModeStarted(ActionMode mode) { base.onActionModeStarted(mode); }
        @Override public void onActionModeFinished(ActionMode mode) { base.onActionModeFinished(mode); }
        @Override public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> data, Menu menu, int deviceId) { base.onProvideKeyboardShortcuts(data, menu, deviceId); }
        @Override public void onPointerCaptureChanged(boolean hasCapture) { base.onPointerCaptureChanged(hasCapture); }
    }

    @Override public void onActivityDestroyed(Activity activity) { if (activity == currentActivity) detach(); }
    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityStarted(Activity a) { }
    @Override public void onActivityPaused(Activity a) {
        if (a != currentActivity || player == null) return;
        boolean wantedPlayback = player.getPlayWhenReady();
        if (SharedPlaybackSession.TAB_YOUTUBE.equals(SharedPlaybackSession.tab(a))) {
            backgroundActive = false;
            resumeAfterLifecyclePause = wantedPlayback && !userPaused;
            lifecyclePauseInProgress = true;
            player.pause();
            return;
        }
        if (AppPreferences.backgroundPlayback(a) && wantedPlayback && currentChannel != null
                && panel != null && panel.getVisibility() == View.VISIBLE) {
            backgroundActive = true;
            resumeAfterLifecyclePause = false;
            keepScreenAwake(false);
            player.setWakeMode(C.WAKE_MODE_NETWORK);
            startBackgroundService(a);
        } else {
            backgroundActive = false;
            resumeAfterLifecyclePause = wantedPlayback && !userPaused;
            lifecyclePauseInProgress = true;
            player.pause();
        }
    }
    @Override public void onActivityStopped(Activity a) { }
    private static MobileInlinePlayerProviderV2 instance;

    /** Starts a selected Live source in the existing inline player when the mobile player is attached. */
    public static boolean playLiveChannel(Channel channel) {
        MobileInlinePlayerProviderV2 owner = instance;
        if (channel == null || owner == null || owner.currentActivity == null
                || AppPreferences.isTvInterface(owner.currentActivity)) return false;
        MobileMiniPlayer.remove();
        MobileNm7Application.suspendYoutubeForIptv();
        owner.playInline(channel);
        return true;
    }

    private boolean resumeAfterYoutube;
    private long pendingResumePositionMs;
    private boolean pendingResumePlaying;

    static boolean isPanelVisible() {
        MobileInlinePlayerProviderV2 owner = instance;
        return owner != null && owner.panel != null && owner.panel.getVisibility() == View.VISIBLE;
    }

    public static boolean hasSession() {
        return instance != null && instance.currentActivity != null && instance.currentChannel != null;
    }

    /** True only when the channel that was playing before YouTube must be restored. */
    public static boolean isIptvPlaybackActive() {
        MobileInlinePlayerProviderV2 owner = instance;
        if (owner == null) return false;
        if (owner.currentChannel != null && owner.player != null) {
            int state = owner.player.getPlaybackState();
            if ((state == androidx.media3.common.Player.STATE_BUFFERING || state == androidx.media3.common.Player.STATE_READY)
                    && owner.player.getPlayWhenReady() && !owner.userPaused) {
                return true;
            }
        }
        if (owner.resumeAfterYoutube && owner.currentChannel != null) {
            return true;
        }
        return false;
    }

    public static boolean shouldResumeIptvAfterYoutube() {
        MobileInlinePlayerProviderV2 owner = instance;
        return owner != null && owner.resumeAfterYoutube && owner.currentChannel != null;
    }

    /** Explicit tab-return hook. MainActivity may already be at the top when the tab is tapped. */
    public static void resumeForIptvTab(Activity activity) {
        MobileInlinePlayerProviderV2 owner = instance;
        if (owner == null) return;
        // The tab click is normally received by SmartTube Browse/Playback. MainActivity
        // is already alive in the task and must be resolved from the provider itself.
        MainActivity main = activity instanceof MainActivity
                ? (MainActivity) activity : owner.currentActivity;
        if (main == null || main.isFinishing() || main.isDestroyed()) {
            android.util.Log.w("NM7Playback", "IPTV tab return: MainActivity unavailable");
            return;
        }
        owner.postResumeIptvForNm7(main);
    }

    private void postResumeIptvForNm7(MainActivity main) {
        if (currentActivity != main || panel == null || playerView == null) {
            attach(main);
        }
        if (currentActivity != main || SharedPlaybackSession.TAB_IPTV.equals(SharedPlaybackSession.tab(main)) == false) return;
        if (!resumeAfterYoutube) {
            if (player != null && !userPaused) {
                if (playerView != null && playerView.getPlayer() != player) {
                    playerView.setPlayer(player);
                }
                if (player.isCurrentMediaItemLive()) {
                    player.seekToDefaultPosition();
                }
                if (!player.getPlayWhenReady()) player.play();
            }
            return;
        }
        if (player != null) {
            resumeAfterYoutube = false;
            if (!userPaused) {
                if (playerView != null && playerView.getPlayer() != player) {
                    playerView.setPlayer(player);
                }
                if (player.isCurrentMediaItemLive()) {
                    player.seekToDefaultPosition();
                }
                if (!player.getPlayWhenReady()) player.play();
            }
            return;
        }
        long restoredPosition = pendingResumePositionMs;
        boolean restoredPlaying = pendingResumePlaying;
        if (currentChannel == null) {
            SharedPlaybackSession.State saved = SharedPlaybackSession.loadIptv(main);
            if (saved != null) {
                java.util.Map<String,String> headers = new java.util.LinkedHashMap<>();
                for (String key : saved.headers.keySet()) {
                    String value = saved.headers.getString(key);
                    if (value != null) headers.put(key, value);
                }
                Channel restored = new Channel(saved.name, "Khôi phục", saved.url, "", "", headers);
                restored.options().addAll(saved.options);
                currentChannel = restored;
                restoredPosition = saved.position;
                restoredPlaying = saved.playing;
            }
        }
        if (currentChannel != null) {
            pendingResumePositionMs = restoredPosition;
            pendingResumePlaying = restoredPlaying;
            resumeAfterYoutube = false;
            mainHandler.postDelayed(() -> {
                if (currentActivity == main
                        && SharedPlaybackSession.TAB_IPTV.equals(SharedPlaybackSession.tab(main))
                        && currentChannel != null) {
                    long position = pendingResumePositionMs;
                    boolean playing = pendingResumePlaying;
                    pendingResumePositionMs = 0L;
                    pendingResumePlaying = false;
                    playInline(currentChannel, position, playing);
                }
            }, 80L);
        }
    }

    public static void stopForSleepTimer() {
        MobileInlinePlayerProviderV2 owner = instance;
        if (owner == null) return;
        ++owner.playGeneration;
        owner.resumeAfterYoutube = false;
        owner.resumeAfterLifecyclePause = false;
        owner.currentChannel = null;
        owner.backgroundActive = false;
        owner.releasePlayer();
        if (owner.currentActivity != null) owner.stopBackgroundService(owner.currentActivity);
    }

    public static void releaseForYoutube() {
        MobileInlinePlayerProviderV2 owner = instance;
        if (owner == null) return;
        if (owner.player != null) {
            owner.resumeAfterYoutube = owner.player.getPlayWhenReady() && !owner.userPaused;
            owner.pendingResumePositionMs = owner.player.isCurrentMediaItemLive() ? 0L : Math.max(0L, owner.player.getCurrentPosition());
            owner.pendingResumePlaying = owner.resumeAfterYoutube;
            if (owner.currentActivity != null && owner.currentChannel != null) {
                android.os.Bundle headers = new android.os.Bundle();
                for (java.util.Map.Entry<String,String> entry : owner.currentChannel.headers().entrySet()) {
                    if (entry.getValue() != null) headers.putString(entry.getKey(), entry.getValue());
                }
                SharedPlaybackSession.saveIptv(owner.currentActivity, owner.currentChannel.name(),
                        owner.currentChannel.url(), owner.currentChannel.mimeHint(), headers,
                        new java.util.ArrayList<>(owner.currentChannel.options()),
                        owner.pendingResumePositionMs, owner.pendingResumePlaying);
            }
        }
        ++owner.playGeneration; // Invalidate pending DRM/retry callbacks before release.
        owner.resumeAfterLifecyclePause = false;
        owner.backgroundActive = false;
        owner.releasePlayer();
        if (owner.currentActivity != null) owner.stopBackgroundService(owner.currentActivity);
    }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }

    public static Channel getCurrentChannel() {
        return instance != null ? instance.currentChannel : null;
    }

    public static EpgStore.Programme getActiveCatchupProgramme() {
        return instance != null ? instance.activeCatchupProgramme : null;
    }

    public static void playCatchup(Channel channel, EpgStore.Programme programme) {
        if (instance != null) instance.playCatchupInternal(channel, programme);
    }

    public static void returnToLive() {
        if (instance != null) instance.returnToLiveInternal();
    }

    public void playCatchupInternal(Channel channel, EpgStore.Programme prog) {
        if (currentActivity == null || channel == null || prog == null) return;
        this.activeCatchupProgramme = prog;
        this.currentChannel = channel;
        String catchupUrl = CatchupUrlBuilder.build(channel, prog);

        Channel dvrChannel = new Channel(
                channel.name(),
                channel.group(),
                catchupUrl,
                channel.logo(),
                channel.logo(),
                channel.logo(),
                channel.tvgId(),
                channel.headers(),
                channel.catchupType(),
                channel.catchupDays(),
                channel.catchupSource()
        );
        dvrChannel.options().addAll(channel.options());
        playInline(dvrChannel, 0L, true);

        if (title != null) {
            title.setText(channel.name() + "  [DVR]");
        }
        if (programme != null) {
            programme.setText("↺ Đang xem lại: " + prog.title + " (" + prog.timeRangeText() + ")");
        }
        if (returnToLiveButton != null) {
            returnToLiveButton.setVisibility(View.VISIBLE);
        }
        if (currentActivity != null) {
            currentActivity.onReplayStarted(channel, prog);
        }
    }

    public void returnToLiveInternal() {
        if (currentChannel == null) return;
        activeCatchupProgramme = null;
        if (returnToLiveButton != null) {
            returnToLiveButton.setVisibility(View.GONE);
        }
        playInline(currentChannel, 0L, true);
        if (currentActivity != null) {
            currentActivity.onReplayStopped();
        }
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String sort) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
