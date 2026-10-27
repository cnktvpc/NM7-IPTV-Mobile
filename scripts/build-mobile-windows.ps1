# NM7 IPTV Mobile 1.10.26 - Windows local build
# SCRIPT_VERSION: 2026-09-19-MOBILE37
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
$ST = Join-Path $Root "third_party\SmartTube-droid"
$Dist = Join-Path $Root "dist\mobile"
$BT = "36.0.0"
function Fail([string]$m) { Write-Host ""; Write-Host "BUILD STOPPED: $m" -ForegroundColor Red; exit 1 }
function Run([string]$exe,[string[]]$cmdArgs) { Write-Host (">> " + $exe + " " + ($cmdArgs -join " ")) -ForegroundColor DarkCyan; & $exe @cmdArgs; if ($LASTEXITCODE -ne 0) { Fail ("Command failed: " + $exe + " (exit $LASTEXITCODE)") } }
function ReadT([string]$p) { [IO.File]::ReadAllText($p) }
function WriteT([string]$p,[string]$s) { [IO.File]::WriteAllText($p,$s,(New-Object Text.UTF8Encoding($false))) }

Write-Host "=== NM7 IPTV Mobile 1.10.26 / Windows ===" -ForegroundColor Cyan
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Fail "Git is not installed or not in PATH." }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Fail "Java is not installed or not in PATH." }

$Gradle = $null
$g = Get-Command gradle.bat -ErrorAction SilentlyContinue
if ($g) { $Gradle = $g.Source }
if (-not $Gradle) {
  foreach ($p in @("C:\Gradle\gradle-8.13\bin\gradle.bat","$env:USERPROFILE\gradle\gradle-8.13\bin\gradle.bat")) {
    if (Test-Path $p) { $Gradle=$p; break }
  }
}
if (-not $Gradle) { Fail "Gradle 8.13 not found." }

if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
  if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) { $env:ANDROID_HOME=$env:ANDROID_SDK_ROOT }
  elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk") { $env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk" }
  else { Fail "Android SDK not found. Set ANDROID_HOME." }
}
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
  $j = @(Get-Item "C:\Program Files\Eclipse Adoptium\jdk-17*" -ErrorAction SilentlyContinue | Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") }) | Select-Object -First 1
  if ($j) { $env:JAVA_HOME=$j.FullName }
}
if (-not $env:JAVA_HOME) { Fail "JDK 17 not found. Set JAVA_HOME." }
$env:PATH=(Join-Path $env:JAVA_HOME "bin") + ";" + $env:PATH
$javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
if (-not (Test-Path $javaExe)) { Fail "JAVA_HOME does not point to a valid JDK 17 installation." }
$javaVersionFile = Join-Path $env:TEMP "nm7-java-version.txt"
Remove-Item $javaVersionFile -Force -ErrorAction SilentlyContinue
$javaProc = Start-Process -FilePath $javaExe -ArgumentList "-version" -Wait -PassThru -NoNewWindow -RedirectStandardError $javaVersionFile
$ver = if (Test-Path $javaVersionFile) { (Get-Content $javaVersionFile | Select-Object -First 1) } else { "" }
Remove-Item $javaVersionFile -Force -ErrorAction SilentlyContinue
if ($javaProc.ExitCode -ne 0 -or $ver -notmatch '"17\.') { Fail ("JDK 17 required. Current: " + $ver) }

if ((& git branch --show-current).Trim() -ne "fix/mobile-1.10.26-sleep-timer-icon") { Run git @("checkout","fix/mobile-1.10.26-sleep-timer-icon") }

$sdk = Join-Path $env:ANDROID_HOME "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdk)) { $sdk=Join-Path $env:ANDROID_HOME "cmdline-tools\bin\sdkmanager.bat" }
if (Test-Path $sdk) { Run $sdk @("platforms;android-34","platforms;android-36","build-tools;36.0.0") }

if (Test-Path $ST) { Remove-Item -Recurse -Force $ST }
New-Item -ItemType Directory -Force (Split-Path $ST) | Out-Null
Run git @("clone","--no-checkout","https://github.com/systematiq-one/SmartTube-droid.git",$ST)
Run git @("-C",$ST,"checkout","--detach","4825d6aa8b6f1d3181927f9e96c7d89cab13d510")
Run git @("-C",$ST,"submodule","update","--init","--recursive")

