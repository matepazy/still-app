package app.still.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageSetupStateTest {
    @Test fun successfulUsageTripCompletes() {
        assertEquals(UsageSetupState.Granted, UsageSetupState.WaitingForUsageSettings.afterUsageSettings(true))
    }

    @Test fun failedUsageTripShowsRecoveryAndCanBeDismissed() {
        val recovered = UsageSetupState.WaitingForUsageSettings.afterUsageSettings(false)
        assertEquals(UsageSetupState.Recovery, recovered)
        assertEquals(UsageSetupState.Explanation, recovered.dismissRecovery())
    }

    @Test fun appInfoReturnChainsOnceToUsageSettings() {
        val next = UsageSetupState.WaitingForAppInfo.afterAppInfo(false)
        assertEquals(UsageSetupState.WaitingForUsageSettings, next)
        assertEquals(UsageSetupState.Recovery, next.afterUsageSettings(false))
        assertEquals(UsageSetupState.Recovery, UsageSetupState.Recovery.afterAppInfo(false))
    }

    @Test fun appInfoReturnWithGrantedAccessCompletes() {
        assertEquals(UsageSetupState.Granted, UsageSetupState.WaitingForAppInfo.afterAppInfo(true))
    }

    @Test fun noSettingsTripNeverShowsRecovery() {
        assertEquals(UsageSetupState.Explanation, UsageSetupState.Explanation.afterUsageSettings(false))
    }

    @Test fun apkCopyRequiresDetectionAndSupportedAndroid() {
        assertEquals(true, recoveryExplanation(true, true).contains("installed from an APK"))
        assertEquals(false, recoveryExplanation(true, false).contains("APK"))
        assertEquals(false, recoveryExplanation(false, true).contains("APK"))
    }
}
