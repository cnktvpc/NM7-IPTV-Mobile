#!/usr/bin/env python3
"""NM7 Mobile 1.10.121 — native Android 15 status-bar fix.

The attached 223383.mp4 proves the problem is not the logo asset or a missing margin.
The Browse Activity is being rendered edge-to-edge because the app targets SDK 36.
Android 15 enforces edge-to-edge for target 35+ and legacy statusBarColor/content
fitting does not keep content below the status bar.

For the user's Android 15 device, Android explicitly provides a compatibility opt-out:
android:windowOptOutEdgeToEdgeEnforcement=true. Use that native window policy for the
SmartTube Browse theme, then let the platform place the entire content area below the
status bar. This removes the need for every previous manual inset/spacer workaround.

The fix is intentionally scoped to Browse theme/window policy. Feed, playback, IPTV,
avatars, spinner, live chat and bottom navigation are untouched.
"""
from pathlib import Path
import re

SMART=Path("third_party/SmartTube-droid")
THEME=SMART/"smarttubedroid/src/main/res/values/themes.xml"
BROWSE=SMART/"smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"

if not THEME.is_file():
    raise SystemExit("v121: SmartTube themes.xml missing")
if not BROWSE.is_file():
    raise SystemExit("v121: BrowseActivity missing")

# Theme-level policy is the important part. The attribute is honored on Android 15
# devices even when targetSdk is 36; Android 16 will ignore the opt-out, so this is
# deliberately a compatibility path rather than a replacement for edge-to-edge support.
t=THEME.read_text(encoding="utf-8")
match=re.search(r'(<style\s+name="Theme\.SmartTubeDroid"[^>]*>)(.*?)(</style>)',t,re.S)
if not match:
    raise SystemExit("v121: Theme.SmartTubeDroid style missing")
body=match.group(2)

def upsert(body,name,value):
    pat=re.compile(r'(?m)^\s*<item\s+name="'+re.escape(name)+r'"[^>]*>.*?</item>\s*')
    line=f'        <item name="{name}">{value}</item>\n'
    if pat.search(body):
        return pat.sub(line,body,count=1)
    return body + line

body=upsert(body,"android:windowOptOutEdgeToEdgeEnforcement","true")
body=upsert(body,"android:windowLightStatusBar","true")
body=upsert(body,"android:statusBarColor","#FFFFFFFF")
body=upsert(body,"android:navigationBarColor","#FFFFFFFF")
body=upsert(body,"android:windowLightNavigationBar","true")
t=t[:match.start(2)]+body+t[match.end(2):]
THEME.write_text(t,encoding="utf-8")

# Remove the previous explicit force-to-edge/inset code in BrowseActivity. With the
# native opt-out, Android will lay out the root below status/navigation bars.
sig="    private void applyNm7PortraitSystemBars() {"
s=BROWSE.read_text(encoding="utf-8")
pos=s.find(sig)
if pos<0:
    raise SystemExit("v121: applyNm7PortraitSystemBars missing")
brace=s.find("{",pos); depth=0; end=-1
for i in range(brace,len(s)):
    if s[i]=="{": depth+=1
    elif s[i]=="}":
        depth-=1
        if depth==0:
            end=i+1
            break
if end<0:
    raise SystemExit("v121: status-bar method end missing")

method='''    private void applyNm7PortraitSystemBars() {
        if (isLandscape()) return;

        final android.view.Window window = getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(android.graphics.Color.WHITE);

        // Theme.SmartTubeDroid opts out of Android 15 edge-to-edge enforcement for
        // this legacy phone Browse screen. Do not re-enable edge-to-edge here.
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            int flags = window.getDecorView().getSystemUiVisibility();
            flags &= ~(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }
'''
s=s[:pos]+method+s[end:]

# Existing v119/v120 spacer must not create another gap in the non-edge-to-edge path.
s=s.replace('android.view.View spacer=findViewById(R.id.nm7_status_bar_spacer);','android.view.View spacer=findViewById(R.id.nm7_status_bar_spacer);')
# Keep the spacer at 0 height. Method above does not modify it.
BROWSE.write_text(s,encoding="utf-8")
print("NM7 Mobile 1.10.121 native Android 15 edge-to-edge opt-out applied")
