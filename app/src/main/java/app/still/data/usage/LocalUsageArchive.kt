package app.still.data.usage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.still.domain.model.AppInfo
import app.still.domain.model.AppUsage
import app.still.domain.model.DailyUsage
import app.still.domain.model.UsageEventRecord
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
    1,
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
            db.insertWithOnConflict(
                "days", null,
                ContentValues().apply {
                    put("day", epochDay)
                    put("range_end_ms", day.rangeEnd.toEpochMilli())
                    put("total_ms", day.total.toMillis())
                    put("unlocks", day.unlocks)
                    put("wakeups", day.wakeups)
                    day.longestBreak?.let { put("longest_break_ms", it.toMillis()) } ?: putNull("longest_break_ms")
                    put("detailed", 1)
                    put("events", UsageEventCodec.encode(events))
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            replaceApps(db, epochDay, day.apps, includeOpens = true)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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
