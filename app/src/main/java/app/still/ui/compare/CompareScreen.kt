package app.still.ui.compare

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.domain.model.CompareSharing
import app.still.ui.components.StillIcons
import app.still.ui.statistics.StatisticsPeriodSelector
import app.still.ui.statistics.label
import app.still.ui.theme.StillSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareTopBar(onBack: () -> Unit) {
    TopAppBar(title = { Text("Compare") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(painterResource(StillIcons.Back), contentDescription = "Back") }
    })
}

@Composable
fun CompareScreen(viewModel: CompareViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.phase == ComparePhase.ScanFriend || state.phase == ComparePhase.ScanReply) {
        Column(modifier.fillMaxSize()) {
            Text("Scan a code", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(StillSpacing.large))
            Text("Point your camera at your friend's Still QR code.", modifier = Modifier.padding(horizontal = StillSpacing.large))
            Spacer(Modifier.height(StillSpacing.medium))
            CompareScanner(viewModel::onScanned)
        }
        return
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = StillSpacing.large)) {
        Spacer(Modifier.height(StillSpacing.large))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(StillSpacing.medium)) }
        when (state.phase) {
            ComparePhase.Start -> {
                Text("Compare screen time\nwith a friend", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(StillSpacing.medium))
                Text("Share a snapshot of your usage through a QR code. Works offline. Nothing is uploaded.")
                Spacer(Modifier.height(StillSpacing.section))
                Text("Time period", style = MaterialTheme.typography.titleMedium)
                Text(state.range.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(StillSpacing.medium))
                StatisticsPeriodSelector(state.period, viewModel::selectPeriod, viewModel::selectCustom)
                Spacer(Modifier.height(StillSpacing.section))
                Text("What to share", style = MaterialTheme.typography.titleMedium)
                ShareRow("Screen time", state.sharing.screenTime) { viewModel.setSharing(state.sharing.copy(screenTime = it)) }
                ShareRow("Usage patterns", state.sharing.patterns) { viewModel.setSharing(state.sharing.copy(patterns = it)) }
                ShareRow("App categories", state.sharing.categories) { viewModel.setSharing(state.sharing.copy(categories = it)) }
                ShareRow("Individual apps", state.sharing.apps) { viewModel.setSharing(state.sharing.copy(apps = it)) }
                Spacer(Modifier.height(StillSpacing.large))
                Button(onClick = viewModel::showOwnQr, enabled = !state.busy && state.sharing.flags() != 0, modifier = Modifier.fillMaxWidth()) { Text("Show my QR code") }
                Text("or", modifier = Modifier.align(Alignment.CenterHorizontally).padding(StillSpacing.small))
                OutlinedButton(onClick = viewModel::scanFriend, modifier = Modifier.fillMaxWidth()) { Text("Scan a friend's code") }
                if (state.busy) CircularProgressIndicator()
            }
            ComparePhase.OwnQr, ComparePhase.ReplyQr -> {
                Text(if (state.phase == ComparePhase.ReplyQr) "Reply code" else "Your QR code", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(StillSpacing.small))
                Text(if (state.phase == ComparePhase.ReplyQr) "Let your friend scan this reply so they can see the comparison." else "Let your friend scan this code with their Still app.")
                Spacer(Modifier.height(StillSpacing.large))
                state.code?.let { code ->
                    val image = remember(code) { CompareQr.bitmap(code).asImageBitmap() }
                    Image(image, contentDescription = "Still comparison QR code", modifier = Modifier.align(Alignment.CenterHorizontally).size(300.dp).background(Color.White).padding(8.dp))
                }
                Spacer(Modifier.height(StillSpacing.medium))
                Text(state.range.label(), modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(StillSpacing.medium))
                Text("This code contains", style = MaterialTheme.typography.titleSmall)
                if (state.sharing.screenTime) Text("• Screen time")
                if (state.sharing.patterns) Text("• Usage patterns")
                if (state.sharing.categories) Text("• App categories")
                if (state.sharing.apps) Text("• Individual app names")
                Text("Nothing is sent over the internet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(StillSpacing.large))
                if (state.phase == ComparePhase.OwnQr) Button(onClick = viewModel::scanReply, modifier = Modifier.fillMaxWidth()) { Text("Scan their reply") }
                if (state.phase == ComparePhase.ReplyQr && state.result != null) {
                    OutlinedButton(onClick = { viewModel.showResult() }, modifier = Modifier.fillMaxWidth()) { Text("View comparison") }
                }
                TextButton(onClick = viewModel::startOver) { Text("Start over") }
            }
            ComparePhase.Result -> {
                state.result?.let { CompareResultView(it) }
                Spacer(Modifier.height(StillSpacing.large))
                if (state.result?.you?.replyTo != null) Button(onClick = viewModel::showReplyQr, modifier = Modifier.fillMaxWidth()) { Text("Let them see the comparison") }
                TextButton(onClick = viewModel::startOver) { Text("Start over") }
            }
            else -> Unit
        }
        Spacer(Modifier.height(StillSpacing.section))
    }
}

@Composable
private fun ShareRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Checkbox(checked = checked, onCheckedChange = onChecked)
    }
}
