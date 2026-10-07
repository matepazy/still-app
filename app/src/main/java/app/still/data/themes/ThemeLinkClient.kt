package app.still.data.themes

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** DNS checked at connection time; redirects, cookies, credentials and local endpoints are forbidden. */
class ThemeLinkClient {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                require(addresses.isNotEmpty() && addresses.all(::publicAddress)) { "Theme links must resolve to public internet addresses" }
            }
        }).build()

    suspend fun fetch(link: String): ByteArray = suspendCancellableCoroutine { continuation ->
        val url = validateUrl(link)
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/zip, text/plain, application/octet-stream").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        require(it.code == 200) { "The link must return a theme directly (HTTP ${it.code}); redirects are not accepted" }
                        val body = requireNotNull(it.body) { "Empty theme response" }
                        require(body.contentLength() <= ThemePackages.MAX_DOWNLOAD) { "Theme download exceeds 10 MB" }
                        body.byteStream().use { stream -> ThemePackages.readLimited(stream, ThemePackages.MAX_DOWNLOAD) }
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }
    companion object {
        fun validateUrl(link: String): HttpUrl {
            require(link.length <= 2048) { "Link is too long" }
            val url = requireNotNull(link.toHttpUrlOrNull()) { "Enter a complete HTTPS link" }
            require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null) { "Use an HTTPS link on port 443 without credentials or a fragment" }
            require(url.host != "localhost" && !url.host.endsWith(".localhost") && !url.host.endsWith(".local")) { "Local theme servers are not supported" }
            return url
        }
        internal fun publicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
            val b = address.address.map { it.toInt() and 255 }
            if (b.size == 4) {
                if (b[0] in listOf(0, 10, 127) || b[0] >= 224) return false
                if (b[0] == 100 && b[1] in 64..127 || b[0] == 169 && b[1] == 254 || b[0] == 172 && b[1] in 16..31 || b[0] == 192 && b[1] == 168) return false
                if (b[0] == 192 && b[1] == 0 || b[0] == 192 && b[1] == 88 && b[2] == 99 || b[0] == 198 && b[1] in 18..19 || b[0] == 198 && b[1] == 51 && b[2] == 100 || b[0] == 203 && b[1] == 0 && b[2] == 113) return false
                return true
            }
            // Global IPv6 unicast only; deny documentation, Teredo and 6to4 tunnels.
            return b.size == 16 && b[0] in 0x20..0x3f && !(b[0] == 0x20 && b[1] == 0x01 && (b[2] == 0x0d && b[3] == 0xb8 || b[2] < 2)) && !(b[0] == 0x20 && b[1] == 0x02)
        }
    }
}
