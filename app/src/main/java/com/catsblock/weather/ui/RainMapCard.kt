package com.catsblock.weather.ui

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.catsblock.weather.data.City
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

private val RainMapBase = Color(0xFFE8E4DC)

private fun osmTileUrl(z: Int, x: Int, y: Int): String = "https://tile.openstreetmap.org/$z/$x/$y.png"

@Composable
private fun RainTileImage(url: String, modifier: Modifier, alpha: Float = 1f) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .addHeader("User-Agent", "CatsBlockWeather/2.0 (Android)")
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
        alpha = alpha
    )
}

@Composable
private fun RainTiles(city: City, zoom: Int, set: RadarSet?, frame: RadarFrame?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clipToBounds().background(RainMapBase)) {
        val n = 1 shl zoom
        val latR = Math.toRadians(city.lat)
        val tx = n * (city.lon + 180.0) / 360.0
        val ty = n * (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0
        val t = 256.dp
        val bx = floor(tx).toInt()
        val by = floor(ty).toInt()
        val rx = ceil(maxWidth.value / 512f).toInt()
        val ry = ceil(maxHeight.value / 512f).toInt()
        val cells = ArrayList<Triple<Int, Int, Int>>()
        for (dy in -ry..ry) {
            val y = by + dy
            if (y !in 0 until n) continue
            for (dx in -rx..rx) cells.add(Triple(bx + dx, y, Math.floorMod(bx + dx, n)))
        }
        cells.forEach { (x, y, xx) ->
            RainTileImage(
                osmTileUrl(zoom, xx, y),
                Modifier
                    .offset(maxWidth / 2 + t * (x - tx).toFloat(), maxHeight / 2 + t * (y - ty).toFloat())
                    .size(t)
            )
        }
        if (set != null && frame != null) {
            cells.forEach { (x, y, xx) ->
                RainTileImage(
                    "${set.host}${frame.path}/256/$zoom/$xx/$y/2/1_1.png",
                    Modifier
                        .offset(maxWidth / 2 + t * (x - tx).toFloat(), maxHeight / 2 + t * (y - ty).toFloat())
                        .size(t),
                    0.8f
                )
            }
        }
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
    var zoom by remember { mutableIntStateOf(6) }
    val shape = RoundedCornerShape(if (fullscreen) 0.dp else 20.dp)
    val open = if (fullscreen) Modifier else Modifier.clickable(onClick = onFullscreen)
    Box(modifier.clip(shape).then(open)) {
        RainTiles(city, zoom, set, frame, Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.Center).size(12.dp).clip(CircleShape).background(Color.White)
                .border(2.dp, Color(0xFF1E88E5), CircleShape)
        )
        Text(
            "© OpenStreetMap · RainViewer",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color(0x80000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White,
            fontSize = 9.sp
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
                RainMapButton(Icons.Rounded.Add, zoom < 8, { zoom += 1 })
                RainMapButton(Icons.Rounded.Remove, zoom > 4, { zoom -= 1 })
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
