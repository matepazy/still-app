package app.still.data.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.os.Process
import app.still.domain.analytics.AppUsageAggregator
import app.still.domain.analytics.BaselineCalculator
import app.still.domain.analytics.DaylineBuilder
import app.still.domain.analytics.ForegroundIntervalReconstructor
import app.still.domain.analytics.SessionAnalyzer
import app.still.domain.analytics.SystemPackageFilter
import app.still.domain.model.AppDetail
import app.still.domain.model.AppInfo
import app.still.domain.model.DailyUsage
import app.still.domain.model.DailyAppUsage
import app.still.domain.model.UsageDashboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class UsageRepository(
    private val context: Context,
    private val dataSource: UsageStatsDataSource,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val metadata = mutableMapOf<String, AppInfo>()
    private val homePackage: String? by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }
    private val launcherPackages: Set<String> by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        buildSet {
            homePackage?.let(::add)
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNullTo(this) { it.activityInfo?.packageName }
        }
    }

    suspend fun dashboard(): Result<UsageDashboard> = withContext(Dispatchers.Default) {
        runCatching {
            val zone = ZoneId.systemDefault()
            val now = clock.instant()
            val today = LocalDate.now(clock)
            val localTime = now.atZone(zone).toLocalTime()
            val historyStart = today.minusDays(7).atStartOfDay(zone).toInstant().minus(Duration.ofHours(24))
            val allEvents = dataSource.events(historyStart, now)
            val current = buildDay(today, now, zone, allEvents)
            val history = (1L..7L).map { offset ->
                val date = today.minusDays(offset)
                val end = date.plusDays(1).atStartOfDay(zone).toInstant()
                buildDay(date, end, zone, allEvents)
            }
            val sameTimeHistory = (1L..7L).map { offset ->
                val date = today.minusDays(offset)
                val cutoff = date.atTime(localTime).atZone(zone).toInstant()
                buildDay(date, cutoff, zone, allEvents)
            }
            val valid = sameTimeHistory.filter { it.total > Duration.ZERO || it.unlocks > 0 || it.wakeups > 0 }
            UsageDashboard(
                today = current,
                comparison = BaselineCalculator.compare(current.total, valid.map { it.total }),
                mostChanged = BaselineCalculator.mostChanged(current.apps, valid.map { it.apps }),
                history = history,
            )
        }
    }

    suspend fun appDetail(packageName: String, dashboard: UsageDashboard): AppDetail? = withContext(Dispatchers.Default) {
        val usage = dashboard.today.apps.firstOrNull { it.app.packageName == packageName } ?: return@withContext null
        val daily = dashboard.history.sortedBy { it.date }.map { day ->
            val available = day.total > Duration.ZERO || day.unlocks > 0 || day.wakeups > 0
            DailyAppUsage(day.date, if (available) day.apps.firstOrNull { it.app.packageName == packageName }?.duration ?: Duration.ZERO else null)
        }
        val validPast = daily.mapNotNull { it.duration }
        val average = validPast.takeIf { it.isNotEmpty() }?.map { it.toMillis() }?.average()?.toLong()?.let(Duration::ofMillis)
        AppDetail(
            date = dashboard.today.date,
            usage = usage,
            dailyUsage = daily,
            averageDaily = average,
            sessions = dashboard.today.sessions.filter { session -> session.apps.any { it.app.packageName == packageName } },
        )
    }

    private fun buildDay(
        date: LocalDate,
        requestedEnd: java.time.Instant,
        zone: ZoneId,
        sourceEvents: List<app.still.domain.model.UsageEventRecord>,
    ): DailyUsage {
        val start = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val end = minOf(requestedEnd, dayEnd)
        val eventsWithLookback = sourceEvents.filter { !it.timestamp.isBefore(start.minus(Duration.ofHours(24))) && !it.timestamp.isAfter(end) }
        val events = eventsWithLookback.filter { !it.timestamp.isBefore(start) }
        val rawIntervals = ForegroundIntervalReconstructor.reconstruct(eventsWithLookback, start, end)
        val intervals = rawIntervals.filterNot { interval ->
            SystemPackageFilter.shouldExcludeFromUsage(interval.packageName, launcherPackages)
        }
        val info: (String) -> AppInfo = ::resolveApp
        val sessions = SessionAnalyzer.groupSessions(intervals, events, info)
        val apps = AppUsageAggregator.aggregate(intervals, info)
        return DailyUsage(
            date = date,
            rangeStart = start,
            rangeEnd = end,
            total = intervals.fold(Duration.ZERO) { total, interval -> total.plus(interval.duration) },
            apps = apps,
            sessions = sessions,
            unlocks = SessionAnalyzer.countUnlocks(events),
            wakeups = SessionAnalyzer.countWakeups(events),
            longestBreak = SessionAnalyzer.longestBreak(intervals, end),
            dayline = DaylineBuilder.build(start, end, intervals, events),
        )
    }

    private fun resolveApp(packageName: String): AppInfo = synchronized(metadata) {
        metadata.getOrPut(packageName) {
            val label = runCatching {
                launcherApps?.getActivityList(packageName, Process.myUserHandle())
                    ?.firstOrNull()
                    ?.label
                    ?.toString()
                    ?: run {
                        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                        packageManager.getApplicationLabel(applicationInfo).toString()
                    }
            }.getOrDefault(packageName)
            AppInfo(packageName, label)
        }
    }
}
