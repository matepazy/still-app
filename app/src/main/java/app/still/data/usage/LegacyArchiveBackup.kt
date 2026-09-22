package app.still.data.usage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Exact logical snapshot of the version-3 archive, kept outside Android backup. */
internal object LegacyArchiveBackup {
    private const val MAGIC = "STILL_USAGE_BACKUP"
    private const val VERSION = 1

    fun create(db: SQLiteDatabase, destination: File) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        if (temporary.exists()) check(temporary.delete())
        DataOutputStream(GZIPOutputStream(BufferedOutputStream(FileOutputStream(temporary)))).use { output ->
            output.writeUTF(MAGIC)
            output.writeInt(VERSION)
            writeDays(db, output)
            writeAppDays(db, output)
            writeMetadata(db, output)
            writeAppSwitches(db, output)
            writeHourlyUsage(db, output)
        }
        read(temporary, null)
        if (destination.exists()) check(destination.delete())
        check(temporary.renameTo(destination)) { "Could not finish archive backup" }
    }

    fun restore(db: SQLiteDatabase, source: File) {
        require(source.isFile) { "The legacy archive backup is missing" }
        read(source, db)
    }

    fun latestRangeEndMillis(source: File): Long {
        if (!source.isFile) return 0L
        return DataInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(source)))).use { input ->
            require(input.readUTF() == MAGIC) { "Not a Still archive backup" }
            require(input.readInt() == VERSION) { "Unsupported Still archive backup" }
            var latest = 0L
            repeat(input.readInt()) {
                input.readLong() // day
                latest = maxOf(latest, input.readLong())
                input.readLong() // total_ms
                input.readNullableInt()
                input.readNullableInt()
                input.readNullableLong()
                input.readNullableInt()
                input.readNullableInt()
                input.readNullableLong()
                input.readNullableLong()
                input.readNullableLong()
                input.readNullableInt()
                input.readInt() // detailed
                input.readNullableBytes()
            }
            latest
        }
    }

    private fun writeDays(db: SQLiteDatabase, output: DataOutputStream) {
        db.query("days", null, null, null, null, null, "day").use { cursor ->
            output.writeInt(cursor.count)
            val day = cursor.getColumnIndexOrThrow("day")
            val rangeEnd = cursor.getColumnIndexOrThrow("range_end_ms")
            val total = cursor.getColumnIndexOrThrow("total_ms")
            val unlocks = cursor.getColumnIndexOrThrow("unlocks")
            val wakeups = cursor.getColumnIndexOrThrow("wakeups")
            val longestBreak = cursor.getColumnIndexOrThrow("longest_break_ms")
            val sessions = cursor.getColumnIndexOrThrow("session_count")
            val quickChecks = cursor.getColumnIndexOrThrow("quick_check_count")
            val longestSession = cursor.getColumnIndexOrThrow("longest_session_ms")
            val firstUse = cursor.getColumnIndexOrThrow("first_use_ms")
            val lastUse = cursor.getColumnIndexOrThrow("last_use_ms")
            val switches = cursor.getColumnIndexOrThrow("switch_count")
            val detailed = cursor.getColumnIndexOrThrow("detailed")
            val events = cursor.getColumnIndexOrThrow("events")
            while (cursor.moveToNext()) {
                output.writeLong(cursor.getLong(day))
                output.writeLong(cursor.getLong(rangeEnd))
                output.writeLong(cursor.getLong(total))
                output.writeNullableInt(cursor.takeUnless { it.isNull(unlocks) }?.getInt(unlocks))
                output.writeNullableInt(cursor.takeUnless { it.isNull(wakeups) }?.getInt(wakeups))
                output.writeNullableLong(cursor.takeUnless { it.isNull(longestBreak) }?.getLong(longestBreak))
                output.writeNullableInt(cursor.takeUnless { it.isNull(sessions) }?.getInt(sessions))
                output.writeNullableInt(cursor.takeUnless { it.isNull(quickChecks) }?.getInt(quickChecks))
                output.writeNullableLong(cursor.takeUnless { it.isNull(longestSession) }?.getLong(longestSession))
                output.writeNullableLong(cursor.takeUnless { it.isNull(firstUse) }?.getLong(firstUse))
                output.writeNullableLong(cursor.takeUnless { it.isNull(lastUse) }?.getLong(lastUse))
                output.writeNullableInt(cursor.takeUnless { it.isNull(switches) }?.getInt(switches))
                output.writeInt(cursor.getInt(detailed))
                output.writeNullableBytes(cursor.takeUnless { it.isNull(events) }?.getBlob(events))
            }
        }
    }

    private fun writeAppDays(db: SQLiteDatabase, output: DataOutputStream) {
        db.query("app_days", null, null, null, null, null, "day, package_name").use { cursor ->
            output.writeInt(cursor.count)
            while (cursor.moveToNext()) {
                output.writeLong(cursor.getLong(cursor.getColumnIndexOrThrow("day")))
                output.writeUTF(cursor.getString(cursor.getColumnIndexOrThrow("package_name")))
                output.writeUTF(cursor.getString(cursor.getColumnIndexOrThrow("label")))
                output.writeLong(cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")))
                val opens = cursor.getColumnIndexOrThrow("opens")
                output.writeNullableInt(cursor.takeUnless { it.isNull(opens) }?.getInt(opens))
            }
        }
    }

    private fun writeMetadata(db: SQLiteDatabase, output: DataOutputStream) {
        db.query("metadata", null, null, null, null, null, "key").use { cursor ->
            output.writeInt(cursor.count)
            while (cursor.moveToNext()) {
                output.writeUTF(cursor.getString(cursor.getColumnIndexOrThrow("key")))
                output.writeUTF(cursor.getString(cursor.getColumnIndexOrThrow("value")))
            }
        }
    }

    private fun writeAppSwitches(db: SQLiteDatabase, output: DataOutputStream) {
        db.query("app_switches", null, null, null, null, null, "day, first_package, second_package").use { cursor ->
            output.writeInt(cursor.count)
            while (cursor.moveToNext()) {
                output.writeLong(cursor.getLong(cursor.getColumnIndexOrThrow("day")))
                output.writeUTF(cursor.getString(cursor.getColumnIndexOrThrow("first_package")))
                output.writeUTF(cursor.getString(cursor.getColumnIndexOrThrow("second_package")))
                output.writeInt(cursor.getInt(cursor.getColumnIndexOrThrow("switch_count")))
            }
        }
    }

    private fun writeHourlyUsage(db: SQLiteDatabase, output: DataOutputStream) {
        db.query("hourly_usage", null, null, null, null, null, "day, local_hour").use { cursor ->
            output.writeInt(cursor.count)
            while (cursor.moveToNext()) {
                output.writeLong(cursor.getLong(cursor.getColumnIndexOrThrow("day")))
                output.writeInt(cursor.getInt(cursor.getColumnIndexOrThrow("local_hour")))
                output.writeLong(cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")))
            }
        }
    }

    private fun read(source: File, db: SQLiteDatabase?) {
        DataInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(source)))).use { input ->
            require(input.readUTF() == MAGIC) { "Not a Still archive backup" }
            require(input.readInt() == VERSION) { "Unsupported Still archive backup" }
            repeat(input.readInt()) {
                val values = ContentValues().apply {
                    put("day", input.readLong())
                    put("range_end_ms", input.readLong())
                    put("total_ms", input.readLong())
                    putNullable("unlocks", input.readNullableInt())
                    putNullable("wakeups", input.readNullableInt())
                    putNullable("longest_break_ms", input.readNullableLong())
                    putNullable("session_count", input.readNullableInt())
                    putNullable("quick_check_count", input.readNullableInt())
                    putNullable("longest_session_ms", input.readNullableLong())
                    putNullable("first_use_ms", input.readNullableLong())
                    putNullable("last_use_ms", input.readNullableLong())
                    putNullable("switch_count", input.readNullableInt())
                    put("detailed", input.readInt())
                    putNullable("events", input.readNullableBytes())
                }
                db?.insertOrThrow("days", null, values)
            }
            repeat(input.readInt()) {
                val values = ContentValues().apply {
                    put("day", input.readLong())
                    put("package_name", input.readUTF())
                    put("label", input.readUTF())
                    put("duration_ms", input.readLong())
                    putNullable("opens", input.readNullableInt())
                }
                db?.insertOrThrow("app_days", null, values)
            }
            repeat(input.readInt()) {
                val values = ContentValues().apply {
                    put("key", input.readUTF())
                    put("value", input.readUTF())
                }
                db?.insertOrThrow("metadata", null, values)
            }
            repeat(input.readInt()) {
                val values = ContentValues().apply {
                    put("day", input.readLong())
                    put("first_package", input.readUTF())
                    put("second_package", input.readUTF())
                    put("switch_count", input.readInt())
                }
                db?.insertOrThrow("app_switches", null, values)
            }
            repeat(input.readInt()) {
                val values = ContentValues().apply {
                    put("day", input.readLong())
                    put("local_hour", input.readInt())
                    put("duration_ms", input.readLong())
                }
                db?.insertOrThrow("hourly_usage", null, values)
            }
        }
    }

    private fun DataOutputStream.writeNullableInt(value: Int?) {
        writeBoolean(value != null)
        value?.let(::writeInt)
    }

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        value?.let(::writeLong)
    }

    private fun DataOutputStream.writeNullableBytes(value: ByteArray?) {
        writeInt(value?.size ?: -1)
        value?.let(::write)
    }

    private fun DataInputStream.readNullableInt(): Int? = if (readBoolean()) readInt() else null
    private fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong() else null
    private fun DataInputStream.readNullableBytes(): ByteArray? {
        val size = readInt()
        if (size < 0) return null
        require(size <= MAX_BLOB_BYTES) { "Invalid backup blob length" }
        return ByteArray(size).also(::readFully)
    }

    private fun ContentValues.putNullable(key: String, value: Int?) = if (value == null) putNull(key) else put(key, value)
    private fun ContentValues.putNullable(key: String, value: Long?) = if (value == null) putNull(key) else put(key, value)
    private fun ContentValues.putNullable(key: String, value: ByteArray?) = if (value == null) putNull(key) else put(key, value)

    private const val MAX_BLOB_BYTES = 64 * 1_024 * 1_024
}
