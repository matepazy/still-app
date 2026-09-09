package app.still.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.still.data.settings.ThemePreference

private val StillDarkColors = darkColorScheme(
    primary = Color(0xFF9FE3B2),
    onPrimary = Color(0xFF073919),
    primaryContainer = Color(0xFF203D2A),
    onPrimaryContainer = Color(0xFFD4F8DC),
    tertiary = Color(0xFFDAD58A),
    onTertiary = Color(0xFF343100),
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

private val StillLightColors = lightColorScheme(
    primary = Color(0xFF27683C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F2C5),
    onPrimaryContainer = Color(0xFF0A2814),
    tertiary = Color(0xFF686316),
    onTertiary = Color.White,
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
    useDynamicColors: Boolean,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themePreference) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val context = LocalContext.current
    val colors = when {
        useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> StillDarkColors
        else -> StillLightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
    }
    MaterialTheme(colorScheme = colors, typography = StillTypography, content = content)
}
