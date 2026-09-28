package app.still.ui.today

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import app.still.ui.theme.StillSpacing
import kotlin.math.sin

@Composable
internal fun FallTodayHeader(date: String, duration: String) {
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    val progress = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "falling leaves")
        val animatedProgress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
            label = "leaf drift",
        )
        animatedProgress
    } else .12f
    val leaf = Path().apply {
        moveTo(10f, 1f)
        cubicTo(18f, 5f, 21f, 11f, 17f, 17f)
        cubicTo(14f, 22f, 9f, 24f, 5f, 23f)
        cubicTo(3f, 16f, 3f, 10f, 10f, 1f)
        close()
    }
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF693A2A), Color(0xFF9A512A), Color(0xFFBA7137)),
                ),
            ),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val colors = listOf(Color(0xFFF7C877), Color(0xFFE8A15C), Color(0xFFFFD899))
            repeat(6) { index ->
                val phase = (progress + index * .173f) % 1f
                val x = size.width * (.12f + (index * .227f) % .80f) +
                    sin(phase * 6.28f + index) * 17.dp.toPx()
                val y = -30.dp.toPx() + phase * (size.height + 60.dp.toPx())
                val leafScale = (if (index % 3 == 0) 1f else .7f) * density
                withTransform({
                    translate(x, y)
                    rotate(phase * 125f + index * 39f, Offset(10f, 12f))
                    scale(leafScale, leafScale, Offset(10f, 12f))
                }) {
                    drawPath(leaf, colors[index % colors.size].copy(alpha = .38f))
                    drawLine(
                        Color(0xFFFFE1AA).copy(alpha = .32f),
                        Offset(10f, 4f), Offset(10f, 21f), strokeWidth = 1f,
                    )
                }
            }
        }
        Column(Modifier.padding(StillSpacing.large)) {
            Text(
                "Today · $date",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFFFE5C4),
            )
            Spacer(Modifier.height(StillSpacing.large))
            Text(duration, style = MaterialTheme.typography.displayLarge, color = Color.White)
            Text("Screen time today", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFE5C4))
        }
    }
}
