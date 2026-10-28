package vn.phuong.iptvplayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds archive URLs from M3U EXTINF metadata without mutating the live URL. */
final class CatchupUrlBuilder {
    private static final Pattern ATTR = Pattern.compile("([\\\\w-]+)\\\\s*=\\\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\\\s,]+))");
    private static final TimeZone VN = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

    static final class Config {
        final String mode;
        final String source;
        final int days;
        Config(String mode, String source, int days) {
            this.mode = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
            this.source = source == null ? "" : source.trim();
            this.days = Math.max(0, days);
        }
        boolean supported() { return !source.isEmpty() && !mode.equals("disabled") && !mode.equals("none"); }
    }

    private CatchupUrlBuilder() {}

    static Config fromChannel(Channel channel) {
        return parseExtInf(channel == null ? "" : channel.originalExtInf());
    }

    static Config parseExtInf(String extinf) {
        Map<String,String> attrs = new LinkedHashMap<>();
        Matcher m = ATTR.matcher(extinf == null ? "" : extinf);
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
            attrs.put(m.group(1).toLowerCase(Locale.ROOT), value == null ? "" : value);
        }
        String mode = attrs.getOrDefault("catchup", "");
        String source = attrs.getOrDefault("catchup-source", "");
        if (source.isEmpty()) source = attrs.getOrDefault("catchup-url", "");
        int days = 0;
        try { days = Integer.parseInt(attrs.getOrDefault("catchup-days", "0").trim()); }
        catch (RuntimeException ignored) {}
        return new Config(mode, source, days);
    }

    static boolean canReplay(Channel channel, EpgProgram programme, long nowMs) {
        if (channel == null || programme == null || !programme.isValid() || !programme.isPast(nowMs)) return false;
        Config config = fromChannel(channel);
        if (config.source.isEmpty() || config.mode.equals("none") || config.mode.equals("disabled")) return false;
        if (config.days > 0 && nowMs - programme.startMs > config.days * 86400000L) return false;
        return true;
    }

    static String build(Channel channel, EpgProgram programme, long nowMs) {
        if (!canReplay(channel, programme, nowMs)) throw new IllegalArgumentException("Kênh không hỗ trợ xem lại chương trình này.");
        Config config = fromChannel(channel);
        String template = config.source;
        long start = programme.startMs / 1000L;
        long end = programme.endMs / 1000L;
        long duration = Math.max(0L, end - start);
        String isoStart = format(programme.startMs, "yyyy-MM-dd'T'HH:mm:ssXXX", TimeZone.getTimeZone("UTC"));
        String isoEnd = format(programme.endMs, "yyyy-MM-dd'T'HH:mm:ssXXX", TimeZone.getTimeZone("UTC"));
        String localStart = format(programme.startMs, "yyyyMMddHHmmss", VN);
        String localEnd = format(programme.endMs, "yyyyMMddHHmmss", VN);
        Map<String,String> replacements = new LinkedHashMap<>();
        replacements.put("{start}", Long.toString(start));
        replacements.put("{utc}", Long.toString(start));
        replacements.put("{end}", Long.toString(end));
        replacements.put("{stop}", Long.toString(end));
        replacements.put("{lutc}", Long.toString(end));
        replacements.put("{duration}", Long.toString(duration));
        replacements.put("{startTime}", Long.toString(start));
        replacements.put("{endTime}", Long.toString(end));
        replacements.put("${start}", Long.toString(start));
        replacements.put("${end}", Long.toString(end));
        replacements.put("${duration}", Long.toString(duration));
        replacements.put("${YYYYMMDDHHMMSS}", localStart);
        replacements.put("${YYYYMMDDHHMMSS}", localStart);
        replacements.put("${UTC}", Long.toString(start));
        replacements.put("${LUTC}", Long.toString(end));
        replacements.put("${START}", Long.toString(start));
        replacements.put("${END}", Long.toString(end));
        replacements.put("${DURATION}", Long.toString(duration));
        replacements.put("${ISO}", isoStart);
        replacements.put("${STARTISO}", isoStart);
        replacements.put("${ENDISO}", isoEnd);
        replacements.put("${START_LOCAL}", localStart);
        replacements.put("${END_LOCAL}", localEnd);
        for (Map.Entry<String,String> entry : replacements.entrySet()) {
            template = template.replace(entry.getKey(), entry.getValue());
        }
        // Common Xtream-style templates may use {start} and {duration}; append mode
        // appends a query template to the live URL, while default uses a full URL.
        if (config.mode.equals("append")) {
            String base = channel.url();
            String query = template.startsWith("?") || template.startsWith("&") ? template.substring(1) : template;
            if (query.isEmpty()) throw new IllegalArgumentException("Mẫu Catch-up rỗng.");
            return base + (base.contains("?") ? "&" : "?") + query;
        }
        if (!template.startsWith("http://") && !template.startsWith("https://")) {
            throw new IllegalArgumentException("Mẫu Catch-up không tạo ra URL HTTP/HTTPS hợp lệ.");
        }
        return template;
    }

    private static String format(long timeMs, String pattern, TimeZone zone) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(zone);
        return f.format(new Date(timeMs));
    }
}
