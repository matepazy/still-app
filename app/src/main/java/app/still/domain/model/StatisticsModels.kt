package app.still.domain.model

import app.still.data.settings.AppCategory
import java.time.Duration
import java.time.LocalDate

data class StatisticsRange(val start: LocalDate, val endInclusive: LocalDate) {
    init { require(!endInclusive.isBefore(start)) }
    val days: Long get() = java.time.temporal.ChronoUnit.DAYS.between(start, endInclusive) + 1
    val previous: StatisticsRange get() = StatisticsRange(start.minusDays(days), start.minusDays(1))
}

enum class StatisticsPeriod(val label: String) {
    Day("1D"), Week("1W"), Month("1M"), SixMonths("6M"), Custom("Custom");
    fun range(today: LocalDate): StatisticsRange = when (this) {
        Day -> StatisticsRange(today, today)
        Week -> StatisticsRange(today.minusDays(6), today)
        Month -> StatisticsRange(today.minusMonths(1).plusDays(1), today)
        SixMonths -> StatisticsRange(today.minusMonths(6).plusDays(1), today)
        Custom -> StatisticsRange(today.minusDays(6), today)
    }
}

data class StatisticsDay(
    val date: LocalDate,
    val screenTime: Duration?,
    val checkIns: Int?,
    val quickChecks: Int?,
    val unlocks: Int?,
    val wakeups: Int?,
    val longestBreak: Duration?,
    val longestSession: Duration?,
    val firstUseMinute: Int?,
    val lastUseMinute: Int?,
    val hourlyMillis: List<Long>?,
    val appSwitches: Int?,
    val apps: List<AppUsage>?,
    val sessionCount: Int? = null,
    val sessionTotalMillis: Long? = null,
    val activityIntervals: List<UsageMinuteInterval>? = null,
    val observedUntilMinute: Int = 1440,
)

/** Calendar-day activity, with midnight at the end represented by 1440. */
data class UsageMinuteInterval(val start: Int, val end: Int)

data class StatisticsApp(val label: String, val packageName: String, val total: Duration, val dailyAverage: Duration, val change: Duration? = null)
data class StatisticsCategory(val category: AppCategory, val total: Duration, val share: Double, val change: Duration?)
data class StatisticsPoint(val label: String, val value: Duration?, val start: LocalDate, val endInclusive: LocalDate)

data class StatisticsSummary(
    val range: StatisticsRange,
    val days: List<StatisticsDay>,
    val total: Duration?,
    val dailyAverage: Duration?,
    val median: Duration?,
    val highest: StatisticsDay?,
    val lowest: StatisticsDay?,
    val change: Duration?,
    val previousAverage: Duration?,
    val checkInsAverage: Double?,
    val quickChecksAverage: Double?,
    val sessionAverage: Duration?,
    val longestSession: Duration?,
    val longestBreakAverage: Duration?,
    /** Minutes on a usage-day timeline; the end can exceed 1440 when crossing midnight. */
    val firstUseTypicalMinute: Int?,
    val lastUseTypicalMinute: Int?,
    val mostActiveHour: Int?,
    val hourlyAverageMillis: List<Long>?,
    val quickCheckShare: Double?,
    val appSwitchesAverage: Double?,
    val topApps: List<StatisticsApp>,
    val categories: List<StatisticsCategory>,
    val points: List<StatisticsPoint>,
    val weekdayAverage: Duration? = null,
    val weekendAverage: Duration? = null,
    val mostActiveWeekday: java.time.DayOfWeek? = null,
    val dailyVariability: Duration? = null,
)
