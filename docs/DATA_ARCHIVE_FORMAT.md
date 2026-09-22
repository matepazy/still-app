# Rebuilding Still's local usage archive

Still stores a compact, versioned representation of Android usage history in
`usage_history.db`. The archive is private to the app, excluded from Android
cloud backup and device transfer, and contains enough information for the app
to rebuild every visible usage statistic. It is not intended to reproduce the
original Android `UsageEvents` byte for byte.

## Compact schema (database version 5, archive format 4)

### `days`

One row per local calendar day:

- `day`: `LocalDate.toEpochDay()`.
- `range_end_ms`: end of the captured range, rounded down to one second.
- `detailed`: whether a rebuildable event stream is available.
- `events`: the versioned compact event stream, or `NULL` for aggregate-only
  days supplied by Android.

### `apps`

A device-local dictionary. Each package name and its latest label are stored
once and assigned a numeric ID.

### `app_days`

The per-day app fallback used for history and for days where Android no longer
provides events. It stores the app ID, foreground duration in whole seconds,
and an optional open count. Package names and labels are not repeated here.

### `metadata` and `archive_control`

`metadata` contains synchronization cursors. `archive_control` records the
active format, migration-notice state, and the pre-migration size measurement.
Neither table contains usage records.

## Event stream version 2

`UsageEventCodec` performs these steps before writing a detailed day:

1. Sort events by timestamp.
2. Round timestamps down to one-second precision.
3. Remove exact duplicates, redundant resumes of the already-active app, and
   pauses for an app that is not active.
4. Build a per-day package dictionary.
5. Write package counts, string lengths, event counts, timestamp deltas, and
   package indexes as unsigned variable-length integers.
6. Gzip the compact binary stream.

The decoder continues to accept version-1 streams, including those restored
from a pre-migration backup.

## Rebuilding a `DailyUsage`

For a detailed day:

1. Decode the previous day's stream and the requested day's stream. The
   previous day supplies state when an app remains active across midnight.
2. Pass both streams to `ForegroundIntervalReconstructor`, bounded by the
   requested start and end instants.
3. Remove launcher and Android system packages with `SystemPackageFilter`.
4. Rebuild per-app duration and open counts with `AppUsageAggregator`.
5. Rebuild sessions and app-switch sequences with `SessionAnalyzer`.
6. Derive unlocks, wakeups, longest break, the Dayline, quick checks, hourly
   activity, first/last use, and frequent app switches with the existing pure
   analyzers.

For an aggregate-only day, join `app_days` to `apps`, convert stored seconds to
`Duration`, and sum them for the daily total. Session-level details remain
unavailable, matching the behavior of the legacy archive.

All visible values rebuilt from detailed data may differ from the legacy copy
by less than one second because sub-second timestamps are deliberately not
retained.

## Migration and rollback

When a version-3 archive is first opened:

1. Still writes an exact logical snapshot to
   `noBackupFilesDir/usage_history_v3_backup.gz`.
2. It validates that the compressed snapshot can be read completely.
3. It converts event blobs to codec version 2, creates the shared app
   dictionary, converts durations to seconds, and removes redundant derived
   tables and columns inside the SQLite upgrade transaction.
4. After the transaction, it runs `VACUUM` once and measures the compact
   database and safety-backup sizes for the user-facing migration notice.

If a legacy event blob cannot be decoded, that day falls back to its preserved
daily app totals while the exact blob remains in the safety backup. If a schema
or backup step fails, SQLite rolls back the upgrade transaction and the legacy
database remains active. The temporary backup is overwritten on the next
attempt.

Settings **Data → Restore backup** performs the reverse operation in a single
transaction: compact tables are replaced with the legacy schema and the exact
logical snapshot is imported. History recorded after the snapshot is copied
into the restored legacy schema, so rollback does not discard newer days. If
import fails, the compact archive is left unchanged. Restored event blobs
remain readable because the decoder supports both codec versions. Turning off
**Save usage history** deletes both active history and the safety backup.

After verifying the compact archive, the user can permanently remove only the
safety copy from **Data → Data stored on this device → Delete backup**. This
does not change the active compact archive or any rebuilt statistics, but it
removes the option to restore the pre-migration format.
