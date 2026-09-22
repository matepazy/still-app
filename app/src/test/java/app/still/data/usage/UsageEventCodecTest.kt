package app.still.data.usage

import app.still.domain.model.UsageEventRecord
import app.still.domain.model.UsageEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.GZIPOutputStream

class UsageEventCodecTest {
    @Test
    fun compressedArchiveRoundTripsEvents() {
        val start = Instant.parse("2026-09-12T08:00:00Z")
        val events = buildList {
            repeat(200) { index ->
                add(
                    UsageEventRecord(
                        timestamp = start.plusSeconds(index * 30L),
                        type = if (index % 2 == 0) UsageEventType.ActivityResumed else UsageEventType.ActivityPaused,
                        packageName = if (index % 4 < 2) "com.example.reader" else "com.example.chat",
                    ),
                )
            }
            add(UsageEventRecord(start.plusSeconds(6_100), UsageEventType.ScreenNonInteractive))
        }

        val encoded = UsageEventCodec.encode(events)

        assertEquals(events, UsageEventCodec.decode(encoded))
        assertTrue("archive should be compact", encoded.size < 1_500)
    }

    @Test
    fun emptyArchiveRoundTrips() {
        assertEquals(emptyList<UsageEventRecord>(), UsageEventCodec.decode(UsageEventCodec.encode(emptyList())))
    }

    @Test
    fun compactArchiveUsesSecondPrecisionAndDropsRedundantTransitions() {
        val start = Instant.parse("2026-09-12T08:00:00Z")
        val events = listOf(
            UsageEventRecord(start.plusMillis(125), UsageEventType.ActivityResumed, "app.one"),
            UsageEventRecord(start.plusMillis(400), UsageEventType.ActivityResumed, "app.one"),
            UsageEventRecord(start.plusSeconds(8).plusMillis(999), UsageEventType.ActivityPaused, "app.one"),
        )

        assertEquals(
            listOf(
                UsageEventRecord(start, UsageEventType.ActivityResumed, "app.one"),
                UsageEventRecord(start.plusSeconds(8), UsageEventType.ActivityPaused, "app.one"),
            ),
            UsageEventCodec.decode(UsageEventCodec.encode(events)),
        )
    }

    @Test
    fun legacyVersionOneArchiveStillDecodes() {
        val event = UsageEventRecord(
            Instant.parse("2026-09-12T08:00:00.123Z"),
            UsageEventType.ActivityResumed,
            "app.one",
        )
        val bytes = ByteArrayOutputStream().also { outputBytes ->
            GZIPOutputStream(outputBytes).use { gzip ->
                DataOutputStream(gzip).use { output ->
                    output.writeByte(1)
                    output.writeInt(1)
                    output.writeUTF("app.one")
                    output.writeInt(1)
                    output.writeLong(event.timestamp.toEpochMilli())
                    output.writeByte(event.type.ordinal)
                    output.writeInt(1)
                }
            }
        }.toByteArray()

        assertEquals(listOf(event), UsageEventCodec.decode(bytes))
    }
}
