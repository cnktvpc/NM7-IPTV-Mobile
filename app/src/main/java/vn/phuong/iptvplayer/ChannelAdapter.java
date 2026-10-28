package vn.phuong.iptvplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ChannelAdapter extends BaseAdapter {
    interface Listener {
        void onSelectionChanged();
        void onFavoriteChanged(Channel channel, boolean favorite);
    }

    private static final int MAX_LOGO_BYTES = 2 * 1024 * 1024;
    private static final int LOGO_CACHE_KB = 128 * 1024;
    private static final long FAILED_LOGO_TTL_MS = 30 * 60 * 1000L;
    private static final int CONNECT_TIMEOUT_MS = 4_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final int PREFETCH_COUNT = 100;

    private static final LruCache<String, Bitmap> LOGO_CACHE =
            new LruCache<String, Bitmap>(LOGO_CACHE_KB) {
                @Override protected int sizeOf(String key, Bitmap bitmap) {
                    return Math.max(1, bitmap.getAllocationByteCount() / 1024);
                }
            };

    private static final Object WAITERS_LOCK = new Object();
    private static final Map<String, List<LogoWaiter>> LOGO_WAITERS = new HashMap<>();
    private static final Map<String, Boolean> LOGO_LOADING = new HashMap<>();
    private static final Map<String, Long> LOGO_FAILED_UNTIL = new HashMap<>();
    private static final Map<String, String> RESOLVED_LOGO_BY_CHANNEL = new HashMap<>();

    /*
     * One shared OkHttp client is deliberately used for every channel logo.
     * This gives us connection pooling, HTTP/2 multiplexing and transparent
     * gzip handling instead of opening a new HttpURLConnection for each row.
     */
    private static final OkHttpClient LOGO_HTTP = NetworkClient.LOGO_HTTP;
    private static final java.util.regex.Pattern SAFE_HEADER_KEY = java.util.regex.Pattern.compile("[!#$%&'*+.^_|~0-9A-Za-z-]+");
    private static final long MAX_DISK_CACHE_BYTES = 40L * 1024L * 1024L;
    private static volatile boolean diskCacheTrimScheduled;

    private final LayoutInflater inflater;
    private final Context context;
    private final Listener listener;
    private final ExecutorService diskIo = Executors.newFixedThreadPool(2);
    private final File logoCacheDir;
    private List<Channel> channels = new ArrayList<>();
    private EpgStore.Guide guide;
    private String playingChannelId = "";

    public ChannelAdapter(Context context, Listener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
        logoCacheDir = new File(context.getCacheDir(), "channel-logos-v4");
        if (!logoCacheDir.exists()) logoCacheDir.mkdirs();
        if (!diskCacheTrimScheduled) {
            diskCacheTrimScheduled = true;
            diskIo.execute(this::trimDiskCacheIfNeeded);
        }
    }

    public void submit(List<Channel> channels) {
        this.channels = new ArrayList<>(channels);
        notifyDataSetChanged();

        // Prefetch the whole selected group, bounded to keep very large
        // playlists from creating an excessive number of requests. The VTV and
        // VTVCab groups are normally well below this bound, so switching away
        // and back can reuse RAM cache immediately.
        int count = Math.min(PREFETCH_COUNT, this.channels.size());
        for (int i = 0; i < count; i++) {
            prefetch(this.channels.get(i));
        }
    }

    public void submitGuide(EpgStore.Guide guide) {
        this.guide = guide;
        EpgStore.setGuide(guide);
        notifyDataSetChanged();
    }

    EpgStore.Guide guide() {
        return guide;
    }

    void setPlayingChannel(Channel channel) {
        String next = channel == null ? "" : AppPreferences.id(channel);
        if (next.equals(playingChannelId)) return;
        playingChannelId = next;
        notifyDataSetChanged();
    }

    /** Reuses the existing asynchronous/disk-cached logo pipeline for non-channel cards. */
    public void bindLogo(View itemRoot, Channel channel) {
        if (itemRoot == null || channel == null) return;
        Holder holder = new Holder(itemRoot);
        if (holder.logo != null && holder.badge != null) loadLogo(holder, channel);
    }

    @Override public int getCount() { return channels.size(); }
    @Override public Channel getItem(int position) { return channels.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_channel, parent, false);
            h = new Holder(convertView);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }

        Channel c = getItem(position);
        h.name.setText(c.name());
        h.group.setText(c.group());
        h.url.setText(c.url());

        EpgStore.Programme programme = guide == null ? null : guide.find(c);
        h.epg.setVisibility(programme == null ? View.GONE : View.VISIBLE);
        if (programme != null) {
            h.programme.setText(programme.title);
            h.epgStart.setText(programme.startText());
            h.epgEnd.setText(programme.endText());
            h.epgProgress.setProgress(programme.progress());
        }

        h.url.setVisibility(AppPreferences.showUrls(context) ? View.VISIBLE : View.GONE);
        h.badge.setText(c.name().isEmpty() ? "TV"
                : c.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT));

        h.favorite.setText(AppPreferences.isFavorite(context, c) ? "★" : "☆");
        h.favorite.setOnClickListener(v -> {
            boolean favorite = AppPreferences.toggleFavorite(context, c);
            h.favorite.setText(favorite ? "★" : "☆");
            listener.onFavoriteChanged(c, favorite);
        });

        if (h.catchup != null) {
            h.catchup.setVisibility(c.hasCatchup() ? View.VISIBLE : View.GONE);
        }

        boolean tv = AppPreferences.isTvInterface(context);
        boolean compact = AppPreferences.compactRows(context);
        boolean playing = !tv && !playingChannelId.isEmpty()
                && playingChannelId.equals(AppPreferences.id(c));

        convertView.setBackgroundResource(
                playing ? R.drawable.channel_card_playing : R.drawable.channel_card);
        convertView.setMinimumHeight(dp(tv ? 82 : (compact ? 58 : 64)));
        int vertical = dp(tv ? 4 : 2);
        convertView.setPadding(0, vertical, 0, vertical);

        loadLogo(h, c);
        return convertView;
    }

    /**
     * Fast logo pipeline (1.10.110):
     * 1) normalize known GitHub/raw URL forms;
     * 2) memory cache;
     * 3) asynchronous disk cache (never on the UI thread);
     * 4) one shared OkHttp connection pool;
     * 5) deduplicated in-flight requests;
     * 6) small decoded bitmaps suitable for the 58dp x 36dp thumbnail;
     * 7) retry every candidate source before falling back to the letter badge.
     */
    private void loadLogo(Holder holder, Channel channel) {
        List<String> candidates = channel == null
                ? Collections.emptyList()
                : normalizedCandidates(channel.logoCandidates());

        holder.logo.setVisibility(View.GONE);
        holder.badge.setVisibility(View.VISIBLE);

        if (candidates.isEmpty() || channel == null) {
            holder.logo.setTag(null);
            return;
        }

        String channelKey = channel.identityKey();
        String resolved = resolvedLogo(channelKey);
        if (resolved != null) {
            int resolvedIndex = candidates.indexOf(resolved);
            candidates = new ArrayList<>(candidates);
            if (resolvedIndex >= 0) candidates.remove(resolvedIndex);
            candidates.add(0, resolved);
        }

        // Serve a previously resolved logo synchronously from RAM. This makes
        // VTV -> VTVCab -> VTV switching instant instead of restarting downloads.
        for (String candidate : candidates) {
            Bitmap cached = LOGO_CACHE.get(cacheKey(candidate, channel.headers()));
            if (cached != null) {
                holder.logo.setTag(candidate);
                showLogo(holder, candidate, cached);
                rememberResolved(channelKey, candidate);
                return;
            }
        }

        holder.logo.setImageDrawable(null);
        holder.logo.setTag(candidates.get(0));
        requestLogo(holder, candidates, channel.headers(), 0, channelKey);
    }

    private void prefetch(Channel channel) {
        if (channel == null) return;
        List<String> candidates = normalizedCandidates(channel.logoCandidates());
        if (candidates.isEmpty()) return;
        requestLogo(null, candidates, channel.headers(), 0, channel.identityKey());
    }

    private List<String> normalizedCandidates(List<String> input) {
        LinkedHashMap<String, Boolean> unique = new LinkedHashMap<>();
        for (String raw : input) {
            String normalized = normalizeLogoUrl(raw);
            if (!normalized.isEmpty() && isRemoteLogo(normalized)) {
                unique.put(normalized, Boolean.TRUE);
            }
        }
        return new ArrayList<>(unique.keySet());
    }

    private void requestLogo(Holder holder, List<String> candidates,
                             Map<String, String> headers, int index, String channelKey) {
        if (index >= candidates.size()) return;

        String url = candidates.get(index);
        String cacheKey = cacheKey(url, headers);
        if (isLogoTemporarilyFailed(cacheKey)) {
            requestLogo(holder, candidates, headers, index + 1, channelKey);
            return;
        }
        if (holder != null) holder.logo.setTag(url);

        Bitmap cached = LOGO_CACHE.get(cacheKey);
        if (cached != null) {
            if (holder != null) showLogo(holder, url, cached);
            return;
        }

        boolean startLoad = false;
        synchronized (WAITERS_LOCK) {
            if (holder != null) {
                List<LogoWaiter> waiters = LOGO_WAITERS.get(cacheKey);
                if (waiters == null) {
                    waiters = new ArrayList<>();
                    LOGO_WAITERS.put(cacheKey, waiters);
                }
                waiters.add(new LogoWaiter(holder, candidates, headers, index, url, channelKey));
            }
            if (!LOGO_LOADING.containsKey(cacheKey)) {
                LOGO_LOADING.put(cacheKey, Boolean.TRUE);
                startLoad = true;
            }
        }

        if (!startLoad) return;

        // Disk cache is probed off the UI thread. A hit avoids all network work.
        diskIo.execute(() -> {
            Bitmap disk = readCachedLogo(cacheKey);
            if (disk != null) {
                LOGO_CACHE.put(cacheKey, disk);
                finishLogoLoad(cacheKey, disk);
                return;
            }

            // Keep LOGO_LOADING=true while the network request is in flight so
            // subsequent binds join the same request instead of starting another.
            startLogoNetwork(cacheKey, url, candidates, headers, channelKey);
        });
    }

    private void startLogoNetwork(String cacheKey, String url, List<String> candidates,
                                  Map<String, String> headers, String channelKey) {
        final String finalCacheKey = cacheKey;
        // Do not inherit the stream's User-Agent. IPTV stream UAs such as
        // Dalvik/cvmedia are often rejected by CDN image hosts. Super OK resolves
        // the icon independently, so the first logo request uses a browser-like UA.
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .get()
                .header("Accept",
                        "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("User-Agent",
                        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36");

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null || value.isEmpty()
                    || "User-Agent".equalsIgnoreCase(key)
                    || "Cookie".equalsIgnoreCase(key)
                    || "Authorization".equalsIgnoreCase(key)) continue;
            if (SAFE_HEADER_KEY.matcher(key).matches()
                    && value.indexOf('\r') < 0 && value.indexOf('\n') < 0) {
                requestBuilder.header(key, value);
            }
        }

        LOGO_HTTP.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, java.io.IOException e) {
                markLogoFailed(finalCacheKey);
                finishLogoLoad(finalCacheKey, null);
            }

            @Override public void onResponse(Call call, Response response) {
                Bitmap bitmap = null;
                try (Response bodyResponse = response) {
                    if (response.isSuccessful() && response.body() != null) {
                        long length = response.body().contentLength();
                        if (length <= MAX_LOGO_BYTES) {
                            byte[] data = response.body().bytes();
                            if (data.length <= MAX_LOGO_BYTES) bitmap = decodeLogo(data);
                        }
                    }
                } catch (Exception ignored) {
                    bitmap = null;
                }
                if (bitmap != null) {
                    LOGO_CACHE.put(finalCacheKey, bitmap);
                    Bitmap ready = bitmap;
                    diskIo.execute(() -> writeCachedLogo(finalCacheKey, ready));
                }
                if (bitmap == null) markLogoFailed(finalCacheKey); else rememberResolved(channelKey, url);
                finishLogoLoad(finalCacheKey, bitmap);
            }
        });
    }

    private void finishLogoLoad(String cacheKey, Bitmap bitmap) {
        List<LogoWaiter> waiters;
        synchronized (WAITERS_LOCK) {
            waiters = LOGO_WAITERS.remove(cacheKey);
            LOGO_LOADING.remove(cacheKey);
        }
        if (waiters == null) return;

        final Bitmap ready = bitmap;
        for (LogoWaiter waiter : waiters) {
            waiter.holder.logo.post(() -> {
                if (!waiter.url.equals(waiter.holder.logo.getTag())) return;
                if (ready != null) {
                    showLogo(waiter.holder, waiter.url, ready);
                } else {
                    requestLogo(waiter.holder, waiter.candidates,
                            waiter.headers, waiter.index + 1, waiter.channelKey);
                }
            });
        }
    }

    private static boolean isLogoTemporarilyFailed(String key) {
        synchronized (WAITERS_LOCK) {
            Long until = LOGO_FAILED_UNTIL.get(key);
            if (until == null) return false;
            if (until > System.currentTimeMillis()) return true;
            LOGO_FAILED_UNTIL.remove(key);
            return false;
        }
    }

    private static void markLogoFailed(String key) {
        synchronized (WAITERS_LOCK) {
            LOGO_FAILED_UNTIL.put(key, System.currentTimeMillis() + FAILED_LOGO_TTL_MS);
        }
    }

    private static String resolvedLogo(String channelKey) {
        synchronized (WAITERS_LOCK) {
            return RESOLVED_LOGO_BY_CHANNEL.get(channelKey);
        }
    }

    private static void rememberResolved(String channelKey, String url) {
        if (channelKey == null || channelKey.isEmpty() || url == null || url.isEmpty()) return;
        synchronized (WAITERS_LOCK) {
            RESOLVED_LOGO_BY_CHANNEL.put(channelKey, url);
        }
    }

    private Bitmap decodeLogo(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int target = Math.max(256,
                Math.round(96 * context.getResources().getDisplayMetrics().density));
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= target
                || bounds.outHeight / (sample * 2) >= target) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private Bitmap readCachedLogo(String cacheKey) {
        File file = cacheFile(cacheKey);
        if (!file.isFile()) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) file.delete();
        return bitmap;
    }

    private void writeCachedLogo(String cacheKey, Bitmap bitmap) {
        File target = cacheFile(cacheKey);
        File temp = new File(target.getAbsolutePath() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 90, output)) return;
            if (!temp.renameTo(target)) {
                try (FileOutputStream copy = new FileOutputStream(target)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, copy);
                }
            }
        } catch (Exception ignored) {
            if (temp.exists()) temp.delete();
        }
    }

    private String normalizeLogoUrl(String value) {
        if (value == null) return "";
        String url = value.trim();
        if (url.isEmpty()) return "";

        // GitHub's browser-style raw URL is valid, but the direct raw form avoids
        // an extra redirect and is noticeably faster on repeated channel loads.
        url = url.replace("/raw.githubusercontent.com/", "/raw.githubusercontent.com/");
        url = url.replace("https://github.com/", "https://raw.githubusercontent.com/");
        url = url.replace("http://github.com/", "https://raw.githubusercontent.com/");
        int blob = url.indexOf("/blob/");
        if (blob > 0 && url.startsWith("https://raw.githubusercontent.com/")) {
            url = url.substring(0, blob) + "/" + url.substring(blob + 6);
        }

        // Normalize /refs/heads/<branch>/ into the direct raw path.
        int refs = url.indexOf("/refs/heads/");
        if (refs > 0 && url.startsWith("https://raw.githubusercontent.com/")) {
            String prefix = url.substring(0, refs);
            String rest = url.substring(refs + "/refs/heads/".length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                String branch = rest.substring(0, slash);
                String path = rest.substring(slash + 1);
                url = prefix + "/" + branch + "/" + path;
            }
        }
        return url;
    }

    private String headerOrDefault(Map<String, String> headers, String name, String fallback) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())
                    && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return entry.getValue();
            }
        }
        return fallback;
    }

    private String cacheKey(String url, Map<String, String> headers) {
        StringBuilder value = new StringBuilder(url);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if ("Referer".equalsIgnoreCase(entry.getKey())
                    || "Origin".equalsIgnoreCase(entry.getKey())) {
                value.append('\n').append(entry.getKey()).append(':').append(entry.getValue());
            }
        }
        return value.toString();
    }

    private void trimDiskCacheIfNeeded() {
        try {
            File[] files = logoCacheDir.listFiles();
            if (files == null || files.length < 200) return;
            long total = 0L;
            for (File f : files) if (f.isFile()) total += f.length();
            if (total <= MAX_DISK_CACHE_BYTES) return;
            java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
            long target = (MAX_DISK_CACHE_BYTES * 3L) / 4L;
            for (File f : files) {
                if (total <= target) break;
                long len = f.length();
                if (f.delete()) total -= len;
            }
        } catch (Exception ignored) { }
    }

    private File cacheFile(String value) {
        return new File(logoCacheDir, sha256(value) + ".img");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", b));
            }
            return result.toString();
        } catch (Exception impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static boolean isRemoteLogo(String url) {
        return url != null
                && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static void showLogo(Holder holder, String url, Bitmap bitmap) {
        if (!url.equals(holder.logo.getTag())) return;
        holder.logo.setImageBitmap(bitmap);
        holder.logo.setVisibility(View.VISIBLE);
        holder.badge.setVisibility(View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class LogoWaiter {
        final Holder holder;
        final List<String> candidates;
        final Map<String, String> headers;
        final int index;
        final String url;
        final String channelKey;

        LogoWaiter(Holder holder, List<String> candidates, Map<String, String> headers,
                   int index, String url, String channelKey) {
            this.holder = holder;
            this.candidates = candidates;
            this.headers = headers;
            this.index = index;
            this.url = url;
            this.channelKey = channelKey;
        }
    }

    private static final class Holder {
        final TextView name, group, url, badge, favorite, catchup, programme, epgStart, epgEnd;
        final ImageView logo;
        final View epg;
        final ProgressBar epgProgress;

        Holder(View v) {
            name = v.findViewById(R.id.txtName);
            group = v.findViewById(R.id.txtGroup);
            url = v.findViewById(R.id.txtUrl);
            badge = v.findViewById(R.id.txtChannelBadge);
            favorite = v.findViewById(R.id.btnFavorite);
            catchup = v.findViewById(R.id.btnCatchup);
            logo = v.findViewById(R.id.imgChannelLogo);
            epg = v.findViewById(R.id.epgSection);
            programme = v.findViewById(R.id.txtProgramme);
            epgStart = v.findViewById(R.id.txtEpgStart);
            epgEnd = v.findViewById(R.id.txtEpgEnd);
            epgProgress = v.findViewById(R.id.epgProgress);
        }
    }
}
