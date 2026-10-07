package app.still.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
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

val LocalThemePreference = staticCompositionLocalOf { ThemePreference.System }

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
    ThemePreference.Mint -> StillLightColors.copy(
        primary = Color(0xFF006A68),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFA4F3E7),
        onPrimaryContainer = Color(0xFF004B46),
        inversePrimary = Color(0xFF67D9CE),
        secondary = Color(0xFF376B68),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC0EEE7),
        onSecondaryContainer = Color(0xFF174A46),
        tertiary = Color(0xFF3D657C),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFCBE9FA),
        onTertiaryContainer = Color(0xFF21495F),
        surfaceTint = Color(0xFF006A68),
        background = Color(0xFFE8FCF7),
        onBackground = Color(0xFF102F2D),
        surface = Color(0xFFE8FCF7),
        onSurface = Color(0xFF102F2D),
        surfaceVariant = Color(0xFFCFF4EB),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFDDF9F1),
        surfaceContainer = Color(0xFFCFF4EB),
        surfaceContainerHigh = Color(0xFFBDEDE1),
        surfaceContainerHighest = Color(0xFFABE5D7),
        onSurfaceVariant = Color(0xFF3F6560),
        outline = Color(0xFF648C85),
        outlineVariant = Color(0xFFA4D8CD),
    )
    ThemePreference.Amber -> StillLightColors.copy(
        primary = Color(0xFF775900),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDB70),
        onPrimaryContainer = Color(0xFF473500),
        inversePrimary = Color(0xFFEAC348),
        secondary = Color(0xFF6D622B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3E6A5),
        onSecondaryContainer = Color(0xFF433B12),
        tertiary = Color(0xFF606C32),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE2EBAD),
        onTertiaryContainer = Color(0xFF374313),
        surfaceTint = Color(0xFF775900),
        background = Color(0xFFFFF8D9),
        onBackground = Color(0xFF2F2A12),
        surface = Color(0xFFFFF8D9),
        onSurface = Color(0xFF2F2A12),
        surfaceVariant = Color(0xFFFAEDBA),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFFF3C9),
        surfaceContainer = Color(0xFFFAEDBA),
        surfaceContainerHigh = Color(0xFFF4E3A5),
        surfaceContainerHighest = Color(0xFFECD88E),
        onSurfaceVariant = Color(0xFF655D34),
        outline = Color(0xFF8A8050),
        outlineVariant = Color(0xFFDBCD90),
    )
    ThemePreference.Plum -> StillDarkColors.copy(
        primary = Color(0xFFE4ACF4),
        onPrimary = Color(0xFF450D59),
        primaryContainer = Color(0xFF692A80),
        onPrimaryContainer = Color(0xFFFAD7FF),
        inversePrimary = Color(0xFF8A399F),
        secondary = Color(0xFFD9B9E1),
        onSecondary = Color(0xFF3F2048),
        secondaryContainer = Color(0xFF593661),
        onSecondaryContainer = Color(0xFFF4D9FA),
        tertiary = Color(0xFFE6B9B6),
        onTertiary = Color(0xFF452827),
        tertiaryContainer = Color(0xFF603E3C),
        onTertiaryContainer = Color(0xFFFFDAD6),
        surfaceTint = Color(0xFFE4ACF4),
        background = Color(0xFF24102F),
        onBackground = Color(0xFFF7E6FB),
        surface = Color(0xFF24102F),
        onSurface = Color(0xFFF7E6FB),
        surfaceVariant = Color(0xFF3B1F48),
        surfaceContainerLowest = Color(0xFF1A0923),
        surfaceContainerLow = Color(0xFF2E173A),
        surfaceContainer = Color(0xFF3B1F48),
        surfaceContainerHigh = Color(0xFF492956),
        surfaceContainerHighest = Color(0xFF583565),
        onSurfaceVariant = Color(0xFFD8BBDD),
        outline = Color(0xFFAC88B5),
        outlineVariant = Color(0xFF694772),
    )
    ThemePreference.Halloween -> StillDarkColors.copy(
        primary = Color(0xFFFFB765),
        onPrimary = Color(0xFF38200F),
        primaryContainer = Color(0xFF57331F),
        onPrimaryContainer = Color(0xFFFFDBAC),
        inversePrimary = Color(0xFF9B531F),
        secondary = Color(0xFFD8BFEA),
        onSecondary = Color(0xFF30243A),
        secondaryContainer = Color(0xFF40304C),
        onSecondaryContainer = Color(0xFFF0D9FF),
        tertiary = Color(0xFFC5D4A3),
        onTertiary = Color(0xFF29331A),
        tertiaryContainer = Color(0xFF3C482B),
        onTertiaryContainer = Color(0xFFE0EDBE),
        surfaceTint = Color(0xFFFFB765),
        background = Color(0xFF15111C),
        onBackground = Color(0xFFF5EDE4),
        surface = Color(0xFF15111C),
        onSurface = Color(0xFFF5EDE4),
        surfaceVariant = Color(0xFF292231),
        surfaceContainerLowest = Color(0xFF100D15),
        surfaceContainerLow = Color(0xFF1C1724),
        surfaceContainer = Color(0xFF241D2B),
        surfaceContainerHigh = Color(0xFF302638),
        surfaceContainerHighest = Color(0xFF3B3043),
        onSurfaceVariant = Color(0xFFCBBCCA),
        outline = Color(0xFF998A9C),
        outlineVariant = Color(0xFF4C4052),
    )
    ThemePreference.Fall -> StillLightColors.copy(
        primary = Color(0xFF98471F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF5D6B5),
        onPrimaryContainer = Color(0xFF4B291B),
        inversePrimary = Color(0xFFE9AD7B),
        secondary = Color(0xFF6F6244),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFECE2C9),
        onSecondaryContainer = Color(0xFF3D3424),
        tertiary = Color(0xFF865342),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF4DDD2),
        onTertiaryContainer = Color(0xFF4C2D25),
        surfaceTint = Color(0xFF98471F),
        background = Color(0xFFFFF8EC),
        onBackground = Color(0xFF30251C),
        surface = Color(0xFFFFF8EC),
        onSurface = Color(0xFF30251C),
        surfaceVariant = Color(0xFFF5EADC),
        surfaceContainerLowest = Color(0xFFFFFDF8),
        surfaceContainerLow = Color(0xFFFCF2E5),
        surfaceContainer = Color(0xFFF5EADC),
        surfaceContainerHigh = Color(0xFFEEE0CE),
        surfaceContainerHighest = Color(0xFFE6D4BD),
        onSurfaceVariant = Color(0xFF69594B),
        outline = Color(0xFF8D7965),
        outlineVariant = Color(0xFFDCC9B4),
    )
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
    ThemePreference.Midnight -> StillDarkColors.copy(
        primary = Color(0xFFA8C8F2),
        onPrimary = Color(0xFF0D294A),
        primaryContainer = Color(0xFF274263),
        onPrimaryContainer = Color(0xFFD9E8FF),
        inversePrimary = Color(0xFF365C88),
        secondary = Color(0xFFB9C8DF),
        onSecondary = Color(0xFF223147),
        secondaryContainer = Color(0xFF34445A),
        onSecondaryContainer = Color(0xFFDCE7F7),
        tertiary = Color(0xFFC7BFDC),
        onTertiary = Color(0xFF302B45),
        tertiaryContainer = Color(0xFF464059),
        onTertiaryContainer = Color(0xFFE9E1F7),
        surfaceTint = Color(0xFFA8C8F2),
        background = Color(0xFF101722),
        onBackground = Color(0xFFE7EDF7),
        surface = Color(0xFF101722),
        onSurface = Color(0xFFE7EDF7),
        surfaceVariant = Color(0xFF1A2534),
        surfaceContainerLowest = Color(0xFF0B111A),
        surfaceContainerLow = Color(0xFF151D29),
        surfaceContainer = Color(0xFF1A2534),
        surfaceContainerHigh = Color(0xFF223044),
        surfaceContainerHighest = Color(0xFF2B3B51),
        onSurfaceVariant = Color(0xFFB4C2D5),
        outline = Color(0xFF8292A8),
        outlineVariant = Color(0xFF38475A),
    )
    ThemePreference.Lavender -> StillLightColors.copy(
        primary = Color(0xFF64558F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE8DFFF),
        onPrimaryContainer = Color(0xFF352952),
        inversePrimary = Color(0xFFCDBEF4),
        secondary = Color(0xFF685F7B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEAE3F3),
        onSecondaryContainer = Color(0xFF3D354F),
        tertiary = Color(0xFF765B79),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF2DDF0),
        onTertiaryContainer = Color(0xFF4B304D),
        surfaceTint = Color(0xFF64558F),
        background = Color(0xFFFAF8FF),
        onBackground = Color(0xFF272330),
        surface = Color(0xFFFAF8FF),
        onSurface = Color(0xFF272330),
        surfaceVariant = Color(0xFFF0EBF7),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF6F2FD),
        surfaceContainer = Color(0xFFF0EBF7),
        surfaceContainerHigh = Color(0xFFE9E3F1),
        surfaceContainerHighest = Color(0xFFE2DBEB),
        onSurfaceVariant = Color(0xFF60596C),
        outline = Color(0xFF898294),
        outlineVariant = Color(0xFFDAD3E3),
    )
    ThemePreference.Rose -> StillLightColors.copy(
        primary = Color(0xFF8D4D64),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E4),
        onPrimaryContainer = Color(0xFF5A263B),
        inversePrimary = Color(0xFFF5B3C8),
        secondary = Color(0xFF795D67),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF5E0E6),
        onSecondaryContainer = Color(0xFF4D3540),
        tertiary = Color(0xFF806046),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF9E2CB),
        onTertiaryContainer = Color(0xFF523A23),
        surfaceTint = Color(0xFF8D4D64),
        background = Color(0xFFFFF8FA),
        onBackground = Color(0xFF302229),
        surface = Color(0xFFFFF8FA),
        onSurface = Color(0xFF302229),
        surfaceVariant = Color(0xFFF8EAF0),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFFF1F5),
        surfaceContainer = Color(0xFFF8EAF0),
        surfaceContainerHigh = Color(0xFFF2E2E9),
        surfaceContainerHighest = Color(0xFFECD9E2),
        onSurfaceVariant = Color(0xFF6B5660),
        outline = Color(0xFF927B85),
        outlineVariant = Color(0xFFE6D2DB),
    )
    ThemePreference.Peach -> StillLightColors.copy(
        primary = Color(0xFF9A533A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDDCE),
        onPrimaryContainer = Color(0xFF612C1D),
        inversePrimary = Color(0xFFF5B8A0),
        secondary = Color(0xFF806154),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF8E3D9),
        onSecondaryContainer = Color(0xFF513B32),
        tertiary = Color(0xFF746438),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF3E7BD),
        onTertiaryContainer = Color(0xFF493E1A),
        surfaceTint = Color(0xFF9A533A),
        background = Color(0xFFFFF9F5),
        onBackground = Color(0xFF322721),
        surface = Color(0xFFFFF9F5),
        onSurface = Color(0xFF322721),
        surfaceVariant = Color(0xFFF8EDE6),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFFF3ED),
        surfaceContainer = Color(0xFFF8EDE6),
        surfaceContainerHigh = Color(0xFFF2E5DD),
        surfaceContainerHighest = Color(0xFFEBDCD3),
        onSurfaceVariant = Color(0xFF6D5C53),
        outline = Color(0xFF947F74),
        outlineVariant = Color(0xFFE7D7CE),
    )
    else -> null
}

