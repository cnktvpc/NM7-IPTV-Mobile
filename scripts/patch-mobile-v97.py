"""NM7 Mobile 1.10.97: hard-disable the YouTube startup/buffering spinner.

The previous v96 patch only changed the setVideo() call site. SmartTube also
calls PlaybackActivity.showProgressBar() from its buffering/controller path,
which can turn the same ProgressBar back on. Override that UI callback so the
Mobile player never renders the indeterminate spinner.

Keep the tested v1.10.91 playback/format path unchanged; do not trade away
decoder/network stability for an artificial buffer reduction.
"""
from pathlib import Path
import re

ROOT = Path("third_party/SmartTube-droid")
PLAYBACK = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"

s = PLAYBACK.read_text(encoding="utf-8")

pattern = re.compile(
    r'(?ms)^    @Override\n    public void showProgressBar\(boolean show\) \{.*?^    \}\n\n    /\*\*\n     \* Only STATE_BUFFERING counts as stalled\.'
)
replacement = '''    @Override
    public void showProgressBar(boolean show) {
        // NM7 1.10.97: never render SmartTube's indeterminate spinner on Mobile.
        // SmartTube can invoke this callback repeatedly from its buffering path;
        // hiding it only from setVideo() is therefore insufficient.
        runOnUiThread(() -> {
            if (mProgressBar != null) {
                mProgressBar.setVisibility(View.GONE);
            }
            mProgressHidePending = false;
        });
    }

    /**
     * Only STATE_BUFFERING counts as stalled.'''

s2, count = pattern.subn(replacement, s, count=1)
if count != 1:
    raise SystemExit(f"v97: expected exactly one showProgressBar method, found {count}")

PLAYBACK.write_text(s2, encoding="utf-8")
print("NM7 Mobile 1.10.97: YouTube ProgressBar hard-disabled at the controller callback")
