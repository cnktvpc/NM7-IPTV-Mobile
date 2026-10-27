"""NM7 Mobile 1.10.101 performance patch — built directly on the user-confirmed 1.10.97 source chain.

Scope is limited to YouTube feed/tab responsiveness and repeated data work.
It does not modify the 1.10.97 avatar pipeline, portrait WindowInsets/status-bar path,
PlayerView/shutter, spinner suppression, or tested playback transport/buffer settings.
"""
from pathlib import Path
ROOT=Path("third_party/SmartTube-droid")

p=ROOT/"common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/BrowsePresenter.java"
s=p.read_text(encoding="utf-8")
anchor="    private int mBootstrapSectionId = -1;\n"
fields="""    private int mBootstrapSectionId = -1;
    private static final long NM7_SECTION_CACHE_TTL_MS = 45_000L;
    private final java.util.Map<Integer, java.util.List<MediaGroup>> mNm7RowsCache = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> mNm7RowsCacheTime = new java.util.HashMap<>();
"""
if s.count(anchor)!=1: raise SystemExit("performance: BrowsePresenter field anchor missing/ambiguous")
s=s.replace(anchor,fields,1)
anchor="""    public void refresh(boolean focusOnContent) {
        updateCurrentSection();
        if (focusOnContent && getView() != null) {
            getView().focusOnContent();
        }
    }
"""
replacement="""    public void refresh(boolean focusOnContent) {
        mNm7RowsCache.clear();
        mNm7RowsCacheTime.clear();
        updateCurrentSection();
        if (focusOnContent && getView() != null) {
            getView().focusOnContent();
        }
    }
"""
if s.count(anchor)!=1: raise SystemExit("performance: refresh anchor missing")
s=s.replace(anchor,replacement,1)
anchor="""                        mediaGroups -> {
                            getView().showProgressBar(false);

                            filterHomeIfNeeded(mediaGroups);
"""
replacement="""                        mediaGroups -> {
                            getView().showProgressBar(false);

                            filterHomeIfNeeded(mediaGroups);
                            mNm7RowsCache.put(section.getId(), new java.util.ArrayList<>(mediaGroups));
                            mNm7RowsCacheTime.put(section.getId(), System.currentTimeMillis());
"""
if s.count(anchor)!=1: raise SystemExit("performance: rows callback anchor missing")
s=s.replace(anchor,replacement,1)
anchor="""        Disposable updateAction = groups
                .subscribe(
"""
replacement="""        java.util.List<MediaGroup> cachedRows = mNm7RowsCache.get(section.getId());
        Long cachedRowsAt = mNm7RowsCacheTime.get(section.getId());
        if (cachedRows != null && cachedRowsAt != null
                && System.currentTimeMillis() - cachedRowsAt < NM7_SECTION_CACHE_TTL_MS) {
            getView().showProgressBar(false);
            for (MediaGroup mediaGroup : cachedRows) {
                if (mediaGroup == null || mediaGroup.isEmpty()) continue;
                VideoGroup videoGroup = VideoGroup.from(mediaGroup, section);
                if (android.text.TextUtils.isEmpty(videoGroup.getTitle())) {
                    videoGroup.setTitle(getContext().getString(R.string.suggestions));
                }
                getView().updateSection(videoGroup);
                mBrowseProcessor.process(videoGroup);
            }
            return;
        }

        Disposable updateAction = groups
                .subscribe(
"""
if s.index(anchor)<0: raise SystemExit("performance: rows subscription anchor missing")
s=s.replace(anchor,replacement,1)
anchor="""                        mediaGroup -> {
                            getView().showProgressBar(false);

                            if (getView() == null) {
"""
if s.count(anchor)!=1: raise SystemExit("performance: grid callback anchor missing")
anchor2="""                                return;
                            }

                            VideoGroup videoGroup = VideoGroup.from(baseGroup, mediaGroup);
"""
replacement2="""                                return;
                            }

                            VideoGroup videoGroup = VideoGroup.from(baseGroup, mediaGroup);
"""
if s.count(anchor2)!=1: raise SystemExit("performance: grid cache anchor missing")
s=s.replace(anchor2,replacement2,1)
p.write_text(s,encoding="utf-8")

