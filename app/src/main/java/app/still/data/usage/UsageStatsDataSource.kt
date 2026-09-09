package app.still.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import app.still.domain.model.UsageEventRecord
import app.still.domain.model.UsageEventType
import java.time.Instant

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
