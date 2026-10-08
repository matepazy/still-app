package app.still.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.still.data.themes.CustomThemeBuilder
import app.still.ui.theme.customThemeColors
import app.still.ui.theme.communityControlColors
import app.still.ui.theme.StillDarkColors
import app.still.ui.theme.StillLightColors
import app.still.ui.theme.themeColor
import org.junit.Assert.*
import org.junit.Test

class CustomThemeColorsTest {
    @Test
    fun recoverySurfacesKeepEveryReadablePresetPalette() {
        for (swatch in CustomThemeBuilder.swatches) for (tone in CustomThemeBuilder.backgroundTones) {
            val scheme = customThemeColors(CustomThemeBuilder.palette(swatch.hex, tone))
            assertSame("${swatch.name}/${tone.name}", scheme, communityControlColors(scheme))
        }
    }

    @Test
    fun unreadableAndTransparentThemesStillHaveVisibleRecoveryControls() {
        assertSame(StillDarkColors, communityControlColors(StillDarkColors.copy(onSurface = StillDarkColors.surface)))
        assertSame(StillLightColors, communityControlColors(StillLightColors.copy(onPrimary = StillLightColors.primary)))
        assertSame(StillDarkColors, communityControlColors(StillDarkColors.copy(background = Color.Transparent)))
    }

    @Test
    fun presetAccentTextIsReadableAcrossActualMaterialSurfaces() {
        for (swatch in CustomThemeBuilder.swatches) for (tone in CustomThemeBuilder.backgroundTones) {
            val scheme = customThemeColors(CustomThemeBuilder.palette(swatch.hex, tone))
            for (surface in listOf(scheme.background, scheme.surface, scheme.surfaceVariant, scheme.surfaceContainerHighest)) {
                val a = scheme.primary.luminance(); val b = surface.luminance()
                assertTrue("${swatch.name}/${tone.name}: $surface", (maxOf(a, b) + .05f) / (minOf(a, b) + .05f) >= 4.5f)
            }
        }
    }

    @Test
    fun completeSchemeHasNoUnspecifiedRolesAndReturnsToTheSameColors() {
        val ocean = CustomThemeBuilder.palette("#285B8A", CustomThemeBuilder.lightTones.first())
        val rose = CustomThemeBuilder.palette("#8D4D64", CustomThemeBuilder.darkTones.first())
        val first = customThemeColors(ocean)
        val other = customThemeColors(rose)
        val restored = customThemeColors(ocean)
        // Cover every role shipped by Material, including the newer fixed and bright/dim roles.
        val getters = ColorScheme::class.java.methods.filter { it.name.startsWith("get") && it.name.endsWith("0d7_KjU") && it.parameterCount == 0 }
        assertTrue(getters.size >= 48)
        for (getter in getters) {
            assertNotEquals(getter.name, Color.Unspecified.value.toLong(), getter.invoke(first))
            assertEquals(getter.name, getter.invoke(first), getter.invoke(restored))
        }
        assertNotEquals(first.primary, other.primary)
        assertNotEquals(first.surfaceBright, other.surfaceBright)
        assertNotEquals(first.primaryFixed, other.primaryFixed)
        assertEquals(themeColor(ocean.getValue("on-primary")), first.onPrimary)
        assertEquals(themeColor(ocean.getValue("background")), first.background)
        assertEquals(themeColor(ocean.getValue("on-background")), first.onBackground)
        assertEquals(themeColor(ocean.getValue("surface")), first.surface)
        assertEquals(themeColor(ocean.getValue("on-surface")), first.onSurface)
    }
}