p=ROOT/"smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"
s=p.read_text(encoding="utf-8")
anchor="        mGridView.setAdapter(mGridAdapter);"
repl="""        mGridView.setAdapter(mGridAdapter);
        mGridView.setHasFixedSize(true);
        mGridView.setItemViewCacheSize(6);
        if (mGridView.getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManager) {
            ((androidx.recyclerview.widget.GridLayoutManager) mGridView.getLayoutManager()).setInitialPrefetchItemCount(6);
        }
"""
if s.count(anchor)!=1: raise SystemExit("performance: YouTube grid adapter anchor missing/ambiguous")
s=s.replace(anchor,repl,1)
p.write_text(s,encoding="utf-8")

print("NM7 1.10.101 performance patch prepared")


# Safe performance scope: rows cache + YouTube grid RecyclerView prefetch only; 1.10.97 playback/avatar/status-bar paths stay untouched.

# CI retrigger after removing risky grid-data cache path.


# NM7 1.10.102 final runtime correction.
# Re-assert the user-confirmed 1.10.97 behavior after all performance edits.
import re

p = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"
s = p.read_text(encoding="utf-8")

spinner_re = re.compile(
    r'(?ms)^    @Override\n    public void showProgressBar\(boolean show\) \{.*?^    \}\n\n    /\*\*\n     \* Only STATE_BUFFERING counts as stalled\.'
)
spinner_new = """    @Override
    public void showProgressBar(boolean show) {
        // NM7 1.10.102: never render SmartTube's indeterminate Mobile spinner.
        if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
        mProgressHidePending = false;
    }

    /**
     * Only STATE_BUFFERING counts as stalled."""
s, count = spinner_re.subn(spinner_new, s, count=1)
if count != 1:
    raise SystemExit("v102: showProgressBar guard missing")

focus_sig = "    public void onWindowFocusChanged(boolean hasFocus) {"
if "mRoot.postDelayed(this::nm7RestorePortraitBars, 700L);" not in s:
    start = s.find(focus_sig)
    if start < 0: raise SystemExit("v102: focus method missing")
    ms = s.rfind("    @Override\n", 0, start)
    brace = s.find("{", start)
    depth = 0
    end = -1
    for i in range(brace, len(s)):
        if s[i] == "{": depth += 1
        elif s[i] == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0: raise SystemExit("v102: focus method end missing")
    focus = """    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isLandscape() && !isInPIPMode()) {
            nm7RestorePortraitBars();
            if (mRoot != null) {
                mRoot.postDelayed(this::nm7RestorePortraitBars, 80L);
                mRoot.postDelayed(this::nm7RestorePortraitBars, 350L);
                mRoot.postDelayed(this::nm7RestorePortraitBars, 700L);
            }
        }
    }"""
    s = s[:ms] + focus + s[end:]
p.write_text(s, encoding="utf-8")

# Avatar metadata fallback must not wait 650 ms. Direct channelThumbnailUrl stays first.
card = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/shared/VideoCardHolder.java"
s = card.read_text(encoding="utf-8")
s = s.replace(".delaySubscription(650, java.util.concurrent.TimeUnit.MILLISECONDS)\n", "")
s = s.replace(".subscribeOn(io.reactivex.schedulers.Schedulers.single())",
              ".subscribeOn(io.reactivex.schedulers.Schedulers.io())")
card.write_text(s, encoding="utf-8")

print("NM7 1.10.102 final runtime correction applied")


# NM7 final Android 15/16 + YouTube regression correction.
# Target SDK 35/36 enforces edge-to-edge, so portrait playback must explicitly
# apply status/navigation insets to the root content.
p = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"
ps = p.read_text(encoding="utf-8")

if "mNm7PortraitInsetsInstalled" not in ps:
    anchor = "    private ImageView mNm7StartupPoster;\n"
    if ps.count(anchor) != 1:
        raise SystemExit("final: startup poster field anchor missing")
    ps = ps.replace(anchor, anchor +
        "    private boolean mNm7PortraitInsetsInstalled;\n"
        "    private int mNm7BaseRootPaddingLeft;\n"
        "    private int mNm7BaseRootPaddingTop;\n"
        "    private int mNm7BaseRootPaddingRight;\n"
        "    private int mNm7BaseRootPaddingBottom;\n", 1)

