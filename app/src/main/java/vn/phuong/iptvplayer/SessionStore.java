package vn.phuong.iptvplayer;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Device-only state. The manifest excludes it from Android backup. */
final class SessionStore {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final ExecutorService SAVE_IO = Executors.newSingleThreadExecutor();

    static List<Channel> snapshot(List<Channel> channels) {
        List<Channel> copy = new ArrayList<>(channels.size());
        for (Channel c : channels) {
            Channel n = new Channel(c.name(), c.group(), c.url(), c.tvgLogo(), c.logoUrl(), c.iconUrl(), c.tvgId(), c.headers());
            n.setSelected(c.selected());
            n.setOriginalExtInf(c.originalExtInf());
            n.options().addAll(c.options());
            copy.add(n);
        }
        return copy;
    }

    static void save(Context context, List<Channel> channels, String source, String epgUrl, int duplicates, int missing) {
        Context app = context.getApplicationContext();
        List<Channel> detached = channels == null ? new ArrayList<>() : channels;
        String sourceCopy = source == null ? "" : source;
        String epgCopy = epgUrl == null ? "" : epgUrl;
        SAVE_IO.execute(() -> {
            try {
                write(app, detached, sourceCopy, epgCopy, duplicates, missing);
            } catch (Exception ignored) { }
        });
    }

    private static void write(Context context, List<Channel> channels, String source, String epgUrl, int duplicates, int missing) throws Exception {
        String exportedM3u = new M3uParser().exportAll(channels);
        AtomicFile m3uAtomic = m3uFile(context);
        FileOutputStream m3uOut = null;
        try {
            m3uOut = m3uAtomic.startWrite();
            m3uOut.write(exportedM3u.getBytes(StandardCharsets.UTF_8));
            m3uAtomic.finishWrite(m3uOut);
        } catch (Exception error) {
            if (m3uOut != null) m3uAtomic.failWrite(m3uOut);
            throw error;
        }

        JSONObject obj = new JSONObject();
        obj.put("source", source);
        obj.put("epg_url", epgUrl);
        obj.put("duplicates", duplicates);
        obj.put("missing", missing);
        JSONArray unchecked = new JSONArray();
        for (Channel channel : channels) if (!channel.selected()) unchecked.put(channel.identityKey());
        obj.put("unchecked", unchecked);
        AtomicFile file = file(context);
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(obj.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception error) {
            if (out != null) file.failWrite(out);
            throw error;
        }
    }

    static State load(Context context) throws Exception {
        AtomicFile file = file(context);
        if (!file.getBaseFile().exists()) return null;
        JSONObject obj = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
        String source = obj.optString("source", "");
        String sourceUrl = PlaylistSourceStore.sourceUrl(source);
        String playlistText;
        AtomicFile m3uAtomic = m3uFile(context);
        if (m3uAtomic.getBaseFile().exists()) {
            playlistText = new String(m3uAtomic.readFully(), StandardCharsets.UTF_8);
        } else {
            playlistText = obj.optString("playlist", "");
        }
        if (playlistText.isEmpty()) return null;
        M3uParser.Result result = new M3uParser().parse(playlistText, sourceUrl);
        Set<String> unchecked = new HashSet<>();
        JSONArray arr = obj.optJSONArray("unchecked");
        if (arr != null) for (int i = 0; i < arr.length(); i++) unchecked.add(arr.getString(i));
        if (!unchecked.isEmpty()) {
            for (Channel channel : result.channels) channel.setSelected(!unchecked.contains(channel.identityKey()));
        }
        return new State(new M3uParser.Result(result.channels, obj.optInt("duplicates"), obj.optInt("missing"), obj.optString("epg_url", "")),
                source);
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "playlist-session.json"));
    }

    private static AtomicFile m3uFile(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "playlist-session.m3u"));
    }

    static final class State {
        final M3uParser.Result result;
        final String source;
        State(M3uParser.Result result, String source) { this.result = result; this.source = source; }
    }
}
