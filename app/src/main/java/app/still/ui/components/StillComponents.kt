package app.still.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
            painter = painterResource(R.drawable.ic_still_brand_mark),
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

/**
 * The app wordmark pairs the supplied Still SVG mark with the product name.
 * The mark is an Android vector conversion of `branding/still_brand_mark.svg`, so it
 * stays crisp and keeps the same proportions at every density and display size.
 */
@Composable
fun StillWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 28.dp,
) {
    Row(
        modifier = modifier.semantics {
            contentDescription = "Still"
            role = Role.Image
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_still_brand_mark),
            contentDescription = null,
            modifier = Modifier.size(markSize),
        )
        androidx.compose.material3.Text(
            "Still",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
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
