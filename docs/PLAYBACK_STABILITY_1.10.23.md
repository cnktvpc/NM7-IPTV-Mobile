# Hồ sơ ổn định phát IPTV — Mobile 1.10.23

Cập nhật: 2026-09-13  
Repo nguồn: `phuongnm7/iptv-player-android` (private), nhánh `main`  
Mục đích: làm cơ sở bảo trì Mobile và xử lý lỗi tương tự trên ứng dụng Android TV.

## Trạng thái xác nhận

- Phiên bản: `versionCode 40`, `versionName 1.10.23`.
- Người dùng xác nhận trên điện thoại thật rằng bản này ổn định hơn 1.10.22 và đang tiếp tục test dài hạn.
- CI xác nhận compile, unit test, lint, ký/đóng gói APK và smoke-test Android 15 thành công.
- Chưa tuyên bố mọi luồng IPTV đều không thể gián đoạn. Server/CDN, token hết hạn, manifest/segment lỗi hoặc codec vượt khả năng thiết bị vẫn có thể làm luồng dừng.
- Không ghi playlist riêng, URL chứa token, cookie, header bí mật hoặc khóa DRM vào tài liệu/log.

## Triệu chứng đã ghi nhận

### Luồng phát

- Kênh đang phát đứng hình, FPS về 0; Media3 có lúc báo `ERROR_CODE_TIMEOUT`.
- Bấm Play hoặc chuyển kênh rồi quay lại thì phát tiếp.
- 1.10.21 phát hiện decoder không xuất frame nhưng vẫn có thể dừng.
- 1.10.22 tự play lại, nhưng tạo vòng lặp phát → tải → phát; thường hiện “Đang tải luồng” hoặc “thử lần 1/6”.
- Một số lần phục hồi phát lặp lại đoạn vừa xem.
- Hiện tượng rõ hơn với kênh nước ngoài và luồng 4K dù kết nối Internet phía người dùng ổn định.

### Logo danh sách

- Logo nhấp nháy khi cuộn nhanh hoặc đổi nhóm.
- Hàng tái sử dụng xóa ImageView trước khi tải lại.
- Mỗi logo hoàn tất từng gọi `notifyDataSetChanged()`, làm toàn danh sách bind lại và tiếp tục chớp.

## Nguyên nhân trong các bản trước

### Watchdog chỉ dựa vào BUFFERING

Sau 10 giây BUFFERING, Player được `prepare()` hoặc tạo lại mà không phân biệt:

- request còn nhận byte hay đã treo;
- buffer còn bao nhiêu;
- vị trí phát có tiến triển không;
- decoder đứng trong khi mạng vẫn hoạt động;
- live offset đã ra ngoài cửa sổ phát hay chưa.

Việc reset `recoveryAttempts` sau 8 giây phát khiến nguồn lỗi lặp lại luôn quay về cấp phục hồi đầu, không nâng cấp chiến lược.

### Seek live edge và tái tạo HTTP quá thường xuyên

- `seekToDefaultPosition()` ở mọi lần phục hồi có thể quay lại segment/điểm mặc định, tạo cảm giác phát lặp.
- Tạo Player/DataSource mới liên tục làm mất socket đang ấm, DNS/TLS/keep-alive và tăng xác suất timeout với máy chủ xa.

### Cache logo chưa đúng cấp

Cache RAM không giải quyết được nhấp nháy nếu callback vẫn làm mới toàn adapter. Nhiều hàng chờ cùng URL cũng chưa được cập nhật theo nhóm.

## Kiến trúc sửa trong 1.10.23

### Duy trì kết nối

Media3 Mobile chuyển sang `OkHttpDataSource` với một `OkHttpClient` dùng chung:

- pool tối đa 8 kết nối;
- giữ kết nối nhàn rỗi 5 phút;
- `retryOnConnectionFailure(true)`;
- ping 20 giây;
- connect timeout 20 giây;
- read timeout 45 giây;
- write timeout 20 giây.

Mục tiêu là tái sử dụng DNS/TLS/socket giữa manifest và segment, thử lại pooled connection hỏng trước khi phá toàn bộ Player. Ping không được xem là bằng chứng server HLS luôn khỏe.

### Theo dõi sức khỏe đa tín hiệu

Watchdog chạy mỗi 2 giây, kết hợp:

- trạng thái BUFFERING/READY/ENDED và `isLoading()`;
- `bufferedPosition - currentPosition`;
- tiến triển byte/bandwidth;
- `DecoderCounters.renderedOutputBufferCount`;
- thời điểm frame cuối;
- `playWhenReady`, playback suppression và lifecycle;
- thời gian phát ổn định liên tục;
- current live offset.

Quy tắc:

- Mạng hoặc buffer còn tiến triển: cho phép chờ tới 35 giây.
- Socket/dữ liệu không tiến triển: phục hồi sau khoảng 18 giây.
- READY nhưng decoder không xuất frame trong 12 giây: phục hồi decoder/Player.
- Không coi pause của người dùng, app vào nền hoặc playback suppression là lỗi luồng.

