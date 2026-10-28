package vn.phuong.iptvplayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Super OK-compatible Catchup / Timeshift URL builder.
 * Resolves standard IPTV catchup-source templates and fallback formats.
 */
public final class CatchupUrlBuilder {
    private static final TimeZone VN_TIME_ZONE = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
    private static final Pattern GENERIC_VARS = Pattern.compile("(?i)[$]?\\{([a-zA-Z_]+)\\}");

    private CatchupUrlBuilder() {}

    public static String build(Channel channel, EpgStore.Programme programme) {
        if (channel == null || programme == null) return channel != null ? channel.url() : "";
        return build(channel, programme.startMs, programme.endMs);
    }

    public static String build(Channel channel, long startMs, long endMs) {
        if (channel == null) return "";
        String baseStreamUrl = channel.url().trim();
        String catchupSource = channel.catchupSource().trim();
        String catchupType = channel.catchupType().trim().toLowerCase(Locale.ROOT);

        String template;
        if (!catchupSource.isEmpty()) {
            if (catchupSource.startsWith("http://") || catchupSource.startsWith("https://")) {
                template = catchupSource;
            } else if (catchupSource.startsWith("/")) {
                try {
                    java.net.URI uri = java.net.URI.create(baseStreamUrl);
                    String hostPort = uri.getPort() > 0 ? (uri.getHost() + ":" + uri.getPort()) : uri.getHost();
                    template = uri.getScheme() + "://" + hostPort + catchupSource;
                } catch (Exception e) {
                    template = baseStreamUrl + catchupSource;
                }
            } else if (catchupSource.startsWith("?")) {
                template = baseStreamUrl + catchupSource;
            } else {
                template = catchupSource;
            }
        } else {
            template = buildDefaultTemplate(baseStreamUrl, catchupType);
        }

        return replaceVariables(template, startMs, endMs);
    }

    private static String buildDefaultTemplate(String url, String type) {
        if ("flussonic".equals(type) || "fs".equals(type)) {
            // Flussonic standard format: replace /index.m3u8 or /mono.m3u8 with /timeshift_abs-${start}.m3u8
            String cleaned = url.replaceAll("(?i)/(index|mono|tracks-[^/]+)\\.m3u8.*", "");
            if (cleaned.endsWith(".m3u8")) {
                cleaned = cleaned.substring(0, cleaned.length() - 5);
            }
            return cleaned + "/timeshift_abs-${start}.m3u8";
        }
        if ("shift".equals(type)) {
            char sep = url.contains("?") ? '&' : '?';
            return url + sep + "timeshift=${start}&duration=${duration}";
        }
        if ("xc".equals(type)) {
            // Xtream Codes timeshift template if url has standard stream pattern: .../live/user/pass/id.ts
            if (url.contains("/live/")) {
                return url.replace("/live/", "/streaming/timeshift.php?stream=") + "&start=${YYYYMMDDHHMM}&duration=${duration}";
            }
        }
        // Default / append: standard in Vietnam IPTV (VTVGo, TV360, MyTV, FPT)
        char sep = url.contains("?") ? '&' : '?';
        return url + sep + "utc=${start}&lutc=${lutc}";
    }

