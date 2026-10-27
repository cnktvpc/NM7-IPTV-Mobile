# NM7 IPTV Mobile — SmartTube integration

The YouTube tab in NM7 IPTV Mobile is intended to host SmartTube Droid in the same APK/process.

## Source pinned

- Repository: `https://github.com/systematiq-one/SmartTube-droid.git`
- Commit: `8eb1e0dd47bf8d0ba3767c66309ac28293debd33`
- License: MIT

## Integration shape

NM7 keeps its existing IPTV home screen as the `IPTV` tab. The `YouTube` tab hosts the SmartTube Droid phone UI and shared engine. SmartTube Droid's own TV app is not used for the Mobile flavor.

The third-party source is tracked as a git submodule so future SmartTube service updates can be pinned and reviewed explicitly.
