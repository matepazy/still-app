package app.still.data.usage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

class UsagePermissionManager(private val context: Context) {
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageSettingsIntent(): Intent = settingsIntent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun restrictedSettingsIntent(): Intent = settingsIntent(
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        data = Uri.fromParts("package", context.packageName, null),
    )

    private fun settingsIntent(action: String, data: Uri? = null): Intent {
        val requested = Intent(action, data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (requested.resolveActivity(context.packageManager) != null) {
            requested
        } else {
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
