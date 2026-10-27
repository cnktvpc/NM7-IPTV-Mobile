import sys

# 1. Update MobileNm7Application.java
p_app = r'D:\NM7 build app\NM7 IPTV Mobile\NM7-IPTV-stable-mobile-1.10.145\app\src\main\java\vn\phuong\iptvplayer\MobileNm7Application.java'
with open(p_app, 'r', encoding='utf-8') as f:
    c = f.read()

target = """    /**
     * IPTV has started real playback. Pause/detach YouTube but preserve its player session"""

rep = """    public static void enterYoutubeMiniPlayer() {
        try {
            Class.forName(SMARTTUBE_PLAYBACK).getMethod("enterNm7MiniPlayer").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w("NM7Playback", "enterYoutubeMiniPlayer failed", error);
        }
    }

    /**
     * IPTV has started real playback. Pause/detach YouTube but preserve its player session"""

if target in c and "enterYoutubeMiniPlayer" not in c:
    c = c.replace(target, rep, 1)
    with open(p_app, 'w', encoding='utf-8') as f:
        f.write(c)
    print("MobileNm7Application updated!")
else:
    print("MobileNm7Application already updated or target not found")

# 2. Update MobileInlinePlayerProviderV2.java
p_inline = r'D:\NM7 build app\NM7 IPTV Mobile\NM7-IPTV-stable-mobile-1.10.145\app\src\main\java\vn\phuong\iptvplayer\MobileInlinePlayerProviderV2.java'
with open(p_inline, 'r', encoding='utf-8') as f:
    c = f.read()

# Add isIptvPlaybackActive()
target = """    public static boolean shouldResumeIptvAfterYoutube() {"""
rep = """    public static boolean isIptvPlaybackActive() {
        MobileInlinePlayerProviderV2 owner = instance;
        if (owner == null) return false;
        if (owner.currentChannel != null && owner.player != null) {
            int state = owner.player.getPlaybackState();
            if ((state == androidx.media3.common.Player.STATE_BUFFERING || state == androidx.media3.common.Player.STATE_READY)
                    && owner.player.getPlayWhenReady() && !owner.userPaused) {
                return true;
            }
        }
        if (owner.resumeAfterYoutube && owner.currentChannel != null) {
            return true;
        }
        return false;
    }

    public static boolean shouldResumeIptvAfterYoutube() {"""

if target in c and "isIptvPlaybackActive" not in c:
    c = c.replace(target, rep, 1)

# In playLiveChannel: dismiss mini player & suspend youtube
target = """    public static boolean playLiveChannel(Channel channel) {
        MobileInlinePlayerProviderV2 owner = instance;
        if (channel == null || owner == null || owner.currentActivity == null
                || AppPreferences.isTvInterface(owner.currentActivity)) return false;
        owner.playInline(channel);"""
rep = """    public static boolean playLiveChannel(Channel channel) {
        MobileInlinePlayerProviderV2 owner = instance;
        if (channel == null || owner == null || owner.currentActivity == null
                || AppPreferences.isTvInterface(owner.currentActivity)) return false;
        MobileMiniPlayer.remove();
        MobileNm7Application.suspendYoutubeForIptv();
        owner.playInline(channel);"""

if target in c:
    c = c.replace(target, rep, 1)

# In playInline: dismiss mini player & suspend youtube
target = """    private void playInline(Channel channel, long resumePositionMs, boolean resumePlaying) {
        if (currentActivity == null || playerView == null) return;"""
rep = """    private void playInline(Channel channel, long resumePositionMs, boolean resumePlaying) {
        if (currentActivity == null || playerView == null) return;
        MobileMiniPlayer.remove();
        MobileNm7Application.suspendYoutubeForIptv();"""

if target in c:
    c = c.replace(target, rep, 1)

with open(p_inline, 'w', encoding='utf-8') as f:
    f.write(c)
print("MobileInlinePlayerProviderV2 updated!")

