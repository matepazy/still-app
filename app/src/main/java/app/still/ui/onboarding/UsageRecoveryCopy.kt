package app.still.ui.onboarding

internal fun recoveryExplanation(supportsRestrictedSettings: Boolean, installedFromApk: Boolean): String = when {
    !supportsRestrictedSettings -> "Return to Android Settings and turn on Usage Access for Still."
    installedFromApk -> "Because Still was installed from an APK, Android may require one extra confirmation before Usage Access can be enabled."
    else -> "If Android showed “For your security, this setting is currently unavailable”, one extra confirmation may be required before Still can use Usage Access."
}
