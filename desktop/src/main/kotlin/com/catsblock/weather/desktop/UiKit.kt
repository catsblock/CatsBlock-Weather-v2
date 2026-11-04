package com.catsblock.weather.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

val CardShape = RoundedCornerShape(4.dp)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1565C0), onPrimary = Color.White, secondary = Color(0xFF00838F), tertiary = Color(0xFFB45309),
    background = Color(0xFFF4F8FF), onBackground = Color(0xFF111827), surface = Color(0xFFF9FBFF), onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE1E8F5), onSurfaceVariant = Color(0xFF475569), outline = Color(0xFF8A97AD),
    outlineVariant = Color(0xFFCBD5E5), surfaceContainer = Color(0xFFEAF1FC), surfaceContainerHigh = Color(0xFFE1EAF8)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8EC5FF), onPrimary = Color(0xFF00315C), secondary = Color(0xFF72D8E4), tertiary = Color(0xFFFFC46B),
    background = Color(0xFF0B1220), onBackground = Color(0xFFE5EAF3), surface = Color(0xFF0F1828), onSurface = Color(0xFFE5EAF3),
    surfaceVariant = Color(0xFF1B2638), onSurfaceVariant = Color(0xFFA9B4C7), outline = Color(0xFF66738A),
    outlineVariant = Color(0xFF2A364B), surfaceContainer = Color(0xFF141F32), surfaceContainerHigh = Color(0xFF1B2840)
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

@Composable
fun Card(modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    val scheme: ColorScheme = MaterialTheme.colorScheme
    Column(
        modifier
            .background(scheme.surfaceContainer, CardShape)
            .border(1.dp, scheme.outlineVariant, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        content()
    }
}

@Composable
fun Choice(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().border(1.dp, scheme.outlineVariant, CardShape)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).background(if (on) scheme.primary else Color.Transparent)
                    .clickable { onSelect(value) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) scheme.onPrimary else scheme.onSurface, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

enum class Glyph { Settings, Refresh, Plus, Close, Check, Back, Pin }

@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        when (glyph) {
            Glyph.Plus -> { drawLine(tint, p(12f, 5f), p(12f, 19f), 2f * u, StrokeCap.Round); drawLine(tint, p(5f, 12f), p(19f, 12f), 2f * u, StrokeCap.Round) }
            Glyph.Close -> { drawLine(tint, p(6f, 6f), p(18f, 18f), 2f * u, StrokeCap.Round); drawLine(tint, p(18f, 6f), p(6f, 18f), 2f * u, StrokeCap.Round) }
            Glyph.Check -> { drawLine(tint, p(5f, 13f), p(10f, 18f), 2f * u, StrokeCap.Round); drawLine(tint, p(10f, 18f), p(19f, 7f), 2f * u, StrokeCap.Round) }
            Glyph.Back -> { drawLine(tint, p(15f, 5f), p(8f, 12f), 2f * u, StrokeCap.Round); drawLine(tint, p(8f, 12f), p(15f, 19f), 2f * u, StrokeCap.Round) }
            Glyph.Refresh -> {
                drawArc(tint, 40f, 290f, false, p(4f, 4f), Size(16f * u, 16f * u), style = stroke)
                val head = Path().apply { moveTo(15f * u, 1.5f * u); lineTo(21f * u, 6f * u); lineTo(14f * u, 8.5f * u); close() }
                drawPath(head, tint)
            }
            Glyph.Settings -> {
                drawCircle(tint, 3f * u, p(12f, 12f), style = stroke)
                drawCircle(tint, 7f * u, p(12f, 12f), style = stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = kotlin.math.cos(a).toFloat()
                    val s = kotlin.math.sin(a).toFloat()
                    drawLine(tint, p(12f + 7f * c, 12f + 7f * s), p(12f + 10f * c, 12f + 10f * s), 2.4f * u, StrokeCap.Round)
                }
            }
            Glyph.Pin -> {
                drawCircle(tint, 4f * u, p(12f, 9f), style = stroke)
                drawLine(tint, p(12f, 13f), p(12f, 21f), 2f * u, StrokeCap.Round)
            }
        }
    }
}

object IconCache {
    private val dir = File(AppPaths.dir, "icons")
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build()

    fun bytes(url: String): ByteArray? {
        val uri = URI.create(url)
        if (uri.scheme != "https" || uri.host != "raw.githubusercontent.com") return null
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
        val cached = File(dir, "$name.svg")
        if (cached.exists()) return runCatching { cached.readBytes() }.getOrNull()
        return runCatching {
            val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent", "CatsBlockWeather-Desktop").GET().build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
            val body = response.body()
            if (response.statusCode() != 200 || body.size > 300_000) null else {
                dir.mkdirs()
                cached.writeBytes(body)
                body
            }
        }.getOrNull()
    }
}

private const val ICON_SET_5 = "https://raw.githubusercontent.com/mrdarrengriffin/google-weather-icons/refs/heads/main/sets/set-5/light/"
private const val ICON_SET_6 = "https://raw.githubusercontent.com/mrdarrengriffin/google-weather-icons/refs/heads/main/sets/set-6/light/"

