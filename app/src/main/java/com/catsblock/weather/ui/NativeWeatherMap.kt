package com.catsblock.weather.ui

import android.os.Bundle
import android.content.ComponentCallbacks2
import android.content.res.Configuration
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import com.catsblock.weather.data.RadarParticlePoint
import com.catsblock.weather.data.RadarProbe
import com.catsblock.weather.data.RainViewerPalette
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.catsblock.weather.data.City
import com.catsblock.weather.i18n.tr
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.maplibre.android.annotations.MarkerOptions
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.annotations.Marker
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.File

@Composable
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
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val mapScope = rememberCoroutineScope()
    val currentOnPointSelected by rememberUpdatedState(onPointSelected)
    val currentOnMapReady by rememberUpdatedState(onMapReady)
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var cityMarker by remember { mutableStateOf<Marker?>(null) }
    var overlayManager by remember { mutableStateOf<NativeWeatherOverlayManager?>(null) }
    var memoryCallbacks by remember { mutableStateOf<ComponentCallbacks2?>(null) }
    var gridOverlay by remember { mutableStateOf<NativeWeatherGridOverlay?>(null) }
    var featureOverlay by remember { mutableStateOf<NativeWeatherFeatureOverlay?>(null) }
    var rainParticles by remember { mutableStateOf<List<RadarParticlePoint>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var centeredCityId by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            MapLibre.getInstance(viewContext)
            val cacheDirectory = File(viewContext.cacheDir, "native-map-http").apply { mkdirs() }
            val client = OkHttpClient.Builder()
                .cache(Cache(cacheDirectory, 96L * 1024L * 1024L))
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "CatsBlockWeather/2.0 (Android; https://github.com/catsblock/CatsBlock-Weather-v2)")
                        .build()
                    val response = chain.proceed(request)
                    if (response.isSuccessful && request.url.host == "tile.openstreetmap.org" && response.header("Cache-Control") == null) {
                        response.newBuilder().header("Cache-Control", "public, max-age=604800").build()
                    } else {
                        response
                    }
                }
                .build()
            HttpRequestUtil.setOkHttpClient(client)
            MapView(viewContext).also { nativeView ->
                nativeView.onCreate(Bundle())
                mapView = nativeView
                val callback = object : ComponentCallbacks2 {
                    override fun onTrimMemory(level: Int) {
                        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) nativeView.onLowMemory()
                    }
                    override fun onLowMemory() = nativeView.onLowMemory()
                    override fun onConfigurationChanged(newConfig: Configuration) = Unit
                }
                memoryCallbacks = callback
                viewContext.registerComponentCallbacks(callback)
                nativeView.getMapAsync { nativeMap ->
                    map = nativeMap
                    nativeMap.uiSettings.isAttributionEnabled = false
                    nativeMap.uiSettings.isCompassEnabled = true
                    nativeMap.uiSettings.isRotateGesturesEnabled = true
                    nativeMap.setStyle(Style.Builder().fromJson(baseStyleJson())) { style ->
                        loaded = true
                        currentOnMapReady(true)
                        nativeMap.addOnMapClickListener { coordinate ->
                            val point = nativeMap.projection.toScreenLocation(coordinate)
                            currentOnPointSelected(
                                coordinate.latitude,
                                coordinate.longitude,
                                point.x / density.density,
                                point.y / density.density
                            )
                            true
                        }
                        applyMapAppearance(style, dark)
                        overlayManager = NativeWeatherOverlayManager(style, mapScope)
                        gridOverlay = NativeWeatherGridOverlay(style, mapScope)
                        featureOverlay = NativeWeatherFeatureOverlay(style, mapScope, viewContext.applicationContext)
                        applyWeatherOverlay(overlayManager, layer, radarTileTemplate)
                        (initialCenter ?: city?.let { LatLng(it.lat, it.lon) })?.let { target ->
                            nativeMap.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                                .target(target)
                                .zoom(initialZoom)
                                .build()
                            centeredCityId = city?.id
                        }
                    }
                }
            }
        },
        update = { nativeView ->
            if (mapView !== nativeView) mapView = nativeView
            map?.style?.let { style ->
                applyMapAppearance(style, dark)
                if (overlayManager == null) overlayManager = NativeWeatherOverlayManager(style, mapScope)
                if (gridOverlay == null) gridOverlay = NativeWeatherGridOverlay(style, mapScope)
                if (featureOverlay == null) featureOverlay = NativeWeatherFeatureOverlay(style, mapScope, nativeView.context.applicationContext)
                applyWeatherOverlay(overlayManager, layer, radarTileTemplate)
            }
        }
    )

    DisposableEffect(mapView, lifecycleOwner) {
        val nativeView = mapView
        if (nativeView == null) {
            onDispose { }
        } else {
            val observer = object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    nativeView.onStart()
                }

                override fun onResume(owner: LifecycleOwner) {
                    nativeView.onResume()
                }

                override fun onPause(owner: LifecycleOwner) {
                    nativeView.onPause()
                }

                override fun onStop(owner: LifecycleOwner) {
                    nativeView.onStop()
                }

            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) nativeView.onStart()
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) nativeView.onResume()
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                memoryCallbacks?.let { nativeView.context.unregisterComponentCallbacks(it) }
                memoryCallbacks = null
                overlayManager?.close()
                overlayManager = null
                gridOverlay?.close()
                gridOverlay = null
                featureOverlay?.close()
                featureOverlay = null
                cityMarker = null
                nativeView.onPause()
                nativeView.onStop()
                nativeView.onDestroy()
                map = null
                mapView = null
                loaded = false
                currentOnMapReady(false)
            }
        }
    }

    LaunchedEffect(city?.id, map, loaded) {
        val nativeMap = map ?: return@LaunchedEffect
        cityMarker?.let(nativeMap::removeMarker)
        cityMarker = null
        val selectedCity = city ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        cityMarker = nativeMap.addMarker(
            MarkerOptions()
                .position(LatLng(selectedCity.lat, selectedCity.lon))
                .title(selectedCity.name)
        )
        if (centeredCityId == selectedCity.id) return@LaunchedEffect
        nativeMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(selectedCity.lat, selectedCity.lon), 6.0),
            850
        )
        centeredCityId = selectedCity.id
    }

    LaunchedEffect(focusRequest, city, map, loaded) {
        if (focusRequest == 0 || !loaded) return@LaunchedEffect
        val selectedCity = city ?: return@LaunchedEffect
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(selectedCity.lat, selectedCity.lon), 7.0), 700)
    }

    val gridLayer = layer.id.takeIf { it in setOf("temp", "clouds", "wind", "pressure", "humidity", "dew", "uv", "aqi", "sea") }
    val gridTimeKey = if (gridLayer == null) 0L else selectedTimeMillis
    LaunchedEffect(gridLayer, gridTimeKey, map, loaded) {
        if (!loaded) return@LaunchedEffect
        val nativeMap = map ?: return@LaunchedEffect
        val manager = gridOverlay ?: nativeMap.style?.let { NativeWeatherGridOverlay(it, mapScope).also { created -> gridOverlay = created } }
            ?: return@LaunchedEffect
        manager.show(gridLayer, selectedTimeMillis)
    }

    val featureLayer = layer.id.takeIf { it in setOf("cyclones", "sst", "fires", "alerts", "drought") }
    LaunchedEffect(featureLayer, map, loaded) {
        if (!loaded) return@LaunchedEffect
        val nativeMap = map ?: return@LaunchedEffect
        var manager = featureOverlay
        if (manager == null) {
            val style = nativeMap.style ?: return@LaunchedEffect
            val context = mapView?.context?.applicationContext ?: return@LaunchedEffect
            manager = NativeWeatherFeatureOverlay(style, mapScope, context)
            featureOverlay = manager
        }
        manager.show(featureLayer)
    }

    LaunchedEffect(layer, selectedTimeMillis, loaded) {
        if (!loaded || layer != MapLayer.Precipitation) {
            rainParticles = emptyList()
            return@LaunchedEffect
        }
        rainParticles = RadarProbe.sampleParticles(selectedTimeMillis / 1000L)
    }

    Box(modifier = modifier) {
        if (layer == MapLayer.Precipitation && rainParticles.isNotEmpty()) {
            RainParticleOverlay(map, rainParticles, Modifier.matchParentSize())
        }
        NativeMapLegend(layer, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
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

@Composable
private fun RainParticleOverlay(map: MapLibreMap?, particles: List<RadarParticlePoint>, modifier: Modifier) {
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(map, particles) {
        if (map == null || particles.isEmpty()) {
            frameNanos = 0L
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { frameNanos = it }
            kotlinx.coroutines.delay(32L)
        }
    }
    Canvas(modifier = modifier) {
        val nativeMap = map ?: return@Canvas
        if (frameNanos == 0L) return@Canvas
        val elapsedSeconds = frameNanos / 1_000_000_000.0
        particles.forEachIndexed { index, particle ->
            val strength = ((particle.dbz - 15) / 50f).coerceIn(0f, 1f)
            val phase = (elapsedSeconds * 0.8 + index * 0.61803398875).rem(1.0)
            val travelDegrees = 0.08 + strength * 0.18
            val headLatitude = (particle.latitude - phase * travelDegrees).coerceIn(-85.0, 85.0)
            val tailLatitude = (headLatitude + travelDegrees * 0.18).coerceIn(-85.0, 85.0)
            val head = nativeMap.projection.toScreenLocation(LatLng(headLatitude, particle.longitude))
            if (head.x !in 0f..size.width || head.y !in 0f..size.height) return@forEachIndexed
            val tail = nativeMap.projection.toScreenLocation(LatLng(tailLatitude, particle.longitude))
            val color = Color(RainViewerPalette.particleColorForDbz(particle.dbz)).copy(alpha = 0.42f + strength * 0.48f)
            drawLine(
                color = color,
                start = Offset(tail.x, tail.y),
                end = Offset(head.x, head.y),
                strokeWidth = 1.1f + strength * 1.4f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun applyWeatherOverlay(manager: NativeWeatherOverlayManager?, layer: MapLayer, tileTemplate: String?) {
    val template = tileTemplate.takeIf { layer == MapLayer.Precipitation && !it.isNullOrBlank() }
    manager?.setOverlay(
        NativeWeatherOverlay.Precipitation,
        template?.let { NativeWeatherTileSource(it, "RainViewer", minZoom = 0, maxZoom = 7, opacity = 0.82f) }
    )
}

private fun applyMapAppearance(style: Style, dark: Boolean) {
    val layer = style.getLayer("openstreetmap-raster") ?: return
    if (dark) {
        layer.setProperties(
            PropertyFactory.rasterBrightnessMin(0.18f),
            PropertyFactory.rasterBrightnessMax(0.76f),
            PropertyFactory.rasterSaturation(-0.2f)
        )
    } else {
        layer.setProperties(
            PropertyFactory.rasterBrightnessMin(0f),
            PropertyFactory.rasterBrightnessMax(1f),
            PropertyFactory.rasterSaturation(0f)
        )
    }
}

private fun baseStyleJson(): String {
    val source = JSONObject()
        .put("type", "raster")
        .put("tiles", JSONArray().put("https://tile.openstreetmap.org/{z}/{x}/{y}.png"))
        .put("tileSize", 256)
        .put("maxzoom", 19)
        .put("attribution", "© OpenStreetMap contributors")
    val layer = JSONObject()
        .put("id", "openstreetmap-raster")
        .put("type", "raster")
        .put("source", "openstreetmap")
    return JSONObject()
        .put("version", 8)
        .put("sources", JSONObject().put("openstreetmap", source))
        .put("layers", JSONArray().put(layer))
        .toString()
}
