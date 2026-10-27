# Tiến độ dự án NM7 IPTV — Mobile 1.10.26

_Cập nhật: 18/09/2026_

## Trạng thái hiện tại

Đang chốt bản **NM7 IPTV Mobile 1.10.26**, versionCode **44**, versionName **1.10.26**, trên nhánh:

`fix/mobile-1.10.26-sleep-timer-icon`

Phạm vi lần này chỉ là **Mobile**. Không build/package TV.

APK đầu ra bắt buộc đúng **2 file ARM**:
- `NM7-IPTV-Mobile-1.10.26-arm64-v8a.apk`
- `NM7-IPTV-Mobile-1.10.26-armeabi-v7a.apk`

Không được có:
- x86
- x86_64
- universal APK
- `libvlc.so`

## Mốc build Windows đã đạt

Ngày 18/09/2026 đã build Mobile thành công bằng Windows + Gradle 8.13.

Kết quả thực tế:
- arm64-v8a: khoảng **61.9 MB**
- armeabi-v7a: khoảng **51.4 MB**
- Script đã verify APK signature.
- Script đã kiểm tra ABI native entries.
- Script đã kiểm tra không có x86/x86_64/libvlc.
- Script đã kiểm tra Mobile manifest không chứa TV/VLC component.

Thư mục output:
`dist\\mobile`

## SmartTube

Nguồn SmartTube được clone từ fork phone/touch và merge upstream stable:
- Upstream stable: **32.47s**
- Upstream repo: yuliskov/SmartTube
- SmartTube được giữ phone/touch UI, không đưa TV UI vào Mobile.

Quy trình Windows bắt buộc:
1. clone/update SmartTube fork;
2. fetch upstream tag `32.47s`;
3. merge tag;
4. `git submodule update --init --force --recursive`;
5. áp dụng toàn bộ patch Mobile;
6. build.

Không tự ý bỏ bước submodule update.

## Các lỗi Windows đã gặp và đã sửa trong script

### 1. PowerShell biến `$args`
Script cũ dùng tên biến/parameter gây đụng biến tự động `$args` của PowerShell.

Đã sửa hàm chạy command để không dùng tên `$args`.

### 2. Kiểm tra Java
Cách bắt stderr cũ làm script xử lý sai output.

Đã chuyển sang `Start-Process` + redirect stdout/stderr để kiểm tra Java 17 ổn định.

### 3. SmartTube shallow clone
Merge upstream 32.47s từng lỗi vì repository clone nông.

Đã sửa:
- `git fetch --unshallow origin`
- `git fetch upstream refs/tags/32.47s`
- sau đó mới merge.

### 4. Git identity trong SmartTube
Merge SmartTube trên Windows yêu cầu identity.

Script đã tự cấu hình local:
- user.name: `NM7 Windows Build`
- user.email: `nm7-build@users.noreply.github.com`

### 5. PowerShell execution policy
Nếu Windows chặn `.ps1`, chạy trong phiên PowerShell hiện tại:

`Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force`

Sau đó chạy script. Có thể dùng `powershell.exe -NoExit ...` nếu cần giữ cửa sổ khi script dừng.

### 6. SmartTube patch marker
Một số lần build dừng vì script tìm marker không tồn tại sau khi upstream 32.47s thay đổi source.

Đặc biệt:
- `PlaybackActivity onStop marker not found`
- `PlaybackActivity onUserLeaveHint marker not found`

Không được tiếp tục bằng cách chèn patch mù vào source. Script phải kiểm tra marker/điểm chèn theo đúng source thực tế và báo lỗi rõ ràng nếu upstream thay đổi.

### 7. PowerShell chuỗi thay thế có `$1`
Đã gặp:
`The variable '$1' cannot be retrieved because it has not been set.`

Nguyên nhân: PowerShell nội suy `$1` trong replacement string.

Khi patch Java bằng regex trong PowerShell, **không dùng replacement string kiểu `"$1..."` trực tiếp**. Phải dùng replacement evaluator/script block hoặc cách thay thế an toàn để PowerShell không hiểu `$1` là biến.

### 8. Browse/ChannelUploads GRID_COLUMNS
Đã gặp:
`cannot find symbol: variable GRID_COLUMNS`

Nguyên nhân script thay `GRID_COLUMNS = 2` nhưng upstream/source có class không còn field đó, hoặc thay sai vị trí.

Không được áp dụng patch `GRID_COLUMNS` bằng regex chung cho mọi Activity. Chỉ patch đúng file/đúng field tồn tại. Nếu BrowseActivity/ChannelUploadsActivity không có field thì thêm field đúng class trước khi dùng, hoặc patch trực tiếp LayoutManager theo cách tương thích source.

### 9. BrowseActivity syntax corruption
Đã từng gặp:
`<identifier> expected`
ở dòng field `private static final int ...`.

Nguyên nhân patch text chèn sai vị trí.

Sau khi patch Java, script phải kiểm tra nội dung/marker trước và sau khi thay thế, tránh chèn lặp hoặc chèn giữa khai báo Java.

### 10. themes.xml resource name
Đã gặp:
`packageMobileDebugResources`
`themes.xml: '' is not a valid resource name character`

Đây là dấu hiệu patch string/resource của SmartTube tạo resource name rỗng. Khi gặp lại phải kiểm tra patch resource/string trước khi build tiếp, không coi đây là lỗi Gradle chung.

### 11. onUserLeaveHint trùng method
Đã gặp:
`method onUserLeaveHint() is already defined in class PlaybackActivity`

