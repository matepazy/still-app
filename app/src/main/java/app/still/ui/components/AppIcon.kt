package app.still.ui.components

import android.graphics.drawable.Drawable
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material3.Text

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
        val painter = remember(drawable) { BitmapPainter(drawable.toBitmap(96, 96).asImageBitmap()) }
        Image(
            painter = painter,
            contentDescription = "$label app icon",
            modifier = modifier.size(size),
        )
    } else {
        Box(
            modifier = modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
