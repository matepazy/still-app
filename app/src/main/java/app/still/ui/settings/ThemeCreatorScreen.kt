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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.still.R
import app.still.StillApplication
import app.still.data.themes.ColorSwatch
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
        } ?: CustomThemeBuilder.creatorSwatches.first().hex.let { initialAccent ->
            val backgrounds = CustomThemeBuilder.matchingBackgrounds(initialAccent)
            CustomThemeBuilder.palette(initialAccent, backgrounds[CustomThemeBuilder.automaticBackgroundIndex(initialAccent)].hex)
        }
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
    var generatedAccent by rememberSaveable { mutableStateOf<String?>(null) }
    var generatedBackground by rememberSaveable { mutableStateOf<String?>(null) }
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
    fun chooseAccent(hex: String) {
        val choices = CustomThemeBuilder.matchingBackgrounds(hex)
        val index = CustomThemeBuilder.automaticBackgroundIndex(hex)
        choose(hex, choices[index].hex)
    }
    BackHandler(enabled = busy || step > 0) { previous() }
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = { SettingsTopBar(title = if (editing == null) "Create a theme" else "Edit theme", onBack = ::previous) },
        bottomBar = {
            Surface {
                Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(StillSpacing.large)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = StillSpacing.small)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                    if (step == 0) OutlinedButton(
                        onClick = {
                            val generated = CustomThemeBuilder.surprisePalette()
                            generatedAccent = generated.getValue("primary")
                            generatedBackground = generated.getValue("background")
                            choose(generated.getValue("primary"), generated.getValue("background"))
                        }, enabled = !busy, contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(painterResource(StillIcons.Dice), "Surprise me", Modifier.size(22.dp))
                    }
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
                            if (step == CreatorSteps.lastIndex) {
                                Icon(painterResource(StillIcons.Check), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(StillSpacing.small))
                            }
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
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxHeight().fillMaxWidth().verticalScroll(scroll).padding(StillSpacing.large),
            verticalArrangement = Arrangement.spacedBy(StillSpacing.medium),
        ) {
            Text("${step + 1} / ${CreatorSteps.size}", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
            Row(Modifier.fillMaxWidth().semantics { contentDescription = "Step ${step + 1} of ${CreatorSteps.size}: ${CreatorSteps[step]}" },
                horizontalArrangement = Arrangement.spacedBy(StillSpacing.xSmall)) {
                CreatorSteps.forEachIndexed { index, _ ->
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape)
                        .background(if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest))
                }
            }
            Spacer(Modifier.height(StillSpacing.small))
            Text(CreatorSteps[step], style = MaterialTheme.typography.headlineSmall)
            if (step == 0) {
                Text("Start with an accent. Backgrounds and text colors are calculated for you.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CreatorPaletteSample(previewColors)
                val accents = CustomThemeBuilder.creatorSwatches.filterNot { it.hex.equals(generatedAccent, true) } + listOfNotNull(generatedAccent?.let {
                    ColorSwatch("Generated · ${it.uppercase()}", it)
                })
                CreatorColorChoices("Accent color", accents, palette.getValue("primary"),
                    "Custom accent color", !busy,
                    onSelect = { chooseAccent(it) }, onCustom = { customAccent = true })
                val backgrounds = CustomThemeBuilder.matchingBackgrounds(validPalette.getValue("primary"))
                    .filterNot { it.hex.equals(generatedBackground, true) } +
                    listOfNotNull(generatedBackground?.let { ColorSwatch("Generated · ${it.uppercase()}", it) })
                CreatorColorChoices("Background color", backgrounds, palette.getValue("background"),
                    "Custom background color", !busy,
                    onSelect = { hex ->
                        choose(backgroundHex = hex)
                    }, onCustom = { customBackground = true })
                Spacer(Modifier.height(StillSpacing.small))
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(if (!showDetails) Modifier
                        .clickable(enabled = !busy, role = Role.Button) { showDetails = true }
                        .semantics { stateDescription = "Collapsed" } else Modifier) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .then(if (showDetails) Modifier
                                .clickable(enabled = !busy, role = Role.Button) { showDetails = false }
                                .semantics { stateDescription = "Expanded" } else Modifier)
                            .padding(StillSpacing.medium), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                            Icon(painterResource(StillIcons.Edit), null, Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Edit all color values", style = MaterialTheme.typography.titleMedium)
                                Text(if (showDetails) "Hide the six color editors" else "Fine-tune the generated palette",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(painterResource(if (showDetails) StillIcons.ChevronUp else StillIcons.ChevronDown), null, Modifier.size(20.dp))
                        }
                        if (showDetails) {
                            HorizontalDivider(Modifier.padding(horizontal = StillSpacing.medium), color = MaterialTheme.colorScheme.outlineVariant)
                            Text("Edit any of the six colors. Choosing an accent or background above recalculates these values.",
                                modifier = Modifier.padding(StillSpacing.medium),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            TextButton(enabled = !busy, onClick = { choose() }) {
                                Icon(painterResource(StillIcons.Refresh), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(StillSpacing.small))
                                Text("Reset color details")
                            }
                        }
                        if (!showDetails) FlowRow(Modifier.fillMaxWidth().padding(start = StillSpacing.medium,
                            end = StillSpacing.medium, bottom = StillSpacing.medium),
                            horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                            verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                            CustomThemeBuilder.roles.forEach { role ->
                                Box(Modifier.size(28.dp).background(themeColor(validPalette.getValue(role)), CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                            }
                        }
                    }
                }
            }
            if (step == 1) {
                Text("Choose whether Still’s launcher icon follows your theme.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val iconChoice = remember(validPalette) { CustomLauncherIcon.closest(validPalette.getValue("primary"), validPalette.getValue("background")) }
                val icon = remember(context, iconChoice) { CustomLauncherIcon.bitmap(context, iconChoice) }
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(vertical = StillSpacing.section),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                    Image(icon.asImageBitmap(), "Still icon with your theme colors",
                        Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
                    Text("Still", style = MaterialTheme.typography.labelLarge)
                }
                HorizontalDivider(Modifier.padding(horizontal = StillSpacing.large), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = useCustomIcon, enabled = !busy, role = Role.Switch) { useCustomIcon = it }
                    .padding(StillSpacing.large), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StillSpacing.xSmall)) {
                        Text("Use custom app icon", style = MaterialTheme.typography.titleMedium)
                        Text("Changes Still’s launcher icon when this theme is active.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = useCustomIcon, onCheckedChange = null, enabled = !busy)
                }
                }
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
                Text("Give your theme a name and check how its colors work together.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, enabled = !busy,
                    label = { Text("Theme name") }, placeholder = { Text("My theme") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth())
                if (editing?.link != null) Text("Saving makes this a local theme and stops linked updates.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (step == 2) {
                Text("Live preview", style = MaterialTheme.typography.titleMedium)
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
                        Surface(color = previewColors.surface, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(StillSpacing.medium),
                                verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                                Text("Apps", style = MaterialTheme.typography.titleMedium)
                                listOf("Reading" to .7f, "Music" to .45f, "Messages" to .25f).forEach { (label, amount) ->
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                                        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        LinearProgressIndicator(progress = { amount }, modifier = Modifier.weight(1f),
                                            color = previewColors.primary, trackColor = previewColors.surfaceContainerHighest)
                                    }
                                }
                            }
                        }
                        Surface(color = previewColors.primary, contentColor = previewColors.onPrimary, shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.padding(horizontal = StillSpacing.medium, vertical = StillSpacing.small),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                                Icon(painterResource(StillIcons.Check), null, Modifier.size(18.dp))
                                Text("Your accent", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Text("Example usage", style = MaterialTheme.typography.bodySmall, color = previewColors.onBackground)
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
                if (CustomThemeBuilder.isValidHex(input)) chooseAccent(input)
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
                if (CustomThemeBuilder.isValidHex(input)) {
                    choose(backgroundHex = input)
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreatorPaletteSample(colors: ColorScheme) {
    Surface(color = colors.background, contentColor = colors.onBackground, shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(StillSpacing.large), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
            Image(painterResource(R.drawable.ic_still_wordmark), "Draft accent on background",
                Modifier.width(72.dp).height(36.dp), colorFilter = ColorFilter.tint(colors.primary))
            Spacer(Modifier.weight(1f))
            Surface(color = colors.surface, contentColor = colors.onSurface, shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(StillSpacing.medium), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                    Box(Modifier.size(12.dp).background(colors.primary, CircleShape))
                    Text("Aa", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreatorColorChoices(
    label: String,
    swatches: List<ColorSwatch>,
    value: String,
    customLabel: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
            verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
            swatches.forEach { swatch ->
                val selected = value.equals(swatch.hex, true)
                Box(Modifier.size(48.dp).clip(CircleShape)
                    .border(if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .selectable(selected, enabled = enabled, role = Role.RadioButton) { onSelect(swatch.hex) }
                    .semantics { contentDescription = "$label: ${swatch.name}" }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp).background(themeColor(swatch.hex), CircleShape), contentAlignment = Alignment.Center) {
                        if (selected) Icon(painterResource(StillIcons.Check), null, Modifier.size(20.dp),
                            tint = themeColor(CustomThemeBuilder.contrastingInk(swatch.hex, darkInk = "#000000")))
                    }
                }
            }
            val borderColor = MaterialTheme.colorScheme.outline.let { if (enabled) it else it.copy(alpha = .38f) }
            Box(Modifier.size(48.dp).clip(CircleShape)
                .drawBehind {
                    val strokeWidth = 1.5.dp.toPx()
                    drawCircle(borderColor, radius = size.minDimension / 2 - strokeWidth / 2,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(.5.dp.toPx(), 4.dp.toPx()))))
                }
                .clickable(enabled = enabled, role = Role.Button, onClick = onCustom)
                .semantics { contentDescription = customLabel }, contentAlignment = Alignment.Center) {
                Icon(painterResource(StillIcons.Add), null, Modifier.size(22.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f))
            }
        }
        Text(swatches.firstOrNull { value.equals(it.hex, true) }?.name ?: "Custom · ${value.uppercase()}",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
