package app.still.ui.settings

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.still.data.settings.ThemePreference
import app.still.ui.components.StillIcons
import app.still.ui.theme.StillDarkColors
import app.still.ui.theme.StillLightColors
import app.still.ui.theme.StillSpacing
import app.still.ui.theme.simpleThemeColors

@Composable
fun ThemeSettingsScreen(
    selectedTheme: ThemePreference,
    promotedSimpleTheme: ThemePreference?,
    onThemeChange: (ThemePreference) -> Unit,
    onDrawerThemeChange: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val supportsWallpaper = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val defaultThemes = listOf(ThemePreference.System, ThemePreference.Light, ThemePreference.Dark, ThemePreference.Wallpaper)
    val simpleThemes = listOf(
        ThemePreference.Black, ThemePreference.White, ThemePreference.LightBlue,
        ThemePreference.Sage, ThemePreference.Sand, ThemePreference.Midnight,
        ThemePreference.Lavender, ThemePreference.Rose, ThemePreference.Peach,
    )
    val orderedSimpleThemes = simpleThemes.promotedFirst(promotedSimpleTheme)
    var showAllSimpleThemes by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val wallpaperColors = if (supportsWallpaper) {
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = StillSpacing.large)) {
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
            defaultThemes.forEach { option ->
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
        Text(
            "Simple colors",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(
                start = StillSpacing.large,
                end = StillSpacing.large,
                top = StillSpacing.xLarge,
                bottom = StillSpacing.medium,
            ),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
                .padding(horizontal = StillSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
        ) {
            orderedSimpleThemes.take(4).forEach { option ->
                ThemeCard(
                    option = option,
                    selected = selectedTheme == option,
                    enabled = true,
                    wallpaperBackground = Color.Unspecified,
                    wallpaperInk = Color.Unspecified,
                    onClick = { onThemeChange(option) },
                )
            }
            if (simpleThemes.size > 4) {
                ShowAllThemesCard(onClick = { showAllSimpleThemes = true })
            }
        }
        Text(
            "Special themes",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(
                start = StillSpacing.large,
                end = StillSpacing.large,
                top = StillSpacing.xLarge,
                bottom = StillSpacing.medium,
            ),
        )
        Text(
            "No special themes yet, but stay tuned!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = StillSpacing.large),
        )
        Spacer(Modifier.height(StillSpacing.xLarge))
    }

    if (showAllSimpleThemes) {
        SimpleThemesSheet(
            themes = orderedSimpleThemes,
            selectedTheme = selectedTheme,
            onDismiss = { showAllSimpleThemes = false },
            onSelect = { option ->
                onDrawerThemeChange(option)
                showAllSimpleThemes = false
            },
        )
    }
}

private fun List<ThemePreference>.promotedFirst(promotedTheme: ThemePreference?): List<ThemePreference> =
    promotedTheme?.takeIf { it in this }?.let { theme -> listOf(theme) + filterNot { it == theme } } ?: this

@Composable
private fun ShowAllThemesCard(onClick: () -> Unit) {
    Column(Modifier.width(104.dp).height(178.dp).clickable(role = Role.Button, onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().height(138.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                        repeat(2) {
                            Box(
                                Modifier.size(18.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .7f), RoundedCornerShape(5.dp)),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(StillSpacing.small))
        Text("Show all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleThemesSheet(
    themes: List<ThemePreference>,
    selectedTheme: ThemePreference,
    onDismiss: () -> Unit,
    onSelect: (ThemePreference) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = StillSpacing.large)
                .padding(bottom = StillSpacing.xLarge),
        ) {
            Text("Simple colors", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(StillSpacing.large))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                themes.chunked(3).forEach { rowThemes ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                        rowThemes.forEach { option ->
                            ThemeCard(
                                option = option,
                                selected = selectedTheme == option,
                                enabled = true,
                                wallpaperBackground = Color.Unspecified,
                                wallpaperInk = Color.Unspecified,
                                onClick = { onSelect(option) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowThemes.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
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
    modifier: Modifier = Modifier.width(104.dp),
) {
    val label = when (option) {
        ThemePreference.System -> "Use system"
        ThemePreference.Light -> "Light"
        ThemePreference.Dark -> "Dark"
        ThemePreference.Wallpaper -> "Wallpaper"
        ThemePreference.Black -> "Black"
        ThemePreference.White -> "White"
        ThemePreference.LightBlue -> "Light Blue"
        ThemePreference.Sage -> "Sage"
        ThemePreference.Sand -> "Sand"
        ThemePreference.Midnight -> "Midnight"
        ThemePreference.Lavender -> "Lavender"
        ThemePreference.Rose -> "Rose"
        ThemePreference.Peach -> "Peach"
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.height(178.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(138.dp)) {
            ThemePreview(
                option = option,
                wallpaperBackground = wallpaperBackground,
                wallpaperInk = wallpaperInk,
                modifier = Modifier.fillMaxSize()
                    .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
                    .padding(4.dp),
            )
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(StillIcons.Check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
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
    val simple = simpleThemeColors(option)
    val background = when (option) {
        ThemePreference.System, ThemePreference.Light -> light.surface
        ThemePreference.Dark -> dark.surface
        ThemePreference.Wallpaper -> wallpaperBackground
        ThemePreference.Black, ThemePreference.White -> simple!!.surface
        ThemePreference.LightBlue, ThemePreference.Sage, ThemePreference.Sand,
        ThemePreference.Midnight, ThemePreference.Lavender, ThemePreference.Rose, ThemePreference.Peach -> simple!!.primaryContainer
    }
    val ink = when (option) {
        ThemePreference.System, ThemePreference.Light -> light.onSurface
        ThemePreference.Dark -> dark.onSurface
        ThemePreference.Wallpaper -> wallpaperInk
        ThemePreference.Black, ThemePreference.White -> simple!!.onSurface
        ThemePreference.LightBlue, ThemePreference.Sage, ThemePreference.Sand,
        ThemePreference.Midnight, ThemePreference.Lavender, ThemePreference.Rose, ThemePreference.Peach -> simple!!.onPrimaryContainer
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
