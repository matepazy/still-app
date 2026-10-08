package app.still.data.themes

import org.junit.Assert.*
import org.junit.Test

class CustomThemeBuilderTest {
    @Test
    fun editingRetainsIdentityMetadataAndBrandingWhileReplacingTheFixedPalette() {
        val palette = CustomThemeBuilder.palette("#285B8A", CustomThemeBuilder.darkTones.first())
        val source = CustomThemeBuilder.buildFixedSource("Before", palette, "custom-edit")
            .replace("@common", "description: Original description\nlicense: MIT\n@common") +
            "@still-app\nbranding:\n  wordmark-color: \"#123456\"\n"
        val original = ThemePackages.load(source.toByteArray())
        val changed = palette + ("primary" to "#ABCDEF88")
        val updated = CustomThemeBuilder.editPackage(original, "After: \"quiet\"", changed).theme
        assertEquals(original.theme.id, updated.id)
        assertEquals(original.theme.version, updated.version)
        assertEquals(original.theme.author, updated.author)
        assertEquals(original.theme.description, updated.description)
        assertEquals(original.theme.license, updated.license)
        assertEquals(original.theme.wordmark, updated.wordmark)
        assertEquals("After: \"quiet\"", updated.title)
        assertEquals(changed, updated.light.mapValues { it.value.text })
        assertEquals(updated.light, updated.dark)
    }

    @Test
    fun editingLegacyUnversionedSourceRetainsItsInstalledIdentity() {
        val palette = CustomThemeBuilder.palette("#285B8A", CustomThemeBuilder.lightTones.first())
        val source = CustomThemeBuilder.buildFixedSource("Before", palette).lines().filterNot {
            it.startsWith("id:") || it.startsWith("version:")
        }.joinToString("\n")
        val original = ThemePackages.load(source.toByteArray())
        val updated = CustomThemeBuilder.editPackage(original, "After", palette)
        assertEquals(original.theme.id, updated.theme.id)
        assertEquals("0.0.0", updated.theme.version)
    }

    @Test
    fun fixedThemeRoundTripsOnePaletteInBothLegacySlots() {
        val colors = CustomThemeBuilder.palette("#F4A261", CustomThemeBuilder.darkTones.first())
        val theme = ThemeCompose.parse(CustomThemeBuilder.buildFixedSource("My theme", colors, "custom-fixed"))
        assertEquals(colors, theme.light.mapValues { it.value.resolve(emptyMap()) })
        assertEquals(colors, theme.dark.mapValues { it.value.resolve(emptyMap()) })
    }

    @Test
    fun everyGeneratedPaletteHasReadableText() {
        for (swatch in CustomThemeBuilder.swatches) {
            for (tone in CustomThemeBuilder.backgroundTones) {
                val colors = CustomThemeBuilder.palette(swatch.hex, tone)
                for ((fill, text) in listOf("primary" to "on-primary", "background" to "on-background", "surface" to "on-surface")) {
                    assertTrue("${swatch.name}/${tone.name}/$text", CustomThemeBuilder.contrastRatio(colors.getValue(fill), colors.getValue(text)) >= 4.5f)
                }
            }
        }
        assertEquals("#000000", CustomThemeBuilder.contrastingInk("#777777", darkInk = "#000000"))
    }

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
        assertEquals(light.getValue("primary"), theme.light["primary"]?.resolve(emptyMap()))
        assertEquals("#FFFFFF", theme.light["on-primary"]?.resolve(emptyMap()))
        assertEquals(dark.getValue("primary"), theme.dark["primary"]?.resolve(emptyMap()))
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
