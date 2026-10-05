package app.still.domain.statistics

import app.still.domain.model.StatisticsDay
import app.still.domain.model.StatisticsRange
import app.still.domain.model.UsageMinuteInterval
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class TypicalUsageWindowTest {
    private val start = LocalDate.of(2026, 9, 21)

    private fun day(offset: Long, vararg intervals: Pair<Int, Int>, observedUntil: Int = 1440): StatisticsDay {
        val activity = intervals.map { UsageMinuteInterval(it.first, it.second) }
        val hourly = (0..23).map { hour ->
            activity.sumOf { (minOf(it.end, (hour + 1) * 60) - maxOf(it.start, hour * 60)).coerceAtLeast(0) * 60_000L }
        }
        return StatisticsCalculator.emptyDay(start.plusDays(offset)).copy(
            screenTime = Duration.ofMillis(hourly.sum()),
            firstUseMinute = activity.minOfOrNull { it.start },
            lastUseMinute = activity.maxOfOrNull { it.end }?.rem(1440),
            hourlyMillis = hourly,
            activityIntervals = activity,
            observedUntilMinute = observedUntil,
        )
    }

    private fun calculate(records: List<StatisticsDay>, lastDay: Long = 6) =
        TypicalUsageWindow.calculate(StatisticsRange(start, start.plusDays(lastDay)), records)

    @Test fun occasionalThreeAmUseDoesNotBecomeMorningOrPullBedtimeEarlier() {
        val records = (0L..7L).map { offset ->
            if (offset == 3L) day(offset, 0 to 180, 480 to 500, 1320 to 1340)
            else day(offset, 480 to 500, 1320 to 1340)
        }
        assertEquals(480 to 1340, calculate(records))
    }

    @Test fun regularAfterMidnightBedtimeRemainsAfterMidnight() {
        val records = (0L..7L).map { day(it, 0 to 60, 480 to 500, 1320 to 1440) }
        assertEquals(480 to 1500, calculate(records))
    }

    @Test fun bedtimesStraddlingMidnightHaveAMidnightMedian() {
        val records = listOf(
            day(0, 480 to 500, 1320 to 1380),
            day(1, 480 to 500, 1320 to 1440),
            day(2, 0 to 60, 480 to 500, 1320 to 1380),
        )
        assertEquals(480 to 1440, calculate(records, lastDay = 1))
    }

    @Test fun partialTodayDoesNotLowerTypicalBedtime() {
        val records = (0L..6L).map { day(it, 480 to 500, 1320 to 1340) } +
            day(7, 480 to 500, observedUntil = 900)
        assertEquals(480 to 1340, calculate(records, lastDay = 7))
    }

    @Test fun unfinishedFollowingMorningCannotCompleteLastNight() {
        val records = listOf(
            day(0, 480 to 500, 1320 to 1380),
            day(1, 480 to 500, 1320 to 1440),
            day(2, 0 to 60, observedUntil = 90),
        )
        assertEquals(480 to 1380, calculate(records, lastDay = 2))
    }

    @Test fun nightShiftLearnsADaytimeBoundary() {
        val records = (0L..7L).map { day(it, 0 to 120, 360 to 420, 1020 to 1050, 1380 to 1440) }
        assertEquals(1020 to 1860, calculate(records))
    }

    @Test fun missingOrAggregateOnlyMorningIsNotAssumedToBeUnused() {
        val incomplete = listOf(day(0, 480 to 500, 1320 to 1340), StatisticsCalculator.emptyDay(start.plusDays(1)))
        assertNull(calculate(incomplete, lastDay = 1))
        assertNull(calculate(listOf(day(0, 480 to 500, 1320 to 1340)), lastDay = 1))
    }

    @Test fun followingDayCompletesWindowWithoutEnteringSelectedStatistics() {
        val records = listOf(day(0, 480 to 500, 1320 to 1440), day(1, 0 to 120, 480 to 500))
        assertEquals(480 to 1560, calculate(records, lastDay = 0))
    }

    @Test fun noActivityHasNoWindow() {
        assertNull(calculate((0L..7L).map { day(it) }))
    }

    @Test fun continuousActivityRetainsAFullClockSpan() {
        val result = calculate((0L..7L).map { day(it, 0 to 1440) })!!
        assertEquals(1440, result.second - result.first)
    }

    @Test fun nextDayActivityDoesNotChangeSelectedTotals() {
        val range = StatisticsRange(start, start.plusDays(1))
        val selected = listOf(day(0, 480 to 500, 1320 to 1440), day(1, 0 to 60, 480 to 500, 1320 to 1440))
        val result = StatisticsCalculator.calculate(range, selected + day(2, 0 to 60, 480 to 600),
            emptyList(), { app.still.data.settings.AppCategory.Social }, app.still.domain.model.StatisticsPeriod.Custom)
        assertEquals(selected.sumOf { it.screenTime!!.toMillis() }, result.total!!.toMillis())
        assertEquals(2, result.days.size)
        assertEquals(1500, result.lastUseTypicalMinute)
    }
}
