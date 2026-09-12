package app.still.ui.update

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
    @Test
    fun rendersCommonGitHubReleaseMarkdownWithoutSyntaxMarkers() {
        val rendered = markdownToAnnotatedString(
            "# Highlights\n- **Faster** startup\n1. Fix `sync`\nRead the [details](https://example.com/release)",
            Color.Blue,
        )

        assertEquals("Highlights\n• Faster startup\n1. Fix sync\nRead the details", rendered.text)
        assertFalse(rendered.text.contains("**"))
        assertFalse(rendered.text.contains("]("))
        assertTrue(rendered.spanStyles.isNotEmpty())
        assertEquals(
            "https://example.com/release",
            rendered.getStringAnnotations("URL", 0, rendered.length).single().item,
        )
    }
}
