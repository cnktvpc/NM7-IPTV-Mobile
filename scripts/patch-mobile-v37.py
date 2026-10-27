"""Fail-closed Mobile-only patch, shared by Windows and CI; input is pinned fork."""
from pathlib import Path
import re

phone = Path('third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui')

def replace(text, old, new):
    if text.count(old) != 1:
        raise SystemExit('Expected exactly one anchor: ' + old[:100])
    return text.replace(old, new, 1)

def method(text, signature, body):
    pattern = re.compile(r'(?ms)^    ' + re.escape(signature) + r' \{.*?^    \}')
    text, count = pattern.subn(lambda _: '    ' + signature + ' {\n' + body + '\n    }', text)
    if count != 1:
        raise SystemExit('Missing/ambiguous method: ' + signature)
    return text

p = phone / 'playback/PlaybackActivity.java'
t = p.read_text(encoding='utf-8')
t = replace(t, 'import android.content.Context;', 'import android.content.Context;\nimport android.content.Intent;')
t = replace(t, 'private static final String TAG = PlaybackActivity.class.getSimpleName();',
            'private static final String TAG = PlaybackActivity.class.getSimpleName();\n' +
            Path('scripts/smarttube-playback-mobile.java.inc').read_text(encoding='utf-8'))
t = replace(t, '        super.onCreate(savedInstanceState);', '''        super.onCreate(savedInstanceState);
        sNm7Active = this;
        if (VERSION.SDK_INT >= 33) Nm7BackApi.register(this);''')
t = replace(t, '        mExoPlayerController.setPlayer(mPlayer);\n        mPlayerView.setPlayer(mPlayer);',
            '        mExoPlayerController.setPlayer(mPlayer);\n        bindNm7PlayerTarget();')
t = replace(t, '        createPlayerObjects();', '''        // The upstream fallback switches to OkHttp only after long buffering.
        // Respect an existing custom DNS setting from the first request instead.
        if (getPlayerTweaksData().getPreferredDnsType()
                != com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData.DNS_TYPE_SYSTEM
                && !getPlayerTweaksData().isNetworkErrorFixingDisabled()) {
            getPlayerTweaksData().setPlayerDataSource(
                    com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
        }
        createPlayerObjects();''')
t = replace(t, '        mPlayerInitializer.release();', '''        cancelNm7TargetRestore(false);
        if (mNm7VideoTarget != null) mNm7VideoTarget.setPlayer(null);
        mPlayerInitializer.release();''')
t = replace(t, '        mPlaybackPresenter.onViewInitialized(); // init all controllers',
            '        mPlaybackPresenter.onViewInitialized(); // init all controllers\n        // Build the decoder/player before the Activity is shown so the selected video only waits for its stream.\n        initializePlayer();')
t = method(t, 'protected void onStart()', '''        super.onStart();
        sNm7Active = this;
        if (mPlayer == null) initializePlayer();''')
t = method(t, 'protected void onResume()', '''        super.onResume();
        mIsBackPressed = false;
        nm7SetBackground(false);
        if (mPlayer == null) initializePlayer();
        blockEngine(false);
        // Every foreground entry owns the full target, including opening another video
        // while a mini session exists (not only a tap on the mini overlay).
        mNm7LeavingForMini = false;
        completeNm7RestoreOnResume();
        mPlaybackPresenter.onViewResumed();
        if (sNm7ResumeAfterIptv && mNm7TargetRestoreWaiting && mPlayer != null) mPlayer.setPlayWhenReady(false);
        showHideWidgets(true);''')
t = method(t, 'protected void onPause()', '''        cancelNm7TargetRestore(true);
        // When Back enters NM7 mini, playback remains foreground in BrowseActivity.
        // Do not block the decoder/renderers or notify presenter of a full playback pause.
        if (sNm7Mini && !mNm7Stopped && !isFinishing() && mPlayer != null) {
            blockEngine(false);
            nm7SetBackground(false);
        } else if (!mNm7Stopped && !isFinishing() && mPlayer != null) {
            blockEngine(true);
            if (mPlayer.getPlayWhenReady()) nm7SetBackground(true);
        }
        super.onPause();
        if (!sNm7Mini) mPlaybackPresenter.onViewPaused();
        showHideWidgets(false);''')
