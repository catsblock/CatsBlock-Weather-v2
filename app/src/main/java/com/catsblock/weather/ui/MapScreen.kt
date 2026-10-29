package com.catsblock.weather.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.FireHotspots
import com.catsblock.weather.data.ThemeMode
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.components.ExpressiveLoader
import com.catsblock.weather.ui.components.ExpressiveProgressBar
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream

private val CycloneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Cyclone",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 12f)
            curveTo(12f, 10.5f, 13.5f, 10f, 14.5f, 11f)
            curveTo(16f, 12.5f, 14.5f, 15.5f, 12f, 15.5f)
            curveTo(8.8f, 15.5f, 7f, 12.5f, 8f, 9.5f)
            curveTo(9.2f, 6f, 14f, 5f, 17f, 7.5f)
            curveTo(20.5f, 10.5f, 19.5f, 16f, 15.5f, 18.8f)
        }
    }.build()
}

internal enum class MapLayer(val id: String, val label: String, val icon: ImageVector) {
    None("none", "None (Default)", Icons.Rounded.LayersClear),
    Precipitation("precip", "Precipitation", Icons.Rounded.Umbrella),
    Temperature("temp", "Temperature", Icons.Rounded.Thermostat),
    Clouds("clouds", "Cloudiness", Icons.Rounded.Cloud),
    Wind("wind", "Wind speed", Icons.Rounded.Air),
    Pressure("pressure", "Pressure", Icons.Rounded.Speed),
    Cyclones("cyclones", "Hurricanes & cyclones", CycloneIcon),
    OceanHeat("sst", "El Niño & ocean heat", Icons.Rounded.Waves),
    Fires("fires", "Active fires", Icons.Rounded.LocalFireDepartment)
}

@SuppressLint("SetJavaScriptEnabled")
internal object MapHost {
    private var view: WebView? = null

    private var appContext: Context? = null

    var progress by mutableIntStateOf(0)
        private set

    var ready by mutableStateOf(false)
        private set

    var failed by mutableStateOf(false)
        private set

    var layer by mutableStateOf(MapLayer.None)

    var cityKey: String? = null

    fun obtain(context: Context, background: Int, init: String): WebView {
        view?.let { return it }
        appContext = context.applicationContext
        val web = WebView(context.applicationContext)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.cacheMode = WebSettings.LOAD_DEFAULT
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.setBackgroundColor(background)
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(webView: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString().orEmpty()
                if (!url.endsWith("__fires.json")) return null
                val data = appContext?.let { FireHotspots.json(it) }
                val headers = mapOf("Access-Control-Allow-Origin" to "*")
                return if (data != null) {
                    WebResourceResponse("application/json", "utf-8", 200, "OK", headers, ByteArrayInputStream(data))
                } else {
                    WebResourceResponse("application/json", "utf-8", 503, "Unavailable", headers, ByteArrayInputStream(ByteArray(0)))
                }
            }

            override fun onPageFinished(webView: WebView?, url: String?) {
                webView?.let { probe(it, 0) }
            }

            override fun onReceivedError(webView: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    ready = false
                    failed = true
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                Log.d("WeatherMap", message?.message().orEmpty())
                return true
            }

            override fun onProgressChanged(webView: WebView?, newProgress: Int) {
                progress = newProgress
            }
        }
        view = web
        pageInit = init
        load(web)
        return web
    }

    private var pageInit = "{}"

    private fun probe(web: WebView, attempt: Int) {
        web.evaluateJavascript("typeof window.MapApi") { result ->
            val ok = result?.contains("object") == true
            when {
                ok -> {
                    ready = true
                    failed = false
                }
                attempt < 40 -> web.postDelayed({ probe(web, attempt + 1) }, 250)
                else -> {
                    ready = false
                    failed = true
                }
            }
        }
    }

    private fun load(web: WebView) {
        ready = false
        failed = false
        progress = 0
        cityKey = null
        web.loadDataWithBaseURL(MapPage.BASE_URL, MapPage.page(pageInit), "text/html", "utf-8", null)
    }

    fun reload() {
        view?.let { load(it) }
    }

    fun run(script: String) {
        if (ready) view?.evaluateJavascript(script, null)
    }

    fun release(frame: FrameLayout) {
        val web = frame.getChildAt(0) as? WebView ?: return
        if (web.parent === frame) {
            frame.removeView(web)
            web.onPause()
        }
    }
}

