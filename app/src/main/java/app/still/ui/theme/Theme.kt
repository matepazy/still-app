package app.still.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.still.data.settings.ThemePreference

internal val StillDarkColors = darkColorScheme(
    primary = Color(0xFF9FE3B2),
    onPrimary = Color(0xFF073919),
    primaryContainer = Color(0xFF203D2A),
    onPrimaryContainer = Color(0xFFD4F8DC),
    inversePrimary = Color(0xFF27683C),
    secondary = Color(0xFFB0D0B6),
    onSecondary = Color(0xFF1A3521),
    secondaryContainer = Color(0xFF304C37),
    onSecondaryContainer = Color(0xFFD0EED4),
    tertiary = Color(0xFFDAD58A),
    onTertiary = Color(0xFF343100),
    tertiaryContainer = Color(0xFF4B4610),
    onTertiaryContainer = Color(0xFFEDE9A7),
    surfaceTint = Color(0xFF9FE3B2),
    background = Color(0xFF111713),
    onBackground = Color(0xFFE9F5EC),
    surface = Color(0xFF111713),
    onSurface = Color(0xFFE9F5EC),
    surfaceVariant = Color(0xFF18201B),
    surfaceContainerLowest = Color(0xFF0D130F),
    surfaceContainerLow = Color(0xFF151C17),
    surfaceContainer = Color(0xFF18201B),
    surfaceContainerHigh = Color(0xFF1D2720),
    surfaceContainerHighest = Color(0xFF243028),
    onSurfaceVariant = Color(0xFFAAB7AD),
    outline = Color(0xFF6F7D72),
    outlineVariant = Color(0xFF303A33),
    error = Color(0xFFFFB4AB),
)

internal val StillLightColors = lightColorScheme(
    primary = Color(0xFF27683C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F2C5),
    onPrimaryContainer = Color(0xFF0A2814),
    inversePrimary = Color(0xFF9FE3B2),
    secondary = Color(0xFF4A6350),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D5),
    onSecondaryContainer = Color(0xFF0D2815),
    tertiary = Color(0xFF686316),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAE8AF),
    onTertiaryContainer = Color(0xFF252200),
    surfaceTint = Color(0xFF27683C),
    background = Color(0xFFF7F9F6),
    onBackground = Color(0xFF172019),
    surface = Color(0xFFF7F9F6),
    onSurface = Color(0xFF172019),
    surfaceVariant = Color(0xFFEAF1EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F1),
    surfaceContainer = Color(0xFFEAF1EB),
    surfaceContainerHigh = Color(0xFFE3ECE5),
    surfaceContainerHighest = Color(0xFFDCE7DE),
    onSurfaceVariant = Color(0xFF526057),
    outline = Color(0xFF758078),
    outlineVariant = Color(0xFFD4DDD5),
    error = Color(0xFFBA1A1A),
)

