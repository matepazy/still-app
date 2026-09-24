package app.still.data.comparison

import app.still.data.settings.AppCategory
import app.still.domain.compare.CompareEngine
import app.still.domain.compare.CompareSnapshotBuilder
import app.still.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.common.HybridBinarizer

class ComparePayloadCodecTest {
    private val date = LocalDate.of(2026, 9, 20)
    private val range = StatisticsRange(date, date)
    private val day = StatisticsDay(date, Duration.ofMinutes(120), 4, 1, 2, 3, Duration.ofMinutes(40),
        Duration.ofMinutes(20), 480, 1200, List(24) { 0 }, 2,
        listOf(AppUsage(AppInfo("secret.package.name", "Friendly app"), Duration.ofMinutes(120), 1)))
    private fun snapshot(sharing: CompareSharing = CompareSharing(), cutoff: Instant? = null, replyTo: String? = null) =
        CompareSnapshotBuilder.build(range, listOf(day), sharing, { AppCategory.Social },
            "0123456789abcdef0123456789abcdef", cutoff, replyTo)

    @Test fun snapshotRoundTripAndNoRawIdentityByDefault() {
        val model = snapshot()
        val encoded = ComparePayloadCodec.encode(model)
        assertEquals(model, ComparePayloadCodec.decode(encoded).getOrThrow())
        assertNull(model.apps)
        assertFalse(encoded.contains("secret.package.name"))
        assertFalse(encoded.contains("UsageEventRecord"))
        assertEquals(120 * 60_000L, model.totalScreenTimeMillis)
        assertEquals(1, model.screenTimeDays)
    }

    @Test fun individualAppsAreOptInAndStillOmitPackageNames() {
        val model = snapshot(CompareSharing(apps = true))
        assertEquals("Friendly app", model.apps!!.first().label)
        assertEquals(AppCategory.Social.displayName, model.apps!!.first().category)
        assertEquals(model, ComparePayloadCodec.decode(ComparePayloadCodec.encode(model)).getOrThrow())
        assertEquals(ComparePayloadCodec.fingerprint(model.copy(apps = model.apps!!.map { it.copy(category = null) })),
            ComparePayloadCodec.fingerprint(model))
        assertFalse(ComparePayloadCodec.encode(model).contains("secret.package.name"))
        val unnamed = day.copy(apps = listOf(AppUsage(AppInfo("secret.package.name", "secret.package.name"), Duration.ofMinutes(5), 1)))
        val sanitized = CompareSnapshotBuilder.build(range, listOf(unnamed), CompareSharing(apps = true), { AppCategory.Social },
            "0123456789abcdef0123456789abcdef")
        assertEquals("Unknown app", sanitized.apps!!.first().label)
    }

    @Test fun rejectsVersionMalformedAndOversizedCodes() {
        assertTrue(ComparePayloadCodec.decode("random").isFailure)
        assertTrue(ComparePayloadCodec.decode("STILL-CMP:1:not-base64").isFailure)
        assertTrue(ComparePayloadCodec.decode("STILL-CMP:1:" + "a".repeat(4000)).isFailure)
        assertThrows(IllegalArgumentException::class.java) { ComparePayloadCodec.encode(snapshot().copy(version = 2)) }
    }

    @Test fun sessionAndReplyHashMustMatch() {
        val first = snapshot()
        val hash = ComparePayloadCodec.fingerprint(first)
        val reply = snapshot(replyTo = hash)
        assertEquals(first, CompareEngine.result(first, reply, hash).you)
        assertThrows(IllegalArgumentException::class.java) { CompareEngine.result(first, reply.copy(sessionId = "ffffffffffffffffffffffffffffffff"), hash) }
        assertThrows(IllegalArgumentException::class.java) { CompareEngine.result(first, reply.copy(replyTo = "000000000000000000000000"), hash) }
    }

