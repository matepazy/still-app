package app.still.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetColorTest {
    @Test
    fun `normalizes valid six digit colors`() {
        assertEquals("#315A41", normalizeWidgetColor(" 315a41 "))
        assertEquals("#ABCDEF", normalizeWidgetColor("#abcdef"))
    }

    @Test
    fun `rejects incomplete or unsupported colors`() {
        assertNull(normalizeWidgetColor("#123"))
        assertNull(normalizeWidgetColor("#12345678"))
        assertNull(normalizeWidgetColor("#12GG56"))
        assertNull(normalizeWidgetColor(null))
    }

    @Test
    fun `parses an opaque argb color`() {
        assertEquals(0xFF315A41.toInt(), parseWidgetColor("#315A41"))
    }

    @Test
    fun `chooses a foreground with accessible text contrast`() {
        listOf(0xFF000000, 0xFFFFFFFF, 0xFF777777, 0xFF315A41, 0xFFFFC107).forEach { background ->
            val colors = widgetContrastColors(background.toInt())
            assertTrue(contrastRatio(colors.background, colors.foreground) >= 4.5)
        }
    }
}
