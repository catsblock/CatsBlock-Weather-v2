package com.catsblock.weather.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.catsblock.weather.data.City
import com.catsblock.weather.data.MapStyle
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.components.ExpressiveLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

internal enum class RadarDataState { Loading, Ready, Unavailable }

internal enum class BaseMapState { Loading, Ready, Failed }

@Stable
internal class RadarMapController {
    var host by mutableStateOf("")
    var frames by mutableStateOf<List<RadarFrame>>(emptyList())
    var index by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
    var radarState by mutableStateOf(RadarDataState.Loading)
    var baseState by mutableStateOf(BaseMapState.Loading)
    var retry by mutableIntStateOf(0)

    val frame: RadarFrame? get() = frames.getOrNull(index)

    fun retryAll() {
        baseState = BaseMapState.Loading
        radarState = RadarDataState.Loading
        retry++
    }
}

internal const val TILE_SIZE = 256
private const val MIN_ZOOM = 2.5f
private const val MAX_ZOOM = 12f
private const val MIN_TILE_ZOOM = 2
private const val MAX_TILE_ZOOM = 12
private const val RADAR_MAX_TILE_ZOOM = 7
private const val FRAME_REFRESH_MILLIS = 300_000L
private const val FRAME_RETRY_MILLIS = 20_000L
private const val FRAME_STEP_MILLIS = 700L
private const val LOAD_TIMEOUT_MILLIS = 30_000L

internal data class TileWindow(val z: Int, val x0: Int, val x1: Int, val y0: Int, val y1: Int)

private fun lonToU(lon: Double) = (lon + 180.0) / 360.0

private fun latToV(lat: Double): Double {
    val radians = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    return (1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0
}

private fun uToLon(u: Double) = u * 360.0 - 180.0

private fun vToLat(v: Double) = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * v))))

private fun wrapU(u: Double): Double = ((u % 1.0) + 1.0) % 1.0

private fun baseKey(z: Int, x: Int, y: Int): Long = (z.toLong() shl 40) or (x.toLong() shl 20) or y.toLong()

private fun radarKey(time: Long, z: Int, x: Int, y: Int): Long =
    (time shl 20) or (z.toLong() shl 16) or (x.toLong() shl 8) or y.toLong()

