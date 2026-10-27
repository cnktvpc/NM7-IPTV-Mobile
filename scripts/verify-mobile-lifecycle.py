"""Fail-closed structural verifier for the Mobile YouTube playback lifecycle.

Runtime playback is still device-tested separately. This verifier checks that the
Mobile build no longer installs or preserves an embedded YouTube mini-player and
that the normal YouTube/IPTV bottom navigation remains intact.
"""
from pathlib import Path
import re
import json
import hashlib

root = Path('third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui')
play = (root / 'playback/PlaybackActivity.java').read_text(encoding='utf-8')
browse = (root / 'browse/BrowseActivity.java').read_text(encoding='utf-8')
search = (root / 'search/SearchActivity.java').read_text(encoding='utf-8')
app = Path('app/src/main/java/vn/phuong/iptvplayer')
application = (app / 'MobileNm7Application.java').read_text(encoding='utf-8')
tabs = (app / 'HomeTabBar.java').read_text(encoding='utf-8')
gradle = Path('app/build.gradle.kts').read_text(encoding='utf-8')
patch = Path('scripts/patch-mobile-v37.py').read_text(encoding='utf-8')
final_delta = Path('scripts/patch-mobile-v69-no-miniplayer.py').read_text(encoding='utf-8')

def body(signature):
    # SmartTube may use public/protected and annotations differently across
    # generated source revisions. Match the method by name, not visibility.
    name = re.escape(signature.split()[-1].replace('()', ''))
    match = re.search(r'(?ms)\b' + name + r'\s*\([^)]*\)\s*\{(.*?)(?=^\s*(?:public|protected|private|@Override)\s+|\Z)', play)
    assert match, signature
    return match.group(1)

checks = 0
def check(condition, label):
    global checks
    assert condition, label
    checks += 1
    print('PASS', label)

# Core lifecycle / playback ownership.
check('vn.phuongnm7.iptvplayer' not in play, 'SmartTube bridge uses the actual Mobile Java package')
check('Build the decoder/player before the Activity is shown' in play, 'Player initialization remains before first-frame wait')
check('if (mPlayer == null) initializePlayer();' in body('protected void onStart()'), 'Foreground reuses/rebuilds the player safely')
check('pauseIptvForYoutube' not in body('protected void onStart()'), 'Activity start does not prematurely release IPTV')
check('playWhenReady && playbackState == Player.STATE_READY' in play, 'IPTV handoff is gated by actual YouTube READY/play intent')
check('MobileInlinePlayerProviderV2.releaseForYoutube();' in application, 'YouTube handoff releases the IPTV inline owner')
check('prefetchNm7FormatInfo(item);' not in play and 'mNm7PrefetchedFormatInfo' not in play, 'No speculative format prefetch remains in PlaybackActivity')
check('mPlayerView' in play and 'PlayerView' in play, 'Mobile playback keeps the PlayerView rendering path')
check('mNm7ObservedPlayer' in play and 'removeVideoListener' in play, 'Decoder/render observers are tied to the current player instance')
check('mPlayer.retry()' in play, 'Decoder/source recovery retains bounded retry support')
check('scripts/patch-mobile-v37.py' in Path('scripts/build-mobile-windows.ps1').read_text(), 'Windows build uses the shared Mobile patch')
check('scripts/patch-mobile-v37.py' in Path('.github/workflows/android-mobile-final.yml').read_text(), 'CI uses the shared Mobile patch')

# No embedded YouTube mini-player.
check('installNm7MiniPlayer();' not in browse, 'Browse does not install an embedded mini-player')
check('installYoutubeMiniPlayer(activity)' not in application, 'Application lifecycle does not reattach a mini-player')
check('MobileMiniPlayer.attach' not in application, 'Application does not create mini-player surfaces')
check('MobileMiniPlayer.remove' not in application, 'Application does not manage mini-player overlays')
check('consumeNm7BrowseBack()' not in search, 'Search uses normal Back without mini-player interception')
check('mNm7LeavingForMini = false;' in final_delta and 'sNm7Mini = false;' in final_delta, 'Mobile playback Back patch clears legacy mini-player state')
check('startActivity(intent);' in final_delta and 'finish();' in final_delta, 'Mobile playback Back patch returns to Browse')

