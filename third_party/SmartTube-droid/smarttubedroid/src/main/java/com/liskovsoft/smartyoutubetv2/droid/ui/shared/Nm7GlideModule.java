package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.content.Context;
import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.module.GlideModule;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.engine.bitmap_recycle.LruArrayPool;

/** Budget image caches together with the video buffer, instead of TV-size defaults. */
@SuppressWarnings("deprecation")
public final class Nm7GlideModule implements GlideModule {
    @Override public void applyOptions(Context context, GlideBuilder builder) {
        long heap = Runtime.getRuntime().maxMemory();
        builder.setMemoryCache(new LruResourceCache(Math.min(16L * 1024 * 1024, heap / 16)));
        builder.setBitmapPool(new LruBitmapPool(Math.min(8L * 1024 * 1024, heap / 32)));
        builder.setArrayPool(new LruArrayPool(2 * 1024 * 1024));
    }
    @Override public void registerComponents(Context context, Glide glide, com.bumptech.glide.Registry registry) { }
}
