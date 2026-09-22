package app.still.data.usage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.still.domain.analytics.SessionAnalyzer
import app.still.domain.analytics.UsagePatternSummarizer
import app.still.domain.model.AppInfo
import app.still.domain.model.AppUsage
import app.still.domain.model.DailyUsage
import app.still.domain.model.UsageEventRecord
import java.io.File
import java.time.Duration
import java.time.LocalDate

enum class ArchiveStorageFormat { Compact, Legacy }

data class StoredDataSummary(
    val dayCount: Long,
    val appCount: Long,
    val oldestDate: LocalDate?,
    val newestDate: LocalDate?,
    val lastUpdatedMillis: Long?,
    val sizeBytes: Long,
    val backupSizeBytes: Long,
    val backupAvailable: Boolean,
    val storageFormat: ArchiveStorageFormat,
)

data class ArchiveMigrationNotice(
    val originalSizeBytes: Long,
    val compactSizeBytes: Long,
    val backupSizeBytes: Long,
) {
    val activeBytesSaved: Long get() = (originalSizeBytes - compactSizeBytes).coerceAtLeast(0L)
    val netBytesSaved: Long get() = originalSizeBytes - compactSizeBytes - backupSizeBytes
}

/**
 * Private app-database archive. Version 4 stores a canonical event stream and
 * normalized app identities. A compressed version-3 snapshot is retained for
 * explicit rollback and is excluded from Android backup and device transfer.
 */