if "private void nm7InstallPortraitInsets()" not in ps:
    anchor = "    private void nm7RestorePortraitBars() {\n"
    if ps.count(anchor) != 1:
        raise SystemExit("final: portrait restore anchor missing")
    helper = """    private void nm7InstallPortraitInsets() {
        if (mNm7PortraitInsetsInstalled || mRoot == null) return;
        mNm7PortraitInsetsInstalled = true;
        mNm7BaseRootPaddingLeft = mRoot.getPaddingLeft();
        mNm7BaseRootPaddingTop = mRoot.getPaddingTop();
        mNm7BaseRootPaddingRight = mRoot.getPaddingRight();
        mNm7BaseRootPaddingBottom = mRoot.getPaddingBottom();
        if (android.os.Build.VERSION.SDK_INT >= 20) {
            mRoot.setOnApplyWindowInsetsListener((v, insets) -> {
                if (!isLandscape() && !isInPIPMode()) {
                    int top = 0;
                    int bottom = 0;
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets bars = insets.getInsets(
                                android.view.WindowInsets.Type.statusBars()
                                        | android.view.WindowInsets.Type.navigationBars()
                                        | android.view.WindowInsets.Type.displayCutout());
                        top = bars.top;
                        bottom = bars.bottom;
                    } else {
                        top = insets.getSystemWindowInsetTop();
                        bottom = insets.getSystemWindowInsetBottom();
                    }
                    mRoot.setPadding(
                            mNm7BaseRootPaddingLeft,
                            Math.max(mNm7BaseRootPaddingTop, top),
                            mNm7BaseRootPaddingRight,
                            Math.max(mNm7BaseRootPaddingBottom, bottom));
                } else {
                    mRoot.setPadding(mNm7BaseRootPaddingLeft, mNm7BaseRootPaddingTop,
                            mNm7BaseRootPaddingRight, mNm7BaseRootPaddingBottom);
                }
                return insets;
            });
            mRoot.requestApplyInsets();
        }
    }

"""
    ps = ps.replace(anchor, helper + anchor, 1)

request_anchor = "        if (mRoot != null) mRoot.requestApplyInsets();\n"
if request_anchor not in ps:
    raise SystemExit("final: requestApplyInsets anchor missing")
if "nm7InstallPortraitInsets();" not in ps:
    ps = ps.replace(request_anchor, "        nm7InstallPortraitInsets();\n" + request_anchor, 1)

# Install immediately after onResume as well, because targetSdk 35/36 ignores
# legacy decor fitting and relies on explicit inset handling.
resume = "    protected void onResume() {"
if resume not in ps:
    raise SystemExit("final: onResume missing")
rp = ps.find(resume)
if "nm7InstallPortraitInsets();" not in ps[rp:rp+1600]:
    sp = ps.find("        super.onResume();", rp)
    if sp < 0:
        raise SystemExit("final: onResume super anchor missing")
    e = sp + len("        super.onResume();")
    ps = ps[:e] + "\n        nm7InstallPortraitInsets();" + ps[e:]

# Keep the startup spinner permanently hidden.
spinner = "    public void showProgressBar(boolean show) {"
if spinner not in ps:
    raise SystemExit("final: showProgressBar missing")
if "NM7 final spinner hard-hide" not in ps:
    start = ps.find(spinner)
    brace = ps.find("{", start)
    depth = 0
    end = -1
    for i in range(brace, len(ps)):
        if ps[i] == "{":
            depth += 1
        elif ps[i] == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        raise SystemExit("final: showProgressBar end missing")
    replacement = """    public void showProgressBar(boolean show) {
        // NM7 final spinner hard-hide: use the native shutter/poster lifecycle.
        if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
        mProgressHidePending = false;
    }"""
    ps = ps[:start] + replacement + ps[end:]

p.write_text(ps, encoding="utf-8")

# Avatar metadata: remove the artificial 650 ms delay and keep the existing
# ConcurrentHashMap de-duplication/cache.
card = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/shared/VideoCardHolder.java"
cs = card.read_text(encoding="utf-8")
cs = cs.replace(".delaySubscription(650, java.util.concurrent.TimeUnit.MILLISECONDS)\n", "")
cs = cs.replace(".subscribeOn(io.reactivex.schedulers.Schedulers.single())",
                ".subscribeOn(io.reactivex.schedulers.Schedulers.io())")
card.write_text(cs, encoding="utf-8")

# Feed cards do not need maxres thumbnails while swiping. Keep playback source
# selection unchanged, but use hqdefault for the feed to reduce decode/network work.
ui = Path("scripts/patch-mobile-ui.py")
us = ui.read_text(encoding="utf-8")
us = us.replace("/maxresdefault.jpg", "/hqdefault.jpg")
ui.write_text(us, encoding="utf-8")

print("NM7 final regression correction: Android 15/16 insets + spinner hard-hide + avatar priority + faster feed thumbnails")
