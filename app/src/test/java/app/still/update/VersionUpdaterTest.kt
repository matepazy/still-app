package app.still.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUpdaterTest {
    @Test
    fun selectsLatestVersionRegardlessOfApiOrder() {
        val latest = release("v1.5.4")
        val releases = listOf(release("v1.5.3"), latest, release("v1.5.2"))
        val result = check(releases, current = "1.5.2") as UpdateState.UpdateAvailable
        assertEquals(latest.tag_name, result.version)
        assertEquals(latest.body, result.notes)
        assertEquals(latest.assets.single().browser_download_url, result.downloadUrl)
        assertEquals(result, check(releases.reversed(), current = "1.5.2"))
    }

    @Test
    fun betaChannelPrefersStableForSameVersionAndBetaOverOlderStable() {
        val releases = listOf(release("v1.5.5-beta1", prerelease = true), release("v1.5.4"))
        assertEquals("v1.5.5-beta1", (check(releases, beta = true) as UpdateState.UpdateAvailable).version)
        assertEquals("v1.5.5", (check(releases + release("v1.5.5"), beta = true) as UpdateState.UpdateAvailable).version)
    }

    @Test
    fun betaChannelSelectsHighestBetaNumber() {
        val releases = listOf(2, 10, 1).map { release("v1.5.5-beta$it", prerelease = true) }
        assertEquals("v1.5.5-beta10", (check(releases, beta = true) as UpdateState.UpdateAvailable).version)
        assertEquals(UpdateState.Idle, check(releases, current = "1.5.5", beta = true))
        assertEquals(UpdateState.Idle, check(releases, current = "1.5.5-beta10", beta = true))
    }

    @Test
    fun releaseChannelUsesGitHubPrereleaseFlag() {
        val releases = listOf(release("v1.6.0", prerelease = true), release("v1.5.4"))
        assertEquals("v1.5.4", (check(releases) as UpdateState.UpdateAvailable).version)
        assertEquals(UpdateState.Idle, check(releases.take(1)))
        // The GitHub flag controls eligibility, even if the tag contains a beta suffix.
        assertEquals("v1.5.5-beta1", (check(listOf(release("v1.5.5-beta1"))) as UpdateState.UpdateAvailable).version)
    }

    @Test
    fun stableReleaseUpdatesInstalledBetaWithoutOfferingDowngrades() {
        assertEquals("v1.5.5", (check(listOf(release("v1.5.5")), current = "1.5.5-beta2") as UpdateState.UpdateAvailable).version)
        assertEquals(UpdateState.Idle, check(listOf(release("v1.5.4")), current = "1.5.5-beta2"))
        assertEquals(UpdateState.Idle, check(emptyList()))
        assertEquals(UpdateState.Idle, check(listOf(release("v1.5.4")), current = "1.5.4"))
    }

    @Test
    fun excludesDraftReleasesAndReportsMissingLatestApk() {
        assertEquals("v1.5.4", (check(listOf(release("v1.6.0").copy(draft = true), release("v1.5.4")), beta = true) as UpdateState.UpdateAvailable).version)
        assertTrue(check(listOf(release("v1.5.4").copy(assets = emptyList()), release("v1.5.3"))) is UpdateState.Error)
    }

    @Test
    fun prefersStillApkAndRetainsLegacyFallback() {
        val target = release("v1.5.4")
        val otherApk = GitHubAsset("other.apk", "https://example.com/other.apk")
        val result = check(listOf(target.copy(assets = listOf(otherApk) + target.assets))) as UpdateState.UpdateAvailable
        assertEquals(target.assets.single().browser_download_url, result.downloadUrl)
        assertEquals(otherApk.browser_download_url, (check(listOf(target.copy(assets = listOf(otherApk)))) as UpdateState.UpdateAvailable).downloadUrl)
    }

    private fun check(releases: List<GitHubRelease>, current: String = "1.5.2", beta: Boolean = false) =
        VersionUpdater.updateStateForReleases(releases, current, beta)

    private fun release(version: String, prerelease: Boolean = false) = GitHubRelease(
        tag_name = version,
        prerelease = prerelease,
        name = version,
        body = "Notes for $version",
        assets = listOf(GitHubAsset("still-$version.apk", "https://example.com/$version.apk")),
    )
}
