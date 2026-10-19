package app.oralyvo.mobile.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.oralyvo.mobile.data.TokenStore
import org.json.JSONObject
import org.json.JSONTokener

/** Reuses the production account forms, including Turnstile, signup
 * confirmation and password recovery. No JavaScript interface is exposed. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TokenStore(context) }
    val authenticated by rememberUpdatedState(onAuthenticated)
    var failure by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var view by remember { mutableStateOf<WebView?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Oralyvo", style = MaterialTheme.typography.headlineMedium)
        Text("Sign in with your Oralyvo email and password. Account creation and password recovery are available below.", Modifier.padding(vertical = 8.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        TextButton(onClick = { failure = null; loading = true; attempt++ }) { Text("Reload sign-in") }
        key(attempt) {
            AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { ctx ->
                // Sign-in always starts clean, including after switching accounts.
                WebStorage.getInstance().deleteAllData()
                WebView(ctx).apply {
                    view = this
                    tag = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    var delivered = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            val url = request.url
                            if (url.scheme == "https" && url.host == "oralyvo.app" && url.path != "/api/auth/oauth") return false
                            if (url.path == "/api/auth/oauth") {
                                failure = "Use email sign-in here. For a social-only account, use password recovery to set a password."
                                return true
                            }
                            if (url.scheme in listOf("https", "mailto")) {
                                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                                catch (_: Exception) { failure = "Could not open that link. Please use your browser." }
                            }
                            return true
                        }
                        override fun onReceivedError(web: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) { loading = false; failure = "Sign-in could not load. Check your connection and reload." }
                        }
                        override fun onPageFinished(web: WebView, url: String) {
                            loading = false
                            val uri = Uri.parse(url)
                            if (uri.scheme != "https" || uri.host != "oralyvo.app" || delivered) return
                            web.evaluateJavascript("""
                                (() => {
                                  if (location.origin !== 'https://oralyvo.app') return null;
                                  document.querySelectorAll('[data-oauth-provider]').forEach(b => b.remove());
                                  const s = JSON.parse(localStorage.getItem('speech2text_pro_session') || 'null');
                                  return s && s.access_token && s.userId ? JSON.stringify({
                                    access_token:s.access_token, refresh_token:s.refresh_token,
                                    expires_at:s.expires_at, email:s.email, userId:s.userId
                                  }) : null;
                                })()
                            """.trimIndent()) { raw ->
                                try {
                                    // evaluateJavascript serializes the returned string once more.
                                    val value = JSONTokener(raw).nextValue() as? String ?: return@evaluateJavascript
                                    val session = TokenStore.parse(JSONObject(value))
                                    check(Uri.parse(web.url).host == "oralyvo.app")
                                    store.save(session)
                                    delivered = true
                                    authenticated()
                                } catch (_: Exception) { failure = "Could not securely complete sign-in. Please reload and try again." }
                            }
                        }
                    }
                    CookieManager.getInstance().removeAllCookies {
                        if (tag == true && !delivered) loadUrl("https://oralyvo.app/login/")
                    }
                }
            }, onRelease = { web -> web.tag = false; web.stopLoading(); web.destroy() })
        }
    }
    DisposableEffect(Unit) {
        onDispose { view?.stopLoading(); WebStorage.getInstance().deleteAllData() }
    }
}
