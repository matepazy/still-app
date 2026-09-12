package app.still.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.still.AppContainer
import app.still.data.settings.ThemePreference
import app.still.data.settings.UserSettings
import app.still.data.settings.WidgetAppearance
import app.still.data.settings.WidgetLabel
import app.still.domain.model.UsageDashboard
import app.still.BuildConfig
import app.still.update.ApkInstaller
import app.still.update.UpdateState
import app.still.update.VersionUpdater
import app.still.widget.ScreenTimeWidgetProvider
import java.io.File
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
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState
    val state: StateFlow<MainUiState> = combine(container.settingsRepository.settings, usageState) { settings, usage ->
        MainUiState(settings, usage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            var startupCheckHandled = false
            container.settingsRepository.settings.collect { settings ->
                if (!startupCheckHandled && settings.onboardingComplete) {
                    startupCheckHandled = true
                    if (settings.versionCheckEnabled == true) checkForUpdates(settings.updateChannel)
                }
            }
        }
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

    fun completeOnboarding(versionCheckEnabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.completeOnboarding(versionCheckEnabled)
    }
    fun setTheme(value: ThemePreference) = viewModelScope.launch { container.settingsRepository.setTheme(value) }
    fun setDynamicColors(value: Boolean) = viewModelScope.launch { container.settingsRepository.setDynamicColors(value) }
    fun setDailyTargetMinutes(value: Long?) = viewModelScope.launch { container.settingsRepository.setDailyTargetMinutes(value) }
    fun setWidgetAppearance(value: WidgetAppearance) = viewModelScope.launch {
        container.settingsRepository.setWidgetAppearance(value)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setWidgetLabel(value: WidgetLabel) = viewModelScope.launch {
        container.settingsRepository.setWidgetLabel(value)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setWidgetShowRefresh(value: Boolean) = viewModelScope.launch {
        container.settingsRepository.setWidgetShowRefresh(value)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setVersionCheckEnabled(value: Boolean) = viewModelScope.launch {
        container.settingsRepository.setVersionCheckEnabled(value)
        if (value) {
            val channel = state.value.settings?.updateChannel ?: "release"
            checkForUpdates(channel)
        }
    }

    fun setUpdateChannel(value: String) = viewModelScope.launch {
        container.settingsRepository.setUpdateChannel(value)
        if (state.value.settings?.versionCheckEnabled == true) checkForUpdates(value)
    }

    fun triggerVersionCheck() {
        checkForUpdates(state.value.settings?.updateChannel ?: "release")
    }

    private fun checkForUpdates(channel: String) {
        _updateState.value = UpdateState.Checking
        VersionUpdater.checkForUpdates(
            currentVersion = BuildConfig.VERSION_NAME,
            includePrereleases = channel == "pre-release",
            onResult = { _updateState.value = it },
        )
    }

    fun startApkDownload(downloadUrl: String) {
        _updateState.value = UpdateState.Downloading(0f)
        VersionUpdater.downloadApk(
            context = container.applicationContext,
            downloadUrl = downloadUrl,
            onProgress = { _updateState.value = UpdateState.Downloading(it) },
            onCompleted = { _updateState.value = UpdateState.Completed(it) },
            onError = { _updateState.value = UpdateState.Error(it) },
        )
    }

    fun canInstallPackages(): Boolean = ApkInstaller.canInstallPackages(container.applicationContext)
    fun requestInstallPermission() = ApkInstaller.requestInstallPermission(container.applicationContext)
    fun installApk(file: File) = ApkInstaller.installApk(container.applicationContext, file)
    fun resetUpdateState() { _updateState.value = UpdateState.Idle }
    fun usageSettingsIntent() = container.permissionManager.usageSettingsIntent()

    fun restrictedSettingsIntent() = container.permissionManager.restrictedSettingsIntent()

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }
}