class LocalUsageArchive(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    private val appContext = context.applicationContext
    private val databaseFiles = listOf(
        appContext.getDatabasePath(DATABASE_NAME),
        appContext.getDatabasePath("$DATABASE_NAME-wal"),
        appContext.getDatabasePath("$DATABASE_NAME-shm"),
    )
    private val legacyBackupFile = File(appContext.noBackupFilesDir, LEGACY_BACKUP_NAME)

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createCompactSchema(db)
        createMetadataTable(db)
        createControlTable(db)
        writeControl(db, FORMAT_COMPACT, 0L, 0L, noticePending = false, compacted = true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createAppSwitchesTable(db)
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE days ADD COLUMN session_count INTEGER")
            db.execSQL("ALTER TABLE days ADD COLUMN quick_check_count INTEGER")
            db.execSQL("ALTER TABLE days ADD COLUMN longest_session_ms INTEGER")
            db.execSQL("ALTER TABLE days ADD COLUMN first_use_ms INTEGER")
            db.execSQL("ALTER TABLE days ADD COLUMN last_use_ms INTEGER")
            db.execSQL("ALTER TABLE days ADD COLUMN switch_count INTEGER")
            createHourlyUsageTable(db)
        }
        if (oldVersion < 4) migrateLegacyArchive(db)
        if (oldVersion == 4 && newVersion >= 5) repairVersionFourControl(db)
    }

    fun metadata(key: String): String? = readableDatabase.query(
        "metadata", arrayOf("value"), "key = ?", arrayOf(key), null, null, null,
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun putMetadata(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "metadata",
            null,
            ContentValues().apply { put("key", key); put("value", value) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun clearHistory() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("days", null, null)
            db.delete("app_days", null, null)
            if (isCompact(db)) db.delete("apps", null, null)
            db.delete("metadata", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (legacyBackupFile.exists()) legacyBackupFile.delete()
    }

    fun storedDataSummary(): StoredDataSummary {
        val db = readableDatabase
        val compact = isCompact(db)
        val history = db.rawQuery(
            "SELECT COUNT(*), MIN(day), MAX(day), MAX(range_end_ms) FROM days",
            null,
        ).use { cursor ->
            check(cursor.moveToFirst())
            HistorySummary(
                dayCount = cursor.getLong(0),
                oldestDate = if (cursor.isNull(1)) null else LocalDate.ofEpochDay(cursor.getLong(1)),
                newestDate = if (cursor.isNull(2)) null else LocalDate.ofEpochDay(cursor.getLong(2)),
                lastUpdatedMillis = if (cursor.isNull(3)) null else cursor.getLong(3),
            )
        }
        val distinctAppColumn = if (compact) "app_id" else "package_name"
        val appCount = db.rawQuery("SELECT COUNT(DISTINCT $distinctAppColumn) FROM app_days", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
        val backupSize = legacyBackupFile.takeIf(File::isFile)?.length() ?: 0L
        return StoredDataSummary(
            dayCount = history.dayCount,
            appCount = appCount,
            oldestDate = history.oldestDate,
            newestDate = history.newestDate,
            lastUpdatedMillis = history.lastUpdatedMillis,
            sizeBytes = databaseSizeBytes() + backupSize,
            backupSizeBytes = backupSize,
            backupAvailable = backupSize > 0L,
            storageFormat = if (compact) ArchiveStorageFormat.Compact else ArchiveStorageFormat.Legacy,
        )
    }

    fun migrationNotice(): ArchiveMigrationNotice? {
        val db = writableDatabase
        if (!isCompact(db)) return null
        val control = readControl(db) ?: return null
        if (!control.noticePending) return null
        if (!control.compacted) {
            runCatching { db.execSQL("VACUUM") }
                .onSuccess { updateControl(db, "compacted", 1) }
        }
        return ArchiveMigrationNotice(
            originalSizeBytes = control.originalBytes,
            compactSizeBytes = databaseSizeBytes(),
            backupSizeBytes = legacyBackupFile.takeIf(File::isFile)?.length() ?: 0L,
        )
    }

    fun acknowledgeMigrationNotice() {
        updateControl(writableDatabase, "notice_pending", 0)
    }

    fun deleteLegacyBackup() {
        require(isCompact(readableDatabase)) { "The safety backup can only be deleted while the compact archive is active" }
        require(legacyBackupFile.isFile) { "No archive backup is available" }
        check(legacyBackupFile.delete()) { "The safety backup could not be deleted" }
    }

    fun restoreLegacyBackup() {
        require(legacyBackupFile.isFile) { "No archive backup is available" }
        val db = writableDatabase
        require(isCompact(db)) { "The legacy archive is already active" }
        val control = checkNotNull(readControl(db))
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TEMP TABLE restore_days AS SELECT * FROM days WHERE range_end_ms > ?",
                arrayOf(control.backupRangeEndMillis),
            )
            db.execSQL(
                """CREATE TEMP TABLE restore_app_days AS
                   SELECT app_days.day, apps.package_name, apps.label, app_days.duration_seconds, app_days.opens
                   FROM app_days JOIN apps ON apps.id = app_days.app_id
                   WHERE app_days.day IN (SELECT day FROM restore_days)""",
            )
            db.execSQL("CREATE TEMP TABLE restore_metadata AS SELECT * FROM metadata")
            db.execSQL("DROP TABLE app_days")
            db.execSQL("DROP TABLE apps")
            db.execSQL("DROP TABLE days")
            db.delete("metadata", null, null)
            createLegacySchema(db)
            LegacyArchiveBackup.restore(db, legacyBackupFile)
            restorePostMigrationHistory(db)
            db.execSQL("INSERT OR REPLACE INTO metadata SELECT * FROM restore_metadata")
            db.execSQL("DROP TABLE restore_metadata")
            db.execSQL("DROP TABLE restore_app_days")
            db.execSQL("DROP TABLE restore_days")
            writeControl(
                db, FORMAT_LEGACY, 0L, control.backupRangeEndMillis,
                noticePending = false, compacted = true,
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        runCatching { db.execSQL("VACUUM") }
    }

    fun dates(): List<LocalDate> = readableDatabase.query(
        "days", arrayOf("day"), null, null, null, null, "day DESC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(LocalDate.ofEpochDay(cursor.getLong(0))) } }

    fun eventRecords(date: LocalDate): List<UsageEventRecord>? = readableDatabase.query(
        "days", arrayOf("events"), "day = ? AND detailed = 1", arrayOf(date.toEpochDay().toString()), null, null, null,
    ).use { cursor ->
        if (!cursor.moveToFirst() || cursor.isNull(0)) null else UsageEventCodec.decode(cursor.getBlob(0))
    }

    fun day(date: LocalDate): DailyUsage? = if (isCompact(readableDatabase)) compactDay(date) else legacyDay(date)

    fun saveAggregate(date: LocalDate, rangeEndMillis: Long, apps: List<AppUsage>) {
        if (isCompact(writableDatabase)) saveCompactAggregate(date, rangeEndMillis, apps)
        else saveLegacyAggregate(date, rangeEndMillis, apps)
    }

    fun saveDetailed(day: DailyUsage, events: List<UsageEventRecord>) {
        if (isCompact(writableDatabase)) saveCompactDetailed(day, events)
        else saveLegacyDetailed(day, events)
    }

    private fun compactDay(date: LocalDate): DailyUsage? {
        val db = readableDatabase
        val values = db.query(
            "days", arrayOf("range_end_ms", "detailed"), "day = ?", arrayOf(date.toEpochDay().toString()), null, null, null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getLong(0) to (cursor.getInt(1) == 1)
        }
        val apps = compactApps(db, date.toEpochDay())
        val zone = java.time.ZoneId.systemDefault()
        return DailyUsage(
            date = date,
            rangeStart = date.atStartOfDay(zone).toInstant(),
            rangeEnd = java.time.Instant.ofEpochMilli(values.first),
            total = apps.fold(Duration.ZERO) { total, app -> total.plus(app.duration) },
            apps = apps,
            sessions = emptyList(),
            unlocks = 0,
            wakeups = 0,
            longestBreak = null,
            dayline = emptyList(),
            detailsAvailable = values.second,
        )
    }

    private fun compactApps(db: SQLiteDatabase, day: Long): List<AppUsage> = db.rawQuery(
        """SELECT apps.package_name, apps.label, app_days.duration_seconds, app_days.opens
           FROM app_days JOIN apps ON apps.id = app_days.app_id
           WHERE app_days.day = ? ORDER BY app_days.duration_seconds DESC""",
        arrayOf(day.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    AppUsage(
                        AppInfo(cursor.getString(0), cursor.getString(1)),
                        Duration.ofSeconds(cursor.getLong(2)),
                        if (cursor.isNull(3)) 0 else cursor.getInt(3),
                    ),
                )
            }
        }
    }

    private fun legacyDay(date: LocalDate): DailyUsage? {
        val values = readableDatabase.query(
            "days",
            arrayOf("range_end_ms", "total_ms", "unlocks", "wakeups", "longest_break_ms", "session_count", "quick_check_count", "detailed"),
            "day = ?", arrayOf(date.toEpochDay().toString()), null, null, null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            LegacyDayValues(
                rangeEndMillis = cursor.getLong(0),
                totalMillis = cursor.getLong(1),
                unlocks = if (cursor.isNull(2)) 0 else cursor.getInt(2),
                wakeups = if (cursor.isNull(3)) 0 else cursor.getInt(3),
                longestBreakMillis = if (cursor.isNull(4)) null else cursor.getLong(4),
                checkInCount = if (cursor.isNull(5)) 0 else cursor.getInt(5),
                quickCheckCount = if (cursor.isNull(6)) 0 else cursor.getInt(6),
                detailed = cursor.getInt(7) == 1,
            )
        }
        val apps = readableDatabase.query(
            "app_days", arrayOf("package_name", "label", "duration_ms", "opens"),
            "day = ?", arrayOf(date.toEpochDay().toString()), null, null, "duration_ms DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        AppUsage(
                            AppInfo(cursor.getString(0), cursor.getString(1)),
                            Duration.ofMillis(cursor.getLong(2)),
                            if (cursor.isNull(3)) 0 else cursor.getInt(3),
                        ),
                    )
                }
            }
        }
        val zone = java.time.ZoneId.systemDefault()
        return DailyUsage(
            date = date,
            rangeStart = date.atStartOfDay(zone).toInstant(),
            rangeEnd = java.time.Instant.ofEpochMilli(values.rangeEndMillis),
            total = Duration.ofMillis(values.totalMillis),
            apps = apps,
            sessions = emptyList(),
            checkInCount = values.checkInCount,
            quickCheckCount = values.quickCheckCount,
            unlocks = values.unlocks,
            wakeups = values.wakeups,
            longestBreak = values.longestBreakMillis?.let(Duration::ofMillis),
            dayline = emptyList(),
            detailsAvailable = values.detailed,
        )
    }

    private fun saveCompactAggregate(date: LocalDate, rangeEndMillis: Long, apps: List<AppUsage>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val day = date.toEpochDay()
            val isDetailed = db.query("days", arrayOf("detailed"), "day = ?", arrayOf(day.toString()), null, null, null)
                .use { it.moveToFirst() && it.getInt(0) == 1 }
            if (!isDetailed) {
                db.insertWithOnConflict(
                    "days", null,
                    ContentValues().apply {
                        put("day", day)
                        put("range_end_ms", quantizeMillis(rangeEndMillis))
                        put("detailed", 0)
                        putNull("events")
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                replaceCompactApps(db, day, apps, includeOpens = false)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun saveCompactDetailed(day: DailyUsage, events: List<UsageEventRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val epochDay = day.date.toEpochDay()
            db.insertWithOnConflict(
                "days", null,
                ContentValues().apply {
                    put("day", epochDay)
                    put("range_end_ms", quantizeMillis(day.rangeEnd.toEpochMilli()))
                    put("detailed", 1)
                    put("events", UsageEventCodec.encode(events))
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            replaceCompactApps(db, epochDay, day.apps, includeOpens = true)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun replaceCompactApps(db: SQLiteDatabase, day: Long, apps: List<AppUsage>, includeOpens: Boolean) {
        db.delete("app_days", "day = ?", arrayOf(day.toString()))
        val durations = quantizeDurations(apps.map { it.duration.toMillis() })
        apps.forEachIndexed { index, usage ->
            db.insertWithOnConflict(
                "apps", null,
                ContentValues().apply {
                    put("package_name", usage.app.packageName)
                    put("label", usage.app.label)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            db.update(
                "apps", ContentValues().apply { put("label", usage.app.label) },
                "package_name = ?", arrayOf(usage.app.packageName),
            )
            val appId = db.query(
                "apps", arrayOf("id"), "package_name = ?", arrayOf(usage.app.packageName), null, null, null,
            ).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }
            db.insertOrThrow(
                "app_days", null,
                ContentValues().apply {
                    put("day", day)
                    put("app_id", appId)
                    put("duration_seconds", durations[index])
                    if (includeOpens) put("opens", usage.opens) else putNull("opens")
                },
            )
        }
        db.execSQL("DELETE FROM apps WHERE id NOT IN (SELECT app_id FROM app_days)")
    }

    private fun saveLegacyAggregate(date: LocalDate, rangeEndMillis: Long, apps: List<AppUsage>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val day = date.toEpochDay()
            val isDetailed = db.query("days", arrayOf("detailed"), "day = ?", arrayOf(day.toString()), null, null, null)
                .use { it.moveToFirst() && it.getInt(0) == 1 }
            if (!isDetailed) {
                db.insertWithOnConflict(
                    "days", null,
                    ContentValues().apply {
                        put("day", day)
                        put("range_end_ms", rangeEndMillis)
                        put("total_ms", apps.sumOf { it.duration.toMillis() })
                        put("detailed", 0)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                replaceLegacyApps(db, day, apps, includeOpens = false)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun saveLegacyDetailed(day: DailyUsage, events: List<UsageEventRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val epochDay = day.date.toEpochDay()
            val patterns = UsagePatternSummarizer.summarize(day, java.time.ZoneId.systemDefault())
            db.insertWithOnConflict(
                "days", null,
                ContentValues().apply {
                    put("day", epochDay)
                    put("range_end_ms", day.rangeEnd.toEpochMilli())
                    put("total_ms", day.total.toMillis())
                    put("unlocks", day.unlocks)
                    put("wakeups", day.wakeups)
                    day.longestBreak?.let { put("longest_break_ms", it.toMillis()) } ?: putNull("longest_break_ms")
                    put("session_count", day.checkInCount)
                    put("quick_check_count", patterns.quickCheckCount)
                    patterns.longestSessionMillis?.let { put("longest_session_ms", it) } ?: putNull("longest_session_ms")
                    patterns.firstUseMillis?.let { put("first_use_ms", it) } ?: putNull("first_use_ms")
                    patterns.lastUseMillis?.let { put("last_use_ms", it) } ?: putNull("last_use_ms")
                    put("switch_count", patterns.switchCount)
                    put("detailed", 1)
                    put("events", UsageEventCodec.encode(events))
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            replaceLegacyApps(db, epochDay, day.apps, includeOpens = true)
            replaceFrequentSwitches(db, epochDay, day)
            replaceHourlyUsage(db, epochDay, patterns.hourlyUsageMillis)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun replaceLegacyApps(db: SQLiteDatabase, day: Long, apps: List<AppUsage>, includeOpens: Boolean) {
        db.delete("app_days", "day = ?", arrayOf(day.toString()))
        apps.forEach { usage ->
            db.insertOrThrow(
                "app_days", null,
                ContentValues().apply {
                    put("day", day)
                    put("package_name", usage.app.packageName)
                    put("label", usage.app.label)
                    put("duration_ms", usage.duration.toMillis())
                    if (includeOpens) put("opens", usage.opens) else putNull("opens")
                },
            )
        }
    }

    private fun migrateLegacyArchive(db: SQLiteDatabase) {
        val hasHistory = db.rawQuery("SELECT EXISTS(SELECT 1 FROM days LIMIT 1)", null).use {
            it.moveToFirst() && it.getInt(0) == 1
        }
        val originalBytes = if (hasHistory) databaseSizeBytes() else 0L
        val backupRangeEndMillis = if (hasHistory) {
            db.rawQuery("SELECT MAX(range_end_ms) FROM days", null).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            }
        } else 0L
        if (hasHistory) LegacyArchiveBackup.create(db, legacyBackupFile)

        createCompactSchema(db, prefix = "compact_")
        db.query("days", arrayOf("day", "range_end_ms", "detailed", "events"), null, null, null, null, "day").use { cursor ->
            while (cursor.moveToNext()) {
                val hadDetails = cursor.getInt(2) == 1
                val compactEvents = if (hadDetails && !cursor.isNull(3)) {
                    runCatching { UsageEventCodec.encode(UsageEventCodec.decode(cursor.getBlob(3))) }.getOrNull()
                } else null
                val detailed = hadDetails && compactEvents != null
                db.insertOrThrow(
                    "compact_days", null,
                    ContentValues().apply {
                        put("day", cursor.getLong(0))
                        put("range_end_ms", quantizeMillis(cursor.getLong(1)))
                        put("detailed", if (detailed) 1 else 0)
                        compactEvents?.let { put("events", it) } ?: putNull("events")
                    },
                )
            }
        }
        migrateLegacyApps(db)

        db.execSQL("DROP TABLE app_switches")
        db.execSQL("DROP TABLE hourly_usage")
        db.execSQL("DROP TABLE app_days")
        db.execSQL("DROP TABLE days")
        db.execSQL("ALTER TABLE compact_days RENAME TO days")
        db.execSQL("ALTER TABLE compact_apps RENAME TO apps")
        db.execSQL("ALTER TABLE compact_app_days RENAME TO app_days")
        createCompactIndexes(db)
        createControlTable(db)
        writeControl(
            db,
            FORMAT_COMPACT,
            originalBytes,
            backupRangeEndMillis,
            noticePending = hasHistory,
            compacted = !hasHistory,
        )
    }

    private fun repairVersionFourControl(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE archive_control ADD COLUMN backup_range_end_ms INTEGER NOT NULL DEFAULT 0")
        val backupRangeEnd = runCatching { LegacyArchiveBackup.latestRangeEndMillis(legacyBackupFile) }.getOrDefault(0L)
        db.update(
            "archive_control",
            ContentValues().apply { put("backup_range_end_ms", backupRangeEnd) },
            "id = 1",
            null,
        )
    }

    private fun createCompactSchema(db: SQLiteDatabase, prefix: String = "") {
        db.execSQL(
            """CREATE TABLE ${prefix}days (
                day INTEGER PRIMARY KEY,
                range_end_ms INTEGER NOT NULL,
                detailed INTEGER NOT NULL DEFAULT 0,
                events BLOB
            ) WITHOUT ROWID""",
        )
        db.execSQL(
            """CREATE TABLE ${prefix}apps (
                id INTEGER PRIMARY KEY,
                package_name TEXT NOT NULL UNIQUE,
                label TEXT NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE ${prefix}app_days (
                day INTEGER NOT NULL,
                app_id INTEGER NOT NULL,
                duration_seconds INTEGER NOT NULL,
                opens INTEGER,
                PRIMARY KEY(day, app_id)
            ) WITHOUT ROWID""",
        )
        if (prefix.isEmpty()) createCompactIndexes(db)
    }

    private fun createCompactIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS app_days_app ON app_days(app_id, day)")
    }

    private fun createLegacySchema(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE days (
                day INTEGER PRIMARY KEY,
                range_end_ms INTEGER NOT NULL,
                total_ms INTEGER NOT NULL,
                unlocks INTEGER,
                wakeups INTEGER,
                longest_break_ms INTEGER,
                session_count INTEGER,
                quick_check_count INTEGER,
                longest_session_ms INTEGER,
                first_use_ms INTEGER,
                last_use_ms INTEGER,
                switch_count INTEGER,
                detailed INTEGER NOT NULL DEFAULT 0,
                events BLOB
            )""",
        )
        db.execSQL(
            """CREATE TABLE app_days (
                day INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                label TEXT NOT NULL,
                duration_ms INTEGER NOT NULL,
                opens INTEGER,
                PRIMARY KEY(day, package_name),
                FOREIGN KEY(day) REFERENCES days(day) ON DELETE CASCADE
            ) WITHOUT ROWID""",
        )
        db.execSQL("CREATE INDEX app_days_package ON app_days(package_name, day)")
        createAppSwitchesTable(db)
        createHourlyUsageTable(db)
    }

    private fun createMetadataTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID")
    }

    private fun createControlTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS archive_control (
                id INTEGER PRIMARY KEY CHECK(id = 1),
                format INTEGER NOT NULL,
                original_bytes INTEGER NOT NULL DEFAULT 0,
                backup_range_end_ms INTEGER NOT NULL DEFAULT 0,
                notice_pending INTEGER NOT NULL DEFAULT 0,
                compacted INTEGER NOT NULL DEFAULT 0
            )""",
        )
    }

    private fun writeControl(
        db: SQLiteDatabase,
        format: Int,
        originalBytes: Long,
        backupRangeEndMillis: Long,
        noticePending: Boolean,
        compacted: Boolean,
    ) {
        db.insertWithOnConflict(
            "archive_control", null,
            ContentValues().apply {
                put("id", 1)
                put("format", format)
                put("original_bytes", originalBytes)
                put("backup_range_end_ms", backupRangeEndMillis)
                put("notice_pending", if (noticePending) 1 else 0)
                put("compacted", if (compacted) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun readControl(db: SQLiteDatabase): ControlValues? = db.query(
        "archive_control", arrayOf("format", "original_bytes", "backup_range_end_ms", "notice_pending", "compacted"),
        "id = 1", null, null, null, null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else ControlValues(
            format = cursor.getInt(0),
            originalBytes = cursor.getLong(1),
            backupRangeEndMillis = cursor.getLong(2),
            noticePending = cursor.getInt(3) == 1,
            compacted = cursor.getInt(4) == 1,
        )
    }

    private fun updateControl(db: SQLiteDatabase, column: String, value: Int) {
        db.update("archive_control", ContentValues().apply { put(column, value) }, "id = 1", null)
    }

    private fun isCompact(db: SQLiteDatabase): Boolean = readControl(db)?.format != FORMAT_LEGACY

    private fun restorePostMigrationHistory(db: SQLiteDatabase) {
        db.query("restore_days", arrayOf("day", "range_end_ms", "detailed", "events"), null, null, null, null, "day").use { cursor ->
            while (cursor.moveToNext()) {
                val day = cursor.getLong(0)
                val totalMillis = db.rawQuery(
                    "SELECT COALESCE(SUM(duration_seconds), 0) * 1000 FROM restore_app_days WHERE day = ?",
                    arrayOf(day.toString()),
                ).use { totalCursor -> check(totalCursor.moveToFirst()); totalCursor.getLong(0) }
                db.insertWithOnConflict(
                    "days", null,
                    ContentValues().apply {
                        put("day", day)
                        put("range_end_ms", cursor.getLong(1))
                        put("total_ms", totalMillis)
                        putNull("unlocks")
                        putNull("wakeups")
                        putNull("longest_break_ms")
                        putNull("session_count")
                        putNull("quick_check_count")
                        putNull("longest_session_ms")
                        putNull("first_use_ms")
                        putNull("last_use_ms")
                        putNull("switch_count")
                        put("detailed", cursor.getInt(2))
                        if (cursor.isNull(3)) putNull("events") else put("events", cursor.getBlob(3))
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                db.delete("app_days", "day = ?", arrayOf(day.toString()))
            }
        }
        db.query(
            "restore_app_days", arrayOf("day", "package_name", "label", "duration_seconds", "opens"),
            null, null, null, null, "day, package_name",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                db.insertOrThrow(
                    "app_days", null,
                    ContentValues().apply {
                        put("day", cursor.getLong(0))
                        put("package_name", cursor.getString(1))
                        put("label", cursor.getString(2))
                        put("duration_ms", cursor.getLong(3) * 1_000L)
                        if (cursor.isNull(4)) putNull("opens") else put("opens", cursor.getInt(4))
                    },
                )
            }
        }
    }

    private fun replaceFrequentSwitches(db: SQLiteDatabase, day: Long, usage: DailyUsage) {
        db.delete("app_switches", "day = ?", arrayOf(day.toString()))
        SessionAnalyzer.frequentSwitches(usage.sessions).forEach { pair ->
            db.insertOrThrow(
                "app_switches", null,
                ContentValues().apply {
                    put("day", day)
                    put("first_package", pair.firstPackage)
                    put("second_package", pair.secondPackage)
                    put("switch_count", pair.switchCount)
                },
            )
        }
    }

    private fun createAppSwitchesTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS app_switches (
                day INTEGER NOT NULL,
                first_package TEXT NOT NULL,
                second_package TEXT NOT NULL,
                switch_count INTEGER NOT NULL,
                PRIMARY KEY(day, first_package, second_package),
                FOREIGN KEY(day) REFERENCES days(day) ON DELETE CASCADE
            ) WITHOUT ROWID""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS app_switches_frequency ON app_switches(switch_count DESC, day DESC)")
    }

    private fun replaceHourlyUsage(db: SQLiteDatabase, day: Long, millisecondsByHour: List<Long>) {
        db.delete("hourly_usage", "day = ?", arrayOf(day.toString()))
        millisecondsByHour.forEachIndexed { hour, durationMillis ->
            if (durationMillis <= 0L) return@forEachIndexed
            db.insertOrThrow(
                "hourly_usage", null,
                ContentValues().apply {
                    put("day", day)
                    put("local_hour", hour)
                    put("duration_ms", durationMillis)
                },
            )
        }
    }

    private fun createHourlyUsageTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS hourly_usage (
                day INTEGER NOT NULL,
                local_hour INTEGER NOT NULL CHECK(local_hour BETWEEN 0 AND 23),
                duration_ms INTEGER NOT NULL,
                PRIMARY KEY(day, local_hour),
                FOREIGN KEY(day) REFERENCES days(day) ON DELETE CASCADE
            ) WITHOUT ROWID""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS hourly_usage_hour ON hourly_usage(local_hour, day)")
    }

    private fun databaseSizeBytes(): Long = databaseFiles.filter(File::isFile).sumOf(File::length)

    private fun migrateLegacyApps(db: SQLiteDatabase) {
        db.query(
            "app_days", arrayOf("day", "package_name", "label", "duration_ms", "opens"),
            null, null, null, null, "day, package_name",
        ).use { cursor ->
            var currentDay: Long? = null
            val rows = mutableListOf<LegacyAppRow>()
            fun flush() {
                val day = currentDay ?: return
                val durations = quantizeDurations(rows.map(LegacyAppRow::durationMillis))
                rows.forEachIndexed { index, row ->
                    db.insertWithOnConflict(
                        "compact_apps", null,
                        ContentValues().apply { put("package_name", row.packageName); put("label", row.label) },
                        SQLiteDatabase.CONFLICT_IGNORE,
                    )
                    val appId = db.query(
                        "compact_apps", arrayOf("id"), "package_name = ?", arrayOf(row.packageName), null, null, null,
                    ).use { appCursor -> check(appCursor.moveToFirst()); appCursor.getLong(0) }
                    db.insertOrThrow(
                        "compact_app_days", null,
                        ContentValues().apply {
                            put("day", day)
                            put("app_id", appId)
                            put("duration_seconds", durations[index])
                            row.opens?.let { put("opens", it) } ?: putNull("opens")
                        },
                    )
                }
                rows.clear()
            }
            while (cursor.moveToNext()) {
                val day = cursor.getLong(0)
                if (currentDay != null && currentDay != day) flush()
                currentDay = day
                rows += LegacyAppRow(
                    packageName = cursor.getString(1),
                    label = cursor.getString(2),
                    durationMillis = cursor.getLong(3),
                    opens = if (cursor.isNull(4)) null else cursor.getInt(4),
                )
            }
            flush()
        }
    }

    private fun quantizeDurations(milliseconds: List<Long>): LongArray {
        if (milliseconds.isEmpty()) return LongArray(0)
        val seconds = LongArray(milliseconds.size) { index -> milliseconds[index].coerceAtLeast(0L) / 1_000L }
        val target = (milliseconds.sum().coerceAtLeast(0L) + 500L) / 1_000L
        val extras = (target - seconds.sum()).coerceIn(0L, milliseconds.size.toLong()).toInt()
        milliseconds.indices
            .sortedByDescending { milliseconds[it].coerceAtLeast(0L) % 1_000L }
            .take(extras)
            .forEach { seconds[it] += 1L }
        return seconds
    }

    private fun quantizeMillis(milliseconds: Long): Long = (milliseconds / 1_000L) * 1_000L

    private data class LegacyDayValues(
        val rangeEndMillis: Long,
        val totalMillis: Long,
        val unlocks: Int,
        val wakeups: Int,
        val longestBreakMillis: Long?,
        val checkInCount: Int,
        val quickCheckCount: Int,
        val detailed: Boolean,
    )

    private data class HistorySummary(
        val dayCount: Long,
        val oldestDate: LocalDate?,
        val newestDate: LocalDate?,
        val lastUpdatedMillis: Long?,
    )

    private data class ControlValues(
        val format: Int,
        val originalBytes: Long,
        val backupRangeEndMillis: Long,
        val noticePending: Boolean,
        val compacted: Boolean,
    )

    private data class LegacyAppRow(
        val packageName: String,
        val label: String,
        val durationMillis: Long,
        val opens: Int?,
    )

    private companion object {
        const val DATABASE_NAME = "usage_history.db"
        const val DATABASE_VERSION = 5
        const val FORMAT_LEGACY = 3
        const val FORMAT_COMPACT = 4
        const val LEGACY_BACKUP_NAME = "usage_history_v3_backup.gz"
    }
}
