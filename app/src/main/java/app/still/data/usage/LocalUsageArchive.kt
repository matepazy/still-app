package app.still.data.usage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.still.domain.model.AppInfo
import app.still.domain.model.AppUsage
import app.still.domain.model.DailyUsage
import app.still.domain.model.UsageEventRecord
import app.still.domain.analytics.SessionAnalyzer
import app.still.domain.analytics.UsagePatternSummarizer
import java.time.Duration
import java.time.LocalDate

/**
 * Private app-database archive. Daily aggregates are normalized for cheap stats
 * queries; event streams are stored as gzip blobs only while detailed events exist.
 */
class LocalUsageArchive(context: Context) : SQLiteOpenHelper(
    context,
    "usage_history.db",
    null,
    3,
) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
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
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID")
        db.execSQL("CREATE INDEX app_days_package ON app_days(package_name, day)")
        createAppSwitchesTable(db)
        createHourlyUsageTable(db)
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
            db.delete("metadata", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun dates(): List<LocalDate> = readableDatabase.query(
        "days", arrayOf("day"), null, null, null, null, "day DESC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(LocalDate.ofEpochDay(cursor.getLong(0))) } }

    fun eventRecords(date: LocalDate): List<UsageEventRecord>? = readableDatabase.query(
        "days", arrayOf("events"), "day = ? AND detailed = 1", arrayOf(date.toEpochDay().toString()), null, null, null,
    ).use { cursor ->
        if (!cursor.moveToFirst() || cursor.isNull(0)) null else UsageEventCodec.decode(cursor.getBlob(0))
    }

    fun day(date: LocalDate): DailyUsage? {
        val values = readableDatabase.query(
            "days",
            arrayOf("range_end_ms", "total_ms", "unlocks", "wakeups", "longest_break_ms", "detailed"),
            "day = ?", arrayOf(date.toEpochDay().toString()), null, null, null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            DayValues(
                rangeEndMillis = cursor.getLong(0),
                totalMillis = cursor.getLong(1),
                unlocks = if (cursor.isNull(2)) 0 else cursor.getInt(2),
                wakeups = if (cursor.isNull(3)) 0 else cursor.getInt(3),
                longestBreakMillis = if (cursor.isNull(4)) null else cursor.getLong(4),
                detailed = cursor.getInt(5) == 1,
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
            unlocks = values.unlocks,
            wakeups = values.wakeups,
            longestBreak = values.longestBreakMillis?.let(Duration::ofMillis),
            dayline = emptyList(),
            detailsAvailable = values.detailed,
        )
    }

    fun saveAggregate(date: LocalDate, rangeEndMillis: Long, apps: List<AppUsage>) {
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
                replaceApps(db, day, apps, includeOpens = false)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun saveDetailed(day: DailyUsage, events: List<UsageEventRecord>) {
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
                    put("session_count", patterns.sessionCount)
                    put("quick_check_count", patterns.quickCheckCount)
                    patterns.longestSessionMillis
                        ?.let { put("longest_session_ms", it) }
                        ?: putNull("longest_session_ms")
                    patterns.firstUseMillis
                        ?.let { put("first_use_ms", it) }
                        ?: putNull("first_use_ms")
                    patterns.lastUseMillis
                        ?.let { put("last_use_ms", it) }
                        ?: putNull("last_use_ms")
                    put("switch_count", patterns.switchCount)
                    put("detailed", 1)
                    put("events", UsageEventCodec.encode(events))
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            replaceApps(db, epochDay, day.apps, includeOpens = true)
            replaceFrequentSwitches(db, epochDay, day)
            replaceHourlyUsage(db, epochDay, patterns.hourlyUsageMillis)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
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

    private fun replaceApps(db: SQLiteDatabase, day: Long, apps: List<AppUsage>, includeOpens: Boolean) {
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

    private data class DayValues(
        val rangeEndMillis: Long,
        val totalMillis: Long,
        val unlocks: Int,
        val wakeups: Int,
        val longestBreakMillis: Long?,
        val detailed: Boolean,
    )
}
