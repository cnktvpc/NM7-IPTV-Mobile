package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.media3.common.util.UnstableApi;

/** Adds fluid horizontal category swipes while leaving ListView vertical scrolling/clicks native. */
@UnstableApi
public final class MobileGroupSwipeProvider extends ContentProvider implements Application.ActivityLifecycleCallbacks {
    private MainActivity activity;
    private ListView list;
    private LinearLayout groupRow;

        private static boolean lifecycleRegistered;

    static synchronized void init(Application app) {
        if (lifecycleRegistered || app == null) return;
        if (AppPreferences.isPhysicalTv(app)) return;
        lifecycleRegistered = true;
        MobileGroupSwipeProvider owner = new MobileGroupSwipeProvider();
        
        app.registerActivityLifecycleCallbacks(owner);
    }

    @Override public boolean onCreate() {
        if (getContext() != null) {
            init((Application) getContext().getApplicationContext());
        }
        return true;
    }

    @Override public void onActivityResumed(Activity resumed) {
        if (!(resumed instanceof MainActivity) || AppPreferences.isTvInterface(resumed)) return;
        MainActivity main = (MainActivity) resumed;
        if (activity == main && list != null) return;
        detach();
        activity = main;
        list = main.findViewById(R.id.listChannels);
        groupRow = main.findViewById(R.id.groupRow);
        if (list == null || groupRow == null) return;

        list.setDividerHeight(dp(main, 1));
        list.setOnItemLongClickListener(null);
        list.setLongClickable(false);

        if (list instanceof Nm7SuperOkGroupSwipeListView) {
            ((Nm7SuperOkGroupSwipeListView) list).setOnGroupSwipeListener(this::switchGroup);
        } else {
            android.util.Log.e("NM7GroupSwipe", "Expected Nm7SuperOkGroupSwipeListView in both layouts");
        }
    }

    private boolean switchGroup(int direction) {
        if (groupRow == null || groupRow.getChildCount() == 0 || list == null) return false;
        int active = 0;
        for (int i = 0; i < groupRow.getChildCount(); i++) {
            if (groupRow.getChildAt(i).isSelected()) { active = i; break; }
        }
        int count = groupRow.getChildCount();
        int next = Math.max(0, Math.min(count - 1, active + direction));
        if (next == active) return false;

        View target = groupRow.getChildAt(next);
        target.performClick();
        list.post(() -> list.setSelection(0));

        View parent = (View) groupRow.getParent();
        if (parent instanceof HorizontalScrollView) {
            int viewport = parent.getWidth();
            int x = Math.max(0, target.getLeft() - Math.max(dp(activity, 12), (viewport - target.getWidth()) / 2));
            parent.post(() -> ((HorizontalScrollView) parent).smoothScrollTo(x, 0));
        }
        return true;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void detach() {
        if (list instanceof Nm7SuperOkGroupSwipeListView) {
            ((Nm7SuperOkGroupSwipeListView) list).setOnGroupSwipeListener(null);
        }
        activity = null;
        list = null;
        groupRow = null;
    }

    @Override public void onActivityDestroyed(Activity destroyed) { if (destroyed == activity) detach(); }
    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityStarted(Activity a) { }
    @Override public void onActivityPaused(Activity a) { }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String sort) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
