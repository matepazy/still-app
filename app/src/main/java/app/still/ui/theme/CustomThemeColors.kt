package app.still.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Complete mapping: never copy the previous or built-in theme's color scheme. */
fun customThemeColors(palette: Map<String, String>): ColorScheme {
    fun color(role: String) = themeColor(palette.getValue(role))
    val primary = color("primary")
    val onPrimary = color("on-primary")
    val background = color("background")
    val surface = color("surface")
    val ink = color("on-surface")
    fun readable(on: Color): Color {
        fun contrast(a: Color, b: Color): Float =
            (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
        return if (contrast(on, Color.Black) >= contrast(on, Color.White)) Color.Black else Color.White
    }
    val container = lerp(surface, primary, .12f)
    val error = if (background.luminance() < .5f) Color(0xFFFFB4AB) else Color(0xFFB3261E)
    val errorContainer = lerp(surface, error, .16f)
    return ColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = container, onPrimaryContainer = readable(container),
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = container, onSecondaryContainer = readable(container),
        tertiary = primary, onTertiary = onPrimary,
        tertiaryContainer = container, onTertiaryContainer = readable(container),
        primaryFixed = container, primaryFixedDim = container,
        onPrimaryFixed = readable(container), onPrimaryFixedVariant = readable(container),
        secondaryFixed = container, secondaryFixedDim = container,
        onSecondaryFixed = readable(container), onSecondaryFixedVariant = readable(container),
        tertiaryFixed = container, tertiaryFixedDim = container,
        onTertiaryFixed = readable(container), onTertiaryFixedVariant = readable(container),
        background = background, onBackground = color("on-background"),
        surface = surface, onSurface = ink, surfaceVariant = lerp(surface, ink, .06f),
        onSurfaceVariant = ink, surfaceTint = primary,
        surfaceContainerLowest = background, surfaceContainerLow = surface,
        surfaceContainer = lerp(surface, ink, .03f),
        surfaceContainerHigh = lerp(surface, ink, .06f),
        surfaceContainerHighest = lerp(surface, ink, .1f),
        surfaceBright = lerp(surface, Color.White, .04f),
        surfaceDim = lerp(surface, Color.Black, .04f),
        outline = lerp(surface, ink, .55f), outlineVariant = lerp(surface, ink, .2f),
        inverseSurface = ink, inverseOnSurface = surface, inversePrimary = readable(ink),
        error = error, onError = readable(error),
        errorContainer = errorContainer, onErrorContainer = readable(errorContainer),
        scrim = Color.Black,
    )
}
