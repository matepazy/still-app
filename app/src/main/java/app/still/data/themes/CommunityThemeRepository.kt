package app.still.data.themes

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import app.still.widget.WidgetUpdateDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Legacy package slots. System is resolved once on install/restore, never followed live.
enum class ThemeAppearance { System, Light, Dark }

private fun fixedAppearance(context: Context, appearance: ThemeAppearance): ThemeAppearance =
    if (appearance != ThemeAppearance.System) appearance
    else if (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        ThemeAppearance.Dark else ThemeAppearance.Light

data class InstalledTheme(
    val content: ThemePackage, val grants: Set<String>, val link: String?, val checkUpdates: Boolean,
    val pending: ThemePackage? = null, val lastChecked: Long = 0, val updateError: String? = null,
    val appearance: ThemeAppearance = ThemeAppearance.System,
)
data class CommunityThemeState(val loaded: Boolean = false, val themes: List<InstalledTheme> = emptyList(), val activeId: String? = null, val restoreError: String? = null) {
    val active: InstalledTheme? get() = themes.firstOrNull { it.content.theme.id == activeId && it.content.theme.available() }
}

/** Private immutable packages plus an atomic catalog. Update downloads never grant access or activate themselves. */
class CommunityThemeRepository(
    private val context: Context,
    private val fetchLink: suspend (String) -> ByteArray = ThemeLinkClient()::fetch,
    private val directory: File = File(context.noBackupFilesDir, "community-themes"),
) {
    init { require(directory.isDirectory || directory.mkdirs()) { "Cannot create private theme storage" } }
    private val catalog = AtomicFile(File(directory, "catalog.json"))
    private val mutableState = MutableStateFlow(CommunityThemeState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (catalog.baseFile.exists()) {
                    val json = JSONObject(catalog.openRead().use { String(ThemePackages.readLimited(it, 256 * 1024), Charsets.UTF_8) })
                    val entries = json.getJSONArray("themes")
                    require(entries.length() <= 12)
                    val themes = (0 until entries.length()).map { index ->
                        val item = entries.getJSONObject(index)
                        val content = readPackage(item.getString("digest"))
                        val grants = item.getJSONArray("grants").let { a -> (0 until a.length()).map(a::getString).toSet() }
                        val valid = content.theme.requests.map(::permissionKey).toSet()
                        require(grants.all { it in valid })
                        val link = item.optString("link").takeIf { it.isNotEmpty() }?.also { ThemeLinkClient.validateUrl(it) }
                        val pending = item.optString("pending").takeIf { it.isNotEmpty() }?.let(::readPackage)?.also {
                            require(it.theme.id == content.theme.id && ThemeCompose.newer(it.theme.version, content.theme.version))
                        }
                        InstalledTheme(content, grants, link, item.optBoolean("checks"), pending, item.optLong("checked"), item.optString("error").takeIf(String::isNotEmpty),
                            fixedAppearance(context, ThemeAppearance.valueOf(item.optString("appearance", ThemeAppearance.System.name))))
                    }
                    require(themes.sumOf { it.content.bytes.size.toLong() + (it.pending?.bytes?.size ?: 0) } <= 40 * 1024 * 1024)
                    require(themes.distinctBy { it.content.theme.id }.size == themes.size)
                    val restored = CommunityThemeState(true, themes, json.optString("active").takeIf(String::isNotEmpty))
                    // Persist the migration before publishing, so a restart keeps this appearance.
                    if ((0 until entries.length()).any { entries.getJSONObject(it).optString("appearance", "System") == "System" }) {
                        persistCatalog(restored)
                    }
                    mutableState.value = restored
                } else mutableState.value = CommunityThemeState(loaded = true)
                mutableState.value.active?.content?.prepareImages()
                ThemeUpdateScheduler.sync(context, mutableState.value.themes.any { it.link != null && it.checkUpdates })
            } catch (_: Exception) {
                mutableState.value = CommunityThemeState(loaded = true, restoreError = "Saved community themes could not be restored. Add the original files again.")
            } finally { ready.complete(Unit) }
        }
    }
    suspend fun fromFile(uri: Uri): ThemePackage = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose a file through the system file picker" }
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open this file" }
            .use { ThemePackages.readLimited(it, ThemePackages.MAX_DOWNLOAD) }
        ThemePackages.load(bytes)
    }
    suspend fun fromLink(link: String): ThemePackage = withContext(Dispatchers.IO) {
        ThemeLinkClient.validateUrl(link)
        ThemePackages.load(fetchLink(link)).also { require(it.theme.updatable) { "Linked themes need an explicit id and version so Still can check for updates" } }
    }

    suspend fun install(content: ThemePackage, grants: Set<String>, link: String?, checks: Boolean) = mutate { old ->
        require(grants.all { key -> content.theme.requests.any { permissionKey(it) == key } }) { "Invalid permissions" }
        link?.let { ThemeLinkClient.validateUrl(it) }
        val previous = old.themes.firstOrNull { it.content.theme.id == content.theme.id }
        require(previous == null || ThemeCompose.newer(content.theme.version, previous.content.theme.version)) { "This theme is already installed. Updates need a newer version with the same id." }
        require(previous == null || link == previous.link) { "An update must use the installed theme's source. Remove it first to change its source." }
        val fixedPaletteAppearance = if (content.theme.light == content.theme.dark && content.theme.requests.isEmpty()) {
            if (CustomThemeBuilder.luminance(content.theme.light.getValue("background").text) < .179f) ThemeAppearance.Dark else ThemeAppearance.Light
        } else ThemeAppearance.System
        val themes = old.themes.filterNot { it.content.theme.id == content.theme.id } + InstalledTheme(content, grants, link, checks, appearance = fixedAppearance(context, previous?.appearance ?: fixedPaletteAppearance))
        require(themes.size <= 12) { "Remove a theme before adding another (12 maximum)" }
        require(themes.sumOf { it.content.bytes.size.toLong() + (it.pending?.bytes?.size ?: 0) } <= 40 * 1024 * 1024) { "Community themes exceed the 40 MB storage limit" }
        old.copy(themes = themes, activeId = content.theme.id, restoreError = null)
    }
    /** An explicit local edit is distinct from a linked update and cannot race a newer installed package. */
    suspend fun edit(id: String, expectedDigest: String, content: ThemePackage) = mutate { old ->
        val previous = requireNotNull(old.themes.firstOrNull { it.content.theme.id == id }) { "Theme is no longer installed" }
        require(ThemeCompose.digest(previous.content.bytes) == expectedDigest) { "This theme changed. Reopen Edit to use the latest colors." }
        require(content.theme.id == id && content.theme.requests == previous.content.theme.requests && content.theme.images == previous.content.theme.images) { "The edited theme must retain its identity and capabilities" }
        val appearance = if (CustomThemeBuilder.luminance(content.theme.light.getValue("background").text) < .179f) ThemeAppearance.Dark else ThemeAppearance.Light
        val replacement = previous.copy(content = content, link = null, checkUpdates = false, pending = null, updateError = null, appearance = appearance)
        val themes = old.themes.map { if (it.content.theme.id == id) replacement else it }
        require(themes.sumOf { it.content.bytes.size.toLong() + (it.pending?.bytes?.size ?: 0) } <= 40 * 1024 * 1024) { "Community themes exceed the 40 MB storage limit" }
        old.copy(themes = themes, activeId = id, restoreError = null)
    }
    suspend fun select(id: String?) = mutate { old ->
        require(id == null || old.themes.any { it.content.theme.id == id && it.content.theme.available() }) { "This theme is not available today" }
        old.copy(activeId = id)
    }
    suspend fun remove(id: String) = mutate { old -> old.copy(themes = old.themes.filterNot { it.content.theme.id == id }, activeId = old.activeId.takeUnless { it == id }) }
    suspend fun permissions(id: String, grants: Set<String>) = mutate { old -> old.copy(themes = old.themes.map { item ->
        if (item.content.theme.id != id) item else {
            require(grants.all { key -> item.content.theme.requests.any { permissionKey(it) == key } })
            item.copy(grants = grants)
        }
    }) }
    suspend fun appearance(id: String, appearance: ThemeAppearance) = mutate { old ->
        require(old.themes.any { it.content.theme.id == id }) { "Theme is no longer installed" }
        old.copy(themes = old.themes.map { if (it.content.theme.id == id) it.copy(appearance = fixedAppearance(context, appearance)) else it })
    }
    suspend fun updateChecks(id: String, enabled: Boolean) = mutate { old -> old.copy(themes = old.themes.map { if (it.content.theme.id == id) it.copy(checkUpdates = enabled && it.link != null) else it }) }
    suspend fun dismissUpdate(id: String) = mutate { old -> old.copy(themes = old.themes.map { if (it.content.theme.id == id) it.copy(pending = null) else it }) }

    suspend fun checkForUpdates(forceId: String? = null) {
        ready.await()
        // Fetch outside the mutex. Recheck source/version/opt-in before committing each result.
        val snapshot = state.value.themes.filter { it.link != null && (it.checkUpdates || it.content.theme.id == forceId) && (forceId != null || System.currentTimeMillis() - it.lastChecked >= 24 * 60 * 60 * 1000L) }
        for (installed in snapshot) {
            val result = runCatching { fromLink(installed.link!!) }
            result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
            mutate { old -> old.copy(themes = old.themes.map { item ->
                if (item.content.theme.id != installed.content.theme.id || item.link != installed.link || item.content.theme.version != installed.content.theme.version || (!item.checkUpdates && forceId == null)) item
                else {
                    val candidate = result.getOrNull()
                    val valid = candidate != null && candidate.theme.id == item.content.theme.id && ThemeCompose.newer(candidate.theme.version, item.content.theme.version)
                    val storageFits = candidate != null && old.themes.sumOf { it.content.bytes.size.toLong() + (if (it === item) 0 else it.pending?.bytes?.size ?: 0) } + candidate.bytes.size <= 40 * 1024 * 1024
                    val error = when {
                        result.isFailure -> "Could not check this link. Try again later."
                        candidate?.theme?.id != item.content.theme.id -> "The link returned a different theme. It was not installed."
                        candidate.theme.version == item.content.theme.version && !candidate.bytes.contentEquals(item.content.bytes) -> "The source changed without a version increase. It was not installed."
                        valid && !storageFits -> "Not enough theme storage for this update. Remove another theme."
                        else -> null
                    }
                    item.copy(pending = if (valid && storageFits && error == null) candidate else item.pending, lastChecked = System.currentTimeMillis(), updateError = error)
                }
            }) }
        }
    }
    private suspend fun mutate(transform: (CommunityThemeState) -> CommunityThemeState) = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            val next = transform(mutableState.value)
            next.active?.content?.prepareImages()
            persistCatalog(next)
            next.themes.filter { it !== next.active }.forEach { it.content.releaseImages(); it.pending?.releaseImages() }
            mutableState.value = next
            val retained = next.themes.flatMap { listOfNotNull(ThemeCompose.digest(it.content.bytes), it.pending?.bytes?.let(ThemeCompose::digest)) }.toSet()
            directory.listFiles()?.filter { it.name.endsWith(".package") && it.name.removeSuffix(".package") !in retained }?.forEach { it.delete() }
            ThemeUpdateScheduler.sync(context, next.themes.any { it.link != null && it.checkUpdates })
            WidgetUpdateDispatcher.updateAll(context)
        }
    }
    private fun persistCatalog(next: CommunityThemeState) {
        val entries = JSONArray()
        next.themes.forEach { item ->
            val digest = writePackage(item.content)
            val pending = item.pending?.let(::writePackage)
            entries.put(JSONObject().put("digest", digest).put("grants", JSONArray(item.grants.sorted())).put("link", item.link ?: "")
                .put("checks", item.checkUpdates).put("pending", pending ?: "").put("checked", item.lastChecked).put("error", item.updateError ?: "").put("appearance", item.appearance.name))
        }
        val bytes = JSONObject().put("themes", entries).put("active", next.activeId ?: "").toString().toByteArray()
        val out = catalog.startWrite()
        try { out.write(bytes); catalog.finishWrite(out) } catch (e: Exception) { catalog.failWrite(out); throw e }
    }
    private fun readPackage(digest: String): ThemePackage {
        require(digest.matches(Regex("[0-9a-f]{64}")))
        val bytes = File(directory, "$digest.package").inputStream().use { ThemePackages.readLimited(it, ThemePackages.MAX_DOWNLOAD) }
        require(ThemeCompose.digest(bytes) == digest)
        return ThemePackages.load(bytes)
    }
    private fun writePackage(content: ThemePackage): String {
        val digest = ThemeCompose.digest(content.bytes); val file = AtomicFile(File(directory, "$digest.package"))
        if (!file.baseFile.exists()) {
            val out = file.startWrite()
            try { out.write(content.bytes); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
        }
        return digest
    }
    companion object {
        // Include the reason in consent identity: revised requests require fresh review.
        fun permissionKey(request: ThemeRequest): String = ThemeCompose.digest("${request.source}\n${request.id}\n${request.type}\n${request.reason}".toByteArray())
    }
}
