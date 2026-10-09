package app.still.data.themes

data class ColorSwatch(val name: String, val hex: String)

data class BackgroundTone(val id: String, val name: String, val backgroundHex: String, val surfaceHex: String)

object CustomThemeBuilder {
    val roles = listOf("primary", "on-primary", "background", "on-background", "surface", "on-surface")

    val swatches = listOf(
        ColorSwatch("Emerald", "#27683C"),
        ColorSwatch("Mint", "#006A68"),
        ColorSwatch("Ocean", "#285B8A"),
        ColorSwatch("Sky", "#1A73E8"),
        ColorSwatch("Indigo", "#3F51B5"),
        ColorSwatch("Midnight", "#274263"),
        ColorSwatch("Lavender", "#64558F"),
        ColorSwatch("Plum", "#8A399F"),
        ColorSwatch("Rose", "#8D4D64"),
        ColorSwatch("Crimson", "#B3261E"),
        ColorSwatch("Peach", "#9A533A"),
        ColorSwatch("Amber", "#775900"),
        ColorSwatch("Terracotta", "#98471F"),
        ColorSwatch("Sand", "#795A38"),
        ColorSwatch("Sage", "#41664D"),
        ColorSwatch("Slate", "#455A64"),
    )

    val lightTones = listOf(
        BackgroundTone("white", "White", "#FFFFFF", "#F7F9F6"),
        BackgroundTone("warm", "Warm", "#FFFDF8", "#F6EDDF"),
        BackgroundTone("tint", "Mist", "#F4F8F5", "#EAF1EB"),
        BackgroundTone("neutral", "Neutral", "#F5F5F5", "#EBEBEB"),
    )

    val darkTones = listOf(
        BackgroundTone("charcoal", "Charcoal", "#121212", "#1E1E1E"),
        BackgroundTone("black", "Black", "#000000", "#141414"),
        BackgroundTone("tint", "Forest", "#111713", "#18201B"),
        BackgroundTone("slate", "Slate", "#151B22", "#21262D"),
    )

    val backgroundTones = lightTones + darkTones

    private val hexRegex = Regex("^#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?$")

    fun isValidHex(hex: String): Boolean = hexRegex.matches(hex.trim())

    fun normalizeHex(hex: String): String {
        val trimmed = hex.trim()
        val withHash = if (trimmed.startsWith('#')) trimmed else "#$trimmed"
        return if (isValidHex(withHash)) withHash.uppercase() else "#27683C"
    }