$nl=[Environment]::NewLine
Get-ChildItem $ST -Recurse -Filter "build.gradle" -File | Where-Object {$_.FullName -notmatch "\\.git\\"} | ForEach-Object {
  $p=$_.FullName; $t=ReadT $p; $orig=$t; $mf=Join-Path $_.Directory.FullName "src\main\AndroidManifest.xml"; $pkg=$null
  if (Test-Path $mf) { $m=[regex]::Match((ReadT $mf),'\bpackage\s*=\s*["'']([^"'']+)["'']'); if($m.Success){$pkg=$m.Groups[1].Value} }
  if($t -match 'android\s*\{'){
    if($pkg -and $t -notmatch '\bnamespace\s'){$t=[regex]::Replace($t,'(android\s*\{)',('$1'+$nl+'    namespace "'+$pkg+'"'),1)}
    if($t -notmatch 'compileSdkVersion|compileSdk\s*[= ]'){$t=[regex]::Replace($t,'(android\s*\{)',('$1'+$nl+'    compileSdkVersion project.properties.compileSdkVersion'),1)}
    if($t -notmatch 'flavorDimensions'){$t=[regex]::Replace($t,'(android\s*\{)',('$1'+$nl+'    flavorDimensions "device"'),1)}
    if($t -notmatch 'mobile\s*\{\s*dimension\s+["'']device["'']'){$t=[regex]::Replace($t,'(android\s*\{)',('$1'+$nl+'    productFlavors { mobile { dimension "device" } }'),1)}
    foreach($f in @("stbeta","ststable","stfdroid")){$t=[regex]::Replace($t,"(?m)^(\s*)$f\s*\{\s*\}",'$1'+$f+' { dimension "default" }')}
    $t=[regex]::Replace($t,"(?m)^(\s*)classifier\s+['""]([^'""]+)['""]\s*$",'$1archiveClassifier.set(''$2'')')
    $t=[regex]::Replace($t,"(?m)^(\s*)classifier\s*=\s*['""]([^'""]+)['""]\s*$",'$1archiveClassifier.set(''$2'')')
    if($t -match 'kotlin-android' -and $t -notmatch 'kotlinOptions'){$t=[regex]::Replace($t,'(android\s*\{)',('$1'+$nl+'    kotlinOptions { jvmTarget = "1.8" }'),1)}
  }
  if($t -ne $orig){WriteT $p $t}
}

# The exact same lifecycle patch as GitHub Actions, with pinned source.
if (Get-Command python -ErrorAction SilentlyContinue) {
    Run python @("scripts/patch-mobile-v37.py")
    Run python @("scripts/verify-mobile-lifecycle.py")
} elseif (Get-Command py -ErrorAction SilentlyContinue) {
    Run py @("-3","scripts/patch-mobile-v37.py")
    Run py @("-3","scripts/verify-mobile-lifecycle.py")
} else { Fail "Python 3 is required for the shared Mobile patch." }

$ui=Join-Path $ST "exoplayer-amzn-2.10.6\library\ui"; $res=Join-Path $ui "src\main\res\layout"; $pv=Join-Path $ui "src\main\java\com\google\android\exoplayer2\ui\PlayerView.java"
foreach($p in @((Join-Path $res "exo_player_view.xml"),(Join-Path $res "exo_simple_player_view.xml"),$pv)){if(-not(Test-Path $p)){Fail "Missing player source: $p"}}
Copy-Item (Join-Path $res "exo_player_view.xml") (Join-Path $res "st_exo_player_view.xml") -Force
Copy-Item (Join-Path $res "exo_simple_player_view.xml") (Join-Path $res "st_exo_simple_player_view.xml") -Force
$p=Join-Path $res "st_exo_player_view.xml"; WriteT $p (ReadT $p).Replace("@layout/exo_simple_player_view","@layout/st_exo_simple_player_view")
$t=ReadT $pv; $n=$t.Replace("R.layout.exo_player_view","R.layout.st_exo_player_view"); if($n -eq $t){Fail "PlayerView layout reference not found."}; WriteT $pv $n

$changed=0
$roots=@(
  (Join-Path $ST "exoplayer-amzn-2.10.6\library\ui\src\main\java"),
  (Join-Path $ST "exoplayer-amzn-2.10.6\library\ui\src\main\res")
)
foreach($r in $roots){
  if(Test-Path $r){
    Get-ChildItem $r -Recurse -File | Where-Object {$_.Extension -in @(".java",".xml")} | ForEach-Object {
      $x=ReadT $_.FullName
      if($x.Contains("show_buffering")){
        $n=$x.Replace("show_buffering","st_show_buffering")
        if($n -ne $x){WriteT $_.FullName $n;$changed++}
      }
    }
  }
}
if($changed -eq 0){Fail "show_buffering patch found no references in legacy ExoPlayer UI."}

