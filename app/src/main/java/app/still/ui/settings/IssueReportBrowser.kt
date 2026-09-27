package app.still.ui.settings

import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val ReportCallback = "still://report-complete"
private val ReportUrl = Uri.parse("https://matepazy.hu/report-error")
    .buildUpon()
    .appendQueryParameter("service", "still-app")
    .appendQueryParameter("callback", ReportCallback)
    .build()
    .toString()

private fun isSiteHomepage(uri: Uri): Boolean =
    uri.scheme == "https" &&
        uri.host == "matepazy.hu" &&
        (uri.path.isNullOrEmpty() || uri.path == "/")

@Composable
fun IssueReportBrowser(
    onSubmitted: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                var completionRequested = false
                fun finishOnce(submitted: Boolean) {
                    if (!completionRequested) {
                        completionRequested = true
                        post { if (submitted) onSubmitted() else onClose() }
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        if (consoleMessage.message() == "submit-OK") {
                            finishOnce(submitted = true)
                            return true
                        }
                        return super.onConsoleMessage(consoleMessage)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (request.isForMainFrame && uri.scheme == "still" && uri.host == "report-complete") {
                            finishOnce(submitted = true)
                            return true
                        }
                        if (request.isForMainFrame && isSiteHomepage(uri)) {
                            finishOnce(submitted = false)
                            return true
                        }
                        if (uri.scheme == "https" && uri.host == "matepazy.hu") return false
                        if (uri.scheme == "https") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                        return true
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (url != null && isSiteHomepage(Uri.parse(url))) finishOnce(submitted = false)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        if (url != null && isSiteHomepage(Uri.parse(url))) finishOnce(submitted = false)
                    }
                }
                loadUrl(ReportUrl)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        },
    )
}
