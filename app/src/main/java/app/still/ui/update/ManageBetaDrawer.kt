package app.still.ui.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.BuildConfig
import app.still.ui.BetaVersionsState
import app.still.ui.MainViewModel
import app.still.ui.components.LoadingSkeleton
import app.still.ui.components.StillDrawer
import app.still.ui.components.StillIcons
import app.still.ui.components.TonalPanel
import app.still.ui.theme.StillSpacing
import app.still.update.SemanticVersion
import app.still.update.UpdateState

@Composable
fun ManageBetaDrawer(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSelect: (UpdateState.UpdateAvailable) -> Unit,
) {
    val state by viewModel.betaVersions.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadBetaVersions() }
    StillDrawer(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.large)) {
            Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.xSmall)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Manage beta",
                        modifier = Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(StillIcons.Close), contentDescription = "Close")
                    }
                }
                Text(
                    "On this device: v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (val current = state) {
                BetaVersionsState.Idle, BetaVersionsState.Loading -> {
                    Text("Loading available versions…", style = MaterialTheme.typography.bodyMedium)
                    repeat(3) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = StillSpacing.small),
                            verticalArrangement = Arrangement.spacedBy(StillSpacing.small),
                        ) {
                            LoadingSkeleton(Modifier.width(144.dp).height(24.dp))
                            LoadingSkeleton(Modifier.width(104.dp).height(16.dp))
                        }
                    }
                }
                is BetaVersionsState.Error -> {
                    TonalPanel(color = MaterialTheme.colorScheme.errorContainer) {
                        Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                            Text(
                                "Couldn't load versions",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                current.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = viewModel::loadBetaVersions) { Text("Try again") }
                        }
                    }
                }
                is BetaVersionsState.Ready -> {
                    ReleaseGroup(
                        title = "Stable release",
                        description = "The latest regular release of Still.",
                        versions = current.versions.filter { !it.isBeta },
                        emptyMessage = "No stable release is available to download.",
                        onSelect = onSelect,
                    )
                    ReleaseGroup(
                        title = "Beta releases",
                        description = "Test versions. Bugs and data changes are possible.",
                        versions = current.versions.filter { it.isBeta },
                        emptyMessage = "No beta releases are available to download.",
                        onSelect = onSelect,
                    )
                }
            }
            TonalPanel(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                    Icon(
                        painterResource(StillIcons.Storage),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.xSmall)) {
                        Text("Back up before switching", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Export in Settings > Data. Older versions may not read newer data or backups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseGroup(
    title: String,
    description: String,
    versions: List<UpdateState.UpdateAvailable>,
    emptyMessage: String,
    onSelect: (UpdateState.UpdateAvailable) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
        Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.xSmall)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (versions.isEmpty()) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    versions.forEachIndexed { index, version ->
                        if (index > 0) HorizontalDivider(
                            modifier = Modifier.padding(horizontal = StillSpacing.medium),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        ReleaseRow(version, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseRow(version: UpdateState.UpdateAvailable, onSelect: (UpdateState.UpdateAvailable) -> Unit) {
    val installed = SemanticVersion.compare(version.version, BuildConfig.VERSION_NAME) == 0
    val older = SemanticVersion.compare(version.version, BuildConfig.VERSION_NAME) < 0
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (installed) Modifier.semantics(mergeDescendants = true) {} else Modifier.clickable(
                role = Role.Button,
                onClickLabel = "Review ${version.version}",
                onClick = { onSelect(version) },
            ))
            .heightIn(min = 72.dp)
            .padding(StillSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StillSpacing.xSmall)) {
            Text(version.version, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    installed -> "Installed on this device"
                    older -> "Older version · View release notes"
                    else -> "View release notes"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painterResource(if (installed) StillIcons.Check else StillIcons.ChevronRight),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
