package app.still.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsSummary
import app.still.ui.components.chartLabelWidth
import app.still.ui.components.ChartTickLabels
import app.still.ui.components.compactDuration
import app.still.ui.theme.StillSpacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenTimeHeatmap(summary: StatisticsSummary, period: StatisticsPeriod) {
    val locale = LocalLocale.current.platformLocale
    val firstWeekday = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val gridStart = remember(summary.range, firstWeekday) {
        val offset = (summary.range.start.dayOfWeek.value - firstWeekday.value + 7) % 7
        summary.range.start.minusDays(offset.toLong())
    }
    val weekCount = (ChronoUnit.DAYS.between(gridStart, summary.range.endInclusive) / 7 + 1).toInt()
    val values = remember(summary) { summary.days.associate { it.date to it.screenTime } }
    val maximum = remember(summary) { summary.days.mapNotNull { it.screenTime?.toMillis() }.maxOrNull()?.coerceAtLeast(1L) ?: 1L }
    val background = MaterialTheme.colorScheme.surfaceContainerHighest
    val primary = MaterialTheme.colorScheme.primary
    val noData = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    val levels = listOf(lerp(background, primary, 0.14f), lerp(background, primary, 0.36f),
        lerp(background, primary, 0.58f), lerp(background, primary, 0.79f), primary)
    fun colorFor(date: LocalDate): Color {
        val millis = values[date]?.toMillis() ?: return noData
        val fraction = millis.toFloat() / maximum
        return levels[when {
            millis == 0L -> 0
            fraction <= 0.25f -> 1
            fraction <= 0.5f -> 2
            fraction <= 0.75f -> 3
            else -> 4
        }]
    }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }

    Column {
        Text("Daily screen time", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(StillSpacing.medium))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val gapPixels = with(density) { 2.dp.roundToPx() }
            val gap = with(density) { gapPixels.toDp() }
            val months = remember(summary.range) {
                val first = YearMonth.from(summary.range.start)
                val last = YearMonth.from(summary.range.endInclusive)
                (0..ChronoUnit.MONTHS.between(first, last).toInt()).map { offset ->
                    first.plusMonths(offset.toLong())
                }
            }
            val measuredWeekdayWidth = chartLabelWidth((0..6).map { firstWeekday.plus(it.toLong()).getDisplayName(TextStyle.SHORT, locale) })
            val weekdayPixels = with(density) { measuredWeekdayWidth.roundToPx() }
            val weekdayWidth = with(density) { weekdayPixels.toDp() }
            val labelHeight = rememberTextMeasurer().measure("Wed", MaterialTheme.typography.labelSmall).size.height
            // Compose rounds each child independently. Allocate whole pixels first so
            // rounding cannot consume the last column's width.
            val cellPixels = with(density) {
                ((maxWidth.roundToPx() - weekdayPixels - gapPixels * (weekCount + 1)) / weekCount)
                    .coerceIn(1, (if (period == StatisticsPeriod.Month) 28.dp else 13.dp).roundToPx())
            }
            val cell = with(density) { cellPixels.toDp() }
            val gridWidth = with(density) {
                (weekdayPixels + gapPixels * (weekCount + 1) + cellPixels * weekCount).toDp()
            }
            val rowHeight = maxOf(cell, with(density) { labelHeight.toDp() })
            val monthCenters = months.map { month ->
                val firstDay = maxOf(month.atDay(1), summary.range.start)
                val lastDay = minOf(month.atEndOfMonth(), summary.range.endInclusive)
                val middle = firstDay.plusDays(ChronoUnit.DAYS.between(firstDay, lastDay) / 2)
                val weeksFromStart = ChronoUnit.DAYS.between(gridStart, middle).toFloat() / 7f
                weekdayWidth + gap + (cell + gap) * weeksFromStart + cell / 2
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.width(gridWidth)) {
                    ChartTickLabels(months.mapIndexed { index, month ->
                        (monthCenters[index] / gridWidth) to month.month.getDisplayName(TextStyle.SHORT, locale)
                    })
                    Spacer(Modifier.height(StillSpacing.small))
                    Row {
                        Column(Modifier.width(weekdayWidth), verticalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(7) { weekday ->
                                val day = firstWeekday.plus(weekday.toLong())
                                Box(Modifier.size(weekdayWidth, rowHeight), contentAlignment = Alignment.CenterEnd) {
                                    if (period == StatisticsPeriod.Month || day.value == 1 || day.value == 3 || day.value == 5) Text(
                                        day.getDisplayName(TextStyle.SHORT, locale),
                                        modifier = Modifier.padding(end = StillSpacing.xSmall),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.End,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(gap))
                        repeat(weekCount) { week ->
                            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                repeat(7) { weekday ->
                                    val date = gridStart.plusDays(week * 7L + weekday)
                                    if (date.isBefore(summary.range.start) || date.isAfter(summary.range.endInclusive)) {
                                        Spacer(Modifier.size(cell, rowHeight))
                                    } else {
                                        val description = "${date.format(dateFormatter)}, ${values[date]?.compactDuration() ?: "no data"}"
                                        Box(Modifier.size(cell, rowHeight), contentAlignment = Alignment.Center) {
                                            Box(
                                                Modifier.size(cell)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(colorFor(date))
                                                    .semantics { contentDescription = description },
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(gap))
                        }
                    }
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = StillSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(StillSpacing.small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("No data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(5.dp))
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(noData))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("0m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                levels.forEach { color ->
                    Spacer(Modifier.width(3.dp))
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
            Spacer(Modifier.width(5.dp))
            Text(java.time.Duration.ofMillis(maximum).compactDuration(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
