package app.still.data.usage

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import app.still.domain.model.AppInfo
import app.still.domain.model.AppUsage
import java.io.File
import java.time.Duration
import java.time.LocalDate

/** Dependency-free device checks, isolated from the installed app's real archive. */
class DataExportInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val root = File(targetContext.cacheDir, "export-test-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(root, name)
            override fun getNoBackupFilesDir(): File = root
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?): android.database.sqlite.SQLiteDatabase =
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, errorHandler: android.database.DatabaseErrorHandler?): android.database.sqlite.SQLiteDatabase =
                android.database.sqlite.SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory,
                    android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY, errorHandler)
        }
        try {
            LocalUsageArchive(isolated).use { archive ->
                archive.writableDatabase.enableWriteAheadLogging()
                val date = LocalDate.of(2026, 9, 1)
                archive.saveAggregate(date, 123_000, listOf(AppUsage(AppInfo("example.app", "Example"), Duration.ofSeconds(120), 4)))
                archive.putMetadata("test", "original")
                createSafetyBackup(File(root, "usage_history_v3_backup.gz"), date)
                val snapshot = File(root, "snapshot")
                archive.exportSnapshot(snapshot)
                val encrypted = File(root, "encrypted")
                snapshot.inputStream().use { input -> encrypted.outputStream().use { output ->
                    EncryptedDataExport.encrypt(input, output, "012345".toCharArray())
                } }
                archive.clearHistory()
                check(archive.storedDataSummary().dayCount == 0L)
                val restored = File(root, "restored")
                encrypted.inputStream().use { input -> restored.outputStream().use { output ->
                    EncryptedDataExport.decrypt(input, output, "012345".toCharArray())
                } }
                archive.importSnapshot(restored)
                check(archive.day(date)?.apps?.single()?.duration == Duration.ofSeconds(120))
                check(archive.day(date)?.apps?.single()?.opens == 0) // Aggregate-only days have no opens.
                check(archive.metadata("test") == "original")
                check(archive.storedDataSummary().dayCount == 1L)
                check(archive.storedDataSummary().backupAvailable)
                val truncated = File(root, "truncated").apply { writeBytes(restored.readBytes().dropLast(4).toByteArray()) }
                check(runCatching { archive.importSnapshot(truncated) }.isFailure)
                check(archive.day(date)?.total == Duration.ofSeconds(120))
                check(archive.metadata("test") == "original")
                val trailing = File(root, "trailing").apply { writeBytes(restored.readBytes() + byteArrayOf(1)) }
                check(runCatching { archive.importSnapshot(trailing) }.isFailure)
                check(archive.storedDataSummary().dayCount == 1L)
                archive.restoreLegacyBackup()
                check(archive.storedDataSummary().storageFormat == ArchiveStorageFormat.Legacy)
                check(archive.day(date)?.total == Duration.ofSeconds(120))
                val legacySnapshot = File(root, "legacy-snapshot")
                archive.exportSnapshot(legacySnapshot)
                archive.upgradeLegacyArchive()
                archive.importSnapshot(legacySnapshot)
                check(archive.storedDataSummary().storageFormat == ArchiveStorageFormat.Legacy)
                check(archive.day(date)?.total == Duration.ofSeconds(120))
                verifyBatchedArchiveReads(archive)
                archive.upgradeLegacyArchive()
                verifyBatchedArchiveReads(archive)
                verifyCurrentUsageAndHistoryCache(isolated, archive)
                archive.close()
                check(archive.storedDataSummary().dayCount > 0L)
                archive.clearHistory()
                check(archive.storedDataSummary().dayCount == 0L)
                // Import rollback keeps the exact prior archive, including its separate safety backup.
                val importedDate = date.plusDays(1)
                archive.saveAggregate(importedDate, 456_000, listOf(AppUsage(AppInfo("new.app", "New"), Duration.ofSeconds(45), 0)))
                val incoming = File(root, "incoming")
                archive.exportSnapshot(incoming)
                archive.clearHistory()
                archive.saveAggregate(date, 123_000, listOf(AppUsage(AppInfo("old.app", "Old"), Duration.ofSeconds(99), 0)))
                archive.putMetadata("test", "before-import")
                createSafetyBackup(File(root, "usage_history_v3_backup.gz"), date)
                val beforeImport = System.currentTimeMillis()
                archive.importSnapshot(incoming)
                val until = requireNotNull(archive.storedDataSummary().importRollbackUntilMillis)
                check(until >= beforeImport + Duration.ofDays(7).toMillis())
                check(until <= System.currentTimeMillis() + Duration.ofDays(7).toMillis())
                check(archive.day(date) == null && archive.day(importedDate) != null)
                check(runCatching { archive.importSnapshot(truncated) }.isFailure)
                check(archive.storedDataSummary().importRollbackUntilMillis == until)
                archive.close()
                check(archive.storedDataSummary().importRollbackUntilMillis == until)
                archive.rollbackImport()
                check(archive.day(date)?.total == Duration.ofSeconds(99))
                check(archive.day(importedDate) == null)
                check(archive.metadata("test") == "before-import")
                check(archive.storedDataSummary().backupAvailable)
                check(archive.storedDataSummary().importRollbackUntilMillis == null)
                check(root.listFiles().orEmpty().none { it.name.startsWith("import-rollback-") })
                // A second import replaces the rollback copy with its immediate predecessor.
                archive.importSnapshot(incoming)
                archive.saveAggregate(importedDate, 456_000, listOf(AppUsage(AppInfo("new.app", "New"), Duration.ofSeconds(77), 0)))
                archive.importSnapshot(incoming)
                archive.rollbackImport()
                check(archive.day(importedDate)?.total == Duration.ofSeconds(77))
                archive.importSnapshot(incoming)
                val expiry = requireNotNull(archive.storedDataSummary().importRollbackUntilMillis)
                check(archive.importRollbackUntilMillis(expiry - 1) == expiry)
                check(archive.importRollbackUntilMillis(expiry) == null)
                check(runCatching { archive.rollbackImport() }.isFailure)
                check(archive.day(importedDate)?.total == Duration.ofSeconds(45))
                check(root.listFiles().orEmpty().none { it.name.startsWith("import-rollback-") })
                archive.importSnapshot(incoming)
                archive.clearHistory()
                check(archive.storedDataSummary().importRollbackUntilMillis == null)
                check(root.listFiles().orEmpty().none { it.name.startsWith("import-rollback-") })
            }
            result.putString("stream", "PASS: encrypted Android SQLite/WAL round trip, metadata, safety backup, legacy/compact formats, rejected malformed imports, rollback, reopen, deletion and batched legacy/compact reads across 400-day boundaries.\n")
            finish(-1, result)
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${android.util.Log.getStackTraceString(failure)}")
            finish(0, result)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun verifyCurrentUsageAndHistoryCache(context: Context, archive: LocalUsageArchive) = kotlinx.coroutines.runBlocking {
        archive.clearHistory()
        val zone = java.time.ZoneId.systemDefault()
        val today = LocalDate.of(2026, 10, 4)
        val now = today.atTime(12, 0).atZone(zone).toInstant()
        val oldDate = today.minusDays(5)
        fun events(date: LocalDate): List<app.still.domain.model.UsageEventRecord> {
            val start = date.atStartOfDay(zone).toInstant()
            return listOf(
                app.still.domain.model.UsageEventRecord(start.plusSeconds(60),
                    app.still.domain.model.UsageEventType.ActivityResumed, "example.app"),
                app.still.domain.model.UsageEventRecord(start.plusSeconds(180),
                    app.still.domain.model.UsageEventType.ActivityPaused, "example.app"),
            )
        }
        val log = mutableListOf<String>()
        val source = object : UsageDataSource {
            override fun events(start: java.time.Instant, end: java.time.Instant): List<app.still.domain.model.UsageEventRecord> {
                log += "events"
                return (events(oldDate) + events(today)).filter { !it.timestamp.isBefore(start) && !it.timestamp.isAfter(end) }
            }
            override fun dailyAppUsage(start: java.time.Instant, end: java.time.Instant): List<SystemDailyAppUsage> {
                log += "aggregate"
                return emptyList()
            }
        }
        val repository = UsageRepository(context, source, archive, java.time.Clock.fixed(now, zone))
        val cold = repository.dashboard { current ->
            log += "current"
            check(current.total == Duration.ofMinutes(2))
            check(archive.metadata("initial_import_complete") == null)
        }.getOrThrow()
        check(log == listOf("events", "current", "aggregate", "events"))
        check(cold.history.single().total == Duration.ofMinutes(2))
        val warm = repository.dashboard().getOrThrow()
        check(warm.history.single() === cold.history.single()) { "Unchanged completed days should be reused" }
        check(warm.comparison == cold.comparison)
        log.clear()
        check(repository.todayUsage().getOrThrow().total == Duration.ofMinutes(2))
        check(log == listOf("events")) { "Widget reads should not query or synchronize history" }
        val snapshot = File(context.noBackupFilesDir, "cache-invalidation-snapshot")
        archive.saveAggregate(oldDate, now.toEpochMilli(), listOf(
            AppUsage(AppInfo("example.app", "Example"), Duration.ofMinutes(9), 0),
        )) // The detailed record remains authoritative.
        repository.exportDatabase(snapshot)
        repository.clearHistory().getOrThrow()
        repository.importDatabase(snapshot)
        val restored = repository.dashboard().getOrThrow()
        check(restored.history.single() !== cold.history.single()) { "Import must invalidate reconstructed history" }
        check(restored.history.single().total == cold.history.single().total)
        snapshot.delete()
    }

    private fun verifyBatchedArchiveReads(archive: LocalUsageArchive) {
        val zone = java.time.ZoneId.systemDefault()
        val dates = (1L..405L).map { LocalDate.of(2026, 9, 1).minusDays(it) }
        val apps = listOf(
            AppUsage(AppInfo("example.app", "Renamed example"), Duration.ofMillis(12_550), 2),
            AppUsage(AppInfo("second.app", "Second"), Duration.ofMillis(8_450), 1),
        )
        dates.forEachIndexed { index, date ->
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            if (index % 2 == 0) {
                val events = listOf(
                    app.still.domain.model.UsageEventRecord(start.plusSeconds(60),
                        app.still.domain.model.UsageEventType.ActivityResumed, "example.app"),
                    app.still.domain.model.UsageEventRecord(start.plusSeconds(81),
                        app.still.domain.model.UsageEventType.ActivityPaused, "example.app"),
                )
                archive.saveDetailed(app.still.domain.model.DailyUsage(
                    date, start, end, Duration.ofSeconds(21), apps, emptyList(),
                    checkInCount = 2, quickCheckCount = 1, unlocks = 3, wakeups = 4,
                    longestBreak = Duration.ofSeconds(30), dayline = emptyList(),
                ), events)
            } else archive.saveAggregate(date, end.toEpochMilli(), apps)
        }
        // Preserve empty detailed days as valid baseline observations.
        val emptyDate = dates.first()
        val emptyDay = checkNotNull(archive.day(emptyDate)).copy(apps = emptyList(), total = Duration.ZERO)
        archive.saveDetailed(emptyDay, emptyList())
        val missing = dates.last().minusDays(1)
        val snapshots = archive.snapshots(dates + dates.first() + missing)
        check(snapshots.size == dates.size)
        check(missing !in snapshots)
        check(archive.snapshots(emptyList()).isEmpty())
        dates.forEach { date ->
            check(snapshots[date]?.day == archive.day(date)) { "Batch day differs: $date" }
            check(snapshots[date]?.events == archive.eventRecords(date)) { "Batch events differ: $date" }
        }
        check(snapshots[emptyDate]?.events == emptyList<app.still.domain.model.UsageEventRecord>())
        archive.pruneUnusedApps()
        check(archive.day(dates[1])?.apps?.size == 2)
    }

    private fun createSafetyBackup(file: File, date: LocalDate) {
        android.database.sqlite.SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE days(day INTEGER, range_end_ms INTEGER, total_ms INTEGER, unlocks INTEGER, wakeups INTEGER, longest_break_ms INTEGER, session_count INTEGER, quick_check_count INTEGER, longest_session_ms INTEGER, first_use_ms INTEGER, last_use_ms INTEGER, switch_count INTEGER, detailed INTEGER, events BLOB)")
            db.execSQL("CREATE TABLE app_days(day INTEGER, package_name TEXT, label TEXT, duration_ms INTEGER, opens INTEGER)")
            db.execSQL("CREATE TABLE metadata(key TEXT, value TEXT)")
            db.execSQL("CREATE TABLE app_switches(day INTEGER, first_package TEXT, second_package TEXT, switch_count INTEGER)")
            db.execSQL("CREATE TABLE hourly_usage(day INTEGER, local_hour INTEGER, duration_ms INTEGER)")
            db.execSQL("INSERT INTO days(day, range_end_ms, total_ms, detailed) VALUES (?, 123000, 120000, 0)", arrayOf(date.toEpochDay()))
            db.execSQL("INSERT INTO app_days VALUES (?, 'example.app', 'Example', 120000, 4)", arrayOf(date.toEpochDay()))
            LegacyArchiveBackup.create(db, file)
        }
    }
}
