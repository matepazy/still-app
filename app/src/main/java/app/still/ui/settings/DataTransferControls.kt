package app.still.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.still.ui.DataTransferState
import app.still.ui.components.StillIcons
import app.still.ui.components.TonalPanel
import app.still.ui.theme.StillSpacing
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DataTransferControls(
    state: DataTransferState,
    onPrepareExport: (CharArray) -> Unit,
    onExportDestination: (Uri?) -> Unit,
    onImport: (Uri, CharArray) -> Unit,
    onDismissResult: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }
    // Secrets never enter saved instance state; rotation restarts PIN creation.
    var pin by remember(dialog) { mutableStateOf("") }
    var firstPin by remember(dialog) { mutableStateOf("") }
    var confirming by remember(dialog) { mutableStateOf(false) }
    var mismatch by remember(dialog) { mutableStateOf(false) }
    var reveal by remember(dialog) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), onExportDestination)
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { importUri = uri.toString(); dialog = "import" }
    }
    val busy = state is DataTransferState.Working || state == DataTransferState.ChoosingDestination
    SettingRow("Export data", if (state is DataTransferState.Working && !state.importing) "Encrypting your history…" else "Save an encrypted copy of your history",
        enabled = !busy, onClick = { dialog = "export" })
    Hairline()
    SettingRow("Import data", if (state is DataTransferState.Working && state.importing) "Restoring your history…" else "Restore from a Still export",
        enabled = !busy, onClick = { importPicker.launch(arrayOf("*/*")) })
    fun close() { pin = ""; firstPin = ""; dialog = null; importUri = null }
    if (dialog != null) {
        val exporting = dialog == "export"
        fun submit() {
            if (pin.length != 6) return
            if (exporting && !confirming) {
                firstPin = pin; pin = ""; confirming = true; reveal = false
            } else if (exporting && pin != firstPin) {
                mismatch = true
            } else {
                val secret = pin.toCharArray()
                val uri = importUri
                close()
                if (exporting) {
                    onPrepareExport(secret)
                    exportPicker.launch("Still-history-${LocalDate.now()}.stilldb")
                } else if (uri != null) onImport(Uri.parse(uri), secret) else secret.fill('\u0000')
            }
        }
        ModalBottomSheet(onDismissRequest = ::close, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = StillSpacing.large).padding(bottom = StillSpacing.large),
                verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                TransferHeading(if (exporting) StillIcons.Privacy else StillIcons.History,
                    if (!exporting) "Import your history" else if (confirming) "Confirm your PIN" else "Encrypt your export",
                    if (!exporting) "Enter the six-digit PIN you created for this file."
                    else if (confirming) "Enter the same six digits again."
                    else "Create a six-digit PIN to protect your saved history.")
                if (!exporting) TonalPanel(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("This replaces your saved history", style = MaterialTheme.typography.titleSmall)
                        Text("Your current history and safety backup will be replaced. App settings stay the same.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (confirming) "Confirm PIN" else "Six-digit PIN", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { reveal = !reveal }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (reveal) "Hide" else "Show") }
                    }
                    TextField(value = pin,
                        onValueChange = { value -> if (value.length <= 6 && value.all { it in '0'..'9' }) { pin = value; mismatch = false } },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics {
                            contentDescription = if (confirming) "Confirm six-digit PIN" else "Six-digit PIN"
                        },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 8.sp),
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword,
                            imeAction = if (exporting && !confirming) ImeAction.Next else ImeAction.Done),
                        keyboardActions = KeyboardActions(onNext = { submit() }, onDone = { submit() }),
                        singleLine = true, isError = mismatch, shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            errorContainerColor = MaterialTheme.colorScheme.errorContainer,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent),
                        supportingText = if (mismatch) ({ Text("PINs don't match. Try those six digits again.") }) else null)
                }
                if (exporting) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(painterResource(StillIcons.Info), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Keep your PIN safe. Still cannot recover it. Six digits offer limited protection against determined guessing; keep the export file private.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = ::submit, enabled = pin.length == 6, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (!exporting) "Replace history and import" else if (confirming) "Choose where to save" else "Continue")
                    }
                    TextButton(onClick = { if (confirming) { confirming = false; firstPin = ""; pin = ""; mismatch = false } else close() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (confirming) "Change PIN" else "Cancel") }
                }
            }
        }
        LaunchedEffect(dialog, confirming) { focus.requestFocus() }
    }
    if (state is DataTransferState.Working) {
        var dismissed by remember(state) { mutableStateOf(false) }
        if (!dismissed) ModalBottomSheet(onDismissRequest = { dismissed = true }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = StillSpacing.large).padding(bottom = StillSpacing.xLarge), verticalArrangement = Arrangement.spacedBy(StillSpacing.large)) {
                TransferHeading(StillIcons.Storage, if (state.importing) "Restoring your history" else "Encrypting your history",
                    if (state.importing) "Checking your file before replacing any saved data." else "Preparing the complete database and safety backup.")
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
    if (state is DataTransferState.Finished) {
        val failed = state.title.endsWith("failed")
        ModalBottomSheet(onDismissRequest = onDismissResult, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = StillSpacing.large)
                .padding(bottom = StillSpacing.large), verticalArrangement = Arrangement.spacedBy(StillSpacing.large)) {
                TransferHeading(if (failed) StillIcons.Error else StillIcons.Check, state.title, state.message,
                    if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Column {
                    Button(onClick = {
                        onDismissResult()
                        if (failed) {
                            if (state.title.startsWith("Import")) importPicker.launch(arrayOf("*/*")) else dialog = "export"
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (failed) "Try again" else "Done") }
                    if (failed) TextButton(onClick = onDismissResult, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun TransferHeading(icon: Int, title: String, description: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
        Icon(painterResource(icon), null, Modifier.size(28.dp), tint = tint)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
