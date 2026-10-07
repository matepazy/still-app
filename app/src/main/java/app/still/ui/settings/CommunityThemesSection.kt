package app.still.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.StillApplication
import app.still.R
import app.still.data.themes.CommunityThemeRepository
import app.still.data.themes.InstalledTheme
import app.still.data.themes.ThemeLinkClient
import app.still.data.themes.ThemePackage
import app.still.data.themes.ThemeAppearance
import app.still.ui.components.StillDrawer
import app.still.ui.components.StillIcons
import app.still.ui.theme.StillDarkColors
import app.still.ui.theme.StillLightColors
import app.still.ui.theme.StillSpacing
import app.still.ui.theme.communityStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CommunityThemesSection(onCommunitySelected: () -> Unit) {
    val context = LocalContext.current
    val repository = (context.applicationContext as StillApplication).container.communityThemes
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var drawer by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }
    var showReasons by remember(drawer) { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ThemePackage?>(null) }
    var managing by remember { mutableStateOf<String?>(null) }
    var fileUpdate by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf("") }
    var sourceLink by remember { mutableStateOf<String?>(null) }
    var grants by remember { mutableStateOf(emptySet<String>()) }
    var checks by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var cancellable by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun perform(canCancel: Boolean = false, block: suspend () -> Unit) {
        if (busy) return
        busy = true; cancellable = canCancel; error = null; notice = null
        operation = scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message?.take(240) ?: "Could not complete this action. Try again." }
            finally { busy = false; cancellable = false; operation = null }
        }
    }
    fun dismiss() {
        if (busy && !cancellable) return
        operation?.cancel()
        if (drawer == null) { pending = null; error = null; notice = null }
        else closing = true
    }
    fun backToDetails() {
        if (busy) return
        pending = null; drawer = "manage"; error = null; notice = null
    }
    fun review(content: ThemePackage, from: String?, previous: InstalledTheme? = null) {
        pending = content; sourceLink = from; checks = previous?.checkUpdates ?: false
        grants = previous?.grants.orEmpty().intersect(content.theme.requests.map(CommunityThemeRepository::permissionKey).toSet())
        drawer = "review"; error = null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val updateId = fileUpdate
        fileUpdate = null
        if (uri != null) perform(canCancel = true) {
            val content = repository.fromFile(uri)
            coroutineContext.ensureActive()
            val previous = updateId?.let { id -> state.themes.firstOrNull { it.content.theme.id == id } }
            if (updateId != null) {
                require(previous != null && content.theme.id == updateId) { "Choose an update for this theme." }
                require(app.still.data.themes.ThemeCompose.newer(content.theme.version, previous.content.theme.version)) { "Choose a newer version of this theme." }
            }
            review(content, null, previous)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = StillSpacing.large), verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
        Text("Community themes", style = MaterialTheme.typography.titleLarge)
        state.restoreError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.themes.isNotEmpty()) Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
            state.themes.forEach { installed ->
                CommunityThemeCard(
                    installed = installed,
                    selected = state.activeId == installed.content.theme.id,
                    enabled = !busy && installed.content.theme.available(),
                    manageEnabled = !busy,
                    onSelect = { perform { onCommunitySelected(); repository.select(installed.content.theme.id) } },
                    onManage = { managing = installed.content.theme.id; drawer = "manage"; error = null; notice = null },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)).clickable(enabled = !busy, role = Role.Button, onClick = { drawer = "add"; error = null; notice = null })
                .padding(StillSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(StillIcons.Download), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Add a theme", style = MaterialTheme.typography.titleMedium)
                Text("From a file or a link", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(StillIcons.ChevronRight), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Loading theme…", style = MaterialTheme.typography.bodyMedium)
            if (cancellable && drawer == null) TextButton(onClick = ::dismiss) { Text("Cancel") }
        }
        if (drawer == null) { error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) } }
    }

    if (drawer != null) {
        // Permission and source controls always use trusted host colors, even when an installed theme hides text.
        val trustedColors = app.still.ui.theme.communityControlColors(state.active?.appearance ?: ThemeAppearance.System)
        MaterialTheme(colorScheme = trustedColors) {
            StillDrawer(onDismissRequest = { closing = false; drawer = null; pending = null; error = null; notice = null }, dismissible = !busy || cancellable,
                dismissRequested = closing, onBackRequest = if (drawer in listOf("permissions", "updates")) ::backToDetails else null) {
                Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                    when (drawer) {
                        "add" -> {
                            Text("Add a theme", style = MaterialTheme.typography.titleLarge)
                            Text("Choose a .tc or .tcb file. Legacy .stc and .stcb files also work.", style = MaterialTheme.typography.bodyMedium)
                            Text("Add a Theme Compose file or a direct link. You choose which system settings each theme can read.", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { drawer = null; picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("From a file") }
                            OutlinedButton(onClick = { drawer = "link"; link = "" }, modifier = Modifier.fillMaxWidth()) { Text("From a link") }
                        }
                        "link" -> {
                            Text("From a link", style = MaterialTheme.typography.titleLarge)
                            Text("Use a direct HTTPS link to the theme file. The server will see your IP address when Still downloads it. No usage data is sent.", style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(value = link, onValueChange = { if (it.length <= 2048) link = it }, label = { Text("HTTPS link") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { perform(canCancel = true) { val url = ThemeLinkClient.validateUrl(link.trim()).toString(); val content = repository.fromLink(url); coroutineContext.ensureActive(); review(content, url) } }, enabled = !busy && link.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Download and review") }
                        }
                        "review", "permissions" -> {
                            val content = pending
                            if (content != null) {
                                val theme = content.theme
                                Text(if (drawer == "permissions") "Theme permissions" else "Review ${theme.title}", style = MaterialTheme.typography.titleLarge)
                                if (drawer == "review") {
                                    val preview = remember(content) { communityStyle(context, InstalledTheme(content, emptySet(), null, false)) }
                                    MaterialTheme(colorScheme = preview.colors) {
                                        Column(Modifier.fillMaxWidth().background(preview.colors.surface, RoundedCornerShape(16.dp)).padding(20.dp)) {
                                            preview.image("index-image")?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 120.dp)) }
                                            Text("Theme preview", color = preview.colors.onSurface, style = MaterialTheme.typography.titleMedium)
                                            Text("Appearance with requests denied", color = preview.colors.onSurface, style = MaterialTheme.typography.bodySmall)
                                            Box(Modifier.padding(top = 12.dp).background(preview.colors.primary, RoundedCornerShape(8.dp)).padding(12.dp)) { Text("Accent", color = preview.colors.onPrimary) }
                                        }
                                    }
                                }
                                if (theme.requests.isEmpty()) Text("No permissions needed.", style = MaterialTheme.typography.bodyMedium)
                                else if (drawer == "review") Text("Choose what this theme can read. Everything stays on your phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                theme.requests.forEach { request ->
                                    val key = CommunityThemeRepository.permissionKey(request)
                                    val allowed = key in grants
                                    val label = when (request.source) {
                                        "sysLightMode" -> "System appearance"
                                        "sysFontScale" -> "Text size"
                                        "sysReducedMotion" -> "Reduced motion"
                                        else -> request.description
                                    }
                                    PermissionChoice(label, if (drawer == "review" || showReasons) request.reason else "", allowed, !busy) {
                                        val next = if (it) grants + key else grants - key
                                        if (drawer == "permissions") perform { repository.permissions(theme.id, next); grants = next }
                                        else grants = next
                                    }
                                }
                                if (drawer == "permissions" && theme.requests.isNotEmpty()) TextButton(onClick = { showReasons = !showReasons }) {
                                    Text(if (showReasons) "Hide reasons" else "Why these permissions?")
                                }
                                if (sourceLink != null && drawer == "review") {
                                    Text("Source: ${ThemeLinkClient.validateUrl(sourceLink!!).host}", style = MaterialTheme.typography.bodySmall)
                                    PermissionChoice("Check this link daily", "Downloads stay pending until you review and install them. The server sees your IP address on each check.", checks, !busy) { checks = it }
                                }
                                if (drawer == "review" && theme.images.containsKey("branding.launcher-icon")) Text("Android keeps Still’s built-in launcher icon. This theme’s launcher image is not applied.", style = MaterialTheme.typography.bodySmall)
                                if (drawer == "review") Button(enabled = !busy, onClick = { perform {
                                    repository.install(content, grants, sourceLink, checks && sourceLink != null)
                                    onCommunitySelected()
                                    pending = null; drawer = null; notice = "Theme installed."
                                } }, modifier = Modifier.fillMaxWidth()) { Text("Install theme") }
                            }
                        }
                        "manage" -> {
                            val item = state.themes.firstOrNull { it.content.theme.id == managing }
                            if (item != null) {
                                Text(item.content.theme.title, style = MaterialTheme.typography.titleLarge)
                                Text("${item.content.theme.author} · ${item.content.theme.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.content.theme.description.isNotBlank()) Text(item.content.theme.description, style = MaterialTheme.typography.bodyMedium)
                                if (item.content.theme.license.isNotBlank()) Text("License: ${item.content.theme.license}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                                    Text("Appearance", style = MaterialTheme.typography.titleSmall)
                                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                                        ThemeAppearance.entries.forEach { appearance ->
                                            FilterChip(selected = item.appearance == appearance, enabled = !busy,
                                                onClick = { perform { repository.appearance(item.content.theme.id, appearance) } },
                                                label = { Text(appearance.name) })
                                        }
                                    }
                                }
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                                    val count = item.content.theme.requests.count { CommunityThemeRepository.permissionKey(it) in item.grants }
                                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    SettingRow("Permissions", supporting = if (item.content.theme.requests.isEmpty()) "No system data requested" else "$count of ${item.content.theme.requests.size} allowed", enabled = !busy,
                                        onClick = { pending = item.content; grants = item.grants; sourceLink = item.link; drawer = "permissions"; error = null; notice = null })
                                    }
                                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    SettingRow("Updates", supporting = when {
                                        item.pending != null -> "Version ${item.pending.theme.version} ready"
                                        item.link == null -> "Import a newer file"
                                        item.checkUpdates -> "Daily checks on"
                                        else -> "Daily checks off"
                                    }, enabled = !busy, onClick = {
                                        if (item.link == null) { fileUpdate = item.content.theme.id; drawer = null; picker.launch(arrayOf("*/*")) }
                                        else { drawer = "updates"; error = null; notice = null }
                                    })
                                    }
                                }
                                TextButton(enabled = !busy, onClick = { perform { repository.remove(item.content.theme.id); drawer = null; notice = "Theme removed." } },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer, containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) { Text("Remove theme") }
                            }
                        }
                        "updates" -> {
                            val item = state.themes.firstOrNull { it.content.theme.id == managing }
                            if (item?.link != null) {
                                Text("Theme updates", style = MaterialTheme.typography.titleLarge)
                                Text(ThemeLinkClient.validateUrl(item.link).host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                PermissionChoice("Check daily", "Updates need approval. Each check shares your IP address with this server.", item.checkUpdates, !busy) { enabled -> perform { repository.updateChecks(item.content.theme.id, enabled) } }
                                item.updateError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                if (item.pending != null) {
                                    Button(enabled = !busy, onClick = { review(item.pending, item.link, item) }, modifier = Modifier.fillMaxWidth()) { Text("Review version ${item.pending.theme.version}") }
                                    TextButton(enabled = !busy, onClick = { perform { repository.dismissUpdate(item.content.theme.id) } }, modifier = Modifier.fillMaxWidth()) { Text("Discard update") }
                                } else {
                                    Button(enabled = !busy, onClick = { perform(canCancel = true) {
                                        repository.checkForUpdates(item.content.theme.id)
                                        val checked = repository.state.value.themes.firstOrNull { it.content.theme.id == item.content.theme.id }
                                        if (checked != null && checked.pending == null && checked.updateError == null) notice = "No update available."
                                    } }, modifier = Modifier.fillMaxWidth()) { Text("Check now") }
                                }
                            }
                        }
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (drawer == "manage" && !busy) Button(onClick = ::dismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    else TextButton(onClick = {
                        if (!busy && drawer in listOf("permissions", "updates")) backToDetails()
                        else dismiss()
                    }, enabled = !busy || cancellable, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Cancel" else when (drawer) { "manage" -> "Done"; "permissions", "updates" -> "Back"; else -> "Cancel" }) }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun CommunityThemeCard(installed: InstalledTheme, selected: Boolean, enabled: Boolean, manageEnabled: Boolean, onSelect: () -> Unit, onManage: () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val fontScale = LocalDensity.current.fontScale
    val preview = remember(installed, dark, fontScale) { communityStyle(context, installed, dark) }
    val indexPath = installed.content.theme.images["index-image"]?.resolve(preview.values)
    val artwork by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, installed.content, indexPath) {
        value = withContext(Dispatchers.IO) {
            indexPath?.let { installed.content.assets[it] }?.let { bytes ->
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                options.inSampleSize = 1
                while (options.outWidth / options.inSampleSize > 512 || options.outHeight / options.inSampleSize > 512) options.inSampleSize *= 2
                options.inJustDecodeBounds = false
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
            }
        }
    }
    val theme = installed.content.theme
    val inset = if (selected) 2.dp else 3.dp
    val innerShape = RoundedCornerShape(16.dp - inset)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth()
        .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
        .padding(inset).clip(innerShape).background(MaterialTheme.colorScheme.surfaceContainerLow)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)) {
        Box(Modifier.fillMaxWidth().height(104.dp).background(preview.colors.background)) {
            val thumbnail = artwork
            if (thumbnail != null) Image(thumbnail, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else {
                Image(painterResource(R.drawable.ic_still_wordmark), null,
                    Modifier.align(Alignment.CenterStart).padding(start = StillSpacing.large).width(100.dp).height(50.dp),
                    colorFilter = ColorFilter.tint(preview.wordmark ?: preview.colors.primary))
                Canvas(Modifier.align(Alignment.CenterEnd).padding(end = StillSpacing.large).size(72.dp)) {
                    drawRoundRect(preview.colors.surface, cornerRadius = CornerRadius(12.dp.toPx()))
                    listOf(.66f, .9f, .54f, .78f).forEachIndexed { index, fraction ->
                        val h = 4.dp.toPx()
                        drawRoundRect(preview.colors.onSurface.copy(alpha = .72f), Offset(size.width * .16f, size.height * (.22f + index * .17f)),
                            Size(size.width * .68f * fraction, h), CornerRadius(h / 2))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(StillSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(theme.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(theme.version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!theme.available()) Text("Available ${theme.availability?.from} – ${theme.availability?.to}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selected && !theme.available()) Text("Selected · outside availability dates", style = MaterialTheme.typography.labelSmall)
                if (installed.pending != null) Text("Update ${installed.pending.theme.version} ready to review", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onManage, enabled = manageEnabled, modifier = Modifier.size(48.dp)) {
                Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(StillIcons.More), "Manage ${theme.title} theme", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            if (selected) Box(Modifier.padding(start = StillSpacing.medium).size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(StillIcons.Check), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PermissionChoice(title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(StillSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
