package app.still.domain.analytics

import app.still.domain.model.AppInfo
import app.still.domain.model.DaylineKind
import app.still.domain.model.DaylineSegment
import app.still.domain.model.DailyUsage
import app.still.domain.model.ForegroundInterval
import app.still.domain.model.UsageEventRecord
import app.still.domain.model.UsageEventType
import app.still.domain.model.UsageSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class UsageAnalyticsTest {
    private val start = Instant.parse("2026-09-09T00:00:00Z")
    private val end = start.plus(Duration.ofDays(1))
    private fun at(minutes: Long) = start.plus(Duration.ofMinutes(minutes))
    private fun event(minutes: Long, type: UsageEventType, pkg: String? = null) = UsageEventRecord(at(minutes), type, pkg)
    private val info: (String) -> AppInfo = { AppInfo(it, it.substringAfterLast('.')) }

    @Test fun ordinaryForegroundBackgroundSession() {
        val intervals = ForegroundIntervalReconstructor.reconstruct(listOf(event(10, UsageEventType.ActivityResumed, "app.one"), event(25, UsageEventType.ActivityPaused, "app.one")), start, end)
        assertEquals(1, intervals.size)
        assertEquals(Duration.ofMinutes(15), intervals.single().duration)
    }

    @Test fun switchingAppsClosesPreviousWithoutCreatingPhoneSession() {
        val events = listOf(event(10, UsageEventType.ActivityResumed, "app.one"), event(15, UsageEventType.ActivityResumed, "app.two"), event(20, UsageEventType.ActivityPaused, "app.two"))
        val intervals = ForegroundIntervalReconstructor.reconstruct(events, start, end)
        assertEquals(listOf("app.one", "app.two"), intervals.map { it.packageName })
        assertEquals(1, SessionAnalyzer.groupSessions(intervals, events, info).size)
    }

    @Test fun duplicateResumeIsIgnored() {
        val intervals = ForegroundIntervalReconstructor.reconstruct(listOf(event(10, UsageEventType.ActivityResumed, "app.one"), event(12, UsageEventType.ActivityResumed, "app.one"), event(20, UsageEventType.ActivityPaused, "app.one")), start, end)
        assertEquals(1, intervals.size)
        assertEquals(Duration.ofMinutes(10), intervals.single().duration)
    }

    @Test fun missingPauseEndsAtQueryBoundary() {
        val rangeEnd = at(30)
        val intervals = ForegroundIntervalReconstructor.reconstruct(listOf(event(10, UsageEventType.ActivityResumed, "app.one")), start, rangeEnd)
        assertEquals(rangeEnd, intervals.single().end)
    }

    @Test fun screenOffEndsForegroundInterval() {
        val intervals = ForegroundIntervalReconstructor.reconstruct(listOf(event(10, UsageEventType.ActivityResumed, "app.one"), event(18, UsageEventType.ScreenNonInteractive)), start, end)
        assertEquals(at(18), intervals.single().end)
    }

    @Test fun shortScreenLockStaysInSameCheckIn() {
        val events = listOf(
            event(10, UsageEventType.ActivityResumed, "app.one"),
            event(12, UsageEventType.ScreenNonInteractive),
            event(17, UsageEventType.KeyguardHidden),
            event(17, UsageEventType.ActivityResumed, "app.one"),
            event(20, UsageEventType.ActivityPaused, "app.one"),
        )
        val intervals = ForegroundIntervalReconstructor.reconstruct(events, start, end)
        assertEquals(1, SessionAnalyzer.groupSessions(intervals, events, info).size)
    }

    @Test fun screenLockLongerThanTenMinutesStartsNewCheckIn() {
        val events = listOf(
            event(10, UsageEventType.ActivityResumed, "app.one"),
            event(12, UsageEventType.ScreenNonInteractive),
            event(23, UsageEventType.KeyguardHidden),
            event(23, UsageEventType.ActivityResumed, "app.one"),
            event(25, UsageEventType.ActivityPaused, "app.one"),
        )
        val intervals = ForegroundIntervalReconstructor.reconstruct(events, start, end)
        assertEquals(2, SessionAnalyzer.groupSessions(intervals, events, info).size)
    }

    @Test fun exactlyTenMinuteScreenLockStaysInSameCheckIn() {
        val events = listOf(
            event(10, UsageEventType.ActivityResumed, "app.one"),
            event(12, UsageEventType.ScreenNonInteractive),
            event(22, UsageEventType.KeyguardHidden),
            event(22, UsageEventType.ActivityResumed, "app.one"),
            event(25, UsageEventType.ActivityPaused, "app.one"),
        )
        val intervals = ForegroundIntervalReconstructor.reconstruct(events, start, end)
        assertEquals(1, SessionAnalyzer.groupSessions(intervals, events, info).size)
    }

    @Test fun frequentAppSwitchPairsAreStoredWithoutDirection() {
        val events = listOf(
            event(10, UsageEventType.ActivityResumed, "app.one"),
            event(11, UsageEventType.ActivityResumed, "app.two"),
            event(12, UsageEventType.ActivityResumed, "app.one"),
            event(13, UsageEventType.ActivityResumed, "app.two"),
            event(14, UsageEventType.ActivityPaused, "app.two"),
        )
        val sessions = SessionAnalyzer.groupSessions(
            ForegroundIntervalReconstructor.reconstruct(events, start, end),
            events,
            info,
        )
        val pair = SessionAnalyzer.frequentSwitches(sessions).single()
        assertEquals("app.one", pair.firstPackage)
        assertEquals("app.two", pair.secondPackage)
        assertEquals(3, pair.switchCount)
    }

    @Test fun dailyPatternSummaryKeepsLongTermStatisticsPoints() {
        val one = AppInfo("app.one", "one")
        val two = AppInfo("app.two", "two")
        val sessions = listOf(
            UsageSession(at(30), at(31), emptyList(), listOf(one)),
            UsageSession(at(90), at(100), emptyList(), listOf(one, two, one)),
        )
        val usage = DailyUsage(
            date = LocalDate.of(2026, 9, 9),
            rangeStart = start,
            rangeEnd = end,
            total = Duration.ofMinutes(30),
            apps = emptyList(),
            sessions = sessions,
            unlocks = 2,
            wakeups = 3,
            longestBreak = Duration.ofHours(1),
            dayline = listOf(
                DaylineSegment(at(50), at(70), DaylineKind.Active),
                DaylineSegment(at(90), at(100), DaylineKind.Active),
            ),
        )

        val summary = UsagePatternSummarizer.summarize(usage, ZoneOffset.UTC)

        assertEquals(2, summary.sessionCount)
        assertEquals(1, summary.quickCheckCount)
        assertEquals(Duration.ofMinutes(10).toMillis(), summary.longestSessionMillis)
        assertEquals(at(30).toEpochMilli(), summary.firstUseMillis)
        assertEquals(at(100).toEpochMilli(), summary.lastUseMillis)
        assertEquals(2, summary.switchCount)
        assertEquals(Duration.ofMinutes(10).toMillis(), summary.hourlyUsageMillis[0])
        assertEquals(Duration.ofMinutes(20).toMillis(), summary.hourlyUsageMillis[1])
    }

    @Test fun quickCheckAtSixtySecondsIsQuick() {
        val events = listOf(UsageEventRecord(at(10), UsageEventType.ActivityResumed, "app.one"), UsageEventRecord(at(10).plusSeconds(60), UsageEventType.ActivityPaused, "app.one"))
        val sessions = SessionAnalyzer.groupSessions(ForegroundIntervalReconstructor.reconstruct(events, start, end), events, info)
        assertTrue(sessions.single().isQuickCheck)
    }

    @Test fun sessionLongerThanSixtySecondsIsNotQuick() {
        val events = listOf(event(10, UsageEventType.ActivityResumed, "app.one"), event(12, UsageEventType.ActivityPaused, "app.one"))
        val sessions = SessionAnalyzer.groupSessions(ForegroundIntervalReconstructor.reconstruct(events, start, end), events, info)
        assertFalse(sessions.single().isQuickCheck)
    }

    @Test fun multipleUnlocksAreCountedAndDuplicatesDebounced() {
        val events = listOf(event(1, UsageEventType.KeyguardHidden), UsageEventRecord(at(1).plusSeconds(2), UsageEventType.KeyguardHidden), event(30, UsageEventType.KeyguardHidden), event(90, UsageEventType.KeyguardHidden))
        assertEquals(3, SessionAnalyzer.countUnlocks(events))
    }

    @Test fun eventAfterDayBoundaryIsExcluded() {
        val dayEnd = start.plus(Duration.ofDays(1))
        val intervals = ForegroundIntervalReconstructor.reconstruct(listOf(UsageEventRecord(dayEnd.minusSeconds(30), UsageEventType.ActivityResumed, "app.one"), UsageEventRecord(dayEnd.plusSeconds(30), UsageEventType.ActivityPaused, "app.one")), start, dayEnd)
        assertEquals(Duration.ofSeconds(30), intervals.single().duration)
        assertEquals(dayEnd, intervals.single().end)
    }

    @Test fun foregroundSessionSpanningMidnightIsClippedToDayStart() {
        val previousDayResume = UsageEventRecord(start.minusSeconds(45), UsageEventType.ActivityResumed, "app.one")
        val pause = UsageEventRecord(start.plusSeconds(30), UsageEventType.ActivityPaused, "app.one")
        val intervals = ForegroundIntervalReconstructor.reconstruct(listOf(previousDayResume, pause), start, end)
        assertEquals(start, intervals.single().start)
        assertEquals(Duration.ofSeconds(30), intervals.single().duration)
        assertFalse(intervals.single().countsAsOpen)
    }

    @Test fun baselineUsesPreviousDaysAtSameTime() {
        val comparison = BaselineCalculator.compare(Duration.ofMinutes(120), listOf(Duration.ofMinutes(150), Duration.ofMinutes(180), Duration.ofMinutes(120)))
        assertEquals(Duration.ofMinutes(150), comparison?.baseline)
        assertEquals(Duration.ofMinutes(-30), comparison?.difference)
    }

    @Test fun daylineUsesOnlyActiveAndInactiveSegmentsAndStopsAtRangeEnd() {
        val rangeEnd = at(180)
        val segments = DaylineBuilder.build(
            rangeStart = start,
            rangeEnd = rangeEnd,
            intervals = listOf(ForegroundInterval("app.one", at(60), at(90))),
        )

        assertEquals(listOf(DaylineKind.Inactive, DaylineKind.Active, DaylineKind.Inactive), segments.map { it.kind })
        assertEquals(rangeEnd, segments.last().end)
    }

    @Test fun insufficientBaselineDoesNotManufactureComparison() {
        assertEquals(null, BaselineCalculator.compare(Duration.ofMinutes(10), listOf(Duration.ofMinutes(20), Duration.ofMinutes(30))))
    }

    @Test fun systemLaunchersAreRecognizedWithoutFilteringOrdinaryApps() {
        assertTrue(SystemPackageFilter.isLauncher("com.google.android.apps.nexuslauncher"))
        assertTrue(SystemPackageFilter.isLauncher("com.example.customhome", setOf("com.example.customhome")))
        assertFalse(SystemPackageFilter.isLauncher("com.google.android.youtube"))
    }

    @Test fun stillIsIncludedInUsageAlongsideOtherUserApps() {
        assertFalse(SystemPackageFilter.shouldExcludeFromUsage("app.still"))
        assertFalse(SystemPackageFilter.shouldExcludeFromUsage("com.google.android.youtube"))
        assertTrue(SystemPackageFilter.shouldExcludeFromUsage("com.android.systemui"))
        assertTrue(SystemPackageFilter.shouldExcludeFromUsage("com.example.customhome", setOf("com.example.customhome")))
    }
}