# 3. Update HomeTabBar.java
p_tab = r'D:\NM7 build app\NM7 IPTV Mobile\NM7-IPTV-stable-mobile-1.10.145\app\src\main\java\vn\phuong\iptvplayer\HomeTabBar.java'
with open(p_tab, 'r', encoding='utf-8') as f:
    c = f.read()

target = """    private static void openIptv(Activity activity) {
        PlayerActivity.cancelYoutubeHandoff();
        SharedPlaybackSession.setTab(activity, SharedPlaybackSession.TAB_IPTV);
        MobileNm7Application.markTabSwitch();
        if (MobileNm7Application.isYoutubeSessionActive()) {
            MobileNm7Application.suspendYoutubeForIptv();
        }
        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startWithoutAnimation(activity, intent);
        MobileInlinePlayerProviderV2.resumeForIptvTab(activity);
    }"""

rep = """    private static void openIptv(Activity activity) {
        PlayerActivity.cancelYoutubeHandoff();
        SharedPlaybackSession.setTab(activity, SharedPlaybackSession.TAB_IPTV);
        MobileNm7Application.markTabSwitch();
        boolean hasIptvPlayback = MobileInlinePlayerProviderV2.isIptvPlaybackActive()
                || MobileNm7Application.hasIptvPlayer();
        if (hasIptvPlayback) {
            if (MobileNm7Application.isYoutubeSessionActive()) {
                MobileNm7Application.suspendYoutubeForIptv();
            }
        } else {
            if (MobileNm7Application.isYoutubeSessionActive()) {
                MobileNm7Application.enterYoutubeMiniPlayer();
            }
        }
        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startWithoutAnimation(activity, intent);
        if (hasIptvPlayback) {
            MobileInlinePlayerProviderV2.resumeForIptvTab(activity);
        }
    }"""

if target in c:
    c = c.replace(target, rep, 1)

target = """    private static void openAppSettings(Activity activity) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showSettingsFromNavigation();
            return;
        }
        // Settings are hosted by the existing MainActivity. Return to that host with
        // the same no-animation handoff used by the existing IPTV navigation.
        SharedPlaybackSession.setTab(activity, SharedPlaybackSession.TAB_IPTV);
        MobileNm7Application.markTabSwitch();
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_APP_SETTINGS, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startWithoutAnimation(activity, intent);
    }"""

rep = """    private static void openAppSettings(Activity activity) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showSettingsFromNavigation();
            return;
        }
        SharedPlaybackSession.setTab(activity, SharedPlaybackSession.TAB_IPTV);
        MobileNm7Application.markTabSwitch();
        boolean hasIptvPlayback = MobileInlinePlayerProviderV2.isIptvPlaybackActive()
                || MobileNm7Application.hasIptvPlayer();
        if (hasIptvPlayback) {
            if (MobileNm7Application.isYoutubeSessionActive()) {
                MobileNm7Application.suspendYoutubeForIptv();
            }
        } else {
            if (MobileNm7Application.isYoutubeSessionActive()) {
                MobileNm7Application.enterYoutubeMiniPlayer();
            }
        }
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_APP_SETTINGS, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startWithoutAnimation(activity, intent);
    }"""

if target in c:
    c = c.replace(target, rep, 1)

# In openBrowse, if clicked from MainActivity and youtube is active, restore to fullscreen
target = """    private static void openBrowse(Activity activity) {
        SharedPlaybackSession.setTab(activity, SharedPlaybackSession.TAB_YOUTUBE);
        MobileNm7Application.markTabSwitch();"""

rep = """    private static void openBrowse(Activity activity) {
        SharedPlaybackSession.setTab(activity, SharedPlaybackSession.TAB_YOUTUBE);
        MobileNm7Application.markTabSwitch();
        if (activity instanceof MainActivity) {
            try {
                Class<?> playback = Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity");
                if (Boolean.TRUE.equals(playback.getMethod("isNm7SessionActive").invoke(null))) {
                    MobileInlinePlayerProviderV2.releaseForYoutube();
                    SmartTubeRuntime.initialize(activity.getApplicationContext());
                    playback.getMethod("restoreNm7Player").invoke(null);
                    return;
                }
            } catch (Throwable ignored) {}
        }"""

