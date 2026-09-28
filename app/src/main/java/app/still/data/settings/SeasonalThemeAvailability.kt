package app.still.data.settings

import app.still.BuildConfig
import java.time.LocalDate
import java.time.MonthDay

/** The Halloween edition is shown from October 15 through November 15, inclusive. */
object SeasonalThemeAvailability {
    fun halloweenAvailable(date: LocalDate = LocalDate.now()): Boolean {
        val day = MonthDay.from(date)
        return day >= MonthDay.of(10, 15) && day <= MonthDay.of(11, 15)
    }

    fun halloweenSelectable(date: LocalDate = LocalDate.now()): Boolean =
        BuildConfig.HALLOWEEN_PREVIEW || halloweenAvailable(date)

    fun activeTheme(theme: ThemePreference, date: LocalDate = LocalDate.now()): ThemePreference =
        if (theme == ThemePreference.Halloween && !halloweenSelectable(date)) ThemePreference.System else theme
}
