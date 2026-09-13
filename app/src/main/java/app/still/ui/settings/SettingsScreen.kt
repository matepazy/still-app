package app.still.ui.settings

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.still.BuildConfig
import app.still.data.settings.ThemePreference
import app.still.data.settings.UserSettings
import app.still.data.settings.WidgetAppearance
import app.still.data.settings.WidgetFontSize
import app.still.data.settings.WidgetFontStyle
import app.still.data.settings.WidgetLabel
import app.still.data.settings.parseWidgetColor
import app.still.data.settings.widgetContrastColors
import app.still.ui.components.compactDuration
import app.still.ui.components.TonalPanel
import app.still.ui.components.StillWordmark
import app.still.ui.theme.StillSpacing
import app.still.ui.components.StillIcons
import app.still.update.UpdateState
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(title: String = "Settings", onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(StillIcons.Back), contentDescription = "Back") } },
    )
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onThemeChange: (ThemePreference) -> Unit,
    onDynamicChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onWidgetClick: () -> Unit,
    modifier: Modifier = Modifier,
    updateState: UpdateState = UpdateState.Idle,
    onVersionCheckChange: (Boolean) -> Unit = {},
    onUpdateChannelChange: (String) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
) {
    var themeDialog by remember { mutableStateOf(false) }
    var updateChannelDialog by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StillSpacing.large),
    ) {
        SectionTitle("Personalization")
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column {
                SettingRow(
                    title = "Theme",
                    supporting = settings.theme.displayName,
                    onClick = { themeDialog = true },
                )
                Hairline()
                SettingSwitch(
                    title = "Use wallpaper colors",
                    supporting = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Match Still to your wallpaper"
                    } else {
                        "Available on Android 12 and later"
                    },
                    checked = settings.useDynamicColors,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = onDynamicChange,
                )
                Hairline()
                SettingRow(
                    title = "Home screen widget",
                    supporting = "Color, appearance, title and refresh",
                    onClick = onWidgetClick,
                )
            }
        }

        SectionTitle("Maintenance")
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column {
                SettingActionRow(
                    title = "Refresh usage data",
                    supporting = "Recalculate your local screen-time history",
                    icon = StillIcons.Refresh,
                    onClick = onRefresh,
                )
                Hairline()
                SettingSwitch(
                    title = "Automatic update checks",
                    supporting = "Check GitHub when Still opens",
                    checked = settings.versionCheckEnabled == true,
                    enabled = true,
                    onCheckedChange = onVersionCheckChange,
                )
                Hairline()
                SettingRow(
                    title = "Update channel",
                    supporting = if (settings.updateChannel == "pre-release") "Beta" else "Stable",
                    onClick = { updateChannelDialog = true },
                )
                Hairline()
                UpdateCheckRow(updateState = updateState, onCheckForUpdates = onCheckForUpdates)
            }
        }

        SectionTitle("About Still")
        TonalPanel(Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "Your usage history stays on this device. Still only reads Android’s local usage statistics to calculate screen time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(StillSpacing.large))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StillWordmark(markSize = 22.dp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(StillSpacing.xLarge))
    }

    if (themeDialog) {
        ChoiceDialog(
            title = "Theme",
            options = ThemePreference.entries.map { it.displayName to { onThemeChange(it) } },
            selected = settings.theme.displayName,
            onDismiss = { themeDialog = false },
        )
    }
    if (updateChannelDialog) {
        ChoiceDialog(
            title = "Update channel",
            options = listOf(
                "Stable" to { onUpdateChannelChange("release") },
                "Beta" to { onUpdateChannelChange("pre-release") },
            ),
            selected = if (settings.updateChannel == "pre-release") "Beta" else "Stable",
            onDismiss = { updateChannelDialog = false },
        )
    }
}

