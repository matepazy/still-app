package app.still.ui.settings

import android.graphics.Color as AndroidColor
import android.text.format.Formatter
import androidx.annotation.DrawableRes
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import app.still.data.settings.DaylineWidgetLabel
import app.still.data.usage.StoredDataSummary
import app.still.data.usage.ArchiveMigrationNotice
import app.still.data.usage.ArchiveStorageFormat
import app.still.domain.model.DailyUsage
import app.still.domain.model.DaylineKind
import app.still.data.settings.UserSettings
import app.still.data.settings.WidgetAppearance
import app.still.data.settings.WidgetFontSize
import app.still.data.settings.WidgetFontStyle
import app.still.data.settings.WidgetLabel
import app.still.data.settings.WIDGET_PILL_RADIUS
import app.still.data.settings.parseWidgetColor
import app.still.data.settings.widgetContrastColors
import app.still.data.settings.systemWidgetColors
import app.still.ui.components.compactDuration
import app.still.ui.components.TonalPanel
import app.still.ui.components.StillWordmark
import app.still.ui.theme.StillSpacing
import app.still.ui.components.StillIcons
import app.still.update.UpdateState
import app.still.ui.ArchiveRestoreState
import app.still.ui.ArchiveUpgradeState
import app.still.ui.ArchiveBackupDeleteState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(
    title: String = "Settings",
    onReset: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(StillIcons.Back), contentDescription = "Back") } },
        actions = {
            onReset?.let { reset ->
                TextButton(onClick = reset) { Text("Reset") }
            }
        },
    )
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onThemeClick: () -> Unit,
    onRefresh: () -> Unit,
    onWidgetClick: () -> Unit,
    onStoredDataClick: () -> Unit,
    onSaveUsageHistoryChange: (Boolean) -> Unit = {},
    storedDataSummary: StoredDataSummary? = null,
    archiveRestoreState: ArchiveRestoreState = ArchiveRestoreState.Idle,
    archiveUpgradeState: ArchiveUpgradeState = ArchiveUpgradeState.Idle,
    onRestoreArchive: () -> Unit = {},
    onUpgradeArchive: () -> Unit = {},
    onDismissArchiveRestoreResult: () -> Unit = {},
    onDismissArchiveUpgradeResult: () -> Unit = {},
    modifier: Modifier = Modifier,
    updateState: UpdateState = UpdateState.Idle,
    onVersionCheckChange: (Boolean) -> Unit = {},
    onUpdateChannelChange: (String) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
) {
    var updateChannelDialog by remember { mutableStateOf(false) }
    var stopSavingDialog by remember { mutableStateOf(false) }
    var restoreArchiveDialog by remember { mutableStateOf(false) }
    var upgradeArchiveDialog by remember { mutableStateOf(false) }
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
                    onClick = onThemeClick,
                )
                Hairline()
                SettingRow(
                    title = "Home screen widget",
                    supporting = "Color, appearance, title and refresh",
                    onClick = onWidgetClick,
                )
            }
        }

        SectionTitle("Data")
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column {
                SettingSwitch(
                    title = "Save usage history",
                    supporting = if (settings.saveUsageHistory) {
                        "On-device history for long-term patterns"
                    } else {
                        "Off — Still only reads current Android data"
                    },
                    checked = settings.saveUsageHistory,
                    enabled = true,
                    onCheckedChange = { enabled ->
                        if (enabled) onSaveUsageHistoryChange(true) else stopSavingDialog = true
                    },
                )
                Hairline()
                SettingRow(
                    title = "Data stored on this device",
                    supporting = "See exactly what Still keeps locally",
                    onClick = onStoredDataClick,
                )
                if (storedDataSummary?.storageFormat == ArchiveStorageFormat.Legacy) {
                    Hairline()
                    SettingRow(
                        title = "Upgrade archive",
                        supporting = if (archiveUpgradeState == ArchiveUpgradeState.Upgrading) {
                            "Upgrading usage history…"
                        } else {
                            "Convert previous history to the compact format"
                        },
                        enabled = archiveUpgradeState != ArchiveUpgradeState.Upgrading,
                        onClick = { upgradeArchiveDialog = true },
                    )
                }
                if (storedDataSummary?.backupAvailable == true &&
                    storedDataSummary.storageFormat == ArchiveStorageFormat.Compact
                ) {
                    Hairline()
                    SettingRow(
                        title = "Restore backup",
                        supporting = if (archiveRestoreState == ArchiveRestoreState.Restoring) {
                            "Restoring previous archive…"
                        } else {
                            "Return to the archive saved before compression"
                        },
                        enabled = archiveRestoreState != ArchiveRestoreState.Restoring,
                        onClick = { restoreArchiveDialog = true },
                    )
                }
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
    if (stopSavingDialog) {
        AlertDialog(
            onDismissRequest = { stopSavingDialog = false },
            title = { Text("Keep usage history on?") },
            text = {
                Text(
                    "Saved history lets Still find long-term patterns, because Android may remove older details. " +
                        "It stays on this device and is never uploaded. Turning this off deletes Still’s saved usage history.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        stopSavingDialog = false
                        onSaveUsageHistoryChange(false)
                    },
                ) { Text("Turn off & delete") }
            },
            dismissButton = {
                TextButton(onClick = { stopSavingDialog = false }) { Text("Keep on") }
            },
        )
    }
    if (restoreArchiveDialog) {
        AlertDialog(
            onDismissRequest = { restoreArchiveDialog = false },
            title = { Text("Restore the previous archive?") },
            text = {
                Text(
                    "Still will switch back to the archive format used before migration. History recorded since then will be kept, " +
                        "and nothing changes unless the backup restores successfully.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreArchiveDialog = false
                        onRestoreArchive()
                    },
                ) { Text("Restore backup") }
            },
            dismissButton = {
                TextButton(onClick = { restoreArchiveDialog = false }) { Text("Cancel") }
            },
        )
    }
    if (upgradeArchiveDialog) {
        AlertDialog(
            onDismissRequest = { upgradeArchiveDialog = false },
            title = { Text("Upgrade usage history?") },
            text = { Text("Still will convert your previous archive to the compact format and keep a local safety backup when there is saved history. You can restore it from Settings › Data.") },
            confirmButton = {
                TextButton(onClick = {
                    upgradeArchiveDialog = false
                    onUpgradeArchive()
                }) { Text("Upgrade archive") }
            },
            dismissButton = { TextButton(onClick = { upgradeArchiveDialog = false }) { Text("Cancel") } },
        )
    }
    when (archiveRestoreState) {
        ArchiveRestoreState.Restored -> AlertDialog(
            onDismissRequest = onDismissArchiveRestoreResult,
            title = { Text("Backup restored") },
            text = { Text("Still is using the previous archive format again. Your saved history is intact.") },
            confirmButton = { TextButton(onClick = onDismissArchiveRestoreResult) { Text("Done") } },
        )
        is ArchiveRestoreState.Error -> AlertDialog(
            onDismissRequest = onDismissArchiveRestoreResult,
            title = { Text("Backup wasn’t restored") },
            text = { Text(archiveRestoreState.message) },
            confirmButton = { TextButton(onClick = onDismissArchiveRestoreResult) { Text("Close") } },
        )
        ArchiveRestoreState.Idle, ArchiveRestoreState.Restoring -> Unit
    }
    when (archiveUpgradeState) {
        ArchiveUpgradeState.Upgraded -> AlertDialog(
            onDismissRequest = onDismissArchiveUpgradeResult,
            title = { Text("Usage history upgraded") },
            text = { Text(if (storedDataSummary?.backupAvailable == true) {
                "Still is using the compact archive. Your previous history is saved as a local safety backup."
            } else {
                "Still is using the compact archive."
            }) },
            confirmButton = { TextButton(onClick = onDismissArchiveUpgradeResult) { Text("Done") } },
        )
        is ArchiveUpgradeState.Error -> AlertDialog(
            onDismissRequest = onDismissArchiveUpgradeResult,
            title = { Text("Archive wasn’t upgraded") },
            text = { Text(archiveUpgradeState.message) },
            confirmButton = { TextButton(onClick = onDismissArchiveUpgradeResult) { Text("Close") } },
        )
        ArchiveUpgradeState.Idle, ArchiveUpgradeState.Upgrading -> Unit
    }
}

