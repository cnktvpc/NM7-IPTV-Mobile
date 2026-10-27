# NM7 IPTV for iPhone and iPad

Native SwiftUI/iOS port of the NM7 IPTV Android project.

## Current iOS MVP

- iPhone + iPad universal target (`TARGETED_DEVICE_FAMILY=1,2`).
- Multiple M3U playlist URLs, add/edit/delete/select.
- Same trusted default playlist URL as Android on a clean install.
- M3U parsing for `#EXTINF`, groups, logos, tvg-id, relative URLs, `#EXTVLCOPT` HTTP headers and pipe URL headers.
- Search by channel/group/URL.
- All / Favorites / Recent filters.
- Current-playing channel highlight in the channel list.
- Native AVPlayer inline player and fullscreen player.
- Tap video to show/hide playback controls.
- Swipe vertically on left 30% of the video to change screen brightness.
- Swipe vertically on right 30% of the video to change system media volume.
- Background audio mode enabled.
- Dark NM7 IPTV interface, adaptive to iPhone and iPad.

## Playback compatibility

iOS AVPlayer is used deliberately for the first version because it integrates best with iPhone/iPad playback and background audio. HLS/M3U8 and normal MP4/HTTP media are the primary supported stream types.

Android-only DRM metadata is not silently reused on iOS. Widevine, ClearKey and PlayReady channels require an iOS-compatible DRM path (normally FairPlay or a licensed cross-platform DRM player SDK). DASH/MPD also needs an additional iOS playback engine if it is not packaged as an Apple-compatible HLS stream.

## Build

The project is generated with XcodeGen:

```bash
cd ios
xcodegen generate
open NM7IPTV.xcodeproj
```

GitHub Actions workflow `.github/workflows/ios.yml` builds the app against the iOS Simulator with code signing disabled. A real-device IPA/TestFlight build needs an Apple signing team/certificate/provisioning profile.