@Composable
fun WidgetSettingsScreen(
    settings: UserSettings,
    widgetPreviewDuration: Duration,
    onWidgetAppearanceChange: (WidgetAppearance) -> Unit,
    onWidgetColorChange: (String?) -> Unit,
    onWidgetLabelChange: (WidgetLabel) -> Unit,
    onWidgetFontSizeChange: (WidgetFontSize) -> Unit,
    onWidgetFontStyleChange: (WidgetFontStyle) -> Unit,
    onWidgetShowRefreshChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var appearanceDialog by remember { mutableStateOf(false) }
    var colorDialog by remember { mutableStateOf(false) }
    var labelDialog by remember { mutableStateOf(false) }
    var fontSizeDialog by remember { mutableStateOf(false) }
    var fontStyleDialog by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StillSpacing.large),
    ) {
        SectionTitle("Preview")
        WidgetPreview(settings, widgetPreviewDuration)

        SectionTitle("Options")
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column {
                WidgetColorSettingRow(
                    color = settings.widgetColor,
                    onClick = { colorDialog = true },
                )
                Hairline()
                if (settings.widgetColor == null) {
                    SettingRow(
                        title = "Appearance",
                        supporting = settings.widgetAppearance.displayName,
                        onClick = { appearanceDialog = true },
                    )
                    Hairline()
                }
                SettingRow(
                    title = "Title",
                    supporting = settings.widgetLabel.displayName,
                    onClick = { labelDialog = true },
                )
                Hairline()
                SettingRow(
                    title = "Text size",
                    supporting = settings.widgetFontSize.displayName,
                    onClick = { fontSizeDialog = true },
                )
                Hairline()
                SettingRow(
                    title = "Text style",
                    supporting = settings.widgetFontStyle.displayName,
                    onClick = { fontStyleDialog = true },
                )
                Hairline()
                SettingSwitch(
                    title = "Show refresh button",
                    supporting = "Refresh from your home screen",
                    checked = settings.widgetShowRefresh,
                    enabled = true,
                    onCheckedChange = onWidgetShowRefreshChange,
                )
            }
        }
        Spacer(Modifier.height(StillSpacing.large))
    }

    if (appearanceDialog) {
        ChoiceDialog(
            title = "Widget appearance",
            options = WidgetAppearance.entries.map { appearance ->
                appearance.displayName to { onWidgetAppearanceChange(appearance) }
            },
            selected = settings.widgetAppearance.displayName,
            onDismiss = { appearanceDialog = false },
        )
    }
    if (colorDialog) {
        WidgetColorDialog(
            current = settings.widgetColor,
            onApply = onWidgetColorChange,
            onDismiss = { colorDialog = false },
        )
    }
    if (labelDialog) {
        ChoiceDialog(
            title = "Widget title",
            options = WidgetLabel.entries.map { label ->
                label.displayName to { onWidgetLabelChange(label) }
            },
            selected = settings.widgetLabel.displayName,
            onDismiss = { labelDialog = false },
        )
    }
    if (fontSizeDialog) {
        ChoiceDialog(
            title = "Widget text size",
            options = WidgetFontSize.entries.map { size ->
                size.displayName to { onWidgetFontSizeChange(size) }
            },
            selected = settings.widgetFontSize.displayName,
            onDismiss = { fontSizeDialog = false },
        )
    }
    if (fontStyleDialog) {
        ChoiceDialog(
            title = "Widget text style",
            options = WidgetFontStyle.entries.map { style ->
                style.displayName to { onWidgetFontStyleChange(style) }
            },
            selected = settings.widgetFontStyle.displayName,
            onDismiss = { fontStyleDialog = false },
        )
    }
}

private val WidgetAppearance.displayName: String
    get() = when (this) {
        WidgetAppearance.System -> "Follow system"
        WidgetAppearance.Light -> "Light"
        WidgetAppearance.Dark -> "Dark"
    }

private val ThemePreference.displayName: String
    get() = when (this) {
        ThemePreference.System -> "Follow system"
        ThemePreference.Light -> "Light"
        ThemePreference.Dark -> "Dark"
    }

private val WidgetLabel.displayName: String
    get() = when (this) {
        WidgetLabel.ScreenTime -> "Screen time"
        WidgetLabel.Today -> "Today"
        WidgetLabel.Hidden -> "Hidden"
    }

private val WidgetFontSize.displayName: String
    get() = when (this) {
        WidgetFontSize.Small -> "Small"
        WidgetFontSize.Medium -> "Standard"
        WidgetFontSize.Large -> "Large"
    }

private val WidgetFontStyle.displayName: String
    get() = when (this) {
        WidgetFontStyle.Regular -> "Regular"
        WidgetFontStyle.Medium -> "Medium"
        WidgetFontStyle.Bold -> "Bold"
    }