@Composable
fun ArchiveMigrationDialog(
    notice: ArchiveMigrationNotice,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activeSaved = Formatter.formatShortFileSize(context, notice.activeBytesSaved)
    val backupSize = Formatter.formatShortFileSize(context, notice.backupSizeBytes)
    val resultText = when {
        notice.netBytesSaved > 0L ->
            "That frees ${Formatter.formatShortFileSize(context, notice.netBytesSaved)} on this device, including the backup."
        notice.activeBytesSaved > 0L ->
            "The active archive is $activeSaved smaller. Keeping the $backupSize safety backup currently uses " +
                "${Formatter.formatShortFileSize(context, -notice.netBytesSaved)} more in total."
        else ->
            "The compact archive and its $backupSize safety backup currently use " +
                "${Formatter.formatShortFileSize(context, -notice.netBytesSaved)} more in total."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { StoredDataIcon(StillIcons.Storage) },
        title = { Text("Usage history upgraded") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                MigrationDialogRow(
                    icon = StillIcons.Apps,
                    title = "Compact history",
                    body = "Still rebuilt your saved history so the app can recreate details when needed.",
                )
                MigrationDialogRow(
                    icon = StillIcons.Storage,
                    title = "Storage",
                    body = resultText,
                )
                MigrationDialogRow(
                    icon = StillIcons.History,
                    title = "Safety backup",
                    body = "Restore it from Settings › Data if anything looks wrong, or delete it later from Data stored on this device.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

@Composable
private fun MigrationDialogRow(@DrawableRes icon: Int, title: String, body: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
    ) {
        StoredDataIcon(icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StillSpacing.small),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StoredDataScreen(
    summary: StoredDataSummary?,
    archiveBackupDeleteState: ArchiveBackupDeleteState = ArchiveBackupDeleteState.Idle,
    onDeleteArchiveBackup: () -> Unit = {},
    onDismissArchiveBackupDeleteResult: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var deleteBackupDialog by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StillSpacing.large),
    ) {
        Text(
            "A private summary of what Still keeps locally. Raw usage records are never shown here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = StillSpacing.large),
        )

        SectionTitle("Overview")
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column {
                StoredDataFact(
                    label = "Saved history",
                    value = summary?.let {
                        val days = NumberFormat.getIntegerInstance().format(it.dayCount)
                        val apps = NumberFormat.getIntegerInstance().format(it.appCount)
                        "$days ${if (it.dayCount == 1L) "day" else "days"} · $apps ${if (it.appCount == 1L) "app" else "apps"}"
                    } ?: "Counting…",
                )
                Hairline()
                StoredDataFact(
                    label = "Coverage",
                    value = summary?.let(::formatCoverage) ?: "Checking…",
                )
                Hairline()
                StoredDataFact(
                    label = "Last updated",
                    value = summary?.lastUpdatedMillis?.let(::formatLastUpdated) ?: if (summary == null) "Checking…" else "Not yet",
                )
                Hairline()
                StoredDataFact(
                    label = "Total storage",
                    value = summary?.let { Formatter.formatShortFileSize(context, it.sizeBytes) } ?: "Calculating…",
                )
                if (summary != null) {
                    Hairline()
                    StoredDataFact(
                        label = "Archive format",
                        value = if (summary.storageFormat == ArchiveStorageFormat.Compact) "Compact" else "Previous",
                    )
                }
                if (summary?.backupAvailable == true) {
                    Hairline()
                    StoredDataFact(
                        label = "Safety backup",
                        value = Formatter.formatShortFileSize(context, summary.backupSizeBytes),
                    )
                }
            }
        }

        if (summary?.backupAvailable == true && summary.storageFormat == ArchiveStorageFormat.Compact) {
            SectionTitle("Safety backup")
            TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                SettingActionRow(
                    title = "Delete backup",
                    supporting = if (archiveBackupDeleteState == ArchiveBackupDeleteState.Deleting) {
                        "Deleting the previous ${Formatter.formatShortFileSize(context, summary.backupSizeBytes)} archive…"
                    } else {
                        "Free ${Formatter.formatShortFileSize(context, summary.backupSizeBytes)} · This removes the restore option"
                    },
                    icon = StillIcons.Delete,
                    enabled = archiveBackupDeleteState != ArchiveBackupDeleteState.Deleting,
                    onClick = { deleteBackupDialog = true },
                )
            }
        }

        SectionTitle("What is kept")
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column {
                StoredDataItem(
                    title = "Usage history",
                    icon = StillIcons.Apps,
                    body = if (summary?.storageFormat == ArchiveStorageFormat.Legacy) {
                        "App, screen and lock events with their original timestamps."
                    } else {
                        "Compact app, screen and lock transitions, rounded to the second."
                    },
                )
                Hairline()
                StoredDataItem(
                    title = "Daily app totals",
                    icon = StillIcons.Timeline,
                    body = "App identity, time used and opens. Sessions, quick checks and patterns are rebuilt when needed.",
                )
                Hairline()
                StoredDataItem(
                    title = "App preferences",
                    icon = StillIcons.Settings,
                    body = "Theme, widgets, navigation and update settings. A downloaded update may remain temporarily in cache.",
                )
            }
        }

        Spacer(Modifier.height(StillSpacing.large))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = StillSpacing.small),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
        ) {
            StoredDataIcon(StillIcons.Privacy)
            Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                Text("Stays on this device", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Usage history is excluded from Android backup and device transfer. Still has no account and does not upload analytics or usage history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(StillSpacing.xLarge))
    }

    if (deleteBackupDialog) {
        AlertDialog(
            onDismissRequest = { deleteBackupDialog = false },
            icon = { StoredDataIcon(StillIcons.Delete) },
            title = { Text("Delete the safety backup?") },
            text = {
                Text(
                    "This permanently deletes the previous ${summary?.let { Formatter.formatShortFileSize(context, it.backupSizeBytes) } ?: ""} " +
                        "archive. Your compact history stays intact, but Restore backup will no longer be available.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteBackupDialog = false
                        onDeleteArchiveBackup()
                    },
                ) {
                    Text("Delete backup", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteBackupDialog = false }) { Text("Cancel") } },
        )
    }
    when (archiveBackupDeleteState) {
        ArchiveBackupDeleteState.Deleted -> AlertDialog(
            onDismissRequest = onDismissArchiveBackupDeleteResult,
            icon = { StoredDataIcon(StillIcons.Storage) },
            title = { Text("Backup deleted") },
            text = { Text("The compact archive is unchanged. The previous-format backup and its restore option have been removed.") },
            confirmButton = { TextButton(onClick = onDismissArchiveBackupDeleteResult) { Text("Done") } },
        )
        is ArchiveBackupDeleteState.Error -> AlertDialog(
            onDismissRequest = onDismissArchiveBackupDeleteResult,
            icon = { StoredDataIcon(StillIcons.Error) },
            title = { Text("Backup wasn’t deleted") },
            text = { Text(archiveBackupDeleteState.message) },
            confirmButton = { TextButton(onClick = onDismissArchiveBackupDeleteResult) { Text("Close") } },
        )
        ArchiveBackupDeleteState.Idle, ArchiveBackupDeleteState.Deleting -> Unit
    }
}

