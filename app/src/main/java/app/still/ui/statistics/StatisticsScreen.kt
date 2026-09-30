package app.still.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.domain.model.StatisticsRange
import app.still.domain.model.StatisticsPeriod
import app.still.data.settings.AppCategory
import app.still.ui.components.StillIcons
import app.still.ui.components.DaySelector
import app.still.ui.components.AppIcon
import app.still.ui.components.TonalPanel
import app.still.ui.components.LoadingSkeleton
import app.still.ui.components.compactDuration
import app.still.ui.theme.StillSpacing
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsTopBar(onCompare: () -> Unit) {
    TopAppBar(title = { Text("Statistics") }, actions = {
        IconButton(onClick = onCompare) {
            Icon(painterResource(StillIcons.Compare), contentDescription = "Compare with a friend", tint = MaterialTheme.colorScheme.onSurface)
        }
    })
}

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, availableDates: List<LocalDate>, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxSize().padding(horizontal = StillSpacing.large)) {
        item {
            Spacer(Modifier.height(StillSpacing.large))
            StatisticsPeriodSelector(period, range, availableDates, viewModel::select, viewModel::selectCustom)
            if (period == StatisticsPeriod.Day) {
                DaySelector(range.start, availableDates, viewModel::selectDay,
                    modifier = Modifier.padding(top = StillSpacing.small), showTodayLabel = false)
            } else {
                Text(range.label(), modifier = Modifier.padding(top = StillSpacing.medium), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(StillSpacing.large))
        }
        when (val value = state) {
            StatisticsState.Loading -> item { StatisticsLoadingContent(period) }
            is StatisticsState.Error -> item { Text(value.message, color = MaterialTheme.colorScheme.error) }
            is StatisticsState.Ready -> statisticsContent(value.summary, period)
        }
        item { Spacer(Modifier.height(StillSpacing.section)) }
    }
}

@Composable
private fun StatisticsLoadingContent(period: StatisticsPeriod) {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Loading statistics" }) {
        LoadingSkeleton(Modifier.width(112.dp).height(20.dp))
        Spacer(Modifier.height(StillSpacing.medium))
        LoadingSkeleton(Modifier.width(172.dp).height(52.dp))
        Spacer(Modifier.height(StillSpacing.small))
        LoadingSkeleton(Modifier.width(226.dp).height(16.dp))
        Spacer(Modifier.height(StillSpacing.xLarge))
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(StillSpacing.large)) {
            Column {
                Row(
                    Modifier.fillMaxWidth().height(if (period == StatisticsPeriod.Month || period == StatisticsPeriod.SixMonths) 150.dp else 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    listOf(0.35f, 0.6f, 0.45f, 0.8f, 0.55f, 0.7f, 0.5f).forEach { fraction ->
                        LoadingSkeleton(Modifier.weight(1f).height((150f * fraction).dp))
                    }
                }
                Spacer(Modifier.height(StillSpacing.large))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LoadingSkeleton(Modifier.width(92.dp).height(36.dp))
                    LoadingSkeleton(Modifier.width(92.dp).height(36.dp))
                }
            }
        }
        Spacer(Modifier.height(StillSpacing.section))
        LoadingSkeleton(Modifier.width(144.dp).height(28.dp))
        Spacer(Modifier.height(StillSpacing.medium))
        repeat(3) {
            LoadingSkeleton(Modifier.fillMaxWidth().height(32.dp))
            Spacer(Modifier.height(StillSpacing.small))
        }
    }
}

