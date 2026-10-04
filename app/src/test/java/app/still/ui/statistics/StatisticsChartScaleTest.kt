package app.still.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class StatisticsChartScaleTest {
    @Test
    fun emptyAndZeroDataHaveUsableScale() {
        assertEquals(Duration.ofMinutes(20).toMillis(), statisticsAxisMaximum(0))
    }

    @Test
    fun hourlyScaleRetainsMinutePrecisionAboveOneHour() {
        assertEquals(Duration.ofMinutes(120).toMillis(), statisticsAxisMaximum(Duration.ofMinutes(80).toMillis()))
    }

    @Test
    fun scaleNeverClipsValuesAndHasWholeMinuteTicks() {
        listOf(1L, 20, 21, 40, 41, 60, 61, 120, 121, 240, 481, 1440, 1441, 10080).forEach { minutes ->
            val value = Duration.ofMinutes(minutes).toMillis()
            val maximum = statisticsAxisMaximum(value)
            assertTrue("Scale must contain $minutes minutes", maximum >= value)
            assertEquals(0L, maximum % (4 * 60_000L))
        }
    }
}