internal fun simpleThemeColors(preference: ThemePreference): ColorScheme? = when (preference) {
    ThemePreference.Black -> StillDarkColors.copy(
        primary = Color.White,
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF292929),
        onPrimaryContainer = Color.White,
        inversePrimary = Color(0xFF343434),
        secondary = Color(0xFFD0D0D0),
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF303030),
        onSecondaryContainer = Color.White,
        tertiary = Color(0xFFBDBDBD),
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF383838),
        onTertiaryContainer = Color.White,
        surfaceTint = Color.White,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF181818),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0C0C0C),
        surfaceContainer = Color(0xFF141414),
        surfaceContainerHigh = Color(0xFF202020),
        surfaceContainerHighest = Color(0xFF2B2B2B),
        onSurfaceVariant = Color(0xFFC4C4C4),
        outline = Color(0xFF8A8A8A),
        outlineVariant = Color(0xFF444444),
    )
    ThemePreference.White -> StillLightColors.copy(
        primary = Color(0xFF343434),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE8E8E8),
        onPrimaryContainer = Color(0xFF202020),
        inversePrimary = Color(0xFFD0D0D0),
        secondary = Color(0xFF5C5C5C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEAEAEA),
        onSecondaryContainer = Color(0xFF252525),
        tertiary = Color(0xFF696969),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEEEEEE),
        onTertiaryContainer = Color(0xFF262626),
        surfaceTint = Color(0xFF343434),
        background = Color.White,
        onBackground = Color(0xFF191919),
        surface = Color.White,
        onSurface = Color(0xFF191919),
        surfaceVariant = Color(0xFFF4F4F4),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFF4F4F4),
        surfaceContainerHigh = Color(0xFFEDEDED),
        surfaceContainerHighest = Color(0xFFE5E5E5),
        onSurfaceVariant = Color(0xFF585858),
        outline = Color(0xFF777777),
        outlineVariant = Color(0xFFD5D5D5),
    )
    ThemePreference.LightBlue -> StillLightColors.copy(
        primary = Color(0xFF285B8A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD3E8FA),
        onPrimaryContainer = Color(0xFF173D60),
        inversePrimary = Color(0xFFAAD3F4),
        secondary = Color(0xFF4D6982),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCEAF5),
        onSecondaryContainer = Color(0xFF253E55),
        tertiary = Color(0xFF566886),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE2E7F7),
        onTertiaryContainer = Color(0xFF2D3E5B),
        surfaceTint = Color(0xFF285B8A),
        background = Color(0xFFF4F9FF),
        onBackground = Color(0xFF172534),
        surface = Color(0xFFF4F9FF),
        onSurface = Color(0xFF172534),
        surfaceVariant = Color(0xFFE6F0FA),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFEDF5FD),
        surfaceContainer = Color(0xFFE6F0FA),
        surfaceContainerHigh = Color(0xFFDDEBF8),
        surfaceContainerHighest = Color(0xFFD2E4F4),
        onSurfaceVariant = Color(0xFF4C6073),
        outline = Color(0xFF73899C),
        outlineVariant = Color(0xFFC8D9E8),
    )
    ThemePreference.Sage -> StillLightColors.copy(
        primary = Color(0xFF41664D),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8EBD9),
        onPrimaryContainer = Color(0xFF284432),
        inversePrimary = Color(0xFFAACDB0),
        secondary = Color(0xFF5A705D),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDFEBDD),
        onSecondaryContainer = Color(0xFF334934),
        tertiary = Color(0xFF66745A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE5EBD6),
        onTertiaryContainer = Color(0xFF3D4932),
        surfaceTint = Color(0xFF41664D),
        background = Color(0xFFF5F8F1),
        onBackground = Color(0xFF202B20),
        surface = Color(0xFFF5F8F1),
        onSurface = Color(0xFF202B20),
        surfaceVariant = Color(0xFFE9F0E5),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF0F5EC),
        surfaceContainer = Color(0xFFE9F0E5),
        surfaceContainerHigh = Color(0xFFE2ECDD),
        surfaceContainerHighest = Color(0xFFD9E6D5),
        onSurfaceVariant = Color(0xFF536351),
        outline = Color(0xFF7C8D79),
        outlineVariant = Color(0xFFCFDDCB),
    )
    ThemePreference.Sand -> StillLightColors.copy(
        primary = Color(0xFF795A38),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF4E3C8),
        onPrimaryContainer = Color(0xFF50391F),
        inversePrimary = Color(0xFFE5C398),
        secondary = Color(0xFF79654F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3E6D3),
        onSecondaryContainer = Color(0xFF4C3C2A),
        tertiary = Color(0xFF80654A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF6E5D1),
        onTertiaryContainer = Color(0xFF503A24),
        surfaceTint = Color(0xFF795A38),
        background = Color(0xFFFFFAF2),
        onBackground = Color(0xFF31271C),
        surface = Color(0xFFFFFAF2),
        onSurface = Color(0xFF31271C),
        surfaceVariant = Color(0xFFF6EDDF),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFCF5EA),
        surfaceContainer = Color(0xFFF6EDDF),
        surfaceContainerHigh = Color(0xFFF1E6D6),
        surfaceContainerHighest = Color(0xFFEBDDCA),
        onSurfaceVariant = Color(0xFF6B5C4B),
        outline = Color(0xFF94826C),
        outlineVariant = Color(0xFFE5D7C4),
    )
    else -> null
}

private val StillTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-2).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp),
)

@Composable
fun StillTheme(
    themePreference: ThemePreference,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themePreference) {
        ThemePreference.System, ThemePreference.Wallpaper -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark, ThemePreference.Black -> true
        ThemePreference.White, ThemePreference.LightBlue, ThemePreference.Sage, ThemePreference.Sand -> false
    }
    val context = LocalContext.current
    val simpleColors = simpleThemeColors(themePreference)
    val colors = when {
        themePreference == ThemePreference.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        simpleColors != null -> simpleColors
        dark -> StillDarkColors
        else -> StillLightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (context as Activity).window
        SideEffect {
            // Keep the activity surface behind Compose in sync with the selected app theme.
            // Android exposes this surface while animating predictive back to the launcher/widget.
            window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, typography = StillTypography, content = content)
}
