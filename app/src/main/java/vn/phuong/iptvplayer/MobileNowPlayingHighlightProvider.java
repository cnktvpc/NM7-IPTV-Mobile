package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;

/** Keeps the currently playing inline Mobile channel visually highlighted. */
@UnstableApi
public final class MobileNowPlayingHighlightProvider extends ContentProvider implements Application.ActivityLifecycleCallbacks {
    private MainActivity currentActivity;
    private ListView channelList;
    private View mainRoot;
    private AdapterView.OnItemClickListener delegateClick;
    private AdapterView.OnItemClickListener wrappedClick;
    private View.OnLayoutChangeListener layoutListener;

        private static boolean lifecycleRegistered;

    static synchronized void init(Application app) {
        if (lifecycleRegistered || app == null) return;
        
        lifecycleRegistered = true;
        MobileNowPlayingHighlightProvider owner = new MobileNowPlayingHighlightProvider();
        
        app.registerActivityLifecycleCallbacks(owner);
    }

    @Override public boolean onCreate() {
        if (getContext() != null) {
            init((Application) getContext().getApplicationContext());
        }
        return true;
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity) || AppPreferences.isTvInterface(activity)) return;
        activity.getWindow().getDecorView().post(() -> attach((MainActivity) activity));
    }

    private void attach(MainActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        ListView list = activity.findViewById(R.id.listChannels);
        View root = activity.findViewById(R.id.mainRoot);
        if (list == null || root == null) return;

        if (currentActivity == activity && channelList == list && list.getOnItemClickListener() == wrappedClick) {
            syncVisibility();
            return;
        }
        detach(false);
        currentActivity = activity;
        channelList = list;
        mainRoot = root;
        delegateClick = list.getOnItemClickListener();
        AdapterView.OnItemClickListener original = delegateClick;
        wrappedClick = (parent, view, position, id) -> {
            if (original != null) original.onItemClick(parent, view, position, id);
            Object item = parent.getAdapter() == null ? null : parent.getAdapter().getItem(position);
            if (item instanceof Channel && parent.getAdapter() instanceof ChannelAdapter) {
                ((ChannelAdapter) parent.getAdapter()).setPlayingChannel((Channel) item);
            }
            if (mainRoot != null) mainRoot.post(this::syncVisibility);
        };
        list.setOnItemClickListener(wrappedClick);

        layoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> syncVisibility();
        root.addOnLayoutChangeListener(layoutListener);
        syncVisibility();
    }

    private void syncVisibility() {
        if (channelList == null || !(channelList.getAdapter() instanceof ChannelAdapter)) return;
        if (!MobileInlinePlayerProviderV2.isPanelVisible() && !containsVisiblePlayer(mainRoot)) {
            ((ChannelAdapter) channelList.getAdapter()).setPlayingChannel(null);
        }
    }

    private boolean containsVisiblePlayer(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof PlayerView) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsVisiblePlayer(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private void detach(boolean clearHighlight) {
        if (mainRoot != null && layoutListener != null) mainRoot.removeOnLayoutChangeListener(layoutListener);
        if (channelList != null) {
            if (clearHighlight && channelList.getAdapter() instanceof ChannelAdapter) {
                ((ChannelAdapter) channelList.getAdapter()).setPlayingChannel(null);
            }
            if (channelList.getOnItemClickListener() == wrappedClick) channelList.setOnItemClickListener(delegateClick);
        }
        currentActivity = null;
        channelList = null;
        mainRoot = null;
        delegateClick = null;
        wrappedClick = null;
        layoutListener = null;
    }

    @Override public void onActivityDestroyed(Activity activity) {
        if (activity == currentActivity) detach(true);
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
