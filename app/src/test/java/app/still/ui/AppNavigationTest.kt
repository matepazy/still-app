package app.still.ui

import app.still.data.settings.LastDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavigationTest {
    @Test
    fun `restorable routes map to their persisted destinations`() {
        LastDestination.entries.forEach { destination ->
            assertEquals(destination, lastDestinationForRoute(destination.route))
        }
    }

    @Test
    fun `detail and unknown routes are not persisted`() {
        assertNull(lastDestinationForRoute("app/{packageName}"))
        assertNull(lastDestinationForRoute("not-a-destination"))
        assertNull(lastDestinationForRoute(null))
    }
}
