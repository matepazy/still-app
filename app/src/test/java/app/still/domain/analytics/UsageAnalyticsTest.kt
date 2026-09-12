package app.still.domain.analytics

import app.still.domain.model.AppInfo
import app.still.domain.model.UsageEventRecord
import app.still.domain.model.UsageEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

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