t = method(t, 'protected void onStop()', '''        super.onStop();
        if (mNm7Stopped || isFinishing()) maybeReleasePlayer();''')
t = method(t, 'public void onUserLeaveHint()', '''        super.onUserLeaveHint();
        if (!mNm7Stopped && !isFinishing() && mPlayer != null) {
            blockEngine(true);
            if (mPlayer.getPlayWhenReady()) nm7SetBackground(true);
        }''')
t = method(t, 'public void onBackPressed()', '''        if (onDetailsBack()) return;
        if (mPlayer == null || mNm7Stopped) { super.onBackPressed(); return; }
        if (mNm7LeavingForMini) return;
        mNm7LeavingForMini = true;
        cancelNm7TargetRestore(true);
        mIsBackPressed = true;
        sNm7RestorePending = false;
        sNm7Mini = true;
        // First Back from the player always enters mini mode exactly once.
        blockEngine(false);
        Intent intent = new Intent(this,
                com.liskovsoft.smartyoutubetv2.droid.ui.browse.BrowseActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        startActivity(intent);''')
t = replace(t, '    protected void onDestroy() {\n        super.onDestroy();', '''    protected void onDestroy() {
        cancelNm7TargetRestore(false);
        if (VERSION.SDK_INT >= 33) Nm7BackApi.unregister(this);
        if (sNm7Active == this) {
            sNm7Active = null;
            sNm7Mini = false;
            nm7SetBackground(false);
            stopService(new Intent().setClassName(this, "vn.phuong.iptvplayer.BackgroundPlaybackService"));
        }
        super.onDestroy();''')
t = replace(t, '            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {', '''            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playWhenReady && playbackState == Player.STATE_READY && !mNm7Stopped && !sNm7SuspendedForIptv && !mNm7OwnsPlayback) {
                    try {
                        Class.forName("vn.phuong.iptvplayer.MobileNm7Application")
                                .getMethod("pauseIptvForYoutube").invoke(null);
                        mNm7OwnsPlayback = true;
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        android.util.Log.e("NM7Playback", "IPTV ownership handoff failed", error);
                    }
                }
                if (mNm7OwnsPlayback && (!playWhenReady || playbackState == Player.STATE_ENDED)) {
                    stopService(new Intent().setClassName(PlaybackActivity.this,
                            "vn.phuong.iptvplayer.BackgroundPlaybackService"));
                } else if (mNm7OwnsPlayback && isEngineBlocked() && !mNm7Stopped) {
                    nm7SetBackground(true);
                }''')
t = re.sub(r'    @Override\n(    public (?:float getPitch|void setPitch)\()', r'\1', t)
t = t.replace('showDetailsMessage(getString(R.string.section_is_empty));', 'showDetailsMessage("No comments available");')
p.write_text(t, encoding='utf-8')

p = phone / 'browse/BrowseActivity.java'
t = p.read_text(encoding='utf-8')
t = replace(t, 'private static final int GRID_COLUMNS = 2;', 'private static final int GRID_COLUMNS = 1;')
t = replace(t, '        toolbar.inflateMenu(R.menu.browse_toolbar);',
            '        toolbar.setTitle("SmartTube Mobile");\n        toolbar.inflateMenu(R.menu.browse_toolbar);')
t = replace(t, 'private static final String TAG = BrowseActivity.class.getSimpleName();',
            'private static final String TAG = BrowseActivity.class.getSimpleName();\n' +
            Path('scripts/smarttube-browse-mobile.java.inc').read_text(encoding='utf-8'))
t = replace(t, '        mJustCreated = false;', '        mJustCreated = false;\n        installNm7MiniPlayer();')
p.write_text(t, encoding='utf-8')

p = phone / 'search/SearchActivity.java'
t = p.read_text(encoding='utf-8')
t = replace(t, '    private void initAppBar() {', '''    @Override
    public void onBackPressed() {
        try {
            if (com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity.consumeNm7BrowseBack()) {
                return;
            }
        } catch (RuntimeException error) {
            android.util.Log.e("NM7Playback", "Search Back mini close failed", error);
        }
        super.onBackPressed();
    }

    private void initAppBar() {''')
p.write_text(t, encoding='utf-8')