Không được thêm method nếu upstream đã có method. Script phải kiểm tra method hiện hữu rồi mới patch.

### 12. isNm7TabSwitch() không tồn tại
Đã gặp:
`cannot find symbol method isNm7TabSwitch()`

Không được gọi helper chưa được định nghĩa trong SmartTube class. Nếu cần kiểm tra tab switch, phải dùng cơ chế đã có trong project, hiện tại là system property:
`nm7.tab.switch.until`

### 13. ABI conflict
Không dùng đồng thời `ndk.abiFilters` và ABI splits theo cách gây conflict.

Cấu hình hiện tại dùng:
- `-PmobileAbiSplits=true`
- ABI splits chỉ gồm `armeabi-v7a`, `arm64-v8a`
- `isUniversalApk = false`

Không thêm lại `ndk { abiFilters ... }` nếu chưa xác nhận tương thích.

## Các patch runtime/UI đã có

### IPTV inline player
`MobileInlinePlayerProviderV2.java`:
- inline IPTV player panel được tag:
`nm7_inline_player`

`MobileIptvUi.java`:
- toolbar được đặt lại ngay sau inline IPTV player;
- có global-layout listener để reposition khi layout thay đổi.

Commit liên quan:
- `443be697c53ef407b788a5f27eda5e514c59f607`
- `c0d4ea657d3df290f0dcb756f20c4e3bfafdc395`

### YouTube / IPTV lifecycle
Đã có cơ chế:
- theo dõi PlayerActivity;
- theo dõi SmartTube Browse/Playback;
- tab switch dùng system property `nm7.tab.switch.until`;
- khi phát video YouTube thì pause IPTV;
- khi phát IPTV thì pause external media;
- cố gắng tránh release IPTV khi chỉ chuyển tab.

### SmartTube PlayerView resource collision
Giữ nguyên patch bắt buộc:
- `exo_player_view.xml` -> `st_exo_player_view.xml`
- `exo_simple_player_view.xml` -> `st_exo_simple_player_view.xml`
- `st_exo_player_view.xml` tham chiếu `st_exo_simple_player_view`
- SmartTube PlayerView dùng `R.layout.st_exo_player_view`
- `show_buffering` -> `st_show_buffering`

Mục đích: tránh collision giữa ExoPlayer cũ của SmartTube và Media3 của IPTV.

### OkHttp
SmartTube `DohProviders.kt` dùng API parser tương thích OkHttp 4.12:
`HttpUrl.get(s)`

### Font tiếng Việt
Đã có font fix ở Application cho TextView/WebView. Không được thêm API Android không tồn tại như `WebSettings.setDefaultFontFamily()`.

## Script build chuẩn

File:
`scripts/build-mobile-windows.ps1`

Build command mà script dùng:

```
gradle --no-daemon --console=plain \
 -PcompileSdkVersion=android-36 -PminSdkVersion=23 -PtargetSdkVersion=36 \
 -PbuildToolsVersion=36.0.0 -PtestXSupportVersion=1.1.0 \
 -PannotationXVersion=1.1.0 -ProbolectricVersion=4.6.1 \
 -PmobileAbiSplits=true \
 :app:testMobileDebugUnitTest assembleMobileDebug \
 -x :slidableactivity:testMobileDebugUnitTest
```

Môi trường Windows đã xác nhận:
- Gradle 8.13: `C:\\Gradle\\gradle-8.13\\bin\\gradle.bat`
- JDK 17: Eclipse Adoptium 17
- Android SDK: `C:\\Users\\Administrator\\AppData\\Local\\Android\\Sdk`

## Nguyên tắc cho lần build tiếp theo

**Không reset/checkout lại branch hoặc clone lại project nếu không cần thiết.**

Bắt đầu từ branch:
`fix/mobile-1.10.26-sleep-timer-icon`

Trước build:
1. `git fetch origin`
2. `git checkout fix/mobile-1.10.26-sleep-timer-icon`
3. `git pull --ff-only origin fix/mobile-1.10.26-sleep-timer-icon`
4. chạy `scripts/build-mobile-windows.ps1`

Nếu script dừng ở patch SmartTube:
- **không sửa tay nhiều lần trong thư mục third_party**;
- lấy chính xác error/marker bị thiếu;
- sửa script patch trước;
- chạy lại từ đầu để quy trình có thể tái lập.

## Trạng thái kiểm thử thiết bị

APK đã build thành công.

**Chưa kết luận runtime đã hết lỗi.**

Bước tiếp theo là cài 2 APK phù hợp thiết bị và test thực tế:
1. IPTV phát bình thường.
2. IPTV -> YouTube khi IPTV đang phát.
3. YouTube -> IPTV.
4. Chỉ chuyển tab, không phát video, player IPTV vẫn giữ.
5. YouTube video start phải pause IPTV.
6. IPTV channel start phải pause YouTube.
7. Back từ YouTube video về Browse.
8. Vietnamese text/font.
9. YouTube feed một cột, giao diện phone/touch.
10. Chuyển tab nhiều lần để kiểm tra Activity/Player lifecycle.
11. Sleep timer/icon và các chức năng IPTV hiện có.

**Hiện tại dừng ở trạng thái: BUILD SUCCESSFUL — chờ test runtime.**

Mọi lỗi runtime tiếp theo sẽ được xử lý dựa trên log/video/screenshot thực tế, không coi bản build thành công là đã hoàn tất chức năng.