private fun mapLabels(): JSONObject = JSONObject()
    .put("td", tr("Tropical depression"))
    .put("ts", tr("Tropical storm"))
    .put("c1", tr("Category 1"))
    .put("c2", tr("Category 2"))
    .put("c3", tr("Category 3"))
    .put("c4", tr("Category 4"))
    .put("c5", tr("Category 5"))
    .put("winds", tr("Max winds"))
    .put("fire", tr("Wildfire"))
    .put("hotspots", tr("Hotspots"))
    .put("none", tr("No active storms right now"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: WeatherViewModel) {
    val city by vm.city.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val surface = MaterialTheme.colorScheme.surface.toArgb()
    val layer = MapHost.layer
    val ready = MapHost.ready
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = LocalBottomInset.current
    val scope = rememberCoroutineScope()
    val initJson = remember {
        val target = city
        JSONObject()
            .put("dark", dark)
            .put("top", topInset.value.toDouble())
            .put("bottom", bottomInset.value.toDouble())
            .put("hasCity", target != null)
            .put("lat", target?.lat ?: 0.0)
            .put("lon", target?.lon ?: 0.0)
            .put("layer", layer.id)
            .put("title", tr(layer.label))
            .put("err", tr("Map data unavailable"))
            .put("f", settings.fahrenheit)
            .put("labels", mapLabels())
            .toString()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sheetOpen by remember { mutableStateOf(false) }
    val locate = remember { Animatable(0f) }

    LaunchedEffect(ready, dark) {
        if (ready) MapHost.run("MapApi.setTheme(" + dark + ")")
    }
    LaunchedEffect(ready, topInset, bottomInset) {
        if (ready) MapHost.run("MapApi.setInsets(" + topInset.value + "," + bottomInset.value + ")")
    }
    LaunchedEffect(ready, city?.id) {
        val target = city
        if (ready && target != null && MapHost.cityKey != target.id) {
            val animate = MapHost.cityKey != null
            MapHost.cityKey = target.id
            MapHost.run("MapApi.setCity(" + target.lat + "," + target.lon + "," + animate + ")")
        }
    }
    LaunchedEffect(ready, layer, settings.fahrenheit, settings.language) {
        if (ready) {
            MapHost.run(
                "MapApi.setLayer(" + JSONObject.quote(layer.id) + "," + JSONObject.quote(tr(layer.label)) + "," +
                    JSONObject.quote(tr("Map data unavailable")) + "," + settings.fahrenheit + "," + mapLabels() + ")"
            )
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val web = MapHost.obtain(ctx, surface, initJson)
                (web.parent as? ViewGroup)?.removeView(web)
                FrameLayout(ctx).also { frame ->
                    frame.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    web.onResume()
                }
            },
            onRelease = { MapHost.release(it) }
        )

        if (MapHost.progress < 100 && !MapHost.failed) {
            ExpressiveProgressBar(
                progress = { MapHost.progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = topInset)
            )
        }

        AnimatedVisibility(
            visible = !ready && !MapHost.failed,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f)
        ) {
            StatusPill {
                ExpressiveLoader(size = 28.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr("Loading map…"), style = MaterialTheme.typography.titleSmall)
            }
        }

        AnimatedVisibility(
            visible = MapHost.failed,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f)
        ) {
            StatusPill {
                Text(tr("Map data unavailable"), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = MapHost::reload) { Text(tr("Retry")) }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = bottomInset + 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            val rotation by animateFloatAsState(
                if (sheetOpen) 180f else 0f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
                label = "layers"
            )
            FloatingActionButton(
                onClick = { sheetOpen = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    Icons.Rounded.Layers,
                    tr("Map layers"),
                    Modifier.graphicsLayer { rotationY = rotation }
                )
            }
            SmallFloatingActionButton(
                onClick = {
                    city?.let {
                        MapHost.run("MapApi.locate()")
                        scope.launch {
                            locate.snapTo(0f)
                            locate.animateTo(1f, tween(700))
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = CircleShape,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    Icons.Rounded.MyLocation,
                    tr("My location"),
                    Modifier.graphicsLayer {
                        val t = locate.value
                        val s = 1f + 0.3f * kotlin.math.sin(t * Math.PI).toFloat()
                        scaleX = s
                        scaleY = s
                        rotationZ = 90f * t
                    }
                )
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    tr("Map layers"),
                    Modifier.padding(start = 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                MapLayer.entries.forEach { item ->
                    LayerRow(item, item == layer) {
                        MapHost.layer = item
                        scope.launch {
                            sheetState.hide()
                            sheetOpen = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun LayerRow(item: MapLayer, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (selected) 32.dp else 18.dp, spring(0.7f, Spring.StiffnessMediumLow), label = "corner")
    val container by animateColorAsState(if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh, tween(260), label = "container")
    val badge by animateColorAsState(if (selected) scheme.secondary else scheme.surfaceContainerHighest, tween(260), label = "badge")
    val badgeInk by animateColorAsState(if (selected) scheme.onSecondary else scheme.onSurfaceVariant, tween(260), label = "badgeInk")
    val pop by animateFloatAsState(if (selected) 1f else 0.9f, spring(0.4f, Spring.StiffnessMedium), label = "pop")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                }
                .clip(CircleShape)
                .background(badge),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, Modifier.size(22.dp), tint = badgeInk)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            tr(item.label),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface
        )
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(0.45f, Spring.StiffnessMedium)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Icon(Icons.Rounded.Check, null, Modifier.size(24.dp), tint = scheme.onSecondaryContainer)
        }
    }
}