$doh=Join-Path $ST "SharedModules\sharedutils\src\main\java\com\liskovsoft\sharedutils\okhttp\DohProviders.kt"
if(Test-Path $doh){$t=ReadT $doh;$t=$t.Replace("import okhttp3.toHttpUrlOrNull"+$nl,"");$t=$t.Replace('return s.toHttpUrlOrNull() ?: throw NullPointerException("unable to parse url")','return HttpUrl.get(s)');$t=$t.Replace('return HttpUrl.parse(s) ?: throw NullPointerException("unable to parse url")','return HttpUrl.get(s)');WriteT $doh $t}

Remove-Item -Recurse -Force (Join-Path $Root "app\build"),$Dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $Dist | Out-Null
$ga=@("--no-daemon","--console=plain","-PcompileSdkVersion=android-36","-PminSdkVersion=23","-PtargetSdkVersion=36","-PbuildToolsVersion=36.0.0","-PtestXSupportVersion=1.1.0","-PannotationXVersion=1.1.0","-ProbolectricVersion=4.6.1","-PmobileAbiSplits=true",":app:testMobileDebugUnitTest","assembleMobileDebug","-x",":slidableactivity:testMobileDebugUnitTest")
Run $Gradle $ga

$out=Join-Path $Root "app\build\outputs\apk\mobile\debug"
$apks=@(Get-ChildItem $out -Filter "app-mobile-*-debug.apk" -File)
if($apks.Count -ne 2){Fail "Expected exactly 2 APKs, found $($apks.Count)."}
$a64=$apks|Where-Object{$_.Name -like "*arm64-v8a*"}
$a7=$apks|Where-Object{$_.Name -like "*armeabi-v7a*"}
if(-not $a64 -or -not $a7){Fail "Expected arm64-v8a and armeabi-v7a APKs."}

$sign=Join-Path $env:ANDROID_HOME "build-tools\$BT\apksigner.bat"
foreach($a in @($a64,$a7)){
    Run $sign @("verify","--verbose","--print-certs",$a.FullName)
    $z=@(& jar tf $a.FullName)
    if(($z -join [Environment]::NewLine) -match "lib/x86/|lib/x86_64/|libvlc\.so"){Fail "Forbidden ABI/VLC content in $($a.Name)."}
}

$z64=@(& jar tf $a64.FullName)
$native64=@($z64 | Where-Object { $_ -match '^lib/[^/]+/[^/]+\.so$' })
$arm64Entries=@($native64 | Where-Object { $_ -match '^lib/arm64-v8a/' })
$wrong64=@($native64 | Where-Object { $_ -match '^lib/armeabi-v7a/|^lib/x86/|^lib/x86_64/' })
if($arm64Entries.Count -eq 0 -or $wrong64.Count -gt 0){
    Write-Host "arm64 APK native entries:" -ForegroundColor Yellow
    $native64 | ForEach-Object { Write-Host ("  "+$_) }
    Fail "arm64 ABI validation failed."
}

$z7=@(& jar tf $a7.FullName)
$native7=@($z7 | Where-Object { $_ -match '^lib/[^/]+/[^/]+\.so$' })
$arm7Entries=@($native7 | Where-Object { $_ -match '^lib/armeabi-v7a/' })
$wrong7=@($native7 | Where-Object { $_ -match '^lib/arm64-v8a/|^lib/x86/|^lib/x86_64/' })
if($arm7Entries.Count -eq 0 -or $wrong7.Count -gt 0){
    Write-Host "armeabi-v7a APK native entries:" -ForegroundColor Yellow
    $native7 | ForEach-Object { Write-Host ("  "+$_) }
    Fail "armv7 ABI validation failed."
}

$manifest=Join-Path $Root "app\build\intermediates\merged_manifest\mobileDebug\processMobileDebugManifest\AndroidManifest.xml"
if(Test-Path $manifest){
    $m=ReadT $manifest
    if($m -match "TvLayoutTunerProvider|TvStreamRecoveryProvider|TvPlayerUiProvider|VlcFallbackActivity|LEANBACK_LAUNCHER"){
        Fail "TV/VLC component detected in Mobile manifest."
    }
}

$bg=ReadT (Join-Path $Root "app\build.gradle.kts")
$mm=[regex]::Match($bg,'versionName\s*=\s*"([^"]+)"')
$v="1.10.26"
if($mm.Success){$v=$mm.Groups[1].Value}

Copy-Item $a64.FullName (Join-Path $Dist "NM7-IPTV-Mobile-$v-arm64-v8a.apk") -Force
Copy-Item $a7.FullName (Join-Path $Dist "NM7-IPTV-Mobile-$v-armeabi-v7a.apk") -Force
Write-Host ""
Write-Host "=== BUILD SUCCESSFUL ===" -ForegroundColor Green
Write-Host "APK output: $Dist"
Get-ChildItem $Dist