@Composable
private fun StoredDataFact(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StillSpacing.large, vertical = StillSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StoredDataItem(title: String, @DrawableRes icon: Int, body: String) {
    Row(
        modifier = Modifier.padding(StillSpacing.large),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
    ) {
        StoredDataIcon(icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StillSpacing.small),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCoverage(summary: StoredDataSummary): String {
    val oldest = summary.oldestDate ?: return "No saved history"
    val newest = summary.newestDate ?: return "No saved history"
    if (oldest == newest) return oldest.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    val startPattern = if (oldest.year == newest.year) "MMM d" else "MMM d, yyyy"
    val endPattern = "MMM d, yyyy"
    return "${oldest.format(DateTimeFormatter.ofPattern(startPattern, Locale.getDefault()))} – " +
        newest.format(DateTimeFormatter.ofPattern(endPattern, Locale.getDefault()))
}

private fun formatLastUpdated(timestampMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val updated = Instant.ofEpochMilli(timestampMillis).atZone(zone)
    val today = LocalDate.now(zone)
    val day = when (updated.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> updated.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
    return "$day, ${updated.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))}"
}

@Composable
private fun StoredDataIcon(@DrawableRes icon: Int) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun WidgetSettingsScreen(
    settings: UserSettings,
    widgetPreviewDay: DailyUsage,
    onWidgetColorChange: (String?) -> Unit,
    onWidgetThemeChange: (WidgetAppearance) -> Unit,
    onWidgetLabelChange: (WidgetLabel) -> Unit,
    onWidgetFontSizeChange: (WidgetFontSize) -> Unit,
    onWidgetFontStyleChange: (WidgetFontStyle) -> Unit,
    onWidgetShowRefreshChange: (Boolean) -> Unit,
    onWidgetCornerRadiusChange: (Int) -> Unit,
    onWidgetBackgroundOpacityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var colorDialog by remember { mutableStateOf(false) }
    val savedCornerRadius = if (settings.widgetCornerRadiusDp == WIDGET_PILL_RADIUS) {
        36f
    } else {
        settings.widgetCornerRadiusDp.toFloat()
    }
    var previewCornerRadius by remember(savedCornerRadius) { mutableFloatStateOf(savedCornerRadius) }
    var previewBackgroundOpacity by remember(settings.widgetBackgroundOpacityPercent) {
        mutableFloatStateOf(settings.widgetBackgroundOpacityPercent.toFloat())
    }
    val previewSettings = settings.copy(
        widgetCornerRadiusDp = previewCornerRadius.toWidgetCornerRadius(),
        widgetBackgroundOpacityPercent = previewBackgroundOpacity.toInt(),
    )

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StillSpacing.large),
    ) {
        SectionTitle("Preview")
        WidgetPreview(previewSettings, widgetPreviewDay.total)
        SectionTitle("Background")
        TonalPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                DirectChoice(
                    title = "Color",
                    options = listOf(
                        DirectChoiceOption("Theme", settings.widgetColor == null) {
                            onWidgetColorChange(null)
                        },
                        DirectChoiceOption("Custom", settings.widgetColor != null) {
                            colorDialog = true
                        },
                    ),
                )
                if (settings.widgetColor == null) {
                    DirectChoice(
                        title = "Theme",
                        options = WidgetAppearance.entries.map { appearance ->
                            DirectChoiceOption(
                                label = appearance.shortDisplayName,
                                selected = settings.widgetAppearance == appearance,
                                onClick = { onWidgetThemeChange(appearance) },
                            )
                        },
                    )
                } else {
                    WidgetCustomColorRow(
                        color = settings.widgetColor,
                        onClick = { colorDialog = true },
                    )
                }
                Hairline()
                WidgetValueSlider(
                    title = "Corner radius",
                    value = previewCornerRadius,
                    range = 0f..36f,
                    steps = 8,
                    valueLabel = { if (it >= 36f) "Pill" else "${it.toInt()} dp" },
                    onValueChange = { previewCornerRadius = it },
                    onValueChangeFinished = {
                        onWidgetCornerRadiusChange(previewCornerRadius.toWidgetCornerRadius())
                    },
                )
                Hairline()
                WidgetValueSlider(
                    title = "Background opacity",
                    value = previewBackgroundOpacity,
                    range = 20f..100f,
                    steps = 7,
                    valueLabel = { "${it.toInt()}%" },
                    onValueChange = { previewBackgroundOpacity = it },
                    onValueChangeFinished = {
                        onWidgetBackgroundOpacityChange(previewBackgroundOpacity.toInt())
                    },
                )
            }
        }

        SectionTitle("Content")
        TonalPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                DirectChoice(
                    title = "Title",
                    options = WidgetLabel.entries.map { label ->
                        DirectChoiceOption(
                            label = label.directDisplayName,
                            selected = settings.widgetLabel == label,
                            onClick = { onWidgetLabelChange(label) },
                        )
                    },
                )
                Hairline()
                DirectChoice(
                    title = "Text size",
                    options = WidgetFontSize.entries.map { size ->
                        DirectChoiceOption(
                            label = size.displayName,
                            selected = settings.widgetFontSize == size,
                            onClick = { onWidgetFontSizeChange(size) },
                        )
                    },
                )
                Hairline()
                DirectChoice(
                    title = "Weight",
                    options = WidgetFontStyle.entries.map { style ->
                        DirectChoiceOption(
                            label = style.displayName,
                            selected = settings.widgetFontStyle == style,
                            onClick = { onWidgetFontStyleChange(style) },
                        )
                    },
                )
                Hairline()
                SettingSwitch(
                    title = "Show refresh button",
                    supporting = null,
                    checked = settings.widgetShowRefresh,
                    enabled = true,
                    onCheckedChange = onWidgetShowRefreshChange,
                )
            }
        }
        Spacer(Modifier.height(StillSpacing.large))
    }

    if (colorDialog) {
        WidgetColorDialog(
            current = settings.widgetColor,
            onApply = onWidgetColorChange,
            onDismiss = { colorDialog = false },
        )
    }
}

