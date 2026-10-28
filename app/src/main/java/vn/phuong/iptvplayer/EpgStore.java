package vn.phuong.iptvplayer;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Downloads and manages XMLTV EPG data, schedules, and catchup timelines. */
public final class EpgStore {
    private static final Pattern NON_ALNUM_UNICODE = Pattern.compile("[^\\p{L}\\p{N}]");
    private static final Pattern RELAXED_SUFFIX_RES = Pattern.compile("(?i)(fullhd|fhd|uhd|4k|1080p|720p|hd)$");
    private static final Pattern RELAXED_PREFIX_CH = Pattern.compile("(?i)^(kenh|channel)");
    private static final Pattern RELAXED_SUFFIX_CH = Pattern.compile("(?i)(television|channel)$");
    private static final Programme MISS = new Programme("", "", 0L, 0L);

    private static volatile Guide globalGuide;

    public static Guide getGuide() {
        return globalGuide;
    }

    public static void setGuide(Guide guide) {
        globalGuide = guide;
    }

    public static final class Programme {
        public final String title;
        public final String desc;
        public final long startMs, endMs;
        private String cachedStartText;
        private String cachedEndText;

        public Programme(String title, long startMs, long endMs) {
            this(title, "", startMs, endMs);
        }

        public Programme(String title, String desc, long startMs, long endMs) {
            this.title = title == null ? "" : title;
            this.desc = desc == null ? "" : desc;
            this.startMs = startMs;
            this.endMs = endMs;
        }

        public boolean isPast(long now) {
            return endMs <= now;
        }

        public boolean isCurrent(long now) {
            return startMs <= now && now < endMs;
        }

        public boolean isFuture(long now) {
            return startMs > now;
        }

        public int progress() {
            long span = endMs - startMs;
            if (span <= 0) return 0;
            return (int) Math.max(0, Math.min(100, (System.currentTimeMillis() - startMs) * 100 / span));
        }

        public String startText() {
            if (cachedStartText == null) cachedStartText = time(startMs);
            return cachedStartText;
        }

        public String endText() {
            if (cachedEndText == null) cachedEndText = time(endMs);
            return cachedEndText;
        }

        public String timeRangeText() {
            return startText() + " - " + endText();
        }

        public String durationText() {
            long durMinutes = Math.max(0, (endMs - startMs) / 60000L);
            if (durMinutes >= 60) {
                long h = durMinutes / 60;
                long m = durMinutes % 60;
                return m > 0 ? (h + " giờ " + m + " phút") : (h + " giờ");
            }
            return durMinutes + " phút";
        }

