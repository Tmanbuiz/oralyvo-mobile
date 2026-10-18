package app.oralyvo.mobile.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders the Cloudflare Turnstile challenge from assets/turnstile.html in a
 * WebView and reports the result back to Compose. There is no native
 * Turnstile SDK for Android, so this is the same trick the web app doesn't
 * need but a native client does: load the widget in a tiny embedded
 * browser and bridge its JS callbacks (see turnstile.html) back to Kotlin
 * through a @JavascriptInterface object.
 *
 * The backend only ever sees the resulting token -- it is passed as
 * "turnstileToken" in the sign-in/sign-up request bodies ApiClient sends,
 * exactly like the web app's own Turnstile widget does.
 */
@Composable
fun TurnstileWebView(
    modifier: Modifier = Modifier,
    onToken: (String) -> Unit,
    onError: (String) -> Unit = {},
    onExpired: () -> Unit = {}
) {
    // rememberUpdatedState so the WebView (created once by AndroidView's
    // factory) always calls the latest lambdas, even if the caller
    // recomposes with new ones -- the JS bridge object itself is created
    // only on first composition.
    val currentOnToken = rememberUpdatedState(onToken)
    val currentOnError = rememberUpdatedState(onError)
    val currentOnExpired = rememberUpdatedState(onExpired)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                @SuppressLint("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onToken(token: String) {
                            currentOnToken.value(token)
                        }

                        @JavascriptInterface
                        fun onError(code: String) {
                            currentOnError.value(code)
                        }

                        @JavascriptInterface
                        fun onExpired() {
                            currentOnExpired.value()
                        }
                    },
                    "AndroidBridge"
                )
                loadUrl("file:///android_asset/turnstile.html")
            }
        }
    )
}
