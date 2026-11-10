package com.catsblock.weather.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
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

val CardShape = RoundedCornerShape(20.dp)
val SmallShape = RoundedCornerShape(12.dp)

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
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, shapes = AppShapes, content = content)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(40.dp)
)

fun weatherSeed(kind: Kind?, isDay: Boolean): Color = when (kind) {
    null -> Color(0xFF1565C0)
    Kind.Clear -> if (isDay) Color(0xFF1E88E5) else Color(0xFF3949AB)
    Kind.FewClouds -> if (isDay) Color(0xFF2F8FD8) else Color(0xFF3F51B5)
    Kind.MoreClouds -> Color(0xFF4F7DA3)
    Kind.Cloudy -> Color(0xFF5B7083)
    Kind.Fog -> Color(0xFF7A8C99)
    Kind.Drizzle -> Color(0xFF0288D1)
    Kind.Rain -> Color(0xFF1565C0)
    Kind.Storm -> Color(0xFF5E35B1)
    Kind.Sleet -> Color(0xFF4A90B8)
    Kind.Hail -> Color(0xFF3F6FB5)
    Kind.Snow -> Color(0xFF42A5D8)
    Kind.HeavySnow -> Color(0xFF5C9BC4)
    Kind.Blizzard -> Color(0xFF7F9FB5)
}

private fun hueOf(c: Color): Float {
    val r = c.red
    val g = c.green
    val b = c.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    if (d == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return (h + 360f) % 360f
}

private fun saturationOf(c: Color): Float {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    return if (max == 0f) 0f else (max - min) / max
}

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

fun seedScheme(seed: Color, dark: Boolean): ColorScheme {
    val h = hueOf(seed)
    val s = saturationOf(seed).coerceIn(0.35f, 0.9f)
    val n = 0.14f
    return if (dark) darkColorScheme(
        primary = hsl(h, s, 0.78f), onPrimary = hsl(h, s, 0.14f),
        primaryContainer = hsl(h, s * 0.85f, 0.26f), onPrimaryContainer = hsl(h, s, 0.90f),
        secondary = hsl(h + 18f, s * 0.45f, 0.76f), onSecondary = hsl(h + 18f, s * 0.45f, 0.14f),
        secondaryContainer = hsl(h + 18f, s * 0.4f, 0.26f), onSecondaryContainer = hsl(h + 18f, s * 0.45f, 0.90f),
        tertiary = hsl(h + 60f, s * 0.6f, 0.78f), onTertiary = hsl(h + 60f, s * 0.6f, 0.14f),
        background = hsl(h, n, 0.07f), onBackground = hsl(h, 0.1f, 0.90f),
        surface = hsl(h, n, 0.08f), onSurface = hsl(h, 0.1f, 0.90f),
        surfaceVariant = hsl(h, n, 0.20f), onSurfaceVariant = hsl(h, 0.12f, 0.76f),
        outline = hsl(h, 0.08f, 0.52f), outlineVariant = hsl(h, 0.1f, 0.28f),
        surfaceContainerLowest = hsl(h, n, 0.05f), surfaceContainerLow = hsl(h, n, 0.10f),
        surfaceContainer = hsl(h, n, 0.13f), surfaceContainerHigh = hsl(h, n, 0.17f), surfaceContainerHighest = hsl(h, n, 0.21f)
    ) else lightColorScheme(
        primary = hsl(h, s, 0.38f), onPrimary = Color.White,
        primaryContainer = hsl(h, s, 0.90f), onPrimaryContainer = hsl(h, s, 0.12f),
        secondary = hsl(h + 18f, s * 0.5f, 0.40f), onSecondary = Color.White,
        secondaryContainer = hsl(h + 18f, s * 0.5f, 0.90f), onSecondaryContainer = hsl(h + 18f, s * 0.5f, 0.12f),
        tertiary = hsl(h + 60f, s * 0.6f, 0.40f), onTertiary = Color.White,
        background = hsl(h, 0.3f, 0.97f), onBackground = hsl(h, 0.2f, 0.10f),
        surface = hsl(h, 0.3f, 0.98f), onSurface = hsl(h, 0.2f, 0.10f),
        surfaceVariant = hsl(h, 0.25f, 0.90f), onSurfaceVariant = hsl(h, 0.15f, 0.30f),
        outline = hsl(h, 0.1f, 0.50f), outlineVariant = hsl(h, 0.15f, 0.80f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = hsl(h, 0.3f, 0.96f),
        surfaceContainer = hsl(h, 0.28f, 0.94f), surfaceContainerHigh = hsl(h, 0.26f, 0.92f), surfaceContainerHighest = hsl(h, 0.24f, 0.90f)
    )
}

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808), surfaceContainer = Color(0xFF0E0E0E),
    surfaceContainerHigh = Color(0xFF151515), surfaceContainerHighest = Color(0xFF1C1C1C), surfaceVariant = Color(0xFF151515)
)