p = phone / 'channeluploads/ChannelUploadsActivity.java'
t = p.read_text(encoding='utf-8').replace('new GridLayoutManager(this, GRID_COLUMNS)', 'new GridLayoutManager(this, 1)')
p.write_text(t, encoding='utf-8')

theme = Path('third_party/SmartTube-droid/smarttubedroid/src/main/res/values/themes.xml')
t = theme.read_text(encoding='utf-8')
t = t.replace('Theme.MaterialComponents.DayNight.NoActionBar', 'Theme.MaterialComponents.NoActionBar')
t = t.replace('#FF0000', '#FF7A00').replace('#CC0000', '#D96300')
t = t.replace('tools:targetApi="23">true', 'tools:targetApi="23">false')
theme.write_text(t, encoding='utf-8')

initializer = Path('third_party/SmartTube-droid/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/ExoPlayerInitializer.java')
t = initializer.read_text(encoding='utf-8')
t = replace(t, 'int bufferForPlaybackMs = 2_500;', 'int bufferForPlaybackMs = 500; // Mobile: faster first-frame threshold; retain forward buffer.')
t = replace(t, 'int bufferForPlaybackAfterRebufferMs = 5_000;', 'int bufferForPlaybackAfterRebufferMs = 1_500;')
initializer.write_text(t, encoding='utf-8')

# Keep the upstream VideoLoaderController startup path unchanged.
# A previous speculative format-info prefetch could race engine initialization and
# leave PlaybackActivity showing an endless spinner when the result arrived early.

print('NM7 Mobile lifecycle and native UI v39 applied to pinned phone source')

# UI overlay deliberately leaves all v39 playback/lifecycle code above unchanged.
import runpy
runpy.run_path("scripts/patch-mobile-ui.py")

# Mobile playback uses SurfaceView for stable decoder-to-surface rendering across mini/fullscreen handoff.
p = Path('third_party/SmartTube-droid/smarttubedroid/src/main/res/layout/playback_activity.xml')
t = p.read_text()
if 'app:surface_type="surface_view"' not in t:
    t = replace(t, 'app:surface_type="texture_view"', 'app:surface_type="surface_view"')
p.write_text(t)
runpy.run_path("scripts/patch-mobile-v55-ui.py")
runpy.run_path("scripts/patch-mobile-v56.py")
runpy.run_path("scripts/patch-mobile-v57.py")
runpy.run_path("scripts/patch-mobile-v58.py")
runpy.run_path("scripts/patch-mobile-v59.py")
runpy.run_path("scripts/patch-mobile-v60.py")
runpy.run_path("scripts/patch-mobile-v61.py")
runpy.run_path("scripts/patch-mobile-v62.py")
runpy.run_path("scripts/patch-mobile-v67.py")
runpy.run_path("scripts/patch-mobile-v68.py")
runpy.run_path("scripts/patch-mobile-v69-no-miniplayer.py")

runpy.run_path("scripts/patch-mobile-v73.py")
runpy.run_path("scripts/patch-mobile-v74.py")
runpy.run_path("scripts/patch-mobile-v75.py")
runpy.run_path("scripts/patch-mobile-v76.py")
runpy.run_path("scripts/patch-mobile-v77.py")
runpy.run_path("scripts/patch-mobile-v78.py")

# 1.10.80 emergency YouTube playback recovery:
# keep the stable 1.10.75 PlaybackActivity system-bar path. The v77
# WindowInsetsController override changed window behavior and the resulting
# build was observed to load the YouTube feed without reliably entering playback.
p = phone / 'playback/PlaybackActivity.java'
t = p.read_text(encoding='utf-8')
t = t.replace('import android.view.WindowInsetsController;\n', '')
# 1.10.80 emergency YouTube playback recovery:
# Restore the exact stable system-bar method without introducing WindowInsetsController.
p = phone / 'playback/PlaybackActivity.java'
t = p.read_text(encoding='utf-8')
t = t.replace('import android.view.WindowInsetsController;\\n', '')
sig = '    private void applySystemUi(boolean fullscreen) {'
start = t.find(sig)
if start < 0:
    raise SystemExit('v80: applySystemUi method not found')
brace = t.find('{', start)
depth = 0
end = -1
for i in range(brace, len(t)):
    if t[i] == '{':
        depth += 1
    elif t[i] == '}':
        depth -= 1
        if depth == 0:
            end = i + 1
            break