    public static String replaceVariables(String template, long startMs, long endMs) {
        if (template == null || template.isEmpty()) return "";

        long startSec = startMs / 1000L;
        long endSec = endMs > 0 ? (endMs / 1000L) : (startSec + 3600L);
        long durationSec = Math.max(0L, endSec - startSec);
        long nowSec = System.currentTimeMillis() / 1000L;
        long offsetSec = Math.max(0L, nowSec - startSec);

        String result = template;

        // Core epoch seconds
        result = replaceAll(result, "(?i)\\$\\{start\\}|\\{start\\}|__START__", String.valueOf(startSec));
        result = replaceAll(result, "(?i)\\$\\{stop\\}|\\{stop\\}|\\$\\{end\\}|\\{end\\}|__STOP__|__END__", String.valueOf(endSec));
        result = replaceAll(result, "(?i)\\$\\{duration\\}|\\{duration\\}|__DURATION__", String.valueOf(durationSec));
        result = replaceAll(result, "(?i)\\$\\{lutc\\}|\\{lutc\\}|\\$\\{utc\\}|\\{utc\\}|__LUTC__", String.valueOf(nowSec));
        result = replaceAll(result, "(?i)\\$\\{offset\\}|\\{offset\\}", String.valueOf(offsetSec));

        // ISO 8601 formatting
        String startIso = formatIso8601(startMs);
        String stopIso = formatIso8601(endMs > 0 ? endMs : (startMs + 3600_000L));
        result = replaceAll(result, "(?i)\\$\\{startiso\\}|\\{startiso\\}", startIso);
        result = replaceAll(result, "(?i)\\$\\{stopiso\\}|\\{stopiso\\}|\\$\\{endiso\\}|\\{endiso\\}", stopIso);

        // Date-time pattern formats (Super OK format support)
        Date startDate = new Date(startMs);
        Date endDate = new Date(endMs > 0 ? endMs : (startMs + 3600_000L));

        result = replaceAll(result, "(?i)\\$\\{YYYYMMDD\\}|\\{YYYYMMDD\\}", formatDate("yyyyMMdd", startDate));
        result = replaceAll(result, "(?i)\\$\\{YYYYMMDDHH\\}|\\{YYYYMMDDHH\\}", formatDate("yyyyMMddHH", startDate));
        result = replaceAll(result, "(?i)\\$\\{YYYYMMDDHHMM\\}|\\{YYYYMMDDHHMM\\}", formatDate("yyyyMMddHHmm", startDate));
        result = replaceAll(result, "(?i)\\$\\{YYYYMMDDHHMMSS\\}|\\{YYYYMMDDHHMMSS\\}", formatDate("yyyyMMddHHmmss", startDate));
        result = replaceAll(result, "(?i)\\$\\{STOPYYYYMMDD\\}|\\{STOPYYYYMMDD\\}|\\$\\{ENDYYYYMMDD\\}|\\{ENDYYYYMMDD\\}", formatDate("yyyyMMdd", endDate));
        result = replaceAll(result, "(?i)\\$\\{start\\s*uhr\\}|\\{start\\s*uhr\\}", formatDate("HH:mm", startDate));
        result = replaceAll(result, "(?i)\\$\\{stop\\s*uhr\\}|\\{stop\\s*uhr\\}|\\$\\{end\\s*uhr\\}|\\{end\\s*uhr\\}", formatDate("HH:mm", endDate));

        // Handle generic fallback vars like ${begin}, ${from}, ${to}, ${until}, ${dur}, ${now}
        result = replaceGenericVars(result, startSec, endSec, durationSec, nowSec);

        return result;
    }

    private static String replaceGenericVars(String input, long startSec, long endSec, long durationSec, long nowSec) {
        if (!input.contains("{")) return input;
        Matcher m = GENERIC_VARS.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1).toLowerCase(Locale.ROOT);
            String rep = null;
            if ("start".equals(name) || "begin".equals(name) || "from".equals(name)) rep = String.valueOf(startSec);
            else if ("stop".equals(name) || "end".equals(name) || "to".equals(name) || "until".equals(name)) rep = String.valueOf(endSec);
            else if ("dur".equals(name) || "duration".equals(name) || "length".equals(name)) rep = String.valueOf(durationSec);
            else if ("lutc".equals(name) || "utc".equals(name) || "now".equals(name) || "current".equals(name)) rep = String.valueOf(nowSec);

            if (rep != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String replaceAll(String source, String regex, String replacement) {
        if (source == null || source.isEmpty()) return "";
        try {
            return source.replaceAll(regex, Matcher.quoteReplacement(replacement));
        } catch (Exception e) {
            return source;
        }
    }

    private static String formatDate(String pattern, Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ROOT);
        sdf.setTimeZone(VN_TIME_ZONE);
        return sdf.format(date);
    }

    private static String formatIso8601(long epochMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZ", Locale.ROOT);
        sdf.setTimeZone(VN_TIME_ZONE);
        return sdf.format(new Date(epochMs));
    }
}
