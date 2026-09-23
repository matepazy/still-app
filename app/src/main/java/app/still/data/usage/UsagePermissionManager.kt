package app.still.data.usage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings

class UsagePermissionManager(private val context: Context) {
    fun installedFromApk(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            context.packageManager.getInstallSourceInfo(context.packageName).packageSource in setOf(
                PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE,
                PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE,
            )
        } catch (_: Exception) {
            false
        }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = try {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                context.applicationInfo.uid,
                context.packageName,
            )
        } catch (_: SecurityException) {
            AppOpsManager.MODE_ERRORED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageSettingsIntent(): Intent {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        return firstResolvableIntent(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    fun restrictedSettingsIntent(): Intent {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        return firstResolvableIntent(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    private fun firstResolvableIntent(vararg candidates: Intent): Intent =
        candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
