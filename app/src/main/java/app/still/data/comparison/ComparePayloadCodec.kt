package app.still.data.comparison

import app.still.domain.model.CompareSnapshot
import app.still.data.settings.AppCategory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object ComparePayloadCodec {
    private const val PREFIX = "STILL-CMP:1:"
    private const val MAX_COMPRESSED = 1400
    private const val MAX_JSON = 12000
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(CompareSnapshot::class.java)

    fun encode(snapshot: CompareSnapshot): String {
        validate(snapshot)
        val bytes = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(adapter.toJson(snapshot).toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        require(bytes.size <= MAX_COMPRESSED) { "Comparison is too large for a QR code." }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun decode(code: String): Result<CompareSnapshot> = runCatching {
        require(code.startsWith(PREFIX) && code.length <= 2000) { "This is not a supported Still comparison code." }
        val compressed = Base64.getUrlDecoder().decode(code.removePrefix(PREFIX))
        require(compressed.size <= MAX_COMPRESSED) { "Comparison code is too large." }
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                require(output.size() <= MAX_JSON) { "Comparison code is too large." }
            }
        }
        val snapshot = requireNotNull(adapter.fromJson(output.toString(Charsets.UTF_8.name())))
        validate(snapshot)
        snapshot
    }

    fun fingerprint(snapshot: CompareSnapshot): String = MessageDigest.getInstance("SHA-256")
        .digest(encode(snapshot).toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) }

    private fun validate(snapshot: CompareSnapshot) {
        require(snapshot.version == 1) { "Unsupported comparison version." }
        require(snapshot.sessionId.matches(Regex("[0-9a-f]{32}"))) { "Invalid comparison session." }
        require(snapshot.replyTo == null || snapshot.replyTo.matches(Regex("[0-9a-f]{24}")))
        val range = snapshot.range
        require(range.days in 1..3660 && !range.endInclusive.isAfter(LocalDate.now()))
        val sharing = snapshot.sharing
        require(snapshot.totalScreenTimeMillis == null || snapshot.totalScreenTimeMillis in 0..(3660L * 24 * 60 * 60 * 1000))
        require(snapshot.averageDailyScreenTimeMillis == null || snapshot.averageDailyScreenTimeMillis in 0..86_400_000L)
        require(snapshot.screenTimeDays == null || snapshot.screenTimeDays in 1..range.days.toInt())
        require(snapshot.checkIns == null || snapshot.checkIns in 0..1_000_000)
        require(snapshot.quickChecks == null || snapshot.quickChecks in 0..1_000_000)
        require(snapshot.longestBreakMillis == null || snapshot.longestBreakMillis in 0..86_400_000L)
        require(sharing.screenTime || (snapshot.totalScreenTimeMillis == null && snapshot.averageDailyScreenTimeMillis == null && snapshot.screenTimeDays == null))
        require(sharing.patterns || (snapshot.checkIns == null && snapshot.quickChecks == null && snapshot.longestBreakMillis == null))
        require(sharing.categories || snapshot.categories == null)
        require(sharing.apps || snapshot.apps == null)
        require(snapshot.categories.orEmpty().size <= 10 && snapshot.apps.orEmpty().size <= 8)
        require(snapshot.categories.orEmpty().all { item -> AppCategory.entries.any { it.displayName == item.name } && item.millis >= 0 })
        require(snapshot.apps.orEmpty().all { it.label.length <= 50 && it.millis >= 0 })
        snapshot.cutoffEpochMillis?.let {
            require(range.endInclusive == LocalDate.now())
            require(it in 0..(System.currentTimeMillis() + 300_000))
        }
    }
}
