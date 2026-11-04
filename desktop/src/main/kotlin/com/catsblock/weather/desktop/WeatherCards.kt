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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun dayLabel(c: Conditions, d: Day): String =
    if (d.date == c.days.first().date) t("Today") else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, uiLocale())

@Composable
fun HeroCard(s: DesktopSettings, c: Conditions, modifier: Modifier = Modifier) {
    val background = weatherColor(c.kind, c.isDay)
    val ink = inkFor(background)
    Row(
        modifier.background(background, CardShape).padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.city?.title.orEmpty(), color = ink, style = MaterialTheme.typography.titleMedium)
            Text(formatTemp(c.temp, s.fahrenheit), color = ink, fontSize = 80.sp, fontWeight = FontWeight.Light)
            Text(c.kind.label(c.isDay), color = ink, style = MaterialTheme.typography.titleLarge)
            val today = c.today
            val range = if (today != null) "  ·  ${formatTemp(today.max, s.fahrenheit)} / ${formatTemp(today.min, s.fahrenheit)}" else ""
            Text(t("Feels like") + " " + formatTemp(c.feels, s.fahrenheit) + range, color = ink.copy(alpha = 0.85f))
        }
        WeatherIcon(c.kind, c.isDay, 112.dp, ink)
    }
}

@Composable
fun AlertCard(c: Conditions, modifier: Modifier = Modifier) {
    val wet = c.wetAhead
    Card(modifier, t("Precipitation")) {
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
                Text(formatTemp(d.max, s.fahrenheit) + " / " + formatTemp(d.min, s.fahrenheit), style = MaterialTheme.typography.bodyLarge)
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
fun WindCard(s: DesktopSettings, c: Conditions, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outlineVariant
    Card(modifier, t("Wind")) {
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
            Column { BigValue(formatWind(c.windKmh, s.windUnit), c.windDir.roundToInt().toString() + "°") }
        }
    }
}

@Composable
fun AirCard(c: Conditions, modifier: Modifier = Modifier) {
    Card(modifier, t("Air quality")) {
        val aqi = c.aqi
        if (aqi == null) Text("—", fontSize = 28.sp) else {
            BigValue(aqi.roundToInt().toString(), t(aqiLabel(aqi)))
            SegmentBar((aqi / 300.0).toFloat(), AqiColors)
        }
    }
}

@Composable
fun UvCard(c: Conditions, modifier: Modifier = Modifier) {
    Card(modifier, t("UV index")) {
        BigValue(c.uv.roundToInt().toString(), t(uvLabel(c.uv)))
        SegmentBar((c.uv / 12.0).toFloat(), UvColors)
    }
}

@Composable
fun HumidityCard(c: Conditions, modifier: Modifier = Modifier) {
    Card(modifier, t("Humidity")) { BigValue(c.humidity.toString() + "%") }
}

@Composable
fun PressureCard(s: DesktopSettings, c: Conditions, modifier: Modifier = Modifier) {
    Card(modifier, t("Pressure")) {
        BigValue(formatPressure(c.pressure, s.pressureUnit))
        SegmentBar(((c.pressure - 960.0) / 90.0).toFloat(), listOf(Color(0xFF4F86A6), Color(0xFF6B7683), Color(0xFFE0A100)))
    }
}

@Composable
fun VisibilityCard(c: Conditions, modifier: Modifier = Modifier) {
    Card(modifier, t("Visibility")) { BigValue(formatVisibility(c.visibilityM)) }
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
