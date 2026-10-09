package app.still.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.still.CustomLauncherIcon
import app.still.R
import app.still.data.themes.CustomThemeBuilder
import app.still.ui.components.StillIcons
import app.still.ui.theme.StillSpacing
import app.still.ui.theme.themeColor

/** Direct edits keep the other saved color roles intact; no palette-generation steps. */
@Composable
internal fun ThemeEditorBody(
    title: String,
    onTitleChange: (String) -> Unit,
    palette: Map<String, String>,
    validPalette: Map<String, String>,
    previewColors: ColorScheme,
    useCustomIcon: Boolean,
    onCustomIconChange: (Boolean) -> Unit,
    enabled: Boolean,
    onEditColor: (String) -> Unit,
    linked: Boolean,
) {
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val iconChoice = remember(validPalette) {
        CustomLauncherIcon.closest(validPalette.getValue("primary"), validPalette.getValue("background"))
    }
    val icon = remember(context, iconChoice) { CustomLauncherIcon.bitmap(context, iconChoice) }

    OutlinedTextField(
        value = title, onValueChange = onTitleChange, enabled = enabled,
        label = { Text("Theme name") }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Live preview", style = MaterialTheme.typography.titleMedium)
    Surface(color = previewColors.background, contentColor = previewColors.onBackground,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(StillSpacing.large),
            verticalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium)) {
                Image(painterResource(R.drawable.ic_still_wordmark), "Draft accent on background",
                    Modifier.width(72.dp).height(36.dp), colorFilter = ColorFilter.tint(previewColors.primary))
                Text("Your theme", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                Surface(color = previewColors.primary, contentColor = previewColors.onPrimary,
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                    Text("Accent", Modifier.padding(StillSpacing.medium), style = MaterialTheme.typography.labelLarge)
                }
                Surface(color = previewColors.surface, contentColor = previewColors.onSurface,
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                    Text("Surface", Modifier.padding(StillSpacing.medium), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    // The three paired groups put each background beside the text drawn on it.
    listOf("Accent", "Background", "Surface").forEachIndexed { group, heading ->
        Spacer(Modifier.height(StillSpacing.small))
        Text(heading, style = MaterialTheme.typography.titleMedium)
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                (group * 2..group * 2 + 1).forEach { index ->
                    val role = CustomThemeBuilder.roles[index]
                    val value = palette.getValue(role)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clickable(enabled = enabled, role = Role.Button) { onEditColor(role) }
                            .padding(StillSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape)
                            .background(themeColor(validPalette.getValue(role)))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(RoleLabels[index], style = MaterialTheme.typography.titleMedium)
                            Text(value.uppercase(), style = MaterialTheme.typography.bodySmall,
                                color = if (CustomThemeBuilder.isValidHex(value)) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.error)
                        }
                        Icon(painterResource(StillIcons.Edit), "Edit ${RoleLabels[index].lowercase()}",
                            Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (index % 2 == 0) HorizontalDivider(Modifier.padding(horizontal = StillSpacing.medium),
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    Spacer(Modifier.height(StillSpacing.small))
    Text("App icon", style = MaterialTheme.typography.titleMedium)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = useCustomIcon, enabled = enabled, role = Role.Switch, onValueChange = onCustomIconChange)
            .padding(vertical = StillSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
    ) {
        Image(icon.asImageBitmap(), null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f)) {
            Text("Use custom app icon", style = MaterialTheme.typography.titleMedium)
            Text("Follows this theme when active", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = useCustomIcon, onCheckedChange = null, enabled = enabled)
    }
    if (!iconChoice.accent.equals(validPalette.getValue("primary").take(7), true) ||
            !iconChoice.background.equals(validPalette.getValue("background").take(7), true)) {
        Text("The icon uses the closest available colors. Your theme colors stay exact.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (linked) Text("Saving makes this a local theme and stops linked updates.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