# Bottom navigation.
check('"YouTube"' in tabs and '"IPTV"' in tabs, 'Bottom navigation contains YouTube and IPTV')
check('"Thư viện"' not in tabs and '"Cài đặt"' not in tabs, 'Bottom navigation has no extra YouTube tabs')
check('addItem(activity, bar, R.drawable.nm7_nav_youtube' in tabs, 'YouTube tab is present')
check('addItem(activity, bar, R.drawable.nm7_nav_iptv' in tabs, 'IPTV tab is present')
check('stopYoutubeForIptv();' not in tabs, 'IPTV tab does not close the YouTube owner')
check('SharedPlaybackSession.setTab(activity, SharedPlaybackSession.TAB_IPTV);' in tabs, 'IPTV tab switches the shared product tab')
check('SharedPlaybackSession.setTab(activity, SharedPlaybackSession.TAB_YOUTUBE);' in tabs, 'YouTube tab switches the shared product tab')
check(tabs.count('addItem(activity, bar,') == 2, 'Exactly two bottom navigation items are created')

# Browse/loading.
check('HomeTabBar.attach(activity, true);' in application, 'YouTube Browse receives the bottom tab bar')
check('HomeTabBar.attach(activity, false);' in application, 'IPTV MainActivity receives the bottom tab bar')
check('GRID_COLUMNS = 1' in browse, 'YouTube recommendations remain single-column')
check('getCardImageUrl()' in Path('scripts/patch-mobile-ui.py').read_text(), 'YouTube thumbnail card image loading remains enabled')
check('patch-mobile-v73.py' in patch and 'runpy.run_path("scripts/patch-mobile-v73.py")' in patch, 'YouTube loading optimization patch is part of the Mobile build chain')
check('patch-mobile-v74.py' in patch and 'runpy.run_path("scripts/patch-mobile-v74.py")' in patch, 'v74 playback/fullscreen optimization patch is part of the Mobile build chain')
check('patch-mobile-v75.py' in patch and 'runpy.run_path("scripts/patch-mobile-v75.py")' in patch, 'v75 fast playback format patch is part of the Mobile build chain')
check('setPlayerDataSource' not in play, 'Mobile playback does not force the YouTube transport to OkHttp')
check('maxresdefault.jpg' in Path('scripts/patch-mobile-v73.py').read_text(), 'YouTube Browse cards preserve max-resolution thumbnail target')
check('mqdefault.jpg' not in Path('scripts/patch-mobile-v73.py').read_text(), 'YouTube Browse optimization does not downgrade thumbnail quality')

# Version.
check('versionCode = 122' in gradle, 'Mobile versionCode is 120 for Mobile 1.10.104 build')
check('versionName = "1.10.106"' in gradle, 'Mobile versionName is 1.10.104')

