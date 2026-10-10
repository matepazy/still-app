package app.still.data.themes

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Offline package transfer. Bound both the QR and inflation before parsing untrusted input. */
object ThemeQrCodec {
    private const val PREFIX = "STILL-THEME:1:"
    private const val MAX_COMPRESSED = 1800
    private const val MAX_PACKAGE = 64 * 1024
    private const val MAX_CODE = 2413

    fun encode(bytes: ByteArray): String {
        require(bytes.size <= MAX_PACKAGE) { "This theme is too large for a QR code." }
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(bytes) }
        }.toByteArray()
        require(compressed.size <= MAX_COMPRESSED) { "This theme is too large for a QR code." }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    fun decode(code: String): Result<ByteArray> = runCatching {
        require(code.startsWith(PREFIX) && code.length <= MAX_CODE) { "Not a Still theme code." }
        val compressed = Base64.getUrlDecoder().decode(code.removePrefix(PREFIX))
        require(compressed.size <= MAX_COMPRESSED) { "Theme code is too large." }
        GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_PACKAGE) { "Theme code is too large." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    fun load(code: String): Result<ThemePackage> = decode(code).mapCatching(ThemePackages::load)
}
