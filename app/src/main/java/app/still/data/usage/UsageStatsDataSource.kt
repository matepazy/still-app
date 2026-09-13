package app.still.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import app.still.domain.model.UsageEventRecord
import app.still.domain.model.UsageEventType
import java.time.Instant

data class SystemDailyAppUsage(
    val bucketStart: Instant,
    val packageName: String,
    val foregroundDurationMillis: Long,
)

class UsageStatsDataSource(context: Context) {
    private val manager = context.getSystemService(UsageStatsManager::class.java)

    fun events(start: Instant, end: Instant): List<UsageEventRecord> {
        val usageEvents = manager.queryEvents(start.toEpochMilli(), end.toEpochMilli())
        val event = UsageEvents.Event()
        return buildList {
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val type = event.eventType.toDomainType() ?: continue
                add(
                    UsageEventRecord(
                        timestamp = Instant.ofEpochMilli(event.timeStamp),
                        type = type,
                        packageName = event.packageName?.takeIf { type.isAppEvent },
                    ),
                )
            }
        }
    }

    /**
     * Android keeps coarser UsageStats buckets longer than it keeps UsageEvents.
     * Reading daily buckets lets Still salvage the oldest history still present on
     * the device even when session-level events have already been pruned.
     */
    fun dailyAppUsage(start: Instant, end: Instant): List<SystemDailyAppUsage> =
        manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            start.toEpochMilli(),
            end.toEpochMilli(),
        ).orEmpty().mapNotNull { stats ->
            val duration = stats.totalTimeInForeground
            val packageName = stats.packageName
            if (duration <= 0L || packageName.isNullOrBlank()) null else {
                SystemDailyAppUsage(
                    bucketStart = Instant.ofEpochMilli(stats.firstTimeStamp),
                    packageName = packageName,
                    foregroundDurationMillis = duration,
                )
            }
        }

    private fun Int.toDomainType(): UsageEventType? = when (this) {
        UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.ActivityResumed
        UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventType.ActivityPaused
        UsageEvents.Event.SCREEN_INTERACTIVE -> UsageEventType.ScreenInteractive
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.ScreenNonInteractive
        UsageEvents.Event.KEYGUARD_HIDDEN -> UsageEventType.KeyguardHidden
        UsageEvents.Event.KEYGUARD_SHOWN -> UsageEventType.KeyguardShown
        else -> null
    }

    private val UsageEventType.isAppEvent: Boolean
        get() = this == UsageEventType.ActivityResumed || this == UsageEventType.ActivityPaused
}
