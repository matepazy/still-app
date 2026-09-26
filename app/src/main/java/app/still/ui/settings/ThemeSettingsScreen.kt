package app.still.ui.settings

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.still.data.settings.ThemePreference
import app.still.ui.theme.StillDarkColors
import app.still.ui.theme.StillLightColors
import app.still.ui.theme.StillSpacing

@Composable
fun ThemeSettingsScreen(
    selectedTheme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val supportsWallpaper = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val wallpaperColors = if (supportsWallpaper) {
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null

    Column(modifier.fillMaxSize().padding(top = StillSpacing.large)) {
        Text(
            "Default themes",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = StillSpacing.large),
        )
        Spacer(Modifier.height(StillSpacing.medium))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
                .padding(horizontal = StillSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
        ) {
            ThemePreference.entries.forEach { option ->
                val available = option != ThemePreference.Wallpaper || supportsWallpaper
                ThemeCard(
                    option = option,
                    selected = selectedTheme == option,
                    enabled = available,
                    wallpaperBackground = wallpaperColors?.primaryContainer ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                    wallpaperInk = wallpaperColors?.onPrimaryContainer ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onThemeChange(option) },
                )
            }
        }
        if (!supportsWallpaper) {
            Text(
                "Wallpaper colors are available on Android 12 and later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = StillSpacing.large, vertical = StillSpacing.medium),
            )
        }
    }
}

@Composable
private fun ThemeCard(
    option: ThemePreference,
    selected: Boolean,
    enabled: Boolean,
    wallpaperBackground: Color,
    wallpaperInk: Color,
    onClick: () -> Unit,
) {
    val label = when (option) {
        ThemePreference.System -> "Use system"
        ThemePreference.Light -> "Light"
        ThemePreference.Dark -> "Dark"
        ThemePreference.Wallpaper -> "Wallpaper"
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.size(width = 104.dp, height = 178.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        ThemePreview(
            option = option,
            wallpaperBackground = wallpaperBackground,
            wallpaperInk = wallpaperInk,
            modifier = Modifier.size(width = 104.dp, height = 138.dp)
                .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
                .padding(4.dp),
        )
        Spacer(Modifier.height(StillSpacing.small))
        Text(label, style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}

@Composable
private fun ThemePreview(
    option: ThemePreference,
    wallpaperBackground: Color,
    wallpaperInk: Color,
    modifier: Modifier = Modifier,
) {
    val light = StillLightColors
    val dark = StillDarkColors
    val background = when (option) {
        ThemePreference.System, ThemePreference.Light -> light.surface
        ThemePreference.Dark -> dark.surface
        ThemePreference.Wallpaper -> wallpaperBackground
    }
    val ink = when (option) {
        ThemePreference.System, ThemePreference.Light -> light.onSurface
        ThemePreference.Dark -> dark.onSurface
        ThemePreference.Wallpaper -> wallpaperInk
    }
    Canvas(modifier) {
        val corner = CornerRadius(12.dp.toPx())
        drawRoundRect(background, cornerRadius = corner)
        if (option == ThemePreference.System) {
            val triangle = Path().apply {
                moveTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            clipPath(triangle) { drawRoundRect(dark.surface, cornerRadius = corner) }
        }
        val padding = size.width * .16f
        val barHeight = 5.dp.toPx()
        val barWidth = size.width - padding * 2
        val rows = listOf(.23f to .66f, .38f to .9f, .53f to .54f, .68f to .78f)
        rows.forEach { (y, fraction) ->
            val top = size.height * y
            val bar = Size(barWidth * fraction, barHeight)
            drawRoundRect(ink.copy(alpha = .72f), Offset(padding, top), bar, CornerRadius(barHeight / 2))
            if (option == ThemePreference.System) {
                val triangle = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                clipPath(triangle) {
                    drawRoundRect(dark.onSurface.copy(alpha = .72f), Offset(padding, top), bar, CornerRadius(barHeight / 2))
                }
            }
        }
    }
}
