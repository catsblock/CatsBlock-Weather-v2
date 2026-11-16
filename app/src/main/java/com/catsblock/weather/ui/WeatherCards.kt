package com.catsblock.weather.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import com.catsblock.weather.data.Solar
import com.catsblock.weather.data.SolarDay
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
import androidx.compose.ui.text.font.FontWeight
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
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.data.isWet
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

@Composable
fun SunCardV2(
    sunrise: LocalDateTime,
    sunset: LocalDateTime,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    solar: SolarDay? = null,
    onClick: (() -> Unit)? = null
) {
    val total = Duration.between(sunrise, sunset).toMinutes().coerceAtLeast(1)
    val elapsed = Duration.between(sunrise, now).toMinutes()
    val target = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val up = !now.isBefore(sunrise) && !now.isAfter(sunset)
    val progress by animateFloatAsState(target, tween(1100), label = "sun")
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface
    val sky = Color(0xFFFFB300)
    val night = Color(0xFF5C6BC0)
    val remaining = Duration.between(now, sunset).toMinutes().coerceAtLeast(0)
    val pulse = rememberInfiniteTransition(label = "pulse")
    val halo by pulse.animateFloat(
        0.85f, 1.15f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo"
    )
    GlassCard(modifier, onClick, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WbSunny, null, Modifier.size(22.dp), tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(tr("Sun"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            durationLabel(total.toInt()),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            if (up) "${durationLabel(remaining.toInt())} ${tr("of daylight left")}" else tr("The sun is down"),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(112.dp)) {
            val w = maxWidth.value
            val h = maxHeight.value
            val horizon = h * 0.62f
            val amp = horizon - 10f
            val a = PI * (1.0 - progress)
            val sx = w * 0.06f + (w * 0.88f) * progress
            val sy = horizon - amp * sin(a).toFloat() * 1f
            Canvas(Modifier.fillMaxSize()) {
                val left = w * 0.06f
                val span = w * 0.88f
                val hp = horizon.dp.toPx()
                val ap = amp.dp.toPx()
                val path = Path()
                val steps = 64
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val x = (left + span * t).dp.toPx()
                    val y = hp - ap * sin(PI * t).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val fill = Path().apply {
                    addPath(path)
                    lineTo((left + span).dp.toPx(), hp)
                    lineTo(left.dp.toPx(), hp)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(sky.copy(alpha = 0.34f), sky.copy(alpha = 0f)), startY = hp - ap, endY = hp))
                drawRect(night.copy(alpha = 0.16f), Offset(0f, hp), Size(size.width, size.height - hp))
                drawLine(ink.copy(alpha = 0.35f), Offset(0f, hp), Offset(size.width, hp), 1.5f.dp.toPx())
                drawPath(path, ink.copy(alpha = 0.22f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                clipPath(Path().apply { addRect(Rect(0f, 0f, sx.dp.toPx(), size.height)) }) {
                    drawPath(path, sky, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
                listOf(left, left + span).forEach { x ->
                    drawCircle(sky, 5.dp.toPx(), Offset(x.dp.toPx(), hp))
                    drawCircle(scheme.surface, 2.5f.dp.toPx(), Offset(x.dp.toPx(), hp))
                }
                val c = Offset(sx.dp.toPx(), sy.dp.toPx())
                val alpha = if (up) 1f else 0.4f
                drawCircle(sky.copy(alpha = 0.18f * alpha), 22.dp.toPx() * halo, c)
                drawCircle(sky.copy(alpha = 0.32f * alpha), 15.dp.toPx() * halo, c)
                drawCircle(Color(0xFFFFE082).copy(alpha = alpha), 10.dp.toPx(), c)
                drawCircle(Color(0xFFFFF8E1).copy(alpha = alpha), 5.dp.toPx(), c)
            }
            Text(
                clockLabel(sunrise),
                Modifier.align(Alignment.TopStart).offset((w * 0.06f - 18f).coerceAtLeast(0f).dp, (horizon + 8f).dp),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                clockLabel(sunset),
                Modifier.align(Alignment.TopEnd).offset(-(w * 0.06f - 18f).coerceAtLeast(0f).dp, (horizon + 8f).dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SunFact(Icons.Rounded.WbSunny, tr("Solar noon"), solar?.noon?.let { clockLabel(it) } ?: "--", Modifier.weight(1f))
            SunFact(Icons.Rounded.WbTwilight, tr("First light"), solar?.firstLight?.let { clockLabel(it) } ?: "--", Modifier.weight(1f))
            SunFact(Icons.Rounded.NightsStay, tr("Last light"), solar?.lastLight?.let { clockLabel(it) } ?: "--", Modifier.weight(1f))
            SunFact(Icons.Rounded.Explore, tr("Sunset direction"), solar?.sunsetAzimuth?.let { "${compassLabel(it)} ${it.roundToInt()}°" } ?: "--", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SunFact(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(scheme.onSurface.copy(alpha = 0.07f)).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun durationLabel(minutes: Int): String = "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

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

@Composable
private fun LiquidLevelCard(
    title: String,
    value: String,
    description: String,
    footer: String,
    fraction: Float,
    modifier: Modifier,
    icon: ImageVector,
    color: Color,
    onClick: (() -> Unit)?
) {
    val progress by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "liquidLevel")
    val orientation = rememberDeviceOrientation()
    val motion = rememberInfiniteTransition(label = "liquidSurface")
    val wave by motion.animateFloat(0f, (2.0 * PI).toFloat(), infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "liquidWave")
    GlassCard(
        modifier.height(176.dp),
        onClick,
        Modifier.fillMaxSize(),
        background = {
            Canvas(Modifier.matchParentSize()) {
                val radius = 32.dp.toPx()
                drawRoundRect(color.copy(alpha = 0.055f), cornerRadius = CornerRadius(radius))
                if (progress > 0f) {
                    val surfaceMoves = progress in 0.01f..0.99f
                    val tilt = Math.toRadians(if (surfaceMoves) (-orientation.roll * 0.65f).coerceIn(-24f, 24f).toDouble() else 0.0)
                    val slope = tan(tilt).toFloat() * size.width * 0.5f
                    val pitchOffset = if (surfaceMoves) orientation.pitch.coerceIn(-60f, 60f) / 60f * size.height * 0.035f else 0f
                    val amplitude = if (progress in 0.02f..0.98f) 2.2.dp.toPx() else 0f
                    val surface = Path()
                    val surfaceLine = Path()
                    for (step in 0..48) {
                        val fractionX = step / 48f
                        val x = size.width * fractionX
                        val y = (size.height * (1f - progress) + pitchOffset + slope * (0.5f - fractionX) + sin(fractionX * 2f * PI.toFloat() + wave) * amplitude)
                            .coerceIn(-4f, size.height + 4f)
                        if (step == 0) {
                            surface.moveTo(x, y)
                            surfaceLine.moveTo(x, y)
                        } else {
                            surface.lineTo(x, y)
                            surfaceLine.lineTo(x, y)
                        }
                    }
                    surface.lineTo(size.width, size.height)
                    surface.lineTo(0f, size.height)
                    surface.close()
                    drawPath(surface, Brush.verticalGradient(listOf(color.copy(alpha = 0.38f), color.copy(alpha = 0.24f))))
                    drawPath(surfaceLine, color.copy(alpha = 0.54f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    ) {
        CardHeader(icon, title)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 38.sp, lineHeight = 44.sp), maxLines = 1)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(footer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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

@Composable
fun WindCardV2(speedKmh: Double, fromDeg: Double, city: City? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val orientation = rememberDeviceOrientation()
    val declination = remember(city?.lat, city?.lon) { city?.let { magneticDeclination(it.lat, it.lon) } ?: 0f }
    val trueHeading = if (orientation.available) (orientation.heading + declination + 360f) % 360f else 0f
    val target = shortestAngle(fromDeg.toFloat() - trueHeading)
    val rot by animateFloatAsState(target, tween(120), label = "windRot")
    GlassCard(modifier, onClick, Modifier.fillMaxSize()) {
        CardHeader(Icons.Rounded.Air, tr("Wind"))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Air,
                contentDescription = "${tr("From")} ${compassLabel(fromDeg)}",
                modifier = Modifier.size(58.dp).graphicsLayer {
                    rotationZ = rot
                    rotationX = (-orientation.pitch * 0.25f).coerceIn(-18f, 18f)
                    rotationY = (orientation.roll * 0.25f).coerceIn(-18f, 18f)
                },
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(windText(speedKmh, LocalUnits.current.wind), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(
            "${tr("From")} ${compassLabel(fromDeg)} · ${fromDeg.roundToInt()}°",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (orientation.available) "${tr("True heading")} ${compassLabel(trueHeading.toDouble())}" else tr("Compass unavailable"),
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
    val f by animateFloatAsState(target, tween(350), label = "pressurePosition")
    val unit = LocalUnits.current.pressure
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    GlassCard(modifier, onClick, Modifier.fillMaxSize()) {
        CardHeader(Icons.Rounded.Speed, tr("Pressure"))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val radius = (min(size.width, size.height) - stroke * 2f) / 2f
                val arcSize = Size(radius * 2f, radius * 2f)
                val topLeft = Offset((size.width - arcSize.width) / 2f, (size.height - arcSize.height) / 2f)
                drawArc(track, 108f, 324f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(primary, 108f, 324f * f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                Text(pressureNumber(hpa, unit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Text(unit.label, Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    val currentRain = remember(bundle.rain, cur.now) {
        bundle.rain.minByOrNull { abs(Duration.between(cur.now, it.time).toMinutes()) }
            ?.takeIf { abs(Duration.between(cur.now, it.time).toMinutes()) <= 30L }
    }
    val rainSoon = remember(bundle) {
        bundle.rain.any {
            it.mm > 0.0 && !it.time.isBefore(cur.now) && it.time.isBefore(cur.now.plusHours(2))
        }
    }
    if (city != null && (cur.kind.isWet || rainSoon)) {
        RadarCard(city, bundle.utcOffsetSeconds, Modifier.fillMaxWidth().height(250.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LiquidLevelCard(
            title = tr("Precipitation"),
            value = precipText(day.precipSum, LocalUnits.current.precip),
            description = "${precipToday(day.precipSum)} · ${tr("Scale: 0–50 mm per day")}",
            footer = currentRain?.let { "${tr("Next 15 minutes")}: ${precipText(it.mm, LocalUnits.current.precip)}" } ?: tr("Current precipitation data unavailable"),
            fraction = (day.precipSum / 50.0).toFloat(),
            modifier = Modifier.weight(1f).then(tall),
            icon = Icons.Rounded.Umbrella,
            color = Color(0xFF42A5F5),
            onClick = { onOpen("Precip") }
        )
        WindCardV2(cur.windSpeed, cur.windDir, city, Modifier.weight(1f).then(tall)) { onOpen("Wind") }
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
        LiquidLevelCard(
            title = tr("Humidity"),
            value = "${cur.humidity}%",
            description = tr("Relative humidity"),
            footer = humidityLabel(cur.humidity),
            fraction = cur.humidity / 100f,
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.WaterDrop,
            color = Color(0xFF29B6F6),
            onClick = { onOpen("Humidity") }
        )
        PressureCardV2(cur.pressure, Modifier.weight(1f).then(tall)) { onOpen("Pressure") }
    }
    val solar = remember(city, bundle.utcOffsetSeconds, cur.now.toLocalDate()) {
        city?.let { Solar.day(cur.now.toLocalDate(), it.lat, it.lon, bundle.utcOffsetSeconds) }
    }
    SunCardV2(
        day.sunrise,
        day.sunset,
        cur.now,
        Modifier.fillMaxWidth(),
        solar,
        if (city != null) ({ onOpen("Sun") }) else null
    )
    VisibilityCardV2(km, visibilityShort(km), Modifier.fillMaxWidth().then(tall)) { onOpen("Visibility") }
    if (cur.cloud >= 0) {
        CloudCoverCard(cur.cloud, bundle.hours, cur.now, Modifier.fillMaxWidth()) { onOpen("Cloud") }
    }
    if (city != null) MoonCard(city, bundle.utcOffsetSeconds, cur.now) { onOpen("Moon") }
    TravelCard { onOpen("Travel") }
}
