from pathlib import Path
import re
import shutil
import subprocess
import sys

root = Path('third_party/SmartTube-droid')

print("1. Running AGP patch...")
subprocess.run([sys.executable, 'scripts/patch-smarttube-agp.py'], check=True)

print("2. Running patch-mobile-v37...")
subprocess.run([sys.executable, 'scripts/patch-mobile-v37.py'], check=True)

print("3. Running patch-mobile-v106...")
subprocess.run([sys.executable, 'scripts/patch-mobile-v106.py'], check=True)

print("4. Running UI chrome patches...")
for p in [
    'scripts/patch-mobile-v114-youtube-ui.py',
    'scripts/patch-mobile-v119-youtube-chrome-final.py',
    'scripts/patch-mobile-v120-youtube-statusbar-final.py',
    'scripts/patch-mobile-v121-native-statusbar.py',
    'scripts/patch-mobile-v122-youtube-overdraw-final.py',
    'scripts/patch-mobile-v123-youtube-wordmark-final.py',
    'scripts/patch-mobile-v128-youtube-wordmark-exact.py',
    'scripts/patch-mobile-v130-pull-refresh.py',
]:
    if Path(p).is_file():
        subprocess.run([sys.executable, p], check=True)

print("5. Renaming legacy show_buffering...")
changed = 0
for base in (
    root / 'exoplayer-amzn-2.10.6/library/ui/src/main/java',
    root / 'exoplayer-amzn-2.10.6/library/ui/src/main/res',
):
    if not base.is_dir():
        continue
    for p in base.rglob('*'):
        if p.suffix not in {'.java', '.xml'}:
            continue
        t = p.read_text(errors='ignore')
        n = t.replace('show_buffering', 'st_show_buffering')
        if n != t:
            p.write_text(n, encoding='utf-8')
            changed += 1
print('Renamed SmartTube legacy show_buffering references:', changed)

print("6. Patching DohProviders and string formats...")
p = Path('third_party/SmartTube-droid/SharedModules/sharedutils/src/main/java/com/liskovsoft/sharedutils/okhttp/DohProviders.kt')
if p.is_file():
    t = p.read_text(encoding='utf-8')
    t = t.replace('import okhttp3.toHttpUrlOrNull\n', '')
    t = t.replace('return s.toHttpUrlOrNull() ?: throw NullPointerException("unable to parse url")', 'return HttpUrl.get(s)')
    t = t.replace('return HttpUrl.parse(s) ?: throw NullPointerException("unable to parse url")', 'return HttpUrl.get(s)')
    p.write_text(t, encoding='utf-8')

wanted = {'auto_frame_rate_applying', 'proxy_test_error', 'proxy_test_start', 'proxy_test_status'}
pat = re.compile(r'(<string\b[^>]*\bname=["\'](' + '|'.join(wanted) + r')["\'][^>]*>)(.*?)(</string>)', re.S)
tok = re.compile(r'%(?![0-9]+\$)([sdif])')
for p in root.rglob('*.xml'):
    if '.git' in p.parts:
        continue
    x = p.read_text(errors='ignore')
    def repl(m):
        i = [0]
        def r(z):
            i[0] += 1
            return f'%{i[0]}${z.group(1)}'
        return m.group(1) + tok.sub(r, m.group(3)) + m.group(4)
    res = pat.sub(repl, x)
    if res != x:
        p.write_text(res, encoding='utf-8')

print("7. Isolating SmartTube legacy PlayerView resources from Media3...")
ui = root / 'exoplayer-amzn-2.10.6/library/ui'
res = ui / 'src/main/res/layout'
player = ui / 'src/main/java/com/google/android/exoplayer2/ui/PlayerView.java'
if res.is_dir() and player.is_file():
    if (res / 'exo_player_view.xml').is_file():
        shutil.copy2(res / 'exo_player_view.xml', res / 'st_exo_player_view.xml')
    if (res / 'exo_simple_player_view.xml').is_file():
        shutil.copy2(res / 'exo_simple_player_view.xml', res / 'st_exo_simple_player_view.xml')
    simple = res / 'st_exo_player_view.xml'
    if simple.is_file():
        t = simple.read_text(encoding='utf-8')
        simple.write_text(t.replace('@layout/exo_simple_player_view', '@layout/st_exo_simple_player_view'), encoding='utf-8')
    t = player.read_text(encoding='utf-8')
    player.write_text(t.replace('R.layout.exo_player_view', 'R.layout.st_exo_player_view'), encoding='utf-8')

print("8. Removing PlaybackActivity pitch override annotations...")
p = Path("third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java")
if p.is_file():
    lines = p.read_text(encoding='utf-8').splitlines(True)
    for i, line in enumerate(lines):
        if "public float getPitch(" in line or "public void setPitch(" in line:
            if i > 0 and lines[i - 1].strip() == "@Override":
                lines[i - 1] = ""
    p.write_text("".join(lines), encoding='utf-8')

print("All SmartTube patches applied successfully!")
