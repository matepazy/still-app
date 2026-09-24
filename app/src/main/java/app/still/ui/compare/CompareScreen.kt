package app.still.ui.compare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import app.still.ui.components.StillIcons
import app.still.ui.components.DaySelector
import app.still.domain.model.StatisticsPeriod
import app.still.ui.statistics.StatisticsPeriodSelector
import app.still.ui.statistics.label
import app.still.ui.theme.StillSpacing
import app.still.domain.model.AppInfo
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareTopBar(onBack: () -> Unit) {
    TopAppBar(title = { Text("Compare") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(painterResource(StillIcons.Back), contentDescription = "Back") }
    })
}

@Composable
fun CompareScreen(viewModel: CompareViewModel, availableDates: List<LocalDate>,
    localApps: Map<String, AppInfo>, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.phase != ComparePhase.Start && state.phase != ComparePhase.Result) { viewModel.back() }
    if (state.phase == ComparePhase.ScanFriend || state.phase == ComparePhase.ScanReply) {
        CompareScanner(viewModel::onScanned, modifier.fillMaxSize())
        return
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = StillSpacing.large)) {
        Spacer(Modifier.height(StillSpacing.large))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(StillSpacing.medium)) }
        when (state.phase) {
            ComparePhase.Start -> {
                Text("Compare with a friend", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(StillSpacing.xLarge))
                Text("Time period", style = MaterialTheme.typography.titleLarge)
                StatisticsPeriodSelector(state.period, state.range, availableDates, viewModel::selectPeriod, viewModel::selectCustom)
                if (state.period == StatisticsPeriod.Day) {
                    DaySelector(state.range.start, availableDates, viewModel::selectDay,
                        modifier = Modifier.padding(top = StillSpacing.small), showTodayLabel = false)
                } else {
                    Text(state.range.label(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = StillSpacing.medium))
                }
                Spacer(Modifier.height(StillSpacing.section))
                Text("Include in your code", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(StillSpacing.medium))
                Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                        ShareChip("Screen time", state.sharing.screenTime, Modifier.weight(1f)) { viewModel.setSharing(state.sharing.copy(screenTime = it)) }
                        ShareChip("Patterns", state.sharing.patterns, Modifier.weight(1f)) { viewModel.setSharing(state.sharing.copy(patterns = it)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                        ShareChip("Categories", state.sharing.categories, Modifier.weight(1f)) { viewModel.setSharing(state.sharing.copy(categories = it)) }
                        ShareChip("Apps", state.sharing.apps, Modifier.weight(1f)) { viewModel.setSharing(state.sharing.copy(apps = it)) }
                    }
                }
                Spacer(Modifier.height(StillSpacing.xLarge))
                Button(onClick = viewModel::showOwnQr, enabled = !state.busy && state.sharing.flags() != 0,
                    modifier = Modifier.fillMaxWidth().height(76.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                        Icon(painterResource(StillIcons.QrCode), contentDescription = null, modifier = Modifier.size(28.dp))
                        Column {
                            Text("Show my code", style = MaterialTheme.typography.titleMedium)
                            Text("Friend scans this phone", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(StillSpacing.small))
                OutlinedButton(onClick = viewModel::scanFriend,
                    modifier = Modifier.fillMaxWidth().height(76.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                        Icon(painterResource(StillIcons.Scan), contentDescription = null, modifier = Modifier.size(28.dp))
                        Column {
                            Text("Scan friend's code", style = MaterialTheme.typography.titleMedium)
                            Text("Use your camera", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("QR only · Nothing is uploaded", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = StillSpacing.medium),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.busy) CircularProgressIndicator()
            }
            ComparePhase.OwnQr, ComparePhase.ReplyQr -> {
                Text(if (state.phase == ComparePhase.ReplyQr) "Show your reply" else "Show your code", style = MaterialTheme.typography.headlineMedium)
                Text("Ask your friend to scan this", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(StillSpacing.large))
                state.code?.let { code ->
                    val image = remember(code) { CompareQr.bitmap(code).asImageBitmap() }
                    Image(image, contentDescription = "Still comparison QR code", modifier = Modifier.align(Alignment.CenterHorizontally).size(300.dp).background(Color.White).padding(8.dp))
                }
                Spacer(Modifier.height(StillSpacing.medium))
                Text(state.range.label(), modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(StillSpacing.medium))
                Spacer(Modifier.height(StillSpacing.medium))
                if (state.phase == ComparePhase.OwnQr) Button(onClick = viewModel::scanReply, modifier = Modifier.fillMaxWidth()) { Text("Scan their reply") }
                if (state.phase == ComparePhase.ReplyQr && state.result != null) {
                    OutlinedButton(onClick = { viewModel.showResult() }, modifier = Modifier.fillMaxWidth()) { Text("View comparison") }
                }
                TextButton(onClick = viewModel::startOver) { Text("Start over") }
            }
            ComparePhase.Result -> {
                state.result?.let { CompareResultView(it, localApps) }
                Spacer(Modifier.height(StillSpacing.large))
                if (state.result?.you?.replyTo != null) Button(onClick = viewModel::showReplyQr, modifier = Modifier.fillMaxWidth()) { Text("Show reply code") }
                TextButton(onClick = viewModel::startOver) { Text("Start over") }
            }
            else -> Unit
        }
        Spacer(Modifier.height(StillSpacing.section))
    }
}

@Composable
private fun ShareChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onSelected: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onSelected(!selected) },
        label = { Text(label) },
        leadingIcon = if (selected) {{
            Icon(painterResource(StillIcons.Check), contentDescription = null, modifier = Modifier.size(18.dp))
        }} else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier.height(48.dp),
    )
}
