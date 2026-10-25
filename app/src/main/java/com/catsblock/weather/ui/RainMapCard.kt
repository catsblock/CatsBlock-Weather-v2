package com.catsblock.weather.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.catsblock.weather.data.City
import com.catsblock.weather.data.MapStyle
import com.catsblock.weather.i18n.tr
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class RadarFrame(val time: Long, val path: String)

data class RadarSet(val host: String, val frames: List<RadarFrame>)

object RadarLoader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun load(): RadarSet? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url("https://api.rainviewer.com/public/weather-maps.json").build())
                .execute()
                .use { r ->
                    if (!r.isSuccessful) return@use null
                    val j = JSONObject(r.body?.string() ?: return@use null)
                    val past = j.getJSONObject("radar").getJSONArray("past")
                    val frames = (0 until past.length()).map {
                        val o = past.getJSONObject(it)
                        RadarFrame(o.getLong("time"), o.getString("path"))
                    }
                    val horizon = (frames.lastOrNull()?.time ?: 0L) - 8 * 3600L
                    RadarSet(j.getString("host"), frames.filter { it.time >= horizon })
                }
        }.getOrNull()
    }
}

@Composable
internal fun rememberDarkMap(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

private object RadarMapRenderer {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val cache = LruCache<String, Bitmap>(96)

    private fun fetch(url: String): Bitmap? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", "CatsBlockWeather/2.0 (Android)").build())
            .execute()
            .use { r ->
                if (!r.isSuccessful) null
                else r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
    }.getOrNull()

    private suspend fun tile(url: String, fallback: String? = null): Bitmap? {
        cache.get(url)?.let { return it }
        val bmp = withContext(Dispatchers.IO) { fetch(url) ?: fallback?.let { fetch(it) } }
        if (bmp != null) cache.put(url, bmp)
        return bmp
    }

    suspend fun render(city: City, zoom: Int, w: Int, h: Int, set: RadarSet?, frame: RadarFrame?, dark: Boolean): Bitmap = coroutineScope {
        val size = 256
        val n = 1 shl zoom
        val latR = Math.toRadians(city.lat.coerceIn(-85.0, 85.0))
        val tx = n * (city.lon + 180.0) / 360.0
        val ty = n * (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0
        val bx = floor(tx).toInt()
        val by = floor(ty).toInt()
        val rx = ceil(w / 2f / size).toInt() + 1
        val ry = ceil(h / 2f / size).toInt() + 1
        val cells = ArrayList<Triple<Int, Int, Int>>()
        for (dy in -ry..ry) {
            val y = by + dy
            if (y !in 0 until n) continue
            for (dx in -rx..rx) cells.add(Triple(bx + dx, y, Math.floorMod(bx + dx, n)))
        }
        val base = cells.map { c -> async { c to tile(MapStyle.tileUrl(dark, zoom, c.third, c.second), MapStyle.fallbackUrl(zoom, c.third, c.second)) } }
        val radar = if (set != null && frame != null && zoom <= 7) {
            cells.map { c -> async { c to tile("${set.host}${frame.path}/256/$zoom/${c.third}/${c.second}/2/1_1.png") } }
        } else {
            emptyList()
        }
        val baseTiles = base.awaitAll()
        val radarTiles = radar.awaitAll()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(out)
        canvas.drawColor(if (dark) MapStyle.DARK_BASE else MapStyle.LIGHT_BASE)
        val basePaint = if (dark) {
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(MapStyle.DARK_LIFT)) }
        } else {
            null
        }
        val radarPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 204 }
        fun draw(list: List<Pair<Triple<Int, Int, Int>, Bitmap?>>, paint: Paint?) {
            list.forEach { (c, bmp) ->
                if (bmp != null) {
                    val left = (w / 2f + size * (c.first - tx)).roundToInt()
                    val top = (h / 2f + size * (c.second - ty)).roundToInt()
                    canvas.drawBitmap(bmp, left.toFloat(), top.toFloat(), paint)
                }
            }
        }
        draw(baseTiles, basePaint)
        draw(radarTiles, radarPaint)
        out
    }
}

