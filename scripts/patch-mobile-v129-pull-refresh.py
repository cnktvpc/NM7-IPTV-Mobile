#!/usr/bin/env python3
"""Add YouTube-like pull-to-refresh to NM7 Mobile Browse, on home only."""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
JAVA = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"
LAYOUT = ROOT / "smarttubedroid/src/main/res/layout/browse_activity.xml"
GRADLE = ROOT / "smarttubedroid/build.gradle"
APP_MODULE_GRADLE = Path("smarttube/build.gradle.kts")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"v129 pull-refresh: expected one {label} anchor, found {count}")
    return text.replace(old, new, 1)


for path in (JAVA, LAYOUT, GRADLE):
    if not path.is_file():
        raise SystemExit(f"v129 pull-refresh: required source missing: {path}")

# The refresh container is deliberately local to Browse, not playback/IPTV.
gradle = GRADLE.read_text(encoding="utf-8")
dep_anchor = "    implementation 'androidx.recyclerview:recyclerview:' + recyclerviewXVersion"
gradle = replace_once(
    gradle,
    dep_anchor,
    dep_anchor + "\n    implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'",
    "SwipeRefreshLayout dependency"
)
GRADLE.write_text(gradle, encoding="utf-8")
 
# The checked-in project compiles these sources through the root :smarttube module.
if not APP_MODULE_GRADLE.is_file():
    raise SystemExit(f"v129 pull-refresh: required app module build file missing: {APP_MODULE_GRADLE}")
module_gradle = APP_MODULE_GRADLE.read_text(encoding="utf-8")
module_anchor = '    implementation("androidx.recyclerview:recyclerview:1.2.1")'
module_gradle = replace_once(
    module_gradle,
    module_anchor,
    module_anchor + '\n    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")',
    "root :smarttube SwipeRefreshLayout dependency"
)
APP_MODULE_GRADLE.write_text(module_gradle, encoding="utf-8")

layout = LAYOUT.read_text(encoding="utf-8")
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
        android:enabled="false">

        <FrameLayout
            android:id="@+id/browse_content"
            android:layout_width="match_parent"
            android:layout_height="match_parent">'''
layout = replace_once(layout, frame_open, swipe_open, "browse_content wrapper")
layout = replace_once(
    layout,
    "    </FrameLayout>\n</LinearLayout>",
    "        </FrameLayout>\n    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>\n</LinearLayout>",
    "browse_content closing wrapper"
)
LAYOUT.write_text(layout, encoding="utf-8")

java = JAVA.read_text(encoding="utf-8")
java = replace_once(
    java,
    "import androidx.recyclerview.widget.RecyclerView;",
    "import androidx.recyclerview.widget.RecyclerView;\nimport androidx.swiperefreshlayout.widget.SwipeRefreshLayout;",
    "SwipeRefreshLayout import"
)
java = replace_once(
    java,
    "    private ProgressBar mProgressBar;",
    "    private ProgressBar mProgressBar;\n    private SwipeRefreshLayout mPullToRefresh;",
    "refresh view field"
)
java = replace_once(
    java,
    "        mProgressBar = findViewById(R.id.browse_progress);",
    "        mProgressBar = findViewById(R.id.browse_progress);\n        mPullToRefresh = findViewById(R.id.browse_pull_refresh);",
    "refresh view binding"
)

refresh_setup_anchor = "        mSettingsView.setAdapter(mSettingsAdapter);"
refresh_setup = '''        mSettingsView.setAdapter(mSettingsAdapter);

        // YouTube-style pull-to-refresh. Enabled only for Home and only when the feed is at top.
        mPullToRefresh.setColorSchemeColors(0xFFFF0033);
        mPullToRefresh.setDistanceToTriggerSync(
                (int) (getResources().getDisplayMetrics().density * 88f));
        mPullToRefresh.setOnChildScrollUpCallback((parent, child) -> {
            RecyclerView current = getCurrentContentView();
            return current == null || current.canScrollVertically(-1);
        });
        mPullToRefresh.setOnRefreshListener(() -> {
            if (!isHomeSection()) {
                mPullToRefresh.setRefreshing(false);
                return;
            }
            // Reuse the presenter; don't recreate Activities or reset tab/playback.
            mBrowsePresenter.refresh(false);
        });'''
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
java = replace_once(java, focus_anchor, focus_replacement, "home-section refresh gating")

progress_anchor = "        runOnUiThread(() -> mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE));"
progress_replacement = '''        runOnUiThread(() -> {
            mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            if (!show && mPullToRefresh != null && mPullToRefresh.isRefreshing()) {
                mPullToRefresh.setRefreshing(false);
            }
        });'''
java = replace_once(java, progress_anchor, progress_replacement, "refresh completion")
java = replace_once(
    java,
    "        showContentView(mErrorContainer);",
    "        if (mPullToRefresh != null) mPullToRefresh.setRefreshing(false);\n        showContentView(mErrorContainer);",
    "error refresh cleanup"
)

helper_anchor = "    @Nullable\n    private RecyclerView getCurrentContentView() {"
helper = '''    private boolean isHomeSection() {
        return mCurrentSection != null
                && mCurrentSection.getId()
                == com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_HOME;
    }

'''
java = replace_once(java, helper_anchor, helper + helper_anchor, "home section helper")
JAVA.write_text(java, encoding="utf-8")

print("NM7 Mobile v1.10.129 pull-to-refresh patch applied.")
