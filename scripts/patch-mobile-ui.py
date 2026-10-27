"""Presentation-only overlay on the verified v39 playback patch."""
from pathlib import Path
import shutil
import re

root = Path('third_party/SmartTube-droid/smarttubedroid/src/main')
ui = root / 'java/com/liskovsoft/smartyoutubetv2/droid/ui'

def replace(text, old, new):
    if text.count(old) != 1:
        raise SystemExit('UI anchor missing/ambiguous: ' + old[:100])
    return text.replace(old, new, 1)

def replace_first(text, old, new):
    if old in text:
        return text.replace(old, new, 1)
    # SmartTube formatting can differ between pinned snapshots. Locate the
    # bindThumbnail method by signature and replace its balanced Java body.
    if 'video.getCardImageUrl()' in old:
        signature = '    private void bindThumbnail(Context context, Video video) {'
        pos = text.find(signature)
        if pos >= 0:
            brace = text.find('{', pos)
            depth = 0
            end = -1
            for i in range(brace, len(text)):
                ch = text[i]
                if ch == '{':
                    depth += 1
                elif ch == '}':
                    depth -= 1
                    if depth == 0:
                        end = i + 1
                        break
            if end > 0:
                return text[:pos] + signature + '\n' + new.lstrip() + '\n    }' + text[end:]
    raise SystemExit('UI anchor missing: ' + old[:100])

for source in Path('scripts/mobile-ui/res').rglob('*.xml'):
    dest = root / 'res' / source.relative_to('scripts/mobile-ui/res')
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, dest)
for source in Path('scripts/mobile-ui/java').glob('*.java'):
    shutil.copyfile(source, ui / 'shared' / source.name)
p = ui / 'browse/BrowseActivity.java'
s = p.read_text()
s = s.replace('VideoRowsAdapter', 'Nm7FeedAdapter')
s = replace(s, 'mRowsView.setAdapter(mRowsAdapter);', 'mRowsView.setAdapter(mRowsAdapter.adapter);')
s = replace(s, '        toolbar.setTitle("SmartTube Mobile");', '''        findViewById(R.id.nm7_search).setOnClickListener(v -> SearchPresenter.instance(this).startSearch(null));
        findViewById(R.id.nm7_voice).setOnClickListener(v -> SearchPresenter.instance(this).startVoice());
        findViewById(R.id.nm7_account).setOnClickListener(v -> AccountSettingsPresenter.instance(this).show());
        findViewById(R.id.nm7_search).setOnLongClickListener(v -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(this, v);
            menu.getMenuInflater().inflate(R.menu.browse_toolbar, menu.getMenu());
            menu.setOnMenuItemClickListener(this::onToolbarItemClicked);
            menu.show();
            return true;
        });''')
s = replace(s, '    private static final int GRID_COLUMNS = 1;', '''    public void nm7OpenLibrary() {
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
    private boolean mNm7SwipeVertical;
    private boolean mNm7SwipeMultiple;
    private int mNm7SwipeSlop;''')
s = replace(s, '        mGridView = findViewById(R.id.browse_grid);', '''        mNm7BrowseContent = findViewById(R.id.browse_content);
        mNm7SwipeSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        mGridView = findViewById(R.id.browse_grid);''')
s = replace(s, '    public void onBackPressed() {', '''    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        final int action = event.getActionMasked();

        if (action == android.view.MotionEvent.ACTION_DOWN) {
            mNm7SwipeDownX = event.getX();
            mNm7SwipeDownY = event.getY();
            mNm7SwipeVertical = false;
            mNm7SwipeMultiple = false;
            mNm7SwipeEligible = mNm7BrowseContent != null
                    && mNm7BrowseContent.getVisibility() == android.view.View.VISIBLE
                    && mNm7SwipeDownY >= mNm7BrowseContent.getTop()
                    && mNm7SwipeDownY <= mNm7BrowseContent.getBottom();
        } else if (action == android.view.MotionEvent.ACTION_POINTER_DOWN) {
            mNm7SwipeMultiple = true;
        } else if (action == android.view.MotionEvent.ACTION_MOVE && mNm7SwipeEligible) {
            float dx = Math.abs(event.getX() - mNm7SwipeDownX);
            float dy = Math.abs(event.getY() - mNm7SwipeDownY);
            if (dy > mNm7SwipeSlop && dy >= dx) {
                mNm7SwipeVertical = true;
            }
        } else if (action == android.view.MotionEvent.ACTION_UP && mNm7SwipeEligible) {
            float dx = event.getX() - mNm7SwipeDownX;
            float dy = event.getY() - mNm7SwipeDownY;
            float density = getResources().getDisplayMetrics().density;
            float threshold = Math.max(mNm7SwipeSlop * 3f,
                    Math.min(mNm7BrowseContent.getWidth() * 0.14f, 56f * density));

            if (!mNm7SwipeMultiple && !mNm7SwipeVertical
                    && Math.abs(dx) >= threshold
                    && Math.abs(dx) > Math.abs(dy) * 1.20f
                    && mTabLayout != null && mTabLayout.getTabCount() > 0) {
                int direction = dx < 0 ? 1 : -1;
                if (mNm7BrowseContent.getLayoutDirection() == android.view.View.LAYOUT_DIRECTION_RTL) {
                    direction = -direction;
                }
                int target = mTabLayout.getSelectedTabPosition() + direction;
                if (target >= 0 && target < mTabLayout.getTabCount()) {
                    selectSection(target, false);
                }
            }
            mNm7SwipeEligible = false;
        } else if (action == android.view.MotionEvent.ACTION_CANCEL) {
            mNm7SwipeEligible = false;
        }

        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        try {
            if (com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity.consumeNm7BrowseBack()) {
                return;
            }
        } catch (RuntimeException error) {
            android.util.Log.e("NM7Playback", "Browse Back mini close failed", error);
        }''')

