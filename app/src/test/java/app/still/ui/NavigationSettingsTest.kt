package app.still.ui

import app.still.data.settings.AppCategory
import app.still.data.settings.LastDestination
import app.still.data.settings.ThemePreference
import app.still.data.settings.UserSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationSettingsTest {
    @Test
    fun `tab persistence keeps the initial restore value without emitting UI changes`() = runTest {
        val initial = UserSettings(lastDestination = LastDestination.Statistics)
        val emissions = flowOf(
            initial,
            initial.copy(lastDestination = LastDestination.Apps),
            initial.copy(lastDestination = LastDestination.Today),
        ).distinctUiSettings().toList()

        assertEquals(listOf(initial), emissions)
    }

    @Test
    fun `real settings changes still reach the UI after switching tabs`() = runTest {
        val initial = UserSettings(lastDestination = LastDestination.Timeline)
        val tab = initial.copy(lastDestination = LastDestination.Apps)
        val theme = tab.copy(theme = ThemePreference.Black)
        val categories = theme.copy(appCategoryOverrides = mapOf("example.app" to AppCategory.entries.first()))
        val history = categories.copy(saveUsageHistory = false)

        val emissions = flowOf(initial, tab, theme, categories, history).distinctUiSettings().toList()

        assertEquals(listOf(initial, theme, categories, history), emissions)
    }

    @Test
    fun `a new collector restores the latest saved tab`() = runTest {
        val latest = UserSettings(lastDestination = LastDestination.Apps)

        assertEquals(listOf(latest), flowOf(latest).distinctUiSettings().toList())
    }
}
