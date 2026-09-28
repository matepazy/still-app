package app.still.data.settings

import app.still.BuildConfig
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonalThemeAvailabilityTest {
    @Test fun halloweenWindowIncludesBothBoundaries() {
        assertFalse(SeasonalThemeAvailability.halloweenAvailable(LocalDate.of(2026, 10, 14)))
        assertTrue(SeasonalThemeAvailability.halloweenAvailable(LocalDate.of(2026, 10, 15)))
        assertTrue(SeasonalThemeAvailability.halloweenAvailable(LocalDate.of(2026, 11, 15)))
        assertFalse(SeasonalThemeAvailability.halloweenAvailable(LocalDate.of(2026, 11, 16)))
    }

    @Test fun selectedHalloweenFallsBackOutsideItsWindow() {
        assertEquals(if (BuildConfig.HALLOWEEN_PREVIEW) ThemePreference.Halloween else ThemePreference.System,
            SeasonalThemeAvailability.activeTheme(ThemePreference.Halloween, LocalDate.of(2026, 12, 1)))
        assertEquals(ThemePreference.Halloween,
            SeasonalThemeAvailability.activeTheme(ThemePreference.Halloween, LocalDate.of(2026, 10, 31)))
        assertEquals(ThemePreference.Fall,
            SeasonalThemeAvailability.activeTheme(ThemePreference.Fall, LocalDate.of(2026, 12, 1)))
    }
}
