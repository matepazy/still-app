package app.still.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionArtworkPaletteTest {
    @Test
    fun paletteIsRepeatableAndIgnoresDisplayPrefix() {
        val expected = versionArtworkPalette("1.6.0-beta5")
        assertEquals(expected, versionArtworkPalette("1.6.0-beta5"))
        assertEquals(expected, versionArtworkPalette("v1.6.0-beta5"))
    }

    @Test
    fun releasesPrereleasesAndBuildSuffixesHaveDistinctColors() {
        val versions = listOf(
            "1.6.0", "1.6.1", "1.7.0", "2.6.0", "1.6.0-beta4",
            "1.6.0-beta5", "1.6.0-beta10", "1.6.0-rc1", "1.6.0+1", "1.6.0+2",
        )
        assertEquals(versions.size, versions.map { versionArtworkPalette(it).colors }.toSet().size)
    }

    @Test
    fun broadVersionRangeHasNoRepeatedColorPalettesAndStaysInBounds() {
        val colors = (0 until 10000).map { versionArtworkPalette("1.6.0-beta$it").colors }
        assertEquals(colors.size, colors.toSet().size)
        colors.flatten().forEach {
            assertTrue(it.hue >= 0f && it.hue < 360f)
            assertTrue(it.saturation in 0f..1f)
            assertTrue(it.value in 0f..1f)
        }
    }
}
