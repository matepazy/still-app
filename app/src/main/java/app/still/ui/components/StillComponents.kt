package app.still.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.still.R

@Composable
fun StillMark(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    container: Boolean = false,
) {
    val mark = @Composable {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Still logo",
            modifier = Modifier.size(size),
        )
    }
    if (container) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * .28f))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { mark() }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { mark() }
    }
}

@Composable
fun TonalPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun IconTile(
    @DrawableRes icon: Int,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), description, Modifier.size(size * .62f))
    }
}
