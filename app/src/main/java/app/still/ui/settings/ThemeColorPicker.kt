package app.still.ui.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.still.data.themes.CustomThemeBuilder
import app.still.ui.theme.StillSpacing
import java.util.Locale
import kotlin.math.roundToInt

/** Keeps incomplete HEX edits intact while the visual controls retain the last valid color. */
@Composable
internal fun ThemeColorPicker(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val initial = remember {
        FloatArray(3).also {
            AndroidColor.colorToHSV(AndroidColor.parseColor(CustomThemeBuilder.normalizeHex(value).take(7)), it)
        }
    }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var brightness by remember { mutableFloatStateOf(initial[2]) }
    var emittedHex by remember { mutableStateOf<String?>(null) }
    var alphaSuffix by remember { mutableStateOf(CustomThemeBuilder.normalizeHex(value).drop(7)) }
    // Ignore our own rounded RGB output: preserve the hue when choosing gray or black.
    LaunchedEffect(value) {
        if (CustomThemeBuilder.isValidHex(value) && !value.trim().equals(emittedHex, true)) {
            val hsv = FloatArray(3).also { AndroidColor.colorToHSV(AndroidColor.parseColor(value.trim().take(7)), it) }
            alphaSuffix = value.trim().drop(7).uppercase(Locale.ROOT)
            if (hsv[1] > 0f) hue = hsv[0]
            saturation = hsv[1]
            brightness = hsv[2]
        }
    }
    fun select(h: Float = hue, s: Float = saturation, b: Float = brightness) {
        hue = h
        saturation = s
        brightness = b
        val hex = String.format(Locale.ROOT, "#%06X", AndroidColor.HSVToColor(floatArrayOf(h, s, b)) and 0xFFFFFF) + alphaSuffix
        emittedHex = hex
        onChange(hex)
    }
    val updatePlane by rememberUpdatedState<(Float, Float) -> Unit>({ s, b -> select(s = s, b = b) })
    Column(verticalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
        Text("Saturation & brightness", style = MaterialTheme.typography.labelLarge)
        Canvas(Modifier.fillMaxWidth().height(152.dp).clip(RoundedCornerShape(12.dp))
            .pointerInput(enabled) {
                if (enabled) awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun update(position: Offset) {
                        if (size.width > 0 && size.height > 0) updatePlane(
                            (position.x / size.width).coerceIn(0f, 1f),
                            (1f - position.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                    update(down.position)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull { it.id == down.id }?.let {
                            update(it.position)
                            it.consume()
                        }
                    } while (event.changes.any { it.id == down.id && it.pressed })
                }
            }
            .semantics {
                contentDescription = "$label saturation and brightness"
                stateDescription = "Saturation ${(saturation * 100).roundToInt()}%, brightness ${(brightness * 100).roundToInt()}%"
                if (enabled) customActions = listOf(
                    CustomAccessibilityAction("Increase saturation") { select(s = (saturation + .05f).coerceAtMost(1f)); true },
                    CustomAccessibilityAction("Decrease saturation") { select(s = (saturation - .05f).coerceAtLeast(0f)); true },
                    CustomAccessibilityAction("Increase brightness") { select(b = (brightness + .05f).coerceAtMost(1f)); true },
                    CustomAccessibilityAction("Decrease brightness") { select(b = (brightness - .05f).coerceAtLeast(0f)); true },
                )
            }) {
            drawRect(Color.hsv(hue, 1f, 1f))
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val radius = 9.dp.toPx()
            val marker = Offset(
                (saturation * size.width).coerceIn(radius, size.width - radius),
                ((1f - brightness) * size.height).coerceIn(radius, size.height - radius),
            )
            drawCircle(Color.Black, radius, marker, style = Stroke(4.dp.toPx()))
            drawCircle(Color.White, radius, marker, style = Stroke(2.dp.toPx()))
        }
        Text("Hue", style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(12.dp).align(androidx.compose.ui.Alignment.Center)
                .clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))))
            Slider(value = hue, onValueChange = { select(h = it) }, valueRange = 0f..360f, enabled = enabled,
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "$label hue" })
        }
        ThemeHexField("HEX", value, enabled, onChange)
    }
}
