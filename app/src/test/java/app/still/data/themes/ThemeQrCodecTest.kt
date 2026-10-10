package app.still.data.themes

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class ThemeQrCodecTest {
    @Test fun creatorThemeSurvivesTransferAndFitsQr() {
        val source = CustomThemeBuilder.buildFixedSource("Berry", CustomThemeBuilder.palette("#285B8A", "#101010"))
        val bytes = source.toByteArray()
        val code = ThemeQrCodec.encode(bytes)
        assertArrayEquals(bytes, ThemeQrCodec.decode(code).getOrThrow())
        assertEquals("Berry", ThemeQrCodec.load(code).getOrThrow().theme.title)
        QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 900, 900,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
    }

    @Test fun rejectsWrongTypeCorruptionAndUnsupportedVersion() {
        listOf("STILL-CMP:1:abc", "STILL-THEME:2:abc", "STILL-THEME:1:%%%",
            "STILL-THEME:1:" + "A".repeat(2500)).forEach {
            assertTrue(ThemeQrCodec.decode(it).isFailure)
        }
        assertTrue(ThemeQrCodec.load(ThemeQrCodec.encode("not a theme".toByteArray())).isFailure)
    }

    @Test fun rejectsInflationBombAndOversizedOutgoingPackage() {
        val oversized = ByteArray(65537)
        assertTrue(runCatching { ThemeQrCodec.encode(oversized) }.isFailure)
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(oversized) }
        }.toByteArray()
        val code = "STILL-THEME:1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
        assertTrue(ThemeQrCodec.decode(code).isFailure)
    }

    @Test fun rejectsPackageThatCannotFitSingleQr() {
        val bytes = ByteArray(5000).also { kotlin.random.Random(42).nextBytes(it) }
        assertTrue(runCatching { ThemeQrCodec.encode(bytes) }.isFailure)
    }
}
