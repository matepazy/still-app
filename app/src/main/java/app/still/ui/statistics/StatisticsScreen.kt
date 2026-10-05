package app.still.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.data.settings.AppCategory
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsRange
import app.still.ui.components.AdaptivePair
import app.still.ui.components.AdaptiveValueRow
import app.still.ui.components.AppIcon
import app.still.ui.components.DaySelector
import app.still.ui.components.DurationHeadline
import app.still.ui.components.LoadingSkeleton
import app.still.ui.components.StillIcons
import app.still.ui.components.TonalPanel
import app.still.ui.components.compactDuration
import app.still.ui.theme.StillSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
            is StatisticsState.Error -> item {
                Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                    Text("Statistics unavailable", style = MaterialTheme.typography.titleLarge)
                    Text(value.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = viewModel::refresh) { Text("Try again") }
                }
            }
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
        Spacer(Modifier.height(StillSpacing.large))
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(StillSpacing.large)) {
            Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                LoadingSkeleton(Modifier.fillMaxWidth(.8f).height(19.dp))
                repeat(2) {
                    LoadingSkeleton(Modifier.fillMaxWidth().height(18.dp))
                    LoadingSkeleton(Modifier.fillMaxWidth().height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(StillSpacing.xLarge))
        LoadingSkeleton(Modifier.width(144.dp).height(21.dp))
        Spacer(Modifier.height(StillSpacing.medium))
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
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(StillSpacing.large)) {
            Column {
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

internal fun LazyListScope.statisticsContent(summary: app.still.domain.model.StatisticsSummary, period: app.still.domain.model.StatisticsPeriod) {
    val singleDay = summary.range.days == 1L
    val day = summary.days.firstOrNull()
    item {
        Text(if (singleDay) "Screen time" else "Daily average", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        DurationHeadline((if (singleDay) day?.screenTime else summary.dailyAverage)?.compactDuration() ?: "—")
        val recordedDays = summary.days.count { it.screenTime != null }
        if (recordedDays == 0) {
            Text("No screen-time data in this range. Choose another day or period.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (!singleDay && recordedDays.toLong() < summary.range.days) {
            Text("Based on $recordedDays of ${summary.range.days} days · missing days excluded",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(StillSpacing.large))
        PeriodComparison(summary, singleDay)
        Spacer(Modifier.height(StillSpacing.xLarge))
        if (period == StatisticsPeriod.Month || period == StatisticsPeriod.SixMonths) {
            ScreenTimeHeatmap(summary, period)
        } else {
            StatisticsChart(summary, period)
        }
        Spacer(Modifier.height(StillSpacing.large))
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(StillSpacing.large)) {
            Column {
                if (singleDay) {
                    AdaptivePair { itemModifier ->
                        SmallValue("Peak hour", summary.mostActiveHour?.let { "${it.toString().padStart(2, '0')}:00" } ?: "—", itemModifier)
                        SmallValue("Longest session", day?.longestSession?.compactDuration() ?: "—", itemModifier)
                    }
                } else {
                    SummaryValueRow("Total", summary.total?.let { total ->
                        if (period == StatisticsPeriod.Month || period == StatisticsPeriod.SixMonths) {
                            total.durationWithDays()
                        } else total.compactDuration()
                    } ?: "—")
                    Spacer(Modifier.height(StillSpacing.medium))
                    SummaryValueRow("Median day", summary.median?.compactDuration() ?: "—")
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
            Spacer(Modifier.height(StillSpacing.large))
            TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(StillSpacing.large)) {
                Column {
                    AdaptivePair { itemModifier ->
                        RhythmValue(if (singleDay) "Check-ins" else "Check-ins / day",
                            if (singleDay) day?.checkIns?.toString() ?: "—" else summary.checkInsAverage?.let { "%.1f".format(it) } ?: "—", itemModifier)
                        RhythmValue(if (singleDay) "Quick checks" else "Quick checks / day",
                            if (singleDay) day?.quickChecks?.toString() ?: "—" else summary.quickChecksAverage?.let { "%.1f".format(it) } ?: "—", itemModifier)
                    }
                    AdaptivePair(Modifier.padding(top = StillSpacing.small)) { itemModifier ->
                        RhythmValue(if (singleDay) "Longest break" else "Average session",
                            (if (singleDay) day?.longestBreak else summary.sessionAverage)?.compactDuration() ?: "—", itemModifier)
                        RhythmValue(if (singleDay) "App switches" else "Longest break / day",
                            if (singleDay) day?.appSwitches?.toString() ?: "—" else summary.longestBreakAverage?.compactDuration() ?: "—", itemModifier)
                    }
                    if (!singleDay) {
                        AdaptivePair(Modifier.padding(top = StillSpacing.small)) { itemModifier ->
                            RhythmValue("App switches / day", summary.appSwitchesAverage?.let { "%.1f".format(it) } ?: "—", itemModifier)
                            RhythmValue("Longest session", summary.longestSession?.compactDuration() ?: "—", itemModifier)
                        }
                    } else if (summary.sessionAverage != null) {
                        Spacer(Modifier.height(StillSpacing.medium))
                        SmallValue("Average session", summary.sessionAverage.compactDuration())
                    }
                }
            }
            if (!singleDay) {
                val detailedDays = summary.days.count { it.checkIns != null }
                if (detailedDays.toLong() < summary.range.days) Text("Detailed activity is available for $detailedDays of ${summary.range.days} days.",
                    Modifier.padding(top = StillSpacing.small), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val first = if (singleDay) day?.firstUseMinute else summary.firstUseAverageMinute
            val last = if (singleDay) day?.lastUseMinute else summary.lastUseAverageMinute
            if (first != null && last != null) UseWindow(first, last, singleDay)
            summary.quickCheckShare?.let { share ->
                Spacer(Modifier.height(StillSpacing.medium))
                QuickCheckBreakdown(share)
            }
        }
    }
    if (!singleDay && (summary.weekdayAverage != null || summary.weekendAverage != null)) {
        item {
            SectionTitle("Week at a glance")
            WeekPattern(summary.weekdayAverage, summary.weekendAverage)
        }
    }
    if (!singleDay && (summary.highest != null || summary.lowest != null)) {
        item {
            DayExtremes(summary.lowest, summary.highest)
        }
    }
    if (!singleDay && (summary.mostActiveWeekday != null || summary.dailyVariability != null)) {
        item {
            AdaptivePair(Modifier.padding(top = StillSpacing.large)) { itemModifier ->
                summary.mostActiveWeekday?.let {
                    SmallValue("Most active day", it.getDisplayName(java.time.format.TextStyle.FULL,
                        androidx.compose.ui.platform.LocalLocale.current.platformLocale), itemModifier)
                }
                summary.dailyVariability?.let {
                    SmallValue("Day to day variation", it.compactDuration(), itemModifier)
                }
            }
        }
    }
    if (summary.topApps.isNotEmpty()) {
        item {
            SectionTitle("Most used apps")
            Text(if (singleDay) "Screen time by app" else "Total screen time in this period",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(StillSpacing.large))
            val max = (summary.total?.toMillis() ?: summary.topApps.sumOf { it.total.toMillis() }).coerceAtLeast(1L)
            summary.topApps.take(5).forEach { app ->
                Row(Modifier.fillMaxWidth().padding(bottom = StillSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.packageName, app.label, size = 36.dp)
                    Spacer(Modifier.width(StillSpacing.medium))
                    Column(Modifier.weight(1f)) {
                        UsageBar(app.label, app.total.compactDuration(),
                            app.total.toMillis().toFloat() / max, bottomSpacing = 0.dp)
                        if (!singleDay) Text("${app.dailyAverage.compactDuration()} / day",
                            Modifier.padding(top = StillSpacing.xSmall), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        app.change?.let { ChangeCaption(it, singleDay) }
                    }
                }
            }
        }
    }
    if (summary.categories.isNotEmpty()) {
        item {
            SectionTitle("By category")
            CategoryDistribution(summary.categories)
            Spacer(Modifier.height(StillSpacing.large))
            summary.categories.filter { it.share > 0.0 }.forEach { category ->
                Row(Modifier.fillMaxWidth().padding(bottom = StillSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(categoryIcon(category.category)), contentDescription = null,
                        tint = statisticsCategoryColor(category.category))
                    Spacer(Modifier.width(StillSpacing.medium))
                    Column(Modifier.weight(1f)) {
                        UsageBar(category.category.displayName,
                            "${(category.share * 100).toInt()}% · ${category.total.compactDuration()}",
                            category.share.toFloat(), color = statisticsCategoryColor(category.category), bottomSpacing = 0.dp)
                        category.change?.let { ChangeCaption(it, singleDay) }
                    }
                }
            }
        }
    }
}

private fun java.time.Duration.durationWithDays(): String {
    val days = toDays().coerceAtLeast(0)
    if (days == 0L) return compactDuration()
    val remainder = minusDays(days)
    return "$days d" + if (remainder.toMinutes() > 0) " ${remainder.compactDuration()}" else ""
}

private fun signed(duration: java.time.Duration): String = (if (duration.isNegative) "−" else "+") + duration.abs().compactDuration()

@Composable
private fun PeriodComparison(summary: app.still.domain.model.StatisticsSummary, singleDay: Boolean) {
    val current = summary.dailyAverage ?: return
    val previous = summary.previousAverage
    val change = summary.change
    val colors = MaterialTheme.colorScheme
    if (previous == null) {
        Text("No previous ${if (singleDay) "day" else "period"} to compare",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        return
    }
    TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(StillSpacing.large)) {
        Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.large)) {
            Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.xSmall)) {
                Row(horizontalArrangement = Arrangement.spacedBy(StillSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    if (change != null && !change.isZero) Icon(
                        painterResource(if (change.isNegative) StillIcons.ArrowDown else StillIcons.ArrowUp),
                        contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant,
                    )
                    Text(when {
                        change == null -> "Period comparison"
                        change.isZero -> "No change"
                        else -> "${change.abs().compactDuration()} ${if (change.isNegative) "less" else "more"}"
                    }, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                }
                Text(if (singleDay) "Screen time compared with the previous day" else "Daily average compared with the previous period",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            val maximum = maxOf(current.toMillis(), previous.toMillis(), 1L).toFloat()
            UsageBar(if (singleDay) "This day" else "This period", current.compactDuration(),
                current.toMillis() / maximum, bottomSpacing = 0.dp)
            UsageBar(if (singleDay) "Previous day" else "Previous period", previous.compactDuration(),
                previous.toMillis() / maximum, color = colors.secondary, bottomSpacing = 0.dp)
        }
    }
}

@Composable
private fun ChangeCaption(change: java.time.Duration, singleDay: Boolean) {
    Text("${signed(change)} vs previous ${if (singleDay) "day" else "period"}", Modifier.padding(top = StillSpacing.xSmall),
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}


@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(StillSpacing.section))
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(StillSpacing.medium))
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
private fun SummaryValueRow(label: String, value: String) {
    AdaptiveValueRow(
        minLeadingWidth = 80.dp,
        leading = {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailing = {
            Text(value, textAlign = TextAlign.End,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
        },
    )
}

@Composable
private fun SmallValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RhythmValue(label: String, value: String, modifier: Modifier = Modifier) {
    SmallValue(label, value, modifier.padding(vertical = StillSpacing.small))
}

@Composable
internal fun UsageBar(label: String, value: String?, fraction: Float, color: Color = MaterialTheme.colorScheme.primary,
    bottomSpacing: androidx.compose.ui.unit.Dp = StillSpacing.medium) {
    Column(Modifier.fillMaxWidth().padding(bottom = bottomSpacing)) {
        AdaptiveValueRow(
            minLeadingWidth = 112.dp,
            leading = { Text(label, style = MaterialTheme.typography.bodyMedium) },
            trailing = { Text(value ?: "—", textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")) },
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(3.dp))) {
            if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun TypicalDay(hours: List<Long>) {
    val points = hours.take(24).mapIndexed { index, value ->
        "${index.toString().padStart(2, '0')}:00" to value
    }
    StatisticsBars(
        points = points,
        ticks = listOf(0 to "12am", 6 to "6am", 12 to "12pm", 18 to "6pm", 23 to "11pm")
            .map { (hour, label) -> (hour + .5f) / 24 to label },
        modifier = Modifier.fillMaxWidth().padding(top = StillSpacing.small),
        height = 148.dp,
    )
}

fun StatisticsRange.label(): String {
    val format = DateTimeFormatter.ofPattern("MMM d")
    return if (start == endInclusive) start.format(format) else "${start.format(format)} – ${endInclusive.format(format)}"
}
