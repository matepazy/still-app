package app.still.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.still.data.themes.CommunityThemeRepository
import app.still.data.themes.InstalledTheme
import app.still.data.themes.ThemeAppearance

val LocalCommunityStyle = staticCompositionLocalOf<CommunityStyle?> { null }
data class CommunityStyle(val installed: InstalledTheme, val values: Map<String, Any?>, val colors: ColorScheme, val wordmark: Color?) {
    fun image(slot: String): android.graphics.Bitmap? = installed.content.theme.images[slot]?.let { field ->
        runCatching { installed.content.bitmap(field.resolve(values)) }.getOrNull()
    }
}

fun communityStyle(context: Context, installed: InstalledTheme, systemDark: Boolean? = null): CommunityStyle {
    val deviceDark = systemDark ?: (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    val dark = when (installed.appearance) {
        ThemeAppearance.System -> deviceDark
        ThemeAppearance.Light -> false
        ThemeAppearance.Dark -> true
    }
    // No denied capability is queried. These values never leave this process.
    val values = installed.content.theme.requests.associate { request -> request.id to
        if (CommunityThemeRepository.permissionKey(request) !in installed.grants) null
        else when (request.source) {
            "sysLightMode" -> if (deviceDark) "dark" else "light"
            "sysReducedMotion" -> (Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
            "sysFontScale" -> context.resources.configuration.fontScale.toDouble()
            else -> null
        }
    }
    val palette = if (dark) installed.content.theme.dark else installed.content.theme.light
    val colors = customThemeColors(palette.mapValues { it.value.resolve(values) })
    return CommunityStyle(installed, values, colors, installed.content.theme.wordmark?.let { themeColor(it.resolve(values)) } ?: colors.primary)
}

fun themeColor(hex: String): Color {
    val rgb = hex.substring(1, 7).toLong(16)
    val alpha = if (hex.length == 9) hex.substring(7, 9).toLong(16) else 255L
    return Color((alpha shl 24) or rgb)
}
fun CommunityStyle.darkIcons(): Boolean = colors.background.luminance() > .179f

/** Keep readable custom palettes on recovery surfaces; fall back only when controls could vanish. */
fun communityControlColors(colors: ColorScheme): ColorScheme {
    fun readable(ink: Color, surface: Color): Boolean {
        if (ink.alpha < 1f || surface.alpha < 1f) return false
        val a = ink.luminance(); val b = surface.luminance()
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f) >= 4.5f
    }
    val surfaces = listOf(colors.surface, colors.surfaceContainer, colors.surfaceContainerHigh, colors.surfaceContainerHighest)
    val readableControls = readable(colors.onBackground, colors.background) &&
        surfaces.all { readable(colors.onSurface, it) && readable(colors.onSurfaceVariant, it) && readable(colors.primary, it) } &&
        readable(colors.primary, colors.background) && readable(colors.onPrimary, colors.primary)
    return if (readableControls) colors
        else if (colors.background.luminance() > .179f) StillLightColors else StillDarkColors
}

/** Community data cannot hide the controls used to revoke consent or remove it. */
@androidx.compose.runtime.Composable
fun TrustedThemeControls(content: @androidx.compose.runtime.Composable () -> Unit) {
    val community = LocalCommunityStyle.current
    if (community == null) content()
    else {
        val colors = communityControlColors(community.colors)
        // A local recovery surface must not overwrite the activity window or clear the active style.
        androidx.compose.material3.MaterialTheme(colorScheme = colors) {
            androidx.compose.material3.Surface(color = colors.background) { content() }
        }
    }
}
