#!/usr/bin/env python3
"""NM7 Mobile 1.10.114: YouTube-style browse chrome and real portrait status-bar inset.

This is intentionally UI-only. Playback, IPTV, YouTube networking, avatar, spinner,
live-chat and lifecycle code are left untouched from 1.10.113.
"""
from pathlib import Path
import re
import shutil

ROOT = Path("third_party/SmartTube-droid")
LAYOUT_SRC = Path("scripts/mobile-ui/res/layout/browse_activity.xml")
LAYOUT_DST = ROOT / "smarttubedroid/src/main/res/layout/browse_activity.xml"
BROWSE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"

if not LAYOUT_SRC.is_file() or not BROWSE.is_file():
    raise SystemExit("v114: expected Mobile browse layout/source is missing")

# v37 already copies the layout, but recopy it here so v114 is self-contained and
# cannot accidentally build with an older generated layout.
shutil.copyfile(LAYOUT_SRC, LAYOUT_DST)

s = BROWSE.read_text(encoding="utf-8")

create_anchor = """        setContentView(R.layout.browse_activity);

        mBrowsePresenter = BrowsePresenter.instance(this);
"""
create_repl = """        setContentView(R.layout.browse_activity);
        installNm7YoutubeChrome();

        mBrowsePresenter = BrowsePresenter.instance(this);
"""
if "installNm7YoutubeChrome();" not in s:
    if s.count(create_anchor) != 1:
        raise SystemExit("v114: BrowseActivity onCreate anchor missing")
    s = s.replace(create_anchor, create_repl, 1)

init_anchor = """    // ------------------------------------------------------------------ init

    private void initToolbar() {
"""
init_repl = """    // ------------------------------------------------------------------ NM7 YouTube chrome

    private void installNm7YoutubeChrome() {
        View explore = findViewById(R.id.nm7_explore);
        if (explore != null) {
            explore.setOnClickListener(v -> {
                if (mTabLayout != null && mTabLayout.getTabCount() > 0) {
                    selectSection(0, false);
                }
            });
        }

        // The reference YouTube create button has no equivalent write/upload feature in
        // SmartTube Mobile. Keep the visual affordance without repurposing it for a
        // different action; existing search/account access remains unchanged elsewhere.
        View create = findViewById(R.id.nm7_video);
        if (create != null) {
            create.setOnClickListener(null);
            create.setFocusable(true);
        }

        applyNm7PortraitSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void applyNm7PortraitSystemBars() {
        if (isLandscape()) {
            return;
        }

        android.view.Window window = getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(android.graphics.Color.WHITE);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.show(android.view.WindowInsets.Type.statusBars());
                controller.setSystemBarsAppearance(
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            int flags = window.getDecorView().getSystemUiVisibility();
            flags &= ~(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }

        final View appBar = findViewById(R.id.browse_app_bar);
        if (appBar != null) {
            appBar.setOnApplyWindowInsetsListener((v, insets) -> {
                int top;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    top = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                } else {
                    top = insets.getSystemWindowInsetTop();
                }
                v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
            appBar.requestApplyInsets();
        }
    }

    @Override
    protected void applySystemBars() {
        super.applySystemBars();
        applyNm7PortraitSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isLandscape()) {
            applyNm7PortraitSystemBars();
        }
    }

    // ------------------------------------------------------------------ init

    private void initToolbar() {
"""
if "private void installNm7YoutubeChrome()" not in s:
    if s.count(init_anchor) != 1:
        raise SystemExit("v114: init anchor missing")
    s = s.replace(init_anchor, init_repl, 1)

BROWSE.write_text(s, encoding="utf-8")
print("NM7 Mobile 1.10.114 YouTube-style chrome + portrait status-bar inset applied")
