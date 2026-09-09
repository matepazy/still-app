package app.still

import android.app.Application
import app.still.data.settings.SettingsRepository
import app.still.data.usage.UsagePermissionManager
import app.still.data.usage.UsageRepository
import app.still.data.usage.UsageStatsDataSource

class StillApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val permissionManager = UsagePermissionManager(application)
    val settingsRepository = SettingsRepository(application)
    val usageRepository = UsageRepository(application, UsageStatsDataSource(application))
}
