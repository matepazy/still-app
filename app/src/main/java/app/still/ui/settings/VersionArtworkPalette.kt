package app.still.ui.settings

import java.security.MessageDigest

internal data class ArtworkHsv(val hue: Float, val saturation: Float, val value: Float)

internal data class VersionArtworkPalette(
    val background: ArtworkHsv,
    val colors: List<ArtworkHsv>,
    val orbitPhase: Float,
)

/** Uses the complete version, including prerelease/build suffixes, without a small hue bucket. */
internal fun versionArtworkPalette(version: String): VersionArtworkPalette {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(version.removePrefix("v").toByteArray(Charsets.UTF_8))
    fun fraction(offset: Int): Float {
        val bits = ((digest[offset].toInt() and 0xff) shl 16) or
            ((digest[offset + 1].toInt() and 0xff) shl 8) or
            (digest[offset + 2].toInt() and 0xff)
        return bits / 16777216f
    }

    val hue = fraction(0) * 360f
    // Keep a harmonious palette while varying each stop independently per release.
    return VersionArtworkPalette(
        background = ArtworkHsv(hue, .62f, .16f),
        colors = listOf(
            ArtworkHsv(hue, .62f + fraction(3) * .20f, .58f + fraction(6) * .18f),
            ArtworkHsv(
                (hue + 40f + fraction(9) * 90f) % 360f,
                .54f + fraction(12) * .22f,
                .68f + fraction(15) * .18f,
            ),
            ArtworkHsv(
                (hue + 230f + fraction(18) * 90f) % 360f,
                .64f + fraction(21) * .20f,
                .50f + fraction(24) * .18f,
            ),
        ),
        orbitPhase = fraction(27) * (2 * Math.PI).toFloat(),
    )
}
