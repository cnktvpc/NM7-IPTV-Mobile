#!/usr/bin/env python3
"""Add robust YouTube Home & feed pull-to-refresh for NM7 Mobile v1.10.130+."""
from pathlib import Path
import re

ROOT = Path("third_party/SmartTube-droid")
JAVA = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"
LAYOUT = ROOT / "smarttubedroid/src/main/res/layout/browse_activity.xml"
UPSTREAM_GRADLE = ROOT / "smarttubedroid/build.gradle"
APP_MODULE_GRADLE = Path("smarttube/build.gradle.kts")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"v130 pull-refresh: expected one {label} anchor, found {count}")
    return text.replace(old, new, 1)


for path in (JAVA, LAYOUT, UPSTREAM_GRADLE, APP_MODULE_GRADLE):
    if not path.is_file():
        raise SystemExit(f"v130 pull-refresh: required source missing: {path}")

# 1. Keep dependency declared in both upstream library metadata and the actual :smarttube module.
gradle = UPSTREAM_GRADLE.read_text(encoding="utf-8")
dep_line = "    implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'"
if dep_line not in gradle:
    dep_anchor = "    implementation 'androidx.recyclerview:recyclerview:' + recyclerviewXVersion"
    gradle = replace_once(
        gradle,
        dep_anchor,
        dep_anchor + "\n" + dep_line,
        "upstream SwipeRefreshLayout dependency",
    )
    UPSTREAM_GRADLE.write_text(gradle, encoding="utf-8")

module_gradle = APP_MODULE_GRADLE.read_text(encoding="utf-8")
module_dep_line = '    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")'
if module_dep_line not in module_gradle:
    module_anchor = '    implementation("androidx.recyclerview:recyclerview:1.2.1")'
    module_gradle = replace_once(
        module_gradle,
        module_anchor,
        module_anchor + "\n" + module_dep_line,
        "actual :smarttube SwipeRefreshLayout dependency",
    )
    APP_MODULE_GRADLE.write_text(module_gradle, encoding="utf-8")

# 2. Place a SwipeRefreshLayout (enabled by default) around the exact content pane used by BrowseActivity,
# and remove the 8dp top padding on browse_rows that caused canScrollVertically(-1) false positives at top.
layout = LAYOUT.read_text(encoding="utf-8")
if 'android:id="@+id/browse_pull_refresh"' not in layout:
    frame_open = '''    <FrameLayout
        android:id="@+id/browse_content"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">'''
    swipe_open = '''    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/browse_pull_refresh"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:enabled="true">

        <FrameLayout
            android:id="@+id/browse_content"
            android:layout_width="match_parent"
            android:layout_height="match_parent">'''
    layout = replace_once(layout, frame_open, swipe_open, "browse_content wrapper")
    layout = replace_once(
        layout,
        "    </FrameLayout>\n</LinearLayout>",
        "        </FrameLayout>\n    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>\n</LinearLayout>",
        "browse_content closing wrapper",
    )
else:
    layout = layout.replace('android:id="@+id/browse_pull_refresh"\n        android:layout_width="match_parent"\n        android:layout_height="0dp"\n        android:layout_weight="1"\n        android:enabled="false">',
                            'android:id="@+id/browse_pull_refresh"\n        android:layout_width="match_parent"\n        android:layout_height="0dp"\n        android:layout_weight="1"\n        android:enabled="true">')

layout = layout.replace(
    'android:id="@+id/browse_rows"\n            android:layout_width="match_parent"\n            android:layout_height="match_parent"\n            android:clipToPadding="false"\n            android:paddingTop="8dp"',
    'android:id="@+id/browse_rows"\n            android:layout_width="match_parent"\n            android:layout_height="match_parent"\n            android:clipToPadding="false"\n            android:paddingTop="0dp"',
)
LAYOUT.write_text(layout, encoding="utf-8")

