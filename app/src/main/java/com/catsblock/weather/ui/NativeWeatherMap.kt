package com.catsblock.weather.ui

import android.annotation.SuppressLint
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
    radarTileTemplate: String?,
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
    var pageReady by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var layerState by remember { mutableStateOf("ready") }
    var retryRequest by remember { mutableIntStateOf(0) }
    var centeredCityId by remember { mutableStateOf<String?>(null) }
    val bridge = remember {
        MapBridge { latitude, longitude, x, y -> currentOnPointSelected(latitude, longitude, x, y) }
    }

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
                        if (request.isForMainFrame && bridge.active) {
                            failed = true
                            currentOnMapReady(true)
                        }
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        if (bridge.active) {
                            failed = true
                            currentOnMapReady(true)
                        }
                        return true
                    }
                }
                val html = buildMapPage(viewContext)
                if (html == null) {
                    failed = true
                    currentOnMapReady(true)
                } else {
                    view.loadDataWithBaseURL(MAP_BASE_URL, html, "text/html", "UTF-8", null)
                }
                webView = view
            }
        }
    )

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
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                bridge.active = false
                view.removeJavascriptInterface("CatsBlockBridge")
                view.stopLoading()
                view.webViewClient = WebViewClient()
                view.loadUrl("about:blank")
                view.removeAllViews()
                view.destroy()
                webView = null
                pageReady = false
                mapReady = false
                currentOnMapReady(false)
            }
        }
    }

    LaunchedEffect(webView, retryRequest) {
        val view = webView ?: return@LaunchedEffect
        while (bridge.active) {
            val raw = runCatching { view.evalString("window.CBStatus ? CBStatus() : ''") }.getOrNull()
            val json = raw?.takeIf { it.isNotEmpty() && it != "null" }?.let {
                runCatching { JSONObject(JSONObject("{\"v\":$it}").getString("v")) }.getOrNull()
            }
            if (json != null) {
                if (json.optBoolean("page") && !pageReady) pageReady = true
                if (json.optBoolean("ready") && !mapReady) {
                    mapReady = true
                    failed = false
                    currentOnMapReady(true)
                }
                val error = json.optString("error")
                if (error.isNotEmpty() && !mapReady) {
                    failed = true
                    currentOnMapReady(true)
                }
                val state = json.optString("layer")
                if (state == "loading" || state == "ready" || state == "error") layerState = state
            }
            delay(if (mapReady) 800L else 300L)
        }
    }

    LaunchedEffect(webView, retryRequest) {
        if (webView == null) return@LaunchedEffect
        delay(15000L)
        if (!mapReady && bridge.active) {
            failed = true
            currentOnMapReady(true)
        }
    }

    LaunchedEffect(pageReady, dark) {
        val view = webView ?: return@LaunchedEffect
        if (pageReady) view.runScript("CBMap.setDark($dark)")
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

    LaunchedEffect(layer.id, pageReady, retryRequest) {
        val view = webView ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        val id = layer.id
        if (retryRequest > 0) view.runScript("CBMap.reloadLayer(${JSONObject.quote(id)})") else view.runScript("CBMap.setLayer(${JSONObject.quote(id)})")
        if (id == "fires" || id == "alerts" || id == "drought") {
            val geoJson = runCatching {
                withContext(Dispatchers.IO) {
                    when (id) {
                        "fires" -> fireCollection(FireHotspots.json(view.context.applicationContext))
                        "alerts" -> AlertFeed.json()?.toString(Charsets.UTF_8)
                        else -> DroughtFeed.json()?.toString(Charsets.UTF_8)
                    }
                }
            }.getOrNull()
            if (currentLayer.id != id) return@LaunchedEffect
            if (geoJson == null) view.runScript("CBMap.featuresFailed(${JSONObject.quote(id)})")
            else view.runScript("CBMap.setFeatures(${JSONObject.quote(id)}, ${JSONObject.quote(geoJson)})")
        }
    }

    Box(modifier = modifier) {
        NativeMapLegend(layer, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
        if (failed || layerState == "error") {
            Surface(
                modifier = Modifier.align(Alignment.Center).clickable {
                    if (failed) {
                        failed = false
                        mapReady = false
                        currentOnMapReady(false)
                        pageReady = false
                        webView?.let { view ->
                            val html = buildMapPage(view.context)
                            if (html != null) view.loadDataWithBaseURL(MAP_BASE_URL, html, "text/html", "UTF-8", null)
                        }
                    }
                    layerState = "loading"
                    retryRequest++
                },
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
        if (showAttribution) {
            val sourceAttribution = when (layer.id) {
                "precip" -> "RainViewer" to "https://www.rainviewer.com/"
                "drought" -> "U.S. Drought Monitor" to "https://droughtmonitor.unl.edu/"
                "temp", "clouds", "wind", "pressure", "humidity", "dew", "uv", "aqi", "sea" -> "Open-Meteo" to "https://open-meteo.com/"
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

private const val MAP_BASE_URL = "https://github.com/catsblock/CatsBlock-Weather-v2/"

private const val MAP_PAGE_STYLE = """
html,body,#map{margin:0;padding:0;width:100%;height:100%;background:#e8e6e1;overflow:hidden}
.leaflet-container{background:#e8e6e1;outline:none;-webkit-tap-highlight-color:transparent}
body.dark .leaflet-container{background:#2b2d31}
body.dark .base-pane{filter:brightness(0.72) saturate(0.8) contrast(1.05)}
#grid{position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:450;opacity:0;transition:opacity 280ms ease}
#grid.visible{opacity:1}
.city-dot{width:14px;height:14px;border-radius:50%;background:#1b6ef3;border:3px solid #fff;box-sizing:border-box;box-shadow:0 1px 4px rgba(0,0,0,.45)}
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

private suspend fun WebView.evalString(script: String): String? = kotlin.coroutines.suspendCoroutine { continuation ->
    post {
        evaluateJavascript(script) { raw ->
            continuation.resumeWith(Result.success(raw))
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
