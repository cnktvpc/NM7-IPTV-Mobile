# Nm7 IPTV cho Samsung Tizen TV

Ứng dụng Web App dành cho TV Samsung đời 2015–2020 (Tizen 2.3–5.5), sử dụng Samsung AVPlay.

## Kiểm thử mã

```sh
cd tizen
npm test
```

## Ký và cài lên TV

1. Cài Tizen Studio và Samsung TV Extension.
2. Nếu dùng trực tiếp mã nguồn trong repo, chép icon Android vào `tizen/icon.png`; artifact CI đã chứa sẵn icon.
3. Bật Developer Mode trên TV, nhập IP máy tính và khởi động lại TV.
4. Kết nối TV bằng Device Manager.
5. Tạo Samsung Certificate Profile có TV Distributor Certificate.
6. Trong thư mục `tizen`, chạy:

```sh
tizen build-web
tizen package -t wgt -s TEN_CHUNG_CHI -- .buildResult
tizen install -n Nm7IPTV.wgt -t TEN_THIET_BI
```

Gói cài cho một TV thật phải được ký bằng chứng chỉ có DUID của TV đó. Không đưa file chứng chỉ hoặc mật khẩu vào GitHub.
