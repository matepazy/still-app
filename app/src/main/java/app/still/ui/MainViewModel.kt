package app.still.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.still.AppContainer
import app.still.SeasonalLauncherIcon
import app.still.data.settings.ThemePreference
import app.still.data.settings.SeasonalThemeAvailability
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
import app.still.data.usage.ArchiveMigrationNotice
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime

sealed interface UsageUiState {
    data object Loading : UsageUiState
    data object PermissionRequired : UsageUiState
    data class Ready(val dashboard: UsageDashboard) : UsageUiState
    data class Error(val message: String) : UsageUiState
}

sealed interface ArchiveRestoreState {
    data object Idle : ArchiveRestoreState
    data object Restoring : ArchiveRestoreState
    data object Restored : ArchiveRestoreState
    data class Error(val message: String) : ArchiveRestoreState
}

sealed interface DataTransferState {
    data object Idle : DataTransferState
    data object ChoosingDestination : DataTransferState
    data class Working(val importing: Boolean, val rollingBack: Boolean = false) : DataTransferState
    data class ImportReady(val uri: android.net.Uri) : DataTransferState
    data class Finished(val title: String, val message: String) : DataTransferState
}

sealed interface ArchiveUpgradeState {
    data object Idle : ArchiveUpgradeState
    data object Upgrading : ArchiveUpgradeState
    data object Upgraded : ArchiveUpgradeState
    data class Error(val message: String) : ArchiveUpgradeState
}

sealed interface ArchiveBackupDeleteState {
    data object Idle : ArchiveBackupDeleteState
    data object Deleting : ArchiveBackupDeleteState
    data object Deleted : ArchiveBackupDeleteState
    data class Error(val message: String) : ArchiveBackupDeleteState
}

data class MainUiState(
    val settings: UserSettings? = null,
    val usage: UsageUiState = UsageUiState.Loading,
)

