package app.still.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("still_settings")

enum class ThemePreference { System, Light, Dark }

enum class WidgetAppearance { System, Light, Dark }

enum class WidgetLabel { ScreenTime, Today, Hidden }

enum class DaylineWidgetLabel { Dayline, Today, Hidden }

enum class WidgetFontSize(val valueSp: Float) { Small(20f), Medium(24f), Large(28f) }

enum class WidgetFontStyle { Regular, Medium, Bold }

const val WIDGET_PILL_RADIUS = 1_000

enum class LastDestination {
    Today,
    Timeline,
    Apps,
    Settings,
    WidgetSettings,
    ScreenTimeWidgetSettings,
    DaylineWidgetSettings,
}

data class UserSettings(
    val onboardingComplete: Boolean = false,
    val theme: ThemePreference = ThemePreference.System,
    val useDynamicColors: Boolean = false,
    val dailyTargetMinutes: Long? = null,
    val versionCheckEnabled: Boolean? = null,
    val updateChannel: String = "release",
    val deferredUpdate: DeferredUpdate? = null,
    val saveUsageHistory: Boolean = true,
    val widgetAppearance: WidgetAppearance = WidgetAppearance.System,
    val widgetColor: String? = null,
    val widgetLabel: WidgetLabel = WidgetLabel.ScreenTime,
    val widgetFontSize: WidgetFontSize = WidgetFontSize.Medium,
    val widgetFontStyle: WidgetFontStyle = WidgetFontStyle.Bold,
    val widgetShowRefresh: Boolean = true,
    val widgetCornerRadiusDp: Int = 24,
    val widgetBackgroundOpacityPercent: Int = 100,
    val daylineWidgetAppearance: WidgetAppearance = WidgetAppearance.System,
    val daylineWidgetColor: String? = null,
    val daylineWidgetLabel: DaylineWidgetLabel = DaylineWidgetLabel.Dayline,
    val daylineWidgetShowRefresh: Boolean = true,
    val daylineWidgetCornerRadiusDp: Int = 24,
    val daylineWidgetBackgroundOpacityPercent: Int = 100,
    val lastDestination: LastDestination = LastDestination.Today,
    val appCategoryOverrides: Map<String, AppCategory> = emptyMap(),
)

