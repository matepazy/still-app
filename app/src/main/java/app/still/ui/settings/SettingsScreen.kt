package app.still.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.still.BuildConfig
import app.still.data.settings.ThemePreference
import app.still.data.settings.UserSettings
import app.still.data.settings.WidgetAppearance
import app.still.data.settings.WidgetLabel
import app.still.ui.components.compactDuration
import app.still.ui.components.TonalPanel
import app.still.ui.components.StillWordmark
import app.still.ui.theme.StillSpacing
import app.still.ui.components.StillIcons
import app.still.update.UpdateState
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(StillIcons.Back), contentDescription = "Back") } },
    )
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onThemeChange: (ThemePreference) -> Unit,
    onDynamicChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    widgetPreviewDuration: Duration = Duration.ZERO,
    onWidgetAppearanceChange: (WidgetAppearance) -> Unit = {},
    onWidgetLabelChange: (WidgetLabel) -> Unit = {},
    onWidgetShowRefreshChange: (Boolean) -> Unit = {},
    updateState: UpdateState = UpdateState.Idle,
    onVersionCheckChange: (Boolean) -> Unit = {},
    onUpdateChannelChange: (String) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
) {
    var themeDialog by remember { mutableStateOf(false) }
    var widgetAppearanceDialog by remember { mutableStateOf(false) }
    var widgetLabelDialog by remember { mutableStateOf(false) }
    var updateChannelDialog by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = StillSpacing.medium)) {
        SectionTitle("Appearance")
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column {
                SettingRow("Theme", settings.theme.name, onClick = { themeDialog = true })
                Hairline()
                SettingSwitch(
                    title = "Use wallpaper colors",
                    supporting = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) null else "Android 12 and later",
                    checked = settings.useDynamicColors,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = onDynamicChange,
                )
            }
        }

        SectionTitle("Widget")
        WidgetPreview(settings, widgetPreviewDuration)
        Spacer(Modifier.height(StillSpacing.small))
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column {
                SettingRow(
                    title = "Appearance",
                    supporting = settings.widgetAppearance.displayName,
                    onClick = { widgetAppearanceDialog = true },
                )
                Hairline()
                SettingRow(
                    title = "Title",
                    supporting = settings.widgetLabel.displayName,
                    onClick = { widgetLabelDialog = true },
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

        SectionTitle("Data")
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onRefresh).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Refresh usage data", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Icon(painterResource(StillIcons.Refresh), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle("Updates")
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column {
                SettingSwitch(
                    title = "Automatically check for updates",
                    supporting = "Check GitHub when Still starts",
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
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Manual check", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when (updateState) {
                                UpdateState.Checking -> "Checking…"
                                is UpdateState.UpdateAvailable -> "Update available: ${updateState.version}"
                                is UpdateState.Downloading -> "Downloading… ${(updateState.progress * 100).toInt()}%"
                                is UpdateState.Completed -> "Ready to install"
                                is UpdateState.Error -> "Check failed"
                                UpdateState.Idle -> "Up to date"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onCheckForUpdates, enabled = updateState !is UpdateState.Checking) { Text("Check") }
                }
            }
        }

        SectionTitle("Privacy")
        TonalPanel(Modifier.fillMaxWidth()) {
            Text(
                "Still reads Android’s local usage statistics to calculate your screen-time history. This information is processed on your device and is not uploaded anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle("About")
        TonalPanel(Modifier.fillMaxWidth()) {
            Column {
                StillWordmark(markSize = 24.dp)
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(StillSpacing.large))
    }

    if (themeDialog) {
        ChoiceDialog(
            title = "Theme",
            options = ThemePreference.entries.map { it.name to { onThemeChange(it) } },
            selected = settings.theme.name,
            onDismiss = { themeDialog = false },
        )
    }
    if (widgetAppearanceDialog) {
        ChoiceDialog(
            title = "Widget appearance",
            options = WidgetAppearance.entries.map { appearance ->
                appearance.displayName to { onWidgetAppearanceChange(appearance) }
            },
            selected = settings.widgetAppearance.displayName,
            onDismiss = { widgetAppearanceDialog = false },
        )
    }
    if (widgetLabelDialog) {
        ChoiceDialog(
            title = "Widget title",
            options = WidgetLabel.entries.map { label ->
                label.displayName to { onWidgetLabelChange(label) }
            },
            selected = settings.widgetLabel.displayName,
            onDismiss = { widgetLabelDialog = false },
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

private val WidgetAppearance.displayName: String
    get() = when (this) {
        WidgetAppearance.System -> "Follow system"
        WidgetAppearance.Light -> "Light"
        WidgetAppearance.Dark -> "Dark"
    }

private val WidgetLabel.displayName: String
    get() = when (this) {
        WidgetLabel.ScreenTime -> "Screen time"
        WidgetLabel.Today -> "Today"
        WidgetLabel.Hidden -> "Hidden"
    }

@Composable
private fun WidgetPreview(settings: UserSettings, duration: Duration) {
    val dark = when (settings.widgetAppearance) {
        WidgetAppearance.System -> isSystemInDarkTheme()
        WidgetAppearance.Light -> false
        WidgetAppearance.Dark -> true
    }
    val background = if (dark) Color(0xFF18201B) else Color(0xFFF1F5F1)
    val primary = if (dark) Color(0xFFE9F5EC) else Color(0xFF172019)
    val secondary = if (dark) Color(0xFFAAB7AD) else Color(0xFF526057)
    val label = when (settings.widgetLabel) {
        WidgetLabel.ScreenTime -> "Screen time"
        WidgetLabel.Today -> "Today"
        WidgetLabel.Hidden -> null
    }
    val description = buildString {
        append("Widget preview, ")
        label?.let { append("$it, ") }
        append(duration.compactDuration())
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
            Text(duration.compactDuration(), style = MaterialTheme.typography.headlineSmall, color = primary)
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
    Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = StillSpacing.large, bottom = StillSpacing.small))
}

@Composable
private fun SettingRow(title: String, supporting: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(StillIcons.ChevronRight), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitch(title: String, supporting: String?, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun Hairline() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