@Composable
fun WidgetSelectorScreen(
    settings: UserSettings,
    previewDay: DailyUsage,
    onScreenTimeClick: () -> Unit,
    onDaylineClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StillSpacing.large),
    ) {
        Text(
            text = "Choose a widget to customize.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = StillSpacing.large, bottom = StillSpacing.small),
        )
        WidgetSelectorItem(title = "Screen time", onClick = onScreenTimeClick) {
            WidgetPreview(settings, previewDay.total)
        }
        Spacer(Modifier.height(StillSpacing.medium))
        WidgetSelectorItem(title = "Dayline", onClick = onDaylineClick) {
            DaylineWidgetPreview(settings, previewDay)
        }
        Spacer(Modifier.height(StillSpacing.xLarge))
    }
}

@Composable
private fun WidgetSelectorItem(
    title: String,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = StillSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Customize",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                painter = painterResource(StillIcons.ChevronRight),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        preview()
    }
}

@Composable
fun DaylineWidgetSettingsScreen(
    settings: UserSettings,
    widgetPreviewDay: DailyUsage,
    onWidgetColorChange: (String?) -> Unit,
    onWidgetThemeChange: (WidgetAppearance) -> Unit,
    onWidgetLabelChange: (DaylineWidgetLabel) -> Unit,
    onWidgetShowRefreshChange: (Boolean) -> Unit,
    onWidgetCornerRadiusChange: (Int) -> Unit,
    onWidgetBackgroundOpacityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var colorDialog by remember { mutableStateOf(false) }
    val savedCornerRadius = if (settings.daylineWidgetCornerRadiusDp == WIDGET_PILL_RADIUS) {
        36f
    } else {
        settings.daylineWidgetCornerRadiusDp.toFloat()
    }
    var previewCornerRadius by remember(savedCornerRadius) { mutableFloatStateOf(savedCornerRadius) }
    var previewBackgroundOpacity by remember(settings.daylineWidgetBackgroundOpacityPercent) {
        mutableFloatStateOf(settings.daylineWidgetBackgroundOpacityPercent.toFloat())
    }
    val previewSettings = settings.copy(
        daylineWidgetCornerRadiusDp = previewCornerRadius.toWidgetCornerRadius(),
        daylineWidgetBackgroundOpacityPercent = previewBackgroundOpacity.toInt(),
    )

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StillSpacing.large),
    ) {
        SectionTitle("Preview")
        DaylineWidgetPreview(previewSettings, widgetPreviewDay)
        SectionTitle("Background")
        TonalPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                DirectChoice(
                    title = "Color",
                    options = listOf(
                        DirectChoiceOption("Theme", settings.daylineWidgetColor == null) {
                            onWidgetColorChange(null)
                        },
                        DirectChoiceOption("Custom", settings.daylineWidgetColor != null) {
                            colorDialog = true
                        },
                    ),
                )
                if (settings.daylineWidgetColor == null) {
                    DirectChoice(
                        title = "Theme",
                        options = WidgetAppearance.entries.map { appearance ->
                            DirectChoiceOption(
                                label = appearance.shortDisplayName,
                                selected = settings.daylineWidgetAppearance == appearance,
                                onClick = { onWidgetThemeChange(appearance) },
                            )
                        },
                    )
                } else {
                    WidgetCustomColorRow(
                        color = settings.daylineWidgetColor,
                        onClick = { colorDialog = true },
                    )
                }
                Hairline()
                WidgetValueSlider(
                    title = "Corner radius",
                    value = previewCornerRadius,
                    range = 0f..36f,
                    steps = 8,
                    valueLabel = { if (it >= 36f) "Pill" else "${it.toInt()} dp" },
                    onValueChange = { previewCornerRadius = it },
                    onValueChangeFinished = {
                        onWidgetCornerRadiusChange(previewCornerRadius.toWidgetCornerRadius())
                    },
                )
                Hairline()
                WidgetValueSlider(
                    title = "Background opacity",
                    value = previewBackgroundOpacity,
                    range = 20f..100f,
                    steps = 7,
                    valueLabel = { "${it.toInt()}%" },
                    onValueChange = { previewBackgroundOpacity = it },
                    onValueChangeFinished = {
                        onWidgetBackgroundOpacityChange(previewBackgroundOpacity.toInt())
                    },
                )
            }
        }

        SectionTitle("Content")
        TonalPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                DirectChoice(
                    title = "Title",
                    options = DaylineWidgetLabel.entries.map { label ->
                        DirectChoiceOption(
                            label = label.directDisplayName,
                            selected = settings.daylineWidgetLabel == label,
                            onClick = { onWidgetLabelChange(label) },
                        )
                    },
                )
                Hairline()
                SettingSwitch(
                    title = "Show refresh button",
                    supporting = null,
                    checked = settings.daylineWidgetShowRefresh,
                    enabled = true,
                    onCheckedChange = onWidgetShowRefreshChange,
                )
            }
        }
        Spacer(Modifier.height(StillSpacing.large))
    }

    if (colorDialog) {
        WidgetColorDialog(
            current = settings.daylineWidgetColor,
            onApply = onWidgetColorChange,
            onDismiss = { colorDialog = false },
        )
    }
}


