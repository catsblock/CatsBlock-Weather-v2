package com.catsblock.weather.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.catsblock.weather.data.NewsArticle
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.typography.GoogleSansFlex

private fun safeHttpUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "https" && scheme != "http") return null
    if (uri.host.isNullOrBlank()) return null
    return uri.toString()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NewsBrowserDialog(article: NewsArticle, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val initial = remember(article.url) { safeHttpUrl(article.url) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(initial == null) }
    var blocked by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(initial) }
    var confirmExternal by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
    BackHandler {
        val web = webView
        if (web != null && web.canGoBack()) web.goBack() else onDismiss()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = tr("Close"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            article.source.ifBlank { tr("Article") },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = GoogleSansFlex.family,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val host = currentUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }.orEmpty()
                        if (host.isNotEmpty()) {
                            Text(
                                host,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = { webView?.reload(); failed = false }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = tr("Retry"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { if (currentUrl != null) confirmExternal = true }) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = tr("Open in browser"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (progress in 0.01f..0.99f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (initial != null) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    setBackgroundColor(0xFF000000.toInt())
                                    settings.apply {
                                        javaScriptEnabled = false
                                        domStorageEnabled = false
                                        allowFileAccess = false
                                        allowContentAccess = false
                                        setSupportMultipleWindows(false)
                                        javaScriptCanOpenWindowsAutomatically = false
                                        setGeolocationEnabled(false)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        mediaPlaybackRequiresUserGesture = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                        userAgentString = userAgentString.replace("; wv", "")
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            val target = safeHttpUrl(request.url?.toString())
                                            if (target == null || request.url.scheme?.lowercase() != "https") {
                                                blocked = true
                                                return true
                                            }
                                            currentUrl = target
                                            return false
                                        }

                                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                                            val scheme = request.url.scheme?.lowercase()
                                            if (scheme != "https") return android.webkit.WebResourceResponse("text/plain", "UTF-8", null)
                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            progress = 0.05f
                                            failed = false
                                            safeHttpUrl(url)?.let { currentUrl = it }
                                        }

                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            if (request?.isForMainFrame == true) failed = true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            progress = 1f
                                            safeHttpUrl(url)?.let { currentUrl = it }
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress / 100f
                                        }
                                    }
                                    loadUrl(initial)
                                    webView = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (failed) {
                        Column(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                if (initial == null) tr("This link cannot be opened inside the app.") else tr("The news service is not available right now."),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = GoogleSansFlex.family,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { failed = false; webView?.reload() }) {
                                Text(tr("Retry"))
                            }
                        }
                    }
                    if (progress < 0.05f && !failed && initial != null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
    if (blocked) {
        AlertDialog(
            onDismissRequest = { blocked = false },
            title = { Text(tr("Blocked link")) },
            text = { Text(tr("This link cannot be opened inside the app.")) },
            confirmButton = { TextButton(onClick = { blocked = false }) { Text(tr("Close")) } }
        )
    }
    if (confirmExternal) {
        val domain = currentUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmExternal = false },
            title = { Text(tr("Open external browser?")) },
            text = { Text(tr("You are about to leave CatsBlock Weather and open domain. The CatsBlock Weather Terms of Service and Privacy Policy cannot apply to third-party websites.").replace("domain", domain)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExternal = false
                    val target = safeHttpUrl(currentUrl) ?: return@TextButton
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                }) { Text(tr("Open anyway")) }
            },
            dismissButton = { TextButton(onClick = { confirmExternal = false }) { Text(tr("Cancel")) } }
        )
    }
}
