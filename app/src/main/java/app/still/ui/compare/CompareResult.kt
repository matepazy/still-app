package app.still.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.still.domain.model.CompareResult
import app.still.ui.components.TonalPanel
import app.still.ui.components.compactDuration
import app.still.ui.statistics.label
import app.still.ui.theme.StillSpacing
import java.time.Duration

@Composable
fun CompareResultView(result: CompareResult) {
    val singleDay = result.you.range.days == 1L
    Text("Side by side", style = MaterialTheme.typography.headlineMedium)
    Text(result.you.range.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(StillSpacing.large))
    Row(horizontalArrangement = Arrangement.spacedBy(StillSpacing.large)) {
        LegendDot("You", MaterialTheme.colorScheme.primary)
        LegendDot("Friend", MaterialTheme.colorScheme.tertiary)
    }
    val youAverage = result.you.averageDailyScreenTimeMillis
    val friendAverage = result.friend.averageDailyScreenTimeMillis
    if (youAverage != null || friendAverage != null) {
        Spacer(Modifier.height(StillSpacing.large))
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(StillSpacing.large)) {
            Column {
                Text(if (singleDay) "Screen time" else "Daily average", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(StillSpacing.medium))
                PairedBars(youAverage, friendAverage, ::duration)
                if (youAverage != null && friendAverage != null) {
                    val difference = Duration.ofMillis(kotlin.math.abs(youAverage - friendAverage))
                    Text(when {
                        youAverage == friendAverage -> "The same ${if (singleDay) "screen time" else "daily average"}"
                        youAverage < friendAverage -> "${difference.compactDuration()} less${if (singleDay) "" else " per day"}"
                        else -> "${difference.compactDuration()} more${if (singleDay) "" else " per day"}"
                    }, modifier = Modifier.padding(top = StillSpacing.medium), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (!singleDay && (result.you.totalScreenTimeMillis != null || result.friend.totalScreenTimeMillis != null)) {
        ResultHeading("Total screen time")
        PairedBars(result.you.totalScreenTimeMillis, result.friend.totalScreenTimeMillis, ::duration)
    }
    if (!singleDay && (result.you.screenTimeDays != null || result.friend.screenTimeDays != null)) {
        Text("Based on ${result.you.screenTimeDays ?: "—"} of your days and ${result.friend.screenTimeDays ?: "—"} of your friend's days with data",
            modifier = Modifier.padding(top = StillSpacing.small), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val patterns = listOf(
        Triple("Check-ins", result.you.checkIns?.toLong(), result.friend.checkIns?.toLong()),
        Triple("Quick checks", result.you.quickChecks?.toLong(), result.friend.quickChecks?.toLong()),
    ).filter { it.second != null || it.third != null }
    if (patterns.isNotEmpty() || result.you.longestBreakMillis != null || result.friend.longestBreakMillis != null) {
        ResultHeading(if (singleDay) "That day's rhythm" else "Usage rhythm")
        patterns.forEach { (label, you, friend) ->
            Text(label, style = MaterialTheme.typography.titleMedium)
            PairedBars(you, friend) { it.toString() }
            Spacer(Modifier.height(StillSpacing.medium))
        }
        if (result.you.longestBreakMillis != null || result.friend.longestBreakMillis != null) {
            Text("Longest break", style = MaterialTheme.typography.titleMedium)
            PairedBars(result.you.longestBreakMillis, result.friend.longestBreakMillis, ::duration)
        }
    }
    val categories = (result.you.categories.orEmpty().map { it.name } + result.friend.categories.orEmpty().map { it.name }).distinct()
    if (categories.isNotEmpty()) {
        ResultHeading("By category")
        categories.forEach { name ->
            Text(name, style = MaterialTheme.typography.titleMedium)
            PairedBars(result.you.categories?.firstOrNull { it.name == name }?.millis,
                result.friend.categories?.firstOrNull { it.name == name }?.millis, ::duration)
            Spacer(Modifier.height(StillSpacing.medium))
        }
    }
    val apps = (result.you.apps.orEmpty().map { it.label } + result.friend.apps.orEmpty().map { it.label }).distinct()
    if (apps.isNotEmpty()) {
        ResultHeading("Individual apps")
        apps.forEach { name ->
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PairedBars(result.you.apps?.firstOrNull { it.label == name }?.millis,
                result.friend.apps?.firstOrNull { it.label == name }?.millis, ::duration)
            Spacer(Modifier.height(StillSpacing.medium))
        }
    }
}

@Composable
private fun ResultHeading(title: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = StillSpacing.section, bottom = StillSpacing.medium))
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.padding(top = 5.dp).size(9.dp).background(color, RoundedCornerShape(5.dp)))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PairedBars(you: Long?, friend: Long?, format: (Long) -> String) {
    val maximum = maxOf(you ?: 0L, friend ?: 0L, 1L)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
        PersonBar(you, maximum, format, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        PersonBar(friend, maximum, format, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
    }
}

@Composable
private fun PersonBar(value: Long?, maximum: Long, format: (Long) -> String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value?.let(format) ?: "—", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))) {
            if (value != null && value > 0) Box(Modifier.fillMaxWidth((value.toFloat() / maximum).coerceIn(0f, 1f))
                .height(7.dp).background(color, RoundedCornerShape(4.dp)))
        }
    }
}

private fun duration(value: Long) = Duration.ofMillis(value).compactDuration()