@Composable
fun AppTheme(settings: DesktopSettings, kind: Kind?, isDay: Boolean, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val seed = if (settings.colorStyle == "custom") FIXED_COLORS[settings.customColor] else weatherSeed(kind, isDay)
    val base = remember(seed, dark) { seedScheme(seed, dark) }
    val scheme = if (dark && settings.amoled) base.amoled() else base
    val density = LocalDensity.current
    val scaled = remember(density, settings.textScale) { Density(density.density, density.fontScale * settings.textScale / 100f) }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, typography = AppTypography) {
        CompositionLocalProvider(LocalDensity provides scaled, content = content)
    }
}

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, title: String? = null, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val scheme: ColorScheme = MaterialTheme.colorScheme
    val base = if (onClick != null) modifier.clip(CardShape).clickable(onClick = onClick) else modifier
    Column(
        base.background(scheme.surfaceContainer.copy(alpha = 0.94f), CardShape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
        content()
    }
}

fun backgroundGradient(kind: Kind, isDay: Boolean): List<Color> {
    if (!isDay) return when (kind) {
        Kind.Storm -> listOf(Color(0xFF05070D), Color(0xFF0E131C), Color(0xFF1B2430))
        Kind.Rain, Kind.Drizzle, Kind.Hail, Kind.Sleet -> listOf(Color(0xFF0A0F18), Color(0xFF151D2A), Color(0xFF263344))
        Kind.Snow, Kind.HeavySnow, Kind.Blizzard -> listOf(Color(0xFF111A27), Color(0xFF223046), Color(0xFF3A4B63))
        else -> listOf(Color(0xFF050B1F), Color(0xFF0B1B3F), Color(0xFF1B2D5C))
    }
    return when (kind) {
        Kind.Clear -> listOf(Color(0xFF2F80ED), Color(0xFF56CCF2), Color(0xFF9CD8F5))
        Kind.FewClouds -> listOf(Color(0xFF3A7BD5), Color(0xFF6DB3F2), Color(0xFFA6D1F0))
        Kind.MoreClouds -> listOf(Color(0xFF4A78A8), Color(0xFF7FA3C7), Color(0xFFB4C7D8))
        Kind.Cloudy -> listOf(Color(0xFF4B5563), Color(0xFF6B7684), Color(0xFF8A95A1))
        Kind.Fog -> listOf(Color(0xFF5A6B78), Color(0xFF7F909C), Color(0xFFA7B4BD))
        Kind.Drizzle -> listOf(Color(0xFF3B4A5C), Color(0xFF55677B), Color(0xFF75879A))
        Kind.Rain -> listOf(Color(0xFF232E3B), Color(0xFF364555), Color(0xFF4F6174))
        Kind.Storm -> listOf(Color(0xFF0F131C), Color(0xFF1E2531), Color(0xFF34404F))
        Kind.Sleet, Kind.Hail -> listOf(Color(0xFF3F4F60), Color(0xFF5E7184), Color(0xFF8799AA))
        Kind.Snow -> listOf(Color(0xFF6C8299), Color(0xFF93A9BE), Color(0xFFBFD0DF))
        Kind.HeavySnow, Kind.Blizzard -> listOf(Color(0xFF55697D), Color(0xFF7D93A9), Color(0xFFA9BCCD))
    }
}

private const val IMAGE_BASE = "https://raw.githubusercontent.com/catsblock/CatsBlock-Weather-v2/main/assets/backgrounds/"

private fun backgroundImage(kind: Kind, isDay: Boolean, time: java.time.LocalTime): String {
    val minutes = time.hour * 60 + time.minute
    val sunrise = minutes in (5 * 60 + 30)..(8 * 60)
    val sunset = minutes in (17 * 60)..(18 * 60 + 40)
    val file = when (kind) {
        Kind.Clear, Kind.FewClouds -> when {
            sunrise -> "Clean_sunrise.jpg"; sunset -> "Clean_sunset.jpg"; !isDay -> "Clean_night.jpg"; else -> "Sunny.jpg"
        }
        Kind.MoreClouds -> when {
            sunrise -> "Partly_cloudy_sunrise.jpg"; sunset -> "Partly_cloudy_sunset.jpg"; !isDay -> "Partly_cloudy_night.jpg"; else -> "Partlycloud.jpg"
        }
        Kind.Cloudy, Kind.Fog -> "cloudy.jpg"
        Kind.Drizzle, Kind.Rain, Kind.Sleet -> "Rain.jpg"
        Kind.Storm, Kind.Hail -> "Storm.jpg"
        Kind.Snow, Kind.HeavySnow, Kind.Blizzard -> "Ssnow.jpg"
    }
    return IMAGE_BASE + file
}