if end < 0:
    raise SystemExit('v80: applySystemUi closing brace not found')
replacement = '''    private void applySystemUi(boolean fullscreen) {
        setNavigationBarVisible(!fullscreen);
    }'''
t = t[:start] + replacement + t[end:]
p.write_text(t, encoding='utf-8')

# Do not auto-start the live-chat controller during normal YouTube playback.
# The chat panel remains compiled, but playback must not depend on chat setup.
chat = Path('third_party/SmartTube-droid/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ChatController.java')
t = chat.read_text(encoding='utf-8')
hook = '''        // NM7 Mobile: show the existing SmartTube live-chat stream automatically
        // when a video exposes a liveChatKey. The service remains receive-only.
        if (mLiveChatKey != null && "true".equals(System.getProperty("nm7.mobile.livechat"))) {
            getPlayerData().setLiveChatEnabled(true);
        }

'''
if t.count(hook) == 1:
    t=t.replace(hook,'')
chat.write_text(t, encoding='utf-8')

print('NM7 1.10.80: restore stable YouTube PlaybackActivity system UI and remove automatic chat startup')



# Do not auto-start the live-chat controller during normal YouTube playback.
# The chat panel remains compiled, but playback must not depend on chat setup.
chat = Path('third_party/SmartTube-droid/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ChatController.java')
t = chat.read_text(encoding='utf-8')
hook = '''        // NM7 Mobile: show the existing SmartTube live-chat stream automatically
        // when a video exposes a liveChatKey. The service remains receive-only.
        if (mLiveChatKey != null && "true".equals(System.getProperty("nm7.mobile.livechat"))) {
            getPlayerData().setLiveChatEnabled(true);
        }

'''
if t.count(hook) == 1:
    t=t.replace(hook,'')
chat.write_text(t, encoding='utf-8')

print('NM7 1.10.80: restore stable YouTube PlaybackActivity system UI and remove automatic chat startup')


# NM7 1.10.83 final Mobile YouTube UI/chat/performance patch.
runpy.run_path("scripts/patch-mobile-v83.py")

# NM7 1.10.84: status bar + live-chat send + avatar parser + poster-first loading.
runpy.run_path("scripts/patch-mobile-v84.py")

# NM7 1.10.85: device-verified status/chat/avatar corrections.
runpy.run_path("scripts/patch-mobile-v85.py")

# NM7 1.10.86: status bar + avatar metadata fallback + poster overlay.
runpy.run_path("scripts/patch-mobile-v86.py")

# NM7 1.10.87: hard status bar + non-sticky poster lifecycle.
runpy.run_path("scripts/patch-mobile-v87.py")

# NM7 1.10.88: decoder-backed poster lifecycle; status/avatar remain unchanged.
runpy.run_path("scripts/patch-mobile-v88.py")

# NM7 1.10.89: gate late Glide poster + keep final IPTV channel above tabs.
runpy.run_path("scripts/patch-mobile-v89.py")

# NM7 1.10.90: clicked-card thumbnail handoff only.
runpy.run_path("scripts/patch-mobile-v90.py")

# NM7 1.10.91: smooth YouTube open + sharper handoff + feed priority.
runpy.run_path("scripts/patch-mobile-v91.py")

# NM7 1.10.95: match the reference video with native PlayerView shutter/reset.
# Keep 1.10.91/1.10.94 transport and playback logic unchanged.
runpy.run_path("scripts/patch-mobile-v94.py")
runpy.run_path("scripts/patch-mobile-v95.py")
runpy.run_path("scripts/patch-mobile-v96.py")
runpy.run_path("scripts/patch-mobile-v97.py")


# NM7 1.10.101: performance-only improvements built on the user-confirmed 1.10.97 baseline.
runpy.run_path("scripts/patch-mobile-performance.py")

# NM7 1.10.104: rollback unsafe v103 render/cache changes and apply conservative performance tuning.
runpy.run_path("scripts/patch-mobile-v103.py")
runpy.run_path("scripts/patch-mobile-v104.py")

# NM7 1.10.105: targeted YouTube network/Browse/4K performance pass.
runpy.run_path("scripts/patch-mobile-v105.py")


# NM7 1.10.106: preserve YouTube player across HOME/background and add measured 4K recovery.
runpy.run_path("scripts/patch-mobile-v106.py")
