package app.still.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import app.still.ui.components.LoadingSkeleton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.BuildConfig
import app.still.ui.BetaVersionsState
import app.still.ui.MainViewModel
import androidx.compose.material3.HorizontalDivider
import app.still.ui.components.StillDrawer
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
        Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
            Text("Manage beta", style = MaterialTheme.typography.titleLarge)
            Text("Installed: v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Choose a published beta or the latest stable release. Export a backup in Settings > Data before rolling back: older versions may not read newer data or backups.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (val current = state) {
                BetaVersionsState.Idle, BetaVersionsState.Loading -> {
                    Text("Loading available versions…", style = MaterialTheme.typography.bodyMedium)
                    repeat(3) {
                        Column(Modifier.fillMaxWidth().padding(vertical = StillSpacing.small),
                            verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                            LoadingSkeleton(Modifier.width(144.dp).height(24.dp))
                            LoadingSkeleton(Modifier.width(104.dp).height(16.dp))
                        }
                    }
                }
                is BetaVersionsState.Error -> {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::loadBetaVersions) { Text("Try again") }
                }
                is BetaVersionsState.Ready -> {
                    if (current.versions.none { !it.isBeta }) {
                        Text("No stable APK is available.", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (current.versions.none { it.isBeta }) {
                        Text("No beta APKs are available.", style = MaterialTheme.typography.bodyMedium)
                    }
                    current.versions.forEach { version ->
                        val installed = SemanticVersion.compare(version.version, BuildConfig.VERSION_NAME) == 0
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TextButton(
                            onClick = { onSelect(version) },
                            enabled = !installed,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = StillSpacing.small),
                                verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                                Text(version.version, style = MaterialTheme.typography.titleMedium)
                                Text(if (installed) "Installed" else if (version.isBeta) "Beta release" else "Latest stable release",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}