    @Test fun cutoffAndHistoricalPeriod() {
        val cutoff = Instant.now()
        val today = LocalDate.now()
        val liveRange = StatisticsRange(today, today)
        val first = CompareSnapshotBuilder.build(liveRange, listOf(day.copy(date = today)), CompareSharing(), { AppCategory.Social },
            "0123456789abcdef0123456789abcdef", cutoff)
        val reply = first.copy(replyTo = ComparePayloadCodec.fingerprint(first))
        assertEquals(cutoff.toEpochMilli(), CompareEngine.result(first, reply, ComparePayloadCodec.fingerprint(first)).you.cutoffEpochMillis)
        assertThrows(IllegalArgumentException::class.java) { CompareEngine.result(first, reply.copy(cutoffEpochMillis = cutoff.plusSeconds(1).toEpochMilli()), null) }
        assertNull(snapshot().cutoffEpochMillis)
    }

    @Test fun disabledSharingDoesNotPopulateFields() {
        val model = snapshot(CompareSharing(screenTime = true, patterns = false, categories = false, apps = false))
        assertNull(model.checkIns)
        assertNull(model.categories)
        assertNull(model.apps)
        assertNotNull(model.totalScreenTimeMillis)
    }

    @Test fun twoQrExchangeProducesMatchingSnapshotsOnBothSides() {
        val first = snapshot()
        val receivedFirst = ComparePayloadCodec.decode(ComparePayloadCodec.encode(first)).getOrThrow()
        val second = CompareSnapshotBuilder.build(range, listOf(day.copy(screenTime = Duration.ofMinutes(150))),
            CompareSharing(), { AppCategory.Social }, receivedFirst.sessionId, null,
            ComparePayloadCodec.fingerprint(receivedFirst))
        val receivedReply = ComparePayloadCodec.decode(ComparePayloadCodec.encode(second)).getOrThrow()
        val secondPhone = CompareEngine.result(second, receivedFirst, null)
        val firstPhone = CompareEngine.result(first, receivedReply, ComparePayloadCodec.fingerprint(first))
        assertEquals(secondPhone.you, firstPhone.friend)
        assertEquals(secondPhone.friend, firstPhone.you)
    }

    @Test fun replyIncludesFirstCodesAppEvenWhenItIsOutsideRespondersTopEight() {
        val first = snapshot(CompareSharing(screenTime = false, patterns = false, categories = false, apps = true))
        val otherApps = (1..8).map { index ->
            AppUsage(AppInfo("other.package.$index", "Other $index"), Duration.ofMinutes(20), 1)
        } + AppUsage(AppInfo("secret.package.name", "Friendly app"), Duration.ofMinutes(5), 1)
        val reply = CompareSnapshotBuilder.build(range, listOf(day.copy(apps = otherApps)), first.sharing,
            { AppCategory.Social }, first.sessionId, null, ComparePayloadCodec.fingerprint(first),
            first.apps!!.map { it.label })
        assertEquals(first.sharingFlags, reply.sharingFlags)
        assertEquals(listOf(CompareApp("Friendly app", 5 * 60_000L, AppCategory.Social.displayName)), reply.apps)
        assertEquals(reply, ComparePayloadCodec.decode(ComparePayloadCodec.encode(reply)).getOrThrow())
    }

    @Test fun payloadFitsAndDecodesAsQr() {
        val code = ComparePayloadCodec.encode(snapshot())
        val matrix = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 800, 800)
        val source = object : LuminanceSource(matrix.width, matrix.height) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray = ByteArray(matrix.width) { x -> if (matrix[x, y]) 0 else 0xff.toByte() }
            override fun getMatrix(): ByteArray = ByteArray(matrix.width * matrix.height) { index ->
                if (matrix[index % matrix.width, index / matrix.width]) 0 else 0xff.toByte()
            }
        }
        val scanned = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        assertEquals(snapshot(), ComparePayloadCodec.decode(scanned).getOrThrow())
    }
}