private val WidgetFontStyle.fontWeight: FontWeight
    get() = when (this) {
        WidgetFontStyle.Regular -> FontWeight.Normal
        WidgetFontStyle.Medium -> FontWeight.Medium
        WidgetFontStyle.Bold -> FontWeight.Bold
    }

private val String?.displayWidgetColor: String
    get() = when (this) {
        null -> "Automatic · follows appearance"
        "#FFFFFF" -> "White"
        "#000000" -> "Black"
        else -> this
    }

@Composable
private fun WidgetPreview(settings: UserSettings, duration: Duration) {
    val dark = when (settings.widgetAppearance) {
        WidgetAppearance.System -> isSystemInDarkTheme()
        WidgetAppearance.Light -> false
        WidgetAppearance.Dark -> true
    }
    val customColors = parseWidgetColor(settings.widgetColor)?.let(::widgetContrastColors)
    val background = customColors?.let { Color(it.background) } ?: if (dark) Color(0xFF18201B) else Color(0xFFF1F5F1)
    val primary = customColors?.let { Color(it.foreground) } ?: if (dark) Color(0xFFE9F5EC) else Color(0xFF172019)
    val secondary = customColors?.let { Color(it.foreground) } ?: if (dark) Color(0xFFAAB7AD) else Color(0xFF526057)
    val label = when (settings.widgetLabel) {
        WidgetLabel.ScreenTime -> "Screen time"
        WidgetLabel.Today -> "Today"
        WidgetLabel.Hidden -> null
    }
    val description = buildString {
        append("Widget preview, ")
        label?.let { append("$it, ") }
        append(duration.compactDuration())
        settings.widgetColor?.let { append(", color $it") }
        if (settings.widgetShowRefresh) append(", refresh button shown")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            label?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = secondary)
            }
            Text(
                text = duration.compactDuration(),
                color = primary,
                fontSize = settings.widgetFontSize.valueSp.sp,
                fontWeight = settings.widgetFontStyle.fontWeight,
            )
        }
        if (settings.widgetShowRefresh) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(StillIcons.Refresh),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = secondary,
                )
            }
        }
    }
}

