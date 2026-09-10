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

class AppContainer(val applicationContext: Application) {
    val permissionManager = UsagePermissionManager(applicationContext)
    val settingsRepository = SettingsRepository(applicationContext)
    val usageRepository = UsageRepository(applicationContext, UsageStatsDataSource(applicationContext))
}
