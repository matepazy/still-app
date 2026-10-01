package app.still.data.usage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.DataInputStream
import java.io.DataOutputStream

/** Logical snapshot of every application table; no SQL from a file is executed. */
internal object DatabaseSnapshot {
    fun tables(compact: Boolean) = if (compact) {
        listOf("days", "apps", "app_days", "metadata", "archive_control")
    } else {
        listOf("days", "app_days", "app_switches", "hourly_usage", "metadata", "archive_control")
    }

    fun write(db: SQLiteDatabase, compact: Boolean, output: DataOutputStream) {
        output.writeUTF("STILL_DATABASE")
        output.writeInt(1)
        output.writeBoolean(compact)
        tables(compact).forEach { table ->
            output.writeUTF(table)
            db.query(table, null, null, null, null, null, null).use { cursor ->
                output.writeInt(cursor.columnCount)
                cursor.columnNames.forEach(output::writeUTF)
                output.writeInt(cursor.count)
                while (cursor.moveToNext()) {
                    repeat(cursor.columnCount) { column ->
                        val type = cursor.getType(column)
                        output.writeByte(type)
                        when (type) {
                            Cursor.FIELD_TYPE_NULL -> Unit
                            Cursor.FIELD_TYPE_INTEGER -> output.writeLong(cursor.getLong(column))
                            Cursor.FIELD_TYPE_FLOAT -> output.writeDouble(cursor.getDouble(column))
                            Cursor.FIELD_TYPE_STRING -> writeBytes(output, cursor.getString(column).toByteArray(Charsets.UTF_8))
                            Cursor.FIELD_TYPE_BLOB -> writeBytes(output, cursor.getBlob(column))
                        }
                    }
                }
            }
        }
    }

    fun readHeader(input: DataInputStream): Boolean {
        require(input.readUTF() == "STILL_DATABASE" && input.readInt() == 1) { "Unsupported database export" }
        return input.readBoolean()
    }

    fun readTables(db: SQLiteDatabase, compact: Boolean, input: DataInputStream) {
        tables(compact).forEach { table ->
            require(input.readUTF() == table) { "Invalid database tables" }
            val expected = db.query(table, null, null, null, null, null, null).use { it.columnNames }
            require(input.readInt() == expected.size) { "Invalid database columns" }
            val columns = List(expected.size) { input.readUTF() }
            require(columns.toSet() == expected.toSet() && columns.distinct().size == expected.size) { "Invalid database columns" }
            val rows = input.readInt()
            require(rows in 0..5_000_000) { "Invalid database row count" }
            repeat(rows) {
                val values = ContentValues()
                columns.forEach { column ->
                    when (input.readUnsignedByte()) {
                        Cursor.FIELD_TYPE_NULL -> values.putNull(column)
                        Cursor.FIELD_TYPE_INTEGER -> values.put(column, input.readLong())
                        Cursor.FIELD_TYPE_FLOAT -> values.put(column, input.readDouble())
                        Cursor.FIELD_TYPE_STRING -> values.put(column, readBytes(input).toString(Charsets.UTF_8))
                        Cursor.FIELD_TYPE_BLOB -> values.put(column, readBytes(input))
                        else -> error("Invalid database value")
                    }
                }
                db.insertOrThrow(table, null, values)
            }
        }
        db.rawQuery("PRAGMA foreign_key_check", null).use { require(!it.moveToFirst()) { "Invalid database relationships" } }
    }

    private fun writeBytes(output: DataOutputStream, bytes: ByteArray) {
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readBytes(input: DataInputStream): ByteArray {
        val size = input.readInt()
        require(size in 0..16 * 1024 * 1024 && size <= input.available()) { "Invalid database value size" }
        return ByteArray(size).also(input::readFully)
    }
}
