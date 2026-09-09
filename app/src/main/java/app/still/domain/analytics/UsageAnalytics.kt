package app.still.domain.analytics

import app.still.domain.model.AppInfo
import app.still.domain.model.AppUsage
import app.still.domain.model.ChangedApp
import app.still.domain.model.DaylineKind
import app.still.domain.model.DaylineSegment
import app.still.domain.model.ForegroundInterval
import app.still.domain.model.SessionAppUsage
import app.still.domain.model.UsageComparison
import app.still.domain.model.UsageEventRecord
import app.still.domain.model.UsageEventType
import app.still.domain.model.UsageSession
import java.time.Duration
import java.time.Instant

object ForegroundIntervalReconstructor {
    fun reconstruct(
        events: List<UsageEventRecord>,
        rangeStart: Instant,
        rangeEnd: Instant,
    ): List<ForegroundInterval> {
        if (!rangeEnd.isAfter(rangeStart)) return emptyList()
        val result = mutableListOf<ForegroundInterval>()
        var activePackage: String? = null
        var activeStart: Instant? = null
        var activeCountsAsOpen = true

        fun close(at: Instant) {
            val packageName = activePackage ?: return
            val start = activeStart ?: return
            val boundedEnd = minOf(at, rangeEnd)
            if (boundedEnd.isAfter(start)) result += ForegroundInterval(packageName, start, boundedEnd, activeCountsAsOpen)
            activePackage = null
            activeStart = null
            activeCountsAsOpen = true
        }

        events.asSequence()
            .filter { !it.timestamp.isAfter(rangeEnd) }
            .sortedBy { it.timestamp }
            .forEach { event ->
                when (event.type) {
                    UsageEventType.ActivityResumed -> {
                        val next = event.packageName ?: return@forEach
                        if (activePackage == next) {
                            if (!event.timestamp.isBefore(rangeStart)) activeCountsAsOpen = true
                            return@forEach
                        }
                        close(event.timestamp)
                        activePackage = next
                        activeStart = maxOf(event.timestamp, rangeStart)
                        activeCountsAsOpen = !event.timestamp.isBefore(rangeStart)
                    }
                    UsageEventType.ActivityPaused -> if (event.packageName == activePackage) close(event.timestamp)
                    UsageEventType.ScreenNonInteractive -> close(event.timestamp)
                    else -> Unit
                }
            }
        close(rangeEnd)
        return result
    }
}

object SessionAnalyzer {
    private val defaultSessionGap = Duration.ofSeconds(90)

    fun groupSessions(
        intervals: List<ForegroundInterval>,
        events: List<UsageEventRecord>,
        appInfo: (String) -> AppInfo,
        meaningfulGap: Duration = defaultSessionGap,
    ): List<UsageSession> {
        if (intervals.isEmpty()) return emptyList()
        val ordered = intervals.sortedBy { it.start }
        val groups = mutableListOf<MutableList<ForegroundInterval>>()

        ordered.forEach { interval ->
            val current = groups.lastOrNull()
            val previous = current?.lastOrNull()
            val boundary = previous != null && events.any {
                !it.timestamp.isBefore(previous.end) && it.timestamp <= interval.start &&
                    (it.type == UsageEventType.ScreenNonInteractive || it.type == UsageEventType.KeyguardHidden)
            }
            val gap = previous?.let { Duration.between(it.end, interval.start) }
            if (current == null || previous == null || boundary || (gap != null && gap > meaningfulGap)) {
                groups += mutableListOf(interval)
            } else {
                current += interval
            }
        }

        return groups.map { group ->
            val byApp = group.groupBy { it.packageName }
                .map { (pkg, spans) -> SessionAppUsage(appInfo(pkg), sumDurations(spans.map { it.duration })) }
                .sortedByDescending { it.duration }
            val sequence = group.map { appInfo(it.packageName) }
                .fold(mutableListOf<AppInfo>()) { result, info ->
                    if (result.lastOrNull()?.packageName != info.packageName) result += info
                    result
                }
            UsageSession(group.first().start, group.maxOf { it.end }, byApp, sequence)
        }
    }

    fun countUnlocks(events: List<UsageEventRecord>): Int = countDebounced(events, UsageEventType.KeyguardHidden)

    fun countWakeups(events: List<UsageEventRecord>): Int = countDebounced(events, UsageEventType.ScreenInteractive)

    private fun countDebounced(events: List<UsageEventRecord>, type: UsageEventType): Int {
        var last: Instant? = null
        var count = 0
        events.asSequence().filter { it.type == type }.sortedBy { it.timestamp }.forEach { event ->
            if (last == null || Duration.between(last, event.timestamp) > Duration.ofSeconds(5)) {
                count++
                last = event.timestamp
            }
        }
        return count
    }

