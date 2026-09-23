package app.still.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsSummary
import app.still.ui.components.compactDuration
import java.time.Duration
import java.time.format.DateTimeFormatter

@Composable
fun StatisticsChart(summary: StatisticsSummary, period: StatisticsPeriod) {
    val points = if (period == StatisticsPeriod.Day) {
        summary.days.firstOrNull()?.hourlyMillis?.mapIndexed { index, value ->
            ("${index.toString().padStart(2, '0')}:00") to Duration.ofMillis(value)
        } ?: emptyList()
    } else summary.points.map { point ->
        val label = if (point.start == point.endInclusive) point.start.format(DateTimeFormatter.ofPattern("MMM d"))
        else "${point.start.format(DateTimeFormatter.ofPattern("MMM d"))} – ${point.endInclusive.format(DateTimeFormatter.ofPattern("MMM d"))}"
        label to point.value
    }
    var selected by remember(summary, period) { mutableIntStateOf(-1) }
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceContainerHigh
    Column {
        if (selected in points.indices) {
            val point = points[selected]
            Text("${point.first} · ${point.second?.compactDuration() ?: "—"}", style = MaterialTheme.typography.labelMedium)
        } else Text("Screen time", style = MaterialTheme.typography.titleMedium)
        val maximum = points.mapNotNull { it.second?.toMillis() }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp)
            .pointerInput(points) { detectTapGestures { position ->
                if (points.isNotEmpty()) selected = (position.x / size.width * points.size).toInt().coerceIn(0, points.lastIndex)
            } }) {
            if (points.isEmpty()) return@Canvas
            val step = size.width / points.size
            val width = (step * 0.66f).coerceAtLeast(1f)
            points.forEachIndexed { index, (_, value) ->
                val fraction = value?.toMillis()?.toFloat()?.div(maximum)?.coerceIn(0f, 1f) ?: 0f
                val height = (size.height * fraction).coerceAtLeast(if (value != null) 2f else 0f)
                drawRect(if (value == null) muted else primary, Offset(index * step + (step - width) / 2, size.height - height), Size(width, height))
            }
        }
        if (points.isEmpty()) Text("Hourly detail is unavailable for this day.", style = MaterialTheme.typography.bodySmall)
    }
}
