# NM7 IPTV Mobile — Candidate 1.10.146 — Super OK-style EPG / Catch-up — 2026-10-28

> **Development branch:** `feature/epg-catchup-superok-20261028`  
> **Baseline:** accepted Mobile 1.10.145 / versionCode 162.  
> **Candidate:** 1.10.146 / versionCode 163.  
> **Previous candidate CI:** passed on commit `04291b79`; a follow-up UX fix is now being built to make the EPG entry visible directly on each channel row. Do not install the previous candidate expecting the new row button. Stable remains unchanged; PR #1 is a draft.

## Implemented in this candidate

- XMLTV schedule model/parser with programme intervals, day navigation, channel-ID matching, and `display-name` aliases.
- A visible **EPG** button on every channel row opens the channel's schedule directly; long-press actions remain available as an alternate entry. The player also has a **Lịch** button and a translucent lower EPG panel with horizontal programme cards, times, titles, state labels and current-programme progress.
- Playback state is separate from wall-clock schedule state: the live programme remains **ĐANG PHÁT** according to the real time, while the chosen archive programme changes to **ĐANG XEM LẠI**.
- Catch-up URL builder reads `catchup`, `catchup-source` / `catchup-url`, and `catchup-days` from original EXTINF metadata; supports full URL/default and append modes plus common start/end/duration and UTC/local placeholders. Placeholder expansion is longest-first to prevent partial replacements.
- The panel remains open when replay starts. The player changes to **DVR · Lịch** and can restore the original live URL/name. Replay launched from the channel action menu carries the original live URL/name, selected programme interval and title, starts in DVR state and opens the EPG panel.
- Added Catch-up unit tests, including quoted metadata, default URL construction, append mode, expiry/future rejection, and overlapping-placeholder regression coverage.

## Latest verified artifact — visible EPG button

