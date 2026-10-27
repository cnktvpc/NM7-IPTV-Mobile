"""Mobile-only presentation overlay: light feed, full-width recommendations, swipe to mini."""
from pathlib import Path

root = Path('third_party/SmartTube-droid/smarttubedroid/src/main')
ui = root / 'java/com/liskovsoft/smartyoutubetv2/droid/ui'

def once(text, old, new):
    if text.count(old) != 1:
        raise SystemExit('v55 UI anchor missing/ambiguous: ' + old[:100])
    return text.replace(old, new, 1)

p = ui / 'playback/PlaybackActivity.java'
s = p.read_text()
s = s.replace('VideoRowsAdapter', 'Nm7FeedAdapter')
s = once(s, 'mSuggestionsView.setAdapter(mSuggestionsAdapter);',
         'mSuggestionsView.setAdapter(mSuggestionsAdapter.adapter);')
s = once(s, '    public boolean dispatchTouchEvent(MotionEvent event) {', '''    public boolean dispatchTouchEvent(MotionEvent event) {
        if (handleNm7MinimizeGesture(event)) return true;''')
s = once(s, '    private boolean mIsLandscape;', '''    private boolean mIsLandscape;
    private float mNm7DragX, mNm7DragY;
    private boolean mNm7DragEligible;

    private boolean handleNm7MinimizeGesture(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mNm7DragX = event.getRawX();
            mNm7DragY = event.getRawY();
            int[] location = new int[2];
            mPlayerContainer.getLocationOnScreen(location);
            // Portrait only; do not steal fullscreen brightness/volume or seek controls.
            mNm7DragEligible = !mIsLandscape && event.getPointerCount() == 1
                    && mNm7DragX >= location[0]
                    && mNm7DragX <= location[0] + mPlayerContainer.getWidth()
                    && mNm7DragY > location[1] + mPlayerContainer.getHeight() * .25f
                    && mNm7DragY < location[1] + mPlayerContainer.getHeight() * .75f;
        } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            mNm7DragEligible = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && mNm7DragEligible) {
            float dx = Math.abs(event.getRawX() - mNm7DragX);
            float dy = event.getRawY() - mNm7DragY;
            if (dy > 64 * getResources().getDisplayMetrics().density && dy > dx * 1.5f) {
                mNm7DragEligible = false;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                if (mGestureHandler != null) mGestureHandler.onTouchEvent(cancel);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                onBackPressed();
                return true;
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mNm7DragEligible = false;
        }
        return false;
    }''')
p.write_text(s)

# Dragging the mini belongs to the overlay, not the Browse section-swipe observer.
p = ui / 'browse/BrowseActivity.java'
s = once(p.read_text(), 'mNm7SwipeEligible = mNm7BrowseContent != null',
         'mNm7SwipeEligible = !isNm7MiniTouch(event) && mNm7BrowseContent != null')
anchor = '    private boolean mNm7SwipeEligible;'
s = once(s, anchor, anchor + '''

    private boolean isNm7MiniTouch(android.view.MotionEvent event) {
        try {
            return Boolean.TRUE.equals(Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer")
                    .getMethod("containsPoint", float.class, float.class)
                    .invoke(null, event.getRawX(), event.getRawY()));
        } catch (ReflectiveOperationException error) { return false; }
    }
''')
p.write_text(s)

# Match the full-width feed in search results too.
p = ui / 'search/SearchActivity.java'
s = once(p.read_text(), 'new GridLayoutManager(this, 2)', 'new GridLayoutManager(this, 1)')
p.write_text(s)

# Use the same readable full-width cards in Browse, Search and below the player.
p = ui / 'shared/VideoCardHolder.java'
s = p.read_text()
old = '!isRow && parent.getContext() instanceof com.liskovsoft.smartyoutubetv2.droid.ui.browse.BrowseActivity'
s = once(s, old, '!isRow')
p.write_text(s)

# Light native theme; video controls retain their original white-on-black colors.
p = root / 'res/values/themes.xml'
s = p.read_text().replace('Theme.MaterialComponents.NoActionBar', 'Theme.MaterialComponents.Light.NoActionBar')
s = s.replace('#FF7A00', '#D32F2F').replace('#D96300', '#B71C1C')
s = s.replace('tools:targetApi="23">false', 'tools:targetApi="23">true')
p.write_text(s)

p = root / 'res/layout/browse_activity.xml'
s = p.read_text().replace('#101014', '#FFFFFF').replace('android:textColor="#FFFFFF"', 'android:textColor="#0F0F0F"')
s = s.replace('android:text="SmartTube"', 'android:text="NM7"').replace('android:tint="#FF7A00"', 'android:tint="#333333"')
s = s.replace('app:tabTextColor="#EEEEEE"', 'app:tabTextColor="#333333"')
s = s.replace('android:padding="12dp"', 'android:padding="0dp"')
s = s.replace('android:paddingStart="12dp" android:paddingEnd="12dp"', 'android:paddingStart="0dp" android:paddingEnd="0dp"')
p.write_text(s)

p = root / 'res/drawable/nm7_tab_bg.xml'
p.write_text(p.read_text().replace('#FFFFFF', '#0F0F0F').replace('#282828', '#F0F0F0'))
p = root / 'res/drawable/nm7_search_bg.xml'
p.write_text(p.read_text().replace('#1A1A22', '#F4F4F4').replace('#3E3E48', '#E5E5E5'))

p = root / 'res/layout/playback_activity.xml'
s = p.read_text()
# Only the metadata and comment panels change; never recolor the overlay icons.
start = s.index('android:id="@+id/playback_panel"')
s = s[:start] + s[start:].replace('@color/playback_control_dim', '#606060').replace('@color/playback_control', '#0F0F0F').replace('@color/playback_panel_bg', '#FFFFFF')
p.write_text(s)
print('v55 light mobile feed, full-width recommendations and swipe-to-mini applied')
