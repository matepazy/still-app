package app.still.domain.statistics

import app.still.domain.model.StatisticsDay
import app.still.domain.model.StatisticsRange

/** A typical waking-day window, rather than an arithmetic average of calendar-day clocks. */
internal object TypicalUsageWindow {
    fun calculate(range: StatisticsRange, records: List<StatisticsDay>): Pair<Int, Int>? {
        val selected = records.filter { it.date >= range.start && it.date <= range.endInclusive }
        val complete = selected.filter { it.observedUntilMinute == 1440 }
        val profiles = complete.mapNotNull { day ->
            day.hourlyMillis?.takeIf { it.size == 24 && it.any { value -> value > 0 } }
        }
        if (profiles.isEmpty()) return null

        // Use a median activity profile once there are enough days to resist an
        // occasional late night. With fewer days, retain all observed activity.
        val profile = (0..23).map { hour ->
            val values = profiles.map { it[hour].toDouble() }.sorted()
            if (values.size < 3) values.average()
            else (values[(values.size - 1) / 2] + values[values.size / 2]) / 2
        }
        fun distanceFromFour(minute: Int): Int {
            val distance = kotlin.math.abs(minute - 240)
            return minOf(distance, 1440 - distance)
        }
        val quietBlocks = (0..23).map { start ->
            val length = (0..23).takeWhile { profile[(start + it) % 24] == 0.0 }.size
            length to ((start * 60 + length * 30) % 1440)
        }
        // Six quiet hours are already a plausible main rest. Longer phone-free
        // workdays should not outweigh a normal overnight rest merely by length.
        // Among equally plausible rests, prefer a boundary near 04:00.
        val longestQuiet = quietBlocks.minWithOrNull(compareByDescending<Pair<Int, Int>> { minOf(it.first, 6) }
            .thenBy { distanceFromFour(it.second) })!!
        val quietStart = (0..23).minWithOrNull(compareBy<Int> { start ->
            (0..3).sumOf { profile[(start + it) % 24] }
        }.thenBy { start ->
            distanceFromFour(((start + 2) % 24) * 60)
        }) ?: return null
        val boundary = if (longestQuiet.first >= 4) longestQuiet.second else ((quietStart + 2) % 24) * 60
        val byDate = records.associateBy { it.date }
        val windows = complete.mapNotNull { day ->
            val activity = day.activityIntervals ?: return@mapNotNull null
            val following = byDate[day.date.plusDays(1)]
            if (boundary > 0 && (following?.activityIntervals == null || following.observedUntilMinute < boundary))
                return@mapNotNull null
            val intervals = activity.filter { it.end > boundary && it.end > it.start }
                .map { maxOf(it.start, boundary) to it.end } +
                following?.activityIntervals.orEmpty().filter { boundary > 0 && it.start < boundary && it.end > it.start }
                    .map { (it.start + 1440) to (minOf(it.end, boundary) + 1440) }
            if (intervals.isEmpty()) return@mapNotNull null
            intervals.minOf { it.first } to intervals.maxOf { it.second }
        }
        if (windows.isEmpty()) return null
        // Keep times unwrapped: 23:00 and 01:00 are close,
        // rather than producing an afternoon bedtime. Both endpoints use paired days.
        fun median(values: List<Int>): Int {
            val sorted = values.sorted()
            return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
        }
        return median(windows.map { it.first }) to median(windows.map { it.second })
    }
}
