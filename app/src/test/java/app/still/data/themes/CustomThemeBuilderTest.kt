package app.still.data.themes

import org.junit.Assert.*
import org.junit.Test

class CustomThemeBuilderTest {
    @Test
    fun generatesValidThemeComposeSource() {
        val light = CustomThemeBuilder.defaultPalette("#285B8A", CustomThemeBuilder.lightTones.first(), isDark = false)
        val dark = CustomThemeBuilder.defaultPalette("#A8C8F2", CustomThemeBuilder.darkTones.first(), isDark = true)
        val source = CustomThemeBuilder.buildSource(
            title = "Ocean Breeze",
            author = "Still User",
            lightColors = light,
            darkColors = dark,
            customId = "custom-ocean-breeze",
        )

        val pkg = ThemePackages.load(source.toByteArray(Charsets.UTF_8))
        val theme = pkg.theme

        assertEquals("custom-ocean-breeze", theme.id)
        assertEquals("Ocean Breeze", theme.title)
        assertEquals("Still User", theme.author)
        assertEquals("1.0.0", theme.version)
        assertEquals("#285B8A", theme.light["primary"]?.resolve(emptyMap()))
        assertEquals("#FFFFFF", theme.light["on-primary"]?.resolve(emptyMap()))
        assertEquals("#A8C8F2", theme.dark["primary"]?.resolve(emptyMap()))
        assertTrue(theme.requests.isEmpty())
        assertTrue(theme.available())
    }

    @Test
    fun quotesMetadataThatContainsYamlPunctuation() {
        val source = CustomThemeBuilder.buildSource(
            title = "Night: Still's \"quiet\" mode",
            author = "A\\B",
            lightColors = CustomThemeBuilder.defaultPalette("#285B8A", CustomThemeBuilder.lightTones.first(), false),
            darkColors = CustomThemeBuilder.defaultPalette("#285B8A", CustomThemeBuilder.darkTones.first(), true),
            customId = "custom-quoted",
        )

        val theme = ThemePackages.load(source.toByteArray(Charsets.UTF_8)).theme
        assertEquals("Night: Still's \"quiet\" mode", theme.title)
        assertEquals("A\\B", theme.author)
    }

    @Test
    fun sanitizeIdProducesValidKeyPattern() {
        val pattern = Regex("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")
        val ids = listOf(
            CustomThemeBuilder.sanitizeId("My Custom Theme!"),
            CustomThemeBuilder.sanitizeId("---Special---Test---"),
            CustomThemeBuilder.sanitizeId("123 Numbers"),
            CustomThemeBuilder.sanitizeId(""),
            CustomThemeBuilder.sanitizeId("  Summer & Autumn 2026 "),
        )
        for (id in ids) {
            assertTrue("ID $id must match keyPattern", pattern.matches(id))
        }
    }

    @Test
    fun contrastingInkCalculation() {
        assertEquals("#172019", CustomThemeBuilder.contrastingInk("#FFFFFF"))
        assertEquals("#172019", CustomThemeBuilder.contrastingInk("#F7F9F6"))
        assertEquals("#FFFFFF", CustomThemeBuilder.contrastingInk("#000000"))
        assertEquals("#FFFFFF", CustomThemeBuilder.contrastingInk("#111713"))
        assertEquals("#FFFFFF", CustomThemeBuilder.contrastingInk("#285B8A"))
    }

    @Test
    fun defaultPaletteCoversAllSixRoles() {
        val palette = CustomThemeBuilder.defaultPalette("#27683C", CustomThemeBuilder.lightTones[0], isDark = false)
        assertEquals(CustomThemeBuilder.roles.toSet(), palette.keys)
        palette.values.forEach { hex ->
            assertTrue(CustomThemeBuilder.isValidHex(hex))
        }
    }
}
