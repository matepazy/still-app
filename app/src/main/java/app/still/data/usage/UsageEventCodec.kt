package app.still.data.usage

import app.still.domain.model.UsageEventRecord
import app.still.domain.model.UsageEventType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Versioned storage for a day's rebuildable UsageEvents.
 *
 * Version 2 keeps only meaningful state transitions, rounds timestamps to one
 * second, and uses unsigned variable-length integers before gzip compression.
 * Version 1 remains readable so interrupted and restored legacy archives work.
 */
object UsageEventCodec {
    private const val LEGACY_VERSION = 1
    private const val VERSION = 2

    fun encode(events: List<UsageEventRecord>): ByteArray {
        val canonical = canonicalize(events)
        val packages = canonical.mapNotNull { it.packageName }.distinct()
        val packageIndexes = packages.withIndex().associate { (index, value) -> value to index + 1 }
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { gzip ->
            DataOutputStream(gzip).use { output ->
                output.writeByte(VERSION)
                output.writeVarUInt(packages.size.toLong())
                packages.forEach { output.writeCompactString(it) }
                output.writeVarUInt(canonical.size.toLong())
                var previousSecond = 0L
                canonical.forEachIndexed { index, event ->
                    val second = event.timestamp.epochSecond
                    output.writeVarUInt(if (index == 0) second else second - previousSecond)
                    output.writeByte(event.type.ordinal)
                    output.writeVarUInt(event.packageName?.let(packageIndexes::get)?.toLong() ?: 0L)
                    previousSecond = second
                }
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<UsageEventRecord> =
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { input ->
            when (val version = input.readUnsignedByte()) {
                LEGACY_VERSION -> input.decodeLegacy()
                VERSION -> input.decodeCompact()
                else -> error("Unsupported usage archive version $version")
            }
        }

    internal fun canonicalize(events: List<UsageEventRecord>): List<UsageEventRecord> {
        val result = mutableListOf<UsageEventRecord>()
        var activePackage: String? = null
        events.asSequence()
            .sortedBy { it.timestamp }
            .map { it.copy(timestamp = Instant.ofEpochSecond(it.timestamp.epochSecond)) }
            .forEach { event ->
                if (result.lastOrNull() == event) return@forEach
                when (event.type) {
                    UsageEventType.ActivityResumed -> {
                        val packageName = event.packageName ?: return@forEach
                        if (activePackage == packageName) return@forEach
                        activePackage = packageName
                        result += event
                    }
                    UsageEventType.ActivityPaused -> {
                        val packageName = event.packageName ?: return@forEach
                        if (activePackage != null && activePackage != packageName) return@forEach
                        activePackage = null
                        result += event
                    }
                    UsageEventType.ScreenNonInteractive -> {
                        activePackage = null
                        result += event
                    }
                    else -> result += event
                }
            }
        return result
    }

    private fun DataInputStream.decodeLegacy(): List<UsageEventRecord> {
        val packages = List(readInt()) { readUTF() }
        val count = readInt()
        var timestamp = 0L
        return List(count) { index ->
            val storedTime = readLong()
            timestamp = if (index == 0) storedTime else timestamp + storedTime
            val type = UsageEventType.entries[readUnsignedByte()]
            val packageIndex = readInt() - 1
            UsageEventRecord(
                timestamp = Instant.ofEpochMilli(timestamp),
                type = type,
                packageName = packages.getOrNull(packageIndex),
            )
        }
    }

    private fun DataInputStream.decodeCompact(): List<UsageEventRecord> {
        val packages = List(readVarUInt().toInt()) { readCompactString() }
        val count = readVarUInt().toInt()
        var second = 0L
        return List(count) { index ->
            val storedTime = readVarUInt()
            second = if (index == 0) storedTime else second + storedTime
            val type = UsageEventType.entries[readUnsignedByte()]
            val packageIndex = readVarUInt().toInt() - 1
            UsageEventRecord(
                timestamp = Instant.ofEpochSecond(second),
                type = type,
                packageName = packages.getOrNull(packageIndex),
            )
        }
    }

    private fun DataOutputStream.writeCompactString(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        writeVarUInt(encoded.size.toLong())
        write(encoded)
    }

    private fun DataInputStream.readCompactString(): String {
        val size = readVarUInt().toInt()
        require(size in 0..MAX_STRING_BYTES) { "Invalid archive string length" }
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeVarUInt(value: Long) {
        require(value >= 0L) { "Archive integers must be unsigned" }
        var remaining = value
        while (remaining >= 0x80L) {
            writeByte(((remaining and 0x7fL) or 0x80L).toInt())
            remaining = remaining ushr 7
        }
        writeByte(remaining.toInt())
    }

    private fun DataInputStream.readVarUInt(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            val next = read()
            if (next < 0) throw EOFException("Unexpected end of usage archive")
            result = result or ((next and 0x7f).toLong() shl shift)
            if (next and 0x80 == 0) return result
            shift += 7
        }
        error("Invalid variable-length integer")
    }

    private const val MAX_STRING_BYTES = 16 * 1_024
}
