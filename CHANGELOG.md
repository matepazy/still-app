# Changelog

## 1.2.0

### New features

- Added a private, on-device usage history archive backed by SQLite.
- Added compressed storage for detailed usage events, keeping recent session-level history available over time.
- Added recovery of older daily app totals from Android's longer-retained daily usage statistics when detailed events are no longer available.
- Added automatic background history synchronization every 12 hours, including persistence across device restarts.
- Added a Privacy & data area with a transparent stored-data explanation and a way to disable and delete saved usage history.
- Added a calendar date picker for navigating across available history, with locale-aware months, weekdays, and enabled dates.
- Added persistent update reminders so postponed releases can reappear after the reminder period.
- Added expanded widget customization: corner radius including pill shape, background opacity, system-aware colors, reset-to-defaults, and scheduled widget refreshes.
- Added widget configuration handling for opening and editing an individual widget from the launcher.
- Added archive round-trip and usage-pattern test coverage.

### Tweaks

- Updated Today, Timeline, Apps, and app-detail data loading to use the local archive while continuing to read current usage from Android.
- Clearly label archived days with aggregate data as **Daily total only** instead of implying that session details or app-open counts are available.
- Made widget previews reflect the selected radius, opacity, appearance, and system palette more accurately.
- Refined widget refresh behavior after wallpaper/configuration changes and when widget instances are added or removed.
- Updated the privacy and architecture documentation to explain local retention, compression, backup exclusion, and the history controls.

### Fixes

- Removed the duplicate progress indicator shown while downloading an app update; the download sheet now uses one progress bar and the percentage label.
