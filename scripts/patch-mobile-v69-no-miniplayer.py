"""NM7 Mobile: YouTube floating mini-player navigation (Back 1x: mini-player, Back 2x: close mini-player)."""
from pathlib import Path
import re

phone = Path("third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui")

def method(text, signature, body):
    pattern = re.compile(r"(?ms)^    " + re.escape(signature) + r" \{.*?^    \}")
    text, count = pattern.subn(lambda _: "    " + signature + " {\n" + body + "\n    }", text)
    if count != 1:
        raise SystemExit("Missing/ambiguous method: " + signature)
    return text

p = phone / "playback/PlaybackActivity.java"
t = p.read_text(encoding="utf-8")
t = method(t, "public void onBackPressed()", """        if (onDetailsBack()) return;
        if (mPlayer == null || mNm7Stopped) { super.onBackPressed(); return; }
        if (mNm7LeavingForMini) {
            consumeNm7BrowseBack();
            return;
        }
        mNm7LeavingForMini = true;
        cancelNm7TargetRestore(true);
        if (mNm7StartupPoster != null) {
            mNm7StartupPoster.setVisibility(View.GONE);
        }
        mIsBackPressed = true;
        sNm7RestorePending = false;
        sNm7Mini = true;
        syncNm7KeepAlive();
        blockEngine(false);
        if (mPanel != null && !isLandscape()) {
            mPanel.setVisibility(View.VISIBLE);
        }
        Intent intent = new Intent(this,
                com.liskovsoft.smartyoutubetv2.droid.ui.browse.BrowseActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        startActivity(intent);
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}""")
p.write_text(t, encoding="utf-8")

p = phone / "browse/BrowseActivity.java"
t = p.read_text(encoding="utf-8")
if "installNm7MiniPlayer();" not in t:
    t = t.replace("mJustCreated = false;", "mJustCreated = false;\n        installNm7MiniPlayer();", 1)

if "consumeNm7BrowseBack()" not in t:
    browse_back = """    @Override
    public void onBackPressed() {
        try {
            if (com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity.consumeNm7BrowseBack()) {
                return;
            }
        } catch (RuntimeException error) {
            android.util.Log.e("NM7Playback", "Browse Back mini close failed", error);
        }
        super.onBackPressed();
    }

"""
    if "private void initToolbar() {" in t:
        t = t.replace("private void initToolbar() {", browse_back + "private void initToolbar() {", 1)
    elif "protected void onStop() {" in t:
        t = t.replace("protected void onStop() {", browse_back + "protected void onStop() {", 1)
p.write_text(t, encoding="utf-8")

p = phone / "search/SearchActivity.java"
t = p.read_text(encoding="utf-8")
if "consumeNm7BrowseBack()" not in t:
    search_back = """    @Override
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

"""
    if "private void initAppBar() {" in t:
        t = t.replace("private void initAppBar() {", search_back + "private void initAppBar() {", 1)
p.write_text(t, encoding="utf-8")

print("NM7 Mobile: YouTube floating mini-player navigation active (Back 1x: mini, Back 2x: close)")