object MediaCache {
    private val dir = File(AppPaths.dir, "media")
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build()

    fun bytes(url: String): ByteArray? {
        val uri = URI.create(url)
        if (uri.scheme != "https" || uri.host != "raw.githubusercontent.com") return null
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
        val cached = File(dir, name)
        if (cached.exists()) return runCatching { cached.readBytes() }.getOrNull()
        return runCatching {
            val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).header("User-Agent", "CatsBlockWeather-Desktop").GET().build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
            val body = response.body()
            if (response.statusCode() != 200 || body.size > 8_000_000) null else {
                dir.mkdirs()
                cached.writeBytes(body)
                body
            }
        }.getOrNull()
    }
}

@Composable
fun WeatherBackdrop(kind: Kind, isDay: Boolean, time: java.time.LocalTime, showImage: Boolean, modifier: Modifier = Modifier) {
    val url = backgroundImage(kind, isDay, time)
    val image by produceState<ImageBitmap?>(null, url, showImage) {
        value = if (!showImage) null else withContext(Dispatchers.IO) {
            MediaCache.bytes(url)?.let { data -> runCatching { org.jetbrains.skia.Image.makeFromEncoded(data).toComposeImageBitmap() }.getOrNull() }
        }
    }
    Box(modifier) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(backgroundGradient(kind, isDay))))
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (!isDay) Box(Modifier.fillMaxSize().background(Color(0xFF050B1F).copy(alpha = 0.35f)))
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

enum class Glyph { Settings, Refresh, Plus, Close, Check, Back, Pin, Sun, Map, Chevron }

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
            Glyph.Sun -> {
                drawCircle(tint, 4.5f * u, p(12f, 12f), style = stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = kotlin.math.cos(a).toFloat()
                    val s = kotlin.math.sin(a).toFloat()
                    drawLine(tint, p(12f + 7.5f * c, 12f + 7.5f * s), p(12f + 10f * c, 12f + 10f * s), 2f * u, StrokeCap.Round)
                }
            }
            Glyph.Map -> {
                val outline = Path().apply {
                    moveTo(3f * u, 6f * u); lineTo(9f * u, 4f * u); lineTo(15f * u, 6f * u); lineTo(21f * u, 4f * u)
                    lineTo(21f * u, 18f * u); lineTo(15f * u, 20f * u); lineTo(9f * u, 18f * u); lineTo(3f * u, 20f * u); close()
                }
                drawPath(outline, tint, style = Stroke(width = 2f * u, cap = StrokeCap.Round))
                drawLine(tint, p(9f, 4f), p(9f, 18f), 2f * u, StrokeCap.Round)
                drawLine(tint, p(15f, 6f), p(15f, 20f), 2f * u, StrokeCap.Round)
            }
            Glyph.Chevron -> {
                drawLine(tint, p(9f, 5f), p(16f, 12f), 2f * u, StrokeCap.Round)
                drawLine(tint, p(16f, 12f), p(9f, 19f), 2f * u, StrokeCap.Round)
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
private const val ICON_V0 = "https://raw.githubusercontent.com/bignutty/google-weather-icons/refs/heads/main/icons/weather/v0/dark/"
private const val ICON_SET_6 = "https://raw.githubusercontent.com/mrdarrengriffin/google-weather-icons/refs/heads/main/sets/set-6/light/"

fun iconUrl(kind: Kind, isDay: Boolean): String = when (kind) {
    Kind.Clear -> ICON_SET_6 + if (isDay) "sunny.svg" else "clear_night.svg"
    Kind.FewClouds -> if (isDay) ICON_SET_5 + "mostly_sunny.svg" else ICON_SET_6 + "mostly_clear_night.svg"
    Kind.MoreClouds -> if (isDay) ICON_SET_5 + "partly_cloudy.svg" else ICON_SET_6 + "partly_cloudy_night.svg"
    Kind.Cloudy -> ICON_SET_5 + "cloudy.svg"
    Kind.Fog -> ICON_SET_6 + "windy.svg"
    Kind.Drizzle -> ICON_V0 + "drizzle.svg"
    Kind.Rain -> ICON_V0 + "heavy_rain.svg"
    Kind.Storm -> ICON_V0 + "strong_tstorms.svg"
    Kind.Sleet -> ICON_V0 + "wintry_mix_rain_snow.svg"
    Kind.Hail -> ICON_V0 + "sleet_hail.svg"
    Kind.Snow -> ICON_V0 + "flurries.svg"
    Kind.HeavySnow -> ICON_V0 + "heavy_snow.svg"
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