# Stable playback patches.
for ver, needles, label in [
    ('v75', ['TV_CLIENT','getFastPlaybackFormatInfo'], 'v75 fast format resolver'),
    ('v76', ['nm7_live_chat_panel','mNm7LiveChatAdapter'], 'v76 live chat/status-bar'),
    ('v77', ['nm7_live_chat_close','setDecorFitsSystemWindows(true)'], 'v77 status-bar/chat'),
    ('v84', ['channelThumbnail.thumbnails[0].url','onRenderedFirstFrame','sendLiveChatMessageObserve'], 'v84 playback/chat/avatar/poster'),
    ('v85', ['android:paddingBottom="64dp"','String nm7ChannelThumbnail = item.getChannelThumbnail();'], 'v85 status/chat/avatar'),
    ('v86', ['nm7_startup_poster','mNm7FirstFrameRendered = true;','NM7_AVATAR_CACHE'], 'v86 status/avatar/poster'),
    ('v87', ['FLAG_FORCE_NOT_FULLSCREEN','onWindowFocusChanged(boolean hasFocus)','nm7ArmPosterReadyFallback'], 'v87 hard status/poster'),
    ('v88', ['renderedOutputBufferCount','absolute_safety_timeout'], 'v88 decoder-backed poster'),
    ('v89', ['mNm7PosterVideoId','dontAnimate()'], 'v89 late-poster'),
    ('v90', ['consumeNm7TransitionPoster','startup_poster_source=clicked_card'], 'v90 clicked-card handoff'),
]:
    source = Path(f'scripts/patch-mobile-{ver}.py').read_text()
    for needle in needles:
        check(needle in source, f'{label}: {needle}')

check('runpy.run_path("scripts/patch-mobile-v91.py")' in patch, 'v91 smooth-open optimization is in the Mobile chain')
check('decoder_moving_frames' in Path('scripts/patch-mobile-v91.py').read_text(), 'v91 waits for moving decoder frames')
check('runpy.run_path("scripts/patch-mobile-v94.py")' in patch, 'v94 playback-position poster gate is in the Mobile chain')
check('runpy.run_path("scripts/patch-mobile-v95.py")' in patch, 'v95 reference-style shutter/loading handoff is in the Mobile chain')
check('keep_content_on_player_reset="false"' in Path('scripts/patch-mobile-v95.py').read_text(), 'v95 disables stale SurfaceView content on reset')
check('shutter_background_color="@android:color/black"' in Path('scripts/patch-mobile-v95.py').read_text(), 'v95 uses a black native PlayerView shutter')
check('android.graphics.Bitmap nm7TransitionPoster = null;' in Path('scripts/patch-mobile-v95.py').read_text(), 'v95 disables the early clicked-card poster')
check('mProgressBar.setVisibility(View.GONE)' in Path('scripts/patch-mobile-v96.py').read_text(), 'v96 removes the indeterminate startup spinner')
check('bufferForPlaybackMs = 150' in Path('scripts/patch-mobile-v96.py').read_text(), 'v96 lowers the initial playback threshold to 150ms')
check('runpy.run_path(\"scripts/patch-mobile-v96.py\")' in patch, 'v96 startup performance patch is in the Mobile chain')
check('runpy.run_path(\"scripts/patch-mobile-v97.py\")' in patch, 'v97 hard spinner suppression patch is in the Mobile chain')
check('public void showProgressBar(boolean show)' in Path('scripts/patch-mobile-v97.py').read_text(), 'v97 hard-disables the SmartTube progress callback')
check('mProgressBar.setVisibility(View.GONE)' in Path('scripts/patch-mobile-v97.py').read_text(), 'v97 forces the progress indicator hidden')
check('runpy.run_path("scripts/patch-mobile-v92.py")' not in patch and 'runpy.run_path("scripts/patch-mobile-v93.py")' not in patch, 'experimental v92/v93 patches are excluded from the Mobile chain')
check('mNm7FirstFramePositionMs' in Path('scripts/patch-mobile-v94.py').read_text(), 'v94 gates poster handoff on playback position')
check('playbackProgressMs >= 120L' in Path('scripts/patch-mobile-v94.py').read_text(), 'v94 requires playback position to advance')
check('BitmapDrawable' in Path('scripts/patch-mobile-v91.py').read_text(), 'v91 reuses original decoded thumbnail bitmap')
check('delaySubscription(650' in Path('scripts/patch-mobile-v91.py').read_text(), 'v91 source records the original deferred avatar fallback')
check('delaySubscription(650' not in Path('third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/shared/VideoCardHolder.java').read_text(), 'v102 removes the 650ms avatar delay from final runtime')
check('Schedulers.io()' in Path('scripts/patch-mobile-performance.py').read_text(), 'v102 avatar fallback uses the I/O scheduler')
check('FLAG_ACTIVITY_NO_ANIMATION' in Path('scripts/patch-mobile-v91.py').read_text(), 'v91 suppresses playback activity transition flash')
check('rendered >= mNm7PosterRenderedBaseline + 3' in Path('scripts/patch-mobile-v91.py').read_text(), 'v91 uses the tested +3 moving-frame threshold')
check('elapsed >= 180L' in Path('scripts/patch-mobile-v91.py').read_text(), 'v91 requires the tested startup stability window')
check('bufferForPlaybackMs = 250' in Path('scripts/patch-mobile-v91.py').read_text(), 'v91 baseline retains the 250ms startup buffer before v96 override')
check('runpy.run_path("scripts/patch-mobile-v103.py")' in patch, 'v103 source remains available in the Mobile chain')
check('runpy.run_path("scripts/patch-mobile-v104.py")' in patch, 'v104 safe performance rollback is in the Mobile chain')
check('runpy.run_path("scripts/patch-mobile-v105.py")' in patch, 'v105 targeted performance patch is in the Mobile chain')
check('mNm7GridCache' in Path('scripts/patch-mobile-v103.py').read_text(), 'v103 grid cache source is retained only for rollback history')
check('mNm7HighFps4k' in Path('scripts/patch-mobile-v103.py').read_text(), 'v103 4K SurfaceView source is retained only for rollback history')
check('mNm74kDropWatchdog' in Path('scripts/patch-mobile-v103.py').read_text(), 'v103 4K watchdog source is retained only for rollback history')
check('mNm7GridCache' not in browse, 'v104 removes the v103 Browse cache from final runtime')
check('mNm7HighFps4k' not in Path('third_party/SmartTube-droid/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/versions/renderer/DebugInfoMediaCodecVideoRenderer.java').read_text(), 'v104 restores decoder timestamp pacing')
check('mNm74kDropWatchdog' not in play, 'v104 removes the v103 4K drop watchdog')
check('setMaxVideoFrameRate(30)' not in play and 'setMaxVideoBitrate(24_000_000)' not in play, 'v105 removes the v104 4K hard cap')
check('setItemViewCacheSize(12)' in browse and 'setItemAnimator(null)' in browse, 'v105 reduces Browse RecyclerView work')
check('NM7_FORMAT_REUSE_MS' in Path('third_party/SmartTube-droid/MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/YouTubeMediaItemService.java').read_text(), 'v105 adds short process-local format reuse')


