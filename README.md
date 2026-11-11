# BezelPop

BezelPop draws a configurable hardware-button bulge along the edge of an Android display. It uses an AccessibilityService and a non-touchable overlay; the original volume-button action is not blocked.

## Current app design

- Jetpack Compose + Material 3 Expressive-style surfaces and controls.
- Android 12+ wallpaper-derived dynamic colours in light and dark mode.
- Vivid title gradient sampled from the system primary, tertiary and secondary accents, plus a low-contrast dotted background.
- Legacy devices fall back to Material light/dark schemes.
- The overlay fill can be set to the current system accent from the Shape section.

## Build APKs with GitHub Actions

1. Push the contents of this folder to the `main` or `master` branch of your repository.
2. Open **Actions** and run **Build APK** (or let a push start it).
3. Download the `BezelPop-apks` artifact. It contains debug and release APKs; the release APK is signed with the debug key for personal installation.

## First run

1. On Android 13+, try enabling the accessibility service once, then open App info → ⋮ → Allow restricted settings if Android blocks it.
2. Enable BezelPop under Accessibility → Installed apps.
3. Set BezelPop battery use to Unrestricted. Samsung devices may also require removing it from Sleeping apps.

The in-app setup guide can be reopened at any time.

## Notes

- Profiles: Portrait, Landscape 1 (rotation 90), Landscape 2 (rotation 270). Upside-down portrait uses Portrait.
- Power-key delivery is restricted by Android and may not be available to accessibility services; power-key fallback modes remain in settings.
- A short hardware-button tap now completes the expansion, holds at full size for the configured minimum hold duration, and only then contracts.
