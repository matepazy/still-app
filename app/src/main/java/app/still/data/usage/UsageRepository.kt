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
import app.still.domain.model.AppUsage
import app.still.domain.model.DailyUsage
import app.still.domain.model.DailyAppUsage
import app.still.domain.model.UsageDashboard
import app.still.domain.model.StatisticsDay
import app.still.domain.model.StatisticsRange
import app.still.data.settings.AppCategory
import app.still.domain.statistics.StatisticsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class UsageRepository(
    private val context: Context,
    private val dataSource: UsageDataSource,
    private val archive: LocalUsageArchive = LocalUsageArchive(context),
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val syncMutex = Mutex()
    // Accessed under syncMutex; only completed days are retained.
    private val completedDays = mutableMapOf<LocalDate, DailyUsage>()
    private val recentEvents = mutableMapOf<LocalDate, List<app.still.domain.model.UsageEventRecord>?>()
    private var historyZone: ZoneId? = null
    private var historyToday: LocalDate? = null
    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val metadata = mutableMapOf<String, AppInfo>()
    private val categoryCache = mutableMapOf<String, AppCategory>()
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

    /** Current usage does not wait for archive synchronization or historical queries. */
    suspend fun todayUsage(): Result<DailyUsage> = withContext(Dispatchers.IO) {
        runCatching { readToday(clock.instant(), ZoneId.systemDefault()) }
    }

    private fun readToday(now: java.time.Instant, zone: ZoneId): DailyUsage {
        val today = now.atZone(zone).toLocalDate()
        val start = today.atStartOfDay(zone).toInstant()
        return buildDay(today, now, zone, dataSource.events(start.minus(Duration.ofHours(24)), now))
    }

    suspend fun dashboard(
        saveHistory: Boolean = true,
        onToday: suspend (DailyUsage) -> Unit = {},
    ): Result<UsageDashboard> = withContext(Dispatchers.IO) {
        runCatching {
            val zone = ZoneId.systemDefault()
            val now = clock.instant()
            val today = now.atZone(zone).toLocalDate()
            val current = readToday(now, zone)
            currentCoroutineContext().ensureActive()
            onToday(current)
            if (!saveHistory) return@runCatching UsageDashboard(current, null, null, emptyList())
            syncMutex.withLock {
                // Take the sync cutoff after acquiring the lock so a queued refresh cannot
                // overwrite newer archive data with an older snapshot.
                synchronizeArchive(clock.instant(), zone)
                currentCoroutineContext().ensureActive()
                val history = loadHistory(today, zone)
                val valid = (1L..BASELINE_DAYS.toLong()).mapNotNull { offset ->
                    val date = today.minusDays(offset)
                    val events = recentEvents[date] ?: return@mapNotNull null
                    val cutoff = date.atTime(now.atZone(zone).toLocalTime()).atZone(zone).toInstant()
                    buildDay(date, cutoff, zone, recentEvents[date.minusDays(1)].orEmpty() + events)
                }
                UsageDashboard(
                    today = current,
                    comparison = BaselineCalculator.compare(current.total, valid.map { it.total }),
                    mostChanged = BaselineCalculator.mostChanged(current.apps, valid.map { it.apps }),
                    history = history,
                )
            }
        }
    }

    private fun loadHistory(today: LocalDate, zone: ZoneId): List<DailyUsage> {
        if (historyZone != zone || historyToday != today) invalidateHistory()
        historyZone = zone
        historyToday = today
        val dates = archive.dates().filter { it.isBefore(today) }
        completedDays.keys.retainAll(dates.toSet())
        val missing = dates.filter { it !in completedDays }
        val baselineStart = today.minusDays(BASELINE_DAYS.toLong() + 1)
        val eventDates = dates.filter { !it.isBefore(baselineStart) && it.isBefore(today) && it !in recentEvents }
        val needed = (missing + missing.map { it.minusDays(1) } + eventDates).distinct()
        val snapshots = archive.snapshots(needed)
        snapshots.values.forEach { snapshot ->
            snapshot.day.apps.forEach { usage ->
                synchronized(metadata) { metadata.putIfAbsent(usage.app.packageName, usage.app) }
            }
        }
        missing.forEach { date ->
            val snapshot = snapshots[date] ?: return@forEach
            completedDays[date] = snapshot.events?.let { events ->
                buildDay(date, date.plusDays(1).atStartOfDay(zone).toInstant(), zone,
                    snapshots[date.minusDays(1)]?.events.orEmpty() + events)
            } ?: snapshot.day
        }
        snapshots.forEach { (date, snapshot) ->
            if (!date.isBefore(baselineStart) && date.isBefore(today)) recentEvents[date] = snapshot.events
        }
        return dates.mapNotNull(completedDays::get)
    }

    private fun invalidateHistory() {
        completedDays.clear()
        recentEvents.clear()
    }

    private fun invalidateDay(date: LocalDate) {
        completedDays.remove(date)
        // A changed lookback can also affect the next day's midnight-spanning session.
        completedDays.remove(date.plusDays(1))
        recentEvents.remove(date)
    }

    suspend fun syncHistory(saveHistory: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            syncMutex.withLock {
                archive.importRollbackUntilMillis()
                if (saveHistory) synchronizeArchive(clock.instant(), ZoneId.systemDefault())
            }
        }
    }

    suspend fun statisticsDays(range: StatisticsRange, cutoff: java.time.Instant? = null): List<StatisticsDay> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(clock)
        val available = archive.dates().filter { !it.isBefore(range.start) && !it.isAfter(range.endInclusive) }.toSet()
        (0 until range.days).map { offset ->
            val date = range.start.plusDays(offset)
            if (date !in available && date != today) return@map StatisticsCalculator.emptyDay(date)
            val end = when {
                date == today -> minOf(clock.instant(), cutoff ?: clock.instant())
                else -> date.plusDays(1).atStartOfDay(zone).toInstant()
            }
            if (date == today && end.isBefore(date.atStartOfDay(zone).toInstant())) return@map StatisticsCalculator.emptyDay(date)
            val day = if (date == today) {
                val start = date.atStartOfDay(zone).toInstant()
                buildDay(date, end, zone, dataSource.events(start.minus(Duration.ofHours(24)), end))
            } else archive.day(date)?.let { stored ->
                    if (stored.detailsAvailable) loadDetailedDay(date, end, zone) ?: stored else stored
                }
            if (day == null) return@map StatisticsCalculator.emptyDay(date)
            val detailed = day.detailsAvailable
            val active = day.dayline.filter { it.kind == app.still.domain.model.DaylineKind.Active }
            val hours = if (detailed) (0..23).map { hour ->
                val start = date.atTime(hour, 0).atZone(zone).toInstant()
                val stop = date.atTime(hour, 0).plusHours(1).atZone(zone).toInstant()
                active.sumOf { segment ->
                    val overlapStart = maxOf(start, segment.start)
                    val overlapEnd = minOf(stop, segment.end)
                    Duration.between(overlapStart, overlapEnd).toMillis().coerceAtLeast(0)
                }
            } else null
            fun minute(instant: java.time.Instant): Int {
                val local = instant.atZone(zone)
                return (java.time.temporal.ChronoUnit.DAYS.between(date, local.toLocalDate()) * 1440 +
                    local.toLocalTime().toSecondOfDay() / 60).toInt().coerceIn(0, 1440)
            }
            StatisticsDay(
                date, day.total, if (detailed) day.checkInCount else null,
                if (detailed) day.quickCheckCount else null,
                if (detailed) day.unlocks else null, if (detailed) day.wakeups else null,
                if (detailed) day.longestBreak else null,
                if (detailed) day.sessions.maxOfOrNull { it.duration } else null,
                if (detailed) active.minByOrNull { it.start }?.start?.let(::minute) else null,
                if (detailed) active.maxByOrNull { it.end }?.end?.let(::minute) else null,
                hours,
                if (detailed) day.sessions.sumOf { (it.sequence.size - 1).coerceAtLeast(0) } else null,
                day.apps,
                if (detailed) day.sessions.size else null,
                if (detailed) day.sessions.sumOf { it.duration.toMillis() } else null,
                if (detailed) active.map { app.still.domain.model.UsageMinuteInterval(minute(it.start), minute(it.end)) } else null,
                minute(end),
            )
        }
    }

    fun categoryFor(packageName: String): AppCategory = synchronized(categoryCache) {
        categoryCache.getOrPut(packageName) { AppCategory.forPackage(packageManager, packageName) }
    }

    suspend fun clearHistory(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { syncMutex.withLock { invalidateHistory(); archive.clearHistory() } }
    }

    suspend fun storedDataSummary(): StoredDataSummary = withContext(Dispatchers.IO) {
        syncMutex.withLock { archive.storedDataSummary() }
    }

    suspend fun migrationNotice(): ArchiveMigrationNotice? = withContext(Dispatchers.IO) {
        syncMutex.withLock { archive.migrationNotice() }
    }

    suspend fun acknowledgeMigrationNotice() = withContext(Dispatchers.IO) {
        syncMutex.withLock { archive.acknowledgeMigrationNotice() }
    }

    suspend fun restoreLegacyBackup(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { syncMutex.withLock { invalidateHistory(); archive.restoreLegacyBackup() } }
    }

    suspend fun exportDatabase(destination: java.io.File) = withContext(Dispatchers.IO) {
        syncMutex.withLock { archive.exportSnapshot(destination) }
    }

    suspend fun importDatabase(source: java.io.File) = withContext(Dispatchers.IO) {
        syncMutex.withLock { invalidateHistory(); archive.importSnapshot(source) }
    }

    suspend fun rollbackDatabaseImport() = withContext(Dispatchers.IO) {
        syncMutex.withLock { invalidateHistory(); archive.rollbackImport() }
    }

    suspend fun upgradeLegacyArchive(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { syncMutex.withLock { invalidateHistory(); archive.upgradeLegacyArchive() } }
    }

    suspend fun deleteLegacyBackup(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { syncMutex.withLock { archive.deleteLegacyBackup() } }
    }

    suspend fun appDetail(packageName: String, dashboard: UsageDashboard): AppDetail? = withContext(Dispatchers.Default) {
        val usage = dashboard.today.apps.firstOrNull { it.app.packageName == packageName } ?: return@withContext null
        val daily = dashboard.history.sortedBy { it.date }.map { day ->
            val available = day.total > Duration.ZERO || day.unlocks > 0 || day.wakeups > 0
            DailyAppUsage(day.date, if (available) day.apps.firstOrNull { it.app.packageName == packageName }?.duration ?: Duration.ZERO else null)
        }
        val validPast = daily.asReversed().mapNotNull { it.duration }.take(BASELINE_DAYS)
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
        val checkIns = SessionAnalyzer.groupSessions(
            intervals,
            events,
            info,
            lockTolerance = Duration.ZERO,
        )
        val apps = AppUsageAggregator.aggregate(intervals, info)
        return DailyUsage(
            date = date,
            rangeStart = start,
            rangeEnd = end,
            total = intervals.fold(Duration.ZERO) { total, interval -> total.plus(interval.duration) },
            apps = apps,
            sessions = sessions,
            checkInCount = checkIns.size,
            quickCheckCount = checkIns.count { it.isQuickCheck },
            unlocks = SessionAnalyzer.countUnlocks(events),
            wakeups = SessionAnalyzer.countWakeups(events),
            longestBreak = SessionAnalyzer.longestBreak(intervals, end),
            dayline = DaylineBuilder.build(start, end, intervals),
        )
    }

    private fun synchronizeArchive(now: java.time.Instant, zone: ZoneId) {
        val previousSync = archive.metadata(LAST_SYNC_KEY)?.toLongOrNull()?.let(java.time.Instant::ofEpochMilli)
        val fullImport = archive.metadata(INITIAL_IMPORT_KEY) != "1"
        val queryStart = if (fullImport) java.time.Instant.EPOCH else {
            previousSync?.minus(Duration.ofHours(24)) ?: java.time.Instant.EPOCH
        }

        val aggregateDays = dataSource.dailyAppUsage(queryStart, now)
            .groupBy { it.bucketStart.atZone(zone).toLocalDate() }
        aggregateDays.forEach { (date, records) ->
            if (date.isAfter(now.atZone(zone).toLocalDate())) return@forEach
            val apps = records
                .filterNot { SystemPackageFilter.shouldExcludeFromUsage(it.packageName, launcherPackages) }
                .groupBy { it.packageName }
                .map { (packageName, values) ->
                    AppUsage(
                        app = resolveApp(packageName),
                        duration = Duration.ofMillis(values.sumOf { it.foregroundDurationMillis }),
                        opens = 0,
                    )
                }
                .filter { it.duration >= Duration.ofSeconds(2) }
                .sortedByDescending { it.duration }
            if (apps.isNotEmpty()) {
                val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
                archive.saveAggregate(date, end.toEpochMilli(), apps)
                invalidateDay(date)
            }
        }

        val queriedEvents = dataSource.events(queryStart, now)
        val eventsByDate = queriedEvents.groupBy { it.timestamp.atZone(zone).toLocalDate() }
        val today = now.atZone(zone).toLocalDate()
        val firstWritableDate = if (fullImport) LocalDate.MIN else previousSync?.atZone(zone)?.toLocalDate() ?: LocalDate.MIN
        val detailDates = eventsByDate.keys
            .filter { !it.isBefore(firstWritableDate) && !it.isAfter(today) }
            .sorted()
        detailDates.forEach { date ->
            val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
            // A 24-hour lookback can reach two calendar dates on a DST transition.
            val relevantEvents = eventsByDate[date.minusDays(2)].orEmpty() +
                eventsByDate[date.minusDays(1)].orEmpty() + eventsByDate[date].orEmpty()
            val day = buildDay(date, end, zone, relevantEvents)
            archive.saveDetailed(day, eventsByDate[date].orEmpty())
            invalidateDay(date)
        }

        archive.pruneUnusedApps()
        archive.putMetadata(INITIAL_IMPORT_KEY, "1")
        archive.putMetadata(LAST_SYNC_KEY, now.toEpochMilli().toString())
    }

    private fun loadDetailedDay(date: LocalDate, end: java.time.Instant, zone: ZoneId): DailyUsage? {
        val events = archive.eventRecords(date) ?: return null
        archive.day(date)?.apps?.forEach { usage ->
            synchronized(metadata) { metadata.putIfAbsent(usage.app.packageName, usage.app) }
        }
        val lookback = archive.eventRecords(date.minusDays(1)).orEmpty()
        return buildDay(date, end, zone, lookback + events)
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

    private companion object {
        const val BASELINE_DAYS = 14
        const val INITIAL_IMPORT_KEY = "initial_import_complete"
        const val LAST_SYNC_KEY = "last_event_sync_ms"
    }
}
