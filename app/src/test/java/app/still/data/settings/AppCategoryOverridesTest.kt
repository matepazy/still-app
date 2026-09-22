package app.still.data.settings

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryOverridesTest {
    @Test
    fun stillDefaultsToProductivity() {
        assertEquals(
            AppCategory.Productivity,
            AppCategory.defaultForPackage("app.still", ApplicationInfo.CATEGORY_UNDEFINED),
        )
    }

    @Test
    fun overridesRoundTrip() {
        val overrides = mapOf(
            "com.example.social" to AppCategory.Social,
            "com.example.music" to AppCategory.MusicAndAudio,
        )

        assertEquals(overrides, decodeAppCategoryOverrides(encodeAppCategoryOverrides(overrides)))
    }

    @Test
    fun malformedAndUnknownCategoriesAreIgnored() {
        val decoded = decodeAppCategoryOverrides(
            setOf(
                "missing-separator",
                "com.example.unknown\tNotARealCategory",
                "com.example.productive\tProductivity",
            ),
        )

        assertEquals(mapOf("com.example.productive" to AppCategory.Productivity), decoded)
    }
}
