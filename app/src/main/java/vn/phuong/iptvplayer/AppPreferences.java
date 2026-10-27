package vn.phuong.iptvplayer;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Private, device-only UI preferences. Channel identifiers are hashed before storage. */
final class AppPreferences {
    private static final String FILE = "ui-preferences";
    private static final String FAVORITES = "favorites";
    private static final String RECENT = "recent";
    private static final int MAX_RECENT = 30;
    private static final String BACKGROUND_PLAYBACK = "background_playback";
    private static final String BACKGROUND_PLAYBACK_DEFAULT_MIGRATED = "background_playback_default_migrated_11026";

    private static SharedPreferences cachedPrefsRef;
    private static Set<String> cachedFavorites;
    private static List<String> cachedRecentList;
    private static Map<String, Integer> cachedRecentRanks;

    private static SharedPreferences prefs(Context context) {
        SharedPreferences p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        synchronized (AppPreferences.class) {
            if (p != cachedPrefsRef) {
                cachedPrefsRef = p;
                cachedFavorites = null;
                cachedRecentList = null;
                cachedRecentRanks = null;
            }
        }
        return p;
    }

    static String id(Channel channel) {
        return channel.sha256Id();
    }

    private static synchronized Set<String> favoritesSet(Context context) {
        SharedPreferences p = prefs(context);
        if (cachedFavorites == null) {
            cachedFavorites = new LinkedHashSet<>(p.getStringSet(FAVORITES, Collections.emptySet()));
        }
        return cachedFavorites;
    }

    private static synchronized void rebuildRecentRankMap() {
        Map<String, Integer> ranks = new HashMap<>(cachedRecentList.size() * 2);
        for (int i = 0; i < cachedRecentList.size(); i++) {
            ranks.put(cachedRecentList.get(i), i);
        }
        cachedRecentRanks = ranks;
    }

    private static synchronized List<String> recentIds(Context context) {
        SharedPreferences p = prefs(context);
        if (cachedRecentList == null) {
            String value = p.getString(RECENT, "");
            if (value == null || value.isEmpty()) {
                cachedRecentList = new ArrayList<>();
            } else {
                cachedRecentList = new ArrayList<>(Arrays.asList(value.split(",")));
            }
            rebuildRecentRankMap();
        }
        return cachedRecentList;
    }

    private static synchronized Map<String, Integer> recentRanks(Context context) {
        recentIds(context);
        return cachedRecentRanks;
    }

    static synchronized boolean isFavorite(Context context, Channel channel) {
        return favoritesSet(context).contains(id(channel));
    }

    static synchronized boolean toggleFavorite(Context context, Channel channel) {
        SharedPreferences p = prefs(context);
        Set<String> values = new LinkedHashSet<>(favoritesSet(context));
        String id = id(channel);
        boolean added;
        if (values.contains(id)) {
            values.remove(id);
            added = false;
        } else {
            values.add(id);
            added = true;
        }
        cachedFavorites = values;
        p.edit().putStringSet(FAVORITES, new LinkedHashSet<>(values)).apply();
        return added;
    }

    static synchronized void recordRecent(Context context, Channel channel) {
        SharedPreferences p = prefs(context);
        List<String> values = new ArrayList<>(recentIds(context));
        String id = id(channel);
        values.remove(id);
        values.add(0, id);
        if (values.size() > MAX_RECENT) values = new ArrayList<>(values.subList(0, MAX_RECENT));
        cachedRecentList = values;
        rebuildRecentRankMap();
        p.edit().putString(RECENT, android.text.TextUtils.join(",", values)).apply();
    }

    static synchronized boolean isRecent(Context context, Channel channel) {
        return recentRanks(context).containsKey(id(channel));
    }

    static synchronized int recentRank(Context context, Channel channel) {
        Integer index = recentRanks(context).get(id(channel));
        return index == null ? Integer.MAX_VALUE : index;
    }

    static synchronized void clearRecent(Context context) {
        SharedPreferences p = prefs(context);
        cachedRecentList = new ArrayList<>();
        cachedRecentRanks = Collections.emptyMap();
        p.edit().remove(RECENT).apply();
    }

    static boolean showUrls(Context context) { return prefs(context).getBoolean("show_urls", false); }
    static void setShowUrls(Context context, boolean value) { prefs(context).edit().putBoolean("show_urls", value).apply(); }
    static boolean compactRows(Context context) { return prefs(context).getBoolean("compact_rows", false); }
    static void setCompactRows(Context context, boolean value) { prefs(context).edit().putBoolean("compact_rows", value).apply(); }
    static boolean showFps(Context context) { return prefs(context).getBoolean("show_fps", true); }
    static void setShowFps(Context context, boolean value) { prefs(context).edit().putBoolean("show_fps", value).apply(); }
    static boolean showClock(Context context) { return prefs(context).getBoolean("show_clock", true); }
    static void setShowClock(Context context, boolean value) { prefs(context).edit().putBoolean("show_clock", value).apply(); }
    static boolean showPlayerSource(Context context) { return prefs(context).getBoolean("show_player_source", false); }
    static void setShowPlayerSource(Context context, boolean value) {
        prefs(context).edit().putBoolean("show_player_source", value).commit();
    }
    static boolean backgroundPlayback(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(BACKGROUND_PLAYBACK_DEFAULT_MIGRATED, false)) {
            p.edit().putBoolean(BACKGROUND_PLAYBACK, true)
                    .putBoolean(BACKGROUND_PLAYBACK_DEFAULT_MIGRATED, true).apply();
        }
        return p.getBoolean(BACKGROUND_PLAYBACK, true);
    }
    static void setBackgroundPlayback(Context context, boolean value) {
        prefs(context).edit().putBoolean(BACKGROUND_PLAYBACK, value).apply();
    }
    static String interfaceMode(Context context) { return prefs(context).getString("interface_mode", "auto"); }
    static void setInterfaceMode(Context context, String value) { prefs(context).edit().putString("interface_mode", value).apply(); }

    /** Hardware/OS level TV detection. Physical TVs always use the TV-safe path. */
    static boolean isPhysicalTv(Context context) {
        UiModeManager manager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (manager != null && manager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return true;
        PackageManager pm = context.getPackageManager();
        return pm != null && (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature("android.software.leanback_only"));
    }

    static boolean isTvInterface(Context context) {
        if (isPhysicalTv(context)) return true;
        String value = interfaceMode(context);
        if ("tv".equals(value)) return true;
        if ("mobile".equals(value)) return false;
        UiModeManager manager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return manager != null && manager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }
}
