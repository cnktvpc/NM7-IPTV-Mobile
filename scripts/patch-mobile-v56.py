"""Phone gesture ownership, collapsing chrome, watch-sheet motion and bounded memory."""
from pathlib import Path
import re
root = Path('third_party/SmartTube-droid')
ui = root / 'smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui'
res = root / 'smarttubedroid/src/main/res'
def once(s, old, new):
    assert s.count(old) == 1, old[:100]
    return s.replace(old, new, 1)
p = ui / 'browse/BrowseActivity.java'
s = p.read_text()
start = s.index('    public boolean dispatchTouchEvent(android.view.MotionEvent event) {')
end = s.index('    @Override\n    public void onBackPressed()', start)
s = s[:start] + '''    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        mNm7SwipeEligible = !isNm7MiniTouch(event) && mNm7BrowseContent != null
                && mNm7BrowseContent.getGlobalVisibleRect(bounds)
                && bounds.contains((int) event.getRawX(), (int) event.getRawY());
        float threshold = Math.max(mNm7SwipeSlop * 3f,
                Math.min(getResources().getDisplayMetrics().widthPixels * .14f,
                        56f * getResources().getDisplayMetrics().density));
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
        return consumed || super.dispatchTouchEvent(event);
    }

''' + s[end:]
s = once(s, '    private int mNm7SwipeSlop;', '''    private int mNm7SwipeSlop;
    private final com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7BrowseGesture mNm7Gesture =
            new com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7BrowseGesture();
    private boolean mNm7ChromeHidden;
    private int mNm7ScrollDistance;

    private void setNm7ChromeHidden(boolean hidden) {
        mNm7ChromeHidden = hidden;
        findViewById(R.id.browse_app_bar).setVisibility(hidden ? View.GONE : View.VISIBLE);
        View root = (View) mNm7BrowseContent.getParent();
        root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(),
                hidden ? 0 : Math.round(64 * getResources().getDisplayMetrics().density));
        try {
            Class.forName("vn.phuong.iptvplayer.HomeTabBar")
                    .getMethod("setVisible", android.app.Activity.class, boolean.class)
                    .invoke(null, this, !hidden);
        } catch (ReflectiveOperationException error) {
            android.util.Log.e("NM7Navigation", "Browse chrome", error);
        }
    }

    private void installNm7ScrollChrome(RecyclerView list) {
        list.setItemViewCacheSize(2);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView view, int dx, int dy) {
                if (dy == 0 || view.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) return;
                if ((dy > 0) != (mNm7ScrollDistance > 0)) mNm7ScrollDistance = 0;
                mNm7ScrollDistance += dy;
                int threshold = Math.round(24 * getResources().getDisplayMetrics().density);
                if (mNm7ScrollDistance > threshold && !mNm7ChromeHidden) setNm7ChromeHidden(true);
                else if (mNm7ScrollDistance < -threshold && mNm7ChromeHidden) setNm7ChromeHidden(false);
            }
        });
    }''')
s = once(s, '        mRowsView.setAdapter(mRowsAdapter.adapter);', '''        mRowsView.setAdapter(mRowsAdapter.adapter);
        installNm7ScrollChrome(mRowsView);
        installNm7ScrollChrome(mGridView);''')
s = once(s, '        installNm7MiniPlayer();', '''        installNm7MiniPlayer();
        mNm7ScrollDistance = 0;
        setNm7ChromeHidden(false);''')
p.write_text(s)

# Sliding watch panel: preserve native playback controls, metadata and continuation feed.
# No web page and no new engine when expanding an existing mini session.
p = ui / 'playback/PlaybackActivity.java'
s = p.read_text()
s = once(s, '    private boolean mNm7DragEligible;', '    private boolean mNm7DragEligible;\n    private boolean mNm7DragConsumed;')
s = once(s, '    private boolean handleNm7MinimizeGesture(MotionEvent event) {', '''    private boolean handleNm7MinimizeGesture(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) mNm7DragConsumed = false;
        if (mNm7DragConsumed) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                mNm7DragConsumed = false;
            return true;
        }''')
s = once(s, '                onBackPressed();\n                return true;', '                mNm7DragConsumed = true;\n                onBackPressed();\n                return true;')
s = once(s, '        sNm7Active = this;\n        if (VERSION.SDK_INT', '''        sNm7Active = this;
        overridePendingTransition(R.anim.nm7_watch_enter, R.anim.nm7_watch_stay);
        if (VERSION.SDK_INT''')
s = once(s, '        startActivity(intent);', '''        startActivity(intent);
        overridePendingTransition(R.anim.nm7_watch_stay, R.anim.nm7_watch_exit);''')
# On first-frame/surface restoration the overlay must not retain a released engine binding.
s = once(s, '        active.mNm7VideoTarget = mini;', '''        if (mini.getPlayer() != active.mPlayer) mini.setPlayer(active.mPlayer);
        active.mNm7VideoTarget = mini;''')
# Runtime button-state updates also need a dark foreground on the light metadata panel.
s = once(s, '            MaterialButton button = (MaterialButton) view;', '''            MaterialButton button = (MaterialButton) view;
            if (buttonState == BUTTON_OFF) color = android.graphics.Color.rgb(15, 15, 15);''')
p.write_text(s)
anim = res / 'anim'; anim.mkdir(exist_ok=True)
(anim/'nm7_watch_enter.xml').write_text('''<translate xmlns:android="http://schemas.android.com/apk/res/android" android:fromYDelta="100%p" android:toYDelta="0" android:duration="220" android:interpolator="@android:interpolator/decelerate_cubic" />''')
(anim/'nm7_watch_exit.xml').write_text('''<translate xmlns:android="http://schemas.android.com/apk/res/android" android:fromYDelta="0" android:toYDelta="100%p" android:duration="180" android:interpolator="@android:interpolator/accelerate_cubic" />''')
(anim/'nm7_watch_stay.xml').write_text('''<alpha xmlns:android="http://schemas.android.com/apk/res/android" android:fromAlpha="1" android:toAlpha="1" android:duration="220" />''')
# Outlined action icons inherited white tint and were invisible against the new light panel.
p = res / 'layout/playback_activity.xml'
s = p.read_text()
def colors(m):
    tag = m.group(0)
    if 'android:textColor=' not in tag: tag = tag.replace('MaterialButton', 'MaterialButton android:textColor="#0F0F0F"', 1)
    if 'app:iconTint=' not in tag: tag = tag.replace('MaterialButton', 'MaterialButton app:iconTint="#0F0F0F"', 1)
    return tag
s = re.sub(r'<com\.google\.android\.material\.button\.MaterialButton\b[^>]*>', colors, s)
p.write_text(s)

# The upstream TV buffer used total device RAM and allowed time to override byte limits.
# On phones this can exhaust the app heap while the feed retains decoded thumbnails.
p = root / 'common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/ExoPlayerInitializer.java'
s = p.read_text()
s = once(s, 'mMaxBufferBytes = deviceRam <= 0 ? 196_000_000 : (int)(deviceRam / 18);',
         'mMaxBufferBytes = (int) Math.max(4L * 1024 * 1024, Math.min(32L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8));')
s = once(s, '        return baseBuilder.createDefaultLoadControl();', '''        baseBuilder.setTargetBufferBytes(mMaxBufferBytes);
        baseBuilder.setPrioritizeTimeOverSizeThresholds(false);
        baseBuilder.setBackBuffer(0, false);
        return baseBuilder.createDefaultLoadControl();''')
p.write_text(s)
print('v56 touch cancellation, scroll chrome, watch-sheet motion and heap-bounded buffer applied')