data class DeferredUpdate(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val remindAfterMillis: Long,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_colors")
        val target = longPreferencesKey("daily_target_minutes")
        val versionCheckEnabled = booleanPreferencesKey("version_check_enabled")
        val updateChannel = stringPreferencesKey("version_check_channel")
        val deferredUpdateVersion = stringPreferencesKey("deferred_update_version")
        val deferredUpdateNotes = stringPreferencesKey("deferred_update_notes")
        val deferredUpdateUrl = stringPreferencesKey("deferred_update_url")
        val deferredUpdateReminder = longPreferencesKey("deferred_update_remind_after_ms")
        val saveUsageHistory = booleanPreferencesKey("save_usage_history")
        val widgetAppearance = stringPreferencesKey("widget_appearance")
        val widgetColor = stringPreferencesKey("widget_color")
        val widgetLabel = stringPreferencesKey("widget_label")
        val widgetFontSize = stringPreferencesKey("widget_font_size")
        val widgetFontStyle = stringPreferencesKey("widget_font_style")
        val widgetShowRefresh = booleanPreferencesKey("widget_show_refresh")
        val widgetCornerRadius = intPreferencesKey("widget_corner_radius_dp")
        val widgetBackgroundOpacity = intPreferencesKey("widget_background_opacity_percent")
        val daylineWidgetAppearance = stringPreferencesKey("dayline_widget_appearance")
        val daylineWidgetColor = stringPreferencesKey("dayline_widget_color")
        val daylineWidgetLabel = stringPreferencesKey("dayline_widget_label")
        val daylineWidgetShowRefresh = booleanPreferencesKey("dayline_widget_show_refresh")
        val daylineWidgetCornerRadius = intPreferencesKey("dayline_widget_corner_radius_dp")
        val daylineWidgetBackgroundOpacity = intPreferencesKey("dayline_widget_background_opacity_percent")
        val lastDestination = stringPreferencesKey("last_destination")
        val appCategoryOverrides = stringSetPreferencesKey("app_category_overrides")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { preferences ->
        val deferredUpdate = preferences[Keys.deferredUpdateVersion]?.let { version ->
            val url = preferences[Keys.deferredUpdateUrl] ?: return@let null
            DeferredUpdate(
                version = version,
                notes = preferences[Keys.deferredUpdateNotes] ?: "No release notes provided.",
                downloadUrl = url,
                remindAfterMillis = preferences[Keys.deferredUpdateReminder] ?: 0L,
            )
        }
        UserSettings(
            onboardingComplete = preferences[Keys.onboarding] ?: false,
            theme = preferences[Keys.theme]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.System,
            useDynamicColors = preferences[Keys.dynamic] ?: false,
            dailyTargetMinutes = preferences[Keys.target],
            versionCheckEnabled = preferences[Keys.versionCheckEnabled],
            updateChannel = preferences[Keys.updateChannel] ?: "release",
            deferredUpdate = deferredUpdate,
            saveUsageHistory = preferences[Keys.saveUsageHistory] ?: true,
            widgetAppearance = preferences[Keys.widgetAppearance]
                ?.let { runCatching { WidgetAppearance.valueOf(it) }.getOrNull() }
                ?: WidgetAppearance.System,
            widgetColor = normalizeWidgetColor(preferences[Keys.widgetColor]),
            widgetLabel = preferences[Keys.widgetLabel]
                ?.let { runCatching { WidgetLabel.valueOf(it) }.getOrNull() }
                ?: WidgetLabel.ScreenTime,
            widgetFontSize = preferences[Keys.widgetFontSize]
                ?.let { runCatching { WidgetFontSize.valueOf(it) }.getOrNull() }
                ?: WidgetFontSize.Medium,
            widgetFontStyle = preferences[Keys.widgetFontStyle]
                ?.let { runCatching { WidgetFontStyle.valueOf(it) }.getOrNull() }
                ?: WidgetFontStyle.Bold,
            widgetShowRefresh = preferences[Keys.widgetShowRefresh] ?: true,
            widgetCornerRadiusDp = normalizeWidgetRadius(preferences[Keys.widgetCornerRadius] ?: 24),
            widgetBackgroundOpacityPercent = (preferences[Keys.widgetBackgroundOpacity] ?: 100).coerceIn(20, 100),
            daylineWidgetAppearance = preferences[Keys.daylineWidgetAppearance]
                ?.let { runCatching { WidgetAppearance.valueOf(it) }.getOrNull() }
                ?: WidgetAppearance.System,
            daylineWidgetColor = normalizeWidgetColor(preferences[Keys.daylineWidgetColor]),
            daylineWidgetLabel = preferences[Keys.daylineWidgetLabel]
                ?.let { runCatching { DaylineWidgetLabel.valueOf(it) }.getOrNull() }
                ?: DaylineWidgetLabel.Dayline,
            daylineWidgetShowRefresh = preferences[Keys.daylineWidgetShowRefresh] ?: true,
            daylineWidgetCornerRadiusDp = normalizeWidgetRadius(preferences[Keys.daylineWidgetCornerRadius] ?: 24),
            daylineWidgetBackgroundOpacityPercent = (preferences[Keys.daylineWidgetBackgroundOpacity] ?: 100).coerceIn(20, 100),
            lastDestination = preferences[Keys.lastDestination]
                ?.let { runCatching { LastDestination.valueOf(it) }.getOrNull() }
                ?: LastDestination.Today,
            appCategoryOverrides = decodeAppCategoryOverrides(preferences[Keys.appCategoryOverrides].orEmpty()),
        )
    }

    suspend fun completeOnboarding() = context.settingsDataStore.edit {
        it[Keys.onboarding] = true
    }
    suspend fun setTheme(value: ThemePreference) = context.settingsDataStore.edit { it[Keys.theme] = value.name }
    suspend fun setDynamicColors(value: Boolean) = context.settingsDataStore.edit { it[Keys.dynamic] = value }
    suspend fun setDailyTargetMinutes(value: Long?) = context.settingsDataStore.edit {
        if (value == null) it.remove(Keys.target) else it[Keys.target] = value
    }
    suspend fun setVersionCheckEnabled(value: Boolean) = context.settingsDataStore.edit {
        it[Keys.versionCheckEnabled] = value
    }
    suspend fun setUpdateChannel(value: String) = context.settingsDataStore.edit {
        it[Keys.updateChannel] = value
    }
    suspend fun rememberUpdate(update: DeferredUpdate) = context.settingsDataStore.edit {
        it[Keys.deferredUpdateVersion] = update.version
        it[Keys.deferredUpdateNotes] = update.notes
        it[Keys.deferredUpdateUrl] = update.downloadUrl
        it[Keys.deferredUpdateReminder] = update.remindAfterMillis
    }
    suspend fun clearRememberedUpdate() = context.settingsDataStore.edit {
        it.remove(Keys.deferredUpdateVersion)
        it.remove(Keys.deferredUpdateNotes)
        it.remove(Keys.deferredUpdateUrl)
        it.remove(Keys.deferredUpdateReminder)
    }
    suspend fun setSaveUsageHistory(value: Boolean) = context.settingsDataStore.edit {
        it[Keys.saveUsageHistory] = value
    }
    suspend fun setWidgetAppearance(value: WidgetAppearance) = context.settingsDataStore.edit {
        it[Keys.widgetAppearance] = value.name
    }
    suspend fun setWidgetColor(value: String?) = context.settingsDataStore.edit {
        normalizeWidgetColor(value)?.let { color -> it[Keys.widgetColor] = color } ?: it.remove(Keys.widgetColor)
    }
    suspend fun setWidgetTheme(value: WidgetAppearance) = context.settingsDataStore.edit {
        it[Keys.widgetAppearance] = value.name
        it.remove(Keys.widgetColor)
    }
    suspend fun setWidgetLabel(value: WidgetLabel) = context.settingsDataStore.edit {
        it[Keys.widgetLabel] = value.name
    }
    suspend fun setWidgetFontSize(value: WidgetFontSize) = context.settingsDataStore.edit {
        it[Keys.widgetFontSize] = value.name
    }
    suspend fun setWidgetFontStyle(value: WidgetFontStyle) = context.settingsDataStore.edit {
        it[Keys.widgetFontStyle] = value.name
    }
    suspend fun setWidgetShowRefresh(value: Boolean) = context.settingsDataStore.edit {
        it[Keys.widgetShowRefresh] = value
    }
    suspend fun setWidgetCornerRadius(valueDp: Int) = context.settingsDataStore.edit {
        it[Keys.widgetCornerRadius] = normalizeWidgetRadius(valueDp)
    }
    suspend fun setWidgetBackgroundOpacity(valuePercent: Int) = context.settingsDataStore.edit {
        it[Keys.widgetBackgroundOpacity] = valuePercent.coerceIn(20, 100)
    }
    suspend fun setDaylineWidgetColor(value: String?) = context.settingsDataStore.edit {
        normalizeWidgetColor(value)?.let { color -> it[Keys.daylineWidgetColor] = color } ?: it.remove(Keys.daylineWidgetColor)
    }
    suspend fun setDaylineWidgetTheme(value: WidgetAppearance) = context.settingsDataStore.edit {
        it[Keys.daylineWidgetAppearance] = value.name
        it.remove(Keys.daylineWidgetColor)
    }
    suspend fun setDaylineWidgetLabel(value: DaylineWidgetLabel) = context.settingsDataStore.edit {
        it[Keys.daylineWidgetLabel] = value.name
    }
    suspend fun setDaylineWidgetShowRefresh(value: Boolean) = context.settingsDataStore.edit {
        it[Keys.daylineWidgetShowRefresh] = value
    }
    suspend fun setDaylineWidgetCornerRadius(valueDp: Int) = context.settingsDataStore.edit {
        it[Keys.daylineWidgetCornerRadius] = normalizeWidgetRadius(valueDp)
    }
    suspend fun setDaylineWidgetBackgroundOpacity(valuePercent: Int) = context.settingsDataStore.edit {
        it[Keys.daylineWidgetBackgroundOpacity] = valuePercent.coerceIn(20, 100)
    }
    suspend fun setLastDestination(value: LastDestination) = context.settingsDataStore.edit {
        it[Keys.lastDestination] = value.name
    }

    suspend fun setAppCategory(packageName: String, category: AppCategory) = context.settingsDataStore.edit { preferences ->
        val overrides = decodeAppCategoryOverrides(preferences[Keys.appCategoryOverrides].orEmpty()).toMutableMap()
        overrides[packageName] = category
        preferences[Keys.appCategoryOverrides] = encodeAppCategoryOverrides(overrides)
    }

    suspend fun resetWidgetSettings() = context.settingsDataStore.edit {
        it.remove(Keys.widgetAppearance)
        it.remove(Keys.widgetColor)
        it.remove(Keys.widgetLabel)
        it.remove(Keys.widgetFontSize)
        it.remove(Keys.widgetFontStyle)
        it.remove(Keys.widgetShowRefresh)
        it.remove(Keys.widgetCornerRadius)
        it.remove(Keys.widgetBackgroundOpacity)
    }

    suspend fun resetDaylineWidgetSettings() = context.settingsDataStore.edit {
        it.remove(Keys.daylineWidgetAppearance)
        it.remove(Keys.daylineWidgetColor)
        it.remove(Keys.daylineWidgetLabel)
        it.remove(Keys.daylineWidgetShowRefresh)
        it.remove(Keys.daylineWidgetCornerRadius)
        it.remove(Keys.daylineWidgetBackgroundOpacity)
    }
}

private fun normalizeWidgetRadius(value: Int): Int =
    if (value >= WIDGET_PILL_RADIUS) WIDGET_PILL_RADIUS else value.coerceIn(0, 32)

private const val APP_CATEGORY_SEPARATOR = '\t'

internal fun encodeAppCategoryOverrides(overrides: Map<String, AppCategory>): Set<String> =
    overrides.mapTo(mutableSetOf()) { (packageName, category) ->
        "$packageName$APP_CATEGORY_SEPARATOR${category.name}"
    }

internal fun decodeAppCategoryOverrides(values: Set<String>): Map<String, AppCategory> = buildMap {
    values.forEach { value ->
        val separatorIndex = value.indexOf(APP_CATEGORY_SEPARATOR)
        if (separatorIndex <= 0 || separatorIndex == value.lastIndex) return@forEach
        val packageName = value.substring(0, separatorIndex)
        val categoryName = value.substring(separatorIndex + 1)
        val category = runCatching { AppCategory.valueOf(categoryName) }.getOrNull() ?: return@forEach
        put(packageName, category)
    }
}
