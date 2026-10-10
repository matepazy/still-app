package app.still.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import app.still.data.themes.ThemePackage
import app.still.data.themes.ThemeQrCodec
import app.still.ui.compare.CompareQr
import app.still.ui.compare.CompareScanner
import app.still.ui.components.StillDrawer
import app.still.ui.components.StillIcons
import app.still.ui.theme.StillSpacing
import app.still.ui.theme.communityStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    var scanning by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf<ThemePackage?>(null) }
    var sharedQr by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

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
                    drawer = "add"
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

    if (scanning) {
        Dialog(onDismissRequest = { scanning = false; drawer = "add" },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            androidx.compose.material3.Scaffold(
                topBar = {
                    androidx.compose.material3.TopAppBar(title = { Text("Import a theme") }, navigationIcon = {
                        IconButton(onClick = { scanning = false; drawer = "add" }) {
                            Icon(painterResource(StillIcons.Back), "Back")
                        }
                    })
                },
            ) { padding ->
                CompareScanner(
                    onCode = { code ->
                        imported = ThemeQrCodec.load(code).getOrNull()
                        scanning = false
                        drawer = "import"
                    },
                    modifier = Modifier.fillMaxSize().padding(padding),
                    validateCode = { ThemeQrCodec.load(it).isSuccess },
                    title = "Scan a theme code",
                    instruction = "Point at the theme's QR code",
                    invalidMessage = "Not a Still theme code",
                )
            }
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
                        "add" -> {
                            Text("Create a theme", style = MaterialTheme.typography.titleLarge)
                            Button(onClick = { drawer = null; onCreateTheme() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(painterResource(StillIcons.Add), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(StillSpacing.small))
                                Text("Create a theme")
                            }
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { drawer = null; imported = null; scanning = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(painterResource(StillIcons.Scan), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(StillSpacing.small))
                                Text("Import a theme")
                            }
                        }
                        "share" -> {
                            Text("Share a theme", style = MaterialTheme.typography.titleLarge)
                            Text("Scan this code in Still to import the theme.", style = MaterialTheme.typography.bodyMedium)
                            sharedQr?.let { qr ->
                                Image(qr, "Theme QR code", Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Fit)
                            }
                            Text("The theme is shared directly between phones. No upload is needed.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = ::dismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                        }
                        "import" -> {
                            imported?.let { content ->
                                Text("Import a theme", style = MaterialTheme.typography.titleLarge)
                                CustomThemeCard(InstalledTheme(content, emptySet(), null, false),
                                    selected = false, enabled = false, manageEnabled = false, onSelect = {}, onManage = {})
                                if (content.theme.requests.isNotEmpty()) {
                                    Text("This theme requests access to local data. Importing does not grant access.",
                                        style = MaterialTheme.typography.bodyMedium)
                                    content.theme.requests.forEach { Text(it.description, style = MaterialTheme.typography.bodySmall) }
                                }
                                Button(enabled = !busy, onClick = {
                                    perform {
                                        repository.install(content, emptySet(), null, false)
                                        onCustomThemeSelected()
                                        imported = null
                                        drawer = null
                                        notice = "Theme imported."
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Import theme") }
                            }
                        }
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
                                            perform(canCancel = true) {
                                                sharedQr = withContext(Dispatchers.Default) {
                                                    CompareQr.bitmap(ThemeQrCodec.encode(item.content.bytes)).asImageBitmap()
                                                }
                                                drawer = "share"
                                            }
                                        }, enabled = !busy, modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(painterResource(StillIcons.Export), null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(StillSpacing.small))
                                        Text("Share")
                                    }
                                }

                                TextButton(
                                    enabled = !busy,
                                    onClick = { drawer = "remove"; error = null; notice = null },
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
                        "remove" -> {
                            val item = state.themes.firstOrNull { it.content.theme.id == managing }
                            if (item != null) {
                                Text("Remove theme?", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Remove \"${item.content.theme.title}\" from your saved themes? This cannot be undone.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        perform {
                                            repository.remove(item.content.theme.id)
                                            drawer = null
                                            managing = null
                                            Toast.makeText(context, "Theme removed", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(painterResource(StillIcons.Delete), null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(StillSpacing.small))
                                    Text("Remove theme")
                                }
                                TextButton(enabled = !busy, onClick = ::dismiss, modifier = Modifier.fillMaxWidth()) {
                                    Text("Cancel")
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
