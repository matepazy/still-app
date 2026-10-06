package app.still.ui.components

import android.content.pm.LauncherApps
import android.os.SystemClock
import android.util.LruCache
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val IconBitmapSize = 96
private const val AppIconCornerPercent = 24
private const val IconCacheLifetimeMillis = 60_000L
private data class CachedAppIcon(val bitmap: ImageBitmap, val loadedAt: Long)
private val appIconCache = LruCache<String, CachedAppIcon>(64)

private fun cachedIcon(key: String): ImageBitmap? = appIconCache.get(key)?.let {
    it.bitmap.takeIf { _ -> SystemClock.elapsedRealtime() - it.loadedAt < IconCacheLifetimeMillis }
}

@Composable
fun AppIcon(packageName: String, label: String, modifier: Modifier = Modifier, size: Dp = 40.dp,
    fallbackIcon: Int = StillIcons.Apps) {
    val context = LocalContext.current.applicationContext
    val configuration = LocalConfiguration.current
    val cacheKey = "$packageName:${configuration.hashCode()}"
    val initialBitmap = remember(cacheKey) { cachedIcon(cacheKey) }
    val bitmap by produceState(initialBitmap, cacheKey) {
        value = withContext(Dispatchers.IO) {
            cachedIcon(cacheKey) ?: if (packageName.isBlank()) null else {
                // Launcher/package-manager calls and bitmap conversion must not block tab entry.
                val drawable = runCatching {
                    context.getSystemService(LauncherApps::class.java)
                        ?.getActivityList(packageName, Process.myUserHandle())
                        ?.firstOrNull()
                        ?.getIcon(0)
                }.getOrNull() ?: runCatching {
                    context.packageManager.getApplicationIcon(packageName)
                }.getOrNull()
                runCatching {
                    drawable?.toBitmap(IconBitmapSize, IconBitmapSize)?.asImageBitmap()
                }.getOrNull()?.also {
                    appIconCache.put(cacheKey, CachedAppIcon(it, SystemClock.elapsedRealtime()))
                }
            }
        }
    }
    if (bitmap != null) {
        val painter = remember(bitmap) { BitmapPainter(requireNotNull(bitmap)) }
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
                painter = painterResource(fallbackIcon),
                contentDescription = "$label app icon unavailable",
                modifier = Modifier.size(size * .48f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
