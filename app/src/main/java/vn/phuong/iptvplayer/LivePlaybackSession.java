package vn.phuong.iptvplayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-process bridge for the same logical Live match while changing playback servers. */
final class LivePlaybackSession {
    private static volatile LiveEvent activeEvent;

    private LivePlaybackSession() { }

    static void setActive(LiveEvent event) { activeEvent = event; }

    static LiveEvent activeEvent() { return activeEvent; }

    static LiveEvent activeFor(Channel channel) {
        LiveEvent current = activeEvent;
        return channel != null && current != null && current.hasSourceUrl(channel.url())
                ? current.withPreferredUrl(channel.url()) : null;
    }

    static void updateSelectedUrl(String url) {
        LiveEvent current = activeEvent;
        if (current != null && current.hasSourceUrl(url)) activeEvent = current.withPreferredUrl(url);
    }

    static void clearIfNotLiveSource(Channel channel) {
        LiveEvent current = activeEvent;
        if (current != null && (channel == null || !current.hasSourceUrl(channel.url()))) {
            activeEvent = null;
        }
    }

    static String encode(LiveEvent event) {
        if (event == null) return "";
        try {
            JSONObject root = new JSONObject();
            root.put("id", event.id());
            root.put("title", event.title());
            root.put("group", event.group());
            root.put("sport", event.sport());
            root.put("kickoff", event.kickoffMillis());
            root.put("selectedUrl", event.primary().url());
            JSONArray sources = new JSONArray();
            for (int i = 0; i < event.sources().size(); i++) {
                Channel channel = event.sources().get(i);
                JSONObject item = new JSONObject();
                item.put("name", channel.name());
                item.put("group", channel.group());
                item.put("url", channel.url());
                item.put("tvgLogo", channel.tvgLogo());
                item.put("logoUrl", channel.logoUrl());
                item.put("iconUrl", channel.iconUrl());
                item.put("tvgId", channel.tvgId());
                JSONObject headers = new JSONObject();
                for (Map.Entry<String, String> header : channel.headers().entrySet()) {
                    headers.put(header.getKey(), header.getValue());
                }
                item.put("headers", headers);
                JSONArray options = new JSONArray();
                for (String option : channel.options()) options.put(option);
                item.put("options", options);
                item.put("label", event.sourceLabel(i));
                sources.put(item);
            }
            root.put("sources", sources);
            return root.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    static LiveEvent decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(encoded);
            JSONArray items = root.optJSONArray("sources");
            if (items == null || items.length() == 0) return null;
            List<Channel> sources = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", "");
                if (!M3uParser.isNetworkUrl(url)) continue;
                Map<String, String> headers = new LinkedHashMap<>();
                JSONObject rawHeaders = item.optJSONObject("headers");
                if (rawHeaders != null) {
                    Iterator<String> keys = rawHeaders.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = rawHeaders.optString(key, "");
                        if (!key.isEmpty() && value.indexOf('\r') < 0 && value.indexOf('\n') < 0) headers.put(key, value);
                    }
                }
                Channel channel = new Channel(
                        item.optString("name", root.optString("title", "Sự kiện Live")),
                        item.optString("group", root.optString("group", "Live")),
                        url,
                        item.optString("tvgLogo", ""),
                        item.optString("logoUrl", ""),
                        item.optString("iconUrl", ""),
                        item.optString("tvgId", ""),
                        headers);
                JSONArray options = item.optJSONArray("options");
                if (options != null) for (int j = 0; j < options.length(); j++) {
                    String option = options.optString(j, "");
                    if (!option.isEmpty()) channel.options().add(option);
                }
                sources.add(channel);
                labels.add(item.optString("label", "Server " + (i + 1)));
            }
            return LiveEvent.restored(
                    root.optString("id", ""),
                    root.optString("title", "Sự kiện Live"),
                    root.optString("group", "Thể thao"),
                    sources, labels, root.optLong("kickoff", 0L),
                    root.optString("sport", "Thể thao"),
                    root.optString("selectedUrl", ""));
        } catch (Exception ignored) {
            return null;
        }
    }
}
