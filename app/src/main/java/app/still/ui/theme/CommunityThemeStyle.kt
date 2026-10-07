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
    fun color(role: String) = themeColor(palette.getValue(role).resolve(values))
    val primary = color("primary"); val onPrimary = color("on-primary")
    val surface = color("surface"); val ink = color("on-surface")
    val base = if (dark) StillDarkColors else StillLightColors
    val colors = base.copy(
        primary = primary, onPrimary = onPrimary, primaryContainer = surface, onPrimaryContainer = ink,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = surface, onSecondaryContainer = ink,
        tertiary = primary, onTertiary = onPrimary, tertiaryContainer = surface, onTertiaryContainer = ink,
        background = color("background"), onBackground = color("on-background"), surface = surface, onSurface = ink,
        surfaceVariant = surface, onSurfaceVariant = ink, surfaceTint = primary,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = surface,
        outline = ink.copy(alpha = .55f), outlineVariant = ink.copy(alpha = .2f),
        inverseSurface = ink, inverseOnSurface = surface, inversePrimary = primary,
    )
    return CommunityStyle(installed, values, colors, installed.content.theme.wordmark?.let { themeColor(it.resolve(values)) })
}

fun themeColor(hex: String): Color {
    val rgb = hex.substring(1, 7).toLong(16)
    val alpha = if (hex.length == 9) hex.substring(7, 9).toLong(16) else 255L
    return Color((alpha shl 24) or rgb)
}
fun CommunityStyle.darkIcons(): Boolean = colors.background.luminance() > .5f

/** Readable host surfaces follow the active theme's appearance without trusting its palette. */
@androidx.compose.runtime.Composable
fun communityControlColors(appearance: ThemeAppearance): ColorScheme {
    val dark = when (appearance) {
        ThemeAppearance.System -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeAppearance.Light -> false
        ThemeAppearance.Dark -> true
    }
    return if (dark) StillDarkColors else StillLightColors
}

/** Community data cannot hide the controls used to revoke consent or remove it. */
@androidx.compose.runtime.Composable
fun TrustedThemeControls(content: @androidx.compose.runtime.Composable () -> Unit) {
    val community = LocalCommunityStyle.current
    if (community == null) content()
    else {
        val preference = when (community.installed.appearance) {
            ThemeAppearance.System -> app.still.data.settings.ThemePreference.System
            ThemeAppearance.Light -> app.still.data.settings.ThemePreference.Light
            ThemeAppearance.Dark -> app.still.data.settings.ThemePreference.Dark
        }
        StillTheme(preference) {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() }
        }
    }
}
