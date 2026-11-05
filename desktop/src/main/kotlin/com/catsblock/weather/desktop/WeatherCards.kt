package com.catsblock.weather.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun dayLabel(c: Conditions, d: Day): String =
    if (d.date == c.days.first().date) t("Today") else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, uiLocale())

@Composable
fun HeroCard(s: DesktopSettings, c: Conditions, modifier: Modifier = Modifier) {
    val ink = Color.White
    val shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 10f)
    val compact = s.compactHero
    Row(modifier.padding(vertical = if (compact) 4.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.city?.title.orEmpty(), color = ink, style = MaterialTheme.typography.titleLarge.copy(shadow = shadow))
            Text(
                formatTemp(c.temp, s.fahrenheit), color = ink, fontSize = if (compact) 64.sp else 108.sp, fontWeight = FontWeight.Light,
                style = MaterialTheme.typography.displayLarge.copy(shadow = shadow, fontSize = if (compact) 64.sp else 108.sp, fontWeight = FontWeight.Light)
            )
            Text(c.kind.label(c.isDay), color = ink, style = MaterialTheme.typography.headlineSmall.copy(shadow = shadow))
            val today = c.today
            val range = if (today != null) "  ·  ↑ ${formatTemp(today.max, s.fahrenheit)}  ↓ ${formatTemp(today.min, s.fahrenheit)}" else ""
            Text(
                t("Feels like") + " " + formatTemp(c.feels, s.fahrenheit) + range,
                color = ink.copy(alpha = 0.92f), style = MaterialTheme.typography.titleMedium.copy(shadow = shadow)
            )
        }
        WeatherIcon(c.kind, c.isDay, if (compact) 96.dp else 148.dp, ink)
    }
}