private fun LazyListScope.statisticsContent(summary: app.still.domain.model.StatisticsSummary, period: app.still.domain.model.StatisticsPeriod) {
    val singleDay = summary.range.days == 1L
    val day = summary.days.firstOrNull()
    item {
        Text(if (singleDay) "Screen time" else "Daily average", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text((if (singleDay) day?.screenTime else summary.dailyAverage)?.compactDuration() ?: "—",
            style = MaterialTheme.typography.displayLarge)
        val change = summary.change
        Text(when {
            change == null -> if (singleDay) "No data for the previous day" else "No previous period to compare"
            change.isZero -> if (singleDay) "Same as the previous day" else "Same as the previous period"
            else -> "${change.abs().compactDuration()} ${if (change.isNegative) "less" else "more"} than ${if (singleDay) "the previous day" else "the previous period"}"
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(StillSpacing.xLarge))
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(StillSpacing.large)) {
            Column {
                if (period == StatisticsPeriod.Month || period == StatisticsPeriod.SixMonths) {
                    ScreenTimeHeatmap(summary, period)
                } else {
                    StatisticsChart(summary, period)
                }
                Row(Modifier.fillMaxWidth().padding(top = StillSpacing.medium), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (singleDay) {
                        SmallValue("Peak hour", summary.mostActiveHour?.let { "${it.toString().padStart(2, '0')}:00" } ?: "—")
                        SmallValue("Longest session", day?.longestSession?.compactDuration() ?: "—")
                    } else {
                        SmallValue("Total", summary.total?.compactDuration() ?: "—")
                        SmallValue("Median day", summary.median?.compactDuration() ?: "—")
                    }
                }
            }
        }
    }
    if (summary.hourlyAverageMillis != null || summary.checkInsAverage != null || summary.sessionAverage != null) {
        item {
            SectionTitle(if (singleDay) "On this day" else "Daily rhythm")
            if (!singleDay) summary.hourlyAverageMillis?.let { hours ->
                Text(if (summary.days.count { it.hourlyMillis != null } > 1) "Typical day" else "Hourly activity",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TypicalDay(hours)
                summary.mostActiveHour?.let {
                    Text("Most active around ${it.toString().padStart(2, '0')}:00", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = StillSpacing.medium), horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                RhythmValue(if (singleDay) "Check-ins" else "Check-ins / day",
                    if (singleDay) day?.checkIns?.toString() ?: "—" else summary.checkInsAverage?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f))
                RhythmValue(if (singleDay) "Quick checks" else "Quick checks / day",
                    if (singleDay) day?.quickChecks?.toString() ?: "—" else summary.quickChecksAverage?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = StillSpacing.small), horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                RhythmValue(if (singleDay) "Longest break" else "Average session",
                    (if (singleDay) day?.longestBreak else summary.sessionAverage)?.compactDuration() ?: "—", Modifier.weight(1f))
                RhythmValue(if (singleDay) "App switches" else "Longest break / day",
                    if (singleDay) day?.appSwitches?.toString() ?: "—" else summary.longestBreakAverage?.compactDuration() ?: "—", Modifier.weight(1f))
            }
            if (!singleDay) {
                Row(Modifier.fillMaxWidth().padding(top = StillSpacing.small), horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                    RhythmValue("App switches / day", summary.appSwitchesAverage?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f))
                    RhythmValue("Longest session", summary.longestSession?.compactDuration() ?: "—", Modifier.weight(1f))
                }
            } else if (summary.sessionAverage != null) {
                Spacer(Modifier.height(StillSpacing.medium))
                SmallValue("Average session", summary.sessionAverage.compactDuration())
            }
            val first = if (singleDay) day?.firstUseMinute else summary.firstUseAverageMinute
            val last = if (singleDay) day?.lastUseMinute else summary.lastUseAverageMinute
            if (first != null && last != null) UseWindow(first, last, singleDay)
            summary.quickCheckShare?.let { share ->
                Spacer(Modifier.height(StillSpacing.medium))
                UsageBar("Quick checks / check-ins", "${(share * 100).toInt()}%", share.toFloat())
            }
        }
    }
    if (!singleDay && (summary.weekdayAverage != null || summary.weekendAverage != null)) {
        item {
            SectionTitle("Week at a glance")
            val max = maxOf(summary.weekdayAverage?.toMillis() ?: 0L, summary.weekendAverage?.toMillis() ?: 0L, 1L)
            UsageBar("Weekdays", summary.weekdayAverage?.compactDuration(), (summary.weekdayAverage?.toMillis() ?: 0L).toFloat() / max)
            UsageBar("Weekends", summary.weekendAverage?.compactDuration(), (summary.weekendAverage?.toMillis() ?: 0L).toFloat() / max)
        }
    }
    if (!singleDay && (summary.highest != null || summary.lowest != null)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = StillSpacing.medium), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallValue("Highest · ${summary.highest?.date?.format(DateTimeFormatter.ofPattern("MMM d")) ?: "—"}",
                    summary.highest?.screenTime?.compactDuration() ?: "—")
                SmallValue("Lowest · ${summary.lowest?.date?.format(DateTimeFormatter.ofPattern("MMM d")) ?: "—"}",
                    summary.lowest?.screenTime?.compactDuration() ?: "—")
            }
        }
    }
    if (!singleDay && (summary.mostActiveWeekday != null || summary.dailyVariability != null)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = StillSpacing.large), horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                Icon(painterResource(StillIcons.Calendar), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(buildString {
                    summary.mostActiveWeekday?.let { append("Most active: ").append(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                    summary.dailyVariability?.let { if (isNotEmpty()) append(" · "); append(it.compactDuration()).append(" day to day variation") }
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (summary.topApps.isNotEmpty()) {
        item {
            SectionTitle("Most used apps")
            val max = summary.topApps.maxOf { it.total.toMillis() }.coerceAtLeast(1L)
            summary.topApps.take(5).forEach { app ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.packageName, app.label, size = 32.dp)
                    Spacer(Modifier.width(StillSpacing.medium))
                    Box(Modifier.weight(1f)) {
                        UsageBar(app.label, app.total.compactDuration() + (app.change?.let { " · ${signed(it)}" } ?: ""),
                            app.total.toMillis().toFloat() / max)
                    }
                }
            }
        }
    }
    if (summary.categories.isNotEmpty()) {
        item {
            SectionTitle("By category")
            summary.categories.filter { it.share > 0.0 }.forEach { category ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(categoryIcon(category.category)), contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(StillSpacing.medium))
                    Box(Modifier.weight(1f)) {
                        UsageBar(category.category.displayName,
                            "${(category.share * 100).toInt()}% · ${category.total.compactDuration()}" +
                                (category.change?.let { " · ${signed(it)}" } ?: ""), category.share.toFloat())
                    }
                }
            }
        }
    }
}

