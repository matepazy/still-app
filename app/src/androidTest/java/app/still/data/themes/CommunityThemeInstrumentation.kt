package app.still.data.themes

import android.app.Instrumentation
import android.app.job.JobScheduler
import android.os.Bundle
import app.still.StillApplication
import app.still.ui.theme.communityStyle
import app.still.ui.theme.themeColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Real Android storage/consent/update tests with a deterministic transport; no remote service is contacted. */
class CommunityThemeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = JSONObject(); var result = 0
        runOnMainSync { }
        try {
            check(targetContext.packageName == "app.still.themepreview") { "Use the isolated theme preview" }
            val application = targetContext.applicationContext as StillApplication
            val source = context.assets.open("themes/consent.tc").bufferedReader().use { it.readText() }
            var response = source.toByteArray()
            var holdDownload = false
            val downloadStarted = CompletableDeferred<Unit>()
            val directory = File(targetContext.noBackupFilesDir, "theme-test-${UUID.randomUUID()}")
            val repository = CommunityThemeRepository(application, {
                if (holdDownload) { downloadStarted.complete(Unit); awaitCancellation() }
                response
            }, directory)
            val link = "https://themes.example.org/theme.tc"
            runBlocking {
                repository.state.first { it.loaded }
                val original = ThemePackages.load(response)
                val originalGrant = CommunityThemeRepository.permissionKey(original.theme.requests.first())
                repository.install(original, setOf(originalGrant), link, true)
                check(repository.state.value.active?.content?.theme?.version == "1.0.0")
                val frozen = repository.state.value.active!!
                check(frozen.appearance != ThemeAppearance.System)
                // Granted expressions still receive the actual system value; only the slot is frozen.
                check(communityStyle(application, frozen, false).colors.background == communityStyle(application, frozen, true).colors.background)
                report.put("installedAppearanceDoesNotFollowSystem", true)
                check(application.getSystemService(JobScheduler::class.java).allPendingJobs.any { it.service.className == ThemeUpdateJobService::class.java.name && it.intervalMillis == 24 * 60 * 60 * 1000L })
                repository.appearance(original.theme.id, ThemeAppearance.Dark)
                val forcedDark = repository.state.value.active!!
                check(communityStyle(application, forcedDark, false).colors.background == themeColor("#111111"))
                check(communityStyle(application, forcedDark, false).values["LightMode"] == "light")
                repository.appearance(original.theme.id, ThemeAppearance.Light)
                check(communityStyle(application, repository.state.value.active!!, true).colors.background == themeColor("#FFFFFF"))
                check(communityStyle(application, repository.state.value.active!!, true).values["LightMode"] == "dark")
                repository.appearance(original.theme.id, ThemeAppearance.Dark)
                report.put("appearanceOverrideKeepsSystemRequestAccurate", true)
                holdDownload = true
                val beforeCancellation = repository.state.value
                val checking = launch { repository.checkForUpdates(original.theme.id) }
                downloadStarted.await()
                checking.cancelAndJoin()
                check(repository.state.value == beforeCancellation)
                holdDownload = false
                report.put("cancelledCheckDoesNotCommit", true)
                response = source.replace("version: 1.0.0", "version: 1.1.0")
                    .replace("@common", "#request sysReducedMotion:\n  id ReducedMotion\n  as boolean\n  reason \"Respect reduced animation\"\n@common").toByteArray()
                repository.checkForUpdates(original.theme.id)
                val staged = repository.state.value.themes.single()
                check(staged.pending?.theme?.version == "1.1.0")
                check(staged.content.theme.version == "1.0.0" && staged.grants == setOf(originalGrant))
                report.put("updateStagedWithoutActivation", true)
                repository.install(staged.pending!!, staged.grants, link, true)
                val upgraded = repository.state.value.active!!
                check(upgraded.content.theme.version == "1.1.0")
                check(upgraded.appearance == ThemeAppearance.Dark)
                report.put("appearancePreservedAcrossUpdate", true)
                val deniedNew = upgraded.content.theme.requests.single { it.source == "sysReducedMotion" }
                check(CommunityThemeRepository.permissionKey(deniedNew) !in upgraded.grants)
                check(communityStyle(application, upgraded).values[deniedNew.id] == null)
                report.put("newRequestsDenied", true)
                repository.permissions(original.theme.id, emptySet())
                check(communityStyle(application, repository.state.value.active!!).values.values.all { it == null })
                report.put("revocationApplied", true)
                val restored = CommunityThemeRepository(application, { response }, directory)
                val saved = restored.state.first { it.loaded }
                check(saved.activeId == original.theme.id && saved.themes.single().grants.isEmpty() && saved.active?.content?.theme?.version == "1.1.0")
                check(saved.active?.appearance == ThemeAppearance.Dark)
                report.put("appearanceRestored", true)
                report.put("privateStorageRestored", true)
                // Legacy System entries freeze once and persist that choice through restarts.
                val migrationDirectory = File(targetContext.noBackupFilesDir, "theme-migration-${UUID.randomUUID()}")
                val migration = CommunityThemeRepository(application, directory = migrationDirectory)
                migration.state.first { it.loaded }
                migration.install(original, emptySet(), null, false)
                val catalog = File(migrationDirectory, "catalog.json")
                val legacy = JSONObject(catalog.readText())
                legacy.getJSONArray("themes").getJSONObject(0).put("appearance", "System")
                catalog.writeText(legacy.toString())
                val migrated = CommunityThemeRepository(application, directory = migrationDirectory).state.first { it.loaded }.active!!
                check(migrated.appearance != ThemeAppearance.System)
                check(JSONObject(catalog.readText()).getJSONArray("themes").getJSONObject(0).getString("appearance") == migrated.appearance.name)
                val restoredMigration = CommunityThemeRepository(application, directory = migrationDirectory).state.first { it.loaded }.active!!
                check(restoredMigration.appearance == migrated.appearance)
                report.put("legacySystemAppearanceFrozenAndPersisted", true)
                val fixedSource = CustomThemeBuilder.buildFixedSource("Fixed ocean", CustomThemeBuilder.palette("#285B8A", CustomThemeBuilder.lightTones.first()), "custom-fixed-ocean")
                migration.install(ThemePackages.load(fixedSource.toByteArray()), emptySet(), null, false)
                val fixed = migration.state.value.active!!
                check(fixed.appearance == ThemeAppearance.Light)
                check(communityStyle(application, fixed, false).colors.toString() == communityStyle(application, fixed, true).colors.toString())
                report.put("createdPaletteStaysFixed", true)
                val editedColors = CustomThemeBuilder.palette("#64558F", CustomThemeBuilder.darkTones.last())
                val edited = CustomThemeBuilder.editPackage(fixed.content, "Edited lavender", editedColors)
                val originalDigest = ThemeCompose.digest(fixed.content.bytes)
                migration.edit(fixed.content.theme.id, originalDigest, edited)
                check(migration.state.value.themes.size == 2 && migration.state.value.activeId == fixed.content.theme.id)
                check(migration.state.value.active!!.content.theme.title == "Edited lavender")
                check(migration.state.value.active!!.content.theme.light == migration.state.value.active!!.content.theme.dark)
                check(runCatching { migration.edit(fixed.content.theme.id, originalDigest, fixed.content) }.isFailure)
                val restoredEdit = CommunityThemeRepository(application, directory = migrationDirectory).state.first { it.loaded }.active!!
                check(restoredEdit.content.theme.title == "Edited lavender" && restoredEdit.content.theme.id == fixed.content.theme.id)
                report.put("editingReplacesAndPersistsSameThemeWithoutDuplicates", true)
                report.put("staleEditRejected", true)
                val localEditRepo = CommunityThemeRepository(application, directory = File(targetContext.noBackupFilesDir, "theme-edit-${UUID.randomUUID()}"))
                localEditRepo.state.first { it.loaded }
                localEditRepo.install(original, setOf(originalGrant), link, true)
                val localContent = CustomThemeBuilder.editPackage(original, "Local revision", editedColors)
                localEditRepo.edit(original.theme.id, ThemeCompose.digest(original.bytes), localContent)
                val localRevision = localEditRepo.state.value.active!!
                check(localRevision.grants == setOf(originalGrant) && localRevision.content.theme.requests == original.theme.requests)
                check(localRevision.link == null && !localRevision.checkUpdates && localRevision.pending == null)
                report.put("localEditPreservesConsentAndStopsLinkedUpdates", true)
                val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
                val imageBytes = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                bitmap.recycle()
                val artworkSource = fixedSource + "@still-app\ntoday:\n  background: \"assets/bg.png\"\n"
                val bundleBytes = java.io.ByteArrayOutputStream()
                java.util.zip.ZipOutputStream(bundleBytes).use { zip ->
                    for ((name, bytes) in mapOf("theme.tc" to artworkSource.toByteArray(), "assets/bg.png" to imageBytes)) {
                        zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                    }
                }
                val artworkPackage = ThemePackages.load(bundleBytes.toByteArray())
                val editedArtwork = CustomThemeBuilder.editPackage(artworkPackage, "Still has artwork", editedColors)
                check(editedArtwork.theme.images == artworkPackage.theme.images && editedArtwork.assets.getValue("assets/bg.png").contentEquals(imageBytes))
                report.put("editingPreservesBundledArtwork", true)
                response = source.replace("version: 1.0.0", "version: 9.0.0").replace("id: test-theme", "id: other-theme").toByteArray()
                repository.checkForUpdates(original.theme.id)
                check(repository.state.value.active!!.content.theme.version == "1.1.0" && repository.state.value.themes.single().pending == null && repository.state.value.themes.single().updateError != null)
                report.put("identityChangeRejected", true)
                response = upgraded.content.bytes.toString(Charsets.UTF_8).replace("title: Example", "title: Mutated").toByteArray()
                repository.checkForUpdates(original.theme.id)
                check(repository.state.value.active!!.content.theme.title == "Example" && repository.state.value.themes.single().updateError != null)
                report.put("sameVersionMutationRejected", true)
                response = source.replace("version: 1.0.0", "version: 2.0.0").replace("sysLightMode:", "usageHistory:").toByteArray()
                repository.checkForUpdates(original.theme.id)
                check(repository.state.value.active!!.content.theme.version == "1.1.0" && repository.state.value.themes.single().pending == null)
                report.put("unsupportedRequestUpdateRejected", true)
                repository.updateChecks(original.theme.id, false)
                check(application.getSystemService(JobScheduler::class.java).allPendingJobs.none { it.service.className == ThemeUpdateJobService::class.java.name })
                report.put("checksRevocable", true)
                repository.remove(original.theme.id)
                check(repository.state.value.activeId == null && repository.state.value.themes.isEmpty())
                report.put("removeRestoresBuiltin", true)
            }
        } catch (e: Throwable) { result = 1; report.put("failure", android.util.Log.getStackTraceString(e)) }
        val file = File(targetContext.getExternalFilesDir(null), "community-theme-test.json")
        file.writeText(report.toString(2))
        finish(result, Bundle().apply { putString("report", file.absolutePath); putString("results", report.toString()) })
    }
}
