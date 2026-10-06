package app.still.ui.today

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.still.ui.components.DurationHeadline
import app.still.ui.theme.StillSpacing

@Composable
internal fun FallTodayHeader(date: String, duration: String) {
    val lifecycleOwner = LocalLifecycleOwner.current
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF693A2A), Color(0xFF9A512A), Color(0xFFBA7137)),
                ),
            ),
    ) {
        Spacer(
            Modifier.matchParentSize()
                .graphicsLayer()
                .fallingLeaves(lifecycleOwner.lifecycle),
        )
        Column(Modifier.padding(StillSpacing.large)) {
            Text(
                "Today · $date",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFFFE5C4),
            )
            Spacer(Modifier.height(StillSpacing.large))
            DurationHeadline(duration, color = Color.White)
            Text("Screen time today", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFE5C4))
        }
    }
}
