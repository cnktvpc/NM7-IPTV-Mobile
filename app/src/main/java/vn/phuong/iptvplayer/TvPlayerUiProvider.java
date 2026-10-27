package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;

import java.lang.reflect.Method;

/** TV-only player chrome: clean startup, transparent bars, and one gear menu. */
@UnstableApi
public final class TvPlayerUiProvider extends ContentProvider implements Application.ActivityLifecycleCallbacks {
    @Override public boolean onCreate() {
        if (getContext() != null) {
            ((Application) getContext().getApplicationContext()).registerActivityLifecycleCallbacks(this);
        }
        return true;
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        if (activity instanceof PlayerActivity && AppPreferences.isTvInterface(activity)) {
            // Activity.onCreate has already inflated the player view, but onStart has
            // not started playback yet. Hide controller chrome here to avoid startup flash.
            tune((PlayerActivity) activity);
        }
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof PlayerActivity) || !AppPreferences.isTvInterface(activity)) return;
        activity.getWindow().getDecorView().post(() -> tune((PlayerActivity) activity));
    }

    private void tune(PlayerActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        PlayerView playerView = activity.findViewById(R.id.playerView);
        if (playerView == null) return;

        playerView.setControllerAutoShow(false);
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerShowTimeoutMs(4500);

        View header = activity.findViewById(R.id.playerHeader);
        if (header != null) {
            header.setBackgroundColor(Color.TRANSPARENT);
            header.setVisibility(View.GONE);
        }

        hide(activity, R.id.btnFormat);
        hide(activity, R.id.btnQuality);
        hide(activity, R.id.btnResize);

        // Media3 draws its own translucent controller gradients. Remove only the
        // chrome backgrounds while keeping buttons, timeline, captions and gear visible.
        View controlsBackground = named(activity, playerView, "exo_controls_background");
        if (controlsBackground != null) controlsBackground.setVisibility(View.GONE);
        clearBackground(named(activity, playerView, "exo_bottom_bar"));
        clearBackground(named(activity, playerView, "exo_top_bar"));
        clearBackground(named(activity, playerView, "exo_minimal_controls"));

        View settings = named(activity, playerView, "exo_settings");
        if (settings != null) {
            settings.setVisibility(View.VISIBLE);
            settings.setContentDescription("Tùy chọn phát");
            settings.setOnClickListener(v -> showPlaybackMenu(activity));
        }

        // Opening a TV channel should show only the video. OK/Enter is already
        // handled by PlayerActivity and will reveal the controller on demand.
        playerView.hideController();
        playerView.requestFocus();
    }

    private void showPlaybackMenu(PlayerActivity activity) {
        String[] labels = {"Định dạng nguồn", "Chất lượng", "Khung hình"};
        String[] methods = {"chooseFormat", "chooseQuality", "chooseResizeMode"};
        new AlertDialog.Builder(activity)
                .setTitle("Tùy chọn phát")
                .setItems(labels, (dialog, which) -> invoke(activity, methods[which]))
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void invoke(PlayerActivity activity, String methodName) {
        try {
            Method method = PlayerActivity.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(activity);
        } catch (Exception error) {
            new AlertDialog.Builder(activity)
                    .setTitle("Tùy chọn phát")
                    .setMessage("Không mở được mục này.")
                    .setPositiveButton("Đóng", null)
                    .show();
        }
    }

    private static void hide(Activity activity, int id) {
        View view = activity.findViewById(id);
        if (view != null) view.setVisibility(View.GONE);
    }

    private static View named(Activity activity, PlayerView playerView, String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : playerView.findViewById(id);
    }

    private static void clearBackground(View view) {
        if (view == null) return;
        view.setBackground(null);
        if (view instanceof ImageView) ((ImageView) view).setImageDrawable(null);
    }

    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
