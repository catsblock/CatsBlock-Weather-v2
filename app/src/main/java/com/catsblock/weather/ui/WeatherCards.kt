package com.catsblock.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsblock.weather.data.City
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.Day
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.data.aqiStatus
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.data.humidityLabel
import com.catsblock.weather.data.precipToday
import com.catsblock.weather.data.uvLevel
import com.catsblock.weather.i18n.AppLanguage
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.data.isWet
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SunCardV2(
    sunrise: LocalDateTime,
    sunset: LocalDateTime,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val total = Duration.between(sunrise, sunset).toMinutes().coerceAtLeast(1)
    val elapsed = Duration.between(sunrise, now).toMinutes()
    val target = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val up = !now.isBefore(sunrise) && !now.isAfter(sunset)
    val progress by animateFloatAsState(target, tween(1100), label = "sun")
    val ink = MaterialTheme.colorScheme.onSurface
    GlassCard(modifier, onClick, Modifier.fillMaxSize()) {
        CardHeader(Icons.Rounded.WbTwilight, tr("Sun"))
        Spacer(Modifier.weight(1f))
        BoxWithConstraints(Modifier.fillMaxWidth().height(78.dp)) {
            val r = min(maxWidth.value / 2f - 18f, maxHeight.value - 20f)
            val cx = maxWidth.value / 2f
            val cy = maxHeight.value - 8f
            val a = PI * (1.0 - progress)
            val sx = cx + r * cos(a).toFloat()
            val sy = cy - r * sin(a).toFloat()
            Canvas(Modifier.fillMaxSize()) {
                val rp = r.dp.toPx()
                val cxp = cx.dp.toPx()
                val cyp = cy.dp.toPx()
                drawLine(
                    ink.copy(alpha = 0.3f),
                    Offset(cxp - rp - 14.dp.toPx(), cyp),
                    Offset(cxp + rp + 14.dp.toPx(), cyp),
                    1.5f.dp.toPx()
                )
                drawArc(
                    ink.copy(alpha = 0.3f), 180f, 180f, false, Offset(cxp - rp, cyp - rp), Size(2 * rp, 2 * rp),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                )
                drawArc(
                    Color.White.copy(alpha = 0.45f), 180f, 180f * progress, false, Offset(cxp - rp, cyp - rp), Size(2 * rp, 2 * rp),
                    style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            WeatherIcon(
                Condition.Clear,
                true,
                Modifier
                    .offset((sx - 12f).dp, (sy - 12f).dp)
                    .size(24.dp)
                    .graphicsLayer { alpha = if (up) 1f else 0.45f }
            )
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
    icon: ImageVector,
    value: String,
    status: String?,
    text: String,
    fraction: Float,
    colors: List<Color>,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    unit: String? = null
) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val f by animateFloatAsState(if (go) fraction.coerceIn(0f, 1f) else 0f, tween(1000), label = "bar")
    GlassCard(modifier, onClick, Modifier.fillMaxSize()) {
        CardHeader(icon, title)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 46.sp),
                maxLines = 1
            )
            if (unit != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    unit,
                    Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
            if (status != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    status,
                    Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val h = 7.dp.toPx()
            val top = (size.height - h) / 2f
            drawRoundRect(Brush.horizontalGradient(colors), Offset(0f, top), Size(size.width, h), CornerRadius(h / 2f))
            val x = (size.width * f).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 4.5f.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, Offset(x, 1.dp.toPx()), Offset(x, size.height - 1.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

private fun visibilityGrade(km: Double): String = tr(
    when {
        km < 1.0 -> "Poor"
        km < 4.0 -> "Fair"
        km < 10.0 -> "Good"
        else -> "Excellent"
    }
)

@Composable
fun VisibilityCardV2(km: Double, description: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    GradientBarCard(
        tr("Visibility"), Icons.Rounded.Visibility,
        if (km >= 10.0) km.roundToInt().toString() else f1(km),
        visibilityGrade(km), description, (km / 20.0).toFloat(),
        listOf(Color(0xFFF44336), Color(0xFFFF9800), Color(0xFFFFEB3B), Color(0xFF66BB6A), Color(0xFF29B6F6)),
        modifier, onClick, "km"
    )
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

private fun compassLetters(): List<String> = when (I18n.lang) {
    AppLanguage.PtBR, AppLanguage.PtPT -> listOf("N", "L", "S", "O")
    AppLanguage.Spanish, AppLanguage.French, AppLanguage.Italian -> listOf("N", "E", "S", "O")
    AppLanguage.Russian -> listOf("С", "В", "Ю", "З")
    AppLanguage.German -> listOf("N", "O", "S", "W")
    AppLanguage.Korean -> listOf("북", "동", "남", "서")
    AppLanguage.Indonesian -> listOf("U", "T", "S", "B")
    else -> listOf("N", "E", "S", "W")
}

@Composable
fun WindCardV2(speedKmh: Double, fromDeg: Double, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val going = ((fromDeg + 180.0) % 360.0).toFloat()
    val rot by animateFloatAsState(going, tween(900), label = "windRot")
    val ink = MaterialTheme.colorScheme.onSurface
    val arrow = if (LocalGlass.current) Color.White else MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val letters = compassLetters()
    GlassCard(modifier, onClick, Modifier.fillMaxSize()) {
        CardHeader(Icons.Rounded.Air, tr("Wind"))
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.weight(1f).fillMaxWidth()) {
            val r = min(size.width, size.height) / 2f - 3.dp.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(ink.copy(alpha = 0.1f), r, c)
            drawCircle(ink.copy(alpha = 0.55f), r, c, style = Stroke(1.5f.dp.toPx()))
            for (i in 0 until 36) {
                val a = Math.toRadians(i * 10.0 - 90.0)
                val major = i % 9 == 0
                val len = if (major) 6.dp.toPx() else if (i % 3 == 0) 4.dp.toPx() else 2.5f.dp.toPx()
                drawLine(
                    ink.copy(alpha = if (major) 0.9f else 0.45f),
                    Offset(c.x + cos(a).toFloat() * (r - len), c.y + sin(a).toFloat() * (r - len)),
                    Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r),
                    1.2f.dp.toPx()
                )
            }
            for (i in 0 until 4) {
                val a = Math.toRadians(i * 90.0 - 90.0)
                val rr = r - 14.dp.toPx()
                val m = measurer.measure(letters[i], labelStyle.copy(color = if (i == 0) Color(0xFFFF6B6B) else ink.copy(alpha = 0.9f)))
                drawText(
                    m,
                    topLeft = Offset(c.x + cos(a).toFloat() * rr - m.size.width / 2f, c.y + sin(a).toFloat() * rr - m.size.height / 2f)
                )
            }
            rotate(rot, c) {
                val path = Path().apply {
                    moveTo(c.x, c.y - r * 0.62f)
                    lineTo(c.x - r * 0.15f, c.y - r * 0.2f)
                    lineTo(c.x + r * 0.15f, c.y - r * 0.2f)
                    close()
                }
                drawPath(path, arrow, style = Fill)
                drawLine(arrow, Offset(c.x, c.y - r * 0.2f), Offset(c.x, c.y + r * 0.38f), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(windText(speedKmh, LocalUnits.current.wind), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(
            "${tr("Blowing to")} ${compassLabel(going.toDouble())}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PressureCardV2(hpa: Double, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val lo = 960.0
    val hi = 1060.0
    val target = ((hpa - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val f by animateFloatAsState(if (go) target else 0f, tween(1200), label = "gauge")
    val ink = MaterialTheme.colorScheme.onSurface
    val arcColor = when {
        target < 0.33f -> Color(0xFF4FC3F7)
        target < 0.66f -> Color(0xFF66BB6A)
        else -> Color(0xFFFF7043)
    }
    GlassCard(modifier, onClick, Modifier.fillMaxSize()) {
        CardHeader(Icons.Rounded.Speed, tr("Pressure"))
        Canvas(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
            val r = min(size.width, size.height) / 2f - 4.dp.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            val tl = Offset(c.x - r, c.y - r)
            val sz = Size(2 * r, 2 * r)
            drawArc(ink.copy(alpha = 0.18f), 135f, 270f, false, tl, sz, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(arcColor, 135f, 270f * f, false, tl, sz, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            for (i in 0..50) {
                val a = Math.toRadians(135.0 + 270.0 * i / 50.0)
                val major = i % 5 == 0
                val len = if (major) 6.dp.toPx() else 3.dp.toPx()
                val outer = r - 9.dp.toPx()
                drawLine(
                    ink.copy(alpha = if (major) 0.85f else 0.35f),
                    Offset(c.x + cos(a).toFloat() * (outer - len), c.y + sin(a).toFloat() * (outer - len)),
                    Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                    if (major) 1.5f.dp.toPx() else 1f.dp.toPx()
                )
            }
            val na = Math.toRadians(135.0 + 270.0 * f)
            val dx = cos(na).toFloat()
            val dy = sin(na).toFloat()
            val len = r - 16.dp.toPx()
            val w = 3.dp.toPx()
            val needle = Path().apply {
                moveTo(c.x + dx * len, c.y + dy * len)
                lineTo(c.x - dy * w, c.y + dx * w)
                lineTo(c.x + dy * w, c.y - dx * w)
                close()
            }
            drawPath(needle, Color(0xFFFF5252), style = Fill)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(pressureNumber(hpa, LocalUnits.current.pressure), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text(
                LocalUnits.current.pressure.label,
                Modifier.padding(bottom = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CardsDashboard(
    bundle: WeatherBundle,
    city: City?,
    day: Day,
    mode: DashboardMode = DashboardMode.Temperature,
    onOpen: (String) -> Unit
) {
    val cur = bundle.current
    val km = cur.visibility / 1000.0
    val tall = Modifier.height(176.dp)
    val rainSoon = remember(bundle) {
        bundle.rain.any {
            it.mm > 0.0 && !it.time.isBefore(cur.now) && it.time.isBefore(cur.now.plusHours(2))
        }
    }
    if (city != null && (cur.kind.isWet || rainSoon)) {
        RadarCard(city, bundle.utcOffsetSeconds, Modifier.fillMaxWidth().height(250.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            title = tr("Precipitation"),
            icon = Icons.Rounded.Umbrella,
            value = f1(day.precipSum),
            unit = "mm",
            description = precipToday(day.precipSum),
            modifier = Modifier.weight(1f),
            onClick = { onOpen("Precip") }
        )
        WindCardV2(cur.windSpeed, cur.windDir, Modifier.weight(1f).then(tall)) { onOpen("Wind") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AirCardV2(
            cur.aqi,
            cur.aqi?.let { aqiShort(it) } ?: tr("Air quality data is not available here."),
            Modifier.weight(1f).then(tall),
            if (cur.aqi != null) ({ onOpen("Air") }) else null
        )
        UvCardV2(cur.uv, uvShort(cur.uv), Modifier.weight(1f).then(tall)) { onOpen("Uv") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            title = tr("Humidity"),
            icon = Icons.Rounded.WaterDrop,
            value = "${cur.humidity}%",
            unit = null,
            description = humidityLabel(cur.humidity),
            modifier = Modifier.weight(1f),
            onClick = { onOpen("Humidity") }
        )
        SunCardV2(
            day.sunrise,
            day.sunset,
            cur.now,
            Modifier.weight(1f).then(tall),
            if (city != null) ({ onOpen("Sun") }) else null
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PressureCardV2(cur.pressure, Modifier.weight(1f).then(tall)) { onOpen("Pressure") }
        VisibilityCardV2(km, visibilityShort(km), Modifier.weight(1f).then(tall)) { onOpen("Visibility") }
    }
    if (cur.cloud >= 0) {
        CloudCoverCard(cur.cloud, bundle.hours, cur.now, Modifier.fillMaxWidth()) { onOpen("Cloud") }
    }
    if (city != null) MoonCard(city, bundle.utcOffsetSeconds, cur.now) { onOpen("Moon") }
}
