package app.still.domain.statistics

import app.still.data.settings.AppCategory
import app.still.domain.model.*
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object StatisticsCalculator {
    fun calculate(
        range: StatisticsRange,
        current: List<StatisticsDay>,
        previous: List<StatisticsDay>,
        categoryOf: (String) -> AppCategory,
        period: StatisticsPeriod,
    ): StatisticsSummary {
        val days = (0 until range.days).map { offset ->
            val date = range.start.plusDays(offset)
            current.firstOrNull { it.date == date } ?: emptyDay(date)
        }
        val valid = days.mapNotNull { it.screenTime?.toMillis() }
        val prior = previous.mapNotNull { it.screenTime?.toMillis() }
        val average = valid.takeIf { it.isNotEmpty() }?.average()?.toLong()?.let(Duration::ofMillis)
        val priorAverage = prior.takeIf { it.isNotEmpty() }?.average()?.toLong()?.let(Duration::ofMillis)
        val sorted = valid.sorted()
        val median = sorted.takeIf { it.isNotEmpty() }?.let {
            Duration.ofMillis(if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2)
        }
        val hourlyDays = days.mapNotNull { it.hourlyMillis?.takeIf { hours -> hours.size == 24 } }
        val hourly = hourlyDays.takeIf { it.isNotEmpty() }?.let { rows ->
            (0..23).map { hour -> rows.sumOf { it[hour] } / rows.size }
        }
        val previousApps = previous.flatMap { it.apps.orEmpty() }.groupBy { it.app.packageName }
            .mapValues { (_, values) -> values.sumOf { it.duration.toMillis() } }
        val apps = days.flatMap { it.apps.orEmpty() }
            .groupBy { it.app.packageName }
            .map { (id, values) ->
                val total = values.fold(Duration.ZERO) { sum, item -> sum.plus(item.duration) }
                val old = previousApps[id] ?: 0L
                StatisticsApp(values.first().app.label, id, total, Duration.ofMillis(total.toMillis() / valid.size.coerceAtLeast(1)),
                    if (previous.any { it.apps != null }) Duration.ofMillis(total.toMillis() - old) else null)
            }.sortedByDescending { it.total }.take(10)
        fun categoryTotals(source: List<StatisticsDay>): Map<AppCategory, Long> = source.flatMap { it.apps.orEmpty() }
            .groupBy { categoryOf(it.app.packageName) }
            .mapValues { (_, values) -> values.sumOf { it.duration.toMillis() } }
        val categoryTotals = categoryTotals(days)
        val priorCategories = categoryTotals(previous)
        val allApps = days.flatMap { it.apps.orEmpty() }.sumOf { it.duration.toMillis() }
        val categories = categoryTotals.map { (category, millis) ->
            StatisticsCategory(category, Duration.ofMillis(millis), if (allApps > 0) millis.toDouble() / allApps else 0.0,
                priorCategories[category]?.let { Duration.ofMillis(millis - it) })
        }.sortedByDescending { it.total }
        val resolution = when {
            period == StatisticsPeriod.SixMonths || range.days > 45 -> 7L
            else -> 1L
        }
        val points = days.chunked(resolution.toInt()).map { bucket ->
            val known = bucket.mapNotNull { it.screenTime?.toMillis() }
            StatisticsPoint(
                bucket.first().date.toString(),
                known.takeIf { it.isNotEmpty() }?.average()?.toLong()?.let(Duration::ofMillis),
                bucket.first().date, bucket.last().date,
            )
        }
        return StatisticsSummary(
            range, days, valid.takeIf { it.isNotEmpty() }?.sum()?.let(Duration::ofMillis), average, median,
            days.filter { it.screenTime != null }.maxByOrNull { it.screenTime!! },
            days.filter { it.screenTime != null }.minByOrNull { it.screenTime!! },
            if (average != null && priorAverage != null) average.minus(priorAverage) else null,
            priorAverage,
            days.mapNotNull { it.checkIns }.takeIf { it.isNotEmpty() }?.average(),
            days.mapNotNull { it.quickChecks }.takeIf { it.isNotEmpty() }?.average(),
            days.mapNotNull { day -> if (day.sessionCount != null && day.sessionTotalMillis != null) day.sessionCount to day.sessionTotalMillis else null }
                .takeIf { it.isNotEmpty() }?.let { pairs -> val count = pairs.sumOf { it.first }; if (count > 0) Duration.ofMillis(pairs.sumOf { it.second } / count) else null },
            days.mapNotNull { it.longestSession }.maxOrNull(),
            days.mapNotNull { it.longestBreak?.toMillis() }.takeIf { it.isNotEmpty() }?.average()?.toLong()?.let(Duration::ofMillis),
            days.mapNotNull { it.firstUseMinute }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            days.mapNotNull { it.lastUseMinute }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            hourly?.indices?.maxByOrNull { hourly[it] }, hourly,
            days.mapNotNull { day -> if (day.checkIns != null && day.quickChecks != null) day.checkIns to day.quickChecks else null }
                .takeIf { it.isNotEmpty() }?.let { pairs -> val count = pairs.sumOf { it.first }; if (count > 0) pairs.sumOf { it.second }.toDouble() / count else null },
            days.mapNotNull { it.appSwitches }.takeIf { it.isNotEmpty() }?.average(),
            apps, categories, points,
            days.filter { it.date.dayOfWeek.value <= 5 }.mapNotNull { it.screenTime?.toMillis() }
                .takeIf { it.isNotEmpty() }?.average()?.toLong()?.let(Duration::ofMillis),
            days.filter { it.date.dayOfWeek.value > 5 }.mapNotNull { it.screenTime?.toMillis() }
                .takeIf { it.isNotEmpty() }?.average()?.toLong()?.let(Duration::ofMillis),
            days.filter { it.screenTime != null }.groupBy { it.date.dayOfWeek }
                .mapValues { (_, values) -> values.mapNotNull { it.screenTime?.toMillis() }.average() }
                .maxByOrNull { it.value }?.key,
            valid.takeIf { it.size > 1 }?.let { values ->
                val mean = values.average()
                Duration.ofMillis(kotlin.math.sqrt(values.map { (it - mean) * (it - mean) }.average()).toLong())
            },
        )
    }

    fun emptyDay(date: LocalDate) = StatisticsDay(date, null, null, null, null, null, null, null, null, null, null, null, null)
}