@Composable
fun AlertCard(c: Conditions, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val wet = c.wetAhead
    Card(modifier, t("Precipitation"), onClick) {
        if (wet == null) {
            Text(t("No rain expected soon"), style = MaterialTheme.typography.titleMedium)
        } else {
            val hours = maxOf(0L, Duration.between(c.now, wet.time).toHours()).toInt().coerceAtLeast(1)
            val text = if (wet.kind.isSnow) t("Snow") + " · $hours h" else t("Rain expected within %d h").format(hours)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WeatherIcon(wet.kind, wet.isDay, 40.dp)
                Column {
                    Text(text, style = MaterialTheme.typography.titleMedium)
                    Text(wet.prob.toString() + "%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun HourRow(s: DesktopSettings, hours: List<Hour>) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        hours.forEach { h ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(h.time.format(clock), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WeatherIcon(h.kind, h.isDay, 36.dp)
                Text(formatTemp(h.temp, s.fahrenheit), style = MaterialTheme.typography.titleMedium)
                Text(if (h.prob >= 20) "${h.prob}%" else " ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun HourlyCard(s: DesktopSettings, c: Conditions, modifier: Modifier = Modifier) {
    Card(modifier, t("Next 36 hours")) { HourRow(s, c.nextHours) }
}

@Composable
fun DailyCard(s: DesktopSettings, c: Conditions, onDay: (Day) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier, t("Daily")) {
        c.days.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().clickable { onDay(d) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(c, d), Modifier.width(72.dp), style = MaterialTheme.typography.bodyLarge)
                WeatherIcon(d.kind, true, 32.dp)
                Spacer(Modifier.width(16.dp))
                Text(d.kind.label(true), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(if (d.prob >= 20) "${d.prob}%" else "", Modifier.width(48.dp), color = MaterialTheme.colorScheme.secondary)
                Text(formatTemp(d.min, s.fahrenheit), Modifier.width(44.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                RangeBar(c, d, Modifier.width(96.dp))
                Text(formatTemp(d.max, s.fahrenheit), Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun DayDetail(s: DesktopSettings, c: Conditions, day: Day, onClose: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                day.date.dayOfWeek.getDisplayName(TextStyle.FULL, uiLocale()) + "  " + formatTemp(day.max, s.fahrenheit) + " / " + formatTemp(day.min, s.fahrenheit),
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium
            )
            Box(Modifier.clickable(onClick = onClose).padding(8.dp)) { GlyphIcon(Glyph.Close, MaterialTheme.colorScheme.onSurface) }
        }
        HourRow(s, c.hoursOf(day))
        Text(t("Sunrise") + " " + day.sunrise.format(clock) + "   " + t("Sunset") + " " + day.sunset.format(clock), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigValue(value: String, caption: String? = null) {
    Text(value, fontSize = 28.sp, fontWeight = FontWeight.Medium)
    if (caption != null) Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun WindCard(s: DesktopSettings, c: Conditions, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outlineVariant
    Card(modifier, t("Wind"), onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.TopCenter) {
                Canvas(Modifier.size(96.dp)) {
                    val r = size.minDimension / 2 - 6f
                    val center = Offset(size.width / 2, size.height / 2)
                    drawCircle(faint, r, center, style = Stroke(2f))
                    val rad = Math.toRadians((c.windDir + 180.0) % 360.0)
                    val dir = Offset(sin(rad).toFloat(), -cos(rad).toFloat())
                    val tip = center + dir * (r - 8f)
                    val tail = center - dir * (r - 20f)
                    drawLine(line, tail, tip, 4f, StrokeCap.Round)
                    val side = Offset(-dir.y, dir.x)
                    val head = Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(tip.x - dir.x * 14f + side.x * 8f, tip.y - dir.y * 14f + side.y * 8f)
                        lineTo(tip.x - dir.x * 14f - side.x * 8f, tip.y - dir.y * 14f - side.y * 8f)
                        close()
                    }
                    drawPath(head, line)
                }
                Text("N", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column { BigValue(formatWind(c.windKmh, s.windUnit), t("From") + " " + compass(c.windDir) + " · " + c.windDir.roundToInt() + "°") }
        }
    }
}

@Composable
fun AirCard(c: Conditions, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(modifier, t("Air quality"), onClick) {
        val aqi = c.aqi
        if (aqi == null) Text("—", fontSize = 28.sp) else {
            BigValue(aqi.roundToInt().toString(), t(aqiLabel(aqi)))
            SegmentBar((aqi / 300.0).toFloat(), AqiColors)
        }
    }
}

@Composable
fun UvCard(c: Conditions, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(modifier, t("UV index"), onClick) {
        BigValue(c.uv.roundToInt().toString(), t(uvLabel(c.uv)))
        SegmentBar((c.uv / 12.0).toFloat(), UvColors)
    }
}

@Composable
fun HumidityCard(s: DesktopSettings, c: Conditions, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(modifier, t("Humidity"), onClick) {
        BigValue(c.humidity.toString() + "%", t("Dew point") + " " + formatTemp(c.dew, s.fahrenheit))
        SegmentBar(c.humidity / 100f, listOf(Color(0xFFE0B100), Color(0xFF3E9B5B), Color(0xFF4F86A6), Color(0xFF2F6F9F)))
    }
}

@Composable
fun CloudCard(c: Conditions, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(modifier, t("Cloud cover"), onClick) {
        BigValue(c.cloud.toString() + "%")
        SegmentBar(c.cloud / 100f, listOf(Color(0xFFDDE5EC), Color(0xFFB0BEC5), Color(0xFF8493A0), Color(0xFF5E6B77)))
    }
}

@Composable
fun PressureCard(s: DesktopSettings, c: Conditions, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(modifier, t("Atmospheric pressure"), onClick) {
        BigValue(formatPressure(c.pressure, s.pressureUnit))
        SegmentBar(((c.pressure - 960.0) / 90.0).toFloat(), listOf(Color(0xFF4F86A6), Color(0xFF6B7683), Color(0xFFE0A100)))
    }
}

@Composable
fun VisibilityCard(c: Conditions, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(modifier, t("Visibility"), onClick) { BigValue(formatVisibility(c.visibilityM)) }
}

@Composable
fun SunCard(c: Conditions, modifier: Modifier = Modifier) {
    val day = c.today
    val track = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    Card(modifier, t("Sunrise") + " / " + t("Sunset")) {
        if (day == null) return@Card
        val total = Duration.between(day.sunrise, day.sunset).seconds.coerceAtLeast(1)
        val progress = (Duration.between(day.sunrise, c.now).seconds.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val pad = 12f
            val rx = size.width / 2 - pad
            val ry = size.height - pad
            val topLeft = Offset(pad, pad)
            val arcSize = Size(rx * 2, ry * 2)
            drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(3f))
            drawArc(accent, 180f, 180f * progress, false, topLeft, arcSize, style = Stroke(3f))
            val a = PI * (1 - progress)
            drawCircle(Color(0xFFF2B01E), 7f, Offset((size.width / 2 + rx * cos(a)).toFloat(), (pad + ry - ry * sin(a)).toFloat()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(day.sunrise.format(clock), style = MaterialTheme.typography.titleMedium)
            Text(day.sunset.format(clock), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun MoonCard(modifier: Modifier = Modifier) {
    val phase = moonPhase()
    val lit = Color(0xFFE8E4D6)
    val dark = MaterialTheme.colorScheme.surfaceVariant
    Card(modifier, t("Moon")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Canvas(Modifier.size(72.dp)) {
                val r = size.minDimension / 2 - 2f
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(dark, r, c)
                val k = cos(2 * PI * phase.fraction).toFloat()
                val waxing = phase.fraction < 0.5
                val circle = Rect(c.x - r, c.y - r, c.x + r, c.y + r)
                val ellipse = Rect(c.x - r * abs(k), c.y - r, c.x + r * abs(k), c.y + r)
                val path = Path()
                if (waxing) {
                    path.arcTo(circle, -90f, 180f, true)
                    path.arcTo(ellipse, 90f, if (k > 0) -180f else 180f, false)
                } else {
                    path.arcTo(circle, -90f, -180f, true)
                    path.arcTo(ellipse, 90f, if (k > 0) 180f else -180f, false)
                }
                path.close()
                drawPath(path, lit)
            }
            Column {
                Text(t(phase.key), style = MaterialTheme.typography.titleMedium)
                Text(t("Illumination") + " " + (phase.illumination * 100).roundToInt() + "%", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RangeBar(c: Conditions, d: Day, modifier: Modifier = Modifier) {
    val lo = c.days.minOf { it.min }
    val hi = c.days.maxOf { it.max }
    val span = (hi - lo).coerceAtLeast(1.0)
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier.height(8.dp)) {
        val a = ((d.min - lo) / span).toFloat() * size.width
        val b = ((d.max - lo) / span).toFloat() * size.width
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
        drawRoundRect(fill, Offset(a, 0f), Size(max(b - a, 8f), size.height), androidx.compose.ui.geometry.CornerRadius(4f, 4f))
    }
}