if target in c:
    c = c.replace(target, rep, 1)

with open(p_tab, 'w', encoding='utf-8') as f:
    f.write(c)
print("HomeTabBar updated!")

# 4. Update MainActivity.java
p_main = r'D:\NM7 build app\NM7 IPTV Mobile\NM7-IPTV-stable-mobile-1.10.145\app\src\main\java\vn\phuong\iptvplayer\MainActivity.java'
with open(p_main, 'r', encoding='utf-8') as f:
    c = f.read()

# In onResume: attach miniplayer if IPTV is not playing
target = """        liveFeedHandler.postDelayed(liveFeedRefresh, LIVE_FEED_REFRESH_MS);
    }"""
rep = """        liveFeedHandler.postDelayed(liveFeedRefresh, LIVE_FEED_REFRESH_MS);
        if (!MobileInlinePlayerProviderV2.isIptvPlaybackActive() && !MobileNm7Application.hasIptvPlayer()) {
            MobileMiniPlayer.attach(this);
        } else {
            MobileMiniPlayer.remove();
        }
    }"""

if target in c and "MobileMiniPlayer.attach(this)" not in c:
    c = c.replace(target, rep, 1)

# In onDestroy: remove miniplayer
target = """        searchHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }"""
rep = """        searchHandler.removeCallbacksAndMessages(null);
        MobileMiniPlayer.remove();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        try {
            if (Boolean.TRUE.equals(Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity")
                    .getMethod("consumeNm7BrowseBack").invoke(null))) {
                return;
            }
        } catch (Throwable ignored) {}
        super.onBackPressed();
    }"""

if target in c and "consumeNm7BrowseBack" not in c:
    c = c.replace(target, rep, 1)

# In play(Channel c, LiveEvent liveEvent)
target = """    private void play(Channel c,LiveEvent liveEvent){
        SharedPlaybackSession.setTab(this,SharedPlaybackSession.TAB_IPTV);"""
rep = """    private void play(Channel c,LiveEvent liveEvent){
        MobileMiniPlayer.remove();
        MobileNm7Application.suspendYoutubeForIptv();
        SharedPlaybackSession.setTab(this,SharedPlaybackSession.TAB_IPTV);"""

if target in c:
    c = c.replace(target, rep, 1)

# In playLiveEvent(LiveEvent event)
target = """    private void playLiveEvent(LiveEvent event){
        if(event==null||event.sources().isEmpty())return;
        LiveEvent selectedEvent=event.withPreferredUrl(LiveEventStore.selectedUrl(this,event.id()));
        LivePlaybackSession.setActive(selectedEvent);
        if(liveAdapter!=null)liveAdapter.setPlayingEvent(selectedEvent.id());
        Channel selected=selectedEvent.primary();
        if(!MobileInlinePlayerProviderV2.playLiveChannel(selected))play(selected,selectedEvent);
    }"""
rep = """    private void playLiveEvent(LiveEvent event){
        if(event==null||event.sources().isEmpty())return;
        LiveEvent selectedEvent=event.withPreferredUrl(LiveEventStore.selectedUrl(this,event.id()));
        LivePlaybackSession.setActive(selectedEvent);
        if(liveAdapter!=null)liveAdapter.setPlayingEvent(selectedEvent.id());
        Channel selected=selectedEvent.primary();
        MobileMiniPlayer.remove();
        MobileNm7Application.suspendYoutubeForIptv();
        if(!MobileInlinePlayerProviderV2.playLiveChannel(selected))play(selected,selectedEvent);
    }"""

if target in c:
    c = c.replace(target, rep, 1)

with open(p_main, 'w', encoding='utf-8') as f:
    f.write(c)
print("MainActivity updated!")
