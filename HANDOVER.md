# HANDOVER — NM7 IPTV Mobile — 1.10.113 — 2026-10-08

## Current
- Stable: **1.10.112 / 128** on `stable/mobile-1.10.112`.
- Baseline commit: `6aea2d995280017c1e7c00310bcca0c9937b50bb`.
- Candidate: **1.10.113 / 129**.
- Candidate branch: `work/mobile-app-settings-bottom-v113`.
- HEAD: `1a5ee858ee0bd9a4a44707f9c0f9b3fa81e5a764`.
- Runtime/device test: **NOT YET CONFIRMED**.

## Changes
### IPTV
- Removed visible row: **Tất cả | Yêu thích | Tải lại | Gần đây**.
- `Tải lại` moved beside `Bỏ lọc`.
- `Tùy chọn IPTV` keeps `Tất cả kênh`, `Yêu thích`, `Gần đây`.
- Hidden section button anchors remain for compatibility with existing MainActivity logic.

### App settings / bottom nav
- Bottom bar: **YouTube | IPTV | Tùy chọn ứng dụng**.
- App settings reuse `MainActivity.showSettings()`; no duplicate settings implementation.

### YouTube header
- Generate-time source: `scripts/mobile-ui/res/layout/browse_activity.xml`.
- Light header with NM7 + search + microphone + account.
- IDs: `nm7_search`, `nm7_voice`, `nm7_account`.

## Versioning
- 1.10.113 / 129 already built.
- Next new build must not reuse 129; use **1.10.114 / 130** or higher.

## CI
- Workflow: `.github/workflows/nm7-mobile-113-ui-refinement.yml`.
- Run: `37655352745` SUCCESS.
- Artifact: `11498119053`.
- APK SHA-256: `72537004c936a9a358712b28261c9709153252561b57b7952e5fe3131f42019b`.
- Artifact digest: `885fccf5dd8e3004ec6962d7d2c2416bebe354452b3c7c3621f1e470e699c75c6`.
- [Actions](https://github.com/phuongnm7/iptv-player-android/actions/runs/37655352745)
- [Artifact](https://github.com/phuongnm7/iptv-player-android/actions/runs/37655352745/artifacts/11498119053)

## Build chain
1. Checkout candidate branch.
2. Clone pinned SmartTube `4825d6aa8b6f1d3181927f9e96c7d89cab13d510`.
3. Apply SmartTube compatibility patches.
4. Run `scripts/patch-mobile-v37.py` and `scripts/patch-mobile-v106.py`.
5. Verify version 1.10.113 / 129.
6. Run lifecycle guards and parser test.
7. Run `:app:assembleMobileDebug`.
8. Upload artifact.

## Important files
- `app/src/main/java/vn/phuong/iptvplayer/MainActivity.java`
- `app/src/main/java/vn/phuong/iptvplayer/MobileIptvUi.java`
- `app/src/main/java/vn/phuong/iptvplayer/HomeTabBar.java`
- `app/src/main/res/layout/activity_main.xml`
- `app/build.gradle.kts`
- `scripts/mobile-ui/res/layout/browse_activity.xml`
- `scripts/patch-mobile-ui.py`

## Do not change casually
- Do not alter `stable/mobile-1.10.112` before 1.10.113 runtime acceptance.
- Do not use `main` as implicit baseline.
- Do not remove section anchors before refactoring MainActivity.
- Do not edit only generated `third_party/SmartTube-droid`; CI regenerates it.
- Do not treat CI PASS as runtime PASS.

## Device acceptance checklist
- IPTV old four-button row absent.
- Bỏ lọc + Tải lại aligned.
- Tùy chọn IPTV switches All/Favorites/Recent.
- Bottom bar settings works.
- Full application settings dialog works.
- YouTube header visual and interactions work.
- YouTube avatar/status bar/spinner/live chat/background/mini/back remain OK.

## Next developer
Start from `work/mobile-app-settings-bottom-v113` @ `1a5ee858ee0bd9a4a44707f9c0f9b3fa81e5a764`. Read `README.md` → `PROGRESS.md` → `HANDOVER.md` before making changes.