@Composable
private fun WidgetValueSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(valueLabel(value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                activeTickColor = MaterialTheme.colorScheme.onPrimary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

private fun Float.toWidgetCornerRadius(): Int =
    if (this >= 36f) WIDGET_PILL_RADIUS else toInt()

private data class DirectChoiceOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectChoice(title: String, options: List<DirectChoiceOption>) {
    Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.selected,
                    onClick = option.onClick,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        activeBorderColor = MaterialTheme.colorScheme.outline,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    label = { Text(option.label, maxLines = 1) },
                )
            }
        }
    }
}

private val WidgetAppearance.displayName: String
    get() = when (this) {
        WidgetAppearance.System -> "Follow system"
        WidgetAppearance.Light -> "Light"
        WidgetAppearance.Dark -> "Dark"
    }

private val WidgetAppearance.shortDisplayName: String
    get() = when (this) {
        WidgetAppearance.System -> "System"
        WidgetAppearance.Light -> "Light"
        WidgetAppearance.Dark -> "Dark"
    }

private val ThemePreference.displayName: String
    get() = when (this) {
        ThemePreference.System -> "Use system"
        ThemePreference.Light -> "Light"
        ThemePreference.Dark -> "Dark"
        ThemePreference.Wallpaper -> "Wallpaper"
    }

