<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="branding/still_wordmark_inverse.svg">
    <source media="(prefers-color-scheme: light)" srcset="branding/still_wordmark.svg">
    <img src="branding/still_wordmark.svg" width="220" alt="Still">
  </picture>
</p>

# Still

Still is a privacy-first Android screen-time app focused on understanding usage patterns without judgment. It reconstructs local usage events into phone sessions, app activity, check-ins, breaks, and same-time-of-day comparisons.

> Screen time without the guilt trip.

## Screenshots

<p align="center">
  <img src="docs/screenshots/emulator-today.png" width="23%" alt="Still Today screen showing screen time and the Dayline">
  <img src="docs/screenshots/emulator-timeline.png" width="23%" alt="Still Timeline screen showing reconstructed phone sessions">
  <img src="docs/screenshots/emulator-apps.png" width="23%" alt="Still Apps screen showing daily app usage">
  <img src="docs/screenshots/emulator-app-detail.png" width="23%" alt="Still app detail screen showing usage history and sessions">
</p>

Captured from the connected Android emulator running Still 0.9.0 with Usage Access enabled. The values shown come from that emulator's local usage history.

## Features

- **Today** — current screen time, a same-time seven-day comparison, check-ins, quick checks, longest break, and one meaningful app change.
- **Dayline** — a compact timeline that makes fragmented use and longer sessions visually distinct.
- **Timeline** — reverse-chronological phone sessions with app duration, switch sequence, and day selection.
- **Apps** — selectable daily usage, opens, share of screen time, and per-app detail with usage history.
- **Appearance** — system, light, and dark themes, with optional Android 12+ wallpaper colors.
- **Local usage archive** — imports the oldest daily usage Android still retains, preserves detailed recent events in compressed form, and keeps the database on-device for long-term statistics.

## Architecture

Still uses Kotlin, Jetpack Compose, Material 3, Navigation Compose, Coroutines/Flow, and DataStore Preferences. Platform event collection lives in `data/usage`, pure calculation code in `domain/analytics`, immutable models in `domain/model`, and screen-focused composables under `ui`.

`UsageStatsDataSource` converts Android `UsageEvents` into a small internal event model. `LocalUsageArchive` stores normalized daily/app totals in SQLite and gzip-compresses the event streams that Android still exposes. A native periodic job refreshes the archive twice daily. Pure Kotlin analyzers reconstruct foreground intervals, group real phone-use sessions, distinguish wakeups from unlocks, aggregate app use, build the Dayline, and calculate same-time baselines. ViewModels perform queries outside composition and expose stable UI state.

## Usage access

Android exposes usage statistics through `UsageStatsManager`. Still declares `android.permission.PACKAGE_USAGE_STATS`, but this is a special access—not a runtime permission dialog. Onboarding opens the system Usage Access screen and checks the actual AppOps state whenever Still resumes. Opening Settings alone never counts as permission approval.

## Privacy model

Still reads Android's local usage history and processes and archives it only on the device. The usage database is excluded from Android cloud backup and device transfer. Still contains no telemetry, advertisements, account system, or remote analytics SDK; its Internet permission is used only by the optional, consent-gated release updater. Appearance and reference-target preferences are stored locally with DataStore.

## Build

Requirements:

- Android Studio with Android SDK 37 installed
- JDK 17

From the repository root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Open the project in Android Studio, let Gradle sync, then run the `app` configuration on an API 28+ device or emulator. Grant Usage Access from Still's onboarding screen.

## Tests

Unit tests cover ordinary resume/pause pairs, app switching, duplicate resumes, missing pauses, screen-off boundaries, quick checks, longer sessions, unlock debouncing, midnight boundaries, same-time baseline calculations, and compressed event-archive round trips.

## Known Android and OEM limitations

Usage-event histories are provided by Android and can be incomplete on some devices. OEM power-management policies may delay events, launcher/system packages vary, and keyguard events are not equally reliable across every device. Still treats malformed sequences defensively, closes open foreground intervals at screen-off or query boundaries, filters only a small set of obvious system noise, and reports unavailable data instead of inventing statistics. App labels or icons can also be unavailable; in that case Still falls back to the package name and a text mark.
