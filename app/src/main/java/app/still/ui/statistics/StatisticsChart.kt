package app.still.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsSummary
import app.still.ui.components.ChartTickLabels
import app.still.ui.components.chartLabelWidth
import app.still.ui.components.compactDuration
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.ceil

@Composable
fun StatisticsChart(summary: StatisticsSummary, period: StatisticsPeriod) {
    val points = if (summary.range.days == 1L) {
        summary.days.firstOrNull()?.hourlyMillis?.mapIndexed { index, value ->
            ("${index.toString().padStart(2, '0')}:00") to Duration.ofMillis(value)
        } ?: emptyList()
    } else summary.points.map { point ->
        val label = if (point.start == point.endInclusive) point.start.format(DateTimeFormatter.ofPattern("MMM d"))
        else "${point.start.format(DateTimeFormatter.ofPattern("MMM d"))} – ${point.endInclusive.format(DateTimeFormatter.ofPattern("MMM d"))}"
        label to point.value
    }
    var selected by remember(summary, period) { mutableIntStateOf(-1) }
    val locale = LocalLocale.current.platformLocale
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceContainerHigh
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val maximum = points.mapNotNull { it.second?.toMillis() }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val axisStep = when {
        maximum <= 30 * 60_000L -> 15 * 60_000L
        maximum <= 60 * 60_000L -> 30 * 60_000L
        else -> 60 * 60_000L
    }
    val axisMaximum = (ceil(maximum.toDouble() / axisStep).toLong() * axisStep).coerceAtLeast(axisStep)
    val labelStride = when {
        points.size <= 7 -> 1
        points.size <= 24 -> 4
        else -> ceil(points.size / 5.0).toInt()
    }
    val scaleLabels = listOf(axisMaximum, axisMaximum / 2, 0L).map { Duration.ofMillis(it).compactDuration() }
    val axisWidth = chartLabelWidth(scaleLabels)
    Column {
        Text(if (summary.range.days == 1L) "By hour" else "Screen time trend", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(axisWidth).height(138.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(axisMaximum, axisMaximum / 2, 0L).forEach { value ->
                    Text(Duration.ofMillis(value).compactDuration(), style = MaterialTheme.typography.labelSmall,
                        color = axis, maxLines = 1)
                }
            }
            Canvas(Modifier.weight(1f).height(138.dp)
                .pointerInput(points) { detectTapGestures { position ->
                    if (points.isNotEmpty()) selected = (position.x / size.width * points.size).toInt().coerceIn(0, points.lastIndex)
                } }) {
                if (points.isEmpty()) return@Canvas
                val inset = 8.dp.toPx()
                val plotHeight = size.height - 2 * inset
                val baseline = size.height - inset
                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    val y = inset + plotHeight * fraction
                    drawLine(axis.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val step = size.width / points.size
                val width = (step * 0.66f).coerceAtLeast(1f)
                points.forEachIndexed { index, (_, value) ->
                    val fraction = value?.toMillis()?.toFloat()?.div(axisMaximum)?.coerceIn(0f, 1f) ?: 0f
                    val barHeight = (plotHeight * fraction).coerceAtLeast(2.dp.toPx())
                    val left = index * step + (step - width) / 2
                    drawRect(if (value == null) muted else primary,
                        Offset(left, baseline - barHeight), Size(width, barHeight))
                    if (index == selected && value != null) {
                        drawRect(axis, Offset(left, baseline - barHeight), Size(width, barHeight), style = Stroke(1.dp.toPx()))
                    }
                }
            }
        }
        if (points.isNotEmpty()) {
            val ticks = points.mapIndexedNotNull { index, point ->
                if (index % labelStride != 0 && index != points.lastIndex) null else {
                    val label = when {
                        summary.range.days == 1L -> index.toString().padStart(2, '0')
                        points.size <= 7 -> summary.points[index].start.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                        else -> point.first.substringBefore(" – ")
                    }
                    (index + .5f) / points.size to label
                }
            }
            ChartTickLabels(ticks, Modifier.padding(start = axisWidth, top = 4.dp))
        }
        if (selected in points.indices) {
            val point = points[selected]
            Text("${point.first} · ${point.second?.compactDuration() ?: "No data"}",
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
        }
        if (points.isEmpty()) Text("Hourly detail is unavailable for this day.", style = MaterialTheme.typography.bodySmall)
    }
}
