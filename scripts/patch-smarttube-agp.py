from pathlib import Path
import re

root = Path('third_party/SmartTube-droid')
for p in root.rglob('build.gradle'):
    if '.git' in p.parts:
        continue
    t = p.read_text(errors='ignore')
    orig = t
    mfile = p.parent / 'src/main/AndroidManifest.xml'
    pkg = None
    if mfile.is_file():
        m = re.search(r'\bpackage\s*=\s*["\']([^"\']+)["\']', mfile.read_text(errors='ignore'))
        if m:
            pkg = m.group(1)
    if 'android {' in t:
        if pkg and not re.search(r'\bnamespace\s', t):
            t = re.sub(r'(android\s*\{)', r'\1\n    namespace "' + pkg + '"', t, 1)
        if not re.search(r'compileSdkVersion|compileSdk\s*[= ]', t):
            t = re.sub(r'(android\s*\{)', r'\1\n    compileSdkVersion project.properties.compileSdkVersion', t, 1)
        if 'flavorDimensions' not in t:
            t = re.sub(r'(android\s*\{)', r'\1\n    flavorDimensions "device"', t, 1)
        elif '"device"' not in re.search(r'flavorDimensions[^\n]*', t).group(0):
            t = re.sub(r'(flavorDimensions\s+[^\n]+)', r'\1, "device"', t, 1)
        if not re.search(r'\bmobile\s*\{\s*dimension\s+["\']device["\']', t, re.S):
            t = re.sub(r'(android\s*\{)', r'\1\n    productFlavors { mobile { dimension "device" } }', t, 1)
        for f in ('stbeta', 'ststable', 'stfdroid'):
            t = re.sub(r'(?m)^(\s*)' + f + r'\s*\{\s*\}', lambda m: m.group(1) + f + ' { dimension "default" }', t)
        t = re.sub(r'(?m)^(\s*)classifier\s+["\']([^"\']+)["\']\s*$', r"\1archiveClassifier.set('\2')", t)
        t = re.sub(r'(?m)^(\s*)classifier\s*=\s*["\']([^"\']+)["\']\s*$', r"\1archiveClassifier.set('\2')", t)
        if 'kotlin-android' in t and 'kotlinOptions' not in t:
            t = re.sub(r'(android\s*\{)', r'\1\n    kotlinOptions { jvmTarget = "1.8" }', t, 1)
    if t != orig:
        p.write_text(t)
print("SmartTube patched for AGP 8.13 successfully.")
