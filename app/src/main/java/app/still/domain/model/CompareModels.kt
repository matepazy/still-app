package app.still.domain.model

import java.time.LocalDate

data class CompareSharing(
    val screenTime: Boolean = true,
    val patterns: Boolean = true,
    val categories: Boolean = true,
    val apps: Boolean = false,
) {
    fun flags(): Int = (if (screenTime) 1 else 0) or (if (patterns) 2 else 0) or
        (if (categories) 4 else 0) or (if (apps) 8 else 0)
    companion object {
        fun fromFlags(flags: Int): CompareSharing {
            require(flags in 0..15 && flags != 0)
            return CompareSharing(flags and 1 != 0, flags and 2 != 0, flags and 4 != 0, flags and 8 != 0)
        }
    }
}

data class CompareCategory(val name: String, val millis: Long)
data class CompareApp(val label: String, val millis: Long)

data class CompareSnapshot(
    val version: Int,
    val sessionId: String,
    val replyTo: String?,
    val rangeStart: String,
    val rangeEnd: String,
    val cutoffEpochMillis: Long?,
    val sharingFlags: Int,
    val totalScreenTimeMillis: Long?,
    val averageDailyScreenTimeMillis: Long?,
    val checkIns: Int?,
    val quickChecks: Int?,
    val longestBreakMillis: Long?,
    val categories: List<CompareCategory>?,
    val apps: List<CompareApp>?,
    val screenTimeDays: Int? = null,
) {
    val range: StatisticsRange get() = StatisticsRange(LocalDate.parse(rangeStart), LocalDate.parse(rangeEnd))
    val sharing: CompareSharing get() = CompareSharing.fromFlags(sharingFlags)
}

data class CompareResult(val you: CompareSnapshot, val friend: CompareSnapshot) {
    init {
        require(you.sessionId == friend.sessionId)
        require(you.range == friend.range)
        require(you.cutoffEpochMillis == friend.cutoffEpochMillis)
    }
}
