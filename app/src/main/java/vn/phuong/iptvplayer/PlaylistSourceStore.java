package vn.phuong.iptvplayer;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Playlist URLs. A trusted default is available on a clean install; user sources stay device-local. */
final class PlaylistSourceStore {
    static final String DEFAULT_URL = "https://phuongnm7-playlist.phuongnm7-iptv.workers.dev/";
    static final String DEFAULT_NAME = "NM7 IPTV";
    private static final String LEGACY_DEFAULT_URL = "https://iptv-live-merge.phuongnm7-iptv.workers.dev/playlist.m3u";
    private static final String FILE = "playlist-sources";
    private static final String KEY = "sources";
    private static final int MAX_SOURCES = 50;

    static final class Source {
        final String name, url;
        Source(String name, String url) { this.name = name; this.url = url; }
    }

    static List<Source> load(Context context) {
        List<Source> result = new ArrayList<>();
        android.content.SharedPreferences preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        try {
            JSONArray values = new JSONArray(preferences.getString(KEY, "[]"));
            for (int i = 0; i < values.length() && result.size() < MAX_SOURCES; i++) {
                JSONObject item = values.getJSONObject(i);
                String url = item.optString("url", "").trim();
                if (isValid(url) && !isDefault(url)) result.add(new Source(cleanName(item.optString("name"), url), url));
            }
        } catch (Exception ignored) { }
        return result;
    }

    static void add(Context context, String name, String url) throws Exception {
        String cleanUrl = url == null ? "" : url.trim();
        if (!isValid(cleanUrl)) throw new IllegalArgumentException("Link playlist phải bắt đầu bằng http:// hoặc https://");
        if (isDefault(cleanUrl)) return;
        List<Source> values = new ArrayList<>(load(context));
        String requestedName = name == null ? "" : name.trim();
        for (int i = values.size() - 1; i >= 0; i--) if (values.get(i).url.equals(cleanUrl)) {
            if (requestedName.isEmpty()) requestedName = values.get(i).name;
            values.remove(i);
        }
        values.add(0, new Source(cleanName(requestedName, cleanUrl), cleanUrl));
        if (values.size() > MAX_SOURCES) values = new ArrayList<>(values.subList(0, MAX_SOURCES));
        save(context, values);
    }

    static void remove(Context context, int index) throws Exception {
        List<Source> values = new ArrayList<>(load(context));
        if (index >= 0 && index < values.size()) { values.remove(index); save(context, values); }
    }

    static void update(Context context, int index, String name, String url) throws Exception {
        String cleanUrl = url == null ? "" : url.trim();
        if (!isValid(cleanUrl)) throw new IllegalArgumentException("Link playlist phải bắt đầu bằng http:// hoặc https://");
        if (isDefault(cleanUrl)) throw new IllegalArgumentException("Nguồn mặc định đã có sẵn trong ứng dụng");
        List<Source> values = new ArrayList<>(load(context));
        if (index < 0 || index >= values.size()) throw new IllegalArgumentException("Nguồn IPTV không còn tồn tại");
        values.remove(index);
        for (int i = values.size() - 1; i >= 0; i--) if (values.get(i).url.equals(cleanUrl)) values.remove(i);
        values.add(Math.min(index, values.size()), new Source(cleanName(name, cleanUrl), cleanUrl));
        save(context, values);
    }

    private static void save(Context context, List<Source> values) throws Exception {
        JSONArray array = new JSONArray();
        for (Source value : values) array.put(new JSONObject().put("name", value.name).put("url", value.url));
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply();
    }

    static boolean isValid(String url) { return url != null && (url.startsWith("https://") || url.startsWith("http://")); }
    static boolean isDefault(String url) { if(url==null)return false;String clean=url.trim();return DEFAULT_URL.equals(clean)||LEGACY_DEFAULT_URL.equals(clean); }
    static boolean isLegacyDefault(String url) { return url!=null&&LEGACY_DEFAULT_URL.equals(url.trim()); }

    static boolean shouldRefreshOnStartup(String sourceDescription) {
        return isValid(sourceUrl(sourceDescription));
    }

    static String sourceUrl(String sourceDescription) {
        if (sourceDescription == null) return "";
        return sourceDescription.split("\n", 2)[0].trim();
    }

    static boolean shouldReturnToDefault(String currentSource, List<Source> remainingSources) {
        String activeUrl = sourceUrl(currentSource);
        if (!isValid(activeUrl) || isDefault(activeUrl)) return false;
        if (remainingSources != null) {
            for (Source source : remainingSources) {
                if (source != null && activeUrl.equals(source.url.trim())) return false;
            }
        }
        return true;
    }
    private static String cleanName(String name, String url) {
        String clean = name == null ? "" : name.trim();
        if (!clean.isEmpty()) return clean.length() > 80 ? clean.substring(0, 80) : clean;
        String host = Uri.parse(url).getHost();
        return host == null || host.isEmpty() ? "Nguồn IPTV" : host;
    }
}
