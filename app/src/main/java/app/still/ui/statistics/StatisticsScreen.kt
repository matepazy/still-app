package app.still.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.domain.model.StatisticsRange
import app.still.ui.components.StillIcons
import app.still.ui.components.TonalPanel
import app.still.ui.components.compactDuration
import app.still.ui.theme.StillSpacing
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsTopBar(onSettings: () -> Unit) {
    TopAppBar(title = { Text("Statistics") }, actions = {
        IconButton(onClick = onSettings) {
            Icon(painterResource(StillIcons.Settings), contentDescription = "Settings")
        }
    })
}

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, onCompare: (StatisticsRange) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = StillSpacing.large)) {
        Spacer(Modifier.height(StillSpacing.large))
        StatisticsPeriodSelector(period, viewModel::select, viewModel::selectCustom)
        Spacer(Modifier.height(StillSpacing.large))
        Text(range.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(StillSpacing.medium))
        when (val value = state) {
            StatisticsState.Loading -> CircularProgressIndicator()
            is StatisticsState.Error -> Text(value.message)
            is StatisticsState.Ready -> {
                val summary = value.summary
                Text(summary.dailyAverage?.compactDuration() ?: "—", style = MaterialTheme.typography.displayLarge)
                Text("Daily average", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(StillSpacing.small))
                Text(summary.change?.let { change ->
                    val direction = if (change.isNegative) "lower" else if (change.isZero) "the same" else "higher"
                    if (change.isZero) "Same as previous period" else "${change.abs().compactDuration()} $direction than previous period"
                } ?: "Previous period unavailable", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(StillSpacing.xLarge))
                StatisticsChart(summary, period)
                Spacer(Modifier.height(StillSpacing.section))
                Text("Screen time", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(StillSpacing.medium))
                Metric("Total", summary.total?.compactDuration())
                Metric("Median day", summary.median?.compactDuration())
                Metric("Highest day", summary.highest?.let { "${it.date.format(DateTimeFormatter.ofPattern("MMM d"))} · ${it.screenTime?.compactDuration()}" })
                Metric("Lowest day", summary.lowest?.let { "${it.date.format(DateTimeFormatter.ofPattern("MMM d"))} · ${it.screenTime?.compactDuration()}" })
                Spacer(Modifier.height(StillSpacing.section))
                Text("Usage rhythm", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(StillSpacing.medium))
                Metric("Check-ins per detailed day", summary.checkInsAverage?.let { "%.1f".format(it) })
                Metric("Quick checks per detailed day", summary.quickChecksAverage?.let { "%.1f".format(it) })
                Metric("Average session", summary.sessionAverage?.compactDuration())
                Metric("Longest session", summary.longestSession?.compactDuration())
                Metric("Average longest break", summary.longestBreakAverage?.compactDuration())
                Metric("Average first use", summary.firstUseAverageMinute?.let(::clockMinute))
                Metric("Average last use", summary.lastUseAverageMinute?.let(::clockMinute))
                Metric("Most active hour", summary.mostActiveHour?.let { "${it.toString().padStart(2, '0')}:00–${((it + 1) % 24).toString().padStart(2, '0')}:00" })
                Metric("Quick checks / check-ins", summary.quickCheckShare?.let { "${(it * 100).toInt()}%" })
                Metric("App switches per detailed day", summary.appSwitchesAverage?.let { "%.1f".format(it) })
                summary.hourlyAverageMillis?.let { hours ->
                    Text(if (summary.days.count { it.hourlyMillis != null } > 1) "Typical day" else "Hourly activity", style = MaterialTheme.typography.titleMedium)
                    TypicalDay(hours)
                }
                Spacer(Modifier.height(StillSpacing.section))
                Text("Patterns", style = MaterialTheme.typography.headlineSmall)
                Metric("Weekday average", summary.weekdayAverage?.compactDuration())
                Metric("Weekend average", summary.weekendAverage?.compactDuration())
                Metric("Most active weekday", summary.mostActiveWeekday?.name?.lowercase()?.replaceFirstChar { it.uppercase() })
                Metric("Daily variation", summary.dailyVariability?.compactDuration())
                Spacer(Modifier.height(StillSpacing.section))
                Text("Apps", style = MaterialTheme.typography.headlineSmall)
                summary.topApps.take(5).forEach { Metric(it.label, it.total.compactDuration() + (it.change?.let { change -> " · ${signed(change)}" } ?: "")) }
                Spacer(Modifier.height(StillSpacing.section))
                Text("Categories", style = MaterialTheme.typography.headlineSmall)
                summary.categories.forEach { Metric(it.category.displayName, "${it.total.compactDuration()} · ${(it.share * 100).toInt()}%" + (it.change?.let { change -> " · ${signed(change)}" } ?: "")) }
            }
        }
        Spacer(Modifier.height(StillSpacing.section))
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(StillSpacing.large)) {
            Column {
                Text("Compare", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(StillSpacing.small))
                Text("Compare your usage patterns with someone nearby. Nothing leaves either phone.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(StillSpacing.medium))
                Button(onClick = { onCompare(range) }) { Text("Compare with a friend") }
            }
        }
        Spacer(Modifier.height(StillSpacing.section))
    }
}

private fun signed(duration: java.time.Duration): String = (if (duration.isNegative) "−" else "+") + duration.abs().compactDuration()
private fun clockMinute(minute: Int): String = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

@Composable
private fun Metric(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TypicalDay(hours: List<Long>) {
    val max = hours.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Text(hours.joinToString("") { value -> "▁▂▃▄▅▆▇█"[((value.toDouble() / max) * 7).toInt().coerceIn(0, 7)].toString() }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    Text("12am         6am         12pm         6pm", style = MaterialTheme.typography.labelSmall)
}

fun StatisticsRange.label(): String {
    val format = DateTimeFormatter.ofPattern("MMM d")
    return if (start == endInclusive) start.format(format) else "${start.format(format)} – ${endInclusive.format(format)}"
}
