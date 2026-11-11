# BezelPop

Draws an iPhone-style bezel bulge on Android while a hardware button is held.
Accessibility service + overlay, no dependencies, idle cost ~zero.

## Build (GitHub)
1. Create a repo, push this folder to `main`.
2. Actions tab > "Build APK" runs automatically (or Run workflow).
3. Download the `BezelPop-apks` artifact, install `app-release.apk` (debug-signed, fine for personal use).

## First run
Settings > Apps > BezelPop > ⋮ > Allow restricted settings (Android 13+),
then Accessibility > BezelPop > On, then Battery > Unrestricted.
The in-app checklist walks through it and can be skipped / reopened.

## Notes
- Profiles: Portrait, Landscape 1 (rotation 90), Landscape 2 (rotation 270). Upside-down portrait uses Portrait.
- The original button action is never blocked (volume still changes).
- Power key is usually not delivered to accessibility services; see fallbacks in the Power card.
