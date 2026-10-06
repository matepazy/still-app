package app.still.ui.today

import android.view.Choreographer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.sin

internal fun Modifier.fallingLeaves(lifecycle: Lifecycle): Modifier = then(FallingLeavesElement(lifecycle))

private data class FallingLeavesElement(val lifecycle: Lifecycle) : ModifierNodeElement<FallingLeavesNode>() {
    override fun create() = FallingLeavesNode(lifecycle)
    override fun update(node: FallingLeavesNode) = node.updateLifecycle(lifecycle)
    override fun InspectorInfo.inspectableProperties() { name = "fallingLeaves" }
}

/** Repaints its own layer without snapshot writes or continuous recomposer frame requests. */
private class FallingLeavesNode(private var lifecycle: Lifecycle) : Modifier.Node(), DrawModifierNode,
    Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var lastFrameNanos = 0L
    private var progress = .12
    private val observer = LifecycleEventObserver { _, _ -> updateRunning() }
    private val colors = arrayOf(
        Color(0xFFF7C877).copy(alpha = .38f),
        Color(0xFFE8A15C).copy(alpha = .38f),
        Color(0xFFFFD899).copy(alpha = .38f),
    )
    private val veinColor = Color(0xFFFFE1AA).copy(alpha = .32f)
    private val leaf = Path().apply {
        moveTo(10f, 1f)
        cubicTo(18f, 5f, 21f, 11f, 17f, 17f)
        cubicTo(14f, 22f, 9f, 24f, 5f, 23f)
        cubicTo(3f, 16f, 3f, 10f, 10f, 1f)
        close()
    }

    override fun onAttach() {
        lifecycle.addObserver(observer)
        updateRunning()
    }

    override fun onDetach() {
        stop()
        lifecycle.removeObserver(observer)
    }

    fun updateLifecycle(value: Lifecycle) {
        if (lifecycle === value) return
        stop()
        if (isAttached) lifecycle.removeObserver(observer)
        lifecycle = value
        if (isAttached) {
            lifecycle.addObserver(observer)
            updateRunning()
        }
    }

    private fun updateRunning() {
        if (isAttached && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (!running) {
                running = true
                lastFrameNanos = 0L
                choreographer.postFrameCallback(this)
            }
        } else stop()
    }

    private fun stop() {
        running = false
        lastFrameNanos = 0L
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running || !isAttached) return
        // Keep ambient drift independent of the system animator duration scale.
        // Reset the timestamp on pause so resuming never fast-forwards the leaves.
        if (lastFrameNanos != 0L) {
            progress = (progress + (frameTimeNanos - lastFrameNanos) / 14_000_000_000.0) % 1.0
            invalidateDraw()
        }
        lastFrameNanos = frameTimeNanos
        choreographer.postFrameCallback(this)
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        repeat(6) { index ->
            val phase = (progress.toFloat() + index * .173f) % 1f
            val x = size.width * (.12f + (index * .227f) % .80f) +
                sin(phase * 6.28f + index) * 17.dp.toPx()
            val y = -30.dp.toPx() + phase * (size.height + 60.dp.toPx())
            val leafScale = (if (index % 3 == 0) 1f else .7f) * density
            withTransform({
                translate(x, y)
                rotate(phase * 125f + index * 39f, Offset(10f, 12f))
                scale(leafScale, leafScale, Offset(10f, 12f))
            }) {
                drawPath(leaf, colors[index % colors.size])
                drawLine(veinColor, Offset(10f, 4f), Offset(10f, 21f), strokeWidth = 1f)
            }
        }
    }
}