@Composable
private fun RainTiles(city: City, zoom: Int, set: RadarSet?, frame: RadarFrame?, dark: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clipToBounds().background(Color(if (dark) MapStyle.DARK_BASE else MapStyle.LIGHT_BASE))) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        var image by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(city, zoom, set, frame, w, h, dark) {
            if (w > 0 && h > 0) image = RadarMapRenderer.render(city, zoom, w, h, set, frame, dark).asImageBitmap()
        }
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}

@Composable
private fun RainMapButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

@Composable
private fun RainPanel(
    city: City,
    offsetSeconds: Int,
    set: RadarSet?,
    index: Int,
    onIndex: (Int) -> Unit,
    playing: Boolean,
    onPlaying: () -> Unit,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val frames = set?.frames.orEmpty()
    val frame = frames.getOrNull(index)
    val last = frames.lastOrNull()
    var zoom by remember { mutableIntStateOf(7) }
    val dark = rememberDarkMap()
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "pulseValue"
    )
    val shape = RoundedCornerShape(if (fullscreen) 0.dp else 18.dp)
    val open = if (fullscreen) Modifier else Modifier.clickable(onClick = onFullscreen)
    Box(modifier.clip(shape).then(open)) {
        RainTiles(city, zoom, set, frame, dark, Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.Center)
                .size(12.dp)
                .graphicsLayer {
                    val sc = 1f + pulse * 2.4f
                    scaleX = sc
                    scaleY = sc
                    alpha = 0.55f * (1f - pulse)
                }
                .clip(CircleShape)
                .background(Color(0xFF1E88E5))
        )
        Box(
            Modifier.align(Alignment.Center).size(12.dp).clip(CircleShape).background(Color.White)
                .border(2.dp, Color(0xFF1E88E5), CircleShape)
        )
        Text(
            MapStyle.CREDIT,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 52.dp),
            color = if (dark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.55f),
            fontSize = 8.sp
        )
        RainMapButton(
            if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            true,
            onFullscreen,
            Modifier.align(Alignment.TopEnd).padding(8.dp)
        )
        if (!fullscreen) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RainMapButton(Icons.Rounded.Add, zoom < 7, { zoom += 1 })
                RainMapButton(Icons.Rounded.Remove, zoom > 5, { zoom -= 1 })
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlaying, enabled = frames.size > 1) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
            }
            Slider(
                value = index.toFloat(),
                onValueChange = { onIndex(it.roundToInt()) },
                valueRange = 0f..max(1, frames.size - 1).toFloat(),
                enabled = frames.size > 1,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    disabledThumbColor = Color.White.copy(alpha = 0.4f),
                    disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
                    disabledInactiveTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )
            val clock = frame?.let {
                clockLabel(LocalDateTime.ofEpochSecond(it.time, 0, ZoneOffset.ofTotalSeconds(offsetSeconds)))
            } ?: "--:--"
            val mins = if (frame != null && last != null) ((last.time - frame.time) / 60L).toInt() else 0
            val rel = if (mins <= 0) tr("Now") else if (mins < 60) "-${mins}m" else "-${mins / 60}h ${mins % 60}m"
            Column(Modifier.width(62.dp).padding(end = 6.dp)) {
                Text(clock, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(rel, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
fun RadarCard(city: City, offsetSeconds: Int, modifier: Modifier = Modifier) {
    val set by produceState<RadarSet?>(null) { value = RadarLoader.load() }
    val frames = set?.frames.orEmpty()
    var index by remember(set) { mutableIntStateOf((frames.size - 1).coerceAtLeast(0)) }
    var playing by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    LaunchedEffect(playing, set) {
        val n = frames.size
        while (playing && n > 1) {
            delay(750)
            index = if (index >= n - 1) 0 else index + 1
        }
    }
    GlassCard(modifier, null, Modifier.fillMaxSize()) {
        CardHeader(Icons.Rounded.Map, tr("Rain radar"))
        RainPanel(
            city = city,
            offsetSeconds = offsetSeconds,
            set = set,
            index = index,
            onIndex = { index = it },
            playing = playing,
            onPlaying = { playing = !playing },
            fullscreen = false,
            onFullscreen = { full = true },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)
        )
    }
    if (full) {
        Dialog(
            onDismissRequest = { full = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
                RainPanel(
                    city = city,
                    offsetSeconds = offsetSeconds,
                    set = set,
                    index = index,
                    onIndex = { index = it },
                    playing = playing,
                    onPlaying = { playing = !playing },
                    fullscreen = true,
                    onFullscreen = { full = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