Path('dist/mobile-diagnostics').mkdir(parents=True, exist_ok=True)
Path('dist/mobile-diagnostics/lifecycle-source-proof.json').write_text(json.dumps({
    'structural_checks': checks,
    'runtime_verified': False,
    'mini_player': False,
    'bottom_tabs': ['YouTube', 'IPTV'],
    'playback_sha256': hashlib.sha256(play.encode()).hexdigest(),
    'browse_sha256': hashlib.sha256(browse.encode()).hexdigest(),
}, indent=2))
print(f'{checks} structural checks passed; device runtime not verified')


# v106 persistence/recovery guards
check('runpy.run_path("scripts/patch-mobile-v106.py")' in patch, 'v106 playback persistence/recovery patch is in the Mobile chain')
check('mNm7Backgrounding' in play, 'v106 tracks normal HOME/background transitions')
check('if (!sNm7Mini && !mNm7Backgrounding) mPlaybackPresenter.onViewPaused();' in play, 'v106 keeps presenter alive across HOME/background')
check('mNm74kRecoveryWatchdog' not in play, 'v106 does not install an unmeasured 4K fallback')
check('NM7_FORMAT_REUSE_MS = 60_000L' in Path('third_party/SmartTube-droid/MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/YouTubeMediaItemService.java').read_text(), 'v106 extends in-memory format reuse for background reopen')