- **Source commit:** [b264cdd62437c360a50f6488430d0e6f3fccdb71](https://github.com/cnktvpc/NM7-IPTV-Mobile/commit/b264cdd62437c360a50f6488430d0e6f3fccdb71).
- **Push build:** [Actions run #40642855178 — SUCCESS](https://github.com/cnktvpc/NM7-IPTV-Mobile/actions/runs/40642855178).
- **Pull-request validation:** [Actions run #40642861504 — SUCCESS](https://github.com/cnktvpc/NM7-IPTV-Mobile/actions/runs/40642861504).
- **Artifact:** `NM7-IPTV-Mobile-FINAL`, ID `12796551400`; expires 2026-11-27.
- **APK filename delivered:** `NM7-IPTV-Mobile-1.10.146-EPG-Catchup-visible-EPG.apk`.
- **APK SHA-256:** `0e53cf109a50ba6de5fe1e56f1512aef603537f09c02e78b40161b5d9d8a0897`.
- **Artifact ZIP SHA-256:** `1d1106636ea1808ad75901bb123474ec7994096ff98731e400e759ae5bc72dc7`.
- Local verification: ZIP and APK archive integrity passed; APK SHA-256 matches the artifact's `SHA256SUMS.txt`; compiled DEX contains the EPG entry callback and schedule/catch-up code.
- Stable remains unchanged. CI validates the build/packaging only; real-device UI and provider-specific replay still require device testing.

## Previous artifact (superseded)

The earlier APK from [run #40639183001](https://github.com/cnktvpc/NM7-IPTV-Mobile/actions/runs/40639183001), commit `04291b79d0f480b06dd574367f4c785ad7c41590`, is superseded because its EPG entry was too difficult to discover. Do not use it to validate the visible row-button fix.

## Still required before merging to stable

- Catch-up is source-dependent: a valid XMLTV feed alone does not mean a provider offers an archive. A real authorized Catch-up channel is still needed to verify provider-specific URL templates, headers and timestamp conventions.
- Device validation remains outstanding for remote/touch focus, day navigation, current/future/past rows, replay-to-live restoration, channel switching, rotation, regular IPTV playback, Live feed and YouTube MiniPlayer.
- Keep PR #1 in draft and stable unchanged until those checks pass.



# NM7 IPTV Mobile — STABLE 1.10.145 — Super OK-style group swipe — 2026-10-26

**Source baseline locked:** `4fc8b187db7e48591e1bcb11969436a56d27d06a` (the exact 1.10.142 commit from the screenshot). This branch is based directly on that commit; neither failed swipe candidate is used as source or edited.

## What was learned from Super OK 2.5 APK
- The uploaded APK contains `com.liskovsoft.smartyoutubetv2.tv.ui.mobile.widgets.HorizontalGroupSwipeHelper`.
- The helper attaches through `RecyclerView.addOnItemTouchListener`, uses `ViewConfiguration` and `VelocityTracker`, intercepts after horizontal intent clears touch slop, follows the finger with damped `translationX`, dispatches an adjacent-group callback on release, then springs back with a 180 ms decelerate animation.
- NM7 keeps its native `ListView` and existing category/filter callbacks; equivalent touch ownership is implemented in `Nm7SuperOkGroupSwipeListView.dispatchTouchEvent` rather than converting the entire IPTV screen to RecyclerView.

## Stable release 1.10.145 — user confirmed OK
- Version: `1.10.145` / `versionCode 162`.
- Development branch: `fix/mobile-1.10.142-superok-group-swipe-20261025`.
- **Stable branch:** `stable/mobile-1.10.145` (release merge commit); `stable/mobile-1.10.142` is retained as a historical branch ref updated to the merge commit.
- **User acceptance:** 2026-10-26 — user confirmed this build is OK.
- The old provider-level `GestureDetector`/`OnTouchListener` observer is removed. Touch dispatch is arbitrated in `dispatchTouchEvent` even if ListView claims the DOWN event; a qualified horizontal stream is canceled and consumed before native ListView click handling, then the content follows the finger and springs back.
- Both portrait and landscape layouts use the custom list; playlist, Live feed parsing, player, EPG, YouTube MiniPlayer, server selection and group/filter logic are preserved.
- Focused Robolectric tests gate APK assembly: slow swipe/long-press cancellation, fast flick, reverse direction, diagonal-start noise, vertical scroll, tap and multitouch.
- **GitHub Actions:** [Run 40212821749 — overall SUCCESS](https://github.com/cnktvpc/NM7-IPTV/actions/runs/40212821749), commit `15614dc5cc5d31d18640dc5304d4eb390bb1adfe`.
- **Focused swipe tests:** 7/7 PASS; blocking gate passed before build.
- **APK build:** `:app:assembleMobileDebug` PASS.
- **Full unit suite:** 97 tests completed, 1 failed: `YouTubeSurfaceLayoutTest.miniUsesSurfaceAfterAllResourceMerges`. The identical test also failed on the original 1.10.142 baseline run [39407088691](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39407088691), which had 90 tests and 1 failure; unrelated to the group-swipe changes. The repository workflow intentionally treats this full-suite legacy failure as non-blocking for the APK upload.
- **Artifact:** [NM7-IPTV-Mobile-1.10.145-SUPEROK-GROUP-SWIPE](https://github.com/cnktvpc/NM7-IPTV/actions/runs/40212821749/artifacts/12606446830), artifact ID `12606446830`, expires 2026-11-24.
- **Artifact ZIP SHA-256:** `2546816e0adefc20384c0f269644f0438e225b612edd9ef1d5ffdba8f53a7117`.
- **APK SHA-256:** `53688a7c3f9c664dd36e2f213dbe10f826543559f9a49a5f752812f5da5cc742` — matches the APK's `SHA256SUMS.txt`; both artifact ZIP and APK passed ZIP integrity checks.
- **Device runtime:** user has tested this APK and confirmed it is OK (2026-10-26). This accepted release is the required base for all subsequent development.

## Future development baseline (mandatory)
- All future NM7 IPTV Mobile changes must start from `stable/mobile-1.10.145` (version `1.10.145`, `versionCode 162`) from the stable release ref.
- Do not base new work on the failed swipe candidates 1.10.143/1.10.144, or reset to 1.10.142. Create a new `work/...` or `fix/...` branch from the stable 1.10.145 ref and keep changes isolated until validated.
- Preserve all existing IPTV, Live, playback, EPG, YouTube MiniPlayer, server selection, favorites and group/filter behavior unless a new request explicitly changes them.

## Device test checklist
1. Swipe left/right over channel list content and Live event cards.
2. Confirm the list follows the finger slightly, springs back and changes exactly one adjacent group.
3. Confirm no accidental click or long-press opens during a swipe.
4. Verify normal taps, vertical scrolling, stationary long press, playback, Live server/favorites and YouTube.

---

## Historical baseline 1.10.142 notes

# NM7 IPTV Mobile — Stable 1.10.142 — YouTube Floating MiniPlayer / SurfaceView — 2026-10-20

> **Trạng thái cập nhật 2026-10-22:** Build APK thành công. Lưu ý: bước `:app:testMobileDebugUnitTest` trong run 39407088691 có lỗi nhưng được cấu hình `continue-on-error: true`, vì vậy workflow vẫn báo SUCCESS. Không ghi nhận unit tests là PASS cho lần build này. Cần xử lý lỗi test và kiểm thử trên thiết bị thật trước khi xác nhận riêng các thay đổi YouTube MiniPlayer là ổn định.

## Thông tin phát hành

- **Ứng dụng:** NM7 IPTV Mobile
- **Version:** 1.10.142
- **versionCode:** 158
- **Nhánh phát hành:** `stable/mobile-1.10.142`
- **Nhánh nguồn đã build:** `build/youtube-miniplayer-20261020`
- **Commit nguồn đã build:** [4fc8b187db7e48591e1bcb11969436a56d27d06a](https://github.com/cnktvpc/NM7-IPTV/commit/4fc8b187db7e48591e1bcb11969436a56d27d06a)
- **GitHub Actions:** [Run 39407088691 — workflow SUCCESS; unit-test step failed non-blocking](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39407088691)
- **Artifact:** `NM7-IPTV-Mobile-1.10.142-YOUTUBE-MINIPLAYER`
- **Artifact ID:** `12258102908` (đã xác nhận tồn tại; hết hạn dự kiến 2026-11-19).
- **APK SHA-256:** Chưa xác minh được checksum của APK trong artifact của run 39407088691; không dùng checksum của run khác để đại diện.

## Nội dung trong bản

- Cửa sổ YouTube mini-player nổi tỷ lệ 16:9.
- Player dùng `SurfaceView` thay cho `TextureView` để thống nhất loại surface giữa màn hình phát toàn màn hình và mini-player.
- Khi mini-player đã được gắn vào root hiện tại, thao tác bind lại được đưa vào hàng đợi UI sau khi view sẵn sàng.
- Khi tạo mini-player mới, chuyển target phát sau khi view đã attach/layout; tránh bind quá sớm vào SurfaceHolder chưa sẵn sàng.
- Ẩn startup poster khi thao tác Back chuyển từ YouTube playback về mini-player.
- Điều chỉnh chuyển tab IPTV: nếu phiên YouTube đang hoạt động thì suspend phiên đó trước khi mở IPTV; yêu cầu resume player được post sau chuyển Activity.
- Giữ phiên bản ứng dụng 1.10.142 / versionCode 158.

## Kiểm tra build và artifact

- GitHub Actions build job: **SUCCESS**.
- Unit tests trong run 39407088691: **FAILED nhưng không chặn build** (`continue-on-error: true`).
- Artifact ZIP: **đọc được, không lỗi cấu trúc**.
- APK: **đọc được như ZIP Android package và có `AndroidManifest.xml`**.
- SHA-256 APK của run 39407088691: **chưa xác minh**.
- Ghi chú: kết quả CI không tự xác nhận chất lượng phát video trên thiết bị. Cần kiểm tra thực tế: mở video, thu nhỏ về mini-player, kéo thả, phóng to lại, chuyển qua tab IPTV rồi quay lại YouTube, và xác nhận hình/tiếng đồng bộ.

## Quy tắc nhánh

- Bản phát hành này được promote từ nhánh build riêng vào stable qua pull request.
- Không ghi đè các nhánh stable cũ.
- Các chỉnh sửa CI chỉ dùng để tạo và xác minh artifact; không xem chúng là thay đổi chức năng ứng dụng.
- Mọi sửa đổi tiếp theo cần đi qua nhánh candidate/build riêng, chạy build, unit tests và regression checks trước khi promote.


## Ghi chú ổn định — cập nhật 2026-10-22

- Commit build: [4fc8b187db7e48591e1bcb11969436a56d27d06a](https://github.com/cnktvpc/NM7-IPTV/commit/4fc8b187db7e48591e1bcb11969436a56d27d06a).
- Commit chỉ bổ sung `scripts/patch-mobile-v130-pull-refresh.py` vào bộ lọc `paths` của workflow để thay đổi script này cũng kích hoạt build.
- APK build và upload thành công trong [run 39407088691](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39407088691), artifact ID `12258102908`.
- Unit-test step thất bại nhưng không chặn workflow; do đó không tuyên bố toàn bộ test pass cho lần build này.
- Nhánh `stable/mobile-1.10.142` giữ nguyên baseline; không nâng mã nguồn ứng dụng chỉ vì thay đổi trigger CI. Cần sửa/đánh giá lỗi unit test và kiểm thử APK trên thiết bị trước khi xác nhận thay đổi MiniPlayer mới là ổn định.

---

# NM7 IPTV Mobile — Stable 1.10.142 — Live card tap-to-play — 2026-10-20

> **Trạng thái: ĐÃ XÁC NHẬN TRÊN THIẾT BỊ — ổn định theo phản hồi người dùng.** Bản 1.10.142 được người dùng cài đặt và xác nhận hoạt động OK. CI build và toàn bộ unit tests đều PASS.

## Thông tin bản phát hành

- **Version:** 1.10.142
- **versionCode:** 158
- **Stable branch:** `stable/mobile-1.10.142`
- **Source branch:** `work/mobile-1.10.142-live-card-tap-to-play-20261020`
- **Baseline:** candidate 1.10.141 — `work/mobile-1.10.141-live-card-compact-20261020`
- **GitHub Actions:** [Run 39353928422 — SUCCESS](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39353928422)
- **APK artifact:** [NM7-IPTV-Mobile-1.10.142-LIVE-TAP-PLAY](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39353928422/artifacts/12234623983), ID `12234623983`
- **APK SHA-256:** `2a1f4be3960ae378e646527d77c989f64d95c2c4132738e9dbd6bd3400668517`

## Thay đổi đã được xác nhận

- Chạm trực tiếp vào thẻ sự kiện Live để phát ngay bằng server đã lưu hoặc server mặc định; không cần mở hộp chọn server trước.
- Nút server độc lập, cao 36dp và rộng tối thiểu 100dp; nhãn là **Chọn server** hoặc **Đổi server** tùy trạng thái phát.
- Bấm nút server mở danh sách nguồn; chọn nguồn khác sẽ lưu lựa chọn và phát nguồn đó.
- Dòng “Đã live … phút” nằm cùng hàng với nút server, căn giữa dọc; bố cục gọn, không chồng lấn.
- Giữ màu nền Live `#D914263C`, viền `#304B6887`, góc bo 14dp và bố cục compact. Chiều cao tối thiểu mobile 74dp, TV 82dp.
- Giữ nguyên nguồn và chu kỳ refresh Live, gộp trận, EPG, xử lý playback, lựa chọn server đã lưu, yêu thích và menu thao tác chạm dài.

## Kiểm thử và xác nhận

- Build `:app:assembleMobileDebug`: **PASS**.
- Toàn bộ `:app:testMobileDebugUnitTest`: **PASS**.
- Regression guards về Live feed, fullscreen server picker, giao diện thẻ và thao tác chạm: **PASS**.
- Kiểm thử thực tế: **người dùng đã xác nhận bản APK hoạt động OK**.

---

# NM7 IPTV Mobile — Candidate 1.10.141 — Compact Live event cards — 2026-10-20

> **Trạng thái: CI PASS, candidate chờ kiểm thử trên thiết bị thật.** Bắt đầu từ candidate 1.10.140 đã đồng bộ màu nền Live. Phạm vi bản này chỉ thu gọn kích thước thẻ sự kiện Live để gần bằng thẻ kênh thông thường; không thay đổi logic Live/playback.

## Thông tin bản sửa

- **Version:** 1.10.141
- **versionCode:** 157
- **Branch:** `work/mobile-1.10.141-live-card-compact-20261020`
- **Base candidate:** 1.10.140 — `work/mobile-1.10.140-live-card-background-20261020`
- **Thay đổi giao diện:** thẻ Live minHeight 66dp; logo 58×36dp; tiêu đề một dòng; padding dọc 2dp; nút server 24dp; hàng thời lượng và nút yêu thích thu gọn theo kích thước hàng kênh.
- **Giữ nguyên:** nền Live trong suốt đồng bộ `#D914263C`, viền `#304B6887`, góc bo 14dp, URL EPG mặc định và toàn bộ logic feed/player.

## Vì sao thẻ Live quá cao?

So sánh mã nguồn và ảnh thiết bị cho thấy thẻ kênh thường dùng logo 58×36dp, tiêu đề một dòng, hàng tối thiểu 66dp và gần như không có padding dọc. Thẻ Live trước đó có logo 56×56dp, tiêu đề tối đa hai dòng, nút server cao 32dp, padding 9dp và khoảng cách đáy 4dp. Những thông số này làm mỗi thẻ Live cao hơn rõ rệt.

## Sửa đổi trong 1.10.141

- Thu gọn layout để hàng Live có chiều cao tương đương hàng kênh.
- Giữ tên trận trên một dòng và dùng ellipsis nếu tên quá dài, tránh một số trận kéo cao riêng một thẻ.
- Giữ đủ thông tin nhóm/môn thể thao, nhãn LIVE, số server, số phút đã live và nút yêu thích.
- Duy trì vùng bấm chọn server và yêu thích; không đụng đến adapter dữ liệu, gộp trận, lịch refresh, lựa chọn nguồn hoặc player.
- Cập nhật regression guard để kiểm tra kích thước thẻ và bảo đảm các kiểm tra Live/fullscreen cũ vẫn còn.

## Build và kiểm thử

- **GitHub Actions:** [Run 39351043416 — SUCCESS](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39351043416).
- **Artifact:** [NM7-IPTV-Mobile-1.10.141-COMPACT-LIVE](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39351043416/artifacts/12233590958), ID `12233590958`.
- **APK SHA-256:** `f2e66e0d5eaec49ba6991b3c50a25b5826e93fcd3e76ff081263d33919d11d6e`.
- Build `:app:assembleMobileDebug`: **PASS**; toàn bộ `:app:testMobileDebugUnitTest`: **PASS**.
- Regression guards cho nguồn Live, nút đổi server fullscreen và sizing thẻ Live: **PASS**.
- Kiểm thử runtime trên điện thoại thật vẫn cần người dùng xác nhận; không đánh dấu stable trước khi được xác nhận.

---

# NM7 IPTV Mobile — Candidate 1.10.140 — Live card background — 2026-10-20

> **Trạng thái: CI PASS, candidate chờ kiểm thử trên thiết bị thật.** Bắt đầu từ candidate 1.10.139; không sửa nhánh baseline/stable. Phạm vi chỉ đồng bộ màu nền và viền thẻ Live với các thẻ kênh thông thường.

## Thông tin bản sửa

- **Version:** 1.10.140
- **versionCode:** 156
- **Branch:** `work/mobile-1.10.140-live-card-background-20261020`
- **Base:** candidate 1.10.139 trên `work/mobile-1.10.139-default-epg-20261019`
- **Baseline feature commit:** `a14188fdd64476a57ac131be6f3457f2b50ce2be`
- **Phạm vi:** chỉ màu nền, viền và bán kính góc của thẻ sự kiện Live; giữ nguyên EPG mặc định và mọi hành vi khác.

## Chẩn đoán và thay đổi

Video cho thấy thẻ Live dùng nền gần đen không trong suốt (`#131419`), khác với nền mặc định của thẻ kênh (`#D914263C`). Viền Live (`#2C2D35`) cũng khác với viền thẻ kênh (`#304B6887`).

- Đổi `live_surface` thành `#D914263C`, cùng màu/alpha với nền mặc định `channel_card`.
- Đồng bộ viền thẻ Live thành `#304B6887` và góc bo thành `14dp`, như thẻ kênh.
- Không thay đổi `LiveEventAdapter`, bộ lọc feed, gộp sự kiện, chọn server, yêu thích, thời lượng live, player hoặc nguồn EPG.
- Đã tăng version lên 1.10.140 / versionCode 156 để phân biệt APK kiểm thử với 1.10.139.

## Build và kiểm thử

- **GitHub Actions:** [Run 39349112739 — SUCCESS](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39349112739).
- **Artifact:** [NM7-IPTV-Mobile-1.10.140-LIVE-CARD-BG](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39349112739/artifacts/12231959910), ID `12231959910`.
- **APK SHA-256:** `e1138bfb64eb9b459691f9fa78939633f32d6c5a4d8d81b07ef6c968771556ab`.
- Build `:app:assembleMobileDebug`: **PASS**; toàn bộ `:app:testMobileDebugUnitTest`: **PASS**.
- Regression guard cho nền/viền/góc bo của thẻ Live: **PASS**.
- Sau CI, cần cài APK và xác nhận nền Live cùng màu/độ trong suốt với tab nhóm kênh; đồng thời thử mở sự kiện, đổi server và yêu thích để loại trừ hồi quy giao diện/chức năng.
- Chưa đánh dấu stable cho tới khi kiểm thử thực tế được xác nhận.

---

## Lịch sử candidate trước đó

# NM7 IPTV Mobile — Candidate 1.10.139 — Default EPG — 2026-10-19

> **Trạng thái: CI PASS, đang là candidate để kiểm thử trên thiết bị; chưa đánh dấu STABLE.** Bản này được build từ đúng baseline `build/mobile-1.10.138-optimized-20261015`. Chỉ thay đổi mặc định lịch phát sóng EPG; không merge/ghi đè nhánh gốc.

## Thông tin bản build

- **Version:** 1.10.139
- **versionCode:** 155
- **Branch:** `work/mobile-1.10.139-default-epg-20261019`
- **Baseline branch:** `build/mobile-1.10.138-optimized-20261015`
- **Baseline commit:** `584aed3ae20f1af7b32ca17e7319ba3dbd7a17cc`
- **Build commit:** `a14188fdd64476a57ac131be6f3457f2b50ce2be`
- **GitHub Actions:** [Run 39192034290 — SUCCESS](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39192034290)
- **Artifact:** [NM7-IPTV-Mobile-1.10.139-OPTIMIZED](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39192034290/artifacts/12160098395)
- **Artifact ID:** `12160098395`
- **APK SHA-256:** `7fca08e2a1d6571a0a5972f5f84f5d38b1b6dc8cbc58f3d887e4a2e515628f74`

## Thay đổi duy nhất trong ứng dụng

- EPG mặc định: `https://epg.io.vn/epg.xml`.
- Khi mở ứng dụng hoặc chuyển sang nguồn playlist mới không khai báo EPG riêng, dùng URL mặc định trên.
- Nếu playlist có `url-tvg`/`x-tvg-url`, vẫn ưu tiên URL EPG được khai báo trong playlist như trước.
- Người dùng vẫn có thể nhập URL EPG thủ công hoặc xóa EPG trong giao diện hiện có.
- Không thay đổi tính năng IPTV, Live, YouTube, giao diện, playback hay các chức năng khác của baseline 1.10.138.

## Xác minh CI

- Build `:app:assembleMobileDebug`: **PASS**.
- Toàn bộ `:app:testMobileDebugUnitTest`: **PASS**.
- Regression checks cho Live feed và bộ chọn server toàn màn hình: **PASS**.
- Kiểm tra cấu hình phiên bản và EPG mặc định: **PASS**.
- SHA-256 APK khớp với checksum do workflow công bố.
- **Chưa xác nhận runtime trên thiết bị thật.** CI PASS không thay thế việc cài đặt và kiểm thử phát kênh thực tế.

## Quy tắc giữ baseline

- Giữ nguyên nhánh gốc `build/mobile-1.10.138-optimized-20261015` tại commit `584aed3ae20f1af7b32ca17e7319ba3dbd7a17cc`.
- Không merge candidate vào nhánh gốc và không thay đổi stable branch khi chưa được yêu cầu.
- Chỉ đánh dấu 1.10.139 là ổn định sau khi người dùng kiểm thử thực tế và xác nhận.

---

# LỊCH SỬ — STABLE BASELINE 1.10.136 — 2026-10-14

> **Tại thời điểm 2026-10-14, 1.10.136 là bản ổn định chính thức.** Người dùng đã cài bản thử nghiệm, xác nhận nút đổi server ở player toàn màn hình hoạt động đúng; bản đã qua build và toàn bộ unit tests.

## Mốc ổn định đã chốt

- **Version:** 1.10.136
- **versionCode:** 152
- **Stable branch:** `stable/mobile-1.10.136`
- **Previous stable:** `stable/mobile-1.10.135` được giữ nguyên làm mốc lịch sử; `stable/mobile-1.10.128` cũng vẫn được giữ nguyên.
- **Development/candidate branch đã kiểm thử:** `fix/mobile-1.10.136-live-fullscreen-server`
- **Feature commit:** `a72bece44b89afa1bf4a79782ec6884f26739cc3`
- **Commit CI xác nhận:** `1d74b8fa1f023c818b13f3f972316c1c3f70afff` (CI run thành công; thay đổi sau feature commit chỉ là chú thích kích hoạt lại workflow).
- **Promotion:** stable release commit có hai parent, giữ cả lịch sử stable 1.10.135 và candidate đã kiểm thử.

## Thay đổi được phát hành

- Khi xem trận trong mục **Live** ở chế độ toàn màn hình, nút **`Server n/N ▾`** nằm ngay trong hàng điều khiển phủ trên video, cạnh nhóm nút điều khiển player.
- Nút chỉ xuất hiện khi sự kiện Live có từ hai nguồn phát trở lên; kênh thông thường hoặc chỉ có một nguồn sẽ không hiện.
- Nút mở lại hộp chọn server hiện có, tiếp tục dùng luồng lưu server và chuyển nguồn playback sẵn có; không tạo cơ chế đổi server thứ hai.
- Nút đổi server bên dưới player ở chế độ xem thông thường vẫn được giữ nguyên.
- Giữ các tính năng của 1.10.135: hiển thị số phút đã live, cập nhật thời lượng mỗi 60 giây và sắp xếp trận mới bắt đầu gần nhất lên trước.

## CI / APK đã xác minh

- **GitHub Actions:** [Run 38467292630 — SUCCESS](https://github.com/phuongnm7/iptv-player-android/actions/runs/38467292630)
- **Artifact:** [NM7-IPTV-Mobile-1.10.136-LIVE-FULLSCREEN-SERVER](https://github.com/phuongnm7/iptv-player-android/actions/runs/38467292630/artifacts/11849217586)
- **Artifact ID:** `11849217586`
- **SHA-256:** `8dd23d2d80fbb569b008522ddf1d381d1b46ca71a561c8e0b5490c8c64688230`
- APK build và toàn bộ Mobile unit tests đều PASS.
- Regression guard: **26 kiểm tra Live** và **7 kiểm tra riêng cho nút server fullscreen** đều PASS.
- Người dùng đã cài thử candidate và xác nhận OK.

## Quy tắc phát triển tiếp theo

1. Lấy `stable/mobile-1.10.136` làm baseline chính thức cho các thay đổi Mobile tiếp theo.
2. Giữ nguyên `stable/mobile-1.10.135` và `stable/mobile-1.10.128`; không ghi đè các mốc cũ.
3. Chỉ thay đổi NM7 IPTV Mobile theo phạm vi yêu cầu; không sửa NM7 TV Android, NM7 TV Web, playlist mặc định hoặc Cloudflare Worker.
4. Chạy build, unit tests và regression checks trước khi phát hành bản tiếp theo.

---

# LỊCH SỬ — STABLE BASELINE 1.10.135 — 2026-10-12 (đã được thay thế)

> 1.10.135 là bản ổn định trước đó và vẫn được giữ nguyên ở `stable/mobile-1.10.135`.

- **Version:** 1.10.135 / `versionCode 151`.
- **Promotion commit:** `b729d22cbd31c616fc92427412208f1c6d20159a`.
- **Feature:** mục Live hiển thị `Đã live N phút`; thời lượng lấy từ kickoff, không âm, ticker 60 giây; trận mới bắt đầu gần nhất được xếp trước.
- **CI:** [Run 38220807402 — SUCCESS](https://github.com/phuongnm7/iptv-player-android/actions/runs/38220807402).
- **Artifact:** [NM7-IPTV-Mobile-1.10.135-LIVE-DURATION](https://github.com/phuongnm7/iptv-player-android/actions/runs/38220807402/artifacts/11734067803).
- **SHA-256:** `e59efa2c6890794f074229190e3f5d096155c9b4dbd2110b1c0cfa112afc889e`.

---
## Lịch sử — Mobile 1.10.134: Live feed đang được người dùng kiểm thử (2026-10-12)

- **Trạng thái thực tế:** người dùng báo mục Live hiện đã lọc được các trận đang live; đang tiếp tục kiểm thử. Đây là kết quả ban đầu, **chưa xác nhận đã đủ tất cả trận** hoặc mọi luồng đều phát được.
- **Candidate:** 1.10.134 / `versionCode 150`, nhánh `work/mobile-live-feed-lau-style-20261012`. Không chuyển sang stable khi chưa có xác nhận kiểm thử cuối cùng.
- **Nguồn Live độc lập:** `https://thethaonm7.phuongnm7-iptv.workers.dev/playlist.m3u`. Mục Live lấy dữ liệu từ feed này, không phụ thuộc playlist người dùng đang chọn.
- **Các xử lý trong candidate:** làm mới feed có cache-busting; nhận dạng thêm định dạng ngày/giờ `HH:mm DD/MM` và `DD/MM HH:mm`; nhận dạng trạng thái live qua emoji xanh và marker văn bản như `[LIVE]`, `live now`, `currently live`, `đang live`, `trực tiếp`; giữ sự kiện được đánh dấu live tối đa 8 giờ trong khi dữ liệu thông thường vẫn theo cửa sổ 180 phút; loại marker trận đã kết thúc/đỏ; nới dung sai giờ kickoff để gộp cùng trận giữa các provider khi tên đội khớp; sửa hộp chọn server để hiện danh sách lựa chọn.
- **Kiểm thử tự động:** GitHub Actions [run 38185598470](https://github.com/phuongnm7/iptv-player-android/actions/runs/38185598470) thành công; build Mobile, toàn bộ unit test, kiểm tra tĩnh và bước chẩn đoán feed riêng đều PASS. APK artifact đã được tải về và SHA-256 đối chiếu thành công với file checksum của CI.
- **Giới hạn xác nhận:** CI không chứng minh dữ liệu live trên thiết bị đã đầy đủ hoặc mọi server phát được. Cần tiếp tục đối chiếu danh sách trận trong app với feed gốc và kiểm tra phát/chuyển server trên thiết bị.
- **Stable:** `stable/mobile-1.10.128` vẫn được giữ nguyên. Candidate chưa phải bản phát hành ổn định.
- **Việc tiếp theo:** trong lúc người dùng kiểm thử, ghi nhận các trận còn thiếu/trùng, trạng thái trận, server và kết quả phát; chỉ sửa tiếp khi có dữ liệu đối chiếu cụ thể. Không sửa NM7 TV Android, NM7 Web, URL playlist mặc định hoặc Cloudflare Worker trong công việc Mobile Live này.

---

# LỊCH SỬ — STABLE BASELINE 1.10.128 — 2026-10-08 (đã được thay thế)

> **1.10.128 từng là bản ổn định chính thức trước khi được thay thế bởi 1.10.135.** Người dùng đã test thực tế và xác nhận bản 1.10.128 ổn định tại thời điểm 2026-10-08.

## Mốc ổn định đã chốt

- **Version:** 1.10.128
- **versionCode:** 144
- **Stable branch:** `stable/mobile-1.10.128`
- **Development branch:** `work/mobile-youtube-ui-v128`
- **Build commit:** `ce19a4cb9ed65b3953107ffff8b2eaf9605ff74a`
- **Build:** GitHub Actions run `37776866236` — **SUCCESS**
- **Người dùng xác nhận:** **ổn định đến hiện tại**

## Phạm vi thay đổi của bản 1.10.128

- Sử dụng **logo YouTube do người dùng cung cấp**.
- Logo đã được tăng kích thước **nhẹ** theo ảnh thực tế người dùng gửi.
- Kích thước hộp logo: **84dp × 32dp**.
- Giữ nguyên vị trí, header, camera, tìm kiếm, chip, feed và các chức năng đã ổn định.
- **Không thay đổi logic IPTV/YouTube, playback, navigation hoặc Android TV.**

## CI / APK cuối cùng đã chốt

- Workflow: **NM7 IPTV Mobile 1.10.128 Exact YouTube Wordmark — Build**
- Run ID: **37776866236 — SUCCESS**
- Artifact: **NM7-IPTV-Mobile-1.10.128-FINAL**
- Artifact ID: **11550202229**
- Artifact size: **69,592,875 bytes**
- Artifact SHA-256: `f61beb800c4bcd10f4fff57082dc99f871d54716c74d972227cd8b2802f4db0d`
- [GitHub Actions](https://github.com/phuongnm7/iptv-player-android/actions/runs/37776866236)
- [APK Artifact](https://github.com/phuongnm7/iptv-player-android/actions/runs/37776866236/artifacts/11550202229)

## Quy tắc phát triển được áp dụng tại thời điểm 1.10.128

1. **Tại thời điểm đó, 1.10.128 là baseline để phát triển bản kế tiếp.** Baseline hiện tại là 1.10.135 trên `stable/mobile-1.10.135`.
2. Baseline nguồn là commit **`ce19a4cb9ed65b3953107ffff8b2eaf9605ff74a`** và stable branch **`stable/mobile-1.10.128`**.
3. Không tự ý quay lại 1.10.127, 1.10.123, 1.10.112 hoặc baseline cũ hơn nếu người dùng không yêu cầu.
4. Không thay đổi các phần đã được người dùng xác nhận ổn định nếu không có yêu cầu cụ thể hoặc bằng chứng regression.
5. Bản sửa tiếp theo phải tăng version/versionCode và phải build/verify trên GitHub Actions trước khi giao.
6. Chỉ khi người dùng test thực tế và xác nhận bản mới ổn định thì mới chuyển stable baseline sang bản mới.

## Trạng thái tại thời điểm 2026-10-08

**STABLE khi đó — 1.10.128; đã được thay thế bởi 1.10.135 ngày 2026-10-12.**

Logo YouTube hiện đã đúng nguồn hình người dùng cung cấp và đã được tăng nhẹ kích thước theo yêu cầu cuối cùng. Bản này được **chốt làm mốc ổn định đến hiện tại**.

---

## Lịch sử ứng viên — 1.10.132 LauTV-style Live Events (CI PASS, chờ test thiết bị)

## Lịch sử ứng viên — 1.10.132 LauTV-style Live Events (CI PASS, chờ test thiết bị)

- **Mục tiêu:** đưa mục `⚽ LIVE (n)` vào hàng chip trên màn hình Kênh, theo mẫu LauTV trong video người dùng cung cấp.
- **Nhánh:** `work/mobile-live-events-v132`; dựa trên nhánh candidate Mobile 1.10.130 tại commit `d62f9ea5b106ab3b7e1cb47ffdc0642d466abc48`.
- **Version:** 1.10.132 / `versionCode 148`. Candidate riêng, chưa stable; nhánh `stable/mobile-1.10.128` giữ nguyên.
- **Thẻ Live:** một sự kiện có tiêu đề, logo, badge LIVE, nhóm và số server. Nhiều entry cùng trận được gộp; server phụ có thể đến từ entry `Server 1/2` hoặc metadata `#NM7-ALT-URL:`.
- **Điều khiển:** nhấn thẻ phát bằng inline player Mobile hiện có; nút số server mở bộ chọn máy chủ chính/dự phòng; yêu thích và máy chủ đã chọn được lưu cục bộ.
- **Nguồn dữ liệu:** playlist M3U đang dùng. Nhận diện nhóm LIVE/Event/Sự kiện và trận `vs` trong nhóm thể thao; không thay URL playlist mặc định và không đụng dự án NM7 TV Android/Web.
- **CI PASS:** [Run 38156013028](https://github.com/phuongnm7/iptv-player-android/actions/runs/38156013028), mã `de28e3b3f11f6129c13716f948340fc3fecc17c0`. Build Mobile + full unit test suite thành công; không đưa vào stable.
- **Artifact:** `NM7-IPTV-Mobile-1.10.132-LIVE-EVENTS`, ID `11707969756`. APK SHA-256: `6b56db820849d902af8a94ddcd98bb2e3bf734de48a59db174f9e0445b737b84`.
- **Chưa runtime PASS:** CI không thay thế việc cài APK trên điện thoại thật. Cần xác nhận chip LIVE, card sự kiện, chọn server, phát inline và yêu thích trước khi chốt STABLE.

---


## Lịch sử ứng viên — 1.10.133 Dedicated Live Feed (trước khi được chốt 1.10.135)

- **Nhánh làm việc:** `work/mobile-live-feed-lau-style-20261012`.
- **Phiên bản ứng viên:** 1.10.133 / `versionCode 149`. Nhánh stable `stable/mobile-1.10.128` không thay đổi.
- **Phạm vi:** chỉ NM7 IPTV Mobile. Không đổi NM7 TV Android, NM7 Web, default playlist của IPTV, hoặc Cloudflare Worker.
- **Nguồn Live độc lập:** `https://thethaonm7.phuongnm7-iptv.workers.dev/playlist.m3u`. Mục Live không lấy danh sách trận từ nguồn playlist do người dùng đang chọn.
- **Nhận diện:** phân tích giờ/ngày, trạng thái trận và cặp đội; chỉ hiển thị fixture được đánh dấu đang diễn ra (🟢) và trong cửa sổ thời gian 180 phút. Trận sắp diễn ra (🟡), trận cũ hoặc entry không nhận diện được giờ/trận không được đưa vào Live.
- **Gộp sự kiện:** hợp nhất tên đội có biến thể phổ biến giữa các provider, giờ đá lệch nhẹ, tên bình luận viên và định dạng HLS/FLV; mỗi URL duy nhất trở thành một server của cùng một trận.
- **Thẻ sự kiện:** ưu tiên logo từ metadata của feed, tên trận đã chuẩn hóa, trạng thái LIVE, môn thể thao, số server và yêu thích.
- **Player:** có thể đổi server ngay trong inline player Mobile; player Activity dự phòng cũng có nút chọn server. Lựa chọn server và yêu thích được lưu cục bộ. Feed cache riêng và làm mới mỗi 5 phút.
- **Khả năng phục hồi:** nếu mạng lỗi, dùng M3U Live cache riêng và vẫn lọc theo thời gian hiện tại; không ghi dữ liệu Live vào playlist/session của người dùng.
- **CI PASS:** [Run 38161961835](https://github.com/phuongnm7/iptv-player-android/actions/runs/38161961835), mã nguồn/workflow `ef40439eb7995a0b4caaa42e6d82549cda750217`. 18/18 điều kiện kiểm tra tĩnh PASS; build `:app:assembleMobileDebug` và toàn bộ `:app:testMobileDebugUnitTest` đều thành công.
- **Artifact:** `NM7-IPTV-Mobile-1.10.133-LIVE-FEED`, ID `11710189331`. APK SHA-256: `a364e0424ea47c77f5fb75265dc64fa6284d55cef055ad0a2b50721e560ce6b8`.
- **Chưa runtime PASS:** chưa xác nhận phát mọi server trên thiết bị thật; CI không chứng minh từng luồng còn hoạt động. Giữ là candidate, chưa chuyển stable.
- **Quy tắc phát hành:** candidate không tự động thành stable; không merge hoặc đổi stable nếu chưa qua CI và chưa kiểm thử thực tế.

> **1.10.128 là mốc ổn định chính thức hiện tại.** Mọi bản Mobile tiếp theo phải lấy đúng commit nền dưới đây; không tự ý quay lại baseline cũ.

## Mốc ổn định chính thức

- **Version:** 1.10.128
- **versionCode:** 144
- **Stable branch:** `stable/mobile-1.10.128`
- **Development branch:** `work/mobile-youtube-pull-refresh-v130`
- **Baseline commit:** `ce19a4cb9ed65b3953107ffff8b2eaf9605ff74a`
- **GitHub Actions run:** `37776866236` — **SUCCESS**
- **Artifact:** `NM7-IPTV-Mobile-1.10.128-FINAL`
- **Artifact ID:** `11550202229`
- **Baseline status:** giữ nguyên; bản candidate mới không tự động được chốt STABLE.

## Phạm vi đã chốt ở 1.10.128

- Logo YouTube được tăng nhẹ theo ảnh người dùng gửi, hộp logo 84dp × 32dp.
- Giữ nguyên layout, phát IPTV/YouTube, điều hướng, trạng thái player và Android TV.
- Chỉ đổi mốc STABLE sau khi người dùng kiểm thử bản mới và xác nhận ổn định.

## Ứng viên tiếp theo — 1.10.130 YouTube pull-to-refresh

- **Version candidate:** 1.10.130
- **versionCode:** 146
- **Development branch:** `work/mobile-youtube-pull-refresh-v130`
- **Nguồn phát triển:** tiếp tục từ nhánh candidate, giữ nguyên mốc STABLE `stable/mobile-1.10.128`.
- **Lý do làm lại:** người dùng xác nhận 1.10.129 không có thay đổi runtime so với 1.10.128; build thành công trước đó nhưng chưa đạt tiêu chí tính năng.
- **Cách xử lý:** giữ `SwipeRefreshLayout` làm indicator/handler chuẩn, bổ sung bộ theo dõi gesture tại `BrowseActivity.dispatchTouchEvent` để xử lý trường hợp feed Home có hàng RecyclerView lồng ngang.
- **Điều kiện kích hoạt:** chỉ khi touch bắt đầu trong content Trang chủ, section đang là `MediaGroup.TYPE_HOME`, feed ở đầu; yêu cầu kéo dọc xuống tối thiểu 96dp và loại trừ vuốt ngang.
- **Tải lại:** dùng `BrowsePresenter.refresh(false)`, không dựng lại Activity, không chủ động đổi tab hoặc player; timeout 20 giây để tránh spinner bị kẹt.
- **Kiểm chứng CI:** phải qua verifier riêng cho đường đi gesture, build và kiểm tra mã trong APK. Build success chưa được xem là runtime pass.
- **Baseline:** 1.10.128 vẫn giữ nguyên, không chuyển stable cho tới khi người dùng xác nhận bản mới chạy thật trên máy.

## APK ổn định hiện tại

- [GitHub Actions 1.10.128](https://github.com/phuongnm7/iptv-player-android/actions/runs/37776866236)
- [Artifact 1.10.128](https://github.com/phuongnm7/iptv-player-android/actions/runs/37776866236/artifacts/11550202229)

---

# LỊCH SỬ CŨ
# NM7 IPTV Mobile — STABLE BASELINE 1.10.112 — 2026-09-30

> **BẢN ỔN ĐỊNH HIỆN TẠI:** 1.10.112. Bản này được chốt làm mốc ổn định tạm thời tại thời điểm hiện tại. **Mọi bản Mobile/build tiếp theo phải lấy chính xác bản này làm nền**, không tự ý lấy một build 1.10.112 khác hoặc một baseline cũ hơn.

## Mốc build ổn định được khóa

- Version: **1.10.112**
- versionCode: **128**
- Workflow: **NM7 IPTV Mobile 1.10.112 Final Build**
- Workflow file: `.github/workflows/nm7-mobile-112-final.yml`
- GitHub Actions run: **#15**
- Commit nguồn của build: **`2ee95f0f4906f0aaecc0748f1e13322b5531afa6`**
- Commit message: `ci: prepare 1.10.112 build`
- Kết quả: **SUCCESS**
- Thời gian: **6m 4s**
- Artifacts: **1**
- Artifact name: **NM7-IPTV-Mobile-FINAL**
- Nhánh khóa baseline: `stable/mobile-1.10.112`

### Quy tắc phát triển từ 1.10.112

1. Bản 1.10.112 ở commit **2ee95f0...** là baseline Mobile chính thức hiện tại.
2. Các bản build sau phải phát triển trực tiếp từ `stable/mobile-1.10.112`.
3. Không dùng một build 1.10.112 khác làm nền.
4. Không lấy `main` hiện tại làm nền nếu `main` đã có các commit phát sinh sau mốc 1.10.112.
5. Mỗi bản mới phải tăng version/versionCode phù hợp và phải được CI build/verify trước khi bàn giao.
6. Chỉ khi người dùng test thực tế và xác nhận một bản mới ổn định thì mới thay đổi stable baseline.

Chi tiết lịch sử kỹ thuật và các mốc trước đây được giữ nguyên bên dưới và trong [PROGRESS.md](PROGRESS.md).

---

# NM7 IPTV Mobile — TRẠNG THÁI HIỆN TẠI — 2026-09-27

> **1.10.106 đã BUILD THÀNH CÔNG trên GitHub Actions #781.** Đây là bản xử lý trực tiếp 3 lỗi người dùng đang phản ánh: video YouTube mở còn chậm, quay lại app sau khi chạy nền phải tải lại video, và pipeline phát chưa mượt. 1.10.106 đã PASS patch, verifier, lifecycle regression checks, unit tests và APK upload. **Chưa đánh dấu runtime PASS** cho đến khi người dùng test thiết bị thật.

## 1.10.106 — Kết quả CI mới nhất

- Version: **1.10.106**
- versionCode: **122**
- Branch: `fix/mobile-1.10.79-youtube-performance`
- Commit CI: `8347399bb2a8c8937eb3c575e5932df876f5e035`
- GitHub Actions: **NM7 Mobile Final Build #781 — SUCCESS**
- Run ID: `36296325937`
- Build Mobile: **SUCCESS**
- Unit tests upload: **SUCCESS**
- APK upload: **SUCCESS**
- Artifact: `NM7-IPTV-Mobile-FINAL`
- Artifact ID: `10923991428`
- Mobile artifact SHA-256: `03f084bf2f8bdc079fecaed3e66e902c1d91a33ef620fb1ddabc584613947e3c`

## Thay đổi 1.10.106

### 1. Giữ nguyên player khi chạy nền
- Khi bấm Home/chuyển ứng dụng, không block ExoPlayer.
- Không gọi đường `onViewPaused()` của presenter trong background transition thông thường.
- Khi quay lại app, ưu tiên sử dụng **cùng ExoPlayer + MediaItem + vị trí phát + buffer**, thay vì tạo lại phiên playback.
- Chỉ thực hiện restore/bind đặc biệt khi thực sự có mini/target restore.

### 2. Giảm việc tải lại format YouTube
- Tăng thời gian reuse format-info trong RAM từ **8 giây → 60 giây**.
- Đây chỉ là cache process-local; không ghi signed stream URL ra disk.
- Mục tiêu là khi video vừa phát xong/đang phát rồi chuyển nền và quay lại, không phải thực hiện lại toàn bộ format-resolution path.

### 3. 4K
- **Không áp đặt giới hạn 4K/30fps hoặc bitrate một cách đoán mò.**
- Không thêm fallback 1440p tự động ở bản này.
- Nếu 4K vẫn giật sau test 1.10.106, bước tiếp theo phải đo codec, resolution, FPS, bitrate và decoder dropped frames của chính video test trước khi sửa.

## Người dùng đã xác nhận trước khi 1.10.106

Trên **1.10.102**, người dùng xác nhận:
- **Avatar YouTube:** PASS.
- **Status bar/player portrait:** PASS.
- **Spinner đen:** PASS.

Các lỗi còn lại người dùng báo:
- YouTube video load vẫn chậm.
- Browse/tab swipe vẫn chậm/lag.
- 4K playback vẫn giật/lag.
- Chạy nền rồi mở lại video phải load lại.

Các phần trên phải tiếp tục được bảo vệ; không được làm regression avatar/status bar/spinner.

## Điều kiện đánh giá 1.10.106

CI PASS **không đồng nghĩa runtime PASS**. Người dùng cần kiểm tra tối thiểu:
1. Mở một video YouTube → thời gian từ tap tới phát.
2. Đang phát → Home/chuyển app → quay lại → **video phải tiếp tục tại vị trí cũ, không tải lại**.
3. Vuốt/chuyển Browse YouTube nhiều lần.
4. Phát video 4K, quan sát giật/rớt frame.
5. Xác nhận avatar, status bar và spinner vẫn như 1.10.102.

Chi tiết kỹ thuật và lịch sử nằm trong `PROGRESS.md`.

---

# Mobile 1.10.91 / versionCode 107 — STABLE BASELINE — USER TEST CONFIRMED

**1.10.91 là bản Mobile tốt nhất/ổn định nhất hiện tại theo kết quả test thực tế mới nhất của người dùng.** Từ thời điểm này, mọi bản Mobile tiếp theo phải phát triển trực tiếp trên nền **1.10.91**, không quay lại các baseline cũ nếu không có yêu cầu đặc biệt.

- Version: **1.10.91**
- versionCode: **107**
- Branch: `fix/mobile-1.10.83-youtube-player-ui`
- Source commit đã build: `60f3be09859f608b23b40b066a9da9168f7b0b7b`
- **NM7 Mobile Final Build #611 — SUCCESS**
- Run: `36104328880`
- Artifact: `NM7-IPTV-Mobile-FINAL`
- Artifact ID: `10850193056`
- [GitHub Actions #611](https://github.com/phuongnm7/iptv-player-android/actions/runs/36104328880)
- [Tải artifact APK ARM64/ARMv7](https://github.com/phuongnm7/iptv-player-android/actions/runs/36104328880/artifacts/10850193056)

## Trạng thái test thiết bị

Người dùng đã cài và test **1.10.91** trên thiết bị thật. Kết luận hiện tại: **đây là bản tốt nhất để làm nền phát triển tiếp theo**.

Các phần đang được giữ nguyên:
- IPTV và danh sách kênh, bao gồm việc kênh cuối không còn bị thanh YouTube/IPTV đè.
- Android status bar trong player YouTube.
- Avatar video/kênh YouTube.
- Live chat.
- Thumbnail không còn bị treo khi video đã phát.
- Tốc độ tải/phát YouTube đã được tối ưu so với các baseline trước.

Lỗi còn lại đã biết:
- Khi mở video YouTube vẫn còn **một nhịp nháy nhẹ của khung hình đầu/chuyển cảnh**. Đây là lỗi ưu tiên cho các bản sau, nhưng **không thay đổi baseline 1.10.91**.
- Thumbnail ở một số đường chuyển tiếp vẫn cần tiếp tục tối ưu độ nét/tốc độ nếu có bằng chứng regression.

## Quy tắc baseline từ 1.10.91

1. Mọi build Mobile mới phải bắt đầu trực tiếp từ **1.10.91 / versionCode 107**.
2. VersionCode/versionName tiếp theo tăng tuần tự từ 107.
3. Không sửa lại các chức năng đã ổn định ở 1.10.91 nếu không có yêu cầu hoặc bằng chứng regression.
4. Khi xử lý lỗi nháy mở video, chỉ thay đổi pipeline transition/player-loading liên quan; phải giữ nguyên IPTV, status bar, avatar, live chat và các phần ổn định khác.
5. Mỗi bản mới phải được CI build/verify trước khi giao APK.
6. Chỉ sau khi người dùng test thực tế mới đánh dấu một bản mới là baseline.

Xem lịch sử kỹ thuật và từng lỗi đã xử lý trong [PROGRESS.md](PROGRESS.md).

---

# Trạng thái Mobile — 1.10.72 / versionCode 90 — STABLE BASELINE

**1.10.72 được chốt làm bản ổn định tạm thời để các bản Mobile tiếp theo phát triển từ đây.**

- Mobile Final Build **#502: SUCCESS** — run 35699011527.
- Commit: 771d599fe6be23ebfeb1ac6a319f50c05279501b.
- APK artifact: https://github.com/phuongnm7/iptv-player-android/actions/runs/35699011527/artifacts/10682325671
- Artifact `NM7-IPTV-Mobile-FINAL`, khoảng 62.13 MB.
- Artifact SHA-256: `e3407a549dd46dc0f4151de12693ee13ad249de169bb06e3550249375003e7f7`.
- Bottom navigation: đúng **2 tab YouTube + IPTV**.
- Đây là **Mobile-only**; không thay đổi Android TV.

### Lỗi đã biết, tạm hoãn

**Avatar kênh YouTube trên thẻ video chưa hiển thị.** Người dùng đã kiểm tra APK thực tế và xác nhận lỗi này. Lỗi được ghi nhận để sửa ở bản sau, không coi 1.10.72 đã hết lỗi avatar.

### Quy tắc phiên bản từ 1.10.72

Bản 1.10.72 là baseline. Bản kế tiếp phải là **1.10.73 / versionCode 91**, sau đó tăng tuần tự cho mọi build mới. Không giữ nguyên version cũ khi build.

Chi tiết lịch sử, lỗi, commit, CI và kế hoạch sửa tiếp theo: [PROGRESS.md](PROGRESS.md).

---

# iptv-player-android

Nm7 IPTV 1.7 — ứng dụng Android tiếng Việt cho mobile và Android TV, không quảng cáo, không phân tích hành vi, không máy chủ trung gian. Màn hình chính có thanh nhóm kênh cuộn ngang; khi đang xem trên TV, phím Trái mở danh sách kênh nhanh phủ lên video mà không dừng phát.

Mốc công việc, lỗi đã sửa, kết quả xác minh và bước tiếp theo: [PROGRESS.md](PROGRESS.md). Đọc file này trước khi tiếp tục ở một phiên khác; không chỉ dựa vào lịch sử trò chuyện hay thư mục tạm.

> **Trạng thái Mobile hiện tại: 1.10.61 build #380 SUCCESS, chờ test thiết bị.** Người dùng test dài hơn và ghi nhận mini của 1.10.60 chuyển đen/báo phiên kết thúc. 1.10.61 sửa các đường báo hết video sớm và giữ mini phát lại khi hết thật. Nhánh `stable/mobile-1.10.60` giữ nguyên làm mốc lịch sử; xem [PROGRESS.md](PROGRESS.md).

## Nhận APK

**1.10.61:** [Tải APK ARM64/ARMv7 và SHA256SUMS — build #380](https://github.com/phuongnm7/iptv-player-android/actions/runs/35558054355/artifacts/10620709984), hết hạn 2026-10-21. Compile/unit tests/đóng gói thành công; **chờ test lại trên thiết bị, chưa stable**. Source `c37977c`.

**1.10.60:** [Tải APK ARM64/ARMv7 và SHA256SUMS — build #379](https://github.com/phuongnm7/iptv-player-android/actions/runs/35545715388/artifacts/10616478461), hết hạn 2026-10-20. Unit tests/build thành công; **đã từng được xác nhận ổn định tạm thời, sau đó test dài hơn tái hiện lỗi mini; giữ làm nền đối chiếu**. Source commit: `3349081`.

**1.10.59:** [Tải APK ARM64/ARMv7 và SHA256SUMS — build #378](https://github.com/phuongnm7/iptv-player-android/actions/runs/35542810501/artifacts/10615413353), hết hạn 2026-10-20. Unit tests/build thành công; **chờ test thiết bị thật, chưa stable**. Commit đã build: `22c0300`.

**1.10.58:** [Tải APK ARM64/ARMv7 và SHA256SUMS — build #375](https://github.com/phuongnm7/iptv-player-android/actions/runs/35519495736/artifacts/10607738558), hết hạn 2026-10-20. Unit tests/build thành công; **chờ test thiết bị thật, chưa stable**. **1.10.57:** build #373 thành công theo ảnh người dùng, còn lỗi trên thiết bị. Các liên kết phiên bản cũ dưới đây giữ để đối chiếu, không phải bản stable.

**1.10.56:** [Tải APK ARM64/ARMv7 và SHA256SUMS — build #372](https://github.com/phuongnm7/iptv-player-android/actions/runs/35510885621/artifacts/10605087490), hết hạn 2026-10-20. Unit tests/build thành công; **chờ test thiết bị thật, chưa stable**. Liên kết 1.10.55 bên dưới là bản cũ còn lỗi.

**Mobile 1.10.55 / versionCode 73:** [build #370 thành công](https://github.com/phuongnm7/iptv-player-android/actions/runs/35504581687). [Tải APK ARM64/ARMv7 và SHA256SUMS](https://github.com/phuongnm7/iptv-player-android/actions/runs/35504581687/artifacts/10603078762) — artifact hết hạn 2026-10-20. Chưa xác nhận stable trên thiết bị thật. APK 1.10.54 đã được người dùng cài thử nhưng còn lỗi, không phải bản stable.

Trong GitHub, mở **Actions → NM7 Mobile Final Build → #379**, tải **NM7-IPTV-Mobile-FINAL** trong Artifacts, giải nén và chọn APK **1.10.60** phù hợp kiến trúc ARM64 hoặc ARMv7 trên Android 6.0 trở lên.

APK dùng chữ ký debug dành cho cài thử cá nhân; không phải bản phát hành Google Play. Không tắt Play Protect. Nếu Android yêu cầu, chỉ cho phép cài APK từ ứng dụng tải tệp mà bạn tin cậy rồi tắt lại quyền đó sau khi cài.

Mỗi máy build có thể sinh khóa debug khác nhau. Nếu Android báo chữ ký không khớp ở lần cài sau, hãy xuất playlist trước khi gỡ bản cũ (gỡ ứng dụng sẽ xóa dữ liệu cục bộ). Bản phát hành cập nhật lâu dài cần khóa ký ổn định lưu trong GitHub Secrets, không đưa khóa vào mã nguồn.

Workflow chỉ chạy khi kho riêng tư; không tạo GitHub Pages hoặc release công khai. APK artifact giữ 30 ngày, báo cáo test giữ 14 ngày. Việc lưu bản APK riêng bên ngoài GitHub không phụ thuộc thời hạn artifact.

## Sử dụng

- Dán URL playlist rồi chọn **Tải URL**, hoặc **Mở tệp** M3U UTF-8 (tối đa 8 MB).
- Sau khi tải, bảng nguồn tự thu gọn để danh sách kênh chiếm phần lớn màn hình; bấm **+ Nguồn** để mở lại.
- Bấm **☰ Link** để lưu tối đa 50 URL playlist, đặt tên, chọn nguồn cần tải hoặc xóa nguồn không dùng. URL chỉ lưu trong vùng app-private.
- Bấm **⚙** để chọn giao diện Tự động/Mobile/TV, hình nền, URL, mật độ hàng, FPS, đồng hồ và lịch sử.
- Với link luồng phát trực tiếp, dán vào cùng ô rồi bấm **Phát URL trực tiếp**.
- Tìm tên/nhóm/URL, lọc nhóm. Chạm kênh để phát; nhấn giữ kênh để xem/copy URL đầy đủ.
- Bật/tắt ô chọn, dùng **Chọn đang lọc** hoặc **Bỏ chọn đang lọc**, rồi **Xuất M3U sạch**.
- Mục trùng đúng URL, header và thuộc tính phát bị bỏ; tên đường dẫn, query token và giá trị header giữ nguyên chữ hoa/thường. Mục thiếu/sai URL được đếm và bỏ.
- Header HTTP (User-Agent, Referer, Origin, Cookie…), EXTINF gốc và các tùy chọn kênh được giữ khi xuất. Thuộc tính EPG toàn playlist chưa được quản lý trong bản này.
- Trong trình phát: **Định dạng** dùng khi máy chủ không có đuôi .m3u8/.mpd; **Chất lượng** đặt trần chất lượng; bộ điều khiển Media3 có chọn âm thanh/phụ đề nếu luồng cung cấp.
- Nút **↻** chọn tự động/ngang/dọc; **Khung hình** chọn Fit/Zoom/Fill. Chỉ số FPS thực tế lấy từ số khung hình video đã render trong mỗi khoảng 2 giây.
- Android TV có launcher Leanback. Ở giao diện TV, dùng D-pad để di chuyển; nút, tab và hàng kênh đang chọn có viền xanh sáng.
- DRM: đọc Widevine/ClearKey/PlayReady và URL/header giấy phép từ KODIPROP; có thể bổ sung cấu hình bằng nút DRM. Chỉ dùng giấy phép hoặc ClearKey hợp lệ mà bạn được cấp quyền.
- Playlist và lựa chọn lưu trong vùng riêng của ứng dụng trên thiết bị. Android backup và chuyển dữ liệu hệ thống bị loại trừ. Không có playlist hay thông tin đăng nhập cá nhân được đóng gói vào APK.

## Hỗ trợ và giới hạn

| Nguồn | Mức hỗ trợ |
| --- | --- |
| HLS / M3U8, DASH / MPD, SmoothStreaming | Media3, tùy container/codec |
| HTTP / HTTPS (MPEG-TS, MP4, MKV…) | Tùy định dạng Media3 và decoder thiết bị |
| RTSP | RTP qua TCP, các codec RTSP Media3 hỗ trợ |
| RTMP | Qua module RTMP Media3; RTMPS chưa bật |
| UDP MPEG-TS | Cần mạng cho phép unicast/multicast; có Wi-Fi multicast lock |
| Full HD / QHD 1440p / 4K UHD 2160p | Cần nguồn có độ phân giải đó, băng thông và decoder phù hợp |
| SRT, RTP URL trần, AceStream/SopCast, giao thức riêng | Chưa hỗ trợ |
| Widevine / ClearKey | Phát khi playlist hoặc người dùng cung cấp cấu hình giấy phép hợp lệ |
| PlayReady | Tùy thiết bị Android TV và định dạng; thiết bị phải hỗ trợ hệ DRM này |
| Mẫu license tùy biến Kodi | Chưa hỗ trợ; cần endpoint trả phản hồi DRM trực tiếp |
| Android TV launcher và D-pad | Có; chế độ TV/Tự động, focus sáng rõ. Chưa tối ưu riêng cho mọi mẫu remote |
| EPG, catch-up, tài khoản Xtream | Chưa có trong bản này |

Ứng dụng không đảm bảo mọi giao thức/codec và không nâng một nguồn HD thành 4K. HTTPS không tự chuyển xuống HTTP; nguồn HTTP trực tiếp được cho phép vì IPTV cũ thường cần. Đừng chia sẻ URL có token hoặc mật khẩu.

## Build và kiểm thử

Yêu cầu JDK 17, Gradle 8.13, Android SDK 36, Build Tools 36.0.0. Android Gradle Plugin 8.13.2, Media3 1.11.0. Phiên bản được cố định; GitHub Actions được ghim theo commit.

Lệnh build khi các công cụ đã có trong PATH:

    gradle --no-daemon testDebugUnitTest lintDebug assembleDebug

Dự án chưa kèm Gradle wrapper JAR. GitHub Actions tự cài Gradle và các gói SDK nên không cần Android Studio trên máy của bạn. Khi mở trong Android Studio, cấu hình bản Gradle 8.13 đã cài hoặc tạo wrapper bằng Gradle; không chỉ tải lại dự án rồi kỳ vọng wrapper tự xuất hiện.

Chạy kiểm tra core không cần Android SDK:

    javac -d /tmp/iptv-core app/src/main/java/vn/phuong/iptvplayer/Channel.java app/src/main/java/vn/phuong/iptvplayer/M3uParser.java tools/CoreCheck.java
    java -cp /tmp/iptv-core CoreCheck

CoreCheck có 36 kiểm tra; 23 test JUnit bao phủ parser, DRM metadata, header giấy phép, FPS, mã hóa định danh và kiểm tra link nguồn. CI còn chạy lint, build APK và xác minh chữ ký APK. Các bước này không chứng minh khả năng phát 4K hoặc truy cập một nhà cung cấp cụ thể trên điện thoại thật.

Workflow còn chạy `tools/android_smoke.py` trên emulator Android 15: mở app, nhập/tìm/lọc/chọn kênh, xem URL đầy đủ, xoay màn hình, giữ playlist sau khi tiến trình khởi động lại và phát MP4/HLS/DASH tổng hợp 320×180 qua localhost. Script tự tạo video bằng FFmpeg, không dùng playlist thật; ảnh chụp và kết quả được giữ trong artifact **Android-emulator-smoke** trong 14 ngày. Xem nhật ký để biết lần chạy nào đã đạt; không suy diễn các thử nghiệm này thành kiểm chứng 4K/RTSP/RTMP/UDP.

## Nguồn kỹ thuật

- AndroidX Media3: https://developer.android.com/media/media3/exoplayer/supported-formats
- Media3 release: https://developer.android.com/jetpack/androidx/releases/media3
- Media3 RTSP: https://developer.android.com/media/media3/exoplayer/rtsp

Chỉ dùng playlist và nội dung bạn có quyền truy cập.


# 1.10.103 — YouTube performance + 4K playback correction — 2026-09-27

## Phạm vi
1.10.103 tiếp tục trực tiếp trên branch `fix/mobile-1.10.79-youtube-performance` và giữ nguyên các behavior đã được người dùng xác nhận ở 1.10.102:
- Avatar YouTube.
- Status bar/player portrait.
- Spinner đen hard-disable.
- IPTV, navigation, mini-player và các chức năng Mobile khác.

## Source changes
- Giảm công việc lặp lại khi vuốt/chuyển section YouTube: section đã tải không chạy lại `BrowseProcessorManager` trên cache hit.
- Thêm cache nhẹ cho grid **chỉ khi page đã hoàn tất**, tránh bỏ qua continuation page.
- Rút ngắn poster/first-frame reveal gate để giảm thời gian cảm nhận khi mở video; vẫn chờ decoder render frame mới trước khi bỏ poster.
- Với **4K high-FPS (>=50fps)**, renderer Mobile dùng đường release SurfaceView giảm hiện tượng timestamp pacing gây stutter.
- Thêm watchdog 4K: chỉ khi video đã render ở 4K và decoder thực sự rơi frame liên tục mới hạ trần xuống 1440p để giữ playback usable. Không hạ chất lượng 4K trước khi phát hiện vấn đề.
- Không sửa avatar/status-bar/spinner trong patch performance.

## Version
- versionName: **1.10.103**
- versionCode: **119**
- Branch: `fix/mobile-1.10.79-youtube-performance`
- Source patch commit: `a799ece305f4559139c63e462957591254115843`
- Version bump commit: `c8a659d8a68405c29856a63f56a9df64ffd4131e`
- CI update commit: `ed4ea6f5cd7e1fc8c787e820719f1d2bbf13ec31`

## Trạng thái
Source 1.10.103 đã được đưa vào build chain. **Chưa đánh dấu PASS** cho đến khi GitHub Actions build thành công, kiểm tra generated source/verifier và người dùng test APK thật, đặc biệt với:
1. thời gian mở video YouTube;
2. vuốt/chuyển tab Browse liên tục;
3. video 4K trong cùng điều kiện như video test đã gửi.
