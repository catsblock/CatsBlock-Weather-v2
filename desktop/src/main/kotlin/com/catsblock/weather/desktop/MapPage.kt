package com.catsblock.weather.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

private const val TILE = 256
private const val MIN_ZOOM = 3
private const val MAX_ZOOM = 10
private const val OVERLAY_MAX_ZOOM = 7

private fun lonToX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * TILE * 2.0.pow(zoom)

private fun latToY(lat: Double, zoom: Int): Double {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1.0 - ln(tan(r) + 1.0 / kotlin.math.cos(r)) / PI) / 2.0 * TILE * 2.0.pow(zoom)
}

private object TileLoader {
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build()
    private val gate = Semaphore(8)

    suspend fun load(url: String): ImageBitmap? = gate.withPermit {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "CatsBlockWeather-Desktop/2.0 (+https://github.com/catsblock/CatsBlock-Weather-v2)")
                    .GET().build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
                if (response.statusCode() != 200 || response.body().size > 2_000_000) null
                else org.jetbrains.skia.Image.makeFromEncoded(response.body()).toComposeImageBitmap()
            }.getOrNull()
        }
    }
}

private enum class MapLayer(val key: String) {
    Radar("Radar"),
    Satellite("Satellite"),
    Precipitation("Precipitation"),
    Wind("Wind speed");

    val isGrid: Boolean get() = this == Precipitation || this == Wind
}

private class GridPoint(val lat: Double, val lon: Double, val precipMm: Double, val windKmh: Double, val windFromDeg: Double)

private const val GRID_COLS = 8
private const val GRID_ROWS = 6
private const val MIN_PRECIP_MM = 0.05

private fun xToLon(x: Double, zoom: Int): Double = x / (TILE * 2.0.pow(zoom)) * 360.0 - 180.0

private fun yToLat(y: Double, zoom: Int): Double {
    val n = PI - 2.0 * PI * y / (TILE * 2.0.pow(zoom))
    return Math.toDegrees(atan(sinh(n)))
}

private fun precipColor(mm: Double): Color = when {
    mm < 0.5 -> Color(0xFF8FD3F4)
    mm < 2.0 -> Color(0xFF2F80ED)
    mm < 6.0 -> Color(0xFF27AE60)
    mm < 15.0 -> Color(0xFFF2C94C)
    mm < 30.0 -> Color(0xFFEB5757)
    else -> Color(0xFF9B51E0)
}

private fun windColor(kmh: Double): Color = when {
    kmh < 20.0 -> Color(0xFF2F80ED)
    kmh < 40.0 -> Color(0xFF27AE60)
    kmh < 60.0 -> Color(0xFFF2C94C)
    kmh < 90.0 -> Color(0xFFEB5757)
    else -> Color(0xFF9B51E0)
}

