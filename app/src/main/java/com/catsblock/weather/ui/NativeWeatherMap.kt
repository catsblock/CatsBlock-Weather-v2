package com.catsblock.weather.ui

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.catsblock.weather.data.AlertFeed
import com.catsblock.weather.data.City
import com.catsblock.weather.data.DroughtFeed
import com.catsblock.weather.data.FireHotspots
import com.catsblock.weather.data.RainViewerPalette
import com.catsblock.weather.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng

@Composable
@SuppressLint("SetJavaScriptEnabled")
internal fun NativeWeatherMap(
    city: City?,
    layer: MapLayer,
    dark: Boolean,
    focusRequest: Int,
    selectedTimeMillis: Long = System.currentTimeMillis(),
    onPointSelected: (latitude: Double, longitude: Double, x: Float, y: Float) -> Unit,
    onMapReady: (Boolean) -> Unit,
    initialCenter: LatLng? = null,
    initialZoom: Double = 5.5,
    showAttribution: Boolean = true,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val currentOnPointSelected by rememberUpdatedState(onPointSelected)
    val currentOnMapReady by rememberUpdatedState(onMapReady)
    val currentLayer by rememberUpdatedState(layer)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var load by remember { mutableStateOf(MapLoadState().start()) }
    var generation by remember { mutableIntStateOf(0) }
    var pageRetry by remember { mutableIntStateOf(0) }
    var layerRetry by remember { mutableIntStateOf(0) }
    var handledLayerRetry by remember { mutableIntStateOf(0) }
    var rendererLost by remember { mutableStateOf(false) }
    var centeredCityId by remember { mutableStateOf<String?>(null) }
    val pageReady = load.pageReady
    val bridge = remember(generation) {
        MapBridge { latitude, longitude, x, y -> currentOnPointSelected(latitude, longitude, x, y) }
    }

    key(generation) {
        AndroidView(
            modifier = modifier,
            factory = { viewContext ->
                WebView(viewContext).also { view ->
                    view.setBackgroundColor(if (dark) 0xFF2B2D31.toInt() else 0xFFE8E6E1.toInt())
                    view.overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    view.isVerticalScrollBarEnabled = false
                    view.isHorizontalScrollBarEnabled = false
                    view.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        mediaPlaybackRequiresUserGesture = true
                    }
                    view.addJavascriptInterface(bridge, "CatsBlockBridge")
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame && bridge.active) load = load.onBaseFailed(MapFailure.SCRIPT)
                        }

                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            if (bridge.active) {
                                rendererLost = true
                                load = load.onBaseFailed(MapFailure.RENDERER)
                            }
                            return true
                        }
                    }
                    val html = buildMapPage(viewContext)
                    if (html == null) {
                        view.post { if (bridge.active) load = load.onBaseFailed(MapFailure.BOOT) }
                    } else {
                        view.loadDataWithBaseURL(MAP_BASE_URL, html, "text/html", "UTF-8", null)
                    }
                    webView = view
                }
            },
            onRelease = { view ->
                releaseMapView(view, bridge)
                if (webView === view) webView = null
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose { currentOnMapReady(false) }
    }

    DisposableEffect(webView, lifecycleOwner) {
        val view = webView
        if (view == null) {
            onDispose { }
        } else {
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    view.onResume()
                    view.runScript("window.CBMap && CBMap.setActive(true)")
                }

                override fun onPause(owner: LifecycleOwner) {
                    view.runScript("window.CBMap && CBMap.setActive(false)")
                    view.onPause()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    LaunchedEffect(load.finishedBaseLoading) {
        currentOnMapReady(load.finishedBaseLoading)
    }

    LaunchedEffect(webView, pageRetry) {
        val view = webView ?: return@LaunchedEffect
        var failures = 0
        var loggedDiagnostics = 0
        while (bridge.active) {
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                delay(1000L)
                continue
            }
            val raw = withTimeoutOrNull(STATUS_POLL_TIMEOUT_MILLIS) { view.evalString("window.CBStatus ? CBStatus() : ''") }
            val json = raw?.takeIf { it.isNotEmpty() && it != "null" }?.let {
                runCatching { JSONObject(JSONObject("{\"v\":$it}").getString("v")) }.getOrNull()
            }
            if (json == null) {
                failures++
                if (load.pageReady && failures >= MAX_STATUS_FAILURES) load = load.onBaseFailed(MapFailure.BRIDGE)
            } else {
                failures = 0
                if (json.optBoolean("page")) load = load.onPageReady()
                if (json.optBoolean("ready")) {
                    load = load.onBaseReady()
                } else {
                    when (json.optString("error")) {
                        "script" -> load = load.onBaseFailed(MapFailure.SCRIPT)
                        "boot" -> load = load.onBaseFailed(MapFailure.BOOT)
                        "tiles" -> load = load.onBaseFailed(MapFailure.BASE_TILES)
                    }
                }
                if (json.optString("layerId") == currentLayer.id) {
                    when (json.optString("layer")) {
                        "loading" -> load = load.onLayerLoading()
                        "ready" -> load = load.onLayerActive()
                        "error" -> load = load.onLayerFailed()
                    }
                }
                val diagnostics = json.optJSONArray("diag")
                if (diagnostics != null) {
                    while (loggedDiagnostics < diagnostics.length()) {
                        val entry = diagnostics.optJSONObject(loggedDiagnostics)
                        if (entry != null) Log.w(MAP_LOG_TAG, "${entry.optString("c")}:${entry.optString("k")}")
                        loggedDiagnostics++
                    }
                }
            }
            delay(if (load.baseReady && load.layer != LayerLoadState.LOADING) 1000L else 300L)
        }
    }

    LaunchedEffect(webView, pageRetry) {
        if (webView == null) return@LaunchedEffect
        val pageStage = withTimeoutOrNull(PAGE_DEADLINE_MILLIS) {
            snapshotFlow { load }.first { it.pageReady || it.failure != null }
        }
        if (pageStage == null) {
            load = load.onBaseFailed(MapFailure.TIMEOUT)
            return@LaunchedEffect
        }
        val baseStage = withTimeoutOrNull(BASE_DEADLINE_MILLIS) {
            snapshotFlow { load }.first { it.baseReady || it.failure != null }
        }
        if (baseStage == null) load = load.onBaseFailed(MapFailure.TIMEOUT)
    }

    LaunchedEffect(pageReady, dark) {
        val view = webView ?: return@LaunchedEffect
        if (pageReady) view.runScript("CBMap.setDark($dark)")
    }

    LaunchedEffect(pageReady, selectedTimeMillis) {
        val view = webView ?: return@LaunchedEffect
        if (pageReady) view.runScript("CBMap.setTime($selectedTimeMillis)")
    }

    LaunchedEffect(pageReady) {
        val view = webView ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        val start = initialCenter?.let { it.latitude to it.longitude } ?: city?.let { it.lat to it.lon }
        if (start != null) {
            val zoom = if (initialCenter != null) initialZoom + 1.0 else 7.0
            view.runScript("CBMap.focus(${start.first}, ${start.second}, $zoom, false)")
            centeredCityId = city?.id
        }
    }

    LaunchedEffect(pageReady, city?.id) {
        val view = webView ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        val selected = city ?: return@LaunchedEffect
        view.runScript("CBMap.setCity(${selected.lat}, ${selected.lon})")
        if (centeredCityId != selected.id && initialCenter == null) {
            view.runScript("CBMap.focus(${selected.lat}, ${selected.lon}, 7, true)")
            centeredCityId = selected.id
        }
    }

    LaunchedEffect(focusRequest, pageReady) {
        val view = webView ?: return@LaunchedEffect
        if (focusRequest == 0 || !pageReady) return@LaunchedEffect
        val selected = city ?: return@LaunchedEffect
        view.runScript("CBMap.focus(${selected.lat}, ${selected.lon}, 8, true)")
    }

    LaunchedEffect(layer.id, pageReady, layerRetry) {
        load = load.onLayerSelected(layer.id)
        val view = webView ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        val id = layer.id
        val reload = layerRetry > handledLayerRetry
        handledLayerRetry = layerRetry
        view.runScript(if (reload) "CBMap.reloadLayer(${JSONObject.quote(id)})" else "CBMap.setLayer(${JSONObject.quote(id)})")
        if (id == "fires" || id == "alerts" || id == "drought") {
            val geoJson = runCatching {
                withTimeoutOrNull(FEATURE_DEADLINE_MILLIS) {
                    withContext(Dispatchers.IO) {
                        when (id) {
                            "fires" -> fireCollection(FireHotspots.json(view.context.applicationContext))
                            "alerts" -> AlertFeed.json()?.toString(Charsets.UTF_8)
                            else -> DroughtFeed.json()?.toString(Charsets.UTF_8)
                        }
                    }
                }
            }.getOrNull()
            if (currentLayer.id != id) return@LaunchedEffect
            if (geoJson == null) view.runScript("CBMap.featuresFailed(${JSONObject.quote(id)})")
            else view.runScript("CBMap.setFeatures(${JSONObject.quote(id)}, ${JSONObject.quote(geoJson)})")
        }
    }

    val retryBase: () -> Unit = {
        val view = webView
        when {
            rendererLost || view == null -> {
                rendererLost = false
                webView = null
                load = load.start()
                generation++
            }
            load.pageReady -> {
                load = load.restartBase()
                view.runScript("CBMap.reloadBase()")
            }
            else -> {
                load = load.start()
                val html = buildMapPage(view.context)
                if (html == null) load = load.onBaseFailed(MapFailure.BOOT)
                else view.loadDataWithBaseURL(MAP_BASE_URL, html, "text/html", "UTF-8", null)
            }
        }
        pageRetry++
    }

    Box(modifier = modifier) {
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            NativeMapLegend(layer, Modifier)
            if (load.baseReady && load.layer == LayerLoadState.LOADING) LayerProgressPill()
            if (load.baseReady && load.layer == LayerLoadState.FAILED) {
                MapNotice(Modifier) {
                    load = load.onLayerSelected(layer.id)
                    layerRetry++
                }
            }
        }
        if (load.phase == MapLoadPhase.ERROR) MapNotice(Modifier.align(Alignment.Center), retryBase)
        if (showAttribution) {
            val sourceAttribution = when (layer.id) {
                "precip" -> "RainViewer" to "https://www.rainviewer.com/"
                "drought" -> "U.S. Drought Monitor" to "https://droughtmonitor.unl.edu/"
                "temp", "clouds", "wind", "pressure", "humidity", "dew", "uv", "aqi", "sea", "coldfront" -> "Open-Meteo" to "https://open-meteo.com/"
                "cyclones" -> "NASA EONET" to "https://eonet.gsfc.nasa.gov/"
                "sst" -> "NASA GIBS" to "https://earthdata.nasa.gov/"
                "fires" -> "NASA FIRMS" to "https://firms.modaps.eosdis.nasa.gov/"
                "alerts" -> "Alert-Hub.org" to "https://alert-hub.org/"
                else -> null
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 6.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), RoundedCornerShape(topStart = 8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "© OpenStreetMap contributors",
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri("https://www.openstreetmap.org/copyright") } },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                sourceAttribution?.let { (label, url) ->
                    Text(
                        text = label,
                        modifier = Modifier.clickable { runCatching { uriHandler.openUri(url) } },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MapNotice(modifier: Modifier, onRetry: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onRetry),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Could not load the weather. Check your connection and try again."), color = MaterialTheme.colorScheme.onSurface)
            Text(tr("Retry"), Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LayerProgressPill() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 2.dp
    ) {
        CircularProgressIndicator(Modifier.padding(8.dp).size(18.dp), strokeWidth = 2.dp)
    }
}

private fun releaseMapView(view: WebView, bridge: MapBridge) {
    bridge.active = false
    view.removeJavascriptInterface("CatsBlockBridge")
    view.stopLoading()
    view.webViewClient = WebViewClient()
    view.loadUrl("about:blank")
    view.removeAllViews()
    view.destroy()
}

private const val MAP_LOG_TAG = "CatsBlockMap"
private const val PAGE_DEADLINE_MILLIS = 15000L
private const val BASE_DEADLINE_MILLIS = 12000L
private const val FEATURE_DEADLINE_MILLIS = 20000L
private const val STATUS_POLL_TIMEOUT_MILLIS = 2000L
private const val MAX_STATUS_FAILURES = 8

private const val MAP_BASE_URL = "https://github.com/catsblock/CatsBlock-Weather-v2/"

private const val MAP_PAGE_STYLE = """
html,body,#map{margin:0;padding:0;width:100%;height:100%;background:#e8e6e1;overflow:hidden}
.leaflet-container{background:#e8e6e1;outline:none;-webkit-tap-highlight-color:transparent}
body.dark .leaflet-container{background:#2b2d31}
body.dark .base-pane{filter:brightness(0.72) saturate(0.8) contrast(1.05)}
#grid{position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:450;opacity:0;transition:opacity 280ms ease}
#grid.visible{opacity:1}
.city-dot{width:14px;height:14px;border-radius:50%;background:#1b6ef3;border:3px solid #fff;box-sizing:border-box;box-shadow:0 1px 4px rgba(0,0,0,.45)}
.leaflet-pane,.leaflet-tile,.leaflet-marker-icon,.leaflet-tile-container,.leaflet-pane>svg,.leaflet-pane>canvas,.leaflet-layer{position:absolute;left:0;top:0}
.leaflet-container{overflow:hidden;touch-action:none}
.leaflet-container img.leaflet-tile{max-width:none!important;max-height:none!important}
.leaflet-tile-pane{z-index:200}.leaflet-overlay-pane{z-index:400}.leaflet-marker-pane{z-index:600}
.leaflet-zoom-animated{transform-origin:0 0}
.leaflet-fade-anim .leaflet-tile{will-change:opacity}
.overlay-tiles{transition:opacity 320ms ease}
"""

private fun buildMapPage(context: android.content.Context): String? {
    val script = runCatching {
        context.assets.open("weather_map.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull() ?: return null
    return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\">" +
        "<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css\" onerror=\"this.onerror=null;this.href='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css'\">" +
        "<style>" + MAP_PAGE_STYLE + "</style>" +
                "</head><body><div id=\"map\"></div><canvas id=\"grid\"></canvas><script>" + script + "</script></body></html>"
}

private class MapBridge(
    private val onPointCallback: (Double, Double, Float, Float) -> Unit
) {
    @Volatile
    var active = true
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPoint(latitude: Double, longitude: Double, x: Double, y: Double) {
        if (!latitude.isFinite() || !longitude.isFinite() || !x.isFinite() || !y.isFinite()) return
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        if (x < 0.0 || y < 0.0 || x > 10000.0 || y > 10000.0) return
        handler.post { if (active) onPointCallback(latitude, longitude, x.toFloat(), y.toFloat()) }
    }
}

private suspend fun WebView.evalString(script: String): String? = suspendCancellableCoroutine { continuation ->
    post {
        evaluateJavascript(script) { raw ->
            if (continuation.isActive) continuation.resume(raw)
        }
    }
}

private fun WebView.runScript(script: String) {
    post { evaluateJavascript(script, null) }
}

private fun fireCollection(bytes: ByteArray?): String? {
    if (bytes == null) return null
    val cells = JSONArray(bytes.toString(Charsets.UTF_8))
    val features = JSONArray()
    for (i in 0 until cells.length()) {
        val cell = cells.optJSONArray(i) ?: continue
        if (cell.length() < 3) continue
        val lat = cell.optDouble(0, Double.NaN)
        val lon = cell.optDouble(1, Double.NaN)
        if (!lat.isFinite() || !lon.isFinite()) continue
        val geometry = JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lon + 0.125).put(lat + 0.125))
        val count = cell.optInt(2)
        val properties = JSONObject().put("count", count).put("radius", (3.0 + kotlin.math.sqrt(count.toDouble())).coerceIn(3.0, 10.0))
        features.put(JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", properties))
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

@Composable
private fun NativeMapLegend(layer: MapLayer, modifier: Modifier) {
    val spec = remember(layer.id) { nativeLegendSpec(layer.id) } ?: return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.widthIn(min = 144.dp).padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(tr(layer.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(spec.colors))
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(spec.minimum, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(spec.maximum, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class NativeLegendSpec(val minimum: String, val maximum: String, val colors: List<Color>)

private fun nativeLegendSpec(layerId: String): NativeLegendSpec? = when (layerId) {
    "precip" -> NativeLegendSpec("15 dBZ", "65 dBZ", listOf(Color(0xFF00DFF2), Color(0xFF287BFF), Color(0xFF8655D8), Color(0xFFED5BAF), Color.White))
    "temp" -> NativeLegendSpec("−30 °C", "+50 °C", listOf(Color(0xFF7A30B0), Color(0xFF3056E2), Color(0xFF22C4E2), Color(0xFF48C668), Color(0xFFFADE34), Color(0xFFFA8C1C), Color(0xFFE23428), Color(0xFF96183C)))
    "clouds" -> NativeLegendSpec("0%", "100%", listOf(Color(0x00E6F2FC), Color(0xD9E6F2FC)))
    "wind" -> NativeLegendSpec("0", "140 km/h", listOf(Color(0xFF2A6FDB), Color(0xFF26C6DA), Color(0xFF66BB6A), Color(0xFFFDD835), Color(0xFFFB8C00), Color(0xFFD32F2F), Color(0xFF8E24AA)))
    "pressure" -> NativeLegendSpec("980 hPa", "1045 hPa", listOf(Color(0xFF7B3294), Color(0xFF3B6FD4), Color(0xFF4FC3F7), Color(0xFFE0E0E0), Color(0xFFFFB74D), Color(0xFFF4511E), Color(0xFFB71C1C)))
    "humidity" -> NativeLegendSpec("0%", "100%", listOf(Color(0xFFEBF5FF), Color(0xFF80CBC4), Color(0xFF26A69A), Color(0xFF1E88E5), Color(0xFF1565C0), Color(0xFF0D47A1)))
    "dew" -> NativeLegendSpec("−20 °C", "+30 °C", listOf(Color(0xFF6E3CAA), Color(0xFF3C64D2), Color(0xFF3CBED2), Color(0xFF5AC86E), Color(0xFFF0DC46), Color(0xFFFA9628), Color(0xFFE63C32), Color(0xFF96143C)))
    "uv" -> NativeLegendSpec("0", "14", listOf(Color(0xFF43A047), Color(0xFFFADC34), Color(0xFFFB8C00), Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF5A1482)))
    "aqi" -> NativeLegendSpec("0", "300 US AQI", listOf(Color(0xFF00E400), Color(0xFFC8DC00), Color(0xFFFF7E00), Color(0xFFFF0000), Color(0xFF99004C), Color(0xFF7E0023)))
    "sea" -> NativeLegendSpec("−2 m", "+2 m", listOf(Color(0xFF2166AC), Color(0xFF67A9CF), Color(0xFFF7F7F7), Color(0xFFFDAE61), Color(0xFFD73027)))
    else -> null
}