private val WidgetLabel.displayName: String
    get() = when (this) {
        WidgetLabel.ScreenTime -> "Screen time"
        WidgetLabel.Today -> "Today"
        WidgetLabel.Hidden -> "Hidden"
    }

private val WidgetLabel.directDisplayName: String
    get() = when (this) {
        WidgetLabel.ScreenTime -> "Screen time"
        WidgetLabel.Today -> "Today"
        WidgetLabel.Hidden -> "None"
    }

private val DaylineWidgetLabel.directDisplayName: String
    get() = when (this) {
        DaylineWidgetLabel.Dayline -> "Dayline"
        DaylineWidgetLabel.Today -> "Today"
        DaylineWidgetLabel.Hidden -> "None"
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

private fun displayWidgetColor(color: String?): String = when (color) {
    null -> "Custom color"
    "#FFFFFF" -> "White"
    "#000000" -> "Black"
    else -> color
}

@Composable
private fun WidgetPreview(settings: UserSettings, duration: Duration) {
    val context = LocalContext.current
    val dark = when (settings.widgetAppearance) {
        WidgetAppearance.System -> isSystemInDarkTheme()
        WidgetAppearance.Light -> false
        WidgetAppearance.Dark -> true
    }
    val customColors = parseWidgetColor(settings.widgetColor)?.let(::widgetContrastColors)
    val systemColors = if (settings.widgetAppearance == WidgetAppearance.System) systemWidgetColors(context, dark) else null
    val background = customColors?.let { Color(it.background) }
        ?: systemColors?.let { Color(it.background) }
        ?: if (dark) Color(0xFF18201B) else Color(0xFFF1F5F1)
    val primary = customColors?.let { Color(it.foreground) }
        ?: systemColors?.let { Color(it.primary) }
        ?: if (dark) Color(0xFFE9F5EC) else Color(0xFF172019)
    val secondary = customColors?.let { Color(it.foreground) }
        ?: systemColors?.let { Color(it.secondary) }
        ?: if (dark) Color(0xFFAAB7AD) else Color(0xFF526057)
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
        append(", ${settings.widgetBackgroundOpacityPercent}% opacity")
        if (settings.widgetCornerRadiusDp == WIDGET_PILL_RADIUS) append(", pill corners")
        else append(", ${settings.widgetCornerRadiusDp} dp corners")
        if (settings.widgetShowRefresh) append(", refresh button shown")
    }

    WidgetPreviewLayout(
        background = background,
        backgroundOpacityPercent = settings.widgetBackgroundOpacityPercent,
        cornerRadiusDp = settings.widgetCornerRadiusDp,
        label = label,
        secondary = secondary,
        showRefresh = settings.widgetShowRefresh,
        description = description,
    ) {
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = duration.compactDuration(),
                color = primary,
                fontSize = settings.widgetFontSize.valueSp.sp,
                fontWeight = settings.widgetFontStyle.fontWeight,
            )
        }
    }
}