    fun luminance(hex: String): Float {
        val clean = hex.removePrefix("#")
        if (clean.length < 6) return 0f
        val r = clean.substring(0, 2).toIntOrNull(16) ?: 0
        val g = clean.substring(2, 4).toIntOrNull(16) ?: 0
        val b = clean.substring(4, 6).toIntOrNull(16) ?: 0
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= .04045) value / 12.92 else Math.pow((value + .055) / 1.055, 2.4)
        }
        return (0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)).toFloat()
    }

    fun contrastingInk(hex: String, lightInk: String = "#FFFFFF", darkInk: String = "#172019"): String =
        if (contrastRatio(hex, darkInk) >= contrastRatio(hex, lightInk)) darkInk else lightInk

    fun contrastRatio(first: String, second: String): Float {
        val a = luminance(first); val b = luminance(second)
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
    }

    private fun mixHex(first: String, second: String, fraction: Float): String {
        val a = normalizeHex(first).removePrefix("#").take(6).toInt(16)
        val b = normalizeHex(second).removePrefix("#").take(6).toInt(16)
        val channels = listOf(16, 8, 0).map { shift ->
            val from = (a shr shift) and 255; val to = (b shr shift) and 255
            (from + (to - from) * fraction).toInt().coerceIn(0, 255)
        }
        return "#%02X%02X%02X".format(channels[0], channels[1], channels[2])
    }

    /** Presets must work as small action text and chart marks, as well as button fills. */
    private fun readableAccent(accent: String, background: String, surface: String, ink: String): String {
        // Reserve contrast for the elevated tonal containers too.
        val surfaces = listOf(background, surface, mixHex(surface, ink, .2f))
        val toward = if (luminance(background) < .179f) "#FFFFFF" else "#000000"
        return (0..100).asSequence().map { mixHex(accent, toward, it / 100f) }
            .first { candidate -> surfaces.all { contrastRatio(candidate, it) >= 4.5f } }
    }

    fun palette(accentHex: String, tone: BackgroundTone): Map<String, String> =
        defaultPalette(accentHex, tone, luminance(tone.backgroundHex) < .5f)

    /** Preserve the two selected colors; derive surfaces and readable text from them. */
    fun palette(accentHex: String, backgroundHex: String): Map<String, String> {
        require(isValidHex(accentHex) && isValidHex(backgroundHex)) { "Enter valid theme colors" }
        val accent = normalizeHex(accentHex)
        val background = normalizeHex(backgroundHex)
        val surface = mixHex(background, contrastingInk(background, darkInk = "#000000"), .06f)
        return mapOf(
            "primary" to accent,
            "on-primary" to contrastingInk(accent, darkInk = "#000000"),
            "background" to background,
            "on-background" to contrastingInk(background, darkInk = "#000000"),
            "surface" to surface,
            "on-surface" to contrastingInk(surface, darkInk = "#000000"),
        )
    }

    /** V1 packages carry two slots; a created theme writes the same palette to both. */
    fun buildFixedSource(title: String, colors: Map<String, String>, customId: String? = null): String =
        buildSource(title, lightColors = colors, darkColors = colors, customId = customId)

    /** Replace only the editable name and palette; retain metadata, requests, artwork and identity. */
    fun editPackage(original: ThemePackage, title: String, colors: Map<String, String>): ThemePackage {
        require(roles.all { isValidHex(colors[it].orEmpty()) }) { "Enter valid theme colors" }
        val (source, assets) = ThemePackages.unpack(original.bytes)
        val generated = buildFixedSource(title, colors, original.theme.id)
        val nameLine = generated.lines().first { it.startsWith("title:") }
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines().toMutableList()
        val titleIndex = lines.indexOfFirst { it.startsWith("title:") }
        require(titleIndex >= 0)
        lines[titleIndex] = nameLine
        if (lines.none { it.startsWith("id:") }) {
            lines.add(if (lines.getOrNull(1)?.startsWith("target:") == true) 2 else 1, "id: ${original.theme.id}")
        }
        val start = lines.indexOfFirst { it.trim() == "@common" }
        require(start >= 0)
        val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith('@') } ?: lines.size
        val updated = (lines.take(start) + listOf("@common") + generated.substringAfter("@common\n").lines() + lines.drop(end)).joinToString("\n")
        val bytes = if (assets.isEmpty()) updated.toByteArray(Charsets.UTF_8) else {
            val out = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(out).use { zip ->
                (mapOf("theme.tc" to updated.toByteArray(Charsets.UTF_8)) + assets).forEach { (name, data) ->
                    zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(data); zip.closeEntry()
                }
            }
            out.toByteArray()
        }
        return ThemePackages.load(bytes)
    }

    fun defaultPalette(accentHex: String, bgTone: BackgroundTone, isDark: Boolean): Map<String, String> {
        val bg = bgTone.backgroundHex
        val surface = bgTone.surfaceHex
        val onBg = contrastingInk(bg, lightInk = "#F4F4F4", darkInk = "#202020")
        val onSurface = contrastingInk(surface, lightInk = "#F4F4F4", darkInk = "#202020")
        val normAccent = readableAccent(normalizeHex(accentHex), bg, surface, onSurface)
        val onPrimary = contrastingInk(normAccent, darkInk = "#000000")
        return mapOf(
            "primary" to normAccent,
            "on-primary" to onPrimary,
            "background" to bg,
            "on-background" to onBg,
            "surface" to surface,
            "on-surface" to onSurface,
        )
    }

    fun sanitizeId(title: String): String {
        val clean = title.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(24)
        val base = if (clean.isEmpty()) "custom" else clean
        val suffix = (System.currentTimeMillis() % 1000000).toString()
        return "custom-$base-$suffix"
    }

    fun buildSource(
        title: String,
        author: String = "You",
        lightColors: Map<String, String>,
        darkColors: Map<String, String>,
        customId: String? = null,
    ): String {
        fun cleanMetadata(value: String, fallback: String): String = value.trim().ifEmpty { fallback }.take(80)
            .filter { it.code >= 32 && it.code !in 0x202A..0x202E && it.code !in 0x2066..0x2069 }
        val safeTitle = cleanMetadata(title, "My Theme")
        val safeAuthor = cleanMetadata(author, "You")
        val id = customId ?: sanitizeId(safeTitle)

        fun quote(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        fun hex(map: Map<String, String>, role: String, fallback: String): String {
            val v = map[role]?.let { normalizeHex(it) } ?: fallback
            return v
        }

        return buildString {
            appendLine("compose-version: 1")
            appendLine("target: still")
            appendLine("id: $id")
            appendLine("version: 1.0.0")
            appendLine("title: \"${quote(safeTitle)}\"")
            appendLine("author: \"${quote(safeAuthor)}\"")
            appendLine("@common")
            appendLine("colors:")
            appendLine("  light:")
            appendLine("    primary: \"${hex(lightColors, "primary", "#27683C")}\"")
            appendLine("    on-primary: \"${hex(lightColors, "on-primary", "#FFFFFF")}\"")
            appendLine("    background: \"${hex(lightColors, "background", "#F7F9F6")}\"")
            appendLine("    on-background: \"${hex(lightColors, "on-background", "#172019")}\"")
            appendLine("    surface: \"${hex(lightColors, "surface", "#F7F9F6")}\"")
            appendLine("    on-surface: \"${hex(lightColors, "on-surface", "#172019")}\"")
            appendLine("  dark:")
            appendLine("    primary: \"${hex(darkColors, "primary", "#9FE3B2")}\"")
            appendLine("    on-primary: \"${hex(darkColors, "on-primary", "#073919")}\"")
            appendLine("    background: \"${hex(darkColors, "background", "#111713")}\"")
            appendLine("    on-background: \"${hex(darkColors, "on-background", "#E9F5EC")}\"")
            appendLine("    surface: \"${hex(darkColors, "surface", "#111713")}\"")
            appendLine("    on-surface: \"${hex(darkColors, "on-surface", "#E9F5EC")}\"")
        }
    }
}
