# HANDOVER — NM7 IPTV Mobile EPG / Catch-up — 2026-10-28

## Branch and release safety

- Repository: `cnktvpc/NM7-IPTV-Mobile`.
- Test branch: `feature/epg-catchup-superok-20261028`.
- Baseline: accepted NM7 IPTV Mobile 1.10.145 / versionCode 162.
- Candidate: **1.10.146 / versionCode 163**.
- Stable was not modified. PR #1 is a draft; do not merge until CI and real-device checks pass.

## Implemented

- `EpgProgram.java`: programme interval model with past/current/future classification.
- `EpgScheduleStore.java`: XMLTV parser, day filtering and channel ID plus `display-name` alias matching.
- `CatchupUrlBuilder.java`: reads Catch-up metadata from the original EXTINF; builds full URL/default or append-query URLs with supported start/end/duration and UTC/local placeholders; checks archive age without mutating the live source.
- `activity_player.xml` / `PlayerActivity.java`: persistent translucent lower EPG panel, day navigation, horizontal programme cards, live progress indicator, separate wall-clock state and selected replay state. Replay starts in the existing player and the panel remains open. When replay is selected from the channel action-menu schedule, the player receives the original live source and selected programme range, opens directly in DVR mode with the selected programme marked, and can return to live.
- `MainActivity.java`: channel action menu and original live URL/name pass-through for replay launched from the channel list.
- `CatchupUrlBuilderTest.java`: metadata parsing, default URL, append mode, archive expiry and future-programme rejection.
- `.github/workflows/android-mobile-final.yml`: feature branch push trigger, candidate version check (1.10.146 / 163), Catch-up tests before assemble, and APK export naming.
- README's candidate summary updated; historical release notes remain.

## Build status — CI verified

- Push build: [Actions run #40639183001](https://github.com/cnktvpc/NM7-IPTV-Mobile/actions/runs/40639183001), commit `04291b79d0f480b06dd574367f4c785ad7c41590`, **success**.
- Pull-request validation: [Actions run #40639187227](https://github.com/cnktvpc/NM7-IPTV-Mobile/actions/runs/40639187227), same commit, **success**.
- Passed: EPG Catch-up unit tests, `:app:assembleMobileDebug`, artifact upload and export to the temporary CI branch.
- Artifact `NM7-IPTV-Mobile-FINAL`, ID `12794439441`. APK SHA-256: `86b09b5b9b6658bd61f682081bcc8f5fb52a5cdcaea5fca9cfd89e6296e69d85`.
- **Build/packaging is verified; real-device playback and provider-specific Catch-up remain unverified.** Do not merge to stable until user/device testing passes.

## Remaining release gates

1. Test remote/touch focus, date navigation, live/past/future rows, archive playback, replay -> live restoration, channel switching and rotation on an Android device.
2. Test one authorized channel that has real Catch-up support and another that does not; confirm the provider URL template and expiry window.
3. Keep PR #1 in draft and stable untouched until the above checks pass.