    fun longestBreak(intervals: List<ForegroundInterval>, now: Instant): Duration? {
        val ordered = intervals.sortedBy { it.start }
        if (ordered.isEmpty()) return null
        var activeEnd = ordered.first().end
        var longest = Duration.ZERO
        ordered.drop(1).forEach { interval ->
            if (interval.start > activeEnd) longest = maxOf(longest, Duration.between(activeEnd, interval.start))
            activeEnd = maxOf(activeEnd, interval.end)
        }
        if (now > activeEnd) longest = maxOf(longest, Duration.between(activeEnd, now))
        return longest.takeUnless { it.isZero }
    }
}

object AppUsageAggregator {
    fun aggregate(intervals: List<ForegroundInterval>, appInfo: (String) -> AppInfo): List<AppUsage> =
        intervals.groupBy { it.packageName }
            .map { (pkg, spans) -> AppUsage(appInfo(pkg), sumDurations(spans.map { it.duration }), spans.count { it.countsAsOpen }) }
            .filter { it.duration >= Duration.ofSeconds(2) }
            .sortedByDescending { it.duration }
}

object BaselineCalculator {
    fun compare(today: Duration, validPreviousDaysAtSameTime: List<Duration>): UsageComparison? {
        if (validPreviousDaysAtSameTime.size < 3) return null
        val averageMillis = validPreviousDaysAtSameTime.map { it.toMillis() }.average().toLong()
        val baseline = Duration.ofMillis(averageMillis)
        return UsageComparison(today.minus(baseline), baseline)
    }

    fun mostChanged(
        today: List<AppUsage>,
        previousDays: List<List<AppUsage>>,
    ): ChangedApp? {
        if (previousDays.size < 3) return null
        val packages = (today.map { it.app.packageName } + previousDays.flatten().map { it.app.packageName }).toSet()
        return packages.mapNotNull { pkg ->
            val current = today.firstOrNull { it.app.packageName == pkg }
            val info = current?.app ?: previousDays.flatten().firstOrNull { it.app.packageName == pkg }?.app ?: return@mapNotNull null
            val average = previousDays.map { day -> day.firstOrNull { it.app.packageName == pkg }?.duration?.toMillis() ?: 0L }.average().toLong()
            ChangedApp(info, Duration.ofMillis((current?.duration?.toMillis() ?: 0L) - average))
        }.maxByOrNull { kotlin.math.abs(it.difference.toMillis()) }
            ?.takeIf { kotlin.math.abs(it.difference.toMinutes()) >= 2 }
    }
}

object DaylineBuilder {
    fun build(
        rangeStart: Instant,
        rangeEnd: Instant,
        intervals: List<ForegroundInterval>,
        events: List<UsageEventRecord>,
    ): List<DaylineSegment> {
        if (!rangeEnd.isAfter(rangeStart)) return emptyList()
        val offRanges = mutableListOf<Pair<Instant, Instant>>()
        var offStart: Instant? = null
        events.sortedBy { it.timestamp }.forEach { event ->
            when (event.type) {
                UsageEventType.ScreenNonInteractive -> if (offStart == null) offStart = maxOf(event.timestamp, rangeStart)
                UsageEventType.ScreenInteractive -> offStart?.let {
                    if (event.timestamp > it) offRanges += it to minOf(event.timestamp, rangeEnd)
                    offStart = null
                }
                else -> Unit
            }
        }
        offStart?.let { if (rangeEnd > it) offRanges += it to rangeEnd }

        val points = buildSet {
            add(rangeStart); add(rangeEnd)
            intervals.forEach { add(maxOf(it.start, rangeStart)); add(minOf(it.end, rangeEnd)) }
            offRanges.forEach { add(it.first); add(it.second) }
        }.sorted()
        val raw = points.zipWithNext().mapNotNull { (start, end) ->
            if (!end.isAfter(start)) return@mapNotNull null
            val midpoint = start.plusMillis(Duration.between(start, end).toMillis() / 2)
            val kind = when {
                intervals.any { midpoint >= it.start && midpoint < it.end } -> DaylineKind.Active
                offRanges.any { midpoint >= it.first && midpoint < it.second } -> DaylineKind.ScreenOff
                else -> DaylineKind.Idle
            }
            DaylineSegment(start, end, kind)
        }
        return raw.fold(mutableListOf()) { merged, segment ->
            val last = merged.lastOrNull()
            if (last != null && last.kind == segment.kind && last.end == segment.start) {
                merged[merged.lastIndex] = last.copy(end = segment.end)
            } else merged += segment
            merged
        }
    }
}

internal fun sumDurations(durations: Iterable<Duration>): Duration =
    durations.fold(Duration.ZERO) { total, duration -> total.plus(duration) }
