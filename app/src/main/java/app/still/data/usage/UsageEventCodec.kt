package app.still.data.usage

import app.still.domain.model.UsageEventRecord
import app.still.domain.model.UsageEventType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Compact, versioned storage for a day's raw UsageEvents. */
object UsageEventCodec {
    private const val VERSION = 1

    fun encode(events: List<UsageEventRecord>): ByteArray {
        val ordered = events.sortedBy { it.timestamp }
        val packages = ordered.mapNotNull { it.packageName }.distinct()
        val packageIndexes = packages.withIndex().associate { (index, value) -> value to index }
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { gzip ->
            DataOutputStream(gzip).use { output ->
                output.writeByte(VERSION)
                output.writeInt(packages.size)
                packages.forEach(output::writeUTF)
                output.writeInt(ordered.size)
                var previousMillis = 0L
                ordered.forEachIndexed { index, event ->
                    val millis = event.timestamp.toEpochMilli()
                    output.writeLong(if (index == 0) millis else millis - previousMillis)
                    output.writeByte(event.type.ordinal)
                    output.writeInt(event.packageName?.let(packageIndexes::get)?.plus(1) ?: 0)
                    previousMillis = millis
                }
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<UsageEventRecord> =
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { input ->
            require(input.readUnsignedByte() == VERSION) { "Unsupported usage archive version" }
            val packages = List(input.readInt()) { input.readUTF() }
            val count = input.readInt()
            var timestamp = 0L
            List(count) { index ->
                val storedTime = input.readLong()
                timestamp = if (index == 0) storedTime else timestamp + storedTime
                val type = UsageEventType.entries[input.readUnsignedByte()]
                val packageIndex = input.readInt() - 1
                UsageEventRecord(
                    timestamp = Instant.ofEpochMilli(timestamp),
                    type = type,
                    packageName = packages.getOrNull(packageIndex),
                )
            }
        }
}
