package vn.phuong.iptvplayer;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
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

/** Mobile watch-and-browse player. TV mode keeps the existing full-screen PlayerActivity. */
@UnstableApi
public final class MobileInlinePlayerProvider extends ContentProvider implements Application.ActivityLifecycleCallbacks {
    private MainActivity currentActivity;
    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout panel;
    private TextView title;
    private TextView programme;
    private TextView stats;
    private ListView channelList;
    private AdapterView.OnItemClickListener originalClick;
    private Channel currentChannel;
    private DecoderCounters videoCounters;
    private final FpsMeter fpsMeter = new FpsMeter();
    private final Handler statsHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService drmIo = Executors.newSingleThreadExecutor();
    private WifiManager.MulticastLock multicastLock;
    private int playGeneration;
    private String resolutionText = "Độ phân giải: —";
    private Window wrappedWindow;
    private Window.Callback originalWindowCallback;
    private Object backDispatcher33;
    private Object backCallback33;

    private final Runnable statsTick = new Runnable() {
        @Override public void run() {
            if (stats != null) {
                String fpsText = "FPS: —";
                if (player != null && videoCounters != null) {
                    double value = fpsMeter.sample(android.os.SystemClock.elapsedRealtime(),
                            videoCounters.renderedOutputBufferCount, player.isPlaying());
                    if (!Double.isNaN(value)) fpsText = String.format(Locale.ROOT, "FPS: %.1f", value);
                    else if (player.isPlaying()) fpsText = "FPS: đang đo…";
                }
                stats.setText(resolutionText + "  •  " + fpsText);
            }
            if (player != null) statsHandler.postDelayed(this, 2000);
        }
    };

    @Override public boolean onCreate() {
        if (getContext() != null) {
            ((Application) getContext().getApplicationContext()).registerActivityLifecycleCallbacks(this);
        }
        return true;
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        MainActivity main = (MainActivity) activity;
        if (AppPreferences.isTvInterface(main)) {
            if (currentActivity == main) detach();
            return;
        }
        if (currentActivity != main || panel == null || panel.getParent() == null) attach(main);
        else if (player != null) player.play();
    }