@Composable
private fun DaylineWidgetPreview(settings: UserSettings, day: DailyUsage) {
    val context = LocalContext.current
    val dark = when (settings.daylineWidgetAppearance) {
        WidgetAppearance.System -> isSystemInDarkTheme()
        WidgetAppearance.Light -> false
        WidgetAppearance.Dark -> true
    }
    val customColors = parseWidgetColor(settings.daylineWidgetColor)?.let(::widgetContrastColors)
    val systemColors = if (settings.daylineWidgetAppearance == WidgetAppearance.System) systemWidgetColors(context, dark) else null
    val background = customColors?.let { Color(it.background) }
        ?: systemColors?.let { Color(it.background) }
        ?: if (dark) Color(0xFF18201B) else Color(0xFFF1F5F1)
    val primary = customColors?.let { Color(it.foreground) }
        ?: systemColors?.let { Color(it.primary) }
        ?: if (dark) Color(0xFFE9F5EC) else Color(0xFF172019)
    val secondary = customColors?.let { Color(it.foreground) }
        ?: systemColors?.let { Color(it.secondary) }
        ?: if (dark) Color(0xFFAAB7AD) else Color(0xFF526057)
    val label = when (settings.daylineWidgetLabel) {
        DaylineWidgetLabel.Dayline -> "Dayline"
        DaylineWidgetLabel.Today -> "Today"
        DaylineWidgetLabel.Hidden -> null
    }
    val fullDayMillis = Duration.ofHours(24).toMillis().toFloat()
    val nowProgress = (Duration.between(day.rangeStart, day.rangeEnd).toMillis() / fullDayMillis).coerceIn(0f, 1f)

    WidgetPreviewLayout(
        background = background,
        backgroundOpacityPercent = settings.daylineWidgetBackgroundOpacityPercent,
        cornerRadiusDp = settings.daylineWidgetCornerRadiusDp,
        label = label,
        secondary = secondary,
        showRefresh = settings.daylineWidgetShowRefresh,
        description = "Dayline widget preview, ${day.total.compactDuration()} screen use today",
    ) {
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.CenterStart) {
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                val radius = 5.dp.toPx()
                val outline = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
                }
                clipPath(outline) {
                    drawRoundRect(secondary.copy(alpha = .14f), cornerRadius = CornerRadius(radius))
                    day.dayline.forEach { segment ->
                        val left = (Duration.between(day.rangeStart, segment.start).toMillis() / fullDayMillis)
                            .coerceIn(0f, 1f) * size.width
                        val right = (Duration.between(day.rangeStart, segment.end).toMillis() / fullDayMillis)
                            .coerceIn(0f, 1f) * size.width
                        if (right > left) {
                            drawRect(
                                color = if (segment.kind == DaylineKind.Active) primary else secondary.copy(alpha = .47f),
                                topLeft = Offset(left, 0f),
                                size = Size(right - left, size.height),
                            )
                        }
                    }
                    listOf(.25f, .5f, .75f).forEach { progress ->
                        drawLine(background.copy(alpha = .3f), Offset(size.width * progress, 0f), Offset(size.width * progress, size.height))
                    }
                    drawLine(primary, Offset(size.width * nowProgress, 0f), Offset(size.width * nowProgress, size.height), 2.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewLayout(
    background: Color,
    backgroundOpacityPercent: Int,
    cornerRadiusDp: Int,
    label: String?,
    secondary: Color,
    showRefresh: Boolean,
    description: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(
                if (cornerRadiusDp == WIDGET_PILL_RADIUS) RoundedCornerShape(percent = 50)
                else RoundedCornerShape(cornerRadiusDp.dp),
            )
            .background(background.copy(alpha = backgroundOpacityPercent / 100f))
            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            label?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = secondary)
            }
            content()
        }
        if (showRefresh) {
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
private fun WidgetColorDialog(
    current: String?,
    onApply: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialColor = parseWidgetColor(current) ?: 0xFF315A41.toInt()
    val initialHsv = remember(current) { FloatArray(3).also { AndroidColor.colorToHSV(initialColor, it) } }
    var hue by remember(current) { mutableStateOf(initialHsv[0]) }
    var saturation by remember(current) { mutableStateOf(initialHsv[1]) }
    var brightness by remember(current) { mutableStateOf(initialHsv[2]) }
    val chosenColor = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
    val chosenHex = chosenColor.toHexColor()

    fun selectColor(color: Int) {
        val hsv = FloatArray(3).also { AndroidColor.colorToHSV(color, it) }
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
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
                    "Pick a background. Still keeps the text readable automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                ) {
                    WidgetColorChoice(
                        label = "White",
                        color = Color.White,
                        isSelected = chosenHex == "#FFFFFF",
                        onClick = { selectColor(0xFFFFFFFF.toInt()) },
                        modifier = Modifier.weight(1f),
                    )
                    WidgetColorChoice(
                        label = "Black",
                        color = Color.Black,
                        isSelected = chosenHex == "#000000",
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
                    },
                )
                HuePicker(
                    hue = hue,
                    onChange = {
                        hue = it
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
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    ) {
                        Box(Modifier.fillMaxSize().background(Color(chosenColor)))
                    }
                    Column {
                        Text(chosenHex, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Custom background",
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
                    onApply(chosenHex)
                    onDismiss()
                },
            ) { Text("Apply") }
        },
    )
}

@Composable
private fun WidgetColorChoice(
    label: String,
    color: Color,
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
            Box(Modifier.fillMaxSize().background(color))
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
private fun WidgetCustomColorRow(color: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Custom color", style = MaterialTheme.typography.titleMedium)
            Text(
                displayWidgetColor(color),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun SettingRow(
    title: String,
    supporting: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painterResource(StillIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f),
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    supporting: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f),
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
