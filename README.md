# BezelPop 2

iPhone-style bezel bulge on Android while a hardware button is held.
Accessibility service + non-touchable overlay; settings UI in Jetpack Compose (Material 3, dynamic colour).

## Build (GitHub Actions)
Push to `main` (or run "Build APK" manually), download the `BezelPop-apks` artifact, install `app-release.apk`.

## Notes
- Four profiles: Portrait 0°, Landscape 90°, Portrait 180°, Landscape 270°. Only the profile matching the current rotation can be edited.
- Original button action is never blocked.
- Power key is usually not delivered to accessibility services; see fallbacks on the Power card.
