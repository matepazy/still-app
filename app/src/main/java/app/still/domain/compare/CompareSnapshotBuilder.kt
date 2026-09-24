package app.still.domain.compare

import app.still.data.settings.AppCategory
import app.still.domain.model.*
import java.time.Instant
import java.util.UUID

object CompareSnapshotBuilder {
    fun build(
        range: StatisticsRange,
        days: List<StatisticsDay>,
        sharing: CompareSharing,
        categoryOf: (String) -> AppCategory,
        sessionId: String = UUID.randomUUID().toString().replace("-", ""),
        cutoff: Instant? = null,
        replyTo: String? = null,
        requestedApps: List<String>? = null,
    ): CompareSnapshot {
        require(range.days in 1..3660)
        val valid = days.mapNotNull { it.screenTime?.toMillis() }
        val apps = days.flatMap { it.apps.orEmpty() }
        val appTotals = if (sharing.apps) apps.groupBy { usage ->
            usage.app.label.takeUnless { it == usage.app.packageName ||
                it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) } ?: "Unknown app"
        }.mapValues { (_, values) -> values.sumOf { it.duration.toMillis() } } else emptyMap()
        val sharedApps = when {
            !sharing.apps -> null
            requestedApps != null -> requestedApps.distinct().take(8).map { label ->
                CompareApp(label.take(50), appTotals[label] ?: 0L)
            }
            else -> appTotals.map { (label, millis) -> CompareApp(label.take(50), millis) }
                .sortedByDescending { it.millis }.take(8)
        }
        return CompareSnapshot(
            version = 1, sessionId = sessionId, replyTo = replyTo,
            rangeStart = range.start.toString(), rangeEnd = range.endInclusive.toString(),
            cutoffEpochMillis = cutoff?.toEpochMilli(), sharingFlags = sharing.flags(),
            totalScreenTimeMillis = if (sharing.screenTime && valid.isNotEmpty()) valid.sum() else null,
            averageDailyScreenTimeMillis = if (sharing.screenTime && valid.isNotEmpty()) valid.average().toLong() else null,
            checkIns = if (sharing.patterns) days.mapNotNull { it.checkIns }.takeIf { it.isNotEmpty() }?.sum() else null,
            quickChecks = if (sharing.patterns) days.mapNotNull { it.quickChecks }.takeIf { it.isNotEmpty() }?.sum() else null,
            longestBreakMillis = if (sharing.patterns) days.mapNotNull { it.longestBreak?.toMillis() }.maxOrNull() else null,
            categories = if (sharing.categories) apps.groupBy { categoryOf(it.app.packageName).displayName }
                .map { (name, values) -> CompareCategory(name, values.sumOf { it.duration.toMillis() }) }
                .sortedByDescending { it.millis } else null,
            apps = sharedApps,
            screenTimeDays = if (sharing.screenTime && valid.isNotEmpty()) valid.size else null,
        )
    }
}

object CompareEngine {
    fun result(local: CompareSnapshot, remote: CompareSnapshot, firstCodeHash: String?): CompareResult {
        require(local.sessionId == remote.sessionId) { "This reply belongs to another comparison." }
        require(local.range == remote.range && local.cutoffEpochMillis == remote.cutoffEpochMillis) { "The comparison periods differ." }
        if (firstCodeHash != null) require(remote.replyTo == firstCodeHash) { "This reply is for another code." }
        return CompareResult(local, remote)
    }
}
