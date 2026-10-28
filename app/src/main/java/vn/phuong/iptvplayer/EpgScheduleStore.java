package vn.phuong.iptvplayer;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Parses full XMLTV programme schedules; network loading is delegated to existing EpgStore. */
final class EpgScheduleStore {
    private EpgScheduleStore() {}

    static final class Schedule {
        private final Map<String,List<EpgProgram>> byChannel;
        Schedule(Map<String,List<EpgProgram>> values) { byChannel = values; }
        List<EpgProgram> forChannel(Channel channel, long dayStartMs) {
            if (channel == null) return Collections.emptyList();
            List<EpgProgram> all = byChannel.get(channel.tvgId());
            if (all == null) all = byChannel.get(channel.name());
            if (all == null) all = byChannel.get(normalize(channel.tvgId()));
            if (all == null) all = byChannel.get(normalize(channel.name()));
            if (all == null) {
                String wantedId = normalize(channel.tvgId());
                String wantedName = normalize(channel.name());
                for (Map.Entry<String,List<EpgProgram>> entry : byChannel.entrySet()) {
                    String key = normalize(entry.getKey());
                    if ((!wantedId.isEmpty() && wantedId.equals(key))
                            || (!wantedName.isEmpty() && wantedName.equals(key))
                            || (!wantedName.isEmpty() && !key.isEmpty()
                                && (wantedName.contains(key) || key.contains(wantedName)))) {
                        all = entry.getValue();
                        break;
                    }
                }
            }
            if (all == null) return Collections.emptyList();
            long dayEnd = dayStartMs + 86400000L;
            List<EpgProgram> result = new ArrayList<>();
            for (EpgProgram p : all) if (p.endMs > dayStartMs && p.startMs < dayEnd) result.add(p);
            return result;
        }
    }

    static Schedule parse(InputStream input) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(input, "UTF-8");
        Map<String,List<EpgProgram>> schedules = new HashMap<>();
        String channel = "", title = "", desc = "";
        String definitionId = "";
        Map<String,String> displayNameToId = new HashMap<>();
        long start = 0, end = 0;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("channel".equals(tag)) {
                    definitionId = value(parser, "id");
                } else if ("display-name".equals(tag) && !definitionId.isEmpty()) {
                    String displayName = parser.nextText().trim();
                    if (!displayName.isEmpty()) {
                        displayNameToId.put(displayName, definitionId);
                        displayNameToId.put(normalize(displayName), definitionId);
                    }
                } else if ("programme".equals(tag)) {
                    channel = value(parser, "channel");
                    start = parseTime(value(parser, "start"));
                    end = parseTime(value(parser, "stop"));
                    title = ""; desc = "";
                } else if ("title".equals(tag) && !channel.isEmpty()) {
                    title = parser.nextText();
                } else if ("desc".equals(tag) && !channel.isEmpty()) {
                    desc = parser.nextText();
                }
            } else if (event == XmlPullParser.END_TAG && "programme".equals(parser.getName())) {
                EpgProgram p = new EpgProgram(channel,title,start,end,desc);
                if (p.isValid() && !channel.isEmpty()) schedules.computeIfAbsent(channel,k->new ArrayList<>()).add(p);
                channel = "";
            } else if (event == XmlPullParser.END_TAG && "channel".equals(parser.getName())) {
                definitionId = "";
            }
            event = parser.next();
        }
        for (List<EpgProgram> list : schedules.values()) list.sort(Comparator.comparingLong(p -> p.startMs));
        Map<String,List<EpgProgram>> aliases = new HashMap<>(schedules);
        for (Map.Entry<String,List<EpgProgram>> entry : schedules.entrySet()) {
            String key = normalize(entry.getKey());
            if (!key.isEmpty()) aliases.putIfAbsent(key, entry.getValue());
        }
        for (Map.Entry<String,String> alias : displayNameToId.entrySet()) {
            List<EpgProgram> programmes = schedules.get(alias.getValue());
            if (programmes != null) {
                aliases.putIfAbsent(alias.getKey(), programmes);
                String key = normalize(alias.getKey());
                if (!key.isEmpty()) aliases.putIfAbsent(key, programmes);
            }
        }
        return new Schedule(aliases);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String value(XmlPullParser parser, String name) {
        String value = parser.getAttributeValue(null,name);
        return value == null ? "" : value;
    }

    private static long parseTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        String raw = value.trim();
        String[] patterns = {"yyyyMMddHHmmss Z","yyyyMMddHHmmss"};
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            if (pattern.endsWith("Z")) format.setTimeZone(TimeZone.getTimeZone("UTC"));
            ParsePosition position = new ParsePosition(0);
            Date parsed = format.parse(raw, position);
            if (parsed != null && position.getIndex() == raw.length()) return parsed.getTime();
        }
        return 0L;
    }
}
