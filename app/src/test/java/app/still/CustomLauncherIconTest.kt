package app.still

import app.still.data.themes.CustomThemeBuilder
import org.junit.Assert.*
import org.junit.Test

class CustomLauncherIconTest {
    @Test fun everyPresetResolvesToItsBundledIcon() {
        CustomThemeBuilder.swatches.forEachIndexed { accentIndex, accent ->
            CustomThemeBuilder.backgroundTones.forEachIndexed { backgroundIndex, background ->
                val choice = CustomLauncherIcon.closest(accent.hex, background.backgroundHex)
                assertEquals(accentIndex, choice.accentIndex)
                assertEquals(backgroundIndex, choice.backgroundIndex)
                assertEquals(accent.hex, choice.accent)
                assertEquals(background.backgroundHex, choice.background)
                assertTrue(choice.resource != 0)
            }
        }
        assertEquals(128, CustomLauncherIcon.aliases.distinct().size)
    }

    @Test fun arbitraryHexUsesTheClosestColorAndIgnoresTrailingAlpha() {
        val choice = CustomLauncherIcon.closest("#27683d88", "#fffffe22")
        assertEquals("#27683C", choice.accent)
        assertEquals("#FFFFFF", choice.background)
        assertEquals("CustomThemeLauncher0_0", choice.alias)
    }
}
