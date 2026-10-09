package app.still.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import app.still.CustomLauncherIcon
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.still.R
import app.still.StillApplication
import app.still.data.themes.CustomThemeBuilder
import app.still.data.themes.ThemePackages
import app.still.data.themes.InstalledTheme
import app.still.data.themes.ThemeCompose
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.ui.components.StillIcons
import app.still.ui.components.StillDrawer
import app.still.ui.theme.StillSpacing
import app.still.ui.theme.customThemeColors
import app.still.ui.theme.themeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Colors and manual details, optional launcher icon, then name and apply. */
private val PaletteSaver = mapSaver<Map<String, String>>(
    save = { it }, restore = { saved -> saved.mapValues { it.value as String } },
)

private val CreatorSteps = listOf("Colors", "App icon", "Name & save")
private val RoleLabels = listOf("Accent", "Text on accent", "Background", "Text on background", "Surface", "Text on surface")
private val RoleDescriptions = listOf("Buttons and highlights", "Text on colored elements", "App background", "Main text", "Cards and panels", "Text on cards and panels")

/** A saved draft across focused steps; only the final action installs the palette. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeCreatorScreen(onClose: () -> Unit, modifier: Modifier = Modifier, themeId: String? = null) {
    val context = LocalContext.current
    val repository = (context.applicationContext as StillApplication).container.communityThemes
    val state by repository.state.collectAsStateWithLifecycle()
    if (themeId != null && !state.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val editing = state.themes.firstOrNull { it.content.theme.id == themeId }
    if (themeId != null && editing == null) {
        Scaffold(topBar = { SettingsTopBar(title = "Edit theme", onBack = onClose) }) { padding ->
            Text("This theme is no longer installed.", Modifier.padding(padding).padding(StillSpacing.large))
        }
        return
    }
    key(themeId) { ThemeCreatorContent(onClose, modifier, editing) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeCreatorContent(onClose: () -> Unit, modifier: Modifier, original: InstalledTheme?) {
    val context = LocalContext.current
    val repository = (context.applicationContext as StillApplication).container.communityThemes
    val editing = remember { original }
    val originalDigest = rememberSaveable { editing?.let { ThemeCompose.digest(it.content.bytes) }.orEmpty() }
    val initialPalette = remember {
        editing?.let { item ->
            val style = app.still.ui.theme.communityStyle(context, item)
            val source = if (item.appearance == app.still.data.themes.ThemeAppearance.Light) item.content.theme.light else item.content.theme.dark
            source.mapValues { it.value.resolve(style.values) }
        } ?: CustomThemeBuilder.palette(CustomThemeBuilder.swatches.first().hex, CustomThemeBuilder.backgroundTones.first().backgroundHex)
    }
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(editing?.content?.theme?.title.orEmpty()) }
    var accent by rememberSaveable { mutableStateOf(if (editing == null) CustomThemeBuilder.swatches.first().hex else initialPalette.getValue("primary")) }
    var background by rememberSaveable { mutableStateOf(initialPalette.getValue("background")) }
    var customBackground by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var useCustomIcon by rememberSaveable { mutableStateOf(editing?.customIcon == true) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var editingRole by rememberSaveable { mutableStateOf<String?>(null) }
    var customAccent by rememberSaveable { mutableStateOf(false) }
    var palette by rememberSaveable(stateSaver = PaletteSaver) {
        mutableStateOf(initialPalette)
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val rolesValid = CustomThemeBuilder.roles.all { CustomThemeBuilder.isValidHex(palette[it].orEmpty()) }
    // Invalid edits keep the last valid preview visible, and can never reach the parser or save.
    var validPalette by rememberSaveable(stateSaver = PaletteSaver) { mutableStateOf(palette) }
    if (rolesValid) SideEffect { validPalette = palette }
    val previewColors = remember(validPalette) { customThemeColors(validPalette) }
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    fun previous() {
        if (!busy) {
            focus.clearFocus()
            if (step > 0) step-- else onClose()
        }
    }
    fun choose(accentHex: String = validPalette.getValue("primary"), backgroundHex: String = validPalette.getValue("background")) {
        accent = accentHex
        background = backgroundHex
        palette = CustomThemeBuilder.palette(accentHex, backgroundHex)
        error = null
    }
    BackHandler(enabled = busy || step > 0) { previous() }
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = { SettingsTopBar(title = if (editing == null) "Create a theme" else "Edit theme", onBack = ::previous) },
        bottomBar = {
            Surface {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(StillSpacing.large)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = StillSpacing.small)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                    if (step > 0) OutlinedButton(onClick = ::previous, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(StillIcons.Back), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(StillSpacing.small))
                        Text("Back")
                    }
                    Button(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        enabled = !busy && rolesValid && CustomThemeBuilder.isValidHex(accent) && CustomThemeBuilder.isValidHex(background) && (step < CreatorSteps.lastIndex || title.isNotBlank()),
                        onClick = {
                            focus.clearFocus()
                            if (step < CreatorSteps.lastIndex) { step++; return@Button }
                            busy = true; error = null
                            scope.launch {
                                try {
                                    val content = if (editing == null) {
                                        ThemePackages.load(CustomThemeBuilder.buildFixedSource(title, palette).toByteArray(Charsets.UTF_8))
                                    } else CustomThemeBuilder.editPackage(editing.content, title, palette)
                                    if (editing == null) {
                                        repository.install(content, emptySet(), null, false, customIcon = useCustomIcon)
                                    } else {
                                        repository.edit(editing.content.theme.id, originalDigest, content, customIcon = useCustomIcon)
                                    }
                                    onClose()
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message?.take(240) ?: "Could not save this theme. Try again." }
                                finally { busy = false }
                            }
                        },
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else {
                            Text(if (step == CreatorSteps.lastIndex) "Save and use theme" else "Next")
                            if (step < CreatorSteps.lastIndex) {
                                Spacer(Modifier.width(StillSpacing.small))
                                Icon(painterResource(StillIcons.ChevronRight), null, Modifier.size(18.dp))
                            }
                        }
                    }
                    }
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxHeight().fillMaxWidth().verticalScroll(scroll).padding(StillSpacing.large),
            verticalArrangement = Arrangement.spacedBy(StillSpacing.medium),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(CreatorSteps[step], style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${step + 1} of ${CreatorSteps.size}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().semantics { contentDescription = "Step ${step + 1} of ${CreatorSteps.size}: ${CreatorSteps[step]}" },
                horizontalArrangement = Arrangement.spacedBy(StillSpacing.xSmall)) {
                CreatorSteps.forEachIndexed { index, _ ->
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape)
                        .background(if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest))
                }
            }
            Spacer(Modifier.height(StillSpacing.small))
            if (step == 0) {
            Text("Choose your accent", style = MaterialTheme.typography.headlineSmall)
            Text("Choose an accent and background. The other colors are calculated for you.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomThemeBuilder.swatches.forEach { swatch ->
                    val selected = palette["primary"].equals(swatch.hex, true)
                    Box(Modifier.size(48.dp).clip(CircleShape)
                        .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .selectable(selected, enabled = !busy, role = Role.RadioButton) { choose(swatch.hex); customAccent = false }
                        .semantics { contentDescription = swatch.name }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(36.dp).background(themeColor(swatch.hex), CircleShape), contentAlignment = Alignment.Center) {
                            if (selected) Icon(painterResource(StillIcons.Check), null,
                                tint = themeColor(CustomThemeBuilder.contrastingInk(swatch.hex, darkInk = "#000000")), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            Text(CustomThemeBuilder.swatches.firstOrNull { accent.equals(it.hex, true) }?.name ?: "Custom accent",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { customAccent = true }, enabled = !busy) { Text("Custom accent color") }
            }
            if (step == 0) {
            Text("Background", style = MaterialTheme.typography.titleLarge)
            Text("Choose a preset or pick any color.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                CustomThemeBuilder.backgroundTones.forEach { tone ->
                    val selected = palette["background"].equals(tone.backgroundHex, true)
                    Column(Modifier.widthIn(min = 76.dp).clip(RoundedCornerShape(12.dp))
                        .border(if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .selectable(selected, enabled = !busy, role = Role.RadioButton) { choose(backgroundHex = tone.backgroundHex) }
                        .padding(StillSpacing.medium), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(themeColor(tone.backgroundHex))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                            if (selected) Icon(painterResource(StillIcons.Check), null,
                                tint = themeColor(CustomThemeBuilder.contrastingInk(tone.backgroundHex)), modifier = Modifier.size(18.dp))
                        }
                        Text(tone.name, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            }
            if (step == 0) {
                TextButton(onClick = { customBackground = true }, enabled = !busy) { Text("Custom background color") }
                TextButton(onClick = { showDetails = !showDetails }, enabled = !busy) {
                    Text(if (showDetails) "Hide color values" else "Edit all color values")
                }
            }
            if (step == 0 && showDetails) {
                Text("Color values", style = MaterialTheme.typography.titleLarge)
                Text("Edit any of the six colors. Choosing an accent or background above recalculates these values.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        CustomThemeBuilder.roles.forEachIndexed { index, role ->
                            val value = palette.getValue(role)
                            Row(Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.Button) { editingRole = role }
                                .padding(StillSpacing.medium), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                                Box(Modifier.size(32.dp).clip(CircleShape)
                                    .background(themeColor(validPalette.getValue(role)))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                Column(Modifier.weight(1f)) {
                                    Text(RoleLabels[index], style = MaterialTheme.typography.titleMedium)
                                    Text(RoleDescriptions[index], style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value.uppercase(), style = MaterialTheme.typography.bodySmall,
                                        color = if (CustomThemeBuilder.isValidHex(value)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                                }
                                Icon(painterResource(StillIcons.ChevronRight), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (index < CustomThemeBuilder.roles.lastIndex) HorizontalDivider(Modifier.padding(horizontal = StillSpacing.medium), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                TextButton(enabled = !busy, onClick = { choose() }) {
                    Icon(painterResource(StillIcons.Refresh), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(StillSpacing.small))
                    Text("Reset color details")
                }
            }
            if (step == 1) {
                Text("Your app icon", style = MaterialTheme.typography.headlineSmall)
                val iconChoice = remember(validPalette) { CustomLauncherIcon.closest(validPalette.getValue("primary"), validPalette.getValue("background")) }
                val icon = remember(iconChoice) { CustomLauncherIcon.bitmap(context, iconChoice) }
                Column(Modifier.fillMaxWidth().padding(vertical = StillSpacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                    Image(icon.asImageBitmap(), "Still icon with your theme colors",
                        Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
                    Text("Still", style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = useCustomIcon, enabled = !busy, role = Role.Checkbox) { useCustomIcon = it }
                    .padding(vertical = StillSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use custom app icon", style = MaterialTheme.typography.titleMedium)
                        Text("Changes Still’s launcher icon when this theme is active.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(checked = useCustomIcon, onCheckedChange = null, enabled = !busy)
                }
                if (!iconChoice.accent.equals(validPalette.getValue("primary").take(7), true) ||
                    !iconChoice.background.equals(validPalette.getValue("background").take(7), true)) {
                    Text("The preview shows the closest available icon colors. Your theme colors stay exact.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Your launcher controls the icon shape and may take a moment to update.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (step == 2) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, enabled = !busy,
                    label = { Text("Theme name") }, placeholder = { Text("My theme") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (editing?.link != null) Text("Saving makes this a local theme and stops linked updates.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (step != 1) {
                Text(if (step == 2) "Your theme" else "Live preview", style = MaterialTheme.typography.titleMedium)
            MaterialTheme(colorScheme = previewColors) {
                Surface(shape = RoundedCornerShape(16.dp), color = previewColors.background,
                    border = androidx.compose.foundation.BorderStroke(1.dp, previewColors.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(StillSpacing.large), verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                        Image(painterResource(R.drawable.ic_still_wordmark), "Still theme preview",
                            Modifier.width(86.dp).height(42.dp), colorFilter = ColorFilter.tint(previewColors.primary))
                        Text("Today", style = MaterialTheme.typography.titleMedium, color = previewColors.onBackground)
                        Surface(color = previewColors.surface, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(StillSpacing.medium), verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                                Text("Screen time", style = MaterialTheme.typography.bodyMedium)
                                Text("2h 24m", style = MaterialTheme.typography.headlineLarge)
                                LinearProgressIndicator(progress = { .6f }, modifier = Modifier.fillMaxWidth(),
                                    color = previewColors.primary, trackColor = previewColors.surfaceContainerHighest)
                            }
                        }
                        // Real Material controls exercise container, text and accent roles together.
                        Row(horizontalArrangement = Arrangement.spacedBy(StillSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(StillIcons.Check), null, tint = previewColors.primary, modifier = Modifier.size(20.dp))
                            Text("Live preview · example usage", style = MaterialTheme.typography.bodySmall, color = previewColors.onBackground)
                        }
                        Surface(color = previewColors.primary, contentColor = previewColors.onPrimary, shape = RoundedCornerShape(8.dp)) {
                            Text("Your accent", style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
            }
            }
            if (step == 0 && rolesValid) {
                val pairs = listOf("primary" to "on-primary", "background" to "on-background", "surface" to "on-surface")
                if (pairs.any { (a, b) -> CustomThemeBuilder.contrastRatio(palette.getValue(a), palette.getValue(b)) < 4.5f }) {
                    Text("Some text may be hard to read. Try a different accent or adjust its text color.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                val accentColor = previewColors.primary
                fun contrast(on: androidx.compose.ui.graphics.Color): Float {
                    val a = accentColor.luminance()
                    val b = on.luminance()
                    return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
                }
                if (listOf(previewColors.background, previewColors.surface, previewColors.surfaceContainerHighest).any { contrast(it) < 4.5f }) {
                    Text("The accent may be hard to see on this background. Try a brighter or darker color.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        }
    }
    if (customAccent) {
        StillDrawer(onDismissRequest = { customAccent = false }) {
            Text("Accent color", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(StillSpacing.medium))
            ThemeColorPicker("Accent color", accent, !busy) { input ->
                accent = input
                if (CustomThemeBuilder.isValidHex(input)) choose(input)
            }
            Spacer(Modifier.height(StillSpacing.medium))
            Button(onClick = { customAccent = false }, enabled = CustomThemeBuilder.isValidHex(accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
        }
    }
    if (customBackground) {
        StillDrawer(onDismissRequest = { customBackground = false }) {
            Text("Background color", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(StillSpacing.medium))
            ThemeColorPicker("Background color", background, !busy) { input ->
                background = input
                if (CustomThemeBuilder.isValidHex(input)) choose(backgroundHex = input)
            }
            Spacer(Modifier.height(StillSpacing.medium))
            Button(onClick = { customBackground = false }, enabled = CustomThemeBuilder.isValidHex(background),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
        }
    }
    editingRole?.let { role ->
        val index = CustomThemeBuilder.roles.indexOf(role)
        StillDrawer(onDismissRequest = { editingRole = null }) {
            Text(RoleLabels[index], style = MaterialTheme.typography.titleLarge)
            Text(RoleDescriptions[index], style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(StillSpacing.medium))
            ThemeColorPicker("Color", palette.getValue(role), !busy) { value ->
                palette = palette + (role to value)
                if (role == "primary") accent = value
                if (role == "background") background = value
            }
            Spacer(Modifier.height(StillSpacing.medium))
            Button(onClick = { editingRole = null }, enabled = CustomThemeBuilder.isValidHex(palette.getValue(role)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
        }
    }
}

@Composable
internal fun ThemeHexField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { if (it.length <= 9) onChange(it) },
        label = { Text(label) }, singleLine = true, enabled = enabled,
        isError = !CustomThemeBuilder.isValidHex(value), supportingText = {
            if (!CustomThemeBuilder.isValidHex(value)) Text("Enter a color as #RRGGBB")
        }, leadingIcon = {
            if (CustomThemeBuilder.isValidHex(value)) Box(Modifier.size(24.dp).clip(CircleShape).background(themeColor(value.trim()))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }, modifier = Modifier.fillMaxWidth())
}
