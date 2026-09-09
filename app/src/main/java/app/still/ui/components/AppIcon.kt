package app.still.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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

private const val IconBitmapSize = 96

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
            BitmapPainter(drawable.toSquareBitmap(IconBitmapSize).asImageBitmap())
        }
        Image(
            painter = painter,
            contentDescription = "$label app icon",
            modifier = modifier.size(size),
        )
    } else {
        Box(
            modifier = modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Launcher adaptive icons are often rendered with a transparent mask around their
 * logo and background. Fill those transparent pixels so app icons keep a square,
 * color-filled footprint in usage rows.
 */
private fun Drawable.toSquareBitmap(size: Int): Bitmap {
    val bitmap = toBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

    val background = pixels
        .asSequence()
        .filter { Color.alpha(it) >= 240 }
        .groupingBy { color ->
            Color.rgb(
                (Color.red(color) / 16) * 16,
                (Color.green(color) / 16) * 16,
                (Color.blue(color) / 16) * 16,
            )
        }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

    if (background == null || pixels.none { Color.alpha(it) < 255 }) return bitmap

    pixels.indices.forEach { index ->
        val pixel = pixels[index]
        val alpha = Color.alpha(pixel)
        if (alpha < 255) {
            val inverseAlpha = 255 - alpha
            pixels[index] = Color.rgb(
                (Color.red(pixel) * alpha + Color.red(background) * inverseAlpha) / 255,
                (Color.green(pixel) * alpha + Color.green(background) * inverseAlpha) / 255,
                (Color.blue(pixel) * alpha + Color.blue(background) * inverseAlpha) / 255,
            )
        }
    }
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap
}
