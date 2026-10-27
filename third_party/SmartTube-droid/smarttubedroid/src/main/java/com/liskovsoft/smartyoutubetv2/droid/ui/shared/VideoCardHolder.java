package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.app.Activity;
import android.content.Context;
import android.os.Build.VERSION;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.droid.R;

/**
 * ViewHolder for a single video card (grid or row style).<br/>
 * Binds title, second title, thumbnail (Glide with fallback), duration badge,
 * LIVE/SHORTS badges, NEW dot and watch-progress bar.<br/>
 * Layouts: {@code shared_video_card.xml} (grid), {@code shared_video_card_row.xml} (row).
 */
public class VideoCardHolder extends RecyclerView.ViewHolder {
    private final ImageView mThumbnail;
    private final TextView mBadge;
    private final View mNewDot;
    private final ProgressBar mProgress;
    private final TextView mTitle;
    private final TextView mSubtitle;
    private Video mVideo;
    public static android.graphics.Bitmap sNm7TransitionPoster;
    private static String sNm7TransitionVideoId;
    private static final java.util.concurrent.ConcurrentHashMap<String, String> NM7_AVATAR_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> NM7_AVATAR_LOADING =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * Inflates the proper card layout and wraps it into a holder.
     *
     * @param parent recycler parent
     * @param isRow true: small horizontal-row card, false: grid card
     */
    public static VideoCardHolder create(ViewGroup parent, boolean isRow) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(!isRow ? R.layout.nm7_video_card : isRow ? R.layout.shared_video_card_row : R.layout.shared_video_card, parent, false);
        return new VideoCardHolder(view);
    }

    public VideoCardHolder(View itemView) {
        super(itemView);

        mThumbnail = itemView.findViewById(R.id.shared_card_thumbnail);
        mBadge = itemView.findViewById(R.id.shared_card_badge);
        mNewDot = itemView.findViewById(R.id.shared_card_new_dot);
        mProgress = itemView.findViewById(R.id.shared_card_progress);
        mTitle = itemView.findViewById(R.id.shared_card_title);
        mSubtitle = itemView.findViewById(R.id.shared_card_subtitle);
    }

    /**
     * Binds a video to the card and wires tap/long-press to the listener.
     */
    public void bind(Video video, VideoGroupAdapter.Listener listener) {
        mVideo = video;

        if (video == null) {
            return;
        }

        Context context = itemView.getContext();

        mTitle.setText(video.getTitle());
        itemView.setContentDescription(video.getTitle());

        CharSequence secondTitle = video.getSecondTitle();
        if (TextUtils.isEmpty(secondTitle)) {
            mSubtitle.setVisibility(View.GONE);
        } else {
            mSubtitle.setVisibility(View.VISIBLE);
            mSubtitle.setText(secondTitle);
        }

        bindBadges(context, video);
        bindProgress(video);
        bindThumbnail(context, video);

        View menu = itemView.findViewById(R.id.nm7_card_menu);
        if (menu != null) menu.setOnClickListener(listener == null ? null : v -> listener.onVideoLongClicked(video));
        if (listener != null) {
            itemView.setOnClickListener(v -> {
                nm7CaptureTransitionPoster(video);
                listener.onVideoClicked(video);
            });
            itemView.setOnLongClickListener(v -> {
                listener.onVideoLongClicked(video);
                return true;
            });
        }
    }

    /**
     * Releases image resources. Call from {@code onViewRecycled}.
     */
    public void unbind() {
        mVideo = null;
        View menu = itemView.findViewById(R.id.nm7_card_menu);
        if (menu != null) menu.setOnClickListener(null);
        View avatar = itemView.findViewById(R.id.shared_card_avatar);
        if (avatar instanceof android.widget.ImageView) ((android.widget.ImageView) avatar).setImageResource(R.drawable.browse_ic_account);

        itemView.setOnClickListener(null);
        itemView.setOnLongClickListener(null);

        // Application context: the holder may outlive its activity
        Glide.with(itemView.getContext().getApplicationContext()).clear(mThumbnail);
        mThumbnail.setImageDrawable(null);
    }

    /**
     * Currently bound video or null.
     */
    public Video getVideo() {
        return mVideo;
    }

    private void nm7CaptureTransitionPoster(Video video) {
        if (video == null || TextUtils.isEmpty(video.videoId) || mThumbnail == null) return;
        android.graphics.drawable.Drawable drawable = mThumbnail.getDrawable();
        if (drawable == null) return;

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            width = mThumbnail.getWidth();
            height = mThumbnail.getHeight();
        }
        if (width <= 0 || height <= 0) return;

        // Keep the transient handoff bitmap bounded while preserving aspect ratio.
        final int maxWidth = 1280;
        if (width > maxWidth) {
            height = Math.max(1, Math.round(height * (maxWidth / (float) width)));
            width = maxWidth;
        }

        try {
            android.graphics.Bitmap bitmap = null;
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                // Preserve the exact high-quality bitmap Glide decoded for the card.
                // Re-drawing it into a new bitmap caused a visible softening on handoff.
                bitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            }
            if (bitmap == null) {
                bitmap = android.graphics.Bitmap.createBitmap(
                        width, height, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                android.graphics.Rect oldBounds = drawable.copyBounds();
                drawable.setBounds(0, 0, width, height);
                drawable.draw(canvas);
                drawable.setBounds(oldBounds);
            }

            synchronized (VideoCardHolder.class) {
                sNm7TransitionPoster = bitmap;
                sNm7TransitionVideoId = video.videoId;
            }
        } catch (RuntimeException ignored) { }
    }

    public static android.graphics.Bitmap consumeNm7TransitionPoster(String videoId) {
        synchronized (VideoCardHolder.class) {
            if (TextUtils.isEmpty(videoId)
                    || !TextUtils.equals(videoId, sNm7TransitionVideoId)
                    || sNm7TransitionPoster == null) {
                return null;
            }
            android.graphics.Bitmap result = sNm7TransitionPoster;
            sNm7TransitionPoster = null;
            sNm7TransitionVideoId = null;
            return result;
        }
    }

    private void bindBadges(Context context, Video video) {
        String badgeText;
        boolean isRedBadge;

        if (video.isLive) {
            badgeText = context.getString(com.liskovsoft.smartyoutubetv2.common.R.string.badge_live);
            isRedBadge = true;
        } else if (video.isShorts) {
            badgeText = context.getString(com.liskovsoft.smartyoutubetv2.common.R.string.header_shorts).toUpperCase();
            isRedBadge = false;
        } else {
            badgeText = video.badge; // duration text usually
            isRedBadge = video.isUpcoming;
        }

        if (TextUtils.isEmpty(badgeText)) {
            mBadge.setVisibility(View.GONE);
        } else {
            mBadge.setVisibility(View.VISIBLE);
            mBadge.setText(badgeText);
            mBadge.setBackgroundResource(isRedBadge ? R.drawable.shared_badge_live_bg : R.drawable.shared_badge_bg);
        }

        mNewDot.setVisibility(video.hasNewContent ? View.VISIBLE : View.GONE);
    }

    private void bindProgress(Video video) {
        // Count progress that very close to zero. E.g. when user closed video immediately.
        int progress = video.percentWatched > 0 && video.percentWatched < 1 ? 1 : Math.round(video.percentWatched);

        if (progress > 0) {
            mProgress.setVisibility(View.VISIBLE);
            mProgress.setMax(100);
            mProgress.setProgress(Math.min(progress, 100));
        } else {
            mProgress.setVisibility(View.GONE);
        }
    }

    private void bindThumbnail(Context context, Video video) {
// NM7 channel avatar: use the YouTube channel thumbnail propagated from
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
                            "getChannelThumbnailUrl", "getChannelThumbnail", "getChannelAvatarUrl", "getChannelAvatar",
                            "getAuthorThumbnailUrl", "getAuthorThumbnail", "getAuthorAvatarUrl", "getAuthorAvatar"
                    };
                    for (String name : mediaMethods) {
                        try {
                            java.lang.reflect.Method mm = mediaItem.getClass().getMethod(name);
                            Object value = mm.invoke(mediaItem);
                            if (value instanceof String && ((String) value).startsWith("http")) {
                                avatarUrl = (String) value;
                                break;
                            }
                            if (value != null) {
                                String[] nestedAvatarMethods = {
                                        "getUrl", "getThumbnailUrl", "getImageUrl",
                                        "getAvatarUrl", "getThumbnail", "getImage", "getAvatar"
                                };
                                for (String nestedName : nestedAvatarMethods) {
                                    try {
                                        java.lang.reflect.Method nm = value.getClass().getMethod(nestedName);
                                        Object nestedValue = nm.invoke(value);
                                        if (nestedValue instanceof String && ((String) nestedValue).startsWith("http")) {
                                            avatarUrl = (String) nestedValue;
                                            break;
                                        }
                                    } catch (ReflectiveOperationException | RuntimeException ignored) {}
                                }
                                if (avatarUrl != null) break;
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
                nm7ResolveAvatar(context, video, avatar);
            }
        }

        String cardImageUrl = video.getCardImageUrl();
        // Ask YouTube for the highest useful card resolution without downgrading
        // an already high-resolution URL.
        if (cardImageUrl != null && cardImageUrl.contains("ytimg.com")) {
            String originalCardImageUrl = cardImageUrl;
            String highResCardImageUrl = cardImageUrl;
            if (cardImageUrl.contains("/default.jpg")) {
                highResCardImageUrl = cardImageUrl.replace("/default.jpg", "/maxresdefault.jpg");
            } else if (cardImageUrl.contains("/mqdefault.jpg")) {
                highResCardImageUrl = cardImageUrl.replace("/mqdefault.jpg", "/maxresdefault.jpg");
            } else if (cardImageUrl.contains("/hqdefault.jpg")) {
                highResCardImageUrl = cardImageUrl.replace("/hqdefault.jpg", "/maxresdefault.jpg");
            }
            String sdResCardImageUrl = highResCardImageUrl.replace("/maxresdefault.jpg", "/sddefault.jpg");
            com.bumptech.glide.request.RequestOptions nm7SharpThumb =
                    new com.bumptech.glide.request.RequestOptions()
                            .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
                            .override(1280, 720)
                            .dontTransform();
            Glide.with(context)
                    .load(highResCardImageUrl)
                    .apply(nm7SharpThumb)
                    .error(Glide.with(context).load(sdResCardImageUrl).apply(nm7SharpThumb)
                            .error(Glide.with(context).load(originalCardImageUrl).apply(nm7SharpThumb)))
                    .into((android.widget.ImageView) itemView.findViewById(R.id.shared_card_thumbnail));
        } else {
            Glide.with(context)
                    .load(cardImageUrl)
                    .into((android.widget.ImageView) itemView.findViewById(R.id.shared_card_thumbnail));
        }

    }

    private void nm7ResolveAvatar(Context context, Video video, android.widget.ImageView avatar) {
        if (video == null || TextUtils.isEmpty(video.videoId) || avatar == null) return;
        final String videoId = video.videoId;
        final String cacheKey = !TextUtils.isEmpty(video.channelId) ? video.channelId : videoId;
        String cached = NM7_AVATAR_CACHE.get(cacheKey);
        if (!TextUtils.isEmpty(cached)) {
            Glide.with(context).load(cached).circleCrop()
                    .placeholder(R.drawable.browse_ic_account)
                    .error(R.drawable.browse_ic_account).into(avatar);
            return;
        }
        if (!NM7_AVATAR_LOADING.add(cacheKey)) return;

        com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance()
                .getMediaItemService().getMetadataObserve(videoId)
                                .take(1)
                // Serialize optional avatar enrichment. Initial feed + thumbnails and
                // selected-video format requests keep the I/O/network priority.
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(metadata -> {
                    NM7_AVATAR_LOADING.remove(cacheKey);
                    String url = metadata != null ? metadata.getAuthorImageUrl() : null;
                    if (TextUtils.isEmpty(url)) return;
                    NM7_AVATAR_CACHE.put(cacheKey, url);
                    if (mVideo != null && TextUtils.equals(mVideo.videoId, videoId)
                            && !((context instanceof Activity) && ((Activity) context).isDestroyed())) {
                        Glide.with(context).load(url).circleCrop()
                                .placeholder(R.drawable.browse_ic_account)
                                .error(R.drawable.browse_ic_account).into(avatar);
                    }
                }, error -> NM7_AVATAR_LOADING.remove(cacheKey));
    }

    private static RequestOptions glideOptions() {
        return new RequestOptions()
                .skipMemoryCache(true) // mirror TV: ensure animated thumbs restart
                // Cache makes app crashing on old android versions
                .diskCacheStrategy(VERSION.SDK_INT > 21 ? DiskCacheStrategy.ALL : DiskCacheStrategy.NONE);
    }
}
