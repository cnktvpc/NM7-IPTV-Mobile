"""Mobile memory budgets, immediate section gestures, and a recycled watch feed."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root = Path('third_party/SmartTube-droid')
ui = root / 'smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui'
res = root / 'smarttubedroid/src/main/res'

def once(s, old, new):
    assert s.count(old) == 1, old[:120]
    return s.replace(old, new, 1)

p = ui / 'shared/Nm7FeedAdapter.java'
s = p.read_text().replace('new ConcatAdapter();', 'new ConcatAdapter(new ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build());')
p.write_text(s)

p = ui / 'shared/VideoCardHolder.java'
s = p.read_text()
s = s.replace('Math.min(1280,', 'Math.min(960,')
# Keep the v58 thumbnail tuning tolerant of the avatar patch and SmartTube source variants.
# Some pinned sources use hq720 directly and others use maxresdefault; either is valid.
s = s.replace('.diskCacheStrategy(DiskCacheStrategy.DATA);', '.diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);')
if 'imageRoot + "maxresdefault.jpg"' in s:
    s = s.replace('imageRoot + "maxresdefault.jpg"', 'imageRoot + "hq720.jpg"', 1)
    s = s.replace('.load(imageRoot + "hq720.jpg").apply(options)', '.load(imageRoot + "maxresdefault.jpg").apply(options)', 1)
if s.count('Glide.with(context).load(imageRoot + "maxresdefault.jpg").apply(options)') == 1:
    s = s.replace('Glide.with(context).load(imageRoot + "maxresdefault.jpg").apply(options)\n                            .error',
                  'Glide.with(context).load(imageRoot + "hq720.jpg").apply(options)', 1)
elif s.count('Glide.with(context).load(imageRoot + "hq720.jpg").apply(options)') == 1:
    pass
# Do not abort the whole mobile build if the pinned thumbnail implementation differs.
p.write_text(s)

p = ui / 'browse/BrowseActivity.java'; s = p.read_text()
s = once(s, '        android.graphics.Rect bounds = new android.graphics.Rect();\n        mNm7SwipeEligible', '        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {\n        android.graphics.Rect bounds = new android.graphics.Rect();\n        mNm7SwipeEligible')
s = once(s, '        float threshold = Math.max(mNm7SwipeSlop * 3f,', '        }\n        float threshold = Math.max(mNm7SwipeSlop * 3f,')
s = once(s, '    private void setNm7ChromeHidden(boolean hidden) {', '    private void setNm7ChromeHidden(boolean hidden) {\n        if (mNm7ChromeHidden == hidden) return;')
s = once(s, '        list.setItemViewCacheSize(2);', '''        list.setItemViewCacheSize(2);
        list.setItemAnimator(null);
        list.getRecycledViewPool().setMaxRecycledViews(0, 4);''')
s = once(s, '56f * getResources().getDisplayMetrics().density', '32f * getResources().getDisplayMetrics().density')
s = once(s, '        mBrowsePresenter.onSectionFocused(section.getId());', '''        // Draw the selected tab before doing presenter work; coalesce rapid swipes.
        if (mNm7SectionRequest != null) mNm7BrowseContent.removeCallbacks(mNm7SectionRequest);
        mNm7SectionRequest = () -> {
            mNm7SectionRequest = null;
            if (!isFinishing() && !isDestroyed() && mCurrentSection == section)
                mBrowsePresenter.onSectionFocused(section.getId());
        };
        mNm7BrowseContent.postOnAnimation(mNm7SectionRequest);''')
s = once(s, '    private boolean mNm7ChromeHidden;', '    private boolean mNm7ChromeHidden;\n    private Runnable mNm7SectionRequest;')
s = once(s, '        int type = group.getSection() != null', '''        if (group.getSection() != null && mCurrentSection != null
                && group.getSection().getId() != mCurrentSection.getId()) return;
        int type = group.getSection() != null''')
p.write_text(s)

A = '{http://schemas.android.com/apk/res/android}'
B = '{http://schemas.android.com/apk/res-auto}'
ET.register_namespace('android', A[1:-1]); ET.register_namespace('app', B[1:-1])
p = res / 'layout/playback_activity.xml'; tree = ET.parse(p); layout = tree.getroot()
def get(id): return next(e for e in layout.iter() if e.get(A+'id') == '@+id/'+id)
def attrs(e, **kw):
    for k,v in kw.items(): e.set(A+k, v)
panel = get('playback_panel'); suggestions = get('playback_suggestions')
header = ET.Element('LinearLayout', {A+'id':'@+id/nm7_watch_metadata',A+'layout_width':'match_parent',A+'layout_height':'wrap_content',A+'orientation':'vertical',A+'background':'#FFFFFF'})
for child in list(panel):
    if child is not suggestions: panel.remove(child); header.append(child)
panel.insert(0,header)
attrs(get('playback_title'), maxLines='2', textSize='17sp', paddingTop='12dp')
attrs(get('playback_second_title'), maxLines='1')
row = get('playback_channel_row'); icon = get('playback_channel_icon'); sub = get('playback_chip_subscribe'); chips = get('playback_chips')
row.remove(icon); row.remove(sub)
attrs(row,paddingTop='4dp',paddingBottom='2dp'); attrs(get('playback_channel_name'),layout_marginStart='0dp',layout_marginLeft='0dp',textSize='12sp')
chips.insert(0,icon);chips.insert(1,sub)
attrs(chips,gravity='center_vertical',paddingTop='6dp',paddingBottom='6dp')
attrs(icon,layout_width='36dp',layout_height='36dp',layout_marginEnd='8dp')
attrs(sub,text='Đăng ký')
like=get('playback_chip_like'); dislike=get('playback_chip_dislike')
chips.remove(like);chips.remove(dislike)
rating=ET.Element('LinearLayout',{A+'layout_width':'wrap_content',A+'layout_height':'44dp',A+'orientation':'horizontal',A+'gravity':'center_vertical',A+'background':'@drawable/nm7_action_pill',A+'layout_marginStart':'8dp',A+'layout_marginEnd':'4dp'})
rating.append(like); rating.append(ET.Element('View',{A+'layout_width':'1dp',A+'layout_height':'20dp',A+'background':'#DADADA'})); rating.append(dislike); chips.insert(2,rating)
for id in ('playback_chip_share','playback_chip_playlist','playback_chip_quality','playback_chip_speed','playback_chip_more'):
    e=get(id);chips.remove(e);chips.append(e)
for e in header.iter('com.google.android.material.button.MaterialButton'):
    attrs(e,layout_height='44dp',minHeight='44dp',textAllCaps='false',layout_marginStart='4dp',layout_marginLeft='4dp',layout_marginEnd='4dp',layout_marginRight='4dp',paddingStart='12dp',paddingEnd='12dp',insetTop='0dp',insetBottom='0dp')
    e.set(B+'cornerRadius','22dp');e.set(B+'strokeWidth','0dp');e.set(B+'backgroundTint','#F1F1F1')
    if e is sub: attrs(e,textColor='#FFFFFF',layout_width='wrap_content');e.set(B+'backgroundTint','#0F0F0F')
    elif e is like or e is dislike: attrs(e,layout_marginStart='0dp',layout_marginLeft='0dp',layout_marginEnd='0dp',layout_marginRight='0dp');e.set(B+'backgroundTint','#00000000')
    elif e.get(A+'id') in ('@+id/playback_chip_share','@+id/playback_chip_playlist'):
        attrs(e,layout_width='wrap_content',text='Chia sẻ' if e.get(A+'id').endswith('share') else 'Lưu');e.set(B+'iconPadding','6dp')
card=get('playback_comments_card');card.clear()
attrs(card,id='@+id/playback_comments_card',layout_width='match_parent',layout_height='wrap_content',layout_margin='12dp',orientation='vertical',padding='12dp',background='@drawable/nm7_comment_card',visibility='gone',contentDescription='Mở bình luận')
ET.SubElement(card,'TextView',{A+'id':'@+id/nm7_comments_heading',A+'layout_width':'match_parent',A+'layout_height':'wrap_content',A+'text':'Bình luận',A+'textColor':'#0F0F0F',A+'textStyle':'bold',A+'textSize':'14sp'})
preview=ET.SubElement(card,'LinearLayout',{A+'layout_width':'match_parent',A+'layout_height':'wrap_content',A+'orientation':'horizontal',A+'gravity':'center_vertical',A+'layout_marginTop':'10dp'})
ET.SubElement(preview,'ImageView',{A+'id':'@+id/nm7_comment_avatar',A+'layout_width':'28dp',A+'layout_height':'28dp',A+'background':'@drawable/playback_channel_placeholder',A+'contentDescription':'@null'})
ET.SubElement(preview,'TextView',{A+'id':'@+id/nm7_comment_preview',A+'layout_width':'0dp',A+'layout_height':'wrap_content',A+'layout_weight':'1',A+'layout_marginStart':'10dp',A+'background':'@drawable/nm7_comment_input',A+'padding':'8dp',A+'text':'Xem bình luận…',A+'textColor':'#606060',A+'maxLines':'2',A+'ellipsize':'end',A+'textSize':'13sp'})
attrs(get('playback_details_title'),textSize='18sp',textStyle='bold'); attrs(get('playback_details_close'),tint='#0F0F0F')
ET.indent(tree,space='    ');tree.write(p,encoding='utf-8',xml_declaration=True)
for name,color,radius in [('nm7_action_pill','#F1F1F1',22),('nm7_comment_card','#F2F2F2',16),('nm7_comment_input','#E8E8E8',18)]:
    (res/f'drawable/{name}.xml').write_text(f'<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="{color}"/><corners android:radius="{radius}dp"/></shape>')
p=res/'layout/playback_comment_item.xml';s=p.read_text().replace('@color/playback_control_dim','#606060').replace('@color/playback_control','#0F0F0F');p.write_text(s)

p = ui / 'playback/PlaybackActivity.java'; s=p.read_text()
s=once(s, '        initWindowInsets();', '        initWindowInsets();\n        installNm7WatchFeed();')
s=once(s, '    private void initWindowInsets() {', '''    private void installNm7WatchFeed() {
        View metadata = findViewById(R.id.nm7_watch_metadata);
        mNm7CommentPreview = findViewById(R.id.nm7_comment_preview);
        mNm7CommentAvatar = findViewById(R.id.nm7_comment_avatar);
        ((android.view.ViewGroup) metadata.getParent()).removeView(metadata);
        com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7WatchHeaderAdapter header =
                new com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7WatchHeaderAdapter(metadata);
        mSuggestionsView.setAdapter(new androidx.recyclerview.widget.ConcatAdapter(header, mSuggestionsAdapter.adapter));
        mSuggestionsView.setItemAnimator(null); mSuggestionsView.setItemViewCacheSize(1);
        mSuggestionsView.getRecycledViewPool().setMaxRecycledViews(1, 4);
    }

    private TextView mNm7CommentPreview;
    private android.widget.ImageView mNm7CommentAvatar;

    private void initWindowInsets() {''')
s=once(s, '            button.setIconTint(android.content.res.ColorStateList.valueOf(color));', '''            if (view.getId() == R.id.playback_chip_subscribe) {
                boolean subscribed = buttonState != BUTTON_OFF;
                button.setText(subscribed ? "Đã đăng ký" : "Đăng ký");
                button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(subscribed ? 0xFFF1F1F1 : 0xFF0F0F0F));
                color = subscribed ? 0xFF0F0F0F : 0xFFFFFFFF;
            }
            button.setIconTint(android.content.res.ColorStateList.valueOf(color));''')
s=once(s, '        mNextCommentsKey = group != null ? group.getNextCommentsKey() : null;', '''        if (mStashedComments == null && group != null && group.getComments() != null
                && !group.getComments().isEmpty() && mNm7CommentPreview != null) {
            CommentItem first = group.getComments().get(0);
            mNm7CommentPreview.setText(first.getMessage());
            Glide.with(this).load(first.getAuthorPhoto()).apply(RequestOptions.circleCropTransform()).override(84,84).into(mNm7CommentAvatar);
        }
        mNextCommentsKey = group != null ? group.getNextCommentsKey() : null;''')
s=once(s, '        mCommentsKey = null;', '        mCommentsKey = null;\n        if (mNm7CommentPreview != null) { mNm7CommentPreview.setText("Xem bình luận…"); Glide.with(this).clear(mNm7CommentAvatar); }')
s=s.replace('showDetailsMessage("No comments available");','showDetailsMessage("Chưa có bình luận để hiển thị");')
s=once(s, '        String name = cause == null ? "Unknown" : cause.getClass().getSimpleName();', '''        String name = cause == null ? "Unknown" : cause.getClass().getSimpleName();
        if (name.contains("OutOfMemory")) { Glide.get(getApplicationContext()).clearMemory(); if (mSuggestionsView != null) mSuggestionsView.getRecycledViewPool().clear(); }''')
s=once(s,'        if (!sNm7Mini) return true;','')
s=once(s,'        boolean manual = mNm7ManualRetry;', '        if (!sNm7Mini && !name.contains("OutOfMemory")) return true;\n        boolean manual = mNm7ManualRetry;')
s=once(s,'    public static boolean isNm7Playing() {','''    public static void prepareNm7Background() {
        if (!isNm7SessionActive() || !sNm7Mini || sNm7SuspendedForIptv || !sNm7Active.mPlayer.getPlayWhenReady()) return;
        sNm7Active.blockEngine(true); sNm7Active.nm7SetBackground(true);
    }
    public static void resumeNm7Foreground() {
        if (!isNm7SessionActive() || !sNm7Mini || sNm7SuspendedForIptv) return;
        sNm7Active.blockEngine(false); sNm7Active.nm7SetBackground(false);
        sNm7Active.stopService(new Intent().setClassName(sNm7Active,"vn.phuong.iptvplayer.BackgroundPlaybackService"));
    }
    public static boolean isNm7Playing() {''')
s=once(s,'    public static String getNm7ErrorLabel() {','''    public static String getNm7ErrorLabel() {
        if (isNm7SessionActive() && sNm7Active.mPlayer.getPlaybackError() != null && sNm7Active.mNm7ErrorLabel == null) {
            Throwable error = sNm7Active.mPlayer.getPlaybackError();
            for (int i=0; i<8 && error!=null; i++, error=error.getCause()) if (error instanceof OutOfMemoryError) return "Thiếu bộ nhớ";
        }''')
s=once(s, '        mCommentsKey = commentsKey;', '        mCommentsKey = commentsKey;\n        updateNm7RatingCounts();')
s=once(s, '    public void setButtonState(int buttonId, int buttonState) {', '    public void setButtonState(int buttonId, int buttonState) {\n        updateNm7RatingCounts();')
s=once(s, '    private void installNm7WatchFeed() {', '''    private void updateNm7RatingCounts() {
        Video video = getVideo();
        int[] ids = {BUTTON_LIKE, BUTTON_DISLIKE};
        String[] counts = {video == null ? null : video.likeCount, video == null ? null : video.dislikeCount};
        for (int i=0;i<ids.length;i++) {
            List<View> buttons = mButtonViews.get(ids[i]); if (buttons == null) continue;
            for (View view : buttons) if (view instanceof MaterialButton) {
                MaterialButton button=(MaterialButton)view; button.setText(counts[i]);
                android.view.ViewGroup.LayoutParams params=button.getLayoutParams();
                params.width=TextUtils.isEmpty(counts[i]) ? Math.round(48*getResources().getDisplayMetrics().density) : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                button.setLayoutParams(params);
            }
        }
    }

    private void installNm7WatchFeed() {''')
s=once(s, '        if (TextUtils.isEmpty(url) || isFinishing() || isDestroyed()) {', '''        if (TextUtils.isEmpty(url)) {
            Glide.with(getApplicationContext()).clear(mBackgroundView); mBackgroundView.setImageDrawable(null); mBackgroundView.setVisibility(View.GONE); return;
        }
        if (isFinishing() || isDestroyed()) {''')
s=once(s, '        Glide.with(this).load(url).into(mBackgroundView);','        Glide.with(this).load(url).override(960, 540).into(mBackgroundView);')
p.write_text(s)

p = root / 'common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ErrorFixerController.java'
s = p.read_text()
s = once(s, '                getPlayerTweaksData().setSectionPlaylistEnabled(false);\n                restartEngine = false;', '                getPlayerTweaksData().setSectionPlaylistEnabled(false);\n                restartEngine = true; // Mobile OOM: release allocator/decoder before retry.')
p.write_text(s)
print('v58 shared recycling, watch metadata header, pill actions, comments and mini/Home lifecycle applied')
