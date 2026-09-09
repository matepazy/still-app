package app.still.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("still_settings")

enum class ThemePreference { System, Light, Dark }

data class UserSettings(
    val onboardingComplete: Boolean = false,
    val theme: ThemePreference = ThemePreference.System,
    val useDynamicColors: Boolean = false,
    val dailyTargetMinutes: Long? = null,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_colors")
        val target = longPreferencesKey("daily_target_minutes")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { preferences ->
        UserSettings(
            onboardingComplete = preferences[Keys.onboarding] ?: false,
            theme = preferences[Keys.theme]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.System,
            useDynamicColors = preferences[Keys.dynamic] ?: false,
            dailyTargetMinutes = preferences[Keys.target],
        )
    }

    suspend fun completeOnboarding() = context.settingsDataStore.edit { it[Keys.onboarding] = true }
    suspend fun setTheme(value: ThemePreference) = context.settingsDataStore.edit { it[Keys.theme] = value.name }
    suspend fun setDynamicColors(value: Boolean) = context.settingsDataStore.edit { it[Keys.dynamic] = value }
    suspend fun setDailyTargetMinutes(value: Long?) = context.settingsDataStore.edit {
        if (value == null) it.remove(Keys.target) else it[Keys.target] = value
    }
}