p.write_text(s)
p = ui / 'shared/VideoCardHolder.java'
s = p.read_text()
s = replace(s, '.inflate(isRow ? R.layout.shared_video_card_row : R.layout.shared_video_card, parent, false);',
    '.inflate(!isRow && parent.getContext() instanceof com.liskovsoft.smartyoutubetv2.droid.ui.browse.BrowseActivity ? R.layout.nm7_video_card : isRow ? R.layout.shared_video_card_row : R.layout.shared_video_card, parent, false);')
s = replace(s, '        if (listener != null) {', '''        View menu = itemView.findViewById(R.id.nm7_card_menu);
        if (menu != null) menu.setOnClickListener(listener == null ? null : v -> listener.onVideoLongClicked(video));
        if (listener != null) {''')
s = replace(s, '        mVideo = null;', '''        mVideo = null;
        View menu = itemView.findViewById(R.id.nm7_card_menu);
        if (menu != null) menu.setOnClickListener(null);
        View avatar = itemView.findViewById(R.id.shared_card_avatar);
        if (avatar instanceof android.widget.ImageView) ((android.widget.ImageView) avatar).setImageResource(R.drawable.browse_ic_account);''')
s = replace_first(s, '''        Glide.with(context)\n                .load(video.getCardImageUrl())\n                .apply(glideOptions())\n                .placeholder(R.drawable.shared_card_placeholder)\n                .error(\n                        Glide.with(context)\n                                .load(video.cardImageUrl)\n                                .apply(glideOptions())\n                                .error(R.drawable.shared_card_placeholder)\n                )\n                .into(mThumbnail);''', '''        // NM7 channel avatar: use the YouTube channel thumbnail propagated from
        // VideoItem/YouTubeMediaItem first; only then inspect nested model fields.
        android.widget.ImageView avatar = itemView.findViewById(R.id.shared_card_avatar);
        if (avatar != null) {
            String avatarUrl = video.channelThumbnailUrl;
            if (avatarUrl != null && !avatarUrl.startsWith("http")) avatarUrl = null;

            // The common Video model can keep the original media item. Resolve the
            // avatar directly from it as a fallback when the copied field is empty.
            if (avatarUrl == null) {
                try {
                    java.lang.reflect.Field mediaField = video.getClass().getDeclaredField("mediaItem");
                    mediaField.setAccessible(true);
                    Object mediaItem = mediaField.get(video);
                    String[] mediaMethods = {
                            "getChannelThumbnailUrl", "getChannelThumbnail",
                            "getAuthorAvatarUrl", "getAuthorAvatar"
                    };
                    for (String name : mediaMethods) {
                        try {
                            java.lang.reflect.Method mm = mediaItem.getClass().getMethod(name);
                            Object value = mm.invoke(mediaItem);
                            if (value instanceof String && ((String) value).startsWith("http")) {
                                avatarUrl = (String) value;
                                break;
                            }
                        } catch (ReflectiveOperationException | RuntimeException ignored) {}
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
            String[] directMethods = {
                    "getAuthorAvatarUrl", "getAuthorAvatar", "getChannelAvatarUrl", "getChannelAvatar",
                    "getAuthorImageUrl", "getChannelImageUrl", "getAuthorIconUrl", "getChannelIconUrl"
            };
            for (String name : directMethods) {
                if (avatarUrl != null) break;
                try {
                    java.lang.reflect.Method m = video.getClass().getMethod(name);
                    Object value = m.invoke(video);
                    if (value instanceof String && ((String) value).startsWith("http")) {
                        avatarUrl = (String) value;
                        break;
                    }
                    if (value != null) {
                        String[] nestedMethods = {"getAvatarUrl", "getAvatar", "getImageUrl", "getImage", "getThumbnailUrl", "getThumbnail", "getUrl"};
                        for (String nested : nestedMethods) {
                            try {
                                java.lang.reflect.Method nm = value.getClass().getMethod(nested);
                                Object nv = nm.invoke(value);
                                if (nv instanceof String && ((String) nv).startsWith("http")) {
                                    avatarUrl = (String) nv;
                                    break;
                                }
                            } catch (ReflectiveOperationException | RuntimeException ignored) {}
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
            if (avatarUrl != null) {
                Glide.with(context).load(avatarUrl).circleCrop()
                        .placeholder(R.drawable.browse_ic_account)
                        .error(R.drawable.browse_ic_account).into(avatar);
            } else {
                avatar.setImageResource(R.drawable.browse_ic_account);
            }
        }

        String cardImageUrl = video.getCardImageUrl();
        // Ask YouTube for the highest useful card resolution without downgrading
        // an already high-resolution URL.
        if (cardImageUrl != null && cardImageUrl.contains("ytimg.com")) {
            String originalCardImageUrl = cardImageUrl;
            String highResCardImageUrl = cardImageUrl;
            if (cardImageUrl.contains("/default.jpg")) {
                highResCardImageUrl = cardImageUrl.replace("/default.jpg", "/hqdefault.jpg");
            } else if (cardImageUrl.contains("/mqdefault.jpg")) {
                highResCardImageUrl = cardImageUrl.replace("/mqdefault.jpg", "/hqdefault.jpg");
            } else if (cardImageUrl.contains("/hqdefault.jpg")) {
                highResCardImageUrl = cardImageUrl.replace("/hqdefault.jpg", "/hqdefault.jpg");
            }
            Glide.with(context)
                    .load(highResCardImageUrl)
                    .error(Glide.with(context).load(originalCardImageUrl))
                    .into((android.widget.ImageView) itemView.findViewById(R.id.shared_card_thumbnail));
        } else {
            Glide.with(context)
                    .load(cardImageUrl)
                    .into((android.widget.ImageView) itemView.findViewById(R.id.shared_card_thumbnail));
        }
''')
p.write_text(s)

