package vn.phuong.iptvplayer;

import java.text.Normalizer;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One logical sports fixture and its independent stream choices. */
public final class LiveEvent {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SERVER_SUFFIX = Pattern.compile(
            "(?i)\\s*(?:[-|•:]\\s*)?(?:server|srv|nguon|máy chủ|may chu)\\s*#?\\s*\\d+\\s*(?:\\([^)]*\\)|\\[[^]]*])?\\s*$");
    private static final Pattern SERVER_ROLE_SUFFIX = Pattern.compile(
            "(?i)\\s*(?:[-|•:]\\s*)?(?:backup|du phong|primary|main)\\s*$");
    private static final Pattern LIVE_TAG = Pattern.compile(
            "(?i)(?:^\\s*\\[?live\\]?\\s*[:|–-]?\\s*|\\s*[|•–-]\\s*live\\s*$|\\s*[\\[(]\\s*live\\s*[)\\]]\\s*$)");
    private static final Pattern TIME_DATE = Pattern.compile(
            "(?<!\\d)(?<hour>[01]?\\d|2[0-3]):(?<minute>[0-5]\\d)\\s+(?<day>\\d{1,2})/(?<month>\\d{1,2})(?:/(?<year>\\d{2,4}))?");
    private static final Pattern DATE_TIME = Pattern.compile(
            "(?<!\\d)(?<day>\\d{1,2})/(?<month>\\d{1,2})(?:/(?<year>\\d{2,4}))?\\s+(?<hour>[01]?\\d|2[0-3]):(?<minute>[0-5]\\d)");
    private static final Pattern MATCH_SPLIT = Pattern.compile("(?i)\\s+(?:vs?\\.?|versus)\\s+");
    private static final Pattern LIVE_MARKER_TEXT = Pattern.compile("(?i)(?:\\[\\s*live\\s*\\]|\\blive\\s+now\\b|\\bcurrently live\\b|\\bdang live\\b|\\btruc tiep\\b)");
    private static final Pattern PARENTHETICAL = Pattern.compile("\\(([^)]{1,50})\\)");
    private static final Pattern TRANSPORT_TAG = Pattern.compile("(?i)\\[(flv|hls(?:\\s*\\d+)?|m3u8|rtmp|dash|mpd)\\]");
    private static final Pattern TEAM_GENERIC_WORDS = Pattern.compile("(?i)\\b(?:fc|cf|sc|afc|clb|club|ca|rcd|ud|cd|ac|us|ss|jk|f\\.?c\\.?|s\\.?c\\.?)\\b");
    private static final Pattern COUNTRY_STATE_SUFFIX = Pattern.compile("_(?:mg|sp|rj)\\b");
    private static final Pattern DISPLAY_RES_SUFFIX = Pattern.compile("(?i)\\s*\\((?:hd|fhd|uhd|4k|1080p|720p)\\)\\s*$");
    private static final Pattern TITLE_NOISE_WORDS = Pattern.compile("\\b(?:hd|fhd|uhd|4k|1080p|720p|server|srv|backup|primary|main)\\b");
    private static final String[] BASKETBALL_TEAMS = {
            "atlanta hawks", "boston celtics", "brooklyn nets", "charlotte hornets",
            "chicago bulls", "cleveland cavaliers", "dallas mavericks", "denver nuggets",
            "detroit pistons", "golden state warriors", "houston rockets", "indiana pacers",
            "la clippers", "los angeles clippers", "los angeles lakers", "la lakers",
            "memphis grizzlies", "miami heat", "milwaukee bucks", "minnesota timberwolves",
            "new orleans pelicans", "new york knicks", "oklahoma city thunder", "orlando magic",
            "philadelphia 76ers", "phoenix suns", "portland trail blazers", "sacramento kings",
            "san antonio spurs", "toronto raptors", "utah jazz", "washington wizards"
    };
    private static final String[] HOCKEY_TEAMS = {
            "anaheim ducks", "boston bruins", "buffalo sabres", "calgary flames",
            "carolina hurricanes", "chicago blackhawks", "colorado avalanche", "columbus blue jackets",
            "dallas stars", "detroit red wings", "edmonton oilers", "florida panthers",
            "los angeles kings", "minnesota wild", "montreal canadiens", "nashville predators",
            "new jersey devils", "new york islanders", "new york rangers", "ottawa senators",
            "philadelphia flyers", "pittsburgh penguins", "san jose sharks", "seattle kraken",
            "st louis blues", "tampa bay lightning", "toronto maple leafs", "vancouver canucks",
            "vegas golden knights", "washington capitals", "winnipeg jets"
    };
    private static final Map<String, String> TEAM_ALIASES = createTeamAliases();
    private static Map<String, String> createTeamAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("atl san luis", "atletico san luis");
        aliases.put("atletico san luis", "atletico san luis");
        aliases.put("atl san luis fc", "atletico san luis");
        aliases.put("atletico de san luis", "atletico san luis");
        aliases.put("stade rennais", "rennes");
        aliases.put("los angeles galaxy", "la galaxy");
        aliases.put("st louis city", "st louis city");
        aliases.put("atletico mineiro", "atletico mineiro");
        aliases.put("rennais", "rennes");
        aliases.put("dep la coruna", "deportivo la coruna");
        aliases.put("dep la cor", "deportivo la coruna");
        aliases.put("deportivo de la coruna", "deportivo la coruna");
        aliases.put("deportivo la coruna", "deportivo la coruna");
        aliases.put("sc freiburg", "freiburg");
        aliases.put("freiburg im breisgau", "freiburg");
        aliases.put("real betis balompie", "real betis");
        aliases.put("betis balompie", "real betis");
        aliases.put("u s sassuolo calcio", "sassuolo");
        aliases.put("sassuolo calcio", "sassuolo");
        aliases.put("kocaelispor", "kocaelispor");
        aliases.put("besiktas jk", "besiktas");
        aliases.put("besiktas", "besiktas");
        aliases.put("sport club internacional", "internacional");
        aliases.put("gremio foot ball porto alegrense", "gremio");
        aliases.put("atletico mineiro mg", "atletico mineiro");
        aliases.put("santos fc sp", "santos");
        return Collections.unmodifiableMap(aliases);
    }
    private static final TimeZone FEED_TIME_ZONE = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
    private static final long LIVE_MAX_AGE_MS = 180L * 60L * 1000L;
    private static final long PRE_KICKOFF_TOLERANCE_MS = 15L * 60L * 1000L;
    private static final long SOURCE_TIME_SKEW_MS = 18L * 60L * 60L * 1000L;
    // Allow long games only while the dedicated feed still explicitly marks them LIVE.
    private static final long LIVE_MARKED_MAX_AGE_MS = 8L * 60L * 60L * 1000L;

    private final String id;
    private final String title;
    private final String group;
    private final List<Channel> sources;
    private final List<String> sourceLabels;
    private final int selectedIndex;
    private final long kickoffMillis;
    private final String sport;
    private final String titleLower;
    private final String groupLower;

    private LiveEvent(String id, String title, String group, List<Channel> sources,
                      List<String> sourceLabels, int selectedIndex, long kickoffMillis, String sport) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.group = group == null ? "" : group;
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        this.sourceLabels = Collections.unmodifiableList(new ArrayList<>(sourceLabels));
        this.selectedIndex = sources.isEmpty() ? 0 : Math.max(0, Math.min(selectedIndex, sources.size() - 1));
        this.kickoffMillis = kickoffMillis;
        this.sport = sport == null ? "Thể thao" : sport;
        this.titleLower = this.title.toLowerCase(Locale.ROOT);
        this.groupLower = this.group.toLowerCase(Locale.ROOT);
    }

    public String id() { return id; }
    public String title() { return title; }
    public String group() { return group; }
    String titleLower() { return titleLower; }
    String groupLower() { return groupLower; }
    public List<Channel> sources() { return sources; }
    public List<String> sourceLabels() { return sourceLabels; }
    public String sourceLabel(int index) {
        if (index < 0 || index >= sourceLabels.size()) return "Server " + (index + 1);
        String value = sourceLabels.get(index);
        return value == null || value.trim().isEmpty() ? "Server " + (index + 1) : value;
    }
    public int selectedIndex() { return selectedIndex; }
    public Channel primary() { return sources.get(selectedIndex); }
    public long kickoffMillis() { return kickoffMillis; }
    /** Whole minutes since kickoff; clamped to zero for future or unavailable timestamps. */
    public long elapsedLiveMinutes(long nowMillis) {
        if (kickoffMillis <= 0L || nowMillis <= kickoffMillis) return 0L;
        return (nowMillis - kickoffMillis) / 60_000L;
    }
    public String sport() { return sport; }
    public int indexOfUrl(String url) {
        if (url == null) return -1;
        for (int i = 0; i < sources.size(); i++) if (url.equals(sources.get(i).url())) return i;
        return -1;
    }
    public boolean hasSourceUrl(String url) { return indexOfUrl(url) >= 0; }

    /** Restore an event whose sources were serialized into a player intent. */
    public static LiveEvent restored(String id, String title, String group, List<Channel> sources,
                                     List<String> labels, long kickoffMillis, String sport,
                                     String selectedUrl) {
        if (sources == null || sources.isEmpty()) return null;
        List<String> safeLabels = new ArrayList<>(sources.size());
        for (int i = 0; i < sources.size(); i++) {
            String label = labels != null && i < labels.size() ? labels.get(i) : "";
            safeLabels.add(label == null || label.trim().isEmpty() ? "Server " + (i + 1) : label);
        }
        LiveEvent event = new LiveEvent(id, title, group, sources, safeLabels, 0, kickoffMillis, sport);
        return event.withPreferredUrl(selectedUrl);
    }

    public LiveEvent withPreferredUrl(String url) {
        int index = indexOfUrl(url);
        return index < 0 ? this : new LiveEvent(id, title, group, sources, sourceLabels, index, kickoffMillis, sport);
    }

    /** Legacy helper retained for non-feed M3U regression tests only. */
    public static List<LiveEvent> fromChannels(List<Channel> channels) {
        Map<String, LegacyBuilder> grouped = new LinkedHashMap<>();
        if (channels == null) return Collections.emptyList();
        for (Channel channel : channels) {
            if (channel == null || !isLiveChannel(channel)) continue;
            String title = displayTitle(channel.name());
            String key = normalizeTitle(title);
            if (key.isEmpty()) key = "event-" + channel.identityKey();
            LegacyBuilder builder = grouped.get(key);
            if (builder == null) {
                builder = new LegacyBuilder(key, title, channel.group());
                grouped.put(key, builder);
            }
            for (Channel source : expandSources(channel, title)) {
                if (builder.seen.add(source.identityKey())) {
                    builder.sources.add(source);
                    builder.labels.add(legacySourceLabel(source, builder.sources.size()));
                }
            }
        }
        List<LiveEvent> result = new ArrayList<>();
        for (LegacyBuilder b : grouped.values()) {
            if (!b.sources.isEmpty()) result.add(new LiveEvent(b.id, b.title, b.group,
                    b.sources, b.labels, 0, 0L, "Thể thao"));
        }
        return result;
    }

    /**
     * Builds Live cards specifically from the dedicated sports feed. A fixture must
     * have an explicit live marker and a plausible start time. Regular provider sources
     * are kept for 180 minutes, while a provider still explicitly marked LIVE is allowed
     * up to eight hours for long matches. Every same-fixture source becomes a server option.
     */
    public static List<LiveEvent> fromDedicatedFeed(List<Channel> channels, long nowMillis) {
        if (channels == null || channels.isEmpty()) return Collections.emptyList();
        List<FeedEntry> entries = new ArrayList<>();
        for (Channel channel : channels) {
            if (channel == null || !M3uParser.isNetworkUrl(channel.url())) continue;
            FeedEntry entry = parseFeedEntry(channel, nowMillis);
            if (entry != null) entries.add(entry);
        }

        List<FixtureBuilder> fixtures = new ArrayList<>();
        for (FeedEntry entry : entries) {
            FixtureBuilder found = null;
            for (FixtureBuilder candidate : fixtures) {
                if (sameFixture(candidate, entry)) { found = candidate; break; }
            }
            if (found == null) {
                found = new FixtureBuilder(entry);
                fixtures.add(found);
            } else found.add(entry);
        }

        List<LiveEvent> live = new ArrayList<>();
        for (FixtureBuilder fixture : fixtures) {
            long activeKickoff = Long.MAX_VALUE;
            for (FeedEntry entry : fixture.entries) {
                if (entry.markedLive && isActiveLive(entry.kickoffMillis, nowMillis)) {
                    activeKickoff = Math.min(activeKickoff, entry.kickoffMillis);
                }
            }
            if (activeKickoff == Long.MAX_VALUE) continue;

            List<Channel> sources = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();
            for (FeedEntry entry : fixture.entries) {
                // Do not offer an ended (red) provider or an expired/future schedule
                // as a "server" just because its team names resemble this fixture.
                boolean regularSourceFresh = isFresh(entry.kickoffMillis, nowMillis);
                boolean explicitlyLiveFresh = entry.markedLive && isActiveLive(entry.kickoffMillis, nowMillis);
                if ((!regularSourceFresh && !explicitlyLiveFresh) || isEndedMarker(entry.channel.name())) continue;
                if (!seenUrls.add(entry.channel.url())) continue;
                String name = fixture.title;
                Channel source = copyWithUrl(entry.channel, entry.channel.url(), name);
                sources.add(source);
                labels.add(feedSourceLabel(entry.channel, sources.size()));
            }
            if (sources.isEmpty()) continue;
            // Prefer a source marked currently live; within the same status prefer one
            // with a feed-provided logo so the event card does not show a blank tile.
            int preferredIndex = -1;
            int preferredScore = -1;
            for (FeedEntry entry : fixture.entries) {
                int sourceIndex = indexOfSourceUrl(sources, entry.channel.url());
                if (sourceIndex < 0) continue;
                int score = (entry.markedLive && isActiveLive(entry.kickoffMillis, nowMillis) ? 2 : 0)
                        + (entry.hasLogo() ? 1 : 0);
                if (score > preferredScore) { preferredScore = score; preferredIndex = sourceIndex; }
            }
            if (preferredIndex > 0) {
                Collections.swap(sources, 0, preferredIndex);
                Collections.swap(labels, 0, preferredIndex);
            }
            Calendar matchDate = Calendar.getInstance(FEED_TIME_ZONE, Locale.ROOT);
            matchDate.setTimeInMillis(activeKickoff);
            String dateKey = String.format(Locale.ROOT, "%04d-%02d-%02d",
                    matchDate.get(Calendar.YEAR), matchDate.get(Calendar.MONTH) + 1,
                    matchDate.get(Calendar.DAY_OF_MONTH));
            String low = fixture.homeKey.compareTo(fixture.awayKey) <= 0 ? fixture.homeKey : fixture.awayKey;
            String high = fixture.homeKey.compareTo(fixture.awayKey) <= 0 ? fixture.awayKey : fixture.homeKey;
            String stableKey = low + "_vs_" + high;
            String id = dateKey + "|" + stableKey;
            String sport = fixture.sport;
            String displayGroup = sport;
            live.add(new LiveEvent(id, fixture.title, displayGroup, sources, labels, 0, activeKickoff, sport));
        }
        live.sort(Comparator.comparingLong(LiveEvent::kickoffMillis).reversed().thenComparing(LiveEvent::title));
        return Collections.unmodifiableList(live);
    }

    private static FeedEntry parseFeedEntry(Channel channel, long nowMillis) {
        String raw = channel.name().trim();
        Matcher schedule = TIME_DATE.matcher(raw);
        if (!schedule.find()) {
            schedule = DATE_TIME.matcher(raw);
            if (!schedule.find()) return null; // no reliable event time for stale filtering
        }
        long kickoff = parseKickoff(schedule, nowMillis);
        if (kickoff <= 0L) return null;
        String afterSchedule = raw.substring(schedule.end()).trim();
        afterSchedule = removeLeadingDecorations(afterSchedule);
        String clean = stripSourceSuffix(afterSchedule);
        Matcher match = MATCH_SPLIT.matcher(clean);
        if (!match.find()) return null;
        String home = clean.substring(0, match.start()).trim();
        String away = clean.substring(match.end()).trim();
        if (home.isEmpty() || away.isEmpty()) return null;
        String homeKey = normalizeTeam(home);
        String awayKey = normalizeTeam(away);
        if (homeKey.isEmpty() || awayKey.isEmpty()) return null;
        boolean markedLive = isLiveMarker(raw);
        String sport = classifySport(raw, homeKey, awayKey);
        String title = clean.replaceAll("\\s+", " ").trim();
        return new FeedEntry(channel, title, home, away, homeKey, awayKey, kickoff, markedLive, sport);
    }

    private static String classifySport(String raw, String homeKey, String awayKey) {
        String normalized = normalizeText(raw);
        if (raw.contains("🏀") || normalized.contains("basketball")) return "Bóng rổ";
        if (raw.contains("🎾") || normalized.contains("tennis")) return "Quần vợt";
        if (raw.contains("🏒") || normalized.contains("hockey")) return "Khúc côn cầu";
        if (raw.contains("🏈") || raw.contains("🏉") || normalized.contains("rugby")
                || normalized.contains("american football")) return "Bóng bầu dục";
        if (raw.contains("⚾") || normalized.contains("baseball")) return "Bóng chày";
        if (raw.contains("🏐") || normalized.contains("volleyball")) return "Bóng chuyền";
        if (raw.contains("🥊") || normalized.contains("mma")) return "Võ thuật";
        if (raw.contains("🎮") || normalized.contains("esports")) return "Esports";

        // Some provider feeds use the same football emoji for every sport. Use
        // recognizable team names as a fallback so NBA/NHL fixtures are not
        // mislabeled as football when the feed omitted a sport marker.
        String matchup = " " + homeKey + " vs " + awayKey + " ";
        for (String team : BASKETBALL_TEAMS) if (matchup.contains(team)) return "Bóng rổ";
        for (String team : HOCKEY_TEAMS) if (matchup.contains(team)) return "Khúc côn cầu";
        return "Bóng đá";
    }

    private static long parseKickoff(Matcher schedule, long nowMillis) {
        try {
            int hour = Integer.parseInt(schedule.group("hour"));
            int minute = Integer.parseInt(schedule.group("minute"));
            int day = Integer.parseInt(schedule.group("day"));
            int month = Integer.parseInt(schedule.group("month"));
            Calendar now = Calendar.getInstance(FEED_TIME_ZONE, Locale.ROOT);
            now.setTimeInMillis(nowMillis);
            int currentYear = now.get(Calendar.YEAR);
            Integer explicitYear = null;
            if (schedule.group("year") != null) {
                int value = Integer.parseInt(schedule.group("year"));
                explicitYear = value < 100 ? (value < 70 ? 2000 + value : 1900 + value) : value;
            }
            int[] candidateYears = explicitYear == null
                    ? new int[]{currentYear, currentYear - 1, currentYear + 1}
                    : new int[]{explicitYear};
            long bestTime = 0L;
            long bestDelta = Long.MAX_VALUE;
            for (int year : candidateYears) {
                Calendar candidate = new GregorianCalendar(FEED_TIME_ZONE, Locale.ROOT);
                candidate.clear();
                candidate.setLenient(false);
                candidate.set(year, month - 1, day, hour, minute, 0);
                candidate.set(Calendar.MILLISECOND, 0);
                long candidateTime;
                try {
                    candidateTime = candidate.getTimeInMillis();
                } catch (IllegalArgumentException invalidDate) {
                    continue;
                }
                long delta = Math.abs(candidateTime - nowMillis);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    bestTime = candidateTime;
                }
            }
            return bestTime;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static boolean isFresh(long kickoff, long now) {
        long age = now - kickoff;
        return age >= -PRE_KICKOFF_TOLERANCE_MS && age <= LIVE_MAX_AGE_MS;
    }

    private static boolean isActiveLive(long kickoff, long now) {
        long age = now - kickoff;
        return age >= -PRE_KICKOFF_TOLERANCE_MS && age <= LIVE_MARKED_MAX_AGE_MS;
    }

    private static boolean isLiveMarker(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        if (raw.contains("🟢") || raw.contains("🟩")) return true;
        String normalized = normalizeText(raw);
        return LIVE_MARKER_TEXT.matcher(normalized).find();
    }

    private static boolean isEndedMarker(String raw) {
        return raw != null && (raw.contains("🔴") || raw.contains("🟥"));
    }

    private static boolean sameFixture(FixtureBuilder fixture, FeedEntry entry) {
        if (Math.abs(fixture.kickoffMillis - entry.kickoffMillis) > SOURCE_TIME_SKEW_MS) return false;
        double directHome = teamSimilarity(fixture.homeKey, entry.homeKey);
        double directAway = teamSimilarity(fixture.awayKey, entry.awayKey);
        double reverseHome = teamSimilarity(fixture.homeKey, entry.awayKey);
        double reverseAway = teamSimilarity(fixture.awayKey, entry.homeKey);
        return (directHome >= 0.68 && directAway >= 0.68)
                || (reverseHome >= 0.68 && reverseAway >= 0.68);
    }

    private static double teamSimilarity(String one, String two) {
        if (one.equals(two)) return 1.0;
        Set<String> a = new LinkedHashSet<>(Arrays.asList(one.split(" ")));
        Set<String> b = new LinkedHashSet<>(Arrays.asList(two.split(" ")));
        a.remove(""); b.remove("");
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> common = new LinkedHashSet<>(a); common.retainAll(b);
        double overlap = (double) common.size() / (a.size() + b.size() - common.size());
        // Also forgive harmless source-specific extra words such as "sporting club".
        double containment = (double) common.size() / Math.min(a.size(), b.size());
        return Math.max(overlap, containment * 0.82);
    }

    private static String normalizeTeam(String value) {
        String normalized = normalizeText(value);
        normalized = NON_WORD.matcher(normalized).replaceAll(" ").trim().replaceAll("\\s+", " ");
        normalized = TEAM_GENERIC_WORDS.matcher(normalized).replaceAll(" ").replaceAll("\s+", " ").trim();
        normalized = normalized.replaceFirst("^thanh pho\s+", "");
        if (normalized.equals("st louis city")) normalized = "st louis";
        if (normalized.equals("sport internacional")) normalized = "internacional";
        if (normalized.equals("santos sp")) normalized = "santos";
        String mapped = TEAM_ALIASES.get(normalized);
        if (mapped != null) return mapped;
        normalized = COUNTRY_STATE_SUFFIX.matcher(normalized).replaceAll(" ");
        return normalized.trim().replaceAll("\\s+", " ");
    }

    private static String removeLeadingDecorations(String value) {
        String result = value == null ? "" : value.trim();
        while (!result.isEmpty()) {
            int cp = result.codePointAt(0);
            if (Character.isLetterOrDigit(cp)) break;
            result = result.substring(Character.charCount(cp)).trim();
        }
        return result;
    }

    private static String stripSourceSuffix(String value) {
        String result = value == null ? "" : value.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            String next = result.replaceFirst("(?i)\\s*\\[[^]]*\\]\\s*$", "").trim();
            if (!next.equals(result)) { result = next; changed = true; continue; }
            next = result.replaceFirst("\\s*\\([^)]*\\)\\s*$", "").trim();
            if (!next.equals(result)) { result = next; changed = true; }
        }
        return result.replaceAll("\\s+", " ").trim();
    }

    private static String feedSourceLabel(Channel channel, int index) {
        List<String> extras = new ArrayList<>();
        String raw = channel.name();
        Matcher parenthetical = PARENTHETICAL.matcher(raw);
        while (parenthetical.find()) {
            String value = parenthetical.group(1).trim();
            String lower = normalizeText(value);
            if (!value.isEmpty() && !lower.matches("18\\+|live|truc tiep|hd|fhd|uhd|4k")
                    && !extras.contains(value)) extras.add(value);
        }
        Matcher transport = TRANSPORT_TAG.matcher(raw);
        while (transport.find()) {
            String value = transport.group(1).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (!extras.contains(value)) extras.add(value);
        }
        String provider = channel.group().trim();
        if (provider.equalsIgnoreCase("Chưa phân nhóm")) provider = "";
        StringBuilder label = new StringBuilder();
        if (!provider.isEmpty()) label.append(provider);
        for (String extra : extras) {
            if (label.length() > 0) label.append(" • ");
            label.append(extra);
        }
        return label.length() == 0 ? "Server " + index : label.toString();
    }

    private static String legacySourceLabel(Channel source, int index) {
        String value = source.group();
        if (value.isEmpty() || value.equalsIgnoreCase("Chưa phân nhóm")) return "Server " + index;
        return value + " • Server " + index;
    }

    private static int indexOfSourceUrl(List<Channel> sources, String url) {
        for (int i = 0; i < sources.size(); i++) if (sources.get(i).url().equals(url)) return i;
        return -1;
    }

    private static FeedEntry entryByUrl(List<FeedEntry> entries, String url) {
        for (FeedEntry entry : entries) if (entry.channel.url().equals(url)) return entry;
        return null;
    }

    private static String normalizeText(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT).replace('đ', 'd')
                .trim().replaceAll("\\s{2,}", " ");
    }

    /** Old M3U recognition retained for compatibility; the actual Live screen does not use it. */
    public static boolean isLiveChannel(Channel channel) {
        if (channel == null) return false;
        String group = normalizeText(channel.group());
        String name = normalizeText(channel.name());
        if (group.contains("live") || group.contains("event") || group.contains("su kien") || group.contains("sukien")) return true;
        if (name.contains("live") || name.contains("truc tiep")) return true;
        boolean sportsGroup = group.contains("the thao") || group.contains("bong da")
                || group.contains("football") || group.contains("soccer") || group.contains("sports") || group.contains("sport");
        return sportsGroup && MATCH_SPLIT.matcher(" " + name + " ").find();
    }

    public static String displayTitle(String raw) {
        String value = raw == null ? "" : raw.trim();
        value = SERVER_SUFFIX.matcher(value).replaceFirst("");
        value = SERVER_ROLE_SUFFIX.matcher(value).replaceFirst("");
        value = LIVE_TAG.matcher(value).replaceAll("");
        value = DISPLAY_RES_SUFFIX.matcher(value).replaceAll("");
        return value.trim().replaceAll("\\s{2,}", " ");
    }

    public static String normalizeTitle(String title) {
        String value = normalizeText(displayTitle(title));
        value = TITLE_NOISE_WORDS.matcher(value).replaceAll(" ");
        return NON_WORD.matcher(value).replaceAll(" ").trim().replaceAll("\\s{2,}", " ");
    }

    private static List<Channel> expandSources(Channel channel, String displayName) {
        List<Channel> result = new ArrayList<>();
        result.add(copyWithUrl(channel, channel.url(), displayName));
        Set<String> already = new LinkedHashSet<>();
        already.add(channel.url());
        for (String option : channel.options()) {
            if (option == null) continue;
            String lower = option.trim().toLowerCase(Locale.ROOT);
            String candidate = null;
            if (lower.startsWith("#nm7-alt-url:")) candidate = option.trim().substring("#nm7-alt-url:".length()).trim();
            else if (lower.startsWith("#nm7-alt-url=")) candidate = option.trim().substring("#nm7-alt-url=".length()).trim();
            if (candidate == null || candidate.isEmpty() || !M3uParser.isNetworkUrl(candidate) || !already.add(candidate)) continue;
            result.add(copyWithUrl(channel, candidate, displayName));
        }
        return result;
    }

    private static Channel copyWithUrl(Channel source, String url, String title) {
        Channel copy = new Channel(title, source.group(), url, source.tvgLogo(), source.logoUrl(),
                source.iconUrl(), source.tvgId(), source.headers());
        copy.setOriginalExtInf(source.originalExtInf());
        for (String option : source.options()) {
            if (option == null || option.trim().toLowerCase(Locale.ROOT).startsWith("#nm7-alt-url")) continue;
            copy.options().add(option);
        }
        return copy;
    }

    private static final class FeedEntry {
        final Channel channel;
        final String title, home, away, homeKey, awayKey, sport;
        final long kickoffMillis;
        final boolean markedLive;
        FeedEntry(Channel channel, String title, String home, String away,
                  String homeKey, String awayKey, long kickoffMillis, boolean markedLive, String sport) {
            this.channel = channel; this.title = title; this.home = home; this.away = away;
            this.homeKey = homeKey; this.awayKey = awayKey; this.kickoffMillis = kickoffMillis;
            this.markedLive = markedLive; this.sport = sport;
        }
        boolean hasLogo() { return !channel.effectiveLogoUrl().isEmpty(); }
    }

    private static final class FixtureBuilder {
        final List<FeedEntry> entries = new ArrayList<>();
        final String homeKey, awayKey, title, sport;
        long kickoffMillis;
        FixtureBuilder(FeedEntry first) {
            entries.add(first); homeKey = first.homeKey; awayKey = first.awayKey;
            title = first.title; sport = first.sport; kickoffMillis = first.kickoffMillis;
        }
        void add(FeedEntry entry) {
            entries.add(entry);
            if (entry.markedLive) kickoffMillis = Math.min(kickoffMillis, entry.kickoffMillis);
        }
    }

    private static final class LegacyBuilder {
        final String id, title, group;
        final List<Channel> sources = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        LegacyBuilder(String id, String title, String group) { this.id = id; this.title = title; this.group = group; }
    }
}
