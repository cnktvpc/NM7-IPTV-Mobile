package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * TV-only stream recovery.
 *
 * Media3 stays the primary player. When a TV firmware decoder fails, NM7 first
 * tries adaptive 1080p and 720p. If MediaCodec still cannot decode the stream,
 * playback is handed to the TV-only VLC/FFmpeg fallback activity. For non-DRM
 * sources that Media3 cannot open because the server returns a bad HTTP status,
 * NM7 also tries VLC directly because its HTTP stack/user-agent behavior differs.
 */
@androidx.media3.common.util.UnstableApi
public final class TvStreamRecoveryProvider extends ContentProvider implements Application.ActivityLifecycleCallbacks {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<ExoPlayer, Player.Listener> attachedPlayers = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<PlayerActivity, DecoderState> decoderStates = Collections.synchronizedMap(new WeakHashMap<>());
    private PlayerActivity activePlayerActivity;

    @Override public boolean onCreate() {
        if (getContext() != null && AppPreferences.isPhysicalTv(getContext())) {
            ((Application) getContext().getApplicationContext()).registerActivityLifecycleCallbacks(this);
        }
        return true;
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof PlayerActivity) {
            activePlayerActivity = (PlayerActivity) activity;
            main.removeCallbacks(playerWatch);
            main.post(playerWatch);
        }
    }

    @Override public void onActivityPaused(Activity activity) {
        if (activity == activePlayerActivity) {
            activePlayerActivity = null;
            main.removeCallbacks(playerWatch);
        }
    }

    private final Runnable playerWatch = new Runnable() {
        @Override public void run() {
            PlayerActivity activity = activePlayerActivity;
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            attachRecovery(activity);
            main.postDelayed(this, 250L);
        }
    };

    private void attachRecovery(PlayerActivity activity) {
        ExoPlayer player = privatePlayer(activity);
        if (player == null || attachedPlayers.containsKey(player)) return;
        Player.Listener listener = new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                if (isDecoderError(error)) {
                    recoverDecoder(activity);
                } else if (isBadHttpStatus(error)) {
                    recoverHttpStatus(activity);
                }
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) markReady(activity, player);
            }
        };
        player.addListener(listener);
        attachedPlayers.put(player, listener);
        PlaybackException existing = player.getPlayerError();
        if (existing != null) {
            if (isDecoderError(existing)) recoverDecoder(activity);
            else if (isBadHttpStatus(existing)) recoverHttpStatus(activity);
        }
    }

    private static boolean isDecoderError(PlaybackException error) {
        String name = error == null ? null : error.getErrorCodeName();
        return name != null && name.contains("DECOD");
    }

    private static boolean isBadHttpStatus(PlaybackException error) {
        return error != null && error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS;
    }

    private void recoverHttpStatus(PlayerActivity activity) {
        // Never hand DRM playback to VLC: the fallback intentionally has no DRM/key path.
        if (!privateString(activity, "drmSystem").isEmpty()) return;
        String url = privateString(activity, "url");
        DecoderState state = decoderStates.get(activity);
        if (state == null || !url.equals(state.url)) {
            state = new DecoderState(url, privateInt(activity, "quality", Integer.MAX_VALUE));
            decoderStates.put(activity, state);
        }
        if (state.recovering) return;
        state.recovering = true;
        View error = activity.findViewById(R.id.playerError);
        if (error != null) error.setVisibility(View.GONE);
        TextView status = activity.findViewById(R.id.txtPlayerStatus);
        if (status != null) status.setText("Media3 bị máy chủ từ chối • đang thử VLC…");
        launchVlcFallback(activity, state);
    }

    private void recoverDecoder(PlayerActivity activity) {
        String url = privateString(activity, "url");
        DecoderState state = decoderStates.get(activity);
        if (state == null || !url.equals(state.url)) {
            state = new DecoderState(url, privateInt(activity, "quality", Integer.MAX_VALUE));
            decoderStates.put(activity, state);
        }
        if (state.recovering) return;
        state.recovering = true;

        View error = activity.findViewById(R.id.playerError);
        if (error != null) error.setVisibility(View.GONE);

        if (state.stage == 0) {
            state.stage++;
            restartAdaptive(activity, state, 1080);
        } else if (state.stage == 1) {
            state.stage++;
            restartAdaptive(activity, state, 720);
        } else {
            state.stage++;
            launchVlcFallback(activity, state);
        }
    }

    private void restartAdaptive(PlayerActivity activity, DecoderState state, int target) {
        TextView status = activity.findViewById(R.id.txtPlayerStatus);
        if (status != null) status.setText("Decoder TV lỗi • đang thử luồng tối đa " + target + "p…");
        int restoreQuality = state.originalQuality;
        main.postDelayed(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            try {
                setPrivateInt(activity, "quality", target);
                setPrivateLong(activity, "position", 0L);
                setPrivateBoolean(activity, "resumePlayback", true);
                invokePrivate(activity, "releasePlayer");
                invokePrivate(activity, "startPlayer");
            } catch (Exception ignored) {
                state.recovering = false;
                recoverDecoder(activity);
                return;
            } finally {
                setPrivateInt(activity, "quality", restoreQuality);
            }
            main.postDelayed(() -> {
                state.recovering = false;
                attachRecovery(activity);
                ExoPlayer current = privatePlayer(activity);
                PlaybackException existing = current == null ? null : current.getPlayerError();
                if (existing != null && isDecoderError(existing)) recoverDecoder(activity);
            }, 500L);
        }, 160L);
    }

    private void launchVlcFallback(PlayerActivity activity, DecoderState state) {
        String drmSystem = privateString(activity, "drmSystem");
        if (!drmSystem.isEmpty()) {
            state.recovering = false;
            showFinalDecoderError(activity,
                    "ERROR_CODE_DECODING_FAILED\nNguồn DRM không thể chuyển an toàn sang VLC fallback.\nHãy dùng luồng/chất lượng khác do nhà cung cấp hỗ trợ trên TV này.");
            return;
        }

        TextView status = activity.findViewById(R.id.txtPlayerStatus);
        if (status != null) status.setText("Đang chuyển sang VLC fallback…");
        main.postDelayed(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            try {
                invokePrivate(activity, "releasePlayer");
            } catch (Exception ignored) { }
            Intent fallback = new Intent();
            fallback.setClassName(activity, "vn.phuong.iptvplayer.VlcFallbackActivity");
            Bundle extras = activity.getIntent().getExtras();
            if (extras != null) fallback.putExtras(extras);
            activity.startActivity(fallback);
            activity.finish();
            state.recovering = false;
        }, 180L);
    }

    private void markReady(PlayerActivity activity, ExoPlayer current) {
        DecoderState state = decoderStates.get(activity);
        if (state != null) {
            state.recovering = false;
            state.readyPlayer = current;
            int stageSnapshot = state.stage;
            main.postDelayed(() -> {
                DecoderState now = decoderStates.get(activity);
                if (now == state && now.readyPlayer == current && current.isPlaying() && now.stage == stageSnapshot) {
                    now.stage = 0;
                }
            }, 30_000L);
        }
        View error = activity.findViewById(R.id.playerError);
        if (error != null) error.setVisibility(View.GONE);
    }

    private void showFinalDecoderError(PlayerActivity activity, String message) {
        View panel = activity.findViewById(R.id.playerError);
        TextView text = activity.findViewById(R.id.txtPlayerError);
        if (panel != null) panel.setVisibility(View.VISIBLE);
        if (text != null) text.setText(message);
    }

    private static ExoPlayer privatePlayer(PlayerActivity activity) {
        Object value = privateObject(activity, "player");
        return value instanceof ExoPlayer ? (ExoPlayer) value : null;
    }

    private static Object privateObject(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String privateString(Object target, String name) {
        Object value = privateObject(target, name);
        return value == null ? "" : value.toString();
    }

    private static int privateInt(Object target, String name, int fallback) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.getInt(target);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void setPrivateInt(Object target, String name, int value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setInt(target, value);
        } catch (Exception ignored) { }
    }

    private static void setPrivateLong(Object target, String name, long value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setLong(target, value);
        } catch (Exception ignored) { }
    }

    private static void setPrivateBoolean(Object target, String name, boolean value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(target, value);
        } catch (Exception ignored) { }
    }

    private static void invokePrivate(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static final class DecoderState {
        final String url;
        final int originalQuality;
        int stage;
        boolean recovering;
        ExoPlayer readyPlayer;
        DecoderState(String url, int originalQuality) {
            this.url = url;
            this.originalQuality = originalQuality;
        }
    }

    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityStarted(Activity a) { }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    @Override public void onActivityDestroyed(Activity a) {
        decoderStates.remove(a);
        if (a == activePlayerActivity) activePlayerActivity = null;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String sort) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
