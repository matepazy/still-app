package app.still.ui.settings

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.still.BuildConfig
import app.still.R
import app.still.ui.components.StillIcons
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Each entry point owns its tap sequence; scrolling cancels clicks normally. */
@Composable
internal fun Modifier.versionArtworkTrigger(onOpen: () -> Unit): Modifier {
    val taps = remember { VersionArtworkTaps() }
    return clickable(
        interactionSource = null,
        indication = null,
        onClick = { if (taps.tap(SystemClock.uptimeMillis())) onOpen() },
    ).semantics {
        customActions = listOf(CustomAccessibilityAction("Open version artwork") {
            onOpen()
            true
        })
    }
}

internal class VersionArtworkTaps {
    private var lastTap = Long.MIN_VALUE
    private var count = 0

    fun tap(now: Long): Boolean {
        count = if (lastTap != Long.MIN_VALUE && now - lastTap in 0..400) count + 1 else 1
        lastTap = now
        if (count != 3) return false
        count = 0
        lastTap = Long.MIN_VALUE
        return true
    }
}

internal fun versionArtworkLabel(version: String): String {
    val normalized = version.removePrefix("v")
    val beta = Regex("^(.+)-beta(\\d+)$").matchEntire(normalized)
    return when {
        beta != null -> "Still v${beta.groupValues[1]} Beta ${beta.groupValues[2]}"
        '-' !in normalized -> "Still v.$normalized Release"
        else -> "Still v$normalized"
    }
}

/** A local, deterministic color composition; no release lookup or downloaded assets. */
@Composable
fun VersionArtworkScreen(
    onClose: () -> Unit,
    version: String = BuildConfig.VERSION_NAME,
    releaseName: String? = null,
    modifier: Modifier = Modifier,
) {
    val seed = remember(version) {
        version.fold(2166136261L) { hash, char ->
            ((hash xor char.code.toLong()) * 16777619L) and 0xffffffffL
        }
    }
    val hue = (seed % 360).toFloat()
    val colors = remember(seed) {
        listOf(
            Color.hsv(hue, .72f, .64f),
            Color.hsv((hue + 65f) % 360f, .62f, .78f),
            Color.hsv((hue + 305f) % 360f, .76f, .57f),
        )
    }
    val animation = rememberInfiniteTransition(label = "Version gradient")
    // A full orbit has no seam. Compose respects the system animator duration scale.
    val progress by animation.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "Gradient orbit",
    )
    Box(modifier.fillMaxSize().background(Color.hsv(hue, .62f, .16f))) {
        Canvas(Modifier.fillMaxSize()) {
            colors.forEachIndexed { index, color ->
                val phase = progress + index * (2 * PI / 3).toFloat() + (seed % 100) / 100f
                val center = Offset(
                    size.width * (.5f + .48f * cos(phase)),
                    size.height * (.5f + .40f * sin(phase)),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to color,
                            .38f to color.copy(alpha = .72f),
                            1f to color.copy(alpha = 0f),
                        ),
                        center = center,
                        radius = size.maxDimension * (.85f + .08f * sin(phase)),
                    ),
                )
            }
            // Keep the white mark and version legible throughout the orbit.
            drawRect(Color.Black.copy(alpha = .42f))
        }
        Column(
            modifier = Modifier.align(Alignment.Center).safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_still_mark),
                contentDescription = "Still logo",
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(176.dp),
            )
            Text(
                text = releaseName?.takeIf { it.isNotBlank() } ?: versionArtworkLabel(version),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd)
                .safeDrawingPadding().padding(16.dp)
                .background(Color.Black.copy(alpha = .20f), CircleShape),
        ) {
            Icon(painterResource(StillIcons.Close), contentDescription = "Close", tint = Color.White)
        }
    }
}