# 3. Patch BrowseActivity.java (idempotent).
java = JAVA.read_text(encoding="utf-8")
if "private SwipeRefreshLayout mPullToRefresh;" not in java:
    if "import android.view.MotionEvent;" not in java:
        java = replace_once(
            java,
            "import android.view.MenuItem;",
            "import android.view.MenuItem;\nimport android.view.MotionEvent;",
            "MotionEvent import",
        )
    if "import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;" not in java:
        java = replace_once(
            java,
            "import androidx.recyclerview.widget.RecyclerView;",
            "import androidx.recyclerview.widget.RecyclerView;\nimport androidx.swiperefreshlayout.widget.SwipeRefreshLayout;",
            "SwipeRefreshLayout import",
        )
    java = replace_once(
        java,
        "    private ProgressBar mProgressBar;",
        """    private ProgressBar mProgressBar;
    private SwipeRefreshLayout mPullToRefresh;
    private float mPullStartRawX;
    private float mPullStartRawY;
    private boolean mPullStartEligible;
    private boolean mPullGestureCancelled;
    private boolean mNativeRefreshHandledThisGesture;
    private boolean mPullRefreshPendingClear;
    private long mPullRefreshStartedAt;
    private final Runnable mPullRefreshTimeout = this::finishPullRefresh;""",
        "pull-refresh state fields",
    )
    java = replace_once(
        java,
        "        mProgressBar = findViewById(R.id.browse_progress);",
        "        mProgressBar = findViewById(R.id.browse_progress);\n        mPullToRefresh = findViewById(R.id.browse_pull_refresh);",
        "refresh view binding",
    )
    refresh_setup_anchor = "        mSettingsView.setAdapter(mSettingsAdapter);"
    refresh_setup = '''        mSettingsView.setAdapter(mSettingsAdapter);

        // Keep AndroidX's native pull indicator/interception, with an Activity-level
        // gesture fallback below for the nested rows/cards used by the YouTube feed.
        mPullToRefresh.setEnabled(true);
        mPullToRefresh.setColorSchemeColors(0xFFFF0033);
        mPullToRefresh.setDistanceToTriggerSync(
                (int) (getResources().getDisplayMetrics().density * 56f));
        mPullToRefresh.setOnChildScrollUpCallback((parent, child) -> !isCurrentFeedAtTop());
        mPullToRefresh.setOnRefreshListener(this::onPullToRefresh);'''
    java = replace_once(java, refresh_setup_anchor, refresh_setup, "refresh callback setup")

    focus_anchor = '''        BrowseSection section = mSections.get(position);
        mCurrentSection = section;
        showContentForType(section.getType());'''
    focus_replacement = '''        BrowseSection section = mSections.get(position);
        mCurrentSection = section;
        if (mPullToRefresh != null) {
            mPullToRefresh.setEnabled(isHomeSection());
        }
        showContentForType(section.getType());'''
    java = replace_once(java, focus_anchor, focus_replacement, "Home-only enablement")

    # Ensure fresh data arriving after pull-to-refresh clears stale ConcatAdapter entries,
    # scrolls to top, and stops the pull-to-refresh spinner cleanly.
    row_anchor = "        if (type == BrowseSection.TYPE_ROW) {"
    row_replacement = '''        if (mPullToRefresh != null) {
            mPullToRefresh.setEnabled(isHomeSection());
        }
        if (mPullRefreshPendingClear && group != null && !group.isEmpty()) {
            mPullRefreshPendingClear = false;
            if (type == BrowseSection.TYPE_ROW && mRowsAdapter != null) {
                mRowsAdapter.clear();
            } else if (mGridAdapter != null) {
                mGridAdapter.clear();
            }
            RecyclerView refreshedView = getCurrentContentView();
            if (refreshedView != null) {
                refreshedView.stopScroll();
                refreshedView.scrollToPosition(0);
            }
            finishPullRefresh();
        }
        if (type == BrowseSection.TYPE_ROW) {'''
    java = replace_once(java, row_anchor, row_replacement, "updateSection pull-refresh clear")

    touch_anchor = "    // ------------------------------------------------------------------ init\n"
    touch_methods = '''    private void trackPullDownGesture(MotionEvent event) {
        if (mPullToRefresh == null) {
            return;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mPullStartRawX = event.getRawX();
                mPullStartRawY = event.getRawY();
                mPullStartEligible = isHomeSection()
                        && !mPullToRefresh.isRefreshing()
                        && isTouchInsidePullArea(event)
                        && isCurrentFeedAtTop();
                mPullGestureCancelled = false;
                mNativeRefreshHandledThisGesture = false;
                if (mPullStartEligible) {
                    mPullToRefresh.setEnabled(true);
                    mPullToRefresh.requestDisallowInterceptTouchEvent(false);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mPullStartEligible && !mPullGestureCancelled
                        && isHomeSection() && !mPullToRefresh.isRefreshing()
                        && isTouchInsidePullArea(event) && isCurrentFeedAtTop()) {
                    mPullStartRawX = event.getRawX();
                    mPullStartRawY = event.getRawY();
                    mPullStartEligible = true;
                }
                if (mPullStartEligible) {
                    float dx = event.getRawX() - mPullStartRawX;
                    float dy = event.getRawY() - mPullStartRawY;
                    float slop = getResources().getDisplayMetrics().density * 8f;
                    if (dy < -slop || Math.abs(dx) > Math.abs(dy) * 1.25f) {
                        mPullGestureCancelled = true;
                    } else if (dy > slop && dy > Math.abs(dx) * 1.2f) {
                        mPullToRefresh.requestDisallowInterceptTouchEvent(false);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mPullStartEligible && !mPullGestureCancelled) {
                    float dx = event.getRawX() - mPullStartRawX;
                    float dy = event.getRawY() - mPullStartRawY;
                    float threshold = getResources().getDisplayMetrics().density * 96f;
                    float triggerPx = getResources().getDisplayMetrics().density * 52f;
                    if ((dy >= threshold || dy >= triggerPx) && dy > Math.abs(dx) * 1.2f) {
                        mPullToRefresh.post(() -> {
                            // Run only if native SwipeRefreshLayout did not already handle it.
                            if (mPullToRefresh != null && !mNativeRefreshHandledThisGesture
                                    && !mPullToRefresh.isRefreshing()
                                    && isHomeSection() && isCurrentFeedAtTop()) {
                                mPullToRefresh.setRefreshing(true);
                                onPullToRefresh();
                            }
                        });
                    }
                }
                mPullStartEligible = false;
                mPullGestureCancelled = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                mPullStartEligible = false;
                mPullGestureCancelled = false;
                break;
            default:
                break;
        }
    }

    private boolean isTouchInsidePullArea(MotionEvent event) {
        if (isNm7MiniTouch(event) || isNm7ChromeTouch(event)) {
            return false;
        }
        if (mPullToRefresh.getWidth() <= 0 || mPullToRefresh.getHeight() <= 0) {
            return true;
        }
        int[] location = new int[2];
        mPullToRefresh.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0] && x < location[0] + mPullToRefresh.getWidth()
                && y >= location[1] && y < location[1] + mPullToRefresh.getHeight();
    }

    private boolean isHomeSection() {
        if (mCurrentSection == null) {
            return true;
        }
        if (mCurrentSection.getId()
                == com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_HOME) {
            return true;
        }
        return mCurrentSection.getType() != BrowseSection.TYPE_SETTINGS_GRID;
    }

    private boolean isCurrentFeedAtTop() {
        RecyclerView current = getCurrentContentView();
        if (current == null) {
            if (mRowsView != null && mRowsView.getVisibility() == View.VISIBLE) {
                current = mRowsView;
            } else if (mGridView != null && mGridView.getVisibility() == View.VISIBLE) {
                current = mGridView;
            }
        }
        if (current == null) {
            return true;
        }
        if (!current.canScrollVertically(-1) || current.getChildCount() == 0) {
            return true;
        }
        View first = current.getChildAt(0);
        if (first == null) {
            return true;
        }
        int pos = current.getChildAdapterPosition(first);
        if (pos > 0) {
            return false;
        }
        int slopPx = (int) (getResources().getDisplayMetrics().density * 24f);
        return first.getTop() >= current.getPaddingTop() - slopPx;
    }

    private void onPullToRefresh() {
        mNativeRefreshHandledThisGesture = true;
        if (!isHomeSection()) {
            finishPullRefresh();
            return;
        }

        mPullRefreshPendingClear = true;
        mPullRefreshStartedAt = android.os.SystemClock.elapsedRealtime();
        mPullToRefresh.removeCallbacks(mPullRefreshTimeout);
        mPullToRefresh.postDelayed(mPullRefreshTimeout, 20_000L);
        // Reuse the current BrowsePresenter reload path; do not recreate the Activity.
        mBrowsePresenter.refresh(false);
    }

    private void finishPullRefresh() {
        mPullRefreshPendingClear = false;
        if (mPullToRefresh != null) {
            mPullToRefresh.removeCallbacks(mPullRefreshTimeout);
            if (mPullToRefresh.isRefreshing()) {
                mPullToRefresh.setRefreshing(false);
            }
        }
    }

'''
    dispatch_pattern = re.compile(
        r'(?m)^(?P<indent>[ \t]*)public\s+boolean\s+dispatchTouchEvent\s*'
        r'\(\s*(?:(?:@[\w.]+(?:\([^)]*\))?)\s*)*(?:(?:final)\s+)?'
        r'(?:android\.view\.)?MotionEvent\s+(?P<arg>\w+)\s*\)\s*\{'
    )
    matches = list(dispatch_pattern.finditer(java))
    if len(matches) > 1:
        raise SystemExit(f"v130 pull-refresh: expected <=1 Activity dispatcher, found {len(matches)}")
    if matches:
        match = matches[0]
        arg = match.group("arg")
        indent = match.group("indent") + "    "
        method_end_probe = java[match.end():match.end() + 500]
        call = f"trackPullDownGesture({arg});"
        if call not in method_end_probe:
            java = java[:match.end()] + "\n" + indent + call + java[match.end():]
    else:
        dispatch = '''    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        trackPullDownGesture(event);
        return super.dispatchTouchEvent(event);
    }

'''
        java = replace_once(java, touch_anchor, dispatch + touch_anchor, "Activity-level gesture dispatcher")
    java = replace_once(java, touch_anchor, touch_methods + touch_anchor, "Activity-level gesture tracker")

    progress_anchor = "        runOnUiThread(() -> mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE));"
    progress_replacement = '''        runOnUiThread(() -> {
            if (mPullToRefresh != null && mPullToRefresh.isRefreshing()) {
                mProgressBar.setVisibility(View.GONE);
            } else {
                mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
            if (!show && (!mPullRefreshPendingClear
                    || android.os.SystemClock.elapsedRealtime() - mPullRefreshStartedAt > 1_200L)) {
                finishPullRefresh();
            }
        });'''
    java = replace_once(java, progress_anchor, progress_replacement, "refresh completion")
    java = replace_once(
        java,
        "        showContentView(mErrorContainer);",
        "        finishPullRefresh();\n        showContentView(mErrorContainer);",
        "error refresh cleanup",
    )
    JAVA.write_text(java, encoding="utf-8")

print("NM7 Mobile v1.10.130 Home pull-to-refresh fallback patch applied.")
