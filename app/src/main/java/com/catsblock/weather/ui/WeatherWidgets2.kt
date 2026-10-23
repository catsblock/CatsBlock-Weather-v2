package com.catsblock.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.catsblock.weather.data.CardId
import com.catsblock.weather.data.CardShape
import com.catsblock.weather.data.City
import com.catsblock.weather.data.Day
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.data.aqiStatus
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.data.humidityLabel
import com.catsblock.weather.data.precipToday
import com.catsblock.weather.data.uvLevel
import com.catsblock.weather.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

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
fun RadarCard(city: City, offsetSeconds: Int, modifier: Modifier = Modifier) {
    val set by produceState<RadarSet?>(null) { value = RadarLoader.load() }
    val frames = set?.frames.orEmpty()
    var index by remember(set) { mutableIntStateOf((frames.size - 1).coerceAtLeast(0)) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing, set) {
        val n = frames.size
        while (playing && n > 1) {
            delay(750)
            index = if (index >= n - 1) 0 else index + 1
        }
    }
    val frame = frames.getOrNull(index)
    val last = frames.lastOrNull()
    val mapShape = RoundedCornerShape(18.dp)
    GlassCard(modifier, null, Modifier.fillMaxSize()) {
        CardHeader(Icons.Rounded.Map, tr("Rain radar"))
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).clip(mapShape)
                .background(Color(0xFF0E1013)).border(1.dp, Color.White.copy(alpha = 0.22f), mapShape)
        ) {
            val z = 6
            val n = 1 shl z
            val latR = Math.toRadians(city.lat)
            val tx = n * (city.lon + 180.0) / 360.0
            val ty = n * (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0
            val t = 220.dp
            val bx = floor(tx).toInt()
            val by = floor(ty).toInt()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val x = bx + dx
                    val y = by + dy
                    if (y in 0 until n) {
                        val xx = ((x % n) + n) % n
                        val pos = Modifier
                            .offset(maxWidth / 2 + t * (x - tx).toFloat(), maxHeight / 2 + t * (y - ty).toFloat())
                            .size(t)
                        AsyncImage(
                            model = "https://basemaps.cartocdn.com/dark_nolabels/$z/$xx/$y.png",
                            contentDescription = null,
                            modifier = pos,
                            contentScale = ContentScale.FillBounds
                        )
                        val s = set
                        if (s != null && frame != null) {
                            AsyncImage(
                                model = "${s.host}${frame.path}/256/$z/$xx/$y/2/1_1.png",
                                contentDescription = null,
                                modifier = pos.alpha(0.85f),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                    }
                }
            }
            Box(
                Modifier.align(Alignment.Center).size(11.dp).clip(CircleShape).background(Color.White)
                    .border(2.dp, Color(0xFF4FC3F7), CircleShape)
            )
            if (frame != null) {
                val clock = clockLabel(LocalDateTime.ofEpochSecond(frame.time, 0, ZoneOffset.ofTotalSeconds(offsetSeconds)))
                Text(
                    clock,
                    Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playing = !playing }, enabled = frames.size > 1) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
                }
                Slider(
                    value = index.toFloat(),
                    onValueChange = { index = it.roundToInt() },
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
                val mins = if (frame != null && last != null) ((last.time - frame.time) / 60L).toInt() else 0
                Text(
                    if (mins <= 0) tr("Now") else if (mins < 60) "-${mins}m" else "-${mins / 60}h ${mins % 60}m",
                    Modifier.width(54.dp).padding(end = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SunCardV2(sunrise: LocalDateTime, sunset: LocalDateTime, now: LocalDateTime, modifier: Modifier = Modifier) {
    val total = Duration.between(sunrise, sunset).toMinutes().coerceAtLeast(1)
    val elapsed = Duration.between(sunrise, now).toMinutes()
    val target = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val up = !now.isBefore(sunrise) && !now.isAfter(sunset)
    val progress by animateFloatAsState(target, tween(1100), label = "sun")
    val ink = MaterialTheme.colorScheme.onSurface
    GlassCard(modifier, null, Modifier.fillMaxSize()) {
        CardHeader(Icons.Rounded.WbTwilight, tr("Sun"))
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.fillMaxWidth().height(78.dp)) {
            val r = min(size.width / 2f - 18.dp.toPx(), size.height - 20.dp.toPx())
            val cx = size.width / 2f
            val cy = size.height - 8.dp.toPx()
            fun p(a: Double) = Offset(cx + r * cos(a).toFloat(), cy - r * sin(a).toFloat())
            drawLine(ink.copy(alpha = 0.3f), Offset(cx - r - 14.dp.toPx(), cy), Offset(cx + r + 14.dp.toPx(), cy), 1.5f.dp.toPx())
            drawArc(
                ink.copy(alpha = 0.3f), 180f, 180f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            )
            drawArc(
                Color.White.copy(alpha = 0.4f), 180f, 180f * progress, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r),
                style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round)
            )
            val a = PI * (1.0 - progress)
            val step = Math.toRadians(2.4)
            for (k in 0 until 16) {
                val fade = (1f - k / 16f) * 0.95f
                for (dir in intArrayOf(-1, 1)) {
                    val a1 = a + dir * step * k
                    val a2 = a + dir * step * (k + 1)
                    if (a1 in 0.0..PI && a2 in 0.0..PI) {
                        drawLine(Color.White.copy(alpha = fade), p(a1), p(a2), 3.4f.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
            val s = p(a)
            val glow = if (up) 1f else 0.45f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.85f * glow), Color.White.copy(alpha = 0.25f * glow), Color.Transparent),
                    center = s,
                    radius = 36.dp.toPx()
                ),
                36.dp.toPx(), s
            )
            for (i in 0 until 16) {
                val ang = Math.toRadians(i * 22.5)
                val inner = 11.dp.toPx()
                val outer = if (i % 2 == 0) 22.dp.toPx() else 16.dp.toPx()
                drawLine(
                    Color.White.copy(alpha = 0.9f * glow),
                    Offset(s.x + inner * cos(ang).toFloat(), s.y + inner * sin(ang).toFloat()),
                    Offset(s.x + outer * cos(ang).toFloat(), s.y + outer * sin(ang).toFloat()),
                    2.dp.toPx(), StrokeCap.Round
                )
            }
            drawCircle(Color.White.copy(alpha = glow), 8.dp.toPx(), s)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clockLabel(sunrise), style = MaterialTheme.typography.labelLarge)
            Text(clockLabel(sunset), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GradientBarCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    status: String?,
    text: String,
    fraction: Float,
    colors: List<Color>,
    modifier: Modifier,
    onClick: (() -> Unit)?
) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val f by animateFloatAsState(if (go) fraction.coerceIn(0f, 1f) else 0f, tween(1000), label = "bar")
    GlassCard(modifier, onClick, Modifier.fillMaxSize()) {
        CardHeader(icon, title)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 46.sp), maxLines = 1)
            if (status != null) {
                Spacer(Modifier.width(8.dp))
                Text(status, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val h = 7.dp.toPx()
            val top = (size.height - h) / 2f
            drawRoundRect(
                Brush.horizontalGradient(colors),
                Offset(0f, top), Size(size.width, h),
                androidx.compose.ui.geometry.CornerRadius(h / 2f)
            )
            val x = (size.width * f).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx(), StrokeCap.Round)
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, 1.dp.toPx()), Offset(x, size.height - 1.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
fun UvCardV2(uv: Double, description: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    GradientBarCard(
        tr("UV index"), Icons.Rounded.WbSunny, uv.toInt().toString(), uvLevel(uv), description, (uv / 11.0).toFloat(),
        listOf(Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFF44336), Color(0xFF9C27B0)),
        modifier, onClick
    )
}

@Composable
fun AirCardV2(aqi: Double?, description: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    GradientBarCard(
        tr("Air quality"), Icons.Rounded.Eco, aqi?.toInt()?.toString() ?: "--", aqi?.let { aqiStatus(it) }, description,
        ((aqi ?: 0.0) / 300.0).toFloat(),
        listOf(Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFF44336), Color(0xFF8F3F97), Color(0xFF7E0023)),
        modifier, onClick
    )
}

@Composable
fun WindCardV2(speedKmh: Double, fromDeg: Double, description: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val going = ((fromDeg + 180.0) % 360.0).toFloat()
    val rot by animateFloatAsState(going, tween(900), label = "windRot")
    val ink = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val horizontal = LocalCardShape.current == CardShape.Horizontal
    val compass: @Composable (Modifier) -> Unit = { m ->
        Canvas(m) {
            val r = min(size.width, size.height) / 2f - 4.dp.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(ink.copy(alpha = 0.1f), r, c)
            drawCircle(ink.copy(alpha = 0.55f), r, c, style = Stroke(1.5f.dp.toPx()))
            for (i in 0 until 36) {
                val a = Math.toRadians(i * 10.0 - 90.0)
                val major = i % 9 == 0
                val len = if (major) 7.dp.toPx() else if (i % 3 == 0) 5.dp.toPx() else 3.dp.toPx()
                drawLine(
                    ink.copy(alpha = if (major) 0.9f else 0.45f),
                    Offset(c.x + cos(a).toFloat() * (r - len), c.y + sin(a).toFloat() * (r - len)),
                    Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r),
                    1.2f.dp.toPx()
                )
            }
            val names = listOf("N", "E", "S", "W")
            for (i in 0 until 4) {
                val a = Math.toRadians(i * 90.0 - 90.0)
                val rr = r - 17.dp.toPx()
                val m = measurer.measure(names[i], labelStyle.copy(color = if (i == 0) Color(0xFFFF6B6B) else ink.copy(alpha = 0.9f)))
                drawText(
                    m,
                    topLeft = Offset(c.x + cos(a).toFloat() * rr - m.size.width / 2f, c.y + sin(a).toFloat() * rr - m.size.height / 2f)
                )
            }
            rotate(rot, c) {
                val path = Path().apply {
                    moveTo(c.x, c.y - r * 0.66f)
                    lineTo(c.x - r * 0.15f, c.y - r * 0.22f)
                    lineTo(c.x + r * 0.15f, c.y - r * 0.22f)
                    close()
                }
                drawPath(path, Color.White, style = Fill)
                drawLine(Color.White, Offset(c.x, c.y - r * 0.22f), Offset(c.x, c.y + r * 0.4f), 3.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(Color.White, 4.dp.toPx(), c)
        }
    }
    val info: @Composable ColumnScope.() -> Unit = {
        Text("${speedKmh.toInt()} km/h", style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(
            "${tr("Blowing to")} ${compassLabel(going.toDouble())}  ·  Bft ${beaufort(speedKmh)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
        if (horizontal) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
    }
    GlassCard(modifier, onClick, Modifier.fillMaxSize()) {
        CardHeader(Icons.Rounded.Air, tr("Wind"))
        Spacer(Modifier.height(6.dp))
        if (horizontal) {
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                compass(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = info)
            }
        } else {
            compass(Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            info()
        }
    }
}

@Composable
fun PressureCardV2(hpa: Double, description: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val lo = 960.0
    val hi = 1060.0
    val target = ((hpa - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val f by animateFloatAsState(if (go) target else 0f, tween(1200), label = "gauge")
    val ink = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.titleLarge.copy(color = ink, fontWeight = FontWeight.SemiBold)
    val unitStyle = MaterialTheme.typography.labelSmall.copy(color = ink.copy(alpha = 0.7f))
    val tickStyle = MaterialTheme.typography.labelSmall.copy(color = ink.copy(alpha = 0.75f), fontSize = 8.sp)
    GlassCard(modifier, onClick, Modifier.fillMaxSize()) {
        CardHeader(Icons.Rounded.Speed, tr("Pressure"))
        Canvas(Modifier.weight(1f).fillMaxWidth()) {
            val r = min(size.width, size.height) / 2f - 6.dp.toPx()
            val c = Offset(size.width / 2f, size.height / 2f + 4.dp.toPx())
            val tl = Offset(c.x - r, c.y - r)
            val sz = Size(2 * r, 2 * r)
            drawArc(ink.copy(alpha = 0.18f), 135f, 270f, false, tl, sz, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(
                    0f to Color(0xFF4FC3F7), 0.25f to Color(0xFF66BB6A), 0.5f to Color(0xFFFFEE58), 0.75f to Color(0xFFFF7043), 1f to Color(0xFF4FC3F7),
                    center = c
                ),
                135f, 270f * f, false, tl, sz, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
            )
            for (i in 0..50) {
                val a = Math.toRadians(135.0 + 270.0 * i / 50.0)
                val major = i % 5 == 0
                val len = if (major) 8.dp.toPx() else 4.dp.toPx()
                val outer = r - 11.dp.toPx()
                drawLine(
                    ink.copy(alpha = if (major) 0.9f else 0.4f),
                    Offset(c.x + cos(a).toFloat() * (outer - len), c.y + sin(a).toFloat() * (outer - len)),
                    Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                    if (major) 1.6f.dp.toPx() else 1f.dp.toPx()
                )
            }
            for (i in intArrayOf(0, 25, 50)) {
                val a = Math.toRadians(135.0 + 270.0 * i / 50.0)
                val rr = r - 30.dp.toPx()
                val m = measurer.measure((lo + (hi - lo) * i / 50.0).toInt().toString(), tickStyle)
                drawText(m, topLeft = Offset(c.x + cos(a).toFloat() * rr - m.size.width / 2f, c.y + sin(a).toFloat() * rr - m.size.height / 2f))
            }
            val na = Math.toRadians(135.0 + 270.0 * f)
            val needle = Offset(c.x + cos(na).toFloat() * (r - 16.dp.toPx()), c.y + sin(na).toFloat() * (r - 16.dp.toPx()))
            drawLine(Color(0xFFFF5252), c, needle, 3.dp.toPx(), StrokeCap.Round)
            drawCircle(Color.White, 6.dp.toPx(), c)
            drawCircle(Color(0xFFFF5252), 3.dp.toPx(), c)
            val vm = measurer.measure(hpa.roundToInt().toString(), valueStyle)
            drawText(vm, topLeft = Offset(c.x - vm.size.width / 2f, c.y + r * 0.34f))
            val um = measurer.measure("hPa", unitStyle)
            drawText(um, topLeft = Offset(c.x - um.size.width / 2f, c.y + r * 0.34f + vm.size.height))
        }
        Text(description, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CardsDashboard(bundle: WeatherBundle, city: City?, day: Day, onOpen: (String) -> Unit) {
    val cur = bundle.current
    val km = cur.visibility / 1000.0
    val cards = buildMap<CardId, @Composable () -> Unit> {
        if (city != null) {
            put(CardId.Radar) { RadarCard(city, bundle.utcOffsetSeconds, Modifier.fillMaxSize()) }
            put(CardId.Moon) { MoonCard(city, bundle.utcOffsetSeconds, cur.now, Modifier.fillMaxSize()) { onOpen("Moon") } }
        }
        put(CardId.Precip) {
            MetricCard(
                title = tr("Precipitation"),
                icon = Icons.Rounded.Umbrella,
                value = f1(day.precipSum),
                unit = "mm",
                description = precipToday(day.precipSum),
                modifier = Modifier.fillMaxSize(),
                onClick = { onOpen("Precip") }
            )
        }
        put(CardId.Wind) { WindCardV2(cur.windSpeed, cur.windDir, windShort(cur.windSpeed), Modifier.fillMaxSize()) { onOpen("Wind") } }
        put(CardId.Air) {
            AirCardV2(
                cur.aqi,
                cur.aqi?.let { aqiShort(it) } ?: tr("Air quality data is not available here."),
                Modifier.fillMaxSize(),
                if (cur.aqi != null) ({ onOpen("Air") }) else null
            )
        }
        put(CardId.Uv) { UvCardV2(cur.uv, uvShort(cur.uv), Modifier.fillMaxSize()) { onOpen("Uv") } }
        put(CardId.Humidity) {
            MetricCard(
                title = tr("Humidity"),
                icon = Icons.Rounded.WaterDrop,
                value = "${cur.humidity}%",
                unit = null,
                description = humidityLabel(cur.humidity),
                modifier = Modifier.fillMaxSize(),
                onClick = { onOpen("Humidity") }
            )
        }
        put(CardId.Sun) { SunCardV2(day.sunrise, day.sunset, cur.now, Modifier.fillMaxSize()) }
        put(CardId.Pressure) { PressureCardV2(cur.pressure, pressureShort(cur.pressure), Modifier.fillMaxSize()) { onOpen("Pressure") } }
        put(CardId.Visibility) {
            MetricCard(
                title = tr("Visibility"),
                icon = Icons.Rounded.Visibility,
                value = if (km >= 10.0) km.roundToInt().toString() else f1(km),
                unit = "km",
                description = visibilityShort(km),
                modifier = Modifier.fillMaxSize(),
                onClick = { onOpen("Visibility") }
            )
        }
    }
    CardGrid(cards)
}
