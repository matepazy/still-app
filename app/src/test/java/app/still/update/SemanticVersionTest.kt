package app.still.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {
    @Test
    fun comparesMajorMinorAndPatchVersions() {
        assertTrue(SemanticVersion.isNewer("v1.0.0", "0.9.9"))
        assertTrue(SemanticVersion.isNewer("1.2.0", "1.1.9"))
        assertTrue(SemanticVersion.isNewer("1.2.4", "1.2.3"))
        assertFalse(SemanticVersion.isNewer("1.2.3", "1.2.3"))
        assertFalse(SemanticVersion.isNewer("1.1.9", "1.2.0"))
    }

    @Test
    fun ignoresTagPrefixAndBuildMetadata() {
        assertTrue(SemanticVersion.isNewer("V2.0.0-beta.1", "1.9.9"))
        assertFalse(SemanticVersion.isNewer("v0.9.0+12", "0.9.0"))
    }

    @Test
    fun ordersStableAndBetaVersions() {
        assertTrue(SemanticVersion.isNewer("v1.5.5", "v1.5.5-beta1"))
        assertTrue(SemanticVersion.isNewer("v1.5.5-beta1", "v1.5.4"))
        assertTrue(SemanticVersion.isNewer("v1.5.5-beta2", "v1.5.5-beta1"))
        assertTrue(SemanticVersion.isNewer("v1.5.5-beta10", "v1.5.5-beta2"))
        assertFalse(SemanticVersion.isNewer("v1.5.5-beta1", "v1.5.5"))
        assertFalse(SemanticVersion.isNewer("v1.5.5-beta1", "v1.5.5-beta2"))
        assertFalse(SemanticVersion.isNewer("v1.5.5-beta1", "v1.5.5-beta1"))
        assertFalse(SemanticVersion.isNewer("v1.5.5-beta1+12", "v1.5.5-beta.1+13"))
        assertFalse(SemanticVersion.isNewer("v1.5.5-beta.1", "v1.5.5-beta1"))
    }

    @Test
    fun recognizesStillReleaseApkNames() {
        assertTrue(VersionUpdater.isStillApkAssetName("still-v1.0.0.apk"))
        assertTrue(VersionUpdater.isStillApkAssetName("STILL-V1.2.3-beta.1.APK"))
        assertTrue(VersionUpdater.isStillApkAssetName("still-v1.5.5-beta10.apk"))
        assertFalse(VersionUpdater.isStillApkAssetName("spectre-v1.0.0.apk"))
        assertFalse(VersionUpdater.isStillApkAssetName("still-1.0.0.apk"))
        assertFalse(VersionUpdater.isStillApkAssetName("still-v1.0.apk"))
    }
}