private fun signed(duration: java.time.Duration): String = (if (duration.isNegative) "−" else "+") + duration.abs().compactDuration()
private fun clockMinute(minute: Int): String = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(StillSpacing.section))
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(StillSpacing.medium))
}

@Composable
private fun UseWindow(first: Int, last: Int, singleDay: Boolean) {
    val startMinute = first.coerceIn(0, 1440)
    val start = startMinute / 1440f
    val end = last.coerceIn(startMinute, 1440) / 1440f
    Spacer(Modifier.height(StillSpacing.large))
    Text(if (singleDay) "First to last use" else "Typical use window", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth().padding(top = StillSpacing.small), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(clockMinute(first), style = MaterialTheme.typography.bodyMedium)
        Text(clockMinute(last), style = MaterialTheme.typography.bodyMedium)
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val active = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(12.dp).padding(top = 4.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        drawRoundRect(active, topLeft = Offset(size.width * start, 0f),
            size = Size(size.width * (end - start).coerceAtLeast(0.005f), size.height), cornerRadius = radius)
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("12am", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("12pm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("12am", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun categoryIcon(category: AppCategory): Int = when (category) {
    AppCategory.Social -> StillIcons.CategorySocial
    AppCategory.Games -> StillIcons.CategoryGames
    AppCategory.Video -> StillIcons.CategoryVideo
    AppCategory.MusicAndAudio -> StillIcons.CategoryMusic
    AppCategory.Photography -> StillIcons.CategoryPhotography
    AppCategory.News -> StillIcons.CategoryNews
    AppCategory.MapsAndNavigation -> StillIcons.CategoryMaps
    AppCategory.Productivity -> StillIcons.CategoryProductivity
    AppCategory.Accessibility -> StillIcons.CategoryAccessibility
    AppCategory.Other -> StillIcons.CategoryOther
}

@Composable
private fun SmallValue(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RhythmValue(label: String, value: String, modifier: Modifier = Modifier) {
    TonalPanel(modifier, contentPadding = PaddingValues(StillSpacing.medium)) {
        SmallValue(label, value)
    }
}

@Composable
internal fun UsageBar(label: String, value: String?, fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    Column(Modifier.fillMaxWidth().padding(bottom = StillSpacing.medium)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(StillSpacing.small))
            Text(value ?: "—", Modifier.weight(1f), maxLines = 2, textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))) {
            if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(7.dp).background(color, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun TypicalDay(hours: List<Long>) {
    val max = hours.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val axisMaximum = (ceil(max / (20 * 60_000.0)).toLong().coerceAtLeast(1L) * 20 * 60_000L)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxWidth().padding(top = StillSpacing.small)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(40.dp).height(80.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(axisMaximum, axisMaximum / 2, 0L).forEach { value ->
                    val minutes = value / 60_000L
                    Text(if (minutes >= 60) "${minutes / 60}h" else "${minutes}m",
                        style = MaterialTheme.typography.labelSmall, color = axisColor)
                }
            }
            Canvas(Modifier.weight(1f).height(80.dp)) {
                val inset = 8.dp.toPx()
                val plotHeight = size.height - 2 * inset
                val baseline = size.height - inset
                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    val y = inset + plotHeight * fraction
                    drawLine(axisColor.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                val step = size.width / 24
                hours.take(24).forEachIndexed { index, value ->
                    val barHeight = (plotHeight * (value.toFloat() / axisMaximum).coerceIn(0f, 1f))
                        .coerceAtLeast(2.dp.toPx())
                    val barWidth = (step - 2.dp.toPx()).coerceAtLeast(1f)
                    drawRoundRect(if (value == 0L) emptyColor else barColor,
                        topLeft = Offset(index * step + (step - barWidth) / 2, baseline - barHeight),
                        size = Size(barWidth, barHeight), cornerRadius = CornerRadius(2.dp.toPx()))
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp)) {
            val labelWidth = 40.dp
            listOf("12am", "6am", "12pm", "6pm", "12am").forEachIndexed { index, label ->
                val left = (maxWidth * (index / 4f) - labelWidth / 2).coerceIn(0.dp, maxWidth - labelWidth)
                Text(label, Modifier.offset(x = left).width(labelWidth), style = MaterialTheme.typography.labelSmall,
                    color = axisColor, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

fun StatisticsRange.label(): String {
    val format = DateTimeFormatter.ofPattern("MMM d")
    return if (start == endInclusive) start.format(format) else "${start.format(format)} – ${endInclusive.format(format)}"
}
