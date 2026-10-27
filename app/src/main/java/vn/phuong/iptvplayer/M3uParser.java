package vn.phuong.iptvplayer;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class M3uParser {
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))");
    private static final Pattern EXT_X_LINE = Pattern.compile("(?m)^\\s*#EXT-X-");
    private static final Pattern LINE_SPLIT = Pattern.compile("\\r\\n|\\n|\\r");
    private static final Pattern ALT_URL_SPLIT = Pattern.compile("[;|]");
    private static final Pattern SUPPORTED_SCHEME = Pattern.compile("(?i)https?|rtsp|rtsps|udp|rtmp|rtmps|rtp|srt");
    private static final Pattern VALID_HEADER_KEY = Pattern.compile("[!#$%&'*+.^_\\x60|~0-9A-Za-z-]+");

    public Result parse(String content, String baseUrl) {
        if (content == null) content = "";
        content = content.replace("\uFEFF", "");
        if (content.trim().startsWith("<") || content.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Nội dung không phải playlist M3U (có thể là trang đăng nhập).");
        }
        if (EXT_X_LINE.matcher(content).find()) {
            if (baseUrl == null || !(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
                throw new IllegalArgumentException("Đây là manifest HLS. Hãy nhập URL gốc của luồng để phát.");
            }
            Channel hls = new Channel("Luồng HLS", "Phát trực tiếp", baseUrl, "", "", "", "", Collections.emptyMap());
            hls.options().add("#KODIPROP:inputstream.adaptive.manifest_type=hls");
            return new Result(Collections.singletonList(hls), 0, 0);
        }
        String[] lines = LINE_SPLIT.split(content);
        List<Channel> channels = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int duplicates = 0;
        int missingUrls = 0;
        String epgUrl = "";

        Metadata pending = null;
        Map<String, String> pendingHeaders = new LinkedHashMap<>();
        List<String> pendingOptions = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.regionMatches(true, 0, "#EXTM3U", 0, 7)) { epgUrl = resolveUrl(parseEpgUrl(line), baseUrl); continue; }

            if (line.regionMatches(true, 0, "#EXTINF:", 0, 8)) {
                if (pending != null) missingUrls++;
                pending = parseMetadata(line);
                pendingHeaders.clear();
                pendingOptions.clear();
                continue;
            }
            if (line.regionMatches(true, 0, "#EXTGRP:", 0, 8) && pending != null) {
                pending.group = line.substring(8).trim();
                continue;
            }
            if (line.regionMatches(true, 0, "#EXTVLCOPT:", 0, 11)) {
                if (!parseHeaderOption(line.substring(11), pendingHeaders) && pending != null) {
                    pendingOptions.add(line);
                }
                continue;
            }
            if (line.startsWith("#")) {
                if (pending != null) pendingOptions.add(line);
                continue;
            }

            UrlAndHeaders parsedUrl = splitUrlAndHeaders(line);
            parsedUrl = new UrlAndHeaders(resolveUrl(parsedUrl.url, baseUrl), parsedUrl.headers);
            pendingHeaders.putAll(parsedUrl.headers);
            Metadata metadata = pending == null ? new Metadata() : pending;
            String name = metadata.name;
            if (name.isEmpty()) name = fallbackName(parsedUrl.url);

            if (parsedUrl.url.isEmpty()) {
                missingUrls++;
            } else {
                // Do not interpret random text as a channel URL.
                if (!isNetworkUrl(parsedUrl.url)) {
                    missingUrls++;
                } else {
                    String tvgLogo = resolveUrl(metadata.tvgLogo, baseUrl);
                    String logoUrl = resolveUrl(metadata.logoUrl, baseUrl);
                    String iconUrl = resolveUrl(metadata.iconUrl, baseUrl);
                    Channel channel = new Channel(name, metadata.group, parsedUrl.url,
                            tvgLogo, logoUrl, iconUrl,
                            metadata.tvgId, pendingHeaders);
                    channel.setOriginalExtInf(metadata.original);
                    channel.options().addAll(pendingOptions);
                    for (String alternateValue : metadata.alternateUrls) {
                        for (String rawCandidate : ALT_URL_SPLIT.split(alternateValue)) {
                            String candidate = resolveUrl(rawCandidate.trim(), baseUrl);
                            String alternateOption = "#NM7-ALT-URL:" + candidate;
                            if (isNetworkUrl(candidate) && !candidate.equals(channel.url())
                                    && !channel.options().contains(alternateOption)) {
                                channel.options().add(alternateOption);
                            }
                        }
                    }
                    if (!seen.add(channel.identityKey())) duplicates++;
                    else channels.add(channel);
                }
            }
            pending = null;
            pendingHeaders = new LinkedHashMap<>();
            pendingOptions.clear();
        }
        if (pending != null) missingUrls++;
        return new Result(channels, duplicates, missingUrls, epgUrl);
    }

    private String parseEpgUrl(String line) {
        Matcher matcher = ATTRIBUTE.matcher(line);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            if (key.equals("url-tvg") || key.equals("x-tvg-url")) {
                String value = firstNonNull(matcher.group(2), matcher.group(3), matcher.group(4)).trim();
                int comma = value.indexOf(',');
                return comma < 0 ? value : value.substring(0, comma).trim();
            }
        }
        return "";
    }

    private Metadata parseMetadata(String line) {
        Metadata metadata = new Metadata();
        metadata.original = line;
        int comma = findNameComma(line);
        String attributes = comma >= 0 ? line.substring(0, comma) : line;
        metadata.name = comma >= 0 ? line.substring(comma + 1).trim() : "";

        Matcher matcher = ATTRIBUTE.matcher(attributes);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = firstNonNull(matcher.group(2), matcher.group(3), matcher.group(4));
            switch (key) {
                case "tvg-name":
                    if (metadata.name.isEmpty()) metadata.name = value;
                    break;
                case "tvg-logo":
                    metadata.tvgLogo = value;
                    break;
                case "logo":
                case "logo-url":
                case "logo_url":
                    metadata.logoUrl = value;
                    break;
                case "icon":
                case "icon-url":
                case "icon_url":
                    metadata.iconUrl = value;
                    break;
                case "tvg-id":
                    metadata.tvgId = value;
                    break;
                case "backup-url":
                case "backup_url":
                case "backup-urls":
                case "backup_urls":
                case "fallback-url":
                case "fallback_url":
                case "fallback-urls":
                case "fallback_urls":
                case "alt-url":
                case "alt_url":
                case "stream-url-2":
                case "stream_url_2":
                case "url-alt":
                case "url_alt":
                    if (value != null && !value.trim().isEmpty()) metadata.alternateUrls.add(value.trim());
                    break;
                case "group-title":
                    metadata.group = value;
                    break;
                default:
                    break;
            }
        }
        return metadata;
    }

    private int findNameComma(String line) {
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if ((current == '\"' || current == '\'') && (!quoted || quote == current)) {
                quoted = !quoted;
                quote = quoted ? current : 0;
            } else if (current == ',' && !quoted) {
                return i;
            }
        }
        return -1;
    }

    private boolean parseHeaderOption(String option, Map<String, String> headers) {
        int separator = option.indexOf('=');
        if (separator < 1) return false;
        String key = option.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String value = option.substring(separator + 1).trim();
        if (key.equals("http-user-agent")) headers.put("User-Agent", value);
        else if (key.equals("http-referrer") || key.equals("http-referer")) headers.put("Referer", value);
        else if (key.equals("http-origin")) headers.put("Origin", value);
        else return false;
        return true;
    }

    private UrlAndHeaders splitUrlAndHeaders(String input) {
        int pipe = input.indexOf('|');
        if (pipe < 0) return new UrlAndHeaders(input.trim(), Collections.emptyMap());
        String url = input.substring(0, pipe).trim();
        Map<String, String> headers = new LinkedHashMap<>();
        String[] pairs = input.substring(pipe + 1).split("&");
        for (String pair : pairs) {
            int separator = pair.indexOf('=');
            if (separator < 1) continue;
            String key = decode(pair.substring(0, separator));
            String value = decode(pair.substring(separator + 1));
            if (key.equalsIgnoreCase("referrer")) key = "Referer";
            if (key.equalsIgnoreCase("user-agent")) key = "User-Agent";
            if (key.equalsIgnoreCase("referer")) key = "Referer";
            if (key.equalsIgnoreCase("origin")) key = "Origin";
            if (validHeader(key, value)) headers.put(key, value);
        }
        return new UrlAndHeaders(url, headers);
    }

    private String resolveUrl(String value, String baseUrl) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return "";
        if (baseUrl == null || baseUrl.isEmpty()) return trimmed;
        try {
            URI uri = URI.create(trimmed);
            if (uri.isAbsolute()) return trimmed;
            return URI.create(baseUrl).resolve(uri).toString();
        } catch (RuntimeException ignored) {
            return trimmed;
        }
    }

    private String fallbackName(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !path.equals("/")) {
                int slash = path.lastIndexOf('/');
                return path.substring(slash + 1);
            }
            if (uri.getHost() != null) return uri.getHost();
        } catch (RuntimeException ignored) { }
        return "Kênh không tên";
    }

    private String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8.name()); }
        catch (Exception ignored) { return value; }
    }

    private String firstNonNull(String... values) {
        for (String value : values) if (value != null) return value;
        return "";
    }

    public String export(List<Channel> channels) {
        return export(channels, true);
    }

    public String exportAll(List<Channel> channels) {
        return export(channels, false);
    }

    private String export(List<Channel> channels, boolean selectedOnly) {
        StringBuilder output = new StringBuilder("#EXTM3U\n");
        for (Channel channel : channels) {
            if ((selectedOnly && !channel.selected()) || channel.url().isEmpty()) continue;
            if (!channel.originalExtInf().isEmpty()) {
                output.append(singleLine(channel.originalExtInf())).append('\n');
                output.append("#EXTGRP:").append(singleLine(channel.group())).append('\n');
            } else {
                output.append("#EXTINF:-1");
                appendAttribute(output, "tvg-id", channel.tvgId());
                appendAttribute(output, "tvg-logo", channel.tvgLogo());
                if (!channel.logoUrl().isEmpty() && !channel.logoUrl().equals(channel.tvgLogo())) {
                    appendAttribute(output, "logo", channel.logoUrl());
                }
                if (!channel.iconUrl().isEmpty() && !channel.iconUrl().equals(channel.tvgLogo())
                        && !channel.iconUrl().equals(channel.logoUrl())) {
                    appendAttribute(output, "icon", channel.iconUrl());
                }
                appendAttribute(output, "group-title", channel.group());
                output.append(',').append(singleLine(channel.name())).append('\n');
            }
            for (String option : channel.options()) output.append(singleLine(option)).append('\n');
            output.append(channel.url());
            boolean first = true;
            for (Map.Entry<String, String> header : channel.headers().entrySet()) {
                if (!validHeader(header.getKey(), header.getValue())) continue;
                output.append(first ? '|' : '&');
                output.append(encode(header.getKey())).append('=').append(encode(header.getValue()));
                first = false;
            }
            output.append('\n');
        }
        return output.toString();
    }

    private void appendAttribute(StringBuilder output, String key, String value) {
        if (value == null || value.isEmpty()) return;
        output.append(' ').append(key).append("=\"")
                .append(singleLine(value).replace("\"", "'"))
                .append('"');
    }

    public static boolean isNetworkUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return scheme != null && uri.getRawAuthority() != null
                    && !uri.getRawAuthority().isEmpty()
                    && SUPPORTED_SCHEME.matcher(scheme).matches();
        } catch (IllegalArgumentException ignored) { return false; }
    }

    private static boolean validHeader(String key, String value) {
        return VALID_HEADER_KEY.matcher(key).matches()
                && value.indexOf('\r') < 0 && value.indexOf('\n') < 0;
    }

    private String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    private String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }

    private static final class Metadata {
        String original = "";
        String name = "";
        String group = "";
        String tvgLogo = "";
        String logoUrl = "";
        String iconUrl = "";
        String tvgId = "";
        final List<String> alternateUrls = new ArrayList<>();
    }

    private static final class UrlAndHeaders {
        final String url;
        final Map<String, String> headers;
        UrlAndHeaders(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers;
        }
    }

    public static final class Result {
        public final List<Channel> channels;
        public final int duplicateCount;
        public final int missingUrlCount;
        public final String epgUrl;

        Result(List<Channel> channels, int duplicateCount, int missingUrlCount) { this(channels, duplicateCount, missingUrlCount, ""); }
        Result(List<Channel> channels, int duplicateCount, int missingUrlCount, String epgUrl) {
            this.channels = Collections.unmodifiableList(channels);
            this.duplicateCount = duplicateCount;
            this.missingUrlCount = missingUrlCount;
            this.epgUrl = epgUrl == null ? "" : epgUrl.trim();
        }
    }
}
