package app.still.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.still.AppContainer
import app.still.data.settings.ThemePreference
import app.still.data.settings.AppCategory
import app.still.data.settings.LastDestination
import app.still.data.settings.UserSettings
import app.still.data.settings.WidgetAppearance
import app.still.data.settings.WidgetFontSize
import app.still.data.settings.WidgetFontStyle
import app.still.data.settings.WidgetLabel
import app.still.data.settings.DeferredUpdate
import app.still.data.settings.DaylineWidgetLabel
import app.still.domain.model.UsageDashboard
import app.still.BuildConfig
import app.still.update.ApkInstaller
import app.still.update.UpdateState
import app.still.update.VersionUpdater
import app.still.update.SemanticVersion
import app.still.widget.WidgetUpdateDispatcher
import app.still.widget.ScreenTimeWidgetProvider
import app.still.widget.DaylineWidgetProvider
import app.still.data.usage.UsageHistoryScheduler
import app.still.data.usage.StoredDataSummary
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val _storedDataSummary = MutableStateFlow<StoredDataSummary?>(null)
    val storedDataSummary: StateFlow<StoredDataSummary?> = _storedDataSummary
    val state: StateFlow<MainUiState> = combine(container.settingsRepository.settings, usageState) { settings, usage ->
        MainUiState(settings, usage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            var startupCheckHandled = false
            container.settingsRepository.settings.collect { settings ->
                if (!startupCheckHandled && settings.onboardingComplete) {
                    startupCheckHandled = true
                    val deferred = settings.deferredUpdate?.takeIf {
                        SemanticVersion.isNewer(it.version, BuildConfig.VERSION_NAME)
                    }
                    when {
                        deferred != null && deferred.remindAfterMillis <= System.currentTimeMillis() -> {
                            _updateState.value = deferred.toUpdateState()
                        }
                        deferred != null -> Unit
                        settings.deferredUpdate != null -> {
                            container.settingsRepository.clearRememberedUpdate()
                            if (settings.versionCheckEnabled == true) checkForUpdates(settings.updateChannel)
                        }
                        settings.versionCheckEnabled == true -> checkForUpdates(settings.updateChannel)
                    }
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
            val saveHistory = container.settingsRepository.settings.first().saveUsageHistory
            usageState.value = container.usageRepository.dashboard(saveHistory).fold(
                onSuccess = UsageUiState::Ready,
                onFailure = { UsageUiState.Error(it.message ?: "Usage information is unavailable right now.") },
            )
        }
    }

    fun refreshStoredDataSummary() = viewModelScope.launch {
        _storedDataSummary.value = container.usageRepository.storedDataSummary()
    }

    fun completeOnboarding(versionCheckEnabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.completeOnboarding(versionCheckEnabled)
    }
    fun setTheme(value: ThemePreference) = viewModelScope.launch { container.settingsRepository.setTheme(value) }
    fun setDynamicColors(value: Boolean) = viewModelScope.launch { container.settingsRepository.setDynamicColors(value) }
    fun setSaveUsageHistory(value: Boolean) = viewModelScope.launch {
        container.settingsRepository.setSaveUsageHistory(value)
        if (value) {
            UsageHistoryScheduler.schedule(container.applicationContext)
        } else {
            UsageHistoryScheduler.cancel(container.applicationContext)
            container.usageRepository.clearHistory()
        }
        refreshStoredDataSummary()
        refresh()
        WidgetUpdateDispatcher.updateAll(container.applicationContext)
    }
    fun setDailyTargetMinutes(value: Long?) = viewModelScope.launch { container.settingsRepository.setDailyTargetMinutes(value) }
    fun setLastDestination(value: LastDestination) = viewModelScope.launch {
        container.settingsRepository.setLastDestination(value)
    }
    fun setAppCategory(packageName: String, category: AppCategory) = viewModelScope.launch {
        container.settingsRepository.setAppCategory(packageName, category)
    }
    fun setWidgetAppearance(value: WidgetAppearance) = viewModelScope.launch {
        container.settingsRepository.setWidgetAppearance(value)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setWidgetColor(value: String?) = viewModelScope.launch {
        container.settingsRepository.setWidgetColor(value)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setWidgetTheme(value: WidgetAppearance) = viewModelScope.launch {
        container.settingsRepository.setWidgetTheme(value)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setWidgetLabel(value: WidgetLabel) = viewModelScope.launch {
        container.settingsRepository.setWidgetLabel(value)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setWidgetFontSize(value: WidgetFontSize) = viewModelScope.launch {
        container.settingsRepository.setWidgetFontSize(value)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setWidgetFontStyle(value: WidgetFontStyle) = viewModelScope.launch {
        container.settingsRepository.setWidgetFontStyle(value)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setWidgetShowRefresh(value: Boolean) = viewModelScope.launch {
        container.settingsRepository.setWidgetShowRefresh(value)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setWidgetCornerRadius(valueDp: Int) = viewModelScope.launch {
        container.settingsRepository.setWidgetCornerRadius(valueDp)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setWidgetBackgroundOpacity(valuePercent: Int) = viewModelScope.launch {
        container.settingsRepository.setWidgetBackgroundOpacity(valuePercent)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun resetWidgetSettings() = viewModelScope.launch {
        container.settingsRepository.resetWidgetSettings()
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
    }
    fun setDaylineWidgetColor(value: String?) = viewModelScope.launch {
        container.settingsRepository.setDaylineWidgetColor(value)
        DaylineWidgetProvider.updateAll(container.applicationContext)
    }
    fun setDaylineWidgetTheme(value: WidgetAppearance) = viewModelScope.launch {
        container.settingsRepository.setDaylineWidgetTheme(value)
        DaylineWidgetProvider.updateAll(container.applicationContext)
    }
    fun setDaylineWidgetLabel(value: DaylineWidgetLabel) = viewModelScope.launch {
        container.settingsRepository.setDaylineWidgetLabel(value)
        DaylineWidgetProvider.updateAll(container.applicationContext)
    }
    fun setDaylineWidgetShowRefresh(value: Boolean) = viewModelScope.launch {
        container.settingsRepository.setDaylineWidgetShowRefresh(value)
        DaylineWidgetProvider.updateAll(container.applicationContext)
    }
    fun setDaylineWidgetCornerRadius(valueDp: Int) = viewModelScope.launch {
        container.settingsRepository.setDaylineWidgetCornerRadius(valueDp)
        DaylineWidgetProvider.updateAll(container.applicationContext)
    }
    fun setDaylineWidgetBackgroundOpacity(valuePercent: Int) = viewModelScope.launch {
        container.settingsRepository.setDaylineWidgetBackgroundOpacity(valuePercent)
        DaylineWidgetProvider.updateAll(container.applicationContext)
    }
    fun resetDaylineWidgetSettings() = viewModelScope.launch {
        container.settingsRepository.resetDaylineWidgetSettings()
        DaylineWidgetProvider.updateAll(container.applicationContext)
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
            onResult = { result ->
                viewModelScope.launch {
                    if (result is UpdateState.UpdateAvailable) {
                        container.settingsRepository.rememberUpdate(result.toDeferredUpdate(remindAfterMillis = 0L))
                    } else if (result is UpdateState.Idle) {
                        container.settingsRepository.clearRememberedUpdate()
                    }
                    _updateState.value = result
                }
            },
        )
    }

    fun remindAboutUpdateLater(update: UpdateState.UpdateAvailable) = viewModelScope.launch {
        container.settingsRepository.rememberUpdate(
            update.toDeferredUpdate(System.currentTimeMillis() + UPDATE_REMINDER_DELAY_MS),
        )
        _updateState.value = UpdateState.Idle
        delay(UPDATE_REMINDER_DELAY_MS)
        val remembered = container.settingsRepository.settings.first().deferredUpdate
        if (remembered?.version == update.version &&
            remembered.remindAfterMillis <= System.currentTimeMillis() &&
            SemanticVersion.isNewer(remembered.version, BuildConfig.VERSION_NAME)
        ) {
            _updateState.value = remembered.toUpdateState()
        }
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

    private fun DeferredUpdate.toUpdateState() = UpdateState.UpdateAvailable(version, notes, downloadUrl)

    private fun UpdateState.UpdateAvailable.toDeferredUpdate(remindAfterMillis: Long) = DeferredUpdate(
        version = version,
        notes = notes,
        downloadUrl = downloadUrl,
        remindAfterMillis = remindAfterMillis,
    )

    private companion object {
        const val UPDATE_REMINDER_DELAY_MS = 24L * 60L * 60L * 1_000L
    }
}
