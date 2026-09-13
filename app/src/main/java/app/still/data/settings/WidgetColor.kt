package app.still.data.settings

private val sixDigitHex = Regex("^[0-9A-Fa-f]{6}$")

fun normalizeWidgetColor(value: String?): String? {
    val digits = value?.trim()?.removePrefix("#") ?: return null
    return if (sixDigitHex.matches(digits)) "#${digits.uppercase()}" else null
}

fun parseWidgetColor(value: String?): Int? {
    val normalized = normalizeWidgetColor(value) ?: return null
    return (0xFF000000L or normalized.drop(1).toLong(16)).toInt()
}

data class WidgetContrastColors(
    val background: Int,
    val foreground: Int,
)

fun widgetContrastColors(background: Int): WidgetContrastColors {
    val opaqueBackground = background or 0xFF000000.toInt()
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()
    val foreground = if (contrastRatio(opaqueBackground, black) >= contrastRatio(opaqueBackground, white)) black else white
    return WidgetContrastColors(opaqueBackground, foreground)
}

internal fun contrastRatio(first: Int, second: Int): Double {
    val lighter = maxOf(relativeLuminance(first), relativeLuminance(second))
    val darker = minOf(relativeLuminance(first), relativeLuminance(second))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(color: Int): Double {
    fun channel(shift: Int): Double {
        val value = ((color shr shift) and 0xFF) / 255.0
        return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
