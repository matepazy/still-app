package app.still.data.themes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

data class ThemePackage(val theme: CommunityTheme, val bytes: ByteArray, val assets: Map<String, ByteArray>) {
    private val decoded = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    fun bitmap(path: String): Bitmap? = assets[path]?.let { data -> decoded.getOrPut(path) { requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size)) } }
    fun prepareImages() { assets.keys.forEach(::bitmap) }
    fun releaseImages() { decoded.clear() }
}

object ThemePackages {
    const val MAX_DOWNLOAD = 10 * 1024 * 1024
    private const val MAX_EXPANDED = 20 * 1024 * 1024
    fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            require(out.size().toLong() + count <= limit) { "Theme file exceeds its size limit" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
    fun unpack(bytes: ByteArray): Pair<String, Map<String, ByteArray>> {
        require(bytes.size <= MAX_DOWNLOAD) { "Theme files must be 10 MB or smaller" }
        if (bytes.take(4) != listOf(0x50.toByte(), 0x4b.toByte(), 3.toByte(), 4.toByte())) {
            require(bytes.size <= ThemeCompose.MAX_SOURCE) { "Theme source exceeds 1 MB" }
            return utf8(bytes) to emptyMap()
        }
        val entries = linkedMapOf<String, ByteArray>(); var expanded = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(entries.size < 32 && name !in entries && !entry.isDirectory) { "Too many, duplicate or directory archive entries" }
                require(name in listOf("theme.tc", "theme.stc") || ThemeCompose.safeArchiveAssetPath(name)) { "Unsafe or unsupported archive entry" }
                val limit = if (name.startsWith("theme.")) ThemeCompose.MAX_SOURCE else MAX_DOWNLOAD
                val data = readLimited(zip, minOf(limit, MAX_EXPANDED - expanded))
                expanded += data.size; entries[name] = data; zip.closeEntry()
            }
        }
        val sources = entries.keys.filter { it in listOf("theme.tc", "theme.stc") }
        require(sources.size == 1) { "Bundle must contain exactly one theme.tc (or legacy theme.stc)" }
        return utf8(entries.remove(sources.single())!!) to entries
    }
    private fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    fun load(bytes: ByteArray): ThemePackage {
        val (source, files) = unpack(bytes)
        val theme = ThemeCompose.parse(source)
        val referenced = theme.images.values.flatMap { it.outputs() }.toSet()
        require(files.keys.containsAll(referenced)) { "Bundle is missing referenced images; source-only imports cannot contain image paths" }
        val applicable = files.filterKeys { it in referenced }
        var totalPixels = 0L
        applicable.forEach { (_, data) ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            require(options.outMimeType in listOf("image/png", "image/jpeg", "image/webp") && options.outWidth in 1..2048 && options.outHeight in 1..2048 && options.outWidth.toLong() * options.outHeight <= 4_194_304) { "Images must be PNG, JPEG or WebP, at most 2048 pixels per side" }
            totalPixels += options.outWidth.toLong() * options.outHeight
            require(totalPixels <= 8_388_608) { "Theme artwork exceeds the 8 megapixel total limit" }
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size)) { "Cannot decode theme image" }
            decoded.recycle()
        }
        return ThemePackage(theme, bytes, applicable)
    }
}
