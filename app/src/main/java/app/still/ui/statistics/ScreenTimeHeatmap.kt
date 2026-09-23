package app.still.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsSummary
import app.still.ui.components.compactDuration
import app.still.ui.theme.StillSpacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

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
    var selectedDate by remember(summary.range) { mutableStateOf<LocalDate?>(null) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }

    Column {
        Text(
            selectedDate?.let { date -> "${date.format(dateFormatter)} · ${values[date]?.compactDuration() ?: "No data"}" }
                ?: "Daily screen time",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(StillSpacing.medium))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 2.dp
            val weekdayWidth = 34.dp
            val cell = ((maxWidth - weekdayWidth - gap * (weekCount + 1)) / weekCount)
                .coerceAtMost(if (period == StatisticsPeriod.Month) 28.dp else 13.dp)
            val gridWidth = weekdayWidth + gap + (cell + gap) * weekCount
            val months = remember(summary.range) {
                val first = YearMonth.from(summary.range.start)
                val last = YearMonth.from(summary.range.endInclusive)
                (0..ChronoUnit.MONTHS.between(first, last).toInt()).map { offset ->
                    first.plusMonths(offset.toLong())
                }
            }
            val labelWidth = 34.dp
            val firstCenter = weekdayWidth + gap + labelWidth / 2
            val lastCenter = gridWidth - labelWidth / 2
            val monthCenters = months.map { month ->
                val firstDay = maxOf(month.atDay(1), summary.range.start)
                val lastDay = minOf(month.atEndOfMonth(), summary.range.endInclusive)
                val middle = firstDay.plusDays(ChronoUnit.DAYS.between(firstDay, lastDay) / 2)
                val weeksFromStart = ChronoUnit.DAYS.between(gridStart, middle).toFloat() / 7f
                (weekdayWidth + gap + (cell + gap) * weeksFromStart + cell / 2).coerceIn(firstCenter, lastCenter)
            }.toMutableList()
            monthCenters.indices.forEach { index ->
                if (index > 0) monthCenters[index] = maxOf(monthCenters[index], monthCenters[index - 1] + labelWidth)
            }
            for (index in monthCenters.indices.reversed()) {
                monthCenters[index] = minOf(monthCenters[index], lastCenter - labelWidth * (monthCenters.lastIndex - index))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.width(gridWidth)) {
                    Box(Modifier.fillMaxWidth().height(20.dp)) {
                        months.forEachIndexed { index, month ->
                            Text(
                                month.month.getDisplayName(TextStyle.SHORT, locale),
                                modifier = Modifier.offset(x = monthCenters[index] - labelWidth / 2).width(labelWidth),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.height(StillSpacing.small))
                    Row {
                        Column(Modifier.width(weekdayWidth), verticalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(7) { weekday ->
                                val day = firstWeekday.plus(weekday.toLong())
                                Box(Modifier.size(weekdayWidth, cell), contentAlignment = Alignment.CenterEnd) {
                                    if (period == StatisticsPeriod.Month || day.value == 1 || day.value == 3 || day.value == 5) Text(
                                        day.getDisplayName(TextStyle.SHORT, locale),
                                        modifier = Modifier.padding(end = StillSpacing.xSmall),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = if (period == StatisticsPeriod.Month) 11.sp else 9.sp,
                                            lineHeight = if (period == StatisticsPeriod.Month) 12.sp else 10.sp,
                                        ),
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
                                        Spacer(Modifier.size(cell))
                                    } else {
                                        val description = "${date.format(dateFormatter)}, ${values[date]?.compactDuration() ?: "no data"}"
                                        Box(
                                            Modifier.size(cell)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(colorFor(date))
                                                .clickable { selectedDate = date }
                                                .semantics { contentDescription = description },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(gap))
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("No data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(5.dp))
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(noData))
            Spacer(Modifier.width(StillSpacing.medium))
            Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            levels.forEach { color ->
                Spacer(Modifier.width(3.dp))
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
            Spacer(Modifier.width(5.dp))
            Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
