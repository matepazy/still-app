package app.still.ui

import app.still.domain.model.AppInfo
import app.still.domain.model.AppUsage
import app.still.domain.model.DailyUsage
import app.still.domain.model.UsageDashboard
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class AppDetailBuilderTest {
    private val app = AppInfo("app.example", "Example")
    private val windowStart = LocalDate.of(2026, 9, 9)

    @Test
    fun selectingAnotherBarKeepsTheFullRollingSevenDayWindow() {
        val days = (0L..6L).map { offset -> day(windowStart.plusDays(offset), offset + 1) }
        val dashboard = UsageDashboard(
            today = days.last(),
            comparison = null,
            mostChanged = null,
            history = days.dropLast(1),
        )

        val firstSelection = requireNotNull(buildAppDetail(app.packageName, dashboard, windowStart.plusDays(4)))
        val secondSelection = requireNotNull(buildAppDetail(app.packageName, dashboard, windowStart.plusDays(1)))

        assertEquals((0L..6L).map(windowStart::plusDays), firstSelection.dailyUsage.map { it.date })
        assertEquals(firstSelection.dailyUsage.map { it.date }, secondSelection.dailyUsage.map { it.date })
        assertEquals(Duration.ofMinutes(5), firstSelection.usage.duration)
        assertEquals(Duration.ofMinutes(2), secondSelection.usage.duration)
    }

    private fun day(date: LocalDate, minutes: Long): DailyUsage {
        val start = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        return DailyUsage(
            date = date,
            rangeStart = start,
            rangeEnd = start.plus(Duration.ofDays(1)),
            total = Duration.ofMinutes(minutes),
            apps = listOf(AppUsage(app, Duration.ofMinutes(minutes), minutes.toInt())),
            sessions = emptyList(),
            unlocks = 1,
            wakeups = 1,
            longestBreak = null,
            dayline = emptyList(),
        )
    }
}