private object GridClient {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun load(coordinates: List<Pair<Double, Double>>): List<GridPoint>? = runCatching {
        val lats = coordinates.joinToString(",") { String.format(Locale.US, "%.3f", it.first.coerceIn(-89.0, 89.0)) }
        val lons = coordinates.joinToString(",") { String.format(Locale.US, "%.3f", ((it.second + 540.0) % 360.0) - 180.0) }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lats&longitude=$lons" +
            "&current=precipitation,wind_speed_10m,wind_direction_10m&wind_speed_unit=kmh&timezone=UTC"
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
            .header("User-Agent", "CatsBlockWeather-Desktop/2.0 (+https://github.com/catsblock/CatsBlock-Weather-v2)")
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200 || response.body().length > 1_000_000) return@runCatching null
        val trimmed = response.body().trim()
        val items: List<JSONObject> = if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            (0 until array.length()).map { array.getJSONObject(it) }
        } else {
            listOf(JSONObject(trimmed))
        }
        if (items.size != coordinates.size) return@runCatching null
        items.mapIndexed { index, item ->
            val current = item.getJSONObject("current")
            GridPoint(
                coordinates[index].first, coordinates[index].second,
                current.optDouble("precipitation", 0.0), current.optDouble("wind_speed_10m", 0.0),
                current.optDouble("wind_direction_10m", 0.0)
            )
        }
    }.getOrNull()
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun MapButton(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MapPage(s: DesktopSettings, dark: Boolean, modifier: Modifier = Modifier) {
    val city = s.city
    val scheme = MaterialTheme.colorScheme
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var zoom by remember { mutableIntStateOf(6) }
    var cx by remember(city?.id) { mutableDoubleStateOf(lonToX(city?.lon ?: 0.0, 6)) }
    var cy by remember(city?.id) { mutableDoubleStateOf(latToY(city?.lat ?: 0.0, 6)) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    var layer by remember { mutableStateOf(MapLayer.Radar) }
    var set by remember { mutableStateOf<RadarSet?>(null) }
    var failed by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf<List<GridPoint>>(emptyList()) }
    var gridFailed by remember { mutableStateOf(false) }
    var frame by remember(layer) { mutableIntStateOf(-1) }
    var playing by remember { mutableStateOf(false) }
    val tiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    val pending = remember { HashSet<String>() }

    fun changeZoom(delta: Int) {
        val next = (zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (next == zoom) return
        val factor = 2.0.pow(next - zoom)
        cx *= factor
        cy *= factor
        zoom = next
    }

    LaunchedEffect(Unit) {
        set = withContext(Dispatchers.IO) { RadarClient.load() }
        failed = set == null
    }

    val frames = when (layer) {
        MapLayer.Radar -> set?.radar.orEmpty()
        MapLayer.Satellite -> set?.satellite.orEmpty()
        MapLayer.Precipitation, MapLayer.Wind -> emptyList()
    }
    val activeIndex = if (frame in frames.indices) frame else frames.indexOfLast { it.time <= Instant.now().epochSecond }.coerceAtLeast(frames.lastIndex)

    LaunchedEffect(playing, frames.size) {
        while (playing && frames.isNotEmpty()) {
            delay(700)
            frame = ((if (frame in frames.indices) frame else activeIndex) + 1) % frames.size
        }
    }

    fun ensure(url: String) {
        if (tiles.containsKey(url) || !pending.add(url)) return
        scope.launch {
            val image = TileLoader.load(url)
            if (image != null) {
                if (tiles.size > 700) tiles.clear()
                tiles[url] = image
            }
            pending.remove(url)
        }
    }

    val n = 1 shl zoom
    val left = cx - size.width / 2.0
    val top = cy - size.height / 2.0
    val x0 = floor(left / TILE).toInt()
    val x1 = floor((left + size.width) / TILE).toInt()
    val y0 = max(0, floor(top / TILE).toInt())
    val y1 = min(n - 1, floor((top + size.height) / TILE).toInt())
    val overlayZoom = min(zoom, OVERLAY_MAX_ZOOM)
    val dz = zoom - overlayZoom
    val scale = 1 shl dz
    val current = frames.getOrNull(activeIndex)
    val host = set?.host

    val gridKey = if (layer.isGrid) "${layer.key}:$zoom:${(cx / 128.0).toInt()}:${(cy / 128.0).toInt()}:${size.width / 64}:${size.height / 64}" else ""
    LaunchedEffect(gridKey) {
        if (!layer.isGrid) return@LaunchedEffect
        delay(450)
        val coordinates = buildList {
            for (j in 0 until GRID_ROWS) for (i in 0 until GRID_COLS) {
                val px = left + (i + 0.5) / GRID_COLS * size.width
                val py = top + (j + 0.5) / GRID_ROWS * size.height
                add(yToLat(py, zoom) to xToLon(px, zoom))
            }
        }
        val loaded = withContext(Dispatchers.IO) { GridClient.load(coordinates) }
        if (loaded == null) gridFailed = true else {
            grid = loaded
            gridFailed = false
        }
    }

    fun baseUrl(x: Int, y: Int): String = "https://tile.openstreetmap.org/$zoom/${Math.floorMod(x, n)}/$y.png"
    fun overlayUrl(x: Int, y: Int): String? {
        if (layer.isGrid || current == null || host == null) return null
        val ox = Math.floorMod(x, n) shr dz
        val oy = y shr dz
        val mode = if (layer == MapLayer.Radar) "2/1_1" else "0/0_0"
        return "$host${current.path}/256/$overlayZoom/$ox/$oy/$mode.png"
    }

    for (ty in y0..y1) for (tx in x0..x1) {
        ensure(baseUrl(tx, ty))
        overlayUrl(tx, ty)?.let { ensure(it) }
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(CardShape).background(scheme.surfaceContainerHigh)) {
            Canvas(
                Modifier.fillMaxSize()
                    .onSizeChanged { size = IntSize(it.width.coerceAtLeast(1), it.height.coerceAtLeast(1)) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            cx -= drag.x
                            cy -= drag.y
                        }
                    }
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val dy = event.changes.first().scrollDelta.y
                        if (dy != 0f) changeZoom(if (dy < 0f) 1 else -1)
                    }
            ) {
                for (ty in y0..y1) for (tx in x0..x1) {
                    val dx = (tx * TILE - left).toInt()
                    val dy = (ty * TILE - top).toInt()
                    tiles[baseUrl(tx, ty)]?.let { drawImage(it, dstOffset = IntOffset(dx, dy), dstSize = IntSize(TILE, TILE)) }
                    val url = overlayUrl(tx, ty)
                    val image = if (url != null) tiles[url] else null
                    if (image != null) {
                        val sub = TILE / scale
                        val sx = (Math.floorMod(tx, n) and (scale - 1)) * sub
                        val sy = (ty and (scale - 1)) * sub
                        drawImage(
                            image, srcOffset = IntOffset(sx, sy), srcSize = IntSize(sub, sub),
                            dstOffset = IntOffset(dx, dy), dstSize = IntSize(TILE, TILE), alpha = 0.72f
                        )
                    }
                }
                if (layer.isGrid) {
                    val cellW = size.width.toFloat() / GRID_COLS
                    val cellH = size.height.toFloat() / GRID_ROWS
                    for (point in grid) {
                        val px = (lonToX(point.lon, zoom) - left).toFloat()
                        val py = (latToY(point.lat, zoom) - top).toFloat()
                        if (layer == MapLayer.Precipitation) {
                            if (point.precipMm >= MIN_PRECIP_MM) {
                                drawRect(
                                    precipColor(point.precipMm).copy(alpha = 0.55f),
                                    Offset(px - cellW / 2, py - cellH / 2), Size(cellW, cellH)
                                )
                            }
                        } else {
                            val length = (12f + (point.windKmh.coerceAtMost(100.0) / 100.0 * 24.0).toFloat())
                            val heading = Math.toRadians(point.windFromDeg + 180.0)
                            val dx = (sin(heading) * length).toFloat()
                            val dy = (-cos(heading) * length).toFloat()
                            val tail = Offset(px - dx / 2, py - dy / 2)
                            val tip = Offset(px + dx / 2, py + dy / 2)
                            val ink = windColor(point.windKmh)
                            drawLine(ink, tail, tip, strokeWidth = 3f)
                            for (side in listOf(-0.5, 0.5)) {
                                val wing = heading + PI + side
                                drawLine(
                                    ink, tip,
                                    Offset(tip.x + (sin(wing) * 8).toFloat(), tip.y - (cos(wing) * 8).toFloat()),
                                    strokeWidth = 3f
                                )
                            }
                        }
                    }
                }
                if (dark) drawRect(Color.Black.copy(alpha = 0.35f))
                if (city != null) {
                    val px = (lonToX(city.lon, zoom) - left).toFloat()
                    val py = (latToY(city.lat, zoom) - top).toFloat()
                    drawCircle(Color.White, 11f, Offset(px, py))
                    drawCircle(scheme.primary, 8f, Offset(px, py))
                    drawCircle(scheme.primary.copy(alpha = 0.35f), 20f, Offset(px, py), style = Stroke(3f))
                }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapButton("+") { changeZoom(1) }
                MapButton("−") { changeZoom(-1) }
                MapButton("◎") {
                    if (city != null) {
                        cx = lonToX(city.lon, zoom)
                        cy = latToY(city.lat, zoom)
                    }
                }
            }
            Text(
                if (layer.isGrid) "© OpenStreetMap · Open-Meteo" else "© OpenStreetMap · RainViewer", Modifier.align(Alignment.BottomStart).padding(8.dp),
                style = MaterialTheme.typography.labelSmall, color = if (dark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.7f)
            )
            if (failed || (layer.isGrid && gridFailed)) Text(t("Map data unavailable"), Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        Card(Modifier.fillMaxWidth(), t("Map layers")) {
            Segmented(MapLayer.entries.map { it to t(it.key) }, layer) { layer = it; playing = false }
            if (frames.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MapButton(if (playing) "❚❚" else "▶") { playing = !playing }
                    Slider(
                        value = activeIndex.toFloat(),
                        onValueChange = { playing = false; frame = it.toInt().coerceIn(0, frames.lastIndex) },
                        valueRange = 0f..max(1, frames.lastIndex).toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        current?.let { Instant.ofEpochSecond(it.time).atZone(ZoneId.systemDefault()).format(timeFormat) }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else if (layer.isGrid) {
                Text(
                    t(if (layer == MapLayer.Precipitation) "Precipitation (mm/h)" else "Wind (km/h)"),
                    color = scheme.onSurfaceVariant
                )
            } else if (!failed) Text(t("Loading map…"), color = scheme.onSurfaceVariant)
        }
    }
}