@Stable
internal class MapViewport(initialLat: Double, initialLon: Double, initialZoom: Float) {
    var cx by mutableDoubleStateOf(lonToU(initialLon))
    var cy by mutableDoubleStateOf(latToV(initialLat))
    var zoom by mutableFloatStateOf(initialZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
    var width by mutableIntStateOf(0)
    var height by mutableIntStateOf(0)
    private var epoch = 0

    private fun world(): Double = TILE_SIZE * 2.0.pow(zoom.toDouble())

    fun interrupt() {
        epoch++
    }

    fun transform(px: Float, py: Float, dx: Float, dy: Float, scale: Float) {
        val oldWorld = world()
        val anchorU = cx + (px - width / 2.0) / oldWorld
        val anchorV = cy + (py - height / 2.0) / oldWorld
        zoom = (zoom + (ln(scale.toDouble()) / ln(2.0)).toFloat()).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val newWorld = world()
        cx = wrapU(anchorU - (px - width / 2.0) / newWorld - dx / newWorld)
        cy = (anchorV - (py - height / 2.0) / newWorld - dy / newWorld).coerceIn(0.0, 1.0)
    }

    suspend fun zoomBy(delta: Float, px: Float = width / 2f, py: Float = height / 2f) {
        val mine = ++epoch
        val start = zoom
        val target = (zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
        var last = start
        animate(start, target, animationSpec = tween(240, easing = FastOutSlowInEasing)) { value, _ ->
            if (epoch != mine) return@animate
            val factor = 2.0.pow((value - last).toDouble()).toFloat()
            last = value
            transform(px, py, 0f, 0f, factor)
        }
    }

    fun jumpTo(lat: Double, lon: Double, targetZoom: Float) {
        epoch++
        cx = lonToU(lon)
        cy = latToV(lat)
        zoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    suspend fun flyTo(lat: Double, lon: Double, targetZoom: Float) {
        val mine = ++epoch
        val startU = cx
        val startV = cy
        val startZoom = zoom
        var deltaU = lonToU(lon) - startU
        if (deltaU > 0.5) deltaU -= 1.0 else if (deltaU < -0.5) deltaU += 1.0
        val endV = latToV(lat)
        val endZoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        animate(0f, 1f, animationSpec = tween(650, easing = FastOutSlowInEasing)) { t, _ ->
            if (epoch != mine) return@animate
            cx = wrapU(startU + deltaU * t)
            cy = startV + (endV - startV) * t
            zoom = startZoom + (endZoom - startZoom) * t
        }
    }

    fun latLonAt(px: Float, py: Float): Pair<Double, Double> {
        val currentWorld = world()
        val u = cx + (px - width / 2.0) / currentWorld
        val v = (cy + (py - height / 2.0) / currentWorld).coerceIn(0.0, 1.0)
        return vToLat(v) to uToLon(wrapU(u))
    }

    fun screenX(lon: Double): Float {
        var delta = lonToU(lon) - cx
        if (delta > 0.5) delta -= 1.0 else if (delta < -0.5) delta += 1.0
        return (width / 2.0 + delta * world()).toFloat()
    }

    fun screenY(lat: Double): Float = (height / 2.0 + (latToV(lat) - cy) * world()).toFloat()

    fun baseZoomLevel(): Int = floor(zoom).toInt().coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)

    fun radarZoomLevel(): Int = baseZoomLevel().coerceAtMost(RADAR_MAX_TILE_ZOOM)

    fun window(z: Int, margin: Int): TileWindow? {
        if (width <= 0 || height <= 0) return null
        val currentWorld = world()
        val n = 1 shl z
        val halfU = width / 2.0 / currentWorld
        val halfV = height / 2.0 / currentWorld
        return TileWindow(
            z,
            floor((cx - halfU) * n).toInt() - margin,
            floor((cx + halfU) * n).toInt() + margin,
            (floor((cy - halfV) * n).toInt() - margin).coerceAtLeast(0),
            (floor((cy + halfV) * n).toInt() + margin).coerceAtMost(n - 1)
        )
    }

    inline fun forEachTile(z: Int, block: (tx: Int, ty: Int, left: Float, top: Float, size: Float) -> Unit) {
        val currentWorld = TILE_SIZE * 2.0.pow(zoom.toDouble())
        val n = 1 shl z
        val halfU = width / 2.0 / currentWorld
        val halfV = height / 2.0 / currentWorld
        val x0 = floor((cx - halfU) * n).toInt()
        val x1 = floor((cx + halfU) * n).toInt()
        val y0 = floor((cy - halfV) * n).toInt().coerceAtLeast(0)
        val y1 = floor((cy + halfV) * n).toInt().coerceAtMost(n - 1)
        val size = (currentWorld / n).toFloat()
        for (ty in y0..y1) {
            for (tx in x0..x1) {
                val left = (width / 2.0 + (tx.toDouble() / n - cx) * currentWorld).toFloat()
                val top = (height / 2.0 + (ty.toDouble() / n - cy) * currentWorld).toFloat()
                block(Math.floorMod(tx, n), ty, left, top, size)
            }
        }
    }
}

private class TileStore {
    val base = LruCache<Long, Bitmap>(192)
    val radar = LruCache<Long, Bitmap>(192)
    private val gate = Semaphore(8)
    var version by mutableIntStateOf(0)

    private fun ordered(window: TileWindow): List<Pair<Int, Int>> {
        val n = 1 shl window.z
        val centerX = (window.x0 + window.x1) / 2.0
        val centerY = (window.y0 + window.y1) / 2.0
        return buildList {
            for (ty in window.y0..window.y1) for (tx in window.x0..window.x1) add(Math.floorMod(tx, n) to ty)
        }.distinct().sortedBy { (tx, ty) ->
            val dx = tx - Math.floorMod(centerX.toInt(), n)
            val dy = ty - centerY
            dx * dx + dy * dy
        }
    }

    suspend fun ensureBase(window: TileWindow): Boolean = coroutineScope {
        val tiles = ordered(window)
        tiles.map { (tx, ty) ->
            async {
                val key = baseKey(window.z, tx, ty)
                if (base.get(key) != null) {
                    true
                } else {
                    gate.withPermit {
                        val bitmap = RadarMapRenderer.tile(
                            MapStyle.tileUrl(false, window.z, tx, ty),
                            MapStyle.fallbackUrl(window.z, tx, ty)
                        )
                        if (bitmap != null) {
                            base.put(key, bitmap)
                            version++
                        }
                        bitmap != null
                    }
                }
            }
        }.awaitAll().any { it }
    }

    suspend fun ensureRadar(host: String, frame: RadarFrame, window: TileWindow): Int = coroutineScope {
        ordered(window).map { (tx, ty) ->
            async {
                val key = radarKey(frame.time, window.z, tx, ty)
                if (radar.get(key) != null) {
                    1
                } else {
                    gate.withPermit {
                        val bitmap = RadarMapRenderer.tile(
                            "$host${frame.path}/256/${window.z}/$tx/$ty/2/0_0.png",
                            recolor = true
                        )
                        if (bitmap != null) {
                            radar.put(key, bitmap)
                            version++
                        }
                        if (bitmap != null) 1 else 0
                    }
                }
            }
        }.awaitAll().sum()
    }
}

@Composable
internal fun RadarTileMap(
    controller: RadarMapController,
    viewport: MapViewport,
    city: City?,
    showRadar: Boolean,
    dark: Boolean,
    focusRequest: Int,
    onPointSelected: (latitude: Double, longitude: Double, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    attributionBottomPadding: Dp = 6.dp
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val store = remember { TileStore() }
    val currentOnPoint by rememberUpdatedState(onPointSelected)
    var resumed by remember { mutableStateOf(true) }
    var centeredCity by remember { mutableStateOf<String?>(null) }
    val scheme = MaterialTheme.colorScheme

    val basePaint = remember(dark) {
        Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (dark) colorFilter = ColorMatrixColorFilter(ColorMatrix(MapStyle.DARK_LIFT))
        }
    }
    val radarPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 204 } }
    val dst = remember { RectF() }
    val src = remember { Rect() }

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                resumed = true
            }

            override fun onStop(owner: LifecycleOwner) {
                resumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(city?.id) {
        val selected = city ?: return@LaunchedEffect
        if (centeredCity == null) {
            viewport.jumpTo(selected.lat, selected.lon, maxOf(viewport.zoom, 6f))
        } else if (centeredCity != selected.id) {
            viewport.flyTo(selected.lat, selected.lon, viewport.zoom)
        }
        centeredCity = selected.id
    }

    LaunchedEffect(focusRequest) {
        val selected = city ?: return@LaunchedEffect
        if (focusRequest > 0) viewport.flyTo(selected.lat, selected.lon, 8f)
    }

    LaunchedEffect(viewport, store, controller.retry) {
        snapshotFlow { viewport.window(viewport.baseZoomLevel(), 1) }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { window ->
                val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) { store.ensureBase(window) } ?: false
                when {
                    loaded -> controller.baseState = BaseMapState.Ready
                    store.base.size() == 0 -> controller.baseState = BaseMapState.Failed
                }
            }
    }

    LaunchedEffect(showRadar, controller.retry) {
        if (!showRadar) {
            controller.playing = false
            return@LaunchedEffect
        }
        if (controller.frames.isEmpty()) controller.radarState = RadarDataState.Loading
        while (isActive) {
            val set = RadarLoader.load()
            if (set == null) {
                if (controller.frames.isEmpty()) controller.radarState = RadarDataState.Unavailable
                delay(FRAME_RETRY_MILLIS)
            } else {
                val previousTime = controller.frame?.time
                controller.host = set.host
                controller.frames = set.frames
                controller.index = set.frames.indexOfFirst { it.time == previousTime }.takeIf { it >= 0 }
                    ?: set.frames.indexOfLast { it.observed }.coerceAtLeast(0)
                delay(FRAME_REFRESH_MILLIS)
            }
        }
    }

    LaunchedEffect(showRadar, controller.frame?.time, controller.host, controller.retry, store) {
        if (!showRadar) return@LaunchedEffect
        val frame = controller.frame ?: return@LaunchedEffect
        if (controller.host.isEmpty()) return@LaunchedEffect
        snapshotFlow { viewport.window(viewport.radarZoomLevel(), 0) }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { window ->
                val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) { store.ensureRadar(controller.host, frame, window) }
                controller.radarState = if (loaded == null || loaded > 0) RadarDataState.Ready else RadarDataState.Unavailable
            }
    }

    LaunchedEffect(showRadar, controller.playing, controller.frames.size, resumed) {
        if (!showRadar || !controller.playing || !resumed || controller.frames.size < 2) return@LaunchedEffect
        while (isActive) {
            val next = (controller.index + 1) % controller.frames.size
            val frame = controller.frames[next]
            val window = viewport.window(viewport.radarZoomLevel(), 0)
            if (window != null) withTimeoutOrNull(FRAME_STEP_MILLIS * 6) { store.ensureRadar(controller.host, frame, window) }
            delay(FRAME_STEP_MILLIS)
            controller.index = next
        }
    }

    Box(modifier.background(if (dark) Color(MapStyle.DARK_BASE) else Color(MapStyle.LIGHT_BASE))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    viewport.width = it.width
                    viewport.height = it.height
                }
                .pointerInput(viewport) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        viewport.interrupt()
                        viewport.transform(centroid.x, centroid.y, pan.x, pan.y, zoomChange)
                    }
                }
                .pointerInput(viewport) {
                    detectTapGestures(
                        onDoubleTap = { offset -> scope.launch { viewport.zoomBy(1f, offset.x, offset.y) } },
                        onTap = { offset ->
                            val (latitude, longitude) = viewport.latLonAt(offset.x, offset.y)
                            currentOnPoint(latitude, longitude, offset.x / density, offset.y / density)
                        }
                    )
                }
        ) {
            store.version
            drawMapLayers(viewport, store, controller, showRadar, basePaint, radarPaint, dst, src)
            if (city != null) drawCityMarker(viewport.screenX(city.lon), viewport.screenY(city.lat), density)
        }

        if (controller.baseState == BaseMapState.Loading) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainerHigh.copy(alpha = 0.95f),
                shadowElevation = 6.dp
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExpressiveLoader(size = 28.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("Loading map…"), style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        if (controller.baseState == BaseMapState.Failed) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
                color = scheme.surfaceContainerHigh,
                shadowElevation = 6.dp
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        tr("Could not load the map. Check your connection and try again."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )
                    TextButton(onClick = { controller.retryAll() }) { Text(tr("Retry")) }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = attributionBottomPadding)
                .background(scheme.surface.copy(alpha = 0.86f), RoundedCornerShape(topStart = 8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "© OpenStreetMap contributors",
                modifier = Modifier.clickable { runCatching { uriHandler.openUri("https://www.openstreetmap.org/copyright") } },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            if (showRadar) {
                Text(" · ", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text(
                    "RainViewer",
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri("https://www.rainviewer.com/") } },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun DrawScope.drawMapLayers(
    viewport: MapViewport,
    store: TileStore,
    controller: RadarMapController,
    showRadar: Boolean,
    basePaint: Paint,
    radarPaint: Paint,
    dst: RectF,
    src: Rect
) {
    val canvas = drawContext.canvas.nativeCanvas
    val baseZ = viewport.baseZoomLevel()
    viewport.forEachTile(baseZ) { tx, ty, left, top, size ->
        dst.set(left, top, left + size + 0.5f, top + size + 0.5f)
        val bitmap = store.base.get(baseKey(baseZ, tx, ty))
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, dst, basePaint)
        } else {
            var k = 1
            while (k <= 3 && baseZ - k >= MIN_TILE_ZOOM) {
                val ancestor = store.base.get(baseKey(baseZ - k, tx shr k, ty shr k))
                if (ancestor != null) {
                    val part = TILE_SIZE shr k
                    val sx = (tx and ((1 shl k) - 1)) * part
                    val sy = (ty and ((1 shl k) - 1)) * part
                    src.set(sx, sy, sx + part, sy + part)
                    canvas.drawBitmap(ancestor, src, dst, basePaint)
                    break
                }
                k++
            }
        }
    }
    val frame = controller.frame
    if (showRadar && frame != null) {
        val radarZ = viewport.radarZoomLevel()
        viewport.forEachTile(radarZ) { tx, ty, left, top, size ->
            val bitmap = store.radar.get(radarKey(frame.time, radarZ, tx, ty))
            if (bitmap != null) {
                dst.set(left, top, left + size + 0.5f, top + size + 0.5f)
                canvas.drawBitmap(bitmap, null, dst, radarPaint)
            }
        }
    }
}

private fun DrawScope.drawCityMarker(x: Float, y: Float, density: Float) {
    if (x < -40f || y < -40f || x > size.width + 40f || y > size.height + 40f) return
    val center = Offset(x, y)
    drawCircle(Color(0x33000000), 8f * density, Offset(x, y + 0.8f * density))
    drawCircle(Color.White, 6.5f * density, center)
    drawCircle(Color(0xFF1E88E5), 4.5f * density, center)
}
