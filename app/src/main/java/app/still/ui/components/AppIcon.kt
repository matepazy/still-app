package app.still.ui.components

import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

private const val IconBitmapSize = 96
private const val AppIconCornerPercent = 24

@Composable
fun AppIcon(packageName: String, label: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val drawable: Drawable? = remember(packageName) {
        runCatching {
            context.getSystemService(LauncherApps::class.java)
                ?.getActivityList(packageName, Process.myUserHandle())
                ?.firstOrNull()
                ?.getIcon(0)
        }.getOrNull() ?: runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    if (drawable != null) {
        val painter = remember(drawable) {
            BitmapPainter(
                drawable.toBitmap(
                    width = IconBitmapSize,
                    height = IconBitmapSize,
                ).asImageBitmap(),
            )
        }
        Image(
            painter = painter,
            contentDescription = "$label app icon",
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(AppIconCornerPercent)),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(AppIconCornerPercent))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(StillIcons.Apps),
                contentDescription = "$label app icon unavailable",
                modifier = Modifier.size(size * .48f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