internal fun appColorScheme(context: Context, preference: ThemePreference): ColorScheme {
    val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    return when (preference) {
        ThemePreference.Wallpaper -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (systemDark) StillDarkColors else StillLightColors
        ThemePreference.System -> if (systemDark) StillDarkColors else StillLightColors
        ThemePreference.Light -> StillLightColors
        ThemePreference.Dark -> StillDarkColors
        else -> requireNotNull(simpleThemeColors(preference))
    }
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
    darkStatusBarIcons: Boolean? = null,
    communityStyle: CommunityStyle? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themePreference) {
        ThemePreference.System, ThemePreference.Wallpaper -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark, ThemePreference.Black, ThemePreference.Midnight, ThemePreference.Plum, ThemePreference.Halloween -> true
        ThemePreference.White, ThemePreference.LightBlue, ThemePreference.Sage, ThemePreference.Sand,
        ThemePreference.Lavender, ThemePreference.Rose, ThemePreference.Peach,
        ThemePreference.Mint, ThemePreference.Amber, ThemePreference.Fall -> false
    }
    val context = LocalContext.current
    val colors = communityStyle?.colors ?: appColorScheme(context, themePreference)
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (context as Activity).window
        SideEffect {
            // Keep the activity surface behind Compose in sync with the selected app theme.
            // Android exposes this surface while animating predictive back to the launcher/widget.
            window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
            // Full-screen content can require different contrast from the surrounding app theme.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkStatusBarIcons ?: communityStyle?.darkIcons() ?: !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = communityStyle?.darkIcons() ?: !dark
        }
    }
    CompositionLocalProvider(LocalThemePreference provides themePreference, LocalCommunityStyle provides communityStyle) {
        MaterialTheme(colorScheme = colors, typography = StillTypography, content = content)
    }
}
