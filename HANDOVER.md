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
- `activity_player.xml` / `PlayerActivity.java`: persistent translucent lower EPG panel, day navigation, horizontal programme cards, live progress indicator, separate wall-clock state and selected replay state. Replay starts in the existing player and the panel remains open. When replay is selected from the channel action-menu schedule, the player receives the original live source and selected programme range, opens directly in DVR mode, and can return to live.
- `MainActivity.java`: channel action menu and original live URL/name pass-through for replay launched from the channel list.
- `CatchupUrlBuilderTest.java`: metadata parsing, default URL, append mode, archive expiry and future-programme rejection.
- `.github/workflows/android-mobile-final.yml`: feature branch push trigger, candidate version check (1.10.146 / 163), Catch-up tests before assemble, and APK export naming.
- README's candidate summary updated; historical release notes remain.

## Build status — incomplete

- Latest observed prior Actions run: [40636615316](https://github.com/cnktvpc/NM7-IPTV-Mobile/actions/runs/40636615316), conclusion `failure`. The API returned zero job records, so there is no step log from that run to inspect.
- **No successful APK artifact or SHA-256 has been verified yet.** Do not state that this candidate built successfully.
- Catch-up is source-dependent; working EPG does not guarantee archive support.

## Release gates

1. Inspect the next workflow run and resolve the no-job/failure issue; when jobs are exposed, fix compile/test/build errors from their logs.
2. Pass `:app:testMobileDebugUnitTest` including `CatchupUrlBuilderTest`, then `:app:assembleMobileDebug`.
3. Verify the artifact and APK checksum from the same successful run.
4. Test remote/touch focus, dates, live/past/future state, archive playback, replay -> live restoration, channel switching and rotation.
5. Test with an authorized channel that has real Catch-up support and another that does not.
6. Add the verified run link, artifact ID, APK filename and SHA-256 here only after they exist. Keep stable untouched until all gates pass.
