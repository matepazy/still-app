package app.still.ui

import app.still.data.settings.LastDestination

internal const val TodayRoute = "today"
internal const val TimelineRoute = "timeline"
internal const val AppsRoute = "apps"
internal const val StatisticsRoute = "statistics"
internal const val CompareRoute = "compare"
internal const val SettingsRoute = "settings"
internal const val ThemeSettingsRoute = "settings/theme"
internal const val WidgetSettingsRoute = "settings/widget"
internal const val ScreenTimeWidgetSettingsRoute = "settings/widget/screen-time"
internal const val DaylineWidgetSettingsRoute = "settings/widget/dayline"
internal const val StoredDataRoute = "settings/data"

data class NavigationRequest(
    val destination: LastDestination,
    val id: Int,
)

internal val LastDestination.route: String
    get() = when (this) {
        LastDestination.Today -> TodayRoute
        LastDestination.Timeline -> TimelineRoute
        LastDestination.Apps -> AppsRoute
        LastDestination.Statistics -> StatisticsRoute
        LastDestination.Settings -> SettingsRoute
        LastDestination.WidgetSettings -> WidgetSettingsRoute
        LastDestination.ScreenTimeWidgetSettings -> ScreenTimeWidgetSettingsRoute
        LastDestination.DaylineWidgetSettings -> DaylineWidgetSettingsRoute
    }

internal fun lastDestinationForRoute(route: String?): LastDestination? = when (route) {
    TodayRoute -> LastDestination.Today
    TimelineRoute -> LastDestination.Timeline
    AppsRoute -> LastDestination.Apps
    StatisticsRoute -> LastDestination.Statistics
    SettingsRoute -> LastDestination.Settings
    WidgetSettingsRoute -> LastDestination.WidgetSettings
    ScreenTimeWidgetSettingsRoute -> LastDestination.ScreenTimeWidgetSettings
    DaylineWidgetSettingsRoute -> LastDestination.DaylineWidgetSettings
    else -> null
}