    private void attach(MainActivity activity) {
        detach();
        currentActivity = activity;
        LinearLayout root = activity.findViewById(R.id.mainRoot);
        channelList = activity.findViewById(R.id.listChannels);
        if (root == null || channelList == null) return;

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(View.GONE);
        panel.setBackgroundResource(R.drawable.panel);
        panel.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 8));

        playerView = new PlayerView(activity);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        panel.addView(playerView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 220)));

        title = new TextView(activity);
        title.setTextSize(17);
        title.setTextColor(activity.getColor(R.color.text_primary));
        title.setMaxLines(1);
        title.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), 0);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        programme = new TextView(activity);
        programme.setTextSize(14);
        programme.setTextColor(activity.getColor(R.color.text_secondary));
        programme.setMaxLines(2);
        programme.setPadding(dp(activity, 8), dp(activity, 2), dp(activity, 8), 0);
        panel.addView(programme, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        stats = new TextView(activity);
        stats.setTextSize(13);
        stats.setTextColor(activity.getColor(R.color.accent));
        stats.setText("Độ phân giải: —  •  FPS: —");
        stats.setPadding(dp(activity, 8), dp(activity, 2), dp(activity, 8), 0);
        panel.addView(stats, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(panel, Math.min(1, root.getChildCount()), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        originalClick = channelList.getOnItemClickListener();
        channelList.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getAdapter().getItem(position);
            if (item instanceof Channel) {
                // In Mobile mode every channel selection stays in the inline player.
                playInline((Channel) item);
            } else if (originalClick != null) {
                originalClick.onItemClick(parent, view, position, id);
            }
        });
        installBackHandling(activity);
    }

    private void playInline(Channel channel) {
        if (currentActivity == null || playerView == null) return;
        int generation = ++playGeneration;
        releasePlayer();
        currentChannel = channel;
        panel.setVisibility(View.VISIBLE);
        title.setText(channel.name());
        resolutionText = "Độ phân giải: —";
        if (stats != null) stats.setText("Độ phân giải: —  •  FPS: —");
        updateProgramme(channel);
        AppPreferences.recordRecent(currentActivity, channel);

        String scheme = Uri.parse(channel.url()).getScheme();
        if (scheme == null || !scheme.matches("(?i)https?|rtsp|udp|rtmp")) {
            programme.setText("Nguồn này chưa hỗ trợ phát trong khung Mobile: " + (scheme == null ? "không rõ giao thức" : scheme));
            return;
        }

        DrmSpec drm = DrmSpec.fromOptions(channel.options());
        if (drm.remoteClearKey()) {
            programme.setText("Đang lấy giấy phép ClearKey…");
            resolveRemoteClearKey(channel, drm, generation);
            return;
        }
        startInlinePlayer(channel, drm, generation);
    }

    private void startInlinePlayer(Channel channel, DrmSpec drm, int generation) {
        if (currentActivity == null || currentChannel != channel || generation != playGeneration) return;
        try {
            Map<String, String> headers = new LinkedHashMap<>(channel.headers());
            String ua = headers.containsKey("User-Agent") ? headers.get("User-Agent") : "Nm7-IPTV/1.10.2 Android";
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setUserAgent(ua)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(20_000)
                    .setDefaultRequestProperties(headers);
            DefaultDataSource.Factory data = new DefaultDataSource.Factory(currentActivity, http);
            DefaultMediaSourceFactory mediaFactory = new DefaultMediaSourceFactory(data);

            MediaItem.Builder media = new MediaItem.Builder().setUri(channel.url());
            String mime = channel.mimeHint();
            if (mime == null || mime.isEmpty()) mime = StreamSpec.inferMime(channel.url(), channel.options());
            if (mime != null && !mime.isEmpty()) media.setMimeType(mime);
            DrmPlayback.configure(drm, media, mediaFactory);

            player = new ExoPlayer.Builder(currentActivity,
                    new DefaultRenderersFactory(currentActivity).setEnableDecoderFallback(true))
                    .setMediaSourceFactory(mediaFactory).build();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
            player.setHandleAudioBecomingNoisy(true);
            playerView.setPlayer(player);
            videoCounters = null;
            fpsMeter.reset();

            player.addAnalyticsListener(new AnalyticsListener() {
                @Override public void onVideoEnabled(EventTime eventTime, DecoderCounters counters) {
                    videoCounters = counters;
                    fpsMeter.reset();
                }
            });
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (generation != playGeneration) return;
                    if (state == Player.STATE_BUFFERING) programme.setText("Đang tải luồng…");
                    if (state == Player.STATE_READY) updateProgramme(channel);
                }
                @Override public void onVideoSizeChanged(VideoSize size) {
                    if (generation != playGeneration) return;
                    if (size.width > 0 && size.height > 0) resolutionText = "Độ phân giải: " + size.width + " × " + size.height;
                    else resolutionText = "Độ phân giải: —";
                    updateStatsNow();
                }
                @Override public void onPlayerError(PlaybackException error) {
                    if (generation != playGeneration) return;
                    programme.setText("Không phát được kênh này trong khung Mobile • " + error.getErrorCodeName());
                    updateStatsNow();
                }
            });

            MediaItem item = media.build();
            String scheme = Uri.parse(channel.url()).getScheme();
            if ("rtsp".equalsIgnoreCase(scheme)) {
                player.setMediaSource(new RtspMediaSource.Factory()
                        .setForceUseRtpTcp(true).setUserAgent(ua).createMediaSource(item));
            } else {
                if ("udp".equalsIgnoreCase(scheme)) acquireMulticast();
                player.setMediaItem(item);
            }
            player.prepare();
            player.play();
            playerView.showController();
            statsHandler.removeCallbacks(statsTick);
            statsHandler.post(statsTick);
        } catch (Exception error) {
            releasePlayer();
            programme.setText("Không phát được kênh này trong khung Mobile • " + readable(error));
        }
    }

    private void resolveRemoteClearKey(Channel channel, DrmSpec drm, int generation) {
        Map<String, String> streamHeaders = new LinkedHashMap<>(channel.headers());
        drmIo.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(drm.license).openConnection();
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(20_000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                for (Map.Entry<String, String> entry : drm.headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
                String streamHost = Uri.parse(channel.url()).getHost();
                String licenseHost = Uri.parse(drm.license).getHost();
                if (streamHost != null && streamHost.equalsIgnoreCase(licenseHost)) {
                    for (String name : new String[]{"User-Agent", "Referer", "Origin", "Cookie"}) {
                        String value = streamHeaders.get(name);
                        if (value != null && !value.isEmpty() && !drm.headers.containsKey(name)) {
                            connection.setRequestProperty(name, value);
                        }
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
                    if (generation == playGeneration && currentChannel == channel) startInlinePlayer(channel, local, generation);
                });
            } catch (Exception error) {
                if (currentActivity != null) currentActivity.runOnUiThread(() -> {
                    if (generation == playGeneration && programme != null) programme.setText("Không lấy được giấy phép ClearKey cho kênh này");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void updateProgramme(Channel channel) {
        if (programme == null || channelList == null) return;
        try {
            Object adapter = channelList.getAdapter();
            if (!(adapter instanceof ChannelAdapter)) { programme.setText(channel.group()); return; }
            Field field = ChannelAdapter.class.getDeclaredField("guide");
            field.setAccessible(true);
            EpgStore.Guide guide = (EpgStore.Guide) field.get(adapter);
            EpgStore.Programme now = guide == null ? null : guide.find(channel);
            if (now == null) programme.setText(channel.group());
            else programme.setText(now.title + "  •  " + now.startText() + " - " + now.endText());
        } catch (Exception ignored) {
            programme.setText(channel.group());
        }
    }

    private void updateStatsNow() {
        if (stats == null) return;
        String fpsText = "FPS: —";
        if (player != null && videoCounters != null) {
            double value = fpsMeter.sample(android.os.SystemClock.elapsedRealtime(),
                    videoCounters.renderedOutputBufferCount, player.isPlaying());
            if (!Double.isNaN(value)) fpsText = String.format(Locale.ROOT, "FPS: %.1f", value);
            else if (player.isPlaying()) fpsText = "FPS: đang đo…";
        }
        stats.setText(resolutionText + "  •  " + fpsText);
    }

    /** First Back closes the inline video and returns to the channel list. */
    private boolean consumeBack() {
        if (panel == null || panel.getVisibility() != View.VISIBLE) return false;
        ++playGeneration;
        releasePlayer();
        currentChannel = null;
        panel.setVisibility(View.GONE);
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
            multicastLock = wifi.createMulticastLock("Nm7InlineUdp");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        }
    }

    private void releasePlayer() {
        statsHandler.removeCallbacks(statsTick);
        videoCounters = null;
        fpsMeter.reset();
        if (player != null) {
            if (playerView != null) playerView.setPlayer(null);
            player.release();
            player = null;
        }
        if (multicastLock != null) {
            try { if (multicastLock.isHeld()) multicastLock.release(); } catch (RuntimeException ignored) { }
            multicastLock = null;
        }
    }

    private void detach() {
        ++playGeneration;
        releasePlayer();
        uninstallBackHandling();
        if (channelList != null && originalClick != null) channelList.setOnItemClickListener(originalClick);
        if (panel != null && panel.getParent() instanceof LinearLayout) {
            ((LinearLayout) panel.getParent()).removeView(panel);
        }
        currentActivity = null;
        currentChannel = null;
        channelList = null;
        originalClick = null;
        panel = null;
        playerView = null;
        title = null;
        programme = null;
        stats = null;
    }

    private static String readable(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @TargetApi(33)
    private static final class Api33Back {
        static void register(MobileInlinePlayerProvider provider, Activity activity) {
            android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback = () -> {
                if (!provider.consumeBack()) activity.finish();
            };
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            provider.backDispatcher33 = dispatcher;
            provider.backCallback33 = callback;
        }
        static void unregister(MobileInlinePlayerProvider provider) {
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
        private final MobileInlinePlayerProvider provider;
        private final Window.Callback base;
        BackWindowCallback(MobileInlinePlayerProvider provider, Window.Callback base) { this.provider = provider; this.base = base; }
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
        @Override public void onWindowAttributesChanged(android.view.WindowManager.LayoutParams attrs) { base.onWindowAttributesChanged(attrs); }
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
    @Override public void onActivityPaused(Activity a) { if (a == currentActivity && player != null) player.pause(); }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String sort) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
