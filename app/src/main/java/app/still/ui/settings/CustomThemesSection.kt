package app.still.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.R
import app.still.StillApplication
import app.still.data.themes.BackgroundTone
import app.still.data.themes.CustomThemeBuilder
import app.still.data.themes.InstalledTheme
import app.still.data.themes.ThemeAppearance
import app.still.data.themes.ThemePackages
import app.still.ui.components.StillDrawer
import app.still.ui.components.StillIcons
import app.still.ui.theme.StillSpacing
import app.still.ui.theme.communityStyle
import app.still.ui.theme.themeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CustomThemesSection(onCustomThemeSelected: () -> Unit) {
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

    // Create theme draft state
    var themeTitle by remember { mutableStateOf("My Theme") }
    var selectedAccentHex by remember { mutableStateOf(CustomThemeBuilder.swatches[0].hex) }
    var customAccentHexInput by remember { mutableStateOf(CustomThemeBuilder.swatches[0].hex) }
    var selectedLightTone by remember { mutableStateOf(CustomThemeBuilder.lightTones[0]) }
    var selectedDarkTone by remember { mutableStateOf(CustomThemeBuilder.darkTones[0]) }
    var lightCustomRoles by remember { mutableStateOf(CustomThemeBuilder.defaultPalette(CustomThemeBuilder.swatches[0].hex, CustomThemeBuilder.lightTones[0], false)) }
    var darkCustomRoles by remember { mutableStateOf(CustomThemeBuilder.defaultPalette(CustomThemeBuilder.swatches[0].hex, CustomThemeBuilder.darkTones[0], true)) }
    var previewInDark by remember { mutableStateOf(false) }
    var showAdvancedRoles by remember { mutableStateOf(false) }
    var advancedTabDark by remember { mutableStateOf(false) }

    fun resetCreateState() {
        themeTitle = "My Theme"
        val firstAccent = CustomThemeBuilder.swatches[0].hex
        selectedAccentHex = firstAccent
        customAccentHexInput = firstAccent
        selectedLightTone = CustomThemeBuilder.lightTones[0]
        selectedDarkTone = CustomThemeBuilder.darkTones[0]
        lightCustomRoles = CustomThemeBuilder.defaultPalette(firstAccent, selectedLightTone, false)
        darkCustomRoles = CustomThemeBuilder.defaultPalette(firstAccent, selectedDarkTone, true)
        previewInDark = false
        showAdvancedRoles = false
        advancedTabDark = false
    }

    fun updateSimpleColors(newAccent: String, lightTone: BackgroundTone = selectedLightTone, darkTone: BackgroundTone = selectedDarkTone) {
        val normAccent = CustomThemeBuilder.normalizeHex(newAccent)
        selectedAccentHex = normAccent
        customAccentHexInput = normAccent
        selectedLightTone = lightTone
        selectedDarkTone = darkTone
        lightCustomRoles = CustomThemeBuilder.defaultPalette(normAccent, lightTone, false)
        darkCustomRoles = CustomThemeBuilder.defaultPalette(normAccent, darkTone, true)
    }

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
                    resetCreateState()
                    drawer = "create"
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
                Text("Design a light and dark theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            // Keep this long editor as a partial, scrollable sheet so the shared
            // handle and bottom attachment remain visible at larger font scales.
            expandToFitContent = false,
            dismissRequested = closing,
        ) {
                Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                    when (drawer) {
                        "create" -> {
                            Text("Create a theme", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Choose a name and a starting palette. You can fine-tune individual roles below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = themeTitle,
                                onValueChange = { if (it.length <= 40) themeTitle = it },
                                label = { Text("Theme name") },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Text("Preview", style = MaterialTheme.typography.titleSmall)
                            val activePreviewPalette = if (previewInDark) darkCustomRoles else lightCustomRoles
                            val previewBg = themeColor(activePreviewPalette["background"] ?: if (previewInDark) "#111713" else "#FFFFFF")
                            val previewSurface = themeColor(activePreviewPalette["surface"] ?: if (previewInDark) "#18201B" else "#F7F9F6")
                            val previewInk = themeColor(activePreviewPalette["on-surface"] ?: if (previewInDark) "#E9F5EC" else "#172019")
                            val previewPrimary = themeColor(activePreviewPalette["primary"] ?: "#27683C")
                            val previewOnPrimary = themeColor(activePreviewPalette["on-primary"] ?: "#FFFFFF")

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilterChip(
                                    selected = !previewInDark,
                                    enabled = !busy,
                                    onClick = { previewInDark = false },
                                    label = { Text("Light") },
                                )
                                FilterChip(
                                    selected = previewInDark,
                                    enabled = !busy,
                                    onClick = { previewInDark = true },
                                    label = { Text("Dark") },
                                )
                            }

                            Box(
                                Modifier.fillMaxWidth().height(112.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(previewBg)
                                    .padding(StillSpacing.medium),
                            ) {
                                Image(
                                    painterResource(R.drawable.ic_still_wordmark),
                                    null,
                                    Modifier.align(Alignment.CenterStart).padding(start = StillSpacing.small).width(90.dp).height(44.dp),
                                    colorFilter = ColorFilter.tint(previewPrimary),
                                )
                                Canvas(Modifier.align(Alignment.CenterEnd).padding(end = StillSpacing.small).size(76.dp)) {
                                    drawRoundRect(previewSurface, cornerRadius = CornerRadius(12.dp.toPx()))
                                    listOf(.66f, .9f, .54f, .78f).forEachIndexed { index, fraction ->
                                        val h = 4.dp.toPx()
                                        drawRoundRect(
                                            previewInk.copy(alpha = .72f),
                                            Offset(size.width * .16f, size.height * (.20f + index * .17f)),
                                            Size(size.width * .68f * fraction, h),
                                            CornerRadius(h / 2),
                                        )
                                    }
                                }
                                Box(
                                    Modifier.align(Alignment.BottomStart).padding(start = StillSpacing.small, bottom = 2.dp)
                                        .background(previewPrimary, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text("Accent", color = previewOnPrimary, style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Text("Accent color", style = MaterialTheme.typography.titleSmall)
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                            ) {
                                CustomThemeBuilder.swatches.forEach { swatch ->
                                    val swatchColor = themeColor(swatch.hex)
                                    val isSelected = selectedAccentHex.equals(swatch.hex, ignoreCase = true)
                                    Box(
                                        Modifier.size(40.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                            .selectable(
                                                selected = isSelected,
                                                enabled = !busy,
                                                role = Role.RadioButton,
                                            ) {
                                                updateSimpleColors(swatch.hex)
                                            }
                                            .semantics { contentDescription = "${swatch.name} accent" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                painterResource(StillIcons.Check),
                                                null,
                                                tint = themeColor(CustomThemeBuilder.contrastingInk(swatch.hex)),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(themeColor(CustomThemeBuilder.normalizeHex(customAccentHexInput)))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                )
                                OutlinedTextField(
                                    value = customAccentHexInput,
                                    onValueChange = { input ->
                                        if (input.length <= 7) {
                                            customAccentHexInput = input
                                            if (CustomThemeBuilder.isValidHex(input)) {
                                                updateSimpleColors(input)
                                            }
                                        }
                                    },
                                    label = { Text("Custom accent (#RRGGBB)") },
                                    isError = customAccentHexInput.isNotBlank() && !CustomThemeBuilder.isValidHex(customAccentHexInput),
                                    singleLine = true,
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Column(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(StillSpacing.medium),
                                verticalArrangement = Arrangement.spacedBy(StillSpacing.small),
                            ) {
                                Text("Background tone", style = MaterialTheme.typography.titleSmall)
                                Text("Light", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                                ) {
                                    CustomThemeBuilder.lightTones.forEach { tone ->
                                        FilterChip(
                                            selected = selectedLightTone.id == tone.id,
                                            enabled = !busy,
                                            onClick = { updateSimpleColors(selectedAccentHex, lightTone = tone) },
                                            label = { Text(tone.name) },
                                        )
                                    }
                                }
                                Text("Dark", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                                ) {
                                    CustomThemeBuilder.darkTones.forEach { tone ->
                                        FilterChip(
                                            selected = selectedDarkTone.id == tone.id,
                                            enabled = !busy,
                                            onClick = { updateSimpleColors(selectedAccentHex, darkTone = tone) },
                                            label = { Text(tone.name) },
                                        )
                                    }
                                }
                            }

                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable(enabled = !busy, role = Role.Button) { showAdvancedRoles = !showAdvancedRoles }
                                    .padding(StillSpacing.medium),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Fine-tune color roles", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Optional: edit all six roles for either mode.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    painterResource(if (showAdvancedRoles) StillIcons.ChevronUp else StillIcons.ChevronDown),
                                    null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            if (showAdvancedRoles) {
                                Row(horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                                    FilterChip(
                                        selected = !advancedTabDark,
                                        enabled = !busy,
                                        onClick = { advancedTabDark = false },
                                        label = { Text("Light roles") },
                                    )
                                    FilterChip(
                                        selected = advancedTabDark,
                                        enabled = !busy,
                                        onClick = { advancedTabDark = true },
                                        label = { Text("Dark roles") },
                                    )
                                }

                                val currentRoles = if (advancedTabDark) darkCustomRoles else lightCustomRoles
                                CustomThemeBuilder.roles.forEach { role ->
                                    val currentVal = currentRoles[role] ?: "#000000"
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier.size(32.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(themeColor(CustomThemeBuilder.normalizeHex(currentVal)))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                                        )
                                        Text(role, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        OutlinedTextField(
                                            value = currentVal,
                                            onValueChange = { newVal ->
                                                if (newVal.length <= 7) {
                                                    val updated = currentRoles + (role to newVal)
                                                    if (advancedTabDark) darkCustomRoles = updated else lightCustomRoles = updated
                                                }
                                            },
                                            singleLine = true,
                                            isError = currentVal.isNotBlank() && !CustomThemeBuilder.isValidHex(currentVal),
                                            enabled = !busy,
                                            modifier = Modifier.width(132.dp),
                                        )
                                    }
                                }
                            }

                            val rolesValid = (lightCustomRoles.values + darkCustomRoles.values)
                                .all { CustomThemeBuilder.isValidHex(it) }
                            Button(
                                enabled = !busy && themeTitle.isNotBlank() && CustomThemeBuilder.isValidHex(customAccentHexInput) && rolesValid,
                                onClick = {
                                    perform {
                                        val source = CustomThemeBuilder.buildSource(
                                            title = themeTitle,
                                            author = "You",
                                            lightColors = lightCustomRoles,
                                            darkColors = darkCustomRoles,
                                        )
                                        val pkg = ThemePackages.load(source.toByteArray(Charsets.UTF_8))
                                        repository.install(pkg, emptySet(), null, false)
                                        onCustomThemeSelected()
                                        repository.select(pkg.theme.id)
                                        drawer = null
                                        notice = "Theme created."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Save and use theme")
                            }
                        }

                        "manage" -> {
                            val item = state.themes.firstOrNull { it.content.theme.id == managing }
                            if (item != null) {
                                Text(item.content.theme.title, style = MaterialTheme.typography.titleLarge)
                                Text("${item.content.theme.author} · ${item.content.theme.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.content.theme.description.isNotBlank()) Text(item.content.theme.description, style = MaterialTheme.typography.bodyMedium)

                                Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                                    Text("Appearance", style = MaterialTheme.typography.titleSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                                        ThemeAppearance.entries.forEach { appearance ->
                                            FilterChip(
                                                selected = item.appearance == appearance,
                                                enabled = !busy,
                                                onClick = { perform { repository.appearance(item.content.theme.id, appearance) } },
                                                label = { Text(appearance.label()) },
                                            )
                                        }
                                    }
                                }

                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    SettingRow(
                                        "Export theme",
                                        supporting = "Save as a .tc file to share or back up",
                                        enabled = !busy,
                                        onClick = {
                                            val slug = item.content.theme.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "theme" }
                                            val suggestedName = "$slug.tc"
                                            exportLauncher.launch(suggestedName)
                                        },
                                    )
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
                                    Text("Remove theme")
                                }
                            }
                        }
                    }

                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (drawer == "manage" && !busy) {
                        Button(onClick = ::dismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    } else if (drawer == "create" && !busy) {
                        TextButton(onClick = ::dismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
}

private fun ThemeAppearance.label(): String = when (this) {
    ThemeAppearance.System -> "System"
    ThemeAppearance.Light -> "Light"
    ThemeAppearance.Dark -> "Dark"
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
                Text(theme.version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
