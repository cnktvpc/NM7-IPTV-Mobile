package vn.phuong.iptvplayer;

import android.content.Context;
import android.content.SharedPreferences;

/** Device-local LIVE event favorites and last-selected server. No credentials or URLs are uploaded. */
final class LiveEventStore {
    private static final String PREFS = "nm7_live_events_v1";
    private LiveEventStore() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String favoriteKey(String eventId) { return "favorite:" + eventId; }
    private static String serverKey(String eventId) { return "server:" + eventId; }

    static boolean isFavorite(Context context, String eventId) {
        return prefs(context).getBoolean(favoriteKey(eventId), false);
    }

    static boolean toggleFavorite(Context context, String eventId) {
        SharedPreferences preferences = prefs(context);
        boolean next = !preferences.getBoolean(favoriteKey(eventId), false);
        preferences.edit().putBoolean(favoriteKey(eventId), next).apply();
        return next;
    }

    static String selectedUrl(Context context, String eventId) {
        return prefs(context).getString(serverKey(eventId), "");
    }

    static void setSelectedUrl(Context context, String eventId, String url) {
        if (url == null || url.trim().isEmpty()) return;
        prefs(context).edit().putString(serverKey(eventId), url.trim()).apply();
    }
}
