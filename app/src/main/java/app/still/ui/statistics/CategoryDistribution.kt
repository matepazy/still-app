package app.still.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.still.data.settings.AppCategory
import app.still.domain.model.StatisticsCategory

/** Stable category colors shared by the distribution, icons, and individual bars. */
@Composable
internal fun statisticsCategoryColor(category: AppCategory): Color {
    val scheme = MaterialTheme.colorScheme
    return when (category) {
        AppCategory.Social -> scheme.primary
        AppCategory.Games -> scheme.tertiary
        AppCategory.Video -> scheme.secondary
        AppCategory.MusicAndAudio -> lerp(scheme.primary, scheme.tertiary, .35f)
        AppCategory.Photography -> lerp(scheme.secondary, scheme.tertiary, .5f)
        AppCategory.News -> lerp(scheme.primary, scheme.secondary, .5f)
        AppCategory.MapsAndNavigation -> lerp(scheme.primary, scheme.tertiary, .7f)
        AppCategory.Productivity -> lerp(scheme.secondary, scheme.primary, .7f)
        AppCategory.Accessibility -> lerp(scheme.secondary, scheme.tertiary, .75f)
        AppCategory.Other -> scheme.outline
    }
}

@Composable
internal fun CategoryDistribution(categories: List<StatisticsCategory>) {
    val visible = categories.filter { it.share > 0.0 }
    if (visible.isEmpty()) return
    val colors = visible.map { statisticsCategoryColor(it.category) }
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    val divider = MaterialTheme.colorScheme.surface
    val description = visible.joinToString { "${it.category.displayName}, ${(it.share * 100).toInt()} percent" }
    Canvas(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(6.dp))
        .semantics { contentDescription = "Screen time by category: $description" }) {
        drawRect(background)
        var left = 0f
        visible.forEachIndexed { index, category ->
            val width = size.width * category.share.toFloat().coerceIn(0f, 1f)
            drawRect(colors[index], Offset(left, 0f), Size(width, size.height))
            if (index > 0) drawLine(divider, Offset(left, 0f), Offset(left, size.height), 2.dp.toPx())
            left += width
        }
    }
}
