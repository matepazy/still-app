package app.still.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.R
import app.still.StillApplication
import app.still.data.themes.InstalledTheme
import app.still.ui.components.StillDrawer
import app.still.ui.components.StillIcons
import app.still.ui.theme.StillSpacing
import app.still.ui.theme.communityStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CustomThemesSection(onCustomThemeSelected: () -> Unit, onCreateTheme: () -> Unit, onEditTheme: (String) -> Unit) {
    val context = LocalContext.current
    val repository = (context.applicationContext as StillApplication).container.communityThemes
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var drawer by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<String?>(null) }
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
        if (drawer == null) { error = null; notice = null }
        else closing = true
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val managingId = managing
        if (uri != null && managingId != null) {
            val item = state.themes.firstOrNull { it.content.theme.id == managingId }
            if (item != null) perform(canCancel = true) {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(item.content.bytes)
                    }
                }
                notice = "Theme exported."
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = StillSpacing.large), verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
        Text("Custom themes", style = MaterialTheme.typography.titleLarge)
        state.restoreError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.themes.isNotEmpty()) Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
            state.themes.forEach { installed ->
                CustomThemeCard(
                    installed = installed,
                    selected = state.activeId == installed.content.theme.id,
                    enabled = !busy && installed.content.theme.available(),
                    manageEnabled = !busy,
                    onSelect = { perform { onCustomThemeSelected(); repository.select(installed.content.theme.id) } },
                    onManage = { managing = installed.content.theme.id; drawer = "manage"; error = null; notice = null },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)).clickable(enabled = !busy, role = Role.Button, onClick = {
                    onCreateTheme()
                    error = null
                    notice = null
                })
                .padding(StillSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(StillIcons.Add), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Create a theme", style = MaterialTheme.typography.titleMedium)
                Text("Choose colors and make it yours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(StillIcons.ChevronRight), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Saving theme…", style = MaterialTheme.typography.bodyMedium)
        }
        if (drawer == null) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (drawer != null) {
        StillDrawer(
            onDismissRequest = { closing = false; drawer = null; error = null; notice = null },
            dismissible = !busy || cancellable,
            expandToFitContent = false,
            dismissRequested = closing,
        ) {
                Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                    when (drawer) {
                        "manage" -> {
                            val item = state.themes.firstOrNull { it.content.theme.id == managing }
                            if (item != null) {
                                Text(item.content.theme.title, style = MaterialTheme.typography.titleLarge)
                                Text(item.content.theme.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.content.theme.description.isNotBlank()) Text(item.content.theme.description, style = MaterialTheme.typography.bodyMedium)

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                                    androidx.compose.material3.FilledTonalButton(
                                        onClick = { drawer = null; onEditTheme(item.content.theme.id) },
                                        enabled = !busy, modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(painterResource(StillIcons.Edit), null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(StillSpacing.small))
                                        Text("Edit")
                                    }
                                    androidx.compose.material3.FilledTonalButton(
                                        onClick = {
                                            val slug = item.content.theme.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "theme" }
                                            val suggestedName = "$slug.tc"
                                            exportLauncher.launch(suggestedName)
                                        }, enabled = !busy, modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(painterResource(StillIcons.Export), null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(StillSpacing.small))
                                        Text("Export")
                                    }
                                }

                                TextButton(
                                    enabled = !busy,
                                    onClick = { perform { repository.remove(item.content.theme.id); drawer = null; notice = "Theme removed." } },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(painterResource(StillIcons.Delete), null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(StillSpacing.small))
                                    Text("Remove theme")
                                }
                            }
                        }
                    }

                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (drawer == "manage" && !busy) {
                        Button(onClick = ::dismiss, modifier = Modifier.fillMaxWidth()) {
                            Icon(painterResource(StillIcons.Check), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(StillSpacing.small))
                            Text("Done")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
}

@Composable
private fun CustomThemeCard(
    installed: InstalledTheme,
    selected: Boolean,
    enabled: Boolean,
    manageEnabled: Boolean,
    onSelect: () -> Unit,
    onManage: () -> Unit,
) {
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
    Column(
        Modifier.fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(inset).clip(innerShape).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
    ) {
        Box(Modifier.fillMaxWidth().height(104.dp).background(preview.colors.background)) {
            val thumbnail = artwork
            if (thumbnail != null) Image(thumbnail, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else {
                Image(
                    painterResource(R.drawable.ic_still_wordmark),
                    null,
                    Modifier.align(Alignment.CenterStart).padding(start = StillSpacing.large).width(100.dp).height(50.dp),
                    colorFilter = ColorFilter.tint(preview.wordmark ?: preview.colors.primary),
                )
                Canvas(Modifier.align(Alignment.CenterEnd).padding(end = StillSpacing.large).size(72.dp)) {
                    drawRoundRect(preview.colors.surface, cornerRadius = CornerRadius(12.dp.toPx()))
                    listOf(.66f, .9f, .54f, .78f).forEachIndexed { index, fraction ->
                        val h = 4.dp.toPx()
                        drawRoundRect(
                            preview.colors.onSurface.copy(alpha = .72f),
                            Offset(size.width * .16f, size.height * (.22f + index * .17f)),
                            Size(size.width * .68f * fraction, h),
                            CornerRadius(h / 2),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(StillSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(theme.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