### Phục hồi phân cấp

1. Cấp đầu giữ Player, timeline và phiên; chỉ `prepare/play` lại.
2. Chỉ seek live edge nếu luồng ENDED hoặc live offset vượt 45 giây.
3. Nếu tiếp tục lỗi, giải phóng Player bị treo và tạo MediaSource mới qua connection pool dùng chung.
4. Backoff tăng dần, giới hạn 0,4–7 giây; lỗi mạng tạm thời không dừng chờ người dùng bấm Play.
5. Chỉ reset lịch sử lỗi sau 2 phút phát liên tục; một đoạn phát ngắn không xóa lịch sử.

### Chống lặp và cân bằng độ trễ

- Live target offset khoảng 10 giây; tốc độ bám live 0,97–1,03x.
- Không seek live edge trong mọi lần retry.
- Buffer Mobile: min 30 giây, max 120 giây; start 1,2 giây; rebuffer 5 giây; back-buffer 20 giây.
- Các ngưỡng này là điểm khởi đầu đã build/test, không phải hằng số bắt buộc cho TV hoặc mọi bitrate.

### Logo không nhấp nháy

- Không gọi `notifyDataSetChanged()` khi từng logo hoàn tất.
- Một URL chỉ có một tác vụ; chỉ các Holder đang chờ URL đó được cập nhật.
- Không xóa drawable nếu URL không đổi.
- Cache RAM LRU 24 MB và cache ổ đĩa trong cache app.
- Giới hạn ảnh 2 MB; connect/read timeout 5/8 giây.
- Dùng `WeakReference` để không giữ View đã rời màn hình.

## Lịch sử triển khai

- `d48a84cd64d848c5259cc185dbc05f92ef105fb0`: connection pool, watchdog đa tín hiệu, phục hồi phân cấp và cache logo.
- `b768815250fd0d480a2a73f9f28a0961ae0e6e76`: workflow/tên APK 1.10.23.
- Run `34727897253` bị compiler chặn vì Media3 1.11 không có `ExoPlayer.getPlaybackError()`; không có APK bàn giao từ run lỗi.
- `454735411f92a39a855d2f5f4024b42804a150c4`: sửa tương thích API.
- Run bàn giao `34728059143`, job `103645667798`: toàn bộ compile, test, lint, ký, đóng gói, upload và smoke Android 15 SUCCESS.
- Artifact `NM7-IPTV-Mobile-1.10.23-APK`, ID `10309245086`.
- Archive digest: `sha256:d1a714f66863d428b547b1931e7c33cd7e2df20d7fb8501f935b86d52952754d`.
- APK: 7.723.539 byte; SHA-256 `0b3755ce087f3920fadbe7c4489d410e28bce431ab548dca386cc24c3740becb`.

## Checklist áp dụng cho Android TV

1. Xác định lớp sở hữu Player thật của TV: `PlayerActivity`, `TvStreamRecoveryProvider` hoặc lớp tương ứng.
2. Giữ nguyên D-pad, bảng kênh nhanh, foreground/background service và audio focus của TV.
3. Kiểm tra transport TV hiện tại trước khi thêm OkHttp; chỉ tạo một connection pool cấp ứng dụng.
4. Thêm network progress, buffered-ahead, rendered frame và live offset vào watchdog TV.
5. Không phục hồi khi người dùng pause, app vào nền có chủ ý, audio focus bị giữ hoặc playback bị suppression.
6. Không reset lịch sử stall sau vài giây phát; không seek live edge vô điều kiện.
7. Khi chuyển kênh D-pad, không giữ đồng thời hai decoder 4K quá lâu; Player phụ phải có timeout và được giải phóng chắc chắn.
8. Chỉ ẩn spinner khi UI vẫn có trạng thái chữ; không che lỗi vĩnh viễn.
9. Cache logo theo URL và cập nhật đúng row; không notify toàn adapter sau từng ảnh.
10. Build đúng flavor TV, kiểm tra APK không phình bất thường và smoke trên Android TV emulator.
11. Test máy thật: nội địa FHD, nước ngoài FHD, 4K, đổi nhanh 20 kênh, chạy 30–60 phút, tắt/mở mạng và đưa app nền.
12. Nếu còn lỗi, thu log đã khử URL/token gồm error code, HTTP status, buffer duration, live offset, bytes progress và frame count.

## Tiêu chí ghi nhận test tiếp theo

Với mỗi kênh, ghi thời gian xem liên tục, số lần BUFFERING, số lần Player được tạo lại, có phát lặp không, FPS/độ phân giải, thời gian phục hồi, có cần bấm Play không và logo có chớp khi cuộn lại không.

Chỉ đánh dấu “đã khắc phục” sau test dài hạn trên nguồn thực. Nếu lỗi chỉ xảy ra ở một nguồn, phân tích manifest/segment/HTTP status đã khử bí mật trước khi tiếp tục tăng buffer hoặc retry toàn cục.
