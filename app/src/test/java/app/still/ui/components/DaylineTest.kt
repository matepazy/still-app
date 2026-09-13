package app.still.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DaylineTest {
    @Test fun guidesOnlyAppearForElapsedQuarterDays() {
        assertEquals(emptyList<Float>(), elapsedDaylineGuides(.24f))
        assertEquals(listOf(.25f), elapsedDaylineGuides(.25f))
        assertEquals(listOf(.25f, .5f), elapsedDaylineGuides(.62f))
        assertEquals(listOf(.25f, .5f, .75f), elapsedDaylineGuides(1f))
    }
}
