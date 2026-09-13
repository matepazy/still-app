package app.still.data.usage

import app.still.domain.model.UsageEventRecord
import app.still.domain.model.UsageEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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
}
