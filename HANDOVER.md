# HANDOVER — NM7 IPTV Mobile EPG / Catch-up — 2026-10-28

## Branch and release safety

- Repository: `cnktvpc/NM7-IPTV-Mobile`.
- Test branch: `feature/epg-catchup-superok-20261028`.
- Baseline: accepted NM7 IPTV Mobile 1.10.145 / versionCode 162.
- Candidate: **1.10.146 / versionCode 163**.
- Stable was not modified. PR #1 is a draft; do not merge until CI and real-device checks pass.

## User feedback / discoverability fix — implemented and CI verified

- User reported the new EPG feature was not visible. The previous candidate hid the entry behind channel long-press and the player header's transient controls; this was not discoverable enough.
- Added a visible `EPG` button directly to every channel row, wired to the same channel schedule/catch-up flow. The existing long-press menu and in-player `Lịch` entry remain. This was built and packaged successfully in both push and PR workflows.

## Implemented

- `EpgProgram.java`: programme interval model with past/current/future classification.
- `EpgScheduleStore.java`: XMLTV parser, day filtering and channel ID plus `display-name` alias matching.
- `CatchupUrlBuilder.java`: reads Catch-up metadata from the original EXTINF; builds full URL/default or append-query URLs with supported start/end/duration and UTC/local placeholders; checks archive age without mutating the live source.
- `activity_player.xml` / `PlayerActivity.java`: persistent translucent lower EPG panel, day navigation, horizontal programme cards, live progress indicator, separate wall-clock state and selected replay state. Replay starts in the existing player and the panel remains open. When replay is selected from the channel action-menu schedule, the player receives the original live source and selected programme range, opens directly in DVR mode with the selected programme marked, and can return to live.
- `MainActivity.java`: channel action menu and original live URL/name pass-through for replay launched from the channel list.
- `CatchupUrlBuilderTest.java`: metadata parsing, default URL, append mode, archive expiry and future-programme rejection.
- `.github/workflows/android-mobile-final.yml`: feature branch push trigger, candidate version check (1.10.146 / 163), Catch-up tests before assemble, and APK export naming.
- README's candidate summary updated; historical release notes remain.

## Latest build — visible EPG fix verified

- Source commit: `b264cdd62437c360a50f6488430d0e6f3fccdb71`.
- Push build [#40642855178](https://github.com/cnktvpc/NM7-IPTV-Mobile/actions/runs/40642855178): **SUCCESS**.
- PR validation [#40642861504](https://github.com/cnktvpc/NM7-IPTV-Mobile/actions/runs/40642861504): **SUCCESS**.
- Unit tests, Mobile APK assembly, artifact upload, and temporary CI-branch export all passed.
- Artifact: `NM7-IPTV-Mobile-FINAL`, ID `12796551400`, expires 2026-11-27.
- Delivered APK: `NM7-IPTV-Mobile-1.10.146-EPG-Catchup-visible-EPG.apk` (91,253,360 bytes).
- APK SHA-256: `0e53cf109a50ba6de5fe1e56f1512aef603537f09c02e78b40161b5d9d8a0897`.
- Artifact ZIP SHA-256: `1d1106636ea1808ad75901bb123474ec7994096ff98731e400e759ae5bc72dc7`.
- Verified locally: checksum matches CI `SHA256SUMS.txt`; APK and containing artifact ZIP pass integrity checks; DEX includes the visible EPG callback and schedule/catch-up implementation.
- Stable remains unchanged; PR #1 remains draft. **Build success does not prove provider-specific catch-up playback or device-level focus/rendering.** Keep these as test gates.

## Superseded build

- Earlier build at commit `04291b79d0f480b06dd574367f4c785ad7c41590`, run #40639183001, was superseded because the EPG entry was not sufficiently visible. Do not use it to test the row-level EPG button.

## Remaining release gates

1. Test remote/touch focus, date navigation, live/past/future rows, archive playback, replay -> live restoration, channel switching and rotation on an Android device.
2. Test one authorized channel that has real Catch-up support and another that does not; confirm the provider URL template and expiry window.
3. Keep PR #1 in draft and stable untouched until the above checks pass.
