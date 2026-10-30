package vn.phuong.iptvplayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class Channel {
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern VTV_NAME_1 = Pattern.compile("vtv 1( hd)?");
    private static final Pattern VTV_NAME_2 = Pattern.compile("vtv 2( hd)?");
    private static final Pattern VTV_NAME_3 = Pattern.compile("vtv 3( hd)?");
    private static final Pattern VTV_NAME_4 = Pattern.compile("vtv 4( hd)?");
    private static final Pattern VTV_NAME_5 = Pattern.compile("vtv 5( hd)?");
    private static final Pattern VTV_NAME_6 = Pattern.compile("vtv 6( hd)?");
    private static final Pattern VTV_NAME_7 = Pattern.compile("vtv 7( hd)?");
    private static final Pattern VTV_NAME_8 = Pattern.compile("vtv 8( hd)?");
    private static final Pattern VTV_NAME_9 = Pattern.compile("vtv 9( hd)?");
    private static final Pattern ON_SPORTS_NAME = Pattern.compile("on sports( hd)?");

    private final String name;
    private final String group;
    private final String url;
    private final String tvgLogo;
    private final String logoUrl;
    private final String iconUrl;
    private final String tvgId;
    private final Map<String, String> headers;
    private final String catchupType;
    private final int catchupDays;
    private final String catchupSource;
    private final String nameLower;
    private final String groupLower;
    private final String urlLower;
    private boolean selected = true;
    private String originalExtInf = "";
    private final List<String> options = new ArrayList<>();

    private String cachedIdentityKey;
    private String cachedSha256Id;
    private int cachedOptionsSize = -1;
    private int cachedOptionsHash;
    private List<String> cachedLogoCandidates;

    public Channel(String name, String group, String url, String logo, String tvgId,
                   Map<String, String> headers) {
        this(name, group, url, logo, logo, "", tvgId, headers, "", 0, "");
    }

    public Channel(String name, String group, String url, String tvgLogo, String logoUrl,
                   String iconUrl, String tvgId, Map<String, String> headers) {
        this(name, group, url, tvgLogo, logoUrl, iconUrl, tvgId, headers, "", 0, "");
    }

    public Channel(String name, String group, String url, String tvgLogo, String logoUrl,
                   String iconUrl, String tvgId, Map<String, String> headers,
                   String catchupType, int catchupDays, String catchupSource) {
        this.name = clean(name, "Kênh không tên");
        this.group = clean(group, "Chưa phân nhóm");
        this.url = clean(url, "");
        this.tvgLogo = clean(tvgLogo, "");
        this.logoUrl = clean(logoUrl, "");
        this.iconUrl = clean(iconUrl, "");
        this.tvgId = clean(tvgId, "");
        this.headers = headers == null || headers.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.catchupType = clean(catchupType, "");
        this.catchupDays = Math.max(0, catchupDays);
        this.catchupSource = clean(catchupSource, "");
        this.nameLower = this.name.toLowerCase(Locale.ROOT);
        this.groupLower = this.group.toLowerCase(Locale.ROOT);
        this.urlLower = this.url.toLowerCase(Locale.ROOT);
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    public String name() { return name; }
    public String group() { return group; }
    public String url() { return url; }
    String nameLower() { return nameLower; }
    String groupLower() { return groupLower; }
    String urlLower() { return urlLower; }

    /** Original tvg-logo attribute from the playlist. */
    public String logo() { return tvgLogo; }
    public String tvgLogo() { return tvgLogo; }

    /** Explicit logo/logo_url attribute when present. */
    public String logoUrl() { return logoUrl; }

    /** icon/icon_url is the final metadata fallback. */
    public String iconUrl() { return iconUrl; }

    /**
     * Super OK-style effective logo resolution:
     * explicit logo URL -> tvg-logo -> icon URL.
     */
    public String effectiveLogoUrl() {
        if (!logoUrl.isEmpty()) return logoUrl;
        if (!tvgLogo.isEmpty()) return tvgLogo;
        return iconUrl;
    }

    /**
     * Ordered unique candidates let the UI retry a secondary logo source without
     * changing the channel's metadata.
     */
    public List<String> logoCandidates() {
        if (cachedLogoCandidates != null) return cachedLogoCandidates;
        LinkedHashMap<String, Boolean> unique = new LinkedHashMap<>();

        if (!logoUrl.isEmpty()) unique.put(logoUrl, Boolean.TRUE);
        if (!tvgLogo.isEmpty()) unique.put(tvgLogo, Boolean.TRUE);
        if (!iconUrl.isEmpty()) unique.put(iconUrl, Boolean.TRUE);

        for (String fallback : logoCatalogFallbacks()) {
            if (fallback != null && !fallback.isEmpty()) unique.put(fallback, Boolean.TRUE);
        }

        cachedLogoCandidates = Collections.unmodifiableList(new ArrayList<>(unique.keySet()));
        return cachedLogoCandidates;
    }

    private List<String> logoCatalogFallbacks() {
        LinkedHashMap<String, Boolean> urls = new LinkedHashMap<>();
        String id = NON_ALNUM.matcher(tvgId.toLowerCase(Locale.ROOT)).replaceAll("");
        String normalizedName = NON_ALNUM.matcher(nameLower).replaceAll(" ").trim();

        String vtv = null;
        if (id.equals("vtv1hd") || VTV_NAME_1.matcher(normalizedName).matches()) vtv = "1";
        else if (id.equals("vtv2hd") || VTV_NAME_2.matcher(normalizedName).matches()) vtv = "2";
        else if (id.equals("vtv3hd") || VTV_NAME_3.matcher(normalizedName).matches()) vtv = "3";
        else if (id.equals("vtv4hd") || VTV_NAME_4.matcher(normalizedName).matches()) vtv = "4";
        else if (id.equals("vtv5hd") || VTV_NAME_5.matcher(normalizedName).matches()) vtv = "5";
        else if (id.equals("vtv6hd") || VTV_NAME_6.matcher(normalizedName).matches()) vtv = "6";
        else if (id.equals("vtv7hd") || VTV_NAME_7.matcher(normalizedName).matches()) vtv = "7";
        else if (id.equals("vtv8hd") || VTV_NAME_8.matcher(normalizedName).matches()) vtv = "8";
        else if (id.equals("vtv9hd") || VTV_NAME_9.matcher(normalizedName).matches()) vtv = "9";
        if (vtv != null) urls.put("https://cdn.hqth.me/logo/thumbs/" + vtv + ".png", Boolean.TRUE);

        if (id.equals("vtv5hdtnb") || nameLower.contains("vtv 5 tây nam bộ") || normalizedName.contains("vtv 5 tay nam bo")) {
            urls.put("https://cdn.hqth.me/logo/thumbs/6.png", Boolean.TRUE);
            urls.put("https://i.imgur.com/mIUWkDx.png", Boolean.TRUE);
        }
        if (id.equals("vtv5hdtn") || nameLower.contains("vtv 5 tây nguyên") || normalizedName.contains("vtv 5 tay nguyen")) {
            urls.put("https://cdn.hqth.me/logo/thumbs/7.png", Boolean.TRUE);
            urls.put("https://i.imgur.com/R8c2swd.png", Boolean.TRUE);
        }

        if (id.equals("onsportsvn") || id.equals("vtvcab3hd")
                || normalizedName.contains("vtvcab 3") || ON_SPORTS_NAME.matcher(normalizedName).matches()) {
            urls.put("https://img.vnmedia.xyz/logo/on-sports.jpg", Boolean.TRUE);
            urls.put("https://cdn.hqth.me/logo/thumbs/14.png", Boolean.TRUE);
        }
        if (id.equals("onsportsplusvn") || id.equals("vtvcab6hd")
                || normalizedName.contains("vtvcab 6") || nameLower.startsWith("on sports+")) {
            urls.put("https://img.vnmedia.xyz/logo/on-sportsplus.jpg", Boolean.TRUE);
            urls.put("https://cdn.hqth.me/logo/thumbs/17.png", Boolean.TRUE);
        }
        if (id.equals("onfootballvn") || id.equals("vtvcab16hd")
                || normalizedName.contains("vtvcab 16") || normalizedName.startsWith("on football")) {
            urls.put("https://img.vnmedia.xyz/logo/on-football.jpg", Boolean.TRUE);
            urls.put("https://cdn.hqth.me/logo/thumbs/24.png", Boolean.TRUE);
        }
        if (id.equals("onsportsnewsvn") || id.equals("vtvcab18hd")
                || normalizedName.contains("vtvcab 18") || normalizedName.startsWith("on sports news")) {
            urls.put("https://img.vnmedia.xyz/logo/on-sportsnews.jpg", Boolean.TRUE);
            urls.put("https://cdn.hqth.me/logo/thumbs/26.png", Boolean.TRUE);
        }

        String cab = null;
        if (id.equals("vtvcab1hd")) cab = "12";
        else if (id.equals("vtvcab2hd")) cab = "13";
        else if (id.equals("vtvcab5hd")) cab = "16";
        else if (id.equals("vtvcab7hd")) cab = "18";
        else if (id.equals("vtvcab8hd")) cab = "19";
        else if (id.equals("vtvcab9hd")) cab = "20";
        else if (id.equals("vtvcab10hd")) cab = "21";
        else if (id.equals("vtvcab12hd")) cab = "22";
        else if (id.equals("vtvcab15hd")) cab = "23";
        else if (id.equals("vtvcab17hd")) cab = "25";
        else if (id.equals("vtvcab19hd")) cab = "27";
        else if (id.equals("vtvcab20hd")) cab = "28";
        else if (id.equals("vtvcab21hd")) cab = "29";
        else if (id.equals("vtvcab22hd")) cab = "30";
        if (cab != null) urls.put("https://cdn.hqth.me/logo/thumbs/" + cab + ".png", Boolean.TRUE);

        return new ArrayList<>(urls.keySet());
    }

    public String tvgId() { return tvgId; }
    public String catchupType() { return catchupType; }
    public int catchupDays() { return catchupDays; }
    public String catchupSource() { return catchupSource; }
    public boolean hasCatchup() {
        if (catchupSource != null && !catchupSource.isEmpty()) return true;
        if (catchupType != null && !catchupType.isEmpty()) return true;
        if (catchupDays > 0) return true;
        String lowerUrl = url != null ? url.toLowerCase(Locale.ROOT) : "";
        String lowerTvg = tvgId != null ? tvgId.toLowerCase(Locale.ROOT) : "";
        if (lowerUrl.contains("vtvprime") || lowerUrl.contains("livesnv2/vtv")) return true;
        if (lowerTvg.matches("(?i)vtv[1-9].*")) return true;
        return false;
    }
    public Map<String, String> headers() { return headers; }
    public boolean selected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public String originalExtInf() { return originalExtInf; }
    public void setOriginalExtInf(String line) { originalExtInf = line; }
    public List<String> options() { return options; }

    public String mimeHint() {
        for (String option : options) {
            String lower = option.toLowerCase(Locale.ROOT);
            if (lower.contains("manifest_type=hls") || lower.contains("manifest-type=hls")) return "application/x-mpegURL";
            if (lower.contains("manifest_type=mpd") || lower.contains("manifest-type=mpd")
                    || lower.contains("manifest_type=dash") || lower.contains("manifest-type=dash"))
                return "application/dash+xml";
            if (lower.startsWith("#kodiprop:mimetype=")) return option.substring(option.indexOf('=') + 1).trim();
        }
        return "";
    }

    public boolean needsDrm() {
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).contains("inputstream.adaptive.license")) return true;
        }
        return false;
    }

    public String identityKey() {
        int currentOptionsSize = options.size();
        int currentOptionsHash = options.hashCode();
        if (cachedIdentityKey != null
                && cachedOptionsSize == currentOptionsSize
                && cachedOptionsHash == currentOptionsHash) {
            return cachedIdentityKey;
        }
        Map<String, String> normalized = headers.isEmpty() ? Collections.emptyMap() : new TreeMap<>();
        if (!headers.isEmpty()) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                normalized.put(header.getKey().toLowerCase(Locale.ROOT), header.getValue());
            }
        }
        StringBuilder key = new StringBuilder(url.length() + 32);
        appendKeyPart(key, url.trim());
        key.append(normalized.size()).append(':');
        for (Map.Entry<String, String> header : normalized.entrySet()) {
            appendKeyPart(key, header.getKey());
            appendKeyPart(key, header.getValue());
        }
        key.append(currentOptionsSize).append(':');
        for (String option : options) appendKeyPart(key, option);
        cachedOptionsSize = currentOptionsSize;
        cachedOptionsHash = currentOptionsHash;
        cachedIdentityKey = key.toString();
        cachedSha256Id = null;
        return cachedIdentityKey;
    }

    String sha256Id() {
        String idKey = identityKey();
        if (cachedSha256Id != null) return cachedSha256Id;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(idKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte value : digest) out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            cachedSha256Id = out.toString();
        } catch (Exception impossible) {
            cachedSha256Id = Integer.toHexString(idKey.hashCode());
        }
        return cachedSha256Id;
    }

    private static void appendKeyPart(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    @Override public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Channel)) return false;
        return identityKey().equals(((Channel) object).identityKey());
    }

    @Override public int hashCode() { return Objects.hash(identityKey()); }
}
