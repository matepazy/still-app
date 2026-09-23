package app.still.ui.onboarding

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.still.ui.theme.StillSpacing

/** Shared by first-run setup and the gate shown if access is revoked later. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsageAccessFlow(
    hasUsageAccess: () -> Boolean,
    usageSettingsIntent: () -> Intent,
    appInfoIntent: () -> Intent,
    installedFromApk: () -> Boolean,
    onGranted: () -> Unit,
    content: @Composable (openUsageSettings: () -> Unit) -> Unit,
) {
    var state by rememberSaveable { mutableStateOf(UsageSetupState.Explanation) }
    val usageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (state != UsageSetupState.WaitingForUsageSettings) return@rememberLauncherForActivityResult
        state = state.afterUsageSettings(hasUsageAccess())
        if (state == UsageSetupState.Granted) onGranted()
    }
    val appInfoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (state != UsageSetupState.WaitingForAppInfo) return@rememberLauncherForActivityResult
        state = state.afterAppInfo(hasUsageAccess())
        when (state) {
            UsageSetupState.Granted -> onGranted()
            UsageSetupState.WaitingForUsageSettings -> usageLauncher.launch(usageSettingsIntent().forActivityResult())
            else -> Unit
        }
    }
    val openUsageSettings = {
        state = UsageSetupState.WaitingForUsageSettings
        usageLauncher.launch(usageSettingsIntent().forActivityResult())
    }

    // MainActivity refreshes permission on every resume. This also covers a grant made
    // outside our Settings trip, while results decide whether recovery is needed.
    LaunchedEffect(state) {
        if (state != UsageSetupState.Granted && hasUsageAccess()) {
            state = UsageSetupState.Granted
            onGranted()
        }
    }

    content(openUsageSettings)

    if (state == UsageSetupState.Recovery) {
        ModalBottomSheet(onDismissRequest = { state = state.dismissRecovery() }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp).padding(bottom = StillSpacing.large),
                verticalArrangement = Arrangement.spacedBy(StillSpacing.medium),
            ) {
                val supportsRestrictedSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                Text(
                    if (supportsRestrictedSettings) "Android blocked the setting?" else "Screen-time access is still off",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    recoveryExplanation(supportsRestrictedSettings, supportsRestrictedSettings && installedFromApk()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (supportsRestrictedSettings) {
                    listOf(
                        "1. Open Still app info",
                        "2. Tap ⋮",
                        "3. Choose Allow restricted settings",
                        "4. Return to Still",
                    ).forEach { step -> Text(step, style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(StillSpacing.small))
                    Button(
                        onClick = {
                            state = UsageSetupState.WaitingForAppInfo
                            appInfoLauncher.launch(appInfoIntent().forActivityResult())
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Open Still app info") }
                }
                TextButton(onClick = openUsageSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Try Usage Access again")
                }
                TextButton(
                    onClick = { state = state.dismissRecovery() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancel") }
            }
        }
    }
}

/** UsagePermissionManager also supports application-context launches, which need NEW_TASK.
 * Activity-result launches must stay in Still's task so the callback means Settings exited. */
private fun Intent.forActivityResult(): Intent = apply { removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
