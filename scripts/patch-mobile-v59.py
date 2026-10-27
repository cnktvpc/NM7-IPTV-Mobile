"""Route engine failures to their actual owner; compact native watch details and comment preview."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path('third_party/SmartTube-droid');ui=root/'smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui';res=root/'smarttubedroid/src/main/res'
def once(s,a,b):
    assert s.count(a)==1,a[:120]
    return s.replace(a,b,1)
def method(s,signature,body):
    s,n=re.subn(r'(?ms)^    '+re.escape(signature)+r' \{.*?^    \}',lambda _: '    '+signature+' {\n'+body+'\n    }',s)
    assert n==1,signature
    return s
# Native error ownership must not depend on whichever view a singleton presenter last saw.
common=root/'common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/controller'
(common/'Nm7EngineErrorOwner.java').write_text('''package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;
import com.google.android.exoplayer2.ExoPlaybackException;
public interface Nm7EngineErrorOwner {
    void onNm7OwnedEngineError(ExoPlaybackException error);
}
''')
p=common/'ExoPlayerController.java';s=p.read_text();s=once(s,'    public void onPlayerError(ExoPlaybackException error) {','''    public void onPlayerError(ExoPlaybackException error) {
        if (mPlayerView instanceof Nm7EngineErrorOwner) {
            ((Nm7EngineErrorOwner) mPlayerView).onNm7OwnedEngineError(error);
            return;
        }''');p.write_text(s)
# Optional total supplied by YouTube; never substitute first-page size for a total.
core=root/'MediaServiceCore'
p=core/'mediaserviceinterfaces/src/main/java/com/liskovsoft/mediaserviceinterfaces/data/MediaItemMetadata.java'
s=p.read_text();s=once(s,'    String getCommentsKey();','    String getCommentsKey();\n    default String getCommentsCount() { return null; }');p.write_text(s)
p=core/'youtubeapi/src/main/java/com/liskovsoft/youtubeapi/common/models/gen/CommonItems.kt'
s=p.read_text();s=once(s,'internal data class EngagementPanelTitleHeaderRenderer(\n    val menu: Menu?,\n    val title: TextItem?', 'internal data class EngagementPanelTitleHeaderRenderer(\n    val menu: Menu?,\n    val title: TextItem?,\n    val contextualInfo: TextItem? = null');p.write_text(s)
p=core/'youtubeapi/src/main/java/com/liskovsoft/youtubeapi/next/v2/impl/MediaItemMetadataImpl.kt'
s=p.read_text();s=once(s,'    override fun getCommentsKey(): String? {',"""    override fun getCommentsCount(): String? = commentsPanel?.engagementPanelSectionListRenderer
        ?.header?.engagementPanelTitleHeaderRenderer?.contextualInfo?.getText()

    override fun getCommentsKey(): String? {""");p.write_text(s)
p=root/'common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/data/Video.java'
s=p.read_text();s=once(s,'    public String likeCount;','    public String likeCount;\n    public String commentsCount;');s=once(s,'        likeCount = metadata.getLikeCount();','        likeCount = metadata.getLikeCount();\n        commentsCount = metadata.getCommentsCount();');p.write_text(s)
p=ui/'playback/PlaybackActivity.java';s=p.read_text()
s=once(s,'        PlaybackGestureHandler.Listener, VideoGroupAdapter.Listener {','''        PlaybackGestureHandler.Listener, VideoGroupAdapter.Listener,
        com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.Nm7EngineErrorOwner {''')
s=once(s,'    private String mNm7ErrorLabel;', '''    private String mNm7ErrorLabel;
    private com.google.android.exoplayer2.ExoPlaybackException mNm7LastEngineError;

    @Override public void onNm7OwnedEngineError(com.google.android.exoplayer2.ExoPlaybackException error) {
        mNm7LastEngineError = error;
        mNm7ErrorLabel = com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7PlaybackError.describe(error, error.type);
        if (mNm7Stopped || isFinishing() || isDestroyed() || sNm7SuspendedForIptv) return;
        // Decoder recovery is owned here. Do not also send the same decoder error into
        // ErrorFixerController, otherwise retry/restart paths can race and break playback.
        if (isNm7DecoderError(error)) {
            recoverNm7DecoderError(error);
            return;
        }
        mPlaybackPresenter.setView(this);
        mPlaybackPresenter.onEngineError(error.type, error.rendererIndex,
                error.getCause() == null ? error : error.getCause());
    }''')
s=method(s,'public static String getNm7ErrorLabel()', '''        PlaybackActivity owner = sNm7Active;
        if (owner == null || owner.mNm7Stopped || owner.isDestroyed()) return "Phiên phát đã kết thúc";
        if (owner.mPlayer == null) return "Bộ phát cần khởi tạo lại";
        com.google.android.exoplayer2.ExoPlaybackException error = owner.mPlayer.getPlaybackError();
        if (error != null) return com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7PlaybackError.describe(error, error.type);
        return owner.mNm7ErrorLabel == null ? "Nguồn phát chưa sẵn sàng" : owner.mNm7ErrorLabel;''')
s=method(s,'public static void toggleNm7Playback()', '''        PlaybackActivity owner = sNm7Active;
        if (owner == null || owner.mNm7Stopped || owner.isFinishing() || owner.isDestroyed() || sNm7SuspendedForIptv) return;
        owner.retryNm7Mini();''')
s=once(s,'        if (sNm7SuspendedForIptv || mNm7Stopped || mPlayer == null) return;','''        if (sNm7SuspendedForIptv || mNm7Stopped) return;
        mPlaybackPresenter.setView(this);
        if (mPlayer == null) {
            if (mNm7SessionVideo != null) {
                mNm7RecoveryWantsPlay = mNm7RecoveryRestoreIntent = true;
                initializePlayer();
            }
            return;
        }''')
s=once(s,'        if (mNm7RecoveryPending) {\n            mNm7RecoveryWantsPlay = !', '        if (mNm7RecoveryPending || (mNm7RecoveryRestoreIntent && mPlayer.getPlaybackError() == null && mPlayer.getPlaybackState() != Player.STATE_IDLE)) {\n            mNm7RecoveryWantsPlay = !')
s=once(s,'        if (error == null) { mPlayer.setPlayWhenReady(!mPlayer.getPlayWhenReady()); return; }','''        if (error == null) {
            if (mPlayer.getPlaybackState() == Player.STATE_IDLE && mNm7SessionVideo != null) {
                // A failed source reload can leave IDLE with no retained ExoPlayer exception.
                mNm7RecoveryWantsPlay = mNm7RecoveryRestoreIntent = true;
                restartEngine();
            } else mPlayer.setPlayWhenReady(!mPlayer.getPlayWhenReady());
            return;
        }''')
s=once(s,'                if (playbackState == Player.STATE_READY) {','''                if (playbackState == Player.STATE_READY && mPlayer.getPlaybackError() == null) {
                    mNm7LastEngineError = null;''')
# Compact metadata: keep description available behind title / explicit more, never repeat it inline.
s=once(s,'        mDescriptionCard.setVisibility(hasDescription ? View.VISIBLE : View.GONE);','''        mDescriptionCard.setVisibility(View.GONE);''')
s=once(s,'        mDescriptionDivider.setVisibility(hasDescription ? View.VISIBLE : View.GONE);','        mDescriptionDivider.setVisibility(View.GONE);')
s=once(s,'        initWindowInsets();','''        mPanelTitle.setOnClickListener(v -> openDescription());
        findViewById(R.id.nm7_details_more).setOnClickListener(v -> openDescription());
        mChannelIcon.setOnClickListener(v -> dispatchButton(BUTTON_CHANNEL));
        initWindowInsets();''')
# Cache references before the metadata view is moved into the RecyclerView.
s=once(s,'        mNm7CommentPreview = findViewById(R.id.nm7_comment_preview);','''        mNm7CommentPreview = findViewById(R.id.nm7_comment_preview);
        mNm7CommentsHeading = findViewById(R.id.nm7_comments_heading);''')
s=once(s,'    private TextView mNm7CommentPreview;','''    private TextView mNm7CommentPreview;
    private TextView mNm7CommentsHeading;
    private Disposable mNm7CommentPreviewAction;
    private CommentGroup mNm7PreviewGroup;
    private String mNm7PreviewKey;

    private void loadNm7CommentPreview() {
        if (mPlayer == null || mPlayer.getPlaybackState() != Player.STATE_READY
                || mNm7Stopped || sNm7SuspendedForIptv || mCommentsKey == null
                || TextUtils.equals(mNm7PreviewKey, mCommentsKey)) return;
        final String key = mCommentsKey;
        final String videoId = getVideo() == null ? null : getVideo().videoId;
        if (videoId == null) return;
        RxHelper.disposeActions(mNm7CommentPreviewAction);
        mNm7PreviewKey = key;
        mNm7CommentPreviewAction = YouTubeServiceManager.instance().getCommentsService()
                .getCommentsObserve(key).subscribe(group -> {
                    if (isDestroyed() || isFinishing() || !TextUtils.equals(key, mCommentsKey)
                            || getVideo() == null || !TextUtils.equals(videoId, getVideo().videoId)) return;
                    mNm7PreviewGroup = group;
                    showNm7CommentPreview(group);
                }, error -> {
                    if (TextUtils.equals(key, mCommentsKey) && mNm7CommentPreview != null)
                        mNm7CommentPreview.setText("Chạm để tải bình luận");
                });
    }

    private void showNm7CommentPreview(CommentGroup group) {
        if (group == null || group.getComments() == null || mNm7CommentPreview == null) return;
        for (CommentItem item : group.getComments()) {
            if (item == null || item.isEmpty()) continue;
            String author = item.getAuthorName();
            mNm7CommentPreview.setText((TextUtils.isEmpty(author) ? "" : author + "  ") + item.getMessage());
            Glide.with(this).load(item.getAuthorPhoto()).apply(RequestOptions.circleCropTransform())
                    .override(84, 84).into(mNm7CommentAvatar);
            return;
        }
        mNm7CommentPreview.setText("Chưa có bình luận");
    }''')
s=once(s,'        mCommentsKey = commentsKey;\n        updateNm7RatingCounts();', '        mCommentsKey = commentsKey;\n        updateNm7RatingCounts();\n        loadNm7CommentPreview();')
s=once(s,'                    mNm7LastEngineError = null;', '                    mNm7LastEngineError = null;\n                    loadNm7CommentPreview();')
s=once(s,'        loadComments(mCommentsKey, true);','''        if (mNm7PreviewGroup != null && TextUtils.equals(mCommentsKey, mNm7PreviewKey)) {
            mCommentsAdapter.clear();
            mDetailsMessage.setVisibility(View.GONE);
            onCommentGroup(mNm7PreviewGroup);
        } else loadComments(mCommentsKey, true);''')
s=once(s,'        mCommentsKey = null;', '''        mCommentsKey = null;
        RxHelper.disposeActions(mNm7CommentPreviewAction);
        mNm7CommentPreviewAction = null;
        mNm7PreviewGroup = null;
        mNm7PreviewKey = null;''')
s=once(s,'    protected void onDestroy() {','''    protected void onDestroy() {
        RxHelper.disposeActions(mNm7CommentPreviewAction);''')
# Preview title should not change to a later page's first comment.
a=s.index('        if (mStashedComments == null && group != null && group.getComments() != null');b=s.index('        mNextCommentsKey = group != null',a)
s=s[:a]+'''        if (mStashedComments == null && mCommentsAdapter.isEmpty()) showNm7CommentPreview(group);
'''+s[b:]
s=s.replace('            mNm7CommentPreview.setText("Xem bình luận…");','            mNm7CommentPreview.setText("Đang tải bình luận…");',1)
s=once(s,'    private void updateNm7RatingCounts() {\n        Video video = getVideo();',"""    private void updateNm7RatingCounts() {
        Video video = getVideo();
        if (mNm7CommentsHeading != null) mNm7CommentsHeading.setText("Bình luận" +
                (video == null || TextUtils.isEmpty(video.commentsCount) ? "" : " " + video.commentsCount));""")
p.write_text(s)
A='{http://schemas.android.com/apk/res/android}';B='{http://schemas.android.com/apk/res-auto}'
ET.register_namespace('android',A[1:-1]);ET.register_namespace('app',B[1:-1])
p=res/'layout/playback_activity.xml';tree=ET.parse(p);layout=tree.getroot()
def get(id):return next(e for e in layout.iter() if e.get(A+'id')=='@+id/'+id)
def attrs(e,**kw):
    for k,v in kw.items():e.set(A+k,v)
header=get('nm7_watch_metadata'); title=get('playback_title');second=get('playback_second_title')
attrs(title,maxLines='1',textSize='16sp',paddingTop='10dp',paddingBottom='4dp')
# The metadata summary shares one line with an explicit, reachable expansion affordance.
summary=ET.Element('LinearLayout',{A+'layout_width':'match_parent',A+'layout_height':'wrap_content',A+'orientation':'horizontal',A+'gravity':'center_vertical',A+'paddingEnd':'12dp'})
pos=list(header).index(second);header.remove(second);header.insert(pos,summary);summary.append(second)
attrs(second,layout_width='0dp',layout_weight='1',maxLines='1',paddingEnd='4dp',paddingRight='4dp',textSize='11sp')
ET.SubElement(summary,'TextView',{A+'id':'@+id/nm7_details_more',A+'layout_width':'wrap_content',A+'layout_height':'32dp',A+'gravity':'center',A+'text':'…xem thêm',A+'textColor':'#0F0F0F',A+'textSize':'12sp',A+'textStyle':'bold',A+'background':'?android:attr/selectableItemBackground'})
# Author is already in the summary; do not show a second author row.
attrs(get('playback_channel_row'),visibility='gone')
attrs(get('playback_channel_icon'),layout_width='32dp',layout_height='32dp')
chips=get('playback_chips');attrs(chips,minHeight='48dp',paddingTop='8dp',paddingBottom='8dp')
for e in chips.iter('com.google.android.material.button.MaterialButton'):
    attrs(e,layout_height='32dp',minHeight='32dp',textSize='12sp',letterSpacing='0',paddingStart='10dp',paddingLeft='10dp',paddingEnd='10dp',paddingRight='10dp')
    e.set(B+'iconSize','18dp');e.set(B+'iconPadding','6dp');e.set(B+'cornerRadius','16dp')
    if e.get(A+'id')=='@+id/playback_chip_share': attrs(e,text='',layout_width='42dp')
for e in chips.iter('LinearLayout'):
    if e.get(A+'background')=='@drawable/nm7_action_pill':attrs(e,layout_height='32dp')
attrs(get('playback_chip_like'),layout_width='wrap_content');attrs(get('playback_chip_dislike'),layout_width='wrap_content')
attrs(get('playback_comments_card'),layout_marginTop='4dp',layout_marginBottom='14dp',padding='12dp')
attrs(get('nm7_comment_preview'),background='@android:color/transparent',padding='0dp',textSize='12sp',text='Đang tải bình luận…')
attrs(get('playback_description_card'),visibility='gone');attrs(get('playback_description_divider'),visibility='gone')
ET.indent(tree,space='    ');tree.write(p,encoding='utf-8',xml_declaration=True)
# Outline glyphs at the reference scale; preserve the same action IDs/handlers.
for name,path in {
'playback_ic_like':'M8,21 L4,21 L4,10 L8,10 Z M8,10 L12,4 C13,2 15,3 15,5 L14,9 L20,9 C21,9 22,10 21.7,11.5 L20,19 C19.7,20.3 19,21 17,21 L8,21',
'playback_ic_dislike':'M8,3 L4,3 L4,14 L8,14 Z M8,14 L12,20 C13,22 15,21 15,19 L14,15 L20,15 C21,15 22,14 21.7,12.5 L20,5 C19.7,3.7 19,3 17,3 L8,3',
'playback_ic_share':'M14,4 L22,11 L14,18 L14,13 C9,13 5,15 2,20 C2,11 7,8 14,8 Z'
}.items():
    (res/f'drawable/{name}.xml').write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:pathData="{path}" android:fillColor="#00000000" android:strokeColor="#FF0F0F0F" android:strokeWidth="1.7" android:strokeLineJoin="round" android:strokeLineCap="round"/></vector>')
print('v59 engine-owner recovery, compact actions, explicit details and lazy real comment preview applied')
