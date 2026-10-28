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
        long start = 0, end = 0;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("programme".equals(tag)) {
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
            }
            event = parser.next();
        }
        for (List<EpgProgram> list : schedules.values()) list.sort(Comparator.comparingLong(p -> p.startMs));
        return new Schedule(schedules);
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
