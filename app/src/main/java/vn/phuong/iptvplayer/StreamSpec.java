package vn.phuong.iptvplayer;

import androidx.media3.common.MimeTypes;

import java.net.URI;
import java.util.List;
import java.util.Locale;

// Resolves adaptive stream types before Media3 chooses a media source factory.
final class StreamSpec {
    private StreamSpec() { }

    static String inferMime(String url, List<String> options) {
        if (options != null) {
            for (String raw : options) {
                String option = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace(" ", "");
                if (option.contains("manifest_type=hls") || option.contains("manifest-type=hls"))
                    return MimeTypes.APPLICATION_M3U8;
                if (option.contains("manifest_type=mpd") || option.contains("manifest-type=mpd")
                        || option.contains("manifest_type=dash") || option.contains("manifest-type=dash"))
                    return MimeTypes.APPLICATION_MPD;
                if (option.contains("manifest_type=ism") || option.contains("manifest-type=ism")
                        || option.contains("manifest_type=smoothstreaming"))
                    return MimeTypes.APPLICATION_SS;
            }
        }
        String path = "";
        String query = "";
        try {
            URI uri = URI.create(url == null ? "" : url);
            path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            query = uri.getQuery() == null ? "" : uri.getQuery().toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) { }
        if (path.endsWith(".m3u8") || path.endsWith(".m3u") || query.contains("format=m3u8"))
            return MimeTypes.APPLICATION_M3U8;
        if (path.endsWith(".mpd") || query.contains("format=mpd") || query.contains("format=dash"))
            return MimeTypes.APPLICATION_MPD;
        if (path.contains(".ism/manifest") || path.contains(".isml/manifest"))
            return MimeTypes.APPLICATION_SS;
        return null;
    }
}
