package app.still.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Lives above the destination scaffolds so a tab change cannot cancel its feedback. */
internal class NavigationIconMotion(private val scope: CoroutineScope) {
    private val progress = listOf(TodayRoute, TimelineRoute, AppsRoute, StatisticsRoute)
        .associateWith { Animatable(1f) }
    private var animation: Job? = null
    private var pressedRoute: String? = null
    var fillingRoute by mutableStateOf<String?>(null)
        private set
    var reduceMotion by mutableStateOf(false)
        private set

    fun setMotionReduced(value: Boolean) {
        if (reduceMotion == value) return
        reduceMotion = value
        animation?.cancel()
        animation = null
        pressedRoute = null
        fillingRoute = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            progress.values.forEach { it.snapTo(1f) }
        }
    }

    fun progress(route: String): Float = if (reduceMotion) 1f else progress.getValue(route).value

    fun play(route: String, alreadySelected: Boolean) {
        if (reduceMotion) return
        val startedOnPress = pressedRoute == route && animation?.isActive == true
        pressedRoute = null
        if (startedOnPress) return
        startAnimation(route, alreadySelected)
    }

    fun press(route: String, alreadySelected: Boolean) {
        if (reduceMotion) return
        pressedRoute = route
        startAnimation(route, alreadySelected)
    }

    fun cancelPress(route: String) {
        if (pressedRoute == route) pressedRoute = null
    }

    private fun startAnimation(route: String, alreadySelected: Boolean) {
        fillingRoute = if (alreadySelected) null else route
        animation?.cancel()
        animation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // A quick second tap replaces the previous sequence instead of queuing it.
            progress.values.forEach { it.snapTo(1f) }
            progress.getValue(route).apply {
                snapTo(0f)
                // Animatable honors Compose's system motion duration scale, including zero.
                animateTo(1f, tween(durationMillis = 560, easing = LinearEasing))
            }
        }
    }
}

@Composable
internal fun NavigationIcon(route: String, motion: NavigationIconMotion, selected: Boolean) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        // Match the existing 24-unit vector masters at rest; only the icon geometry moves.
        val progress = motion.progress(route)
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            when (route) {
                TodayRoute -> {
                    val home = Path().apply {
                        moveTo(4f, 10.5f)
                        lineTo(12f, 4f)
                        lineTo(20f, 10.5f)
                        lineTo(20f, 20f)
                        lineTo(14f, 20f)
                        lineTo(14f, 15f)
                        lineTo(10f, 15f)
                        lineTo(10f, 20f)
                        lineTo(4f, 20f)
                        close()
                    }
                    val fill = when {
                        !selected -> 0f
                        motion.fillingRoute == route -> fillAmount(progress)
                        else -> 1f
                    }
                    val sizePulse = 1f + 0.045f * pulse(progress)
                    scale(sizePulse, sizePulse, pivot = Offset(12f, 12f)) {
                        drawPath(home, color.copy(alpha = color.alpha * fill))
                        drawPath(home, color, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                StatisticsRoute -> {
                    val tops = floatArrayOf(12f, 7f, 10f, 4f)
                    val changes = floatArrayOf(-3f, 2.5f, -3.5f, 2f)
                    tops.forEachIndexed { index, top ->
                        val x = 4f + index * 5f
                        drawLine(
                            color,
                            Offset(x, 19f),
                            Offset(x, top + changes[index] * pulse(progress, index * 0.06f)),
                            strokeWidth = 1.8f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                TimelineRoute -> {
                    drawLine(color, Offset(6f, 4f), Offset(6f, 20f), 1.8f, StrokeCap.Round)
                    val scroll = 18f * FastOutSlowInEasing.transform(progress)
                    clipRect(left = 3f, top = 4f, right = 20f, bottom = 20f) {
                        // Three replacement rows enter below as the originals scroll out above.
                        repeat(6) { index ->
                            val y = 6f + index * 6f - scroll
                            val end = if (index % 3 == 1) 15f else 18f
                            drawLine(color, Offset(10f, y), Offset(end, y), 1.8f, StrokeCap.Round)
                            drawCircle(color, 1.5f, Offset(6f, y))
                        }
                    }
                }
                AppsRoute -> repeat(4) { index ->
                    val column = index % 2
                    val row = index / 2
                    val inset = 0.2f * pulse(progress, index * 0.045f)
                    val topLeft = Offset(4f + column * 10f + inset, 4f + row * 10f + inset)
                    val squareSize = Size(6f - inset * 2, 6f - inset * 2)
                    val fill = when {
                        !selected -> 0f
                        motion.fillingRoute == route -> fillAmount(progress, index * 0.045f)
                        else -> 1f
                    }
                    drawRect(
                        color.copy(alpha = color.alpha * fill),
                        topLeft = topLeft,
                        size = squareSize,
                    )
                    drawRect(
                        color,
                        topLeft = topLeft,
                        size = squareSize,
                        style = Stroke(width = 1.8f),
                    )
                }
            }
        }
    }
}

private fun fillAmount(progress: Float, delay: Float = 0f): Float {
    val phase = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform((phase / 0.45f).coerceIn(0f, 1f))
}

private fun pulse(progress: Float, delay: Float = 0f): Float {
    val phase = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
    // Explicit endpoints keep resting geometry exact, including when animations are disabled.
    return if (phase == 0f || phase == 1f) 0f else sin(phase * PI).toFloat()
}
