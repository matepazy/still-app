package app.still.ui.onboarding

/** The Settings trip that Still deliberately started. Results from other resumes do not advance it. */
internal enum class UsageSetupState {
    Explanation,
    WaitingForUsageSettings,
    Recovery,
    WaitingForAppInfo,
    Granted,
}

internal fun UsageSetupState.afterUsageSettings(granted: Boolean): UsageSetupState =
    if (granted) UsageSetupState.Granted
    else if (this == UsageSetupState.WaitingForUsageSettings) UsageSetupState.Recovery
    else this

internal fun UsageSetupState.afterAppInfo(granted: Boolean): UsageSetupState =
    if (granted) UsageSetupState.Granted
    else if (this == UsageSetupState.WaitingForAppInfo) UsageSetupState.WaitingForUsageSettings
    else this

internal fun UsageSetupState.dismissRecovery(): UsageSetupState =
    if (this == UsageSetupState.Recovery) UsageSetupState.Explanation else this
