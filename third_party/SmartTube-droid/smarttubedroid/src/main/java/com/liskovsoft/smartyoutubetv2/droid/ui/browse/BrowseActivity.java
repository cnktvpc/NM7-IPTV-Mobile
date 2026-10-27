package com.liskovsoft.smartyoutubetv2.droid.ui.browse;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textview.MaterialTextView;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoGroupAdapter;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7FeedAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Home screen of the touch UI. Implements common's {@link BrowseView}:
 * a top toolbar (search/account/refresh), a horizontally scrollable {@link TabLayout}
 * of dynamic sections and a content area that swaps between a 2-column video grid,
 * a rows list and a settings list depending on {@link BrowseSection#getType()}.
 * All data comes from {@link BrowsePresenter} — this class contains zero business logic.
 */
public class BrowseActivity extends DroidActivity implements BrowseView {
    private static final String TAG = BrowseActivity.class.getSimpleName();
    private void installNm7MiniPlayer() {
        try {
            Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer")
                    .getMethod("attach", android.app.Activity.class).invoke(null, this);
        } catch (ReflectiveOperationException error) {
            android.util.Log.e("NM7Playback", "Native Browse mini-player", error);
        }
    }

    public void nm7OpenLibrary() {
        mBrowsePresenter.selectSection(com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_USER_PLAYLISTS);
    }

    public void nm7OpenSettings() {
        mBrowsePresenter.selectSection(com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_SETTINGS);
    }

    private static final int GRID_COLUMNS = 1;

    // NM7 1.10.43: observe horizontal section swipes at Activity dispatch level.
    private android.view.View mNm7BrowseContent;
    private float mNm7SwipeDownX;
    private float mNm7SwipeDownY;
    private boolean mNm7SwipeEligible;

    private boolean isNm7MiniTouch(android.view.MotionEvent event) {
        try {
            return Boolean.TRUE.equals(Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer")
                    .getMethod("containsPoint", float.class, float.class)
                    .invoke(null, event.getRawX(), event.getRawY()));
        } catch (ReflectiveOperationException error) { return false; }
    }

    private boolean mNm7SwipeVertical;
    private boolean mNm7SwipeMultiple;
    private int mNm7SwipeSlop;
    private final com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7BrowseGesture mNm7Gesture =
            new com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7BrowseGesture();
    private boolean mNm7ChromeHidden;
    private Runnable mNm7SectionRequest;
    private int mNm7ScrollDistance;
    private final com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7ChromeGesture mNm7ChromeGesture =
            new com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7ChromeGesture();

    private boolean isNm7ChromeTouch(android.view.MotionEvent event) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        View header = findViewById(R.id.browse_app_bar);
        View footer = getWindow().getDecorView().findViewWithTag("nm7_home_tab_bar");
        for (View view : new View[]{header, footer}) {
            if (view != null && view.isShown() && view.getGlobalVisibleRect(bounds)
                    && bounds.contains((int) event.getRawX(), (int) event.getRawY())) return true;
        }
        return false;
    }

    private void setNm7ChromeHidden(boolean hidden) {
        // v122: normal-flow header; no overlay transition.
    }

    private void installNm7ScrollChrome(RecyclerView list) {
        // v122: normal-flow feed; no synthetic header padding.
    }

    private BrowsePresenter mBrowsePresenter;
    /** Mirrors the tab order. Index in this list == tab position. */
    private final List<BrowseSection> mSections = new ArrayList<>();
    private TabLayout mTabLayout;
    private RecyclerView mGridView;
    private RecyclerView mRowsView;
    private RecyclerView mSettingsView;
    private View mErrorContainer;
    private MaterialTextView mErrorMessage;
    private MaterialButton mErrorAction;
    private ProgressBar mProgressBar;
    private SwipeRefreshLayout mPullToRefresh;
    private float mPullStartRawX;
    private float mPullStartRawY;
    private boolean mPullStartEligible;
    private boolean mPullGestureCancelled;
    private boolean mNativeRefreshHandledThisGesture;
    private boolean mPullRefreshPendingClear;
    private long mPullRefreshStartedAt;
    private final Runnable mPullRefreshTimeout = this::finishPullRefresh;
    private VideoGroupAdapter mGridAdapter;
    private Nm7FeedAdapter mRowsAdapter;
    private BrowseSettingsAdapter mSettingsAdapter;
    private BrowseSection mCurrentSection;
    /** Ignore TabLayout callbacks while tabs are being (re)built programmatically. */
    private boolean mSuppressTabEvents;
    /** Skip the onViewResumed() that immediately follows onCreate (mirrors TV's BrowseFragment). */
    private boolean mJustCreated;
    private boolean mNm7FirstFeedLayoutFixed;

    private final VideoGroupAdapter.Listener mCardListener = new VideoGroupAdapter.Listener() {
        @Override
        public void onVideoClicked(Video item) {
            mBrowsePresenter.onVideoItemClicked(item);
        }

        @Override
        public void onVideoLongClicked(Video item) {
            // Selected first: keeps presenter's mCurrentVideo/multi-grid state in sync
            mBrowsePresenter.onVideoItemSelected(item);
            mBrowsePresenter.onVideoItemLongClicked(item);
        }

        @Override
        public void onScrollEnd(Video item) {
            mBrowsePresenter.onScrollEnd(item);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.browse_activity);
        installNm7YoutubeChrome();

        mBrowsePresenter = BrowsePresenter.instance(this);
        mBrowsePresenter.setView(this);

        initToolbar();
        initTabs();
        initContent();

        mJustCreated = true;

        mBrowsePresenter.onViewInitialized();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mJustCreated) {
            mBrowsePresenter.onViewResumed();
        }

        mJustCreated = false;
        installNm7MiniPlayer();
        mNm7ScrollDistance = 0;
        setNm7ChromeHidden(false);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mBrowsePresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mBrowsePresenter.onViewDestroyed();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        trackPullDownGesture(event);
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        mNm7SwipeEligible = !isNm7MiniTouch(event) && !isNm7ChromeTouch(event) && mNm7BrowseContent != null
                && mNm7BrowseContent.getGlobalVisibleRect(bounds)
                && bounds.contains((int) event.getRawX(), (int) event.getRawY());
        }
        float threshold = Math.max(mNm7SwipeSlop * 3f,
                Math.min(getResources().getDisplayMetrics().widthPixels * .14f,
                        32f * getResources().getDisplayMetrics().density));
        boolean consumed = mNm7Gesture.update(event, mNm7SwipeEligible, mNm7SwipeSlop, threshold);
        if (mNm7Gesture.cancelChild()) {
            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(event);
            cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            mGridView.stopScroll();
            mRowsView.stopScroll();
        }
        int direction = mNm7Gesture.direction();
        if (direction != 0 && mTabLayout != null) {
            if (mNm7BrowseContent.getLayoutDirection() == android.view.View.LAYOUT_DIRECTION_RTL) direction = -direction;
            int target = mTabLayout.getSelectedTabPosition() + direction;
            if (target >= 0 && target < mTabLayout.getTabCount()) selectSection(target, false);
        }
        int chrome = mNm7ChromeGesture.update(event, mNm7SwipeEligible,
                24 * getResources().getDisplayMetrics().density);
        if (!consumed && chrome != 0) setNm7ChromeHidden(chrome > 0);
        return consumed || super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        try {
            if (com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity.consumeNm7BrowseBack()) {
                return;
            }
        } catch (RuntimeException error) {
            android.util.Log.e("NM7Playback", "Browse Back mini close failed", error);
        }
        // YouTube-app-style back: first jump to the first section, then exit
        if (mTabLayout != null && mTabLayout.getSelectedTabPosition() > 0 && mTabLayout.getTabCount() > 0) {
            selectSection(0, false);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ NM7 YouTube chrome

    private void installNm7YoutubeChrome() {
        View explore = findViewById(R.id.nm7_explore);
        if (explore != null) {
            explore.setOnClickListener(v -> {
                if (mTabLayout != null && mTabLayout.getTabCount() > 0) {
                    selectSection(0, false);
                }
            });
        }

        // The reference YouTube create button has no equivalent write/upload feature in
        // SmartTube Mobile. Keep the visual affordance without repurposing it for a
        // different action; existing search/account access remains unchanged elsewhere.
        View create = findViewById(R.id.nm7_video);
        if (create != null) {
            create.setOnClickListener(null);
            create.setFocusable(true);
        }

        applyNm7PortraitSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void applyNm7PortraitSystemBars() {
        // Android 15 placement is owned by Theme.SmartTubeDroid (v121).
    }










    @Override
    protected void applySystemBars() {
        super.applySystemBars();
        applyNm7PortraitSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isLandscape()) {
            applyNm7PortraitSystemBars();
        }
    }

    private void trackPullDownGesture(MotionEvent event) {
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

    // ------------------------------------------------------------------ init

    private void initToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.browse_toolbar);
        // Not an AppCompatActivity: inflate the menu on the standalone toolbar directly
        findViewById(R.id.nm7_search).setOnClickListener(v -> SearchPresenter.instance(this).startSearch(null));
        findViewById(R.id.nm7_voice).setOnClickListener(v -> SearchPresenter.instance(this).startVoice());
        findViewById(R.id.nm7_account).setOnClickListener(v -> AccountSettingsPresenter.instance(this).show());
        findViewById(R.id.nm7_search).setOnLongClickListener(v -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(this, v);
            menu.getMenuInflater().inflate(R.menu.browse_toolbar, menu.getMenu());
            menu.setOnMenuItemClickListener(this::onToolbarItemClicked);
            menu.show();
            return true;
        });
        toolbar.inflateMenu(R.menu.browse_toolbar);
        toolbar.setOnMenuItemClickListener(this::onToolbarItemClicked);
    }

    private boolean onToolbarItemClicked(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.browse_action_search) {
            SearchPresenter.instance(this).startSearch(null);
            return true;
        } else if (itemId == R.id.browse_action_account) {
            // Full accounts screen, not the bare chooser: this one carries the "Add account"
            // entry that starts the Google sign-in flow. The chooser alone is a dead end
            // while no account exists yet.
            AccountSettingsPresenter.instance(this).show();
            return true;
        } else if (itemId == R.id.browse_action_refresh) {
            mBrowsePresenter.refresh();
            return true;
        }

        return false;
    }

    private void initTabs() {
        mTabLayout = findViewById(R.id.browse_tabs);
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (!mSuppressTabEvents) {
                    focusSection(tab.getPosition());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // NOP
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Tap on the current tab refreshes the section (mirrors TV's selectSection behavior)
                if (!mSuppressTabEvents) {
                    focusSection(tab.getPosition());
                }
            }
        });
    }

    private void nm7FixInitialFeedPosition() {
        // v122: initial position is supplied by RecyclerView's normal layout.
    }

    private void initContent() {
        mNm7BrowseContent = findViewById(R.id.browse_content);
        mNm7SwipeSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        mGridView = findViewById(R.id.browse_grid);
        mRowsView = findViewById(R.id.browse_rows);
        mSettingsView = findViewById(R.id.browse_settings);
        mErrorContainer = findViewById(R.id.browse_error_container);
        mErrorMessage = findViewById(R.id.browse_error_message);
        mErrorAction = findViewById(R.id.browse_error_action);
        mProgressBar = findViewById(R.id.browse_progress);
        mPullToRefresh = findViewById(R.id.browse_pull_refresh);

        mGridAdapter = new VideoGroupAdapter(mCardListener);
        mGridView.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));
        mGridView.setAdapter(mGridAdapter);
        mGridView.setHasFixedSize(true);