fun iconUrl(kind: Kind, isDay: Boolean): String = when (kind) {
    Kind.Clear -> ICON_SET_6 + if (isDay) "sunny.svg" else "clear_night.svg"
    Kind.FewClouds -> if (isDay) ICON_SET_5 + "mostly_sunny.svg" else ICON_SET_6 + "mostly_clear_night.svg"
    Kind.MoreClouds -> if (isDay) ICON_SET_5 + "partly_cloudy.svg" else ICON_SET_6 + "partly_cloudy_night.svg"
    Kind.Cloudy -> ICON_SET_5 + "cloudy.svg"
    Kind.Fog -> ICON_SET_6 + "windy.svg"
    Kind.Drizzle -> ICON_SET_6 + "drizzle.svg"
    Kind.Rain -> ICON_SET_6 + "heavy_rain.svg"
    Kind.Storm -> ICON_SET_6 + "strong_thunderstorms.svg"
    Kind.Sleet -> ICON_SET_6 + "wintry_mix.svg"
    Kind.Hail -> ICON_SET_6 + "sleet_hail.svg"
    Kind.Snow -> ICON_SET_6 + "flurries.svg"
    Kind.HeavySnow -> ICON_SET_6 + "heavy_snow.svg"
    Kind.Blizzard -> ICON_SET_6 + "blowing_snow.svg"
}

@Composable
fun WeatherIcon(kind: Kind, isDay: Boolean, size: Dp, tint: Color = Color.Unspecified) {
    val density = LocalDensity.current
    val url = iconUrl(kind, isDay)
    val painter by produceState<Painter?>(null, url) {
        value = withContext(Dispatchers.IO) {
            IconCache.bytes(url)?.let { data -> runCatching { loadSvgPainter(ByteArrayInputStream(data), density) }.getOrNull() }
        }
    }
    val loaded = painter
    if (loaded != null) {
        androidx.compose.foundation.Image(loaded, contentDescription = kind.label(isDay), modifier = Modifier.size(size))
    } else {
        val ink = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
        Canvas(Modifier.size(size)) { drawFallbackGlyph(kind, isDay, ink) }
    }
}

private fun DrawScope.drawFallbackGlyph(kind: Kind, isDay: Boolean, ink: Color) {
    val u = size.minDimension / 24f
    val sun = Color(0xFFF2B01E)
    fun cloud(dy: Float) {
        drawCircle(ink.copy(alpha = 0.9f), 4.5f * u, Offset(9f * u, (13f + dy) * u))
        drawCircle(ink.copy(alpha = 0.9f), 5.5f * u, Offset(14f * u, (11.5f + dy) * u))
        drawRect(ink.copy(alpha = 0.9f), Offset(5f * u, (13f + dy) * u), Size(14.5f * u, 4.5f * u))
    }
    when (kind) {
        Kind.Clear -> if (isDay) {
            drawCircle(sun, 5f * u, Offset(12f * u, 12f * u))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
                drawLine(sun, Offset((12f + 7.5f * c) * u, (12f + 7.5f * s) * u), Offset((12f + 10f * c) * u, (12f + 10f * s) * u), 2f * u, StrokeCap.Round)
            }
        } else drawCircle(ink, 6f * u, Offset(12f * u, 12f * u))
        Kind.FewClouds, Kind.MoreClouds -> { drawCircle(sun, 4f * u, Offset(16f * u, 8f * u)); cloud(0f) }
        Kind.Cloudy -> cloud(-1f)
        Kind.Fog -> for (i in 0 until 3) drawLine(ink, Offset(4f * u, (8f + i * 4f) * u), Offset(20f * u, (8f + i * 4f) * u), 2f * u, StrokeCap.Round)
        Kind.Storm -> {
            cloud(-3f)
            val bolt = Path().apply { moveTo(13f * u, 14f * u); lineTo(9f * u, 20f * u); lineTo(12f * u, 20f * u); lineTo(10.5f * u, 24f * u); lineTo(16f * u, 17f * u); lineTo(13f * u, 17f * u); close() }
            drawPath(bolt, sun)
        }
        else -> {
            cloud(-3f)
            val wet = if (kind.isSnow || kind == Kind.Hail || kind == Kind.Sleet) Color(0xFFBFD8F2) else Color(0xFF4F9BE0)
            for (i in 0 until 3) {
                val x = (8f + i * 4f) * u
                if (kind.isSnow) drawCircle(wet, 1.2f * u, Offset(x, 20f * u))
                else drawLine(wet, Offset(x, 18f * u), Offset(x - 1.5f * u, 21.5f * u), 2f * u, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun SegmentBar(position: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val gap = 2f
        val w = (size.width - gap * (colors.size - 1)) / colors.size
        colors.forEachIndexed { i, c -> drawRect(c, Offset(i * (w + gap), 5f), Size(w, 5f)) }
        val x = (position.coerceIn(0f, 1f) * size.width)
        drawLine(marker, Offset(x, 0f), Offset(x, size.height), 3f)
    }
}

val AqiColors = listOf(Color(0xFF3E9B5B), Color(0xFFE0B100), Color(0xFFE07A1F), Color(0xFFC8402F), Color(0xFF7A2E8A))
val UvColors = listOf(Color(0xFF3E9B5B), Color(0xFFE0B100), Color(0xFFE07A1F), Color(0xFFC8402F), Color(0xFF7A2E8A))
