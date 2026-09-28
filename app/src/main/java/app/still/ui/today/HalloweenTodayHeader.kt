package app.still.ui.today

import android.animation.ValueAnimator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import app.still.ui.theme.StillSpacing
import kotlin.math.sin

@Composable
internal fun HalloweenTodayHeader(date: String, duration: String) {
    val glow = if (ValueAnimator.areAnimatorsEnabled()) {
        val transition = rememberInfiniteTransition(label = "moonlight")
        val value by transition.animateFloat(
            initialValue = .6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3300), RepeatMode.Reverse),
            label = "moonlight glow",
        )
        value
    } else .8f

    Box(
        Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF251A31), Color(0xFF472B3D), Color(0xFF6A3B2A))),
                RoundedCornerShape(22.dp),
            ),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val moon = Offset(size.width * .83f, size.height * .29f)
            val radius = 27.dp.toPx()
            drawCircle(Color(0xFFFFB765).copy(alpha = .12f * glow), radius * 1.85f, moon)
            drawCircle(Color(0xFFFFD99A).copy(alpha = .2f * glow), radius * 1.35f, moon)
            drawCircle(Color(0xFFFFE3B4), radius, moon)
            drawCircle(Color(0xFFE8C992).copy(alpha = .46f), radius * .13f,
                moon + Offset(-radius * .36f, -radius * .16f))
            drawCircle(Color(0xFFE8C992).copy(alpha = .35f), radius * .09f,
                moon + Offset(radius * .19f, radius * .3f))

            val stars = listOf(.58f to .19f, .67f to .42f, .95f to .57f, .74f to .72f, .93f to .13f)
            stars.forEachIndexed { index, (x, y) ->
                val center = Offset(size.width * x, size.height * y)
                val alpha = (.42f + .24f * sin(index * 2f + glow * 2f)).coerceIn(.2f, .72f)
                drawLine(Color(0xFFFFE7BD).copy(alpha = alpha),
                    center + Offset(-3.dp.toPx(), 0f), center + Offset(3.dp.toPx(), 0f), 1.dp.toPx())
                drawLine(Color(0xFFFFE7BD).copy(alpha = alpha),
                    center + Offset(0f, -3.dp.toPx()), center + Offset(0f, 3.dp.toPx()), 1.dp.toPx())
            }

            val hill = Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, size.height * .94f)
                cubicTo(size.width * .35f, size.height * .80f,
                    size.width * .68f, size.height * .99f, size.width, size.height * .82f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(hill, Color(0xFF17131D).copy(alpha = .55f))
            val bat = Path().apply {
                moveTo(0f, 4f)
                quadraticTo(7f, -1f, 14f, 5f)
                lineTo(19f, 2f)
                lineTo(23f, 4f)
                lineTo(28f, 2f)
                quadraticTo(37f, -1f, 44f, 5f)
                quadraticTo(36f, 4f, 33f, 12f)
                quadraticTo(27f, 8f, 23f, 14f)
                quadraticTo(18f, 8f, 12f, 12f)
                quadraticTo(9f, 5f, 0f, 4f)
                close()
            }
            val batOrigin = Offset(size.width * .57f, size.height * .27f)
            withTransform({
                translate(batOrigin.x, batOrigin.y)
                scale(1.7f, 1.7f, Offset.Zero)
            }) { drawPath(bat, Color(0xFF17131D)) }
            val unit = 1.dp.toPx()
            val pumpkin = Offset(size.width * .86f, size.height * .77f)
            drawCircle(Color(0xFFFFAB52).copy(alpha = .11f * glow), 34f * unit, pumpkin)
            drawOval(Color(0xFF100E17).copy(alpha = .45f),
                Offset(pumpkin.x - 26f * unit, pumpkin.y + 16f * unit), Size(52f * unit, 8f * unit))
            drawOval(Color(0xFF9D4B2B),
                Offset(pumpkin.x - 23f * unit, pumpkin.y - 19f * unit), Size(46f * unit, 39f * unit))
            drawOval(Color(0xFFDE7938),
                Offset(pumpkin.x - 18f * unit, pumpkin.y - 19f * unit), Size(36f * unit, 39f * unit))
            drawOval(Color(0xFFF2A04B),
                Offset(pumpkin.x - 11f * unit, pumpkin.y - 18f * unit), Size(22f * unit, 37f * unit))
            val stem = Path().apply {
                moveTo(pumpkin.x - 3f * unit, pumpkin.y - 17f * unit)
                lineTo(pumpkin.x - 2f * unit, pumpkin.y - 27f * unit)
                lineTo(pumpkin.x + 4f * unit, pumpkin.y - 28f * unit)
                lineTo(pumpkin.x + 3f * unit, pumpkin.y - 17f * unit)
                close()
            }
            drawPath(stem, Color(0xFF7B8A56))
            val eyes = Path().apply {
                moveTo(pumpkin.x - 14f * unit, pumpkin.y - 5f * unit)
                lineTo(pumpkin.x - 6f * unit, pumpkin.y - 9f * unit)
                lineTo(pumpkin.x - 6f * unit, pumpkin.y - 2f * unit)
                close()
                moveTo(pumpkin.x + 14f * unit, pumpkin.y - 5f * unit)
                lineTo(pumpkin.x + 6f * unit, pumpkin.y - 9f * unit)
                lineTo(pumpkin.x + 6f * unit, pumpkin.y - 2f * unit)
                close()
            }
            drawPath(eyes, Color(0xFF38202B))
            val smile = Path().apply {
                moveTo(pumpkin.x - 12f * unit, pumpkin.y + 5f * unit)
                quadraticTo(pumpkin.x, pumpkin.y + 15f * unit,
                    pumpkin.x + 12f * unit, pumpkin.y + 5f * unit)
            }
            drawPath(smile, Color(0xFF38202B), style = Stroke(3f * unit))
            drawArc(Color(0xFFFFB765).copy(alpha = .33f), 198f, 55f, false,
                topLeft = Offset(1.dp.toPx(), size.height - 46.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(70.dp.toPx(), 38.dp.toPx()),
                style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.padding(StillSpacing.large)) {
            Text("Today · $date", style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFE2C8D7))
            Spacer(Modifier.height(StillSpacing.large))
            Text(duration, style = MaterialTheme.typography.displayLarge, color = Color(0xFFFFF2E5))
            Text("Screen time today", style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE2C8D7))
        }
    }
}
