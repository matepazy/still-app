<p align="center">
  <img src="branding/still_lockup.svg" width="320" alt="Still">
</p>

# Still

Still is a privacy-first Android screen-time app focused on understanding usage patterns without judgment. It reconstructs local usage events into phone sessions, app activity, check-ins, breaks, and same-time-of-day comparisons.

> Screen time without the guilt trip.

## Screenshots

![Still interface gallery](docs/screenshots/implementation-grid.png)

The gallery shows onboarding, permission guidance, Today, Timeline, Apps, app detail, Settings, light theme, and the no-access state. These captures use preview-only fixture data rendered by the production Compose screens; fake analytics are not included in runtime builds.

## Features

- **Today** — current screen time, a same-time seven-day comparison, check-ins, quick checks, longest break, and one meaningful app change.
- **Dayline** — a compact timeline that makes fragmented use and longer sessions visually distinct.
- **Timeline** — reverse-chronological phone sessions with app duration, switch sequence, and day selection.
- **Apps** — selectable daily usage, opens, share of screen time, and per-app detail with usage history.
- **Appearance** — system, light, and dark themes, with optional Android 12+ wallpaper colors.
- **Local-only processing** — no account, network permission, telemetry, analytics SDK, or cloud service.

## Architecture

Still uses Kotlin, Jetpack Compose, Material 3, Navigation Compose, Coroutines/Flow, and DataStore Preferences. Platform event collection lives in `data/usage`, pure calculation code in `domain/analytics`, immutable models in `domain/model`, and screen-focused composables under `ui`.

`UsageStatsDataSource` converts Android `UsageEvents` into a small internal event model. Pure Kotlin analyzers reconstruct foreground intervals, group real phone-use sessions, distinguish wakeups from unlocks, aggregate app use, build the Dayline, and calculate same-time baselines. ViewModels perform queries outside composition and expose stable UI state.

## Usage access

Android exposes usage statistics through `UsageStatsManager`. Still declares `android.permission.PACKAGE_USAGE_STATS`, but this is a special access—not a runtime permission dialog. Onboarding opens the system Usage Access screen and checks the actual AppOps state whenever Still resumes. Opening Settings alone never counts as permission approval.

## Privacy model

Still reads Android's local usage-event history and processes it on the device. The app does not declare the Internet permission and contains no telemetry, advertisements, account system, or remote SDK. Appearance and reference-target preferences are stored locally with DataStore.

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

Unit tests cover ordinary resume/pause pairs, app switching, duplicate resumes, missing pauses, screen-off boundaries, quick checks, longer sessions, unlock debouncing, midnight boundaries, and same-time baseline calculations.

## Known Android and OEM limitations

Usage-event histories are provided by Android and can be incomplete on some devices. OEM power-management policies may delay events, launcher/system packages vary, and keyguard events are not equally reliable across every device. Still treats malformed sequences defensively, closes open foreground intervals at screen-off or query boundaries, filters only a small set of obvious system noise, and reports unavailable data instead of inventing statistics. App labels or icons can also be unavailable; in that case Still falls back to the package name and a text mark.
