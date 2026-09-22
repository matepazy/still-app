package app.still.data.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

enum class AppCategory(val displayName: String) {
    Social("Social"),
    Games("Games"),
    Video("Video"),
    MusicAndAudio("Music & audio"),
    Photography("Photography"),
    News("News"),
    MapsAndNavigation("Maps & navigation"),
    Productivity("Productivity"),
    Accessibility("Accessibility"),
    Other("Other"),
    ;

    companion object {
        fun fromAndroidCategory(category: Int): AppCategory = when (category) {
            ApplicationInfo.CATEGORY_SOCIAL -> Social
            ApplicationInfo.CATEGORY_GAME -> Games
            ApplicationInfo.CATEGORY_VIDEO -> Video
            ApplicationInfo.CATEGORY_AUDIO -> MusicAndAudio
            ApplicationInfo.CATEGORY_IMAGE -> Photography
            ApplicationInfo.CATEGORY_NEWS -> News
            ApplicationInfo.CATEGORY_MAPS -> MapsAndNavigation
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> Productivity
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> Accessibility
            else -> Other
        }

        internal fun defaultForPackage(packageName: String, androidCategory: Int): AppCategory =
            if (packageName == STILL_PACKAGE_NAME) Productivity else fromAndroidCategory(androidCategory)

        @Suppress("DEPRECATION")
        fun forPackage(packageManager: PackageManager, packageName: String): AppCategory {
            if (packageName == STILL_PACKAGE_NAME) return Productivity
            val applicationInfo = runCatching {
                packageManager.getApplicationInfo(packageName, 0)
            }.getOrNull()
            return defaultForPackage(
                packageName,
                applicationInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED,
            )
        }

        private const val STILL_PACKAGE_NAME = "app.still"
    }
}
