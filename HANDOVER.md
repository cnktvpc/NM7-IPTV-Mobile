# HANDOVER — NM7 IPTV Mobile EPG / Catch-up candidate

## Current task

Implement the Super OK-style EPG programme schedule and replay workflow for NM7 IPTV Mobile.

- Branch: `feature/epg-catchup-superok-20261028`
- Baseline: `main`, whose README records the accepted stable 1.10.145 / versionCode 162 baseline.
- Candidate version: **1.10.146 / versionCode 163**.
- Stable branch has not been modified.
- Do not merge until CI and device tests pass.

## Changes currently committed

- `EpgProgram.java`: programme model with start/end epoch milliseconds and past/current/future checks.
- `EpgScheduleStore.java`: parses full XMLTV programme intervals and supports per-day schedules.
- `CatchupUrlBuilder.java`: reads Catch-up attributes from `Channel.originalExtInf()`; supports `default` full URL and `append` query modes, start/end/duration and common UTC/local placeholders, and `catchup-days` expiry checks.
- `MainActivity.java`: channel action menu now offers **Lịch phát sóng / Xem lại**. It downloads/parses the XMLTV schedule off the UI thread, lists programmes for a day, marks current/future/replayable past entries, navigates previous/next day and starts an archive URL via the existing player when the channel metadata supports replay.
- `CatchupUrlBuilderTest.java`: tests metadata parsing, URL building, append mode, expired programmes and future-programme rejection.
- `app/build.gradle.kts`: version bumped to 1.10.146 / 163.
- README updated with implementation status and validation gates.

## Important technical notes

- Channel's original live URL is not mutated. Replay uses a temporary Channel object and the existing PlayerActivity.
- Current EPG display in the existing channel list is a separate snapshot from the new full programme schedule.
- Catch-up is provider-dependent. A working XMLTV feed does not imply a channel has an archive.
- The first UI entry point is the channel action menu. It does **not yet reproduce** Super OK's exact in-player lower overlay, DVR badge and persistent EPG panel while replaying. This is a known follow-up, not something to claim finished.
- Verify `CatchupUrlBuilder` placeholder syntax against real M3U EXTINF examples; do not leak signed URLs or tokens into logs.

## Next steps

1. Inspect CI triggered by this branch. Fix all Java compile, lint, unit-test and generated-source failures.
2. Test the URL builder against real Catch-up metadata from a source the user is authorized to access.
3. Validate XMLTV matching and timezone handling.
4. Implement the in-player EPG overlay and explicit LIVE/DVR state while preserving the current player session.
5. Test replay -> back to live, channel switch, player rotation/recreation, YouTube MiniPlayer and Live feed regression.
6. Update this file and README with actual workflow run, artifact, checksums and known failures. Never mark runtime PASS without device confirmation.
