package app.still.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsSummary
import app.still.ui.components.AdaptiveValueRow
import app.still.ui.components.ChartTickLabels
import app.still.ui.components.chartLabelWidth
import app.still.ui.components.compactDuration
import app.still.ui.theme.StillSpacing
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.ceil

@Composable
fun StatisticsChart(summary: StatisticsSummary, period: StatisticsPeriod) {
    val singleDay = summary.range.days == 1L
    val locale = LocalLocale.current.platformLocale
    val dateFormat = DateTimeFormatter.ofPattern("MMM d", locale)
    val points = if (singleDay) {
        summary.days.firstOrNull()?.hourlyMillis?.mapIndexed { index, value ->
            "${index.toString().padStart(2, '0')}:00" to value
        }.orEmpty()
    } else summary.points.map { point ->
        val label = if (point.start == point.endInclusive) point.start.format(dateFormat)
        else "${point.start.format(dateFormat)} – ${point.endInclusive.format(dateFormat)}"
        label to point.value?.toMillis()
    }
    val stride = when {
        points.size <= 7 -> 1
        points.size <= 24 -> 4
        else -> ceil(points.size / 5.0).toInt()
    }
    val ticks = points.mapIndexedNotNull { index, point ->
        if (index % stride != 0 && index != points.lastIndex) null else {
            val label = when {
                singleDay -> "${index.toString().padStart(2, '0')}:00"
                points.size <= 7 -> summary.points[index].start.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                else -> point.first.substringBefore(" – ")
            }
            (index + .5f) / points.size to label
        }
    }
    Column {
        StatisticsHeading(if (singleDay) "By hour" else "Screen time trend", help = buildString {
            append(if (singleDay) "Screen time each hour." else if (summary.points.any { it.start != it.endInclusive })
                "Daily average within each bar's date range." else "Screen time each day.")
            append(" Tap a bar to see screen time.")
            if (!singleDay) append(" The dashed line shows the daily average. Dots indicate missing data.")
        })
        if (points.isEmpty()) {
            Text("Hourly detail is unavailable for this day.", Modifier.padding(top = StillSpacing.medium),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            StatisticsBars(points, ticks, referenceMillis = if (singleDay) null else summary.dailyAverage?.toMillis(),
                modifier = Modifier.padding(top = StillSpacing.medium), selectionKey = summary.range to period)
        }
    }
}

/** Shared geometry keeps trend and hourly activity scales aligned with their grid lines. */
@Composable
internal fun StatisticsBars(
    points: List<Pair<String, Long?>>,
    ticks: List<Pair<Float, String>>,
    modifier: Modifier = Modifier,
    referenceMillis: Long? = null,
    height: Dp = 180.dp,
    selectionKey: Any? = null,
    showDetails: Boolean = true,
) {
    var selected by remember(points, selectionKey) { mutableIntStateOf(-1) }
    val colors = MaterialTheme.colorScheme
    val maximum = maxOf(points.mapNotNull { it.second }.maxOrNull() ?: 0L, referenceMillis ?: 0L)
    val axisMaximum = statisticsAxisMaximum(maximum)
    val scaleLabels = (4 downTo 0).map { Duration.ofMillis(axisMaximum * it / 4).compactDuration() }
    val axisWidth = chartLabelWidth(scaleLabels)
    val labelHeight = with(LocalDensity.current) {
        rememberTextMeasurer().measure("0m", MaterialTheme.typography.labelSmall).size.height.toDp()
    }
    val chartHeight = maxOf(height, labelHeight * 5 + 32.dp)
    val detail = points.getOrNull(selected)
    fun advance(delta: Int): Boolean {
        if (points.isEmpty()) return false
        selected = if (selected < 0) 0 else (selected + delta + points.size) % points.size
        return true
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(axisWidth).height(chartHeight).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                scaleLabels.forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
            Canvas(Modifier.weight(1f).height(chartHeight).padding(vertical = labelHeight / 2 + 8.dp)
                .semantics {
                    contentDescription = detail?.let { "${it.first}, ${it.second?.let { value -> Duration.ofMillis(value).compactDuration() } ?: "No data"}" }
                        ?: "Screen time chart, ${points.size} values"
                    onClick(label = "Next value") { advance(1) }
                    customActions = listOf(
                        CustomAccessibilityAction("Previous value") { advance(-1) },
                        CustomAccessibilityAction("Next value") { advance(1) },
                    )
                }
                .pointerInput(points, selectionKey) { detectTapGestures { position ->
                    if (points.isNotEmpty()) {
                        val index = (position.x / size.width * points.size).toInt().coerceIn(0, points.lastIndex)
                        selected = if (selected == index) -1 else index
                    }
                } }) {
                if (points.isEmpty()) return@Canvas
                val step = size.width / points.size
                if (selected in points.indices) drawRoundRect(colors.primary.copy(alpha = .08f),
                    Offset(selected * step, 0f), Size(step, size.height), CornerRadius(4.dp.toPx()))
                (0..4).forEach { index ->
                    val y = size.height * index / 4
                    drawLine(colors.outlineVariant, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                val width = (step * .6f).coerceAtLeast(1f)
                points.forEachIndexed { index, (_, value) ->
                    val left = index * step + (step - width) / 2
                    val barHeight = size.height * ((value ?: 0L).toFloat() / axisMaximum).coerceIn(0f, 1f)
                    if (value == null) {
                        drawCircle(colors.onSurfaceVariant, 1.5.dp.toPx(), Offset(left + width / 2, size.height))
                    } else if (value > 0) {
                        drawRoundRect(if (selected < 0 || selected == index) colors.primary else colors.secondary.copy(alpha = .45f),
                            Offset(left, size.height - barHeight), Size(width, barHeight),
                            CornerRadius(minOf(3.dp.toPx(), width / 2, barHeight / 2)))
                    }
                }
                referenceMillis?.let { value ->
                    val y = size.height * (1f - value.toFloat() / axisMaximum)
                    drawLine(colors.onSurfaceVariant, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                }
            }
        }
        ChartTickLabels(ticks, Modifier.padding(start = axisWidth, top = StillSpacing.xSmall))
        if (showDetails) Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = StillSpacing.medium),
            verticalArrangement = Arrangement.Center) {
            if (detail != null) {
                AdaptiveValueRow(minLeadingWidth = 100.dp,
                    leading = { Text(detail.first, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) },
                    trailing = { Text(detail.second?.let { Duration.ofMillis(it).compactDuration() } ?: "No data",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")) })
            }
            if (referenceMillis != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.width(20.dp).height(12.dp)) {
                        drawLine(colors.onSurfaceVariant, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                            1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                    }
                    Text("Daily average · ${Duration.ofMillis(referenceMillis).compactDuration()}",
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun statisticsAxisMaximum(maximumMillis: Long): Long {
    val stepMinutes = listOf(5L, 10L, 15L, 30L, 60L, 120L, 180L, 240L, 360L)
        .firstOrNull { maximumMillis <= it * 4 * 60_000L }
        ?: (ceil(maximumMillis / (4 * 60_000.0 * 60)).toLong() * 60)
    return stepMinutes * 4 * 60_000L
}