        private static String time(long value) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(value));
        }
    }

    public static final class Guide {
        private final Map<String, Programme> programmes;
        private final Map<String, List<Programme>> schedules;
        private final Map<String, String> relaxedKeys;
        private final Map<String, Programme> resolvedCache = new ConcurrentHashMap<>();
        private final Map<String, List<Programme>> resolvedScheduleCache = new ConcurrentHashMap<>();

        Guide(Map<String, Programme> programmes, Map<String, List<Programme>> schedules) {
            this.programmes = programmes;
            this.schedules = schedules;
            this.relaxedKeys = new HashMap<>(programmes.size() + schedules.size());
            for (String key : programmes.keySet()) {
                String r = relaxed(key);
                if (!r.isEmpty()) this.relaxedKeys.put(key, r);
            }
            for (String key : schedules.keySet()) {
                String r = relaxed(key);
                if (!r.isEmpty()) this.relaxedKeys.put(key, r);
            }
        }

        public Programme find(Channel channel) {
            if (channel == null) return null;
            String cacheKey = channel.tvgId() + "\0" + channel.name();
            Programme cached = resolvedCache.get(cacheKey);
            if (cached != null) return cached == MISS ? null : cached;

            String id = normalize(channel.tvgId());
            String name = normalize(channel.name());
            Programme p = programmes.get(id);
            if (p == null) p = programmes.get(name);
            String relaxedId = relaxed(id);
            if (p == null) p = programmes.get(relaxedId);
            String target = relaxed(name);
            if (p == null) p = programmes.get(target);
            if (p == null && !target.isEmpty()) {
                for (Map.Entry<String, Programme> item : programmes.entrySet()) {
                    String key = relaxedKeys.get(item.getKey());
                    if (key != null && !key.isEmpty()
                            && (target.equals(key) || target.contains(key) || key.contains(target))) {
                        p = item.getValue();
                        break;
                    }
                }
            }
            resolvedCache.put(cacheKey, p == null ? MISS : p);
            return p;
        }

        public List<Programme> getSchedule(Channel channel, long targetDateMs) {
            if (channel == null) return Collections.emptyList();
            List<Programme> all = findAllProgrammes(channel);
            if (all.isEmpty()) return Collections.emptyList();

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(targetDateMs);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long dayStart = cal.getTimeInMillis();
            long dayEnd = dayStart + 86400000L;

            List<Programme> dayList = new ArrayList<>();
            for (Programme p : all) {
                // Include programs starting today or spanning into today
                if ((p.startMs >= dayStart && p.startMs < dayEnd)
                        || (p.startMs < dayStart && p.endMs > dayStart)) {
                    dayList.add(p);
                }
            }
            Collections.sort(dayList, Comparator.comparingLong(o -> o.startMs));
            return dayList;
        }

        public List<Programme> findAllProgrammes(Channel channel) {
            if (channel == null) return Collections.emptyList();
            String cacheKey = channel.tvgId() + "\0" + channel.name();
            List<Programme> cached = resolvedScheduleCache.get(cacheKey);
            if (cached != null) return cached;

            String id = normalize(channel.tvgId());
            String name = normalize(channel.name());
            List<Programme> list = schedules.get(id);
            if (list == null) list = schedules.get(name);
            String relaxedId = relaxed(id);
            if (list == null) list = schedules.get(relaxedId);
            String target = relaxed(name);
            if (list == null) list = schedules.get(target);

            if (list == null && !target.isEmpty()) {
                for (Map.Entry<String, List<Programme>> item : schedules.entrySet()) {
                    String key = relaxedKeys.get(item.getKey());
                    if (key != null && !key.isEmpty()
                            && (target.equals(key) || target.contains(key) || key.contains(target))) {
                        list = item.getValue();
                        break;
                    }
                }
            }

            if (list == null) list = Collections.emptyList();
            resolvedScheduleCache.put(cacheKey, list);
            return list;
        }

        public int size() { return programmes.size(); }
    }

    public static Guide download(String address) throws Exception {
        if (address == null || !(address.startsWith("http://") || address.startsWith("https://"))) {
            throw new IllegalArgumentException("URL EPG phải bắt đầu bằng http:// hoặc https://");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Nm7-IPTV/1.10.1 Android");
        connection.setRequestProperty("Accept-Encoding", "gzip");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        try (InputStream raw = new BufferedInputStream(connection.getInputStream());
             InputStream input = isGzip(address, connection) ? new GZIPInputStream(raw) : raw) {
            Guide guide = parse(input, System.currentTimeMillis());
            globalGuide = guide;
            return guide;
        } finally {
            connection.disconnect();
        }
    }

    public static Guide parse(InputStream input, long now) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(input, "UTF-8");
        Map<String, List<String>> displayNames = new HashMap<>();
        Map<String, Programme> byId = new HashMap<>();
        Map<String, List<Programme>> schedulesById = new HashMap<>();

        String channelId = "", programmeId = "", title = "", desc = "";
        long start = 0, end = 0;
        long minKeepTime = now - 8L * 86400000L;
        long maxKeepTime = now + 4L * 86400000L;

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("channel".equals(tag)) {
                    channelId = value(parser, "id");
                } else if ("display-name".equals(tag) && !channelId.isEmpty()) {
                    String name = parser.nextText();
                    if (name != null && !name.trim().isEmpty()) {
                        displayNames.computeIfAbsent(channelId, k -> new ArrayList<>()).add(name.trim());
                    }
                } else if ("programme".equals(tag)) {
                    programmeId = value(parser, "channel");
                    start = parseTime(value(parser, "start"));
                    end = parseTime(value(parser, "stop"));
                    title = "";
                    desc = "";
                } else if ("title".equals(tag) && !programmeId.isEmpty()) {
                    title = parser.nextText();
                } else if ("desc".equals(tag) && !programmeId.isEmpty()) {
                    desc = parser.nextText();
                }
            } else if (event == XmlPullParser.END_TAG) {
                String tag = parser.getName();
                if ("channel".equals(tag)) {
                    channelId = "";
                } else if ("programme".equals(tag)) {
                    if (!programmeId.isEmpty() && end > start && end >= minKeepTime && start <= maxKeepTime) {
                        String cleanTitle = title == null || title.trim().isEmpty() ? "Chương trình TV" : title.trim();
                        String cleanDesc = desc == null ? "" : desc.trim();
                        Programme programme = new Programme(cleanTitle, cleanDesc, start, end);

                        schedulesById.computeIfAbsent(programmeId, k -> new ArrayList<>()).add(programme);

                        if (start <= now && now < end) {
                            byId.put(programmeId, programme);
                        }
                    }
                    programmeId = "";
                }
            }
            event = parser.next();
        }

        // Sort all program lists by start time
        for (List<Programme> list : schedulesById.values()) {
            Collections.sort(list, Comparator.comparingLong(o -> o.startMs));
        }

        Map<String, Programme> resultCurrent = new HashMap<>(byId.size() * 4);
        for (Map.Entry<String, Programme> item : byId.entrySet()) {
            putAlias(resultCurrent, item.getKey(), item.getValue());
            List<String> names = displayNames.get(item.getKey());
            if (names != null) for (String name : names) putAlias(resultCurrent, name, item.getValue());
        }

        Map<String, List<Programme>> resultSchedules = new HashMap<>(schedulesById.size() * 4);
        for (Map.Entry<String, List<Programme>> item : schedulesById.entrySet()) {
            putScheduleAlias(resultSchedules, item.getKey(), item.getValue());
            List<String> names = displayNames.get(item.getKey());
            if (names != null) for (String name : names) putScheduleAlias(resultSchedules, name, item.getValue());
        }

        return new Guide(resultCurrent, resultSchedules);
    }

    private static void putAlias(Map<String, Programme> result, String value, Programme programme) {
        String normal = normalize(value);
        if (!normal.isEmpty()) result.put(normal, programme);
        String relaxed = relaxed(normal);
        if (!relaxed.isEmpty()) result.put(relaxed, programme);
    }

    private static void putScheduleAlias(Map<String, List<Programme>> result, String value, List<Programme> list) {
        String normal = normalize(value);
        if (!normal.isEmpty()) result.put(normal, list);
        String relaxed = relaxed(normal);
        if (!relaxed.isEmpty()) result.put(relaxed, list);
    }

    public static boolean isGzip(String url, HttpURLConnection connection) {
        String encoding = connection.getContentEncoding();
        String type = connection.getContentType();
        return (url != null && url.toLowerCase(Locale.ROOT).endsWith(".gz"))
                || (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip"))
                || (type != null && type.toLowerCase(Locale.ROOT).contains("gzip"));
    }

    private static String value(XmlPullParser parser, String name) {
        String value = parser.getAttributeValue(null, name);
        return value == null ? "" : value.trim();
    }

    /** Fast numeric XMLTV timestamp parser with SimpleDateFormat fallback. */
    private static long parseTime(String value) {
        if (value == null) return 0L;
        String clean = value.trim();
        int len = clean.length();
        if (len >= 12) {
            int digits = 0;
            while (digits < len && Character.isDigit(clean.charAt(digits))) digits++;
            if (digits == 12 || digits == 14) {
                try {
                    int year = Integer.parseInt(clean.substring(0, 4));
                    int month = Integer.parseInt(clean.substring(4, 6));
                    int day = Integer.parseInt(clean.substring(6, 8));
                    int hour = Integer.parseInt(clean.substring(8, 10));
                    int minute = Integer.parseInt(clean.substring(10, 12));
                    int second = digits == 14 ? Integer.parseInt(clean.substring(12, 14)) : 0;
                    String rest = clean.substring(digits).trim();
                    TimeZone tz = TimeZone.getDefault();
                    if (!rest.isEmpty()) {
                        if ("Z".equalsIgnoreCase(rest) || "UTC".equalsIgnoreCase(rest) || "GMT".equalsIgnoreCase(rest)) {
                            tz = TimeZone.getTimeZone("GMT");
                        } else if ((rest.charAt(0) == '+' || rest.charAt(0) == '-') && rest.length() >= 5) {
                            tz = TimeZone.getTimeZone("GMT" + rest.substring(0, 5));
                        }
                    }
                    Calendar cal = new GregorianCalendar(tz, Locale.ROOT);
                    cal.clear();
                    cal.setLenient(false);
                    cal.set(year, month - 1, day, hour, minute, second);
                    cal.set(Calendar.MILLISECOND, 0);
                    return cal.getTimeInMillis();
                } catch (Exception ignored) { }
            }
        }
        String normalizedSpace = clean.replaceAll("\\s+", " ");
        String[] patterns = {"yyyyMMddHHmmss Z", "yyyyMMddHHmm Z", "yyyyMMddHHmmss", "yyyyMMddHHmm"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.ROOT);
                if (!pattern.contains("Z")) f.setTimeZone(TimeZone.getDefault());
                f.setLenient(false);
                Date d = f.parse(normalizedSpace);
                if (d != null) return d.getTime();
            } catch (Exception ignored) { }
        }
        return 0L;
    }

    private static String normalize(String value) {
        return value == null ? "" : NON_ALNUM_UNICODE.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static String relaxed(String value) {
        String n = normalize(value);
        if (n.isEmpty()) return "";
        n = RELAXED_SUFFIX_RES.matcher(n).replaceAll("");
        n = RELAXED_PREFIX_CH.matcher(n).replaceAll("");
        return RELAXED_SUFFIX_CH.matcher(n).replaceAll("");
    }
}