@Composable
private fun WidgetColorDialog(current: String?, onApply: (String?) -> Unit, onDismiss: () -> Unit) {
    val initialColor = parseWidgetColor(current) ?: 0xFF315A41.toInt()
    val initialHsv = remember(current) { FloatArray(3).also { AndroidColor.colorToHSV(initialColor, it) } }
    var hue by remember(current) { mutableStateOf(initialHsv[0]) }
    var saturation by remember(current) { mutableStateOf(initialHsv[1]) }
    var brightness by remember(current) { mutableStateOf(initialHsv[2]) }
    var useAutomatic by remember(current) { mutableStateOf(current == null) }
    val chosenColor = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
    val chosenHex = chosenColor.toHexColor()

    fun selectColor(color: Int) {
        val hsv = FloatArray(3).also { AndroidColor.colorToHSV(color, it) }
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        useAutomatic = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Widget color") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StillSpacing.medium),
            ) {
                Text(
                    "Choose a background. Still picks readable text automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                ) {
                    WidgetColorChoice(
                        label = "Automatic",
                        color = null,
                        isSelected = useAutomatic,
                        onClick = { useAutomatic = true },
                        modifier = Modifier.weight(1f),
                    )
                    WidgetColorChoice(
                        label = "White",
                        color = Color.White,
                        isSelected = !useAutomatic && chosenHex == "#FFFFFF",
                        onClick = { selectColor(0xFFFFFFFF.toInt()) },
                        modifier = Modifier.weight(1f),
                    )
                    WidgetColorChoice(
                        label = "Black",
                        color = Color.Black,
                        isSelected = !useAutomatic && chosenHex == "#000000",
                        onClick = { selectColor(0xFF000000.toInt()) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text("Custom color", style = MaterialTheme.typography.labelMedium)
                SaturationBrightnessPicker(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onChange = { newSaturation, newBrightness ->
                        saturation = newSaturation
                        brightness = newBrightness
                        useAutomatic = false
                    },
                )
                HuePicker(
                    hue = hue,
                    onChange = {
                        hue = it
                        useAutomatic = false
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(chosenColor))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Column {
                        Text(if (useAutomatic) "Automatic" else chosenHex, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (useAutomatic) "Uses the selected appearance" else "Custom background",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                onClick = {
                    onApply(if (useAutomatic) null else chosenHex)
                    onDismiss()
                },
            ) { Text("Apply") }
        },
    )
}

@Composable
private fun WidgetColorChoice(
    label: String,
    color: Color?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics {
                selected = isSelected
                role = Role.RadioButton
                contentDescription = label
            }
            .padding(vertical = StillSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StillSpacing.xSmall),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        ) {
            if (color == null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Color(0xFFF1F5F1), 90f, 180f, true)
                    drawArc(Color(0xFF18201B), 270f, 180f, true)
                }
            } else {
                Box(Modifier.fillMaxSize().background(color))
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SaturationBrightnessPicker(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(152.dp)
            .clip(RoundedCornerShape(12.dp))
            .colorPickerInput { position, width, height ->
                onChange(
                    (position.x / width).coerceIn(0f, 1f),
                    (1f - position.y / height).coerceIn(0f, 1f),
                )
            }
            .semantics { contentDescription = "Saturation and brightness picker" },
    ) {
        drawRect(Color.hsv(hue, 1f, 1f))
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val marker = Offset(saturation * size.width, (1f - brightness) * size.height)
        drawCircle(Color.White, radius = 8.dp.toPx(), center = marker, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, radius = 10.dp.toPx(), center = marker, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun HuePicker(hue: Float, onChange: (Float) -> Unit) {
    val colors = listOf(
        Color.Red,
        Color.Yellow,
        Color.Green,
        Color.Cyan,
        Color.Blue,
        Color.Magenta,
        Color.Red,
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .colorPickerInput { position, width, _ ->
                onChange((position.x / width).coerceIn(0f, 1f) * 360f)
            }
            .semantics { contentDescription = "Hue picker" },
    ) {
        drawRect(Brush.horizontalGradient(colors))
        val marker = Offset((hue / 360f) * size.width, size.height / 2f)
        drawCircle(Color.White, radius = 8.dp.toPx(), center = marker, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, radius = 10.dp.toPx(), center = marker, style = Stroke(1.dp.toPx()))
    }
}

private fun Modifier.colorPickerInput(onPosition: (Offset, Float, Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onPosition(down.position, size.width.toFloat(), size.height.toFloat())
        do {
            val event = awaitPointerEvent()
            event.changes.firstOrNull()?.let { change ->
                onPosition(change.position, size.width.toFloat(), size.height.toFloat())
                change.consume()
            }
        } while (event.changes.any { it.pressed })
    }
}

private fun Int.toHexColor(): String = "#" + (this and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

@Composable
private fun WidgetColorSettingRow(color: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Color", style = MaterialTheme.typography.bodyMedium)
            Text(color.displayWidgetColor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .padding(end = StillSpacing.small)
                .size(28.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        ) {
            val parsed = parseWidgetColor(color)
            if (parsed == null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Color(0xFFF1F5F1), 90f, 180f, true)
                    drawArc(Color(0xFF18201B), 270f, 180f, true)
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color(parsed)))
            }
        }
        Icon(painterResource(StillIcons.ChevronRight), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Pair<String, () -> Unit>>, selected: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, action) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { action(); onDismiss() }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == label, onClick = { action(); onDismiss() })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = StillSpacing.large, bottom = StillSpacing.small),
    )
}

@Composable
private fun SettingRow(title: String, supporting: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(StillIcons.ChevronRight), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitch(title: String, supporting: String?, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    supporting: String,
    icon: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdateCheckRow(updateState: UpdateState, onCheckForUpdates: () -> Unit) {
    val status = when (updateState) {
        UpdateState.Checking -> "Checking…"
        is UpdateState.UpdateAvailable -> "${updateState.version} is available"
        is UpdateState.Downloading -> "Downloading… ${(updateState.progress * 100).toInt()}%"
        is UpdateState.Completed -> "Ready to install"
        is UpdateState.Error -> "Couldn’t check. Try again."
        UpdateState.Idle -> "Check for the latest version"
    }
    val statusColor = when (updateState) {
        is UpdateState.Error -> MaterialTheme.colorScheme.error
        is UpdateState.UpdateAvailable, is UpdateState.Completed -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("App version", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor)
        }
        TextButton(
            onClick = onCheckForUpdates,
            enabled = updateState !is UpdateState.Checking,
        ) {
            Text("Check now")
        }
    }
}

@Composable
private fun Hairline() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