// Restore the first saved destination, then let NavController own live selection.
// Persisting another tab must not rebuild the UI as though its settings changed.
internal fun Flow<UserSettings>.distinctUiSettings(): Flow<UserSettings> =
    distinctUntilChangedBy { it.copy(lastDestination = LastDestination.Today) }

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val _dataTransferState = MutableStateFlow<DataTransferState>(DataTransferState.Idle)
    val dataTransferState: StateFlow<DataTransferState> = _dataTransferState
    private var exportPin: CharArray? = null

    fun prepareDataExport(pin: CharArray) {
        exportPin?.fill('\u0000')
        exportPin = pin
        _dataTransferState.value = DataTransferState.ChoosingDestination
    }

    fun exportData(uri: android.net.Uri?) {
        val pin = exportPin ?: return
        exportPin = null
        if (uri == null) {
            pin.fill('\u0000')
            _dataTransferState.value = DataTransferState.Idle
            return
        }
        transferData(uri, pin, importing = false)
    }

    fun importData(uri: android.net.Uri, pin: CharArray) = transferData(uri, pin, importing = true)

    private fun validateImportFile(uri: android.net.Uri) {
        val resolver = container.applicationContext.contentResolver
        val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        checkNotNull(resolver.openInputStream(uri)).use { app.still.data.usage.EncryptedDataExport.validateFile(name, it) }
    }

    fun selectDataImport(uri: android.net.Uri) {
        if (_dataTransferState.value is DataTransferState.Working) return
        _dataTransferState.value = DataTransferState.Working(importing = true)
        viewModelScope.launch {
            val valid = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { validateImportFile(uri) }.isSuccess
            }
            _dataTransferState.value = if (valid) DataTransferState.ImportReady(uri) else
                DataTransferState.Finished("Import failed", "Choose a .stilldb database export created by Still. Your existing history was kept.")
        }
    }

    fun rollbackDataImport() {
        if (_dataTransferState.value is DataTransferState.Working) return
        _dataTransferState.value = DataTransferState.Working(importing = true, rollingBack = true)
        viewModelScope.launch {
            val result = runCatching { container.usageRepository.rollbackDatabaseImport() }
            _dataTransferState.value = result.fold(
                onSuccess = { DataTransferState.Finished("Previous database restored", "Your history and safety backup from before the latest import have been restored.") },
                onFailure = { DataTransferState.Finished("Rollback failed", "The previous database could not be restored or the seven-day period has ended. Your current history was kept.") },
            )
            refreshStoredDataSummary()
            if (result.isSuccess) {
                refresh()
                WidgetUpdateDispatcher.updateAll(container.applicationContext)
            }
        }
    }

    private fun transferData(uri: android.net.Uri, pin: CharArray, importing: Boolean) {
        if (_dataTransferState.value is DataTransferState.Working) { pin.fill('\u0000'); return }
        _dataTransferState.value = DataTransferState.Working(importing)
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val context = container.applicationContext
                var snapshot: File? = null
                var encrypted: File? = null
                try {
                    snapshot = File.createTempFile("data-transfer-", ".tmp", context.noBackupFilesDir)
                    if (importing) {
                        validateImportFile(uri)
                        val input = checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open this file" }
                        input.use { source -> snapshot.outputStream().buffered().use { output ->
                            app.still.data.usage.EncryptedDataExport.decrypt(source, output, pin)
                        } }
                        // GCM authentication has succeeded before any saved history is touched.
                        container.usageRepository.importDatabase(snapshot)
                    } else {
                        container.usageRepository.exportDatabase(snapshot)
                        encrypted = File.createTempFile("encrypted-export-", ".tmp", context.noBackupFilesDir)
                        snapshot.inputStream().buffered().use { input -> encrypted.outputStream().buffered().use { output ->
                            app.still.data.usage.EncryptedDataExport.encrypt(input, output, pin)
                        } }
                        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Cannot write this file" }.use { output ->
                            encrypted.inputStream().use { it.copyTo(output) }
                        }
                    }
                    Result.success(Unit)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Result.failure<Unit>(failure)
                } finally {
                    pin.fill('\u0000')
                    snapshot?.delete()
                    encrypted?.delete()
                }
            }
            _dataTransferState.value = result.fold(
                onSuccess = { DataTransferState.Finished(if (importing) "Data imported" else "Data exported",
                    if (importing) "Your saved usage history has been replaced. You can restore the previous database for seven days from Settings › Data. Your app settings are unchanged."
                    else "Your full usage database and safety backup were saved in an encrypted file. Keep your PIN to import it.") },
                onFailure = { DataTransferState.Finished(if (importing) "Import failed" else "Export failed",
                    if (importing) "The PIN may be incorrect, or the file is damaged or unsupported. Your existing history was kept."
                    else "The export could not be saved. Check the destination and available storage, then try again.") },
            )
            if (result.isSuccess && importing) {
                refreshStoredDataSummary()
                refresh()
                WidgetUpdateDispatcher.updateAll(container.applicationContext)
            }
        }
    }

    fun dismissDataTransferResult() { _dataTransferState.value = DataTransferState.Idle }

    override fun onCleared() {
        exportPin?.fill('\u0000')
        exportPin = null
        super.onCleared()
    }
    private val usageState = MutableStateFlow<UsageUiState>(UsageUiState.Loading)
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState
    private val _storedDataSummary = MutableStateFlow<StoredDataSummary?>(null)
    val storedDataSummary: StateFlow<StoredDataSummary?> = _storedDataSummary
    private val _archiveMigrationNotice = MutableStateFlow<ArchiveMigrationNotice?>(null)
    val archiveMigrationNotice: StateFlow<ArchiveMigrationNotice?> = _archiveMigrationNotice
    private val _archiveRestoreState = MutableStateFlow<ArchiveRestoreState>(ArchiveRestoreState.Idle)
    val archiveRestoreState: StateFlow<ArchiveRestoreState> = _archiveRestoreState
    private val _archiveUpgradeState = MutableStateFlow<ArchiveUpgradeState>(ArchiveUpgradeState.Idle)
    val archiveUpgradeState: StateFlow<ArchiveUpgradeState> = _archiveUpgradeState
    private val _archiveBackupDeleteState = MutableStateFlow<ArchiveBackupDeleteState>(ArchiveBackupDeleteState.Idle)
    val archiveBackupDeleteState: StateFlow<ArchiveBackupDeleteState> = _archiveBackupDeleteState
    val state: StateFlow<MainUiState> = combine(container.settingsRepository.settings.distinctUiSettings(), usageState) { settings, usage ->
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
                            checkForUpdates(settings.updateChannel)
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
                refreshSeasonalTheme()
                refresh()
            }
        }
    }

    fun onResume() {
        refreshSeasonalTheme()
        val hasPermission = container.permissionManager.hasUsageAccess()
        if (!hasPermission) usageState.value = UsageUiState.PermissionRequired
        else refresh()
    }

    private var refreshJob: kotlinx.coroutines.Job? = null

    fun refresh() {
        refreshJob?.cancel()
        if (!container.permissionManager.hasUsageAccess()) {
            usageState.value = UsageUiState.PermissionRequired
            return
        }
        refreshJob = viewModelScope.launch {
            // Keep the navigation tree (and an active scanner) alive during a resume refresh.
            if (usageState.value !is UsageUiState.Ready) usageState.value = UsageUiState.Loading
            val saveHistory = container.settingsRepository.settings.first().saveUsageHistory
            val result = container.usageRepository.dashboard(saveHistory) { current ->
                val previous = (usageState.value as? UsageUiState.Ready)?.dashboard
                usageState.value = UsageUiState.Ready(
                    if (saveHistory && previous?.today?.date == current.date) previous.copy(today = current)
                    else UsageDashboard(current, null, null, emptyList()),
                )
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            usageState.value = result.fold(
                onSuccess = UsageUiState::Ready,
                // Keep the freshly loaded current usage if only the history refresh failed.
                onFailure = { usageState.value.takeIf { it is UsageUiState.Ready }
                    ?: UsageUiState.Error(it.message ?: "Usage information is unavailable right now.") },
            )
            _archiveMigrationNotice.value = container.usageRepository.migrationNotice()
        }
    }

    fun refreshStoredDataSummary() = viewModelScope.launch {
        _storedDataSummary.value = container.usageRepository.storedDataSummary()
    }

    fun acknowledgeArchiveMigration() = viewModelScope.launch {
        container.usageRepository.acknowledgeMigrationNotice()
        _archiveMigrationNotice.value = null
    }

    fun restoreArchiveBackup() = viewModelScope.launch {
        if (_archiveRestoreState.value == ArchiveRestoreState.Restoring) return@launch
        _archiveRestoreState.value = ArchiveRestoreState.Restoring
        container.usageRepository.restoreLegacyBackup().fold(
            onSuccess = {
                _archiveRestoreState.value = ArchiveRestoreState.Restored
                refreshStoredDataSummary()
                refresh()
                WidgetUpdateDispatcher.updateAll(container.applicationContext)
            },
            onFailure = {
                _archiveRestoreState.value = ArchiveRestoreState.Error(
                    it.message ?: "The backup could not be restored. Your compact archive is unchanged.",
                )
            },
        )
    }

    fun dismissArchiveRestoreResult() {
        _archiveRestoreState.value = ArchiveRestoreState.Idle
    }

    private fun refreshSeasonalTheme() {
        container.settingsRepository.refreshSeasonalDate()
        viewModelScope.launch {
            SeasonalLauncherIcon.sync(container.applicationContext, container.settingsRepository.settings.first().theme)
            WidgetUpdateDispatcher.updateAll(container.applicationContext)
        }
    }

    fun upgradeArchive() = viewModelScope.launch {
        if (_archiveUpgradeState.value == ArchiveUpgradeState.Upgrading) return@launch
        _archiveUpgradeState.value = ArchiveUpgradeState.Upgrading
        container.usageRepository.upgradeLegacyArchive().fold(
            onSuccess = {
                _archiveUpgradeState.value = ArchiveUpgradeState.Upgraded
                refreshStoredDataSummary()
                refresh()
                WidgetUpdateDispatcher.updateAll(container.applicationContext)
            },
            onFailure = {
                _archiveUpgradeState.value = ArchiveUpgradeState.Error(
                    it.message ?: "The archive could not be upgraded. Your previous archive is unchanged.",
                )
            },
        )
    }

    fun dismissArchiveUpgradeResult() {
        _archiveUpgradeState.value = ArchiveUpgradeState.Idle
    }

    fun deleteArchiveBackup() = viewModelScope.launch {
        if (_archiveBackupDeleteState.value == ArchiveBackupDeleteState.Deleting) return@launch
        _archiveBackupDeleteState.value = ArchiveBackupDeleteState.Deleting
        container.usageRepository.deleteLegacyBackup().fold(
            onSuccess = {
                _archiveBackupDeleteState.value = ArchiveBackupDeleteState.Deleted
                refreshStoredDataSummary()
            },
            onFailure = {
                _archiveBackupDeleteState.value = ArchiveBackupDeleteState.Error(
                    it.message ?: "The safety backup could not be deleted. Try again.",
                )
            },
        )
    }

    fun dismissArchiveBackupDeleteResult() {
        _archiveBackupDeleteState.value = ArchiveBackupDeleteState.Idle
    }

    fun completeOnboarding() = viewModelScope.launch {
        if (container.permissionManager.hasUsageAccess()) container.settingsRepository.completeOnboarding()
    }
    fun setTheme(value: ThemePreference) = viewModelScope.launch {
        container.settingsRepository.setTheme(value)
        SeasonalLauncherIcon.sync(container.applicationContext, SeasonalThemeAvailability.activeTheme(value))
        WidgetUpdateDispatcher.updateAll(container.applicationContext)
    }
    fun setThemeFromDrawer(value: ThemePreference) = viewModelScope.launch {
        container.settingsRepository.setThemeFromDrawer(value)
        SeasonalLauncherIcon.sync(container.applicationContext, SeasonalThemeAvailability.activeTheme(value))
        WidgetUpdateDispatcher.updateAll(container.applicationContext)
    }
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
    fun setWidgetShowThemeGraphics(value: Boolean) = viewModelScope.launch {
        container.settingsRepository.setWidgetShowThemeGraphics(value)
        ScreenTimeWidgetProvider.updateAll(container.applicationContext)
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
    fun setDaylineWidgetShowThemeGraphics(value: Boolean) = viewModelScope.launch {
        container.settingsRepository.setDaylineWidgetShowThemeGraphics(value)
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

    private var updateCheckGeneration = 0

    private fun checkForUpdates(channel: String) {
        if (_updateState.value is UpdateState.Downloading || _updateState.value is UpdateState.Completed) return
        val generation = ++updateCheckGeneration
        _updateState.value = UpdateState.Checking
        VersionUpdater.checkForUpdates(
            currentVersion = BuildConfig.VERSION_NAME,
            includePrereleases = channel == "pre-release",
            onResult = { result ->
                viewModelScope.launch {
                    if (generation != updateCheckGeneration) return@launch
                    if (result is UpdateState.UpdateAvailable) {
                        container.settingsRepository.rememberUpdate(result.toDeferredUpdate(remindAfterMillis = 0L))
                    } else if (result is UpdateState.Idle) {
                        container.settingsRepository.clearRememberedUpdate()
                    }
                    if (generation == updateCheckGeneration) _updateState.value = result
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
        val settings = container.settingsRepository.settings.first()
        val remembered = settings.deferredUpdate
        if (remembered?.version == update.version &&
            remembered.remindAfterMillis <= System.currentTimeMillis() &&
            SemanticVersion.isNewer(remembered.version, BuildConfig.VERSION_NAME)
        ) {
            checkForUpdates(settings.updateChannel)
        }
    }

    private val _betaVersions = MutableStateFlow<BetaVersionsState>(BetaVersionsState.Idle)
    val betaVersions: StateFlow<BetaVersionsState> = _betaVersions

    fun loadBetaVersions() {
        if (_betaVersions.value is BetaVersionsState.Loading) return
        _betaVersions.value = BetaVersionsState.Loading
        VersionUpdater.fetchReleases { result ->
            _betaVersions.value = result.fold(
                onSuccess = { BetaVersionsState.Ready(VersionUpdater.managedVersions(it)) },
                onFailure = { BetaVersionsState.Error(it.message ?: "Couldn’t load versions.") },
            )
        }
    }

    fun selectManagedVersion(update: UpdateState.UpdateAvailable) {
        if (_updateState.value !is UpdateState.Downloading) {
            updateCheckGeneration++
            _updateState.value = update
        }
    }

    fun dismissVersionSwitch() { _updateState.value = UpdateState.Idle }

    fun startApkDownload(downloadUrl: String) {
        if (_updateState.value is UpdateState.Downloading) return
        updateCheckGeneration++
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

    fun hasUsageAccess() = container.permissionManager.hasUsageAccess()

    fun installedFromApk() = container.permissionManager.installedFromApk()

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }

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

sealed interface BetaVersionsState {
    data object Idle : BetaVersionsState
    data object Loading : BetaVersionsState
    data class Ready(val versions: List<UpdateState.UpdateAvailable>) : BetaVersionsState
    data class Error(val message: String) : BetaVersionsState
}