# Avatar data path: VideoItem -> YouTubeMediaItem -> Video -> VideoCardHolder.
video_candidates = list(Path('third_party/SmartTube-droid').rglob('Video.java'))
if not video_candidates:
    raise SystemExit('Avatar patch: Video.java not found')
video_model = video_candidates[0]
vs = video_model.read_text()
if 'public String channelThumbnailUrl;' not in vs:
    vs = vs.replace('    public String author;\n', '    public String author;\n    public String channelThumbnailUrl;\n', 1)
vs = vs.replace(
    '        video.mediaItem = item;\n\n        return video;',
    '''        video.mediaItem = item;
        try {
            java.lang.reflect.Method avatarMethod = item.getClass().getMethod("getChannelThumbnailUrl");
            Object avatar = avatarMethod.invoke(item);
            if (avatar instanceof String && ((String) avatar).startsWith("http")) {
                video.channelThumbnailUrl = (String) avatar;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}

        return video;''', 1)
vs = vs.replace('        video.author = item.author;\n', '        video.author = item.author;\n        video.channelThumbnailUrl = item.channelThumbnailUrl;\n', 1)
video_model.write_text(vs)

media_item = root.parent.parent.parent / 'MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/data/YouTubeMediaItem.java'
if media_item.exists():
    ms = media_item.read_text()
    if 'private String mChannelThumbnailUrl;' not in ms:
        ms = ms.replace('    private String mChannelId;\n', '    private String mChannelId;\n    private String mChannelThumbnailUrl;\n', 1)

    marker = '        video.mChannelId = item.getChannelId();'
    if 'video.mChannelThumbnailUrl = (String) thumb;' not in ms:
        block = '''        video.mChannelId = item.getChannelId();
        try {
            java.lang.reflect.Method thumbMethod = item.getClass().getMethod("getChannelThumbnail");
            Object thumb = thumbMethod.invoke(item);
            if (thumb instanceof String) {
                video.mChannelThumbnailUrl = (String) thumb;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
'''
        ms = ms.replace(marker, block, 1)

    # Remove only the stale override attached to the NM7-only getter.\n    ms = re.sub(r'(?m)^[ \\t]*@Override[ \\t]*\\n(?=[ \\t]*public String getChannelThumbnailUrl\\(\\))', '', ms, count=1)
    if 'public String getChannelThumbnailUrl()' not in ms:
        anchor = '    public String getChannelId() {'
        getter = '''    public String getChannelThumbnailUrl() {
        return mChannelThumbnailUrl;
    }

'''
        ms = ms.replace(anchor, getter + anchor, 1)
    media_item.write_text(ms)

print('NM7 Mobile avatar-only channelThumbnail fix applied')
