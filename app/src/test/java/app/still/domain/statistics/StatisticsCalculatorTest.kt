package app.still.domain.statistics

import app.still.data.settings.AppCategory
import app.still.domain.model.AppInfo
import app.still.domain.model.AppUsage
import app.still.domain.model.StatisticsDay
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsRange
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class StatisticsCalculatorTest {
    private val today = LocalDate.of(2026, 9, 23)
    private fun day(date: LocalDate, minutes: Long?, detailed: Boolean = true, hour: Int? = null): StatisticsDay = StatisticsDay(
        date = date,
        screenTime = minutes?.let(Duration::ofMinutes),
        checkIns = if (detailed && minutes != null) 4 else null,
        quickChecks = if (detailed && minutes != null) 1 else null,
        unlocks = if (detailed && minutes != null) 3 else null,
        wakeups = if (detailed && minutes != null) 5 else null,
        longestBreak = if (detailed && minutes != null) Duration.ofMinutes(40) else null,
        longestSession = if (detailed && minutes != null) Duration.ofMinutes(20) else null,
        firstUseMinute = if (detailed && minutes != null) 8 * 60 else null,
        lastUseMinute = if (detailed && minutes != null) 20 * 60 else null,
        hourlyMillis = if (detailed && hour != null) List(24) { if (it == hour) 60_000L else 0L } else null,
        appSwitches = if (detailed && minutes != null) 2 else null,
        apps = minutes?.let { listOf(AppUsage(AppInfo("a", "App A"), Duration.ofMinutes(it), 1)) },
    )
    private fun calculate(range: StatisticsRange, current: List<StatisticsDay>, previous: List<StatisticsDay> = emptyList(), period: StatisticsPeriod = StatisticsPeriod.Custom) =
        StatisticsCalculator.calculate(range, current, previous, { AppCategory.Social }, period)

    @Test fun oneDayAndPreviousDay() {
        val range = StatisticsPeriod.Day.range(today)
        assertEquals(today.minusDays(1), range.previous.start)
        val result = calculate(range, listOf(day(today, 120)), listOf(day(today.minusDays(1), 150)), StatisticsPeriod.Day)
        assertEquals(Duration.ofMinutes(120), result.dailyAverage)
        assertEquals(Duration.ofMinutes(-30), result.change)
        assertEquals(1, result.points.size)
    }

    @Test fun sevenDayRangeAndMissingDays() {
        val range = StatisticsPeriod.Week.range(today)
        assertEquals(7, range.days)
        assertEquals(range.start.minusDays(7), range.previous.start)
        val result = calculate(range, listOf(day(range.start, 60), day(today, 120)))
        assertEquals(7, result.days.size)
        assertNull(result.days[1].screenTime)
        assertEquals(Duration.ofMinutes(90), result.dailyAverage)
        assertEquals(Duration.ofMinutes(180), result.total)
    }

    @Test fun monthlyAndCustomBoundaries() {
        val month = StatisticsPeriod.Month.range(today)
        assertEquals(today.minusMonths(1).plusDays(1), month.start)
        assertEquals(month.days, month.previous.days)
        val custom = StatisticsRange(LocalDate.of(2026, 2, 27), LocalDate.of(2026, 3, 2))
        assertEquals(4, custom.days)
        assertEquals(LocalDate.of(2026, 2, 23), custom.previous.start)
        assertEquals(LocalDate.of(2026, 2, 26), custom.previous.endInclusive)
    }

    @Test fun aggregateOnlyDoesNotBecomeDetailedZero() {
        val range = StatisticsRange(today.minusDays(2), today)
        val result = calculate(range, listOf(day(range.start, 60), day(range.start.plusDays(1), 90, detailed = false)))
        assertEquals(4.0, result.checkInsAverage!!, 0.001)
        assertEquals(1.0, result.quickChecksAverage!!, 0.001)
        assertNull(result.days[1].checkIns)
        assertNull(result.days[2].checkIns)
        assertEquals(Duration.ofMinutes(75), result.dailyAverage)
    }

    @Test fun categoriesTopAppsAndHourlyUseAvailableData() {
        val range = StatisticsRange(today.minusDays(1), today)
        val result = calculate(range, listOf(day(range.start, 60, hour = 20), day(today, 120, hour = 20)))
        assertEquals("App A", result.topApps.first().label)
        assertEquals(Duration.ofMinutes(180), result.topApps.first().total)
        assertEquals(AppCategory.Social, result.categories.first().category)
        assertEquals(1.0, result.categories.first().share, 0.001)
        assertEquals(20, result.mostActiveHour)
        assertEquals(60_000L, result.hourlyAverageMillis!![20])
    }

    @Test fun sessionAndWeekdayPatternsUseDetailedRecords() {
        val monday = LocalDate.of(2026, 9, 21)
        val range = StatisticsRange(monday, monday.plusDays(1))
        val first = day(monday, 60).copy(sessionCount = 2, sessionTotalMillis = 30 * 60_000L)
        val second = day(monday.plusDays(1), 120, detailed = false)
        val result = calculate(range, listOf(first, second))
        assertEquals(Duration.ofMinutes(15), result.sessionAverage)
        assertEquals(Duration.ofMinutes(90), result.weekdayAverage)
        assertEquals(monday.plusDays(1).dayOfWeek, result.mostActiveWeekday)
        assertNull(result.weekendAverage)
        assertNotNull(result.dailyVariability)
    }

    @Test fun sixMonthsUsesWeeklyDisplayAverages() {
        val range = StatisticsPeriod.SixMonths.range(today)
        assertEquals(today.minusMonths(6).plusDays(1), range.start)
        assertEquals(today, range.endInclusive)
        val result = calculate(range, listOf(day(range.start, 60)), period = StatisticsPeriod.SixMonths)
        assertTrue(result.points.size in 26..27)
        assertEquals(Duration.ofMinutes(60), result.points.first().value)
        assertNull(result.points[1].value)
    }
}
