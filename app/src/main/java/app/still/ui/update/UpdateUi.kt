package app.still.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.ui.MainViewModel
import app.still.ui.components.TonalPanel
import app.still.ui.theme.StillSpacing
import app.still.update.UpdateState
import app.still.ui.components.StillIcons

@Composable
fun VersionOptInDialog(onDecision: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(painterResource(StillIcons.Download), contentDescription = null) },
        title = { Text("Automatic updates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                Text("Would you like Still to automatically check for updates via the GitHub API?")
                Text(
                    "Still does not send usage data. A version check only generates the standard network information GitHub receives during an API request, such as your IP address.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onDecision(true) }) { Text("Stay up to date") } },
        dismissButton = {
            OutlinedButton(onClick = { onDecision(false) }) {
                Text("I don't want the latest version")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDetailsSheet(
    update: UpdateState.UpdateAvailable,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var showPermissionExplanation by remember { mutableStateOf(false) }

    LaunchedEffect(updateState) {
        if (updateState is UpdateState.Completed) {
            val file = (updateState as UpdateState.Completed).apkFile
            if (viewModel.canInstallPackages()) viewModel.installApk(file)
            else showPermissionExplanation = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (updateState !is UpdateState.Downloading) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = StillSpacing.large)
                .padding(bottom = StillSpacing.large),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                Icon(painterResource(StillIcons.Update), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Update to ${update.version}", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(StillSpacing.large))

            when (val current = updateState) {
                is UpdateState.Downloading -> {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            progress = { current.progress },
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(StillSpacing.medium))
                        Text("Downloading update… ${(current.progress * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(StillSpacing.small))
                        LinearProgressIndicator(progress = { current.progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
                is UpdateState.Completed -> {
                    StatusMessage("Download complete", "Ready to install the new version.")
                }
                is UpdateState.Error -> {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(StillIcons.Error), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(StillSpacing.small))
                        Text("Update failed", style = MaterialTheme.typography.titleMedium)
                        Text(current.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
                else -> {
                    Text("Release notes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(StillSpacing.small))
                    TonalPanel(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        Text(
                            update.notes,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(StillSpacing.large))
            if (updateState !is UpdateState.Downloading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
                    when (val current = updateState) {
                        is UpdateState.Completed -> Button(
                            onClick = {
                                if (viewModel.canInstallPackages()) viewModel.installApk(current.apkFile)
                                else showPermissionExplanation = true
                            },
                            modifier = Modifier.weight(1.25f),
                        ) { Text("Install") }
                        is UpdateState.Error -> Button(onClick = viewModel::resetUpdateState, modifier = Modifier.weight(1.25f)) { Text("Retry") }
                        else -> Button(
                            onClick = { viewModel.startApkDownload(update.downloadUrl) },
                            modifier = Modifier.weight(1.25f),
                        ) { Text("Download & install") }
                    }
                }
            }
        }
    }

    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text("Installation permission required") },
            text = { Text("To update Still, allow it to install unknown apps in Android settings. You can return here and tap Install afterward.") },
            confirmButton = {
                Button(onClick = {
                    showPermissionExplanation = false
                    viewModel.requestInstallPermission()
                }) { Text("Go to settings") }
            },
            dismissButton = { OutlinedButton(onClick = { showPermissionExplanation = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusMessage(title: String, body: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(StillIcons.Download), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(StillSpacing.small))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