mGridView.setItemAnimator(null);
androidx.recyclerview.widget.RecyclerView.RecycledViewPool nm7Pool =
        new androidx.recyclerview.widget.RecyclerView.RecycledViewPool();
nm7Pool.setMaxRecycledViews(0, 16);
mGridView.setRecycledViewPool(nm7Pool);
        mGridView.setItemViewCacheSize(12);
        if (mGridView.getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManager) {
            ((androidx.recyclerview.widget.GridLayoutManager) mGridView.getLayoutManager()).setInitialPrefetchItemCount(10);
        }


        mRowsAdapter = new Nm7FeedAdapter(mCardListener);
        mRowsView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mRowsView.setAdapter(mRowsAdapter.adapter);
        installNm7ScrollChrome(mRowsView);
        installNm7ScrollChrome(mGridView);

        mSettingsAdapter = new BrowseSettingsAdapter();
        mSettingsView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mSettingsView.setAdapter(mSettingsAdapter);

        // Keep AndroidX's native pull indicator/interception, with an Activity-level
        // gesture fallback below for the nested rows/cards used by the YouTube feed.
        mPullToRefresh.setEnabled(true);
        mPullToRefresh.setColorSchemeColors(0xFFFF0033);
        mPullToRefresh.setDistanceToTriggerSync(
                (int) (getResources().getDisplayMetrics().density * 56f));
        mPullToRefresh.setOnChildScrollUpCallback((parent, child) -> !isCurrentFeedAtTop());
        mPullToRefresh.setOnRefreshListener(this::onPullToRefresh);
        installNm7ScrollChrome(mSettingsView);
    }

    // ---------------------------------------------------------- tab plumbing

    /**
     * A tab became active: remember the section, swap the content area to the matching
     * view type and let the presenter load the data.
     */
    private void focusSection(int position) {
        if (position < 0 || position >= mSections.size()) {
            return;
        }

        BrowseSection section = mSections.get(position);
        mCurrentSection = section;
        if (mPullToRefresh != null) {
            mPullToRefresh.setEnabled(isHomeSection());
        }
        showContentForType(section.getType());

        // Draw the selected tab before doing presenter work; coalesce rapid swipes.
        if (mNm7SectionRequest != null) mNm7BrowseContent.removeCallbacks(mNm7SectionRequest);
        mNm7SectionRequest = () -> {
            mNm7SectionRequest = null;
            if (!isFinishing() && !isDestroyed() && mCurrentSection == section)
                mBrowsePresenter.onSectionFocused(section.getId());
        };
        mNm7BrowseContent.postOnAnimation(mNm7SectionRequest);
    }

    private int indexOf(int sectionId) {
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getId() == sectionId) {
                return i;
            }
        }

        return -1;
    }

    private TabLayout.Tab createTab(BrowseSection section) {
        TabLayout.Tab tab = mTabLayout.newTab();
        tab.setText(section.getTitle());
        // Section options (rename/move/unpin...) on long tap, like TV's header long-press
        tab.view.setOnLongClickListener(v -> {
            mBrowsePresenter.onSectionLongPressed(section.getId());
            return true;
        });

        return tab;
    }

    // ------------------------------------------------------------ BrowseView

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) {
            return;
        }

        int existingIndex = indexOf(section.getId());

        // Already present at the requested spot — nothing to do (mirrors TV logic)
        if (existingIndex != -1 && (index == -1 || existingIndex == index)) {
            // Sync possibly changed title (sections can be renamed)
            TabLayout.Tab tab = mTabLayout.getTabAt(existingIndex);
            if (tab != null) {
                tab.setText(section.getTitle());
            }
            mSections.set(existingIndex, section);
            return;
        }

        removeSection(section);

        int insertIndex = (index < 0 || index > mSections.size()) ? mSections.size() : index;

        mSuppressTabEvents = true;
        try {
            mSections.add(insertIndex, section);
            // setSelected=false: never auto-select here; the presenter drives selection
            // through selectSection() after all sections are added
            mTabLayout.addTab(createTab(section), insertIndex, false);
        } finally {
            mSuppressTabEvents = false;
        }
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (section == null) {
            return;
        }

        int index = indexOf(section.getId());

        if (index == -1) {
            return;
        }

        mSuppressTabEvents = true;
        try {
            mSections.remove(index);
            mTabLayout.removeTabAt(index);

            if (mCurrentSection != null && mCurrentSection.getId() == section.getId()) {
                // TabLayout auto-selected a neighbor while events were suppressed — resync
                int selected = mTabLayout.getSelectedTabPosition();
                mCurrentSection = (selected >= 0 && selected < mSections.size()) ? mSections.get(selected) : null;
            }
        } finally {
            mSuppressTabEvents = false;
        }
    }

    @Override
    public void removeAllSections() {
        mSuppressTabEvents = true;
        try {
            mSections.clear();
            mTabLayout.removeAllTabs();
            mCurrentSection = null;
        } finally {
            mSuppressTabEvents = false;
        }
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index < 0 || mTabLayout.getTabCount() == 0) {
            return;
        }

        // Fallback to the last section if index is above size (mirrors TV logic)
        int target = Math.min(index, mTabLayout.getTabCount() - 1);

        if (mTabLayout.getSelectedTabPosition() == target) {
            // Selecting the same tab fires no onTabSelected — refresh the section manually
            focusSection(target);
        } else {
            TabLayout.Tab tab = mTabLayout.getTabAt(target);
            if (tab != null) {
                tab.select(); // fires onTabSelected -> focusSection()
            }
        }

        if (focusOnContent) {
            focusOnContent();
        }
    }

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null) {
            return;
        }

        if (group.getSection() != null && mCurrentSection != null
                && group.getSection().getId() != mCurrentSection.getId()) return;
        int type = group.getSection() != null ? group.getSection().getType() :
                mCurrentSection != null ? mCurrentSection.getType() : BrowseSection.TYPE_GRID;

        // Content arrived — make sure the error state is gone (TV's restoreMainFragment analog)
        showContentForType(type);
        nm7FixInitialFeedPosition();

        if (mPullToRefresh != null) {
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
        if (type == BrowseSection.TYPE_ROW) {
            mRowsAdapter.update(group);
        } else {
            // TYPE_GRID / TYPE_SHORTS_GRID / TYPE_MULTI_GRID all render as a 2-column grid.
            // MULTI_GRID's second column (uploads preview) is simplified away on the phone:
            // tapping a channel card opens it (BrowsePresenter handles that path itself).
            mGridAdapter.update(group);
        }
    }

    @Override
    public void updateSection(SettingsGroup group) {
        if (group == null) {
            return;
        }

        showContentForType(BrowseSection.TYPE_SETTINGS_GRID);
        mSettingsAdapter.setItems(group.getItems());
    }

    @Override
    public void clearSection(BrowseSection section) {
        // TV clears whatever the current fragment shows; do the same for all content views
        mGridAdapter.clear();
        mRowsAdapter.clear();
        mSettingsAdapter.clear();
    }

    @Override
    public void selectSectionItem(int index) {
        if (index < 0) {
            return;
        }

        RecyclerView current = getCurrentContentView();

        if (current != null && current.getLayoutManager() != null && current.getAdapter() != null
                && index < current.getAdapter().getItemCount()) {
            current.getLayoutManager().scrollToPosition(index);
        }
    }

    @Override
    public void selectSectionItem(Video item) {
        // TV-only: restores DPAD focus on a particular card after boot/crash.
        // The shared adapter API exposes no Video->position lookup and phones
        // have no card focus concept, so this is a documented no-op.
    }

    @Override
    public void showError(ErrorFragmentData data) {
        if (data == null) {
            return;
        }

        finishPullRefresh();
        showContentView(mErrorContainer);

        mErrorMessage.setText(data.getMessage());

        String actionText = data.getActionText();

        if (actionText != null) {
            mErrorAction.setText(actionText);
            mErrorAction.setVisibility(View.VISIBLE);
            mErrorAction.setOnClickListener(v -> data.onAction());
        } else {
            mErrorAction.setVisibility(View.GONE);
            mErrorAction.setOnClickListener(null);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        // May be called from a background thread (TV posts to the main looper too)
        runOnUiThread(() -> {
            if (mPullToRefresh != null && mPullToRefresh.isRefreshing()) {
                mProgressBar.setVisibility(View.GONE);
            } else {
                mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
            if (!show && (!mPullRefreshPendingClear
                    || android.os.SystemClock.elapsedRealtime() - mPullRefreshStartedAt > 1_200L)) {
                finishPullRefresh();
            }
        });
    }

    @Override
    public boolean isProgressBarShowing() {
        return mProgressBar != null && mProgressBar.getVisibility() == View.VISIBLE;
    }

    @Override
    public void focusOnContent() {
        // No DPAD focus on phones; just make sure the visible content gets view focus
        RecyclerView current = getCurrentContentView();

        if (current != null) {
            current.requestFocus();
        }
    }

    @Override
    public boolean isEmpty() {
        if (mErrorContainer != null && mErrorContainer.getVisibility() == View.VISIBLE) {
            return true; // error state == no content
        }

        int type = mCurrentSection != null ? mCurrentSection.getType() : BrowseSection.TYPE_GRID;

        if (type == BrowseSection.TYPE_SETTINGS_GRID) {
            return mSettingsAdapter == null || mSettingsAdapter.isEmpty();
        } else if (type == BrowseSection.TYPE_ROW) {
            return mRowsAdapter == null || mRowsAdapter.isEmpty();
        }

        return mGridAdapter == null || mGridAdapter.isEmpty();
    }

    @Override
    public void updateBadge() {
        // TV-only: swaps the top-right Leanback badge drawable with the bridge app's icon.
        // The phone toolbar shows a fixed app title instead, so nothing to update.
    }

    // -------------------------------------------------------------- helpers

    /** Show the content view matching a section type, hiding all the others. */
    private void showContentForType(int sectionType) {
        switch (sectionType) {
            case BrowseSection.TYPE_ROW:
                showContentView(mRowsView);
                break;
            case BrowseSection.TYPE_SETTINGS_GRID:
                showContentView(mSettingsView);
                break;
            case BrowseSection.TYPE_ERROR:
                // Content stays as-is; the presenter follows up with showError()
                break;
            case BrowseSection.TYPE_GRID:
            case BrowseSection.TYPE_SHORTS_GRID:
            case BrowseSection.TYPE_MULTI_GRID:
            default:
                showContentView(mGridView);
                break;
        }
    }

    private void showContentView(View view) {
        mGridView.setVisibility(view == mGridView ? View.VISIBLE : View.GONE);
        mRowsView.setVisibility(view == mRowsView ? View.VISIBLE : View.GONE);
        mSettingsView.setVisibility(view == mSettingsView ? View.VISIBLE : View.GONE);
        mErrorContainer.setVisibility(view == mErrorContainer ? View.VISIBLE : View.GONE);
    }

    @Nullable
    private RecyclerView getCurrentContentView() {
        if (mRowsView != null && mRowsView.getVisibility() == View.VISIBLE) {
            return mRowsView;
        } else if (mSettingsView != null && mSettingsView.getVisibility() == View.VISIBLE) {
            return mSettingsView;
        } else if (mGridView != null && mGridView.getVisibility() == View.VISIBLE) {
            return mGridView;
        }

        return null;
    }
}
