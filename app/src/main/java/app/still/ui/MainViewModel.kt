package app.still.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.still.AppContainer
import app.still.data.settings.ThemePreference
import app.still.data.settings.UserSettings
import app.still.domain.model.UsageDashboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime

sealed interface UsageUiState {
    data object Loading : UsageUiState
    data object PermissionRequired : UsageUiState
    data class Ready(val dashboard: UsageDashboard) : UsageUiState
    data class Error(val message: String) : UsageUiState
}

data class MainUiState(
    val settings: UserSettings? = null,
    val usage: UsageUiState = UsageUiState.Loading,
)

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val usageState = MutableStateFlow<UsageUiState>(UsageUiState.Loading)
    val state: StateFlow<MainUiState> = combine(container.settingsRepository.settings, usageState) { settings, usage ->
        MainUiState(settings, usage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            while (true) {
                val now = ZonedDateTime.now()
                val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(Duration.between(now, nextDay).toMillis().coerceAtLeast(1_000) + 1_000)
                refresh()
            }
        }
    }

    fun onResume() {
        val hasPermission = container.permissionManager.hasUsageAccess()
        if (!hasPermission) usageState.value = UsageUiState.PermissionRequired
        else refresh()
    }

    fun refresh() {
        if (!container.permissionManager.hasUsageAccess()) {
            usageState.value = UsageUiState.PermissionRequired
            return
        }
        viewModelScope.launch {
            usageState.value = UsageUiState.Loading
            usageState.value = container.usageRepository.dashboard().fold(
                onSuccess = UsageUiState::Ready,
                onFailure = { UsageUiState.Error(it.message ?: "Usage information is unavailable right now.") },
            )
        }
    }

    fun completeOnboarding() = viewModelScope.launch { container.settingsRepository.completeOnboarding() }
    fun setTheme(value: ThemePreference) = viewModelScope.launch { container.settingsRepository.setTheme(value) }
    fun setDynamicColors(value: Boolean) = viewModelScope.launch { container.settingsRepository.setDynamicColors(value) }
    fun setDailyTargetMinutes(value: Long?) = viewModelScope.launch { container.settingsRepository.setDailyTargetMinutes(value) }
    fun usageSettingsIntent() = container.permissionManager.settingsIntent()

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }
}
