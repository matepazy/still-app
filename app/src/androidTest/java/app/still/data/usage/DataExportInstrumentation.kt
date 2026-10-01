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
                archive.close()
                check(archive.storedDataSummary().dayCount == 1L)
                archive.clearHistory()
                check(archive.storedDataSummary().dayCount == 0L)
            }
            result.putString("stream", "PASS: encrypted Android SQLite/WAL round trip, metadata, safety backup, legacy/compact formats, rejected malformed imports, rollback, reopen and deletion.\n")
            finish(-1, result)
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${android.util.Log.getStackTraceString(failure)}")
            finish(0, result)
        } finally {
            root.deleteRecursively()
        }
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
