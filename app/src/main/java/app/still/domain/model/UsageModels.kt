package app.still.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class UsageEventType {
    ActivityResumed,
    ActivityPaused,
    ScreenInteractive,
    ScreenNonInteractive,
    KeyguardHidden,
    KeyguardShown,
}

data class UsageEventRecord(
    val timestamp: Instant,
    val type: UsageEventType,
    val packageName: String? = null,
)

data class ForegroundInterval(
    val packageName: String,
    val start: Instant,
    val end: Instant,
    val countsAsOpen: Boolean = true,
) {
    val duration: Duration get() = Duration.between(start, end).coerceAtLeast(Duration.ZERO)
}

data class AppInfo(
    val packageName: String,
    val label: String,
)

data class AppUsage(
    val app: AppInfo,
    val duration: Duration,
    val opens: Int,
)

data class SessionAppUsage(
    val app: AppInfo,
    val duration: Duration,
)

data class UsageSession(
    val start: Instant,
    val end: Instant,
    val apps: List<SessionAppUsage>,
    val sequence: List<AppInfo>,
) {
    val duration: Duration get() = Duration.between(start, end).coerceAtLeast(Duration.ZERO)
    val isQuickCheck: Boolean get() = duration <= Duration.ofSeconds(60)
}

enum class DaylineKind { Active, ScreenOff, Idle }

data class DaylineSegment(
    val start: Instant,
    val end: Instant,
    val kind: DaylineKind,
)

data class UsageComparison(
    val difference: Duration,
    val baseline: Duration,
)

data class ChangedApp(
    val app: AppInfo,
    val difference: Duration,
)

data class DailyUsage(
    val date: LocalDate,
    val rangeStart: Instant,
    val rangeEnd: Instant,
    val total: Duration,
    val apps: List<AppUsage>,
    val sessions: List<UsageSession>,
    val unlocks: Int,
    val wakeups: Int,
    val longestBreak: Duration?,
    val dayline: List<DaylineSegment>,
)

data class AppDetail(
    val date: LocalDate,
    val usage: AppUsage,
    val dailyUsage: List<DailyAppUsage>,
    val averageDaily: Duration?,
    val sessions: List<UsageSession>,
)

data class DailyAppUsage(
    val date: LocalDate,
    val duration: Duration?,
)

data class UsageDashboard(
    val today: DailyUsage,
    val comparison: UsageComparison?,
    val mostChanged: ChangedApp?,
    val history: List<DailyUsage>,
)

private fun Duration.coerceAtLeast(minimum: Duration): Duration = if (this < minimum) minimum else this
