package app.still.ui.components

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

fun Duration.compactDuration(): String {
    val totalMinutes = toMinutes().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours} h ${minutes} min"
        hours > 0 -> "${hours} h"
        totalMinutes > 0 -> "${totalMinutes} min"
        seconds > 0 -> "<1 min"
        else -> "0 min"
    }
}

fun Duration.signedCompactDuration(): String {
    val prefix = if (isNegative) "−" else "+"
    return prefix + abs(toMillis()).let(Duration::ofMillis).compactDuration()
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
fun Instant.clockTime(zone: ZoneId = ZoneId.systemDefault()): String = atZone(zone).format(timeFormatter)
