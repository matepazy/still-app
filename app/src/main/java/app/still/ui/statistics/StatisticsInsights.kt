package app.still.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.still.ui.components.compactDuration
import app.still.ui.theme.StillSpacing
import java.time.Duration
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun UseWindow(first: Int, last: Int, singleDay: Boolean) {
    val start = Math.floorMod(first, 1440)
    val end = Math.floorMod(last, 1440)
    val span = (last - first).coerceIn(0, 1440)
    val colors = MaterialTheme.colorScheme
    Spacer(Modifier.height(StillSpacing.large))
    Text(if (singleDay) "First to last use" else "Typical use window", style = MaterialTheme.typography.titleMedium)
    if (!singleDay) Text("Median first and last use from complete days, split during your quietest hours.",
        Modifier.padding(top = StillSpacing.xSmall), style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant)
    InsightLayout(graphic = { chartSize ->
        Box(Modifier.size(chartSize).semantics {
            contentDescription = "24 hour clock, first use ${clockMinute(start)}, last use ${clockMinute(end)}"
        }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(22.dp)) {
                val radius = size.minDimension / 2 - 4.dp.toPx()
                val center = Offset(size.width / 2, size.height / 2)
                val topLeft = center - Offset(radius, radius)
                val diameter = Size(radius * 2, radius * 2)
                drawCircle(colors.outlineVariant, radius, center, style = Stroke(2.dp.toPx()))
                repeat(24) { hour ->
                    val angle = Math.toRadians(hour * 15.0 - 90)
                    fun at(r: Float) = center + Offset(cos(angle).toFloat() * r, sin(angle).toFloat() * r)
                    drawLine(colors.onSurfaceVariant, at(radius - if (hour % 6 == 0) 7.dp.toPx() else 3.dp.toPx()),
                        at(radius - 1.dp.toPx()), 1.dp.toPx())
                }
                drawArc(colors.primary, start * .25f - 90, span * .25f, false, topLeft, diameter,
                    style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                listOf(start, end).forEach { minute ->
                    val angle = Math.toRadians(minute * .25 - 90)
                    val point = center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
                    drawCircle(colors.surface, 5.dp.toPx(), point)
                    drawCircle(colors.primary, 3.dp.toPx(), point)
                }
            }
            Text("24h", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
            listOf(Alignment.TopCenter to "00", Alignment.CenterEnd to "06",
                Alignment.BottomCenter to "12", Alignment.CenterStart to "18").forEach { (alignment, label) ->
                Text(label, Modifier.align(alignment), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }, details = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StillSpacing.large)) {
            TimeEndpoint("First use", clockMinute(start))
            TimeEndpoint("Last use", clockMinute(end) + if (span > 0 && end <= start) " (+1 day)" else "")
        }
    })
}

@Composable
private fun TimeEndpoint(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.xSmall)) {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun QuickCheckBreakdown(share: Double) {
    val fraction = share.coerceIn(0.0, 1.0).toFloat()
    val percent = (fraction * 100).toInt()
    val colors = MaterialTheme.colorScheme
    Text("Check-in breakdown", style = MaterialTheme.typography.titleMedium)
    Text("Quick checks last less than a minute", Modifier.padding(top = StillSpacing.xSmall),
        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    InsightLayout(graphic = { chartSize ->
        Box(Modifier.size(chartSize).semantics { contentDescription = "$percent percent of check-ins were quick checks" },
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val bounds = Size(size.width - stroke, size.height - stroke)
                drawArc(colors.surfaceContainerHighest, -90f, 360f, false, Offset(inset, inset), bounds, style = Stroke(stroke))
                drawArc(colors.primary, -90f, fraction * 360f, false, Offset(inset, inset), bounds, style = Stroke(stroke))
            }
            Text("$percent%", style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
        }
    }, details = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StillSpacing.large)) {
            BreakdownLegend("Quick checks · $percent%", colors.primary)
            BreakdownLegend("Other check-ins · ${100 - percent}%", colors.surfaceContainerHighest)
        }
    })
}

/** Give these visualizations comparable weight to the other Statistics charts. */
@Composable
private fun InsightLayout(
    graphic: @Composable (androidx.compose.ui.unit.Dp) -> Unit,
    details: @Composable () -> Unit,
) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = StillSpacing.medium)) {
        val chartSize = (maxWidth * .44f).coerceIn(144.dp, 184.dp)
        if (maxWidth < 320.dp * fontScale) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(StillSpacing.large)) {
                graphic(chartSize)
                details()
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StillSpacing.large),
                verticalAlignment = Alignment.CenterVertically) {
                graphic(chartSize)
                Box(Modifier.weight(1f)) { details() }
            }
        }
    }
}

@Composable
private fun BreakdownLegend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun WeekPattern(weekday: Duration?, weekend: Duration?) {
    val maximum = maxOf(weekday?.toMillis() ?: 0L, weekend?.toMillis() ?: 0L, 1L).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
        Text("Average screen time per day", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        UsageBar("Weekdays", weekday?.compactDuration(), (weekday?.toMillis() ?: 0L) / maximum, bottomSpacing = 0.dp)
        UsageBar("Weekends", weekend?.compactDuration(), (weekend?.toMillis() ?: 0L) / maximum,
            color = MaterialTheme.colorScheme.secondary, bottomSpacing = 0.dp)
    }
}

private fun clockMinute(minute: Int): String = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

@Composable
internal fun DayExtremes(lowest: app.still.domain.model.StatisticsDay?, highest: app.still.domain.model.StatisticsDay?) {
    val colors = MaterialTheme.colorScheme
    val locale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val format = java.time.format.DateTimeFormatter.ofPattern("MMM d", locale)
    Column(Modifier.fillMaxWidth().padding(top = StillSpacing.large)) {
        Text("Daily range", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(StillSpacing.medium))
        app.still.ui.components.AdaptivePair { itemModifier ->
            listOf("Lowest" to lowest, "Highest" to highest).forEach { (label, day) ->
                Column(itemModifier) {
                    Text(day?.screenTime?.compactDuration() ?: "—",
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                    Text("$label · ${day?.date?.format(format) ?: "—"}",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
