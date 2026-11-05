package com.catsblock.weather.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class Metric(val titleKey: String) {
    Precip("Precipitation forecast"), Wind("Wind forecast"), Air("Air quality"), Uv("UV index"),
    Humidity("Humidity"), Pressure("Pressure forecast"), Cloud("Cloud cover"), Visibility("Visibility")
}

private class DayData(val date: LocalDate, val hours: List<Hour>)

private class ChartSpec(
    val title: String,
    val select: (Hour) -> Double?,
    val color: Color,
    val bars: Boolean = false,
    val yMin: Float,
    val yMax: Float,
    val thresholds: List<Pair<Float, String>> = emptyList(),
    val bands: List<Pair<Float, Color>> = emptyList(),
    val format: (Float) -> String
)

private class Spec(
    val accent: Color,
    val valid: (DayData) -> Boolean = { true },
    val pick: (DayData) -> Hour?,
    val pillTop: (DayData) -> String,
    val pillFill: (DayData) -> Float,
    val charts: (DayData) -> List<ChartSpec>,
    val headline: @Composable (DayData, Hour?) -> Unit
)

private val Blue = Color(0xFF64B5F6)
private val WindBlue = Color(0xFF90B4F0)
private val Cyan = Color(0xFF4DD0E1)
private val CloudGrey = Color(0xFFB0BEC5)
private val PressureViolet = Color(0xFFB39DDB)
private val VisibilityColors = listOf(Color(0xFFD13B3B), Color(0xFFE0701A), Color(0xFFE6C229), Color(0xFF66BB6A), Color(0xFF4DD0E1))
private val clock = DateTimeFormatter.ofPattern("HH:mm")

private fun num(v: Float, decimals: Int = 0): String = String.format(Locale.US, "%.${decimals}f", v)

private fun middayOf(d: DayData): Hour? = d.hours.firstOrNull { it.time.hour == 12 } ?: d.hours.getOrNull(d.hours.size / 2)

private fun band(value: Double, breaks: List<Double>): Int = breaks.count { value >= it }

@Composable
private fun Headline(
    title: String,
    value: String,
    sub: String?,
    color: Color,
    trailing: (@Composable () -> Unit)? = null,
    below: @Composable () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
                Text(value, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = color)
                if (sub != null) Text(sub, style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
        below()
    }
}

@Composable
private fun CompassDial(deg: Double) {
    val line = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(112.dp)) {
            val r = size.minDimension / 2 - 4f
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(faint, r, center, style = Stroke(2f))
            val rad = Math.toRadians((deg + 180.0) % 360.0)
            val dir = Offset(sin(rad).toFloat(), -cos(rad).toFloat())
            val tip = center + dir * (r - 22f)
            val tail = center - dir * (r - 34f)
            drawLine(line, tail, tip, 5f, StrokeCap.Round)
            val side = Offset(-dir.y, dir.x)
            val head = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - dir.x * 14f + side.x * 8f, tip.y - dir.y * 14f + side.y * 8f)
                lineTo(tip.x - dir.x * 14f - side.x * 8f, tip.y - dir.y * 14f - side.y * 8f)
                close()
            }
            drawPath(head, line)
        }
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text("N", Modifier.align(Alignment.TopCenter), fontSize = 11.sp, color = muted)
        Text("S", Modifier.align(Alignment.BottomCenter), fontSize = 11.sp, color = muted)
        Text("W", Modifier.align(Alignment.CenterStart).padding(start = 4.dp), fontSize = 11.sp, color = muted)
        Text("E", Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), fontSize = 11.sp, color = muted)
    }
}

@Composable
private fun DayPill(top: String, fill: Float, bottom: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.width(64.dp).clip(SmallShape)
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(top, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
        Box(Modifier.padding(vertical = 6.dp).height(64.dp).width(14.dp), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(fill.coerceIn(0.05f, 1f)).background(accent, RoundedCornerShape(7.dp)))
        }
        Text(bottom, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HourlyChart(spec: ChartSpec, day: DayData, nowIndex: Int?) {
    val scheme = MaterialTheme.colorScheme
    val values = remember(day, spec) { day.hours.map { h -> spec.select(h)?.toFloat() } }
    if (values.isEmpty()) return
    var hover by remember(day) { mutableStateOf<Int?>(null) }
    var width by remember { mutableIntStateOf(1) }
    val shown = (hover ?: nowIndex ?: values.indexOfFirst { it != null }.coerceAtLeast(0)).coerceIn(0, values.lastIndex)
    val current = values[shown]
    Card(Modifier.fillMaxWidth(), spec.title) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (current != null) spec.format(current) else "—", style = MaterialTheme.typography.headlineSmall, color = spec.color)
            Spacer(Modifier.width(10.dp))
            Text(day.hours[shown].time.format(clock), color = scheme.onSurfaceVariant)
        }
        Canvas(
            Modifier.fillMaxWidth().height(190.dp)
                .onSizeChanged { width = it.width.coerceAtLeast(1) }
                .onPointerEvent(PointerEventType.Move) { event ->
                    val x = event.changes.first().position.x
                    hover = ((x / width) * values.size).toInt().coerceIn(0, values.lastIndex)
                }
                .onPointerEvent(PointerEventType.Exit) { hover = null }
        ) {
            val n = values.size
            val w = size.width
            val h = size.height
            val range = (spec.yMax - spec.yMin).coerceAtLeast(0.0001f)
            fun y(v: Float): Float = h - ((v.coerceIn(spec.yMin, spec.yMax) - spec.yMin) / range) * h
            val step = w / n
            fun x(i: Int): Float = (i + 0.5f) * step
            spec.bands.forEachIndexed { i, (from, color) ->
                val to = spec.bands.getOrNull(i + 1)?.first ?: spec.yMax
                drawRect(color.copy(alpha = 0.12f), Offset(0f, y(to)), Size(w, (y(from) - y(to)).coerceAtLeast(0f)))
            }
            for (k in 0..3) drawLine(scheme.outlineVariant.copy(alpha = 0.5f), Offset(0f, h * k / 3f), Offset(w, h * k / 3f), 1f)
            spec.thresholds.forEach { (tv, _) ->
                drawLine(scheme.outline, Offset(0f, y(tv)), Offset(w, y(tv)), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
            if (spec.bars) {
                values.forEachIndexed { i, v ->
                    if (v != null && v > 0f) {
                        val top = y(v)
                        drawRect(spec.color, Offset(x(i) - step * 0.35f, top), Size(step * 0.7f, h - top))
                    }
                }
            } else {
                val line = Path()
                val area = Path()
                var started = false
                var lastX = 0f
                values.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    val px = x(i)
                    val py = y(v)
                    if (!started) {
                        line.moveTo(px, py)
                        area.moveTo(px, h)
                        area.lineTo(px, py)
                        started = true
                    } else {
                        line.lineTo(px, py)
                        area.lineTo(px, py)
                    }
                    lastX = px
                }
                if (started) {
                    area.lineTo(lastX, h)
                    area.close()
                    drawPath(area, Brush.verticalGradient(listOf(spec.color.copy(alpha = 0.32f), Color.Transparent)))
                    drawPath(line, spec.color, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (nowIndex != null) drawLine(scheme.onSurface.copy(alpha = 0.45f), Offset(x(nowIndex), 0f), Offset(x(nowIndex), h), 2f)
            if (hover != null) {
                drawLine(scheme.onSurface.copy(alpha = 0.6f), Offset(x(shown), 0f), Offset(x(shown), h), 1.5f)
                if (current != null) drawCircle(spec.color, 6f, Offset(x(shown), y(current)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00:00", "06:00", "12:00", "18:00", "23:00").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        spec.thresholds.forEach { (_, label) ->
            Text("- - $label", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

private fun specFor(metric: Metric, s: DesktopSettings, c: Conditions, days: List<DayData>): Spec {
    fun peakOf(d: DayData, f: (Hour) -> Double): Hour? = d.hours.maxByOrNull(f)
    return when (metric) {
        Metric.Precip -> {
            val unit = s.precipUnit
            val weekMax = max(days.maxOfOrNull { d -> d.hours.sumOf { it.precip } } ?: 0.0, 5.0)
            Spec(
                accent = Blue,
                pick = { d -> peakOf(d) { it.precip } },
                pillTop = { d -> (d.hours.maxOfOrNull { it.prob } ?: 0).toString() + "%" },
                pillFill = { d -> (d.hours.sumOf { it.precip } / weekMax).toFloat() },
                charts = { d ->
                    val peak = (d.hours.maxOfOrNull { it.precip } ?: 0.0) * unit.factor
                    listOf(
                        ChartSpec(
                            title = t("Hourly forecast"), select = { it.precip * unit.factor }, color = Blue, bars = true,
                            yMin = 0f, yMax = max((2.0 * unit.factor).toFloat(), (peak * 1.3).toFloat()),
                            format = { num(it, unit.decimals) + " " + unit.label }
                        )
                    )
                },
                headline = { d, _ ->
                    val total = d.hours.sumOf { it.precip }
                    val chance = d.hours.maxOfOrNull { it.prob } ?: 0
                    Headline(t("Precipitation"), formatPrecip(total, unit), t("total") + " · $chance% " + t("chance"), Blue)
                }
            )
        }
        Metric.Wind -> {
            val unit = s.windUnit
            val weekMax = max(days.maxOfOrNull { d -> d.hours.maxOfOrNull { it.windKmh } ?: 0.0 } ?: 0.0, 10.0)
            Spec(
                accent = WindBlue,
                pick = { d -> peakOf(d) { it.windKmh } },
                pillTop = { d -> "Bft " + beaufort(d.hours.maxOfOrNull { it.windKmh } ?: 0.0) },
                pillFill = { d -> ((d.hours.maxOfOrNull { it.windKmh } ?: 0.0) / weekMax).toFloat() },
                charts = { d ->
                    val moderate = (20.0 * unit.factor).toFloat()
                    val peak = ((d.hours.maxOfOrNull { it.windKmh } ?: 0.0) * unit.factor).toFloat()
                    listOf(
                        ChartSpec(
                            title = t("Hourly wind"), select = { it.windKmh * unit.factor }, color = WindBlue,
                            yMin = 0f, yMax = max(moderate * 1.3f, peak * 1.25f),
                            thresholds = listOf(moderate to t("Moderate") + " (" + moderate.roundToInt() + " " + unit.label + ")"),
                            format = { it.roundToInt().toString() + " " + unit.label }
                        )
                    )
                },
                headline = { _, h ->
                    val speed = h?.windKmh ?: 0.0
                    val dir = h?.windDir ?: 0.0
                    Headline(
                        t("Wind"), formatWind(speed, unit), "Bft ${beaufort(speed)} · " + t("From") + " " + compass(dir), WindBlue,
                        trailing = { CompassDial(dir) }
                    ) {
                        if (h != null) Text(t("Gusts") + " " + formatWind(h.gust, unit), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
        Metric.Air -> Spec(
            accent = AqiColors[1],
            valid = { d -> d.hours.any { it.aqi != null } },
            pick = { d -> d.hours.filter { it.aqi != null }.maxByOrNull { it.aqi ?: 0.0 } },
            pillTop = { d -> (d.hours.maxOfOrNull { it.aqi ?: 0.0 } ?: 0.0).roundToInt().toString() },
            pillFill = { d -> ((d.hours.maxOfOrNull { it.aqi ?: 0.0 } ?: 0.0) / 200.0).toFloat() },
            charts = { d ->
                val peak = (d.hours.maxOfOrNull { it.aqi ?: 0.0 } ?: 0.0).toFloat()
                listOf(
                    ChartSpec(
                        title = t("Hourly AQI"), select = { it.aqi }, color = AqiColors[2],
                        yMin = 0f, yMax = max(150f, peak + 10f),
                        bands = listOf(0f to AqiColors[0], 50f to AqiColors[1], 100f to AqiColors[2], 150f to AqiColors[3], 200f to AqiColors[4]),
                        thresholds = listOf(50f to t("Good") + " (50)", 100f to t("Moderate") + " (100)"),
                        format = { "AQI " + it.roundToInt() }
                    )
                )
            },
            headline = { _, h ->
                val aqi = h?.aqi ?: 0.0
                Headline(t("Air quality"), aqi.roundToInt().toString(), t(aqiLabel(aqi)), AqiColors[min(4, band(aqi, listOf(50.0, 100.0, 150.0, 200.0)))]) {
                    SegmentBar((aqi / 300.0).toFloat(), AqiColors)
                    Text(t("AQI scale") + " · " + t("Based on the US Air Quality Index (AQI)"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
        Metric.Uv -> Spec(
            accent = UvColors[1],
            pick = { d -> peakOf(d) { it.uv } },
            pillTop = { d -> "UV " + (d.hours.maxOfOrNull { it.uv } ?: 0.0).roundToInt() },
            pillFill = { d -> ((d.hours.maxOfOrNull { it.uv } ?: 0.0) / 12.0).toFloat() },
            charts = { d ->
                val peak = (d.hours.maxOfOrNull { it.uv } ?: 0.0).toFloat()
                listOf(
                    ChartSpec(
                        title = t("Hourly UV index"), select = { it.uv }, color = UvColors[2],
                        yMin = 0f, yMax = max(12f, peak + 1f),
                        bands = listOf(0f to UvColors[0], 3f to UvColors[1], 6f to UvColors[2], 8f to UvColors[3], 11f to UvColors[4]),
                        thresholds = listOf(3f to t("Moderate") + " (3)", 6f to t("High") + " (6)", 8f to t("Very high") + " (8)"),
                        format = { "UV " + num(it, 1) }
                    )
                )
            },
            headline = { d, h ->
                val uv = h?.uv ?: 0.0
                val peak = d.hours.maxByOrNull { it.uv }
                val sub = t(uvLabel(uv)) + if (peak != null && peak.uv > 0.0) " · " + t("Peak around") + " " + peak.time.format(clock) + " · " + peak.uv.roundToInt() else ""
                Headline(t("UV index"), uv.roundToInt().toString(), sub, UvColors[min(4, band(uv, listOf(3.0, 6.0, 8.0, 11.0)))]) {
                    SegmentBar((uv / 12.0).toFloat(), UvColors)
                }
            }
        )
        Metric.Humidity -> {
            fun shown(v: Double): Double = if (s.fahrenheit) v * 9 / 5 + 32 else v
            Spec(
                accent = Cyan,
                pick = { d -> middayOf(d) },
                pillTop = { d -> d.hours.map { it.humidity }.average().roundToInt().toString() + "%" },
                pillFill = { d -> (d.hours.map { it.humidity }.average() / 100.0).toFloat() },
                charts = { d ->
                    val lo = shown(d.hours.minOfOrNull { it.dew } ?: 0.0).toFloat()
                    val hi = shown(d.hours.maxOfOrNull { it.dew } ?: 0.0).toFloat()
                    listOf(
                        ChartSpec(
                            title = t("Humidity"), select = { it.humidity.toDouble() }, color = Cyan,
                            yMin = 0f, yMax = 100f, format = { it.roundToInt().toString() + "%" }
                        ),
                        ChartSpec(
                            title = t("Dew point"), select = { shown(it.dew) }, color = Blue,
                            yMin = lo - 2f, yMax = hi + 2f, format = { it.roundToInt().toString() + "°" }
                        )
                    )
                },
                headline = { _, h ->
                    Headline(
                        t("Humidity"), (h?.humidity ?: 0).toString() + "%",
                        t("Dew point") + " " + formatTemp(h?.dew ?: 0.0, s.fahrenheit), Cyan
                    )
                }
            )
        }
        Metric.Pressure -> {
            val unit = s.pressureUnit
            Spec(
                accent = PressureViolet,
                pick = { d -> middayOf(d) },
                pillTop = { d -> num((d.hours.map { it.pressure }.average() * unit.factor).toFloat(), unit.decimals) },
                pillFill = { d -> ((d.hours.map { it.pressure }.average() - 980.0) / 60.0).toFloat() },
                charts = { d ->
                    val lo = (d.hours.minOfOrNull { it.pressure } ?: 1000.0)
                    val hi = (d.hours.maxOfOrNull { it.pressure } ?: 1020.0)
                    val mid = (lo + hi) / 2
                    val half = max((hi - lo) / 2 + 2.0, 5.0)
                    listOf(
                        ChartSpec(
                            title = t("Hourly pressure"), select = { it.pressure * unit.factor }, color = PressureViolet,
                            yMin = ((mid - half) * unit.factor).toFloat(), yMax = ((mid + half) * unit.factor).toFloat(),
                            thresholds = listOf((1013.25 * unit.factor).toFloat() to t("Standard")),
                            format = { num(it, unit.decimals) + " " + unit.label }
                        )
                    )
                },
                headline = { d, h ->
                    val index = d.hours.indexOf(h)
                    val ahead = d.hours.getOrNull(index + 3)?.pressure
                    val delta = if (h != null && ahead != null) ahead - h.pressure else 0.0
                    val trend = if (delta > 1.0) "Rising" else if (delta < -1.0) "Falling" else "Steady"
                    Headline(t("Atmospheric pressure"), formatPressure(h?.pressure ?: 1013.0, unit), t(trend), PressureViolet)
                }
            )
        }
        Metric.Cloud -> Spec(
            accent = CloudGrey,
            pick = { d -> middayOf(d) },
            pillTop = { d -> d.hours.map { it.cloud }.average().roundToInt().toString() + "%" },
            pillFill = { d -> (d.hours.map { it.cloud }.average() / 100.0).toFloat() },
            charts = {
                listOf(
                    ChartSpec(
                        title = t("Hourly cloud cover"), select = { it.cloud.toDouble() }, color = CloudGrey,
                        yMin = 0f, yMax = 100f, format = { it.roundToInt().toString() + "%" }
                    )
                )
            },
            headline = { _, h ->
                val cloud = h?.cloud ?: 0
                val label = if (cloud < 15) "Clear" else if (cloud < 40) "Mostly clear" else if (cloud < 70) "Partly cloudy" else "Cloudy"
                Headline(t("Cloud cover"), "$cloud%", t(label), CloudGrey)
            }
        )
        Metric.Visibility -> Spec(
            accent = VisibilityColors[3],
            pick = { d -> middayOf(d) },
            pillTop = { d -> num((d.hours.map { min(it.visibilityM / 1000.0, 20.0) }.average()).toFloat(), 0) + " km" },
            pillFill = { d -> (d.hours.map { min(it.visibilityM / 1000.0, 20.0) }.average() / 20.0).toFloat() },
            charts = {
                listOf(
                    ChartSpec(
                        title = t("Hourly visibility"), select = { min(it.visibilityM / 1000.0, 20.0) }, color = VisibilityColors[3],
                        yMin = 0f, yMax = 20f,
                        bands = listOf(0f to VisibilityColors[0], 1f to VisibilityColors[1], 4f to VisibilityColors[2], 10f to VisibilityColors[3], 16f to VisibilityColors[4]),
                        format = { num(it, 1) + " km" }
                    )
                )
            },
            headline = { _, h ->
                val km = min((h?.visibilityM ?: 10000.0) / 1000.0, 20.0)
                val index = band(km, listOf(1.0, 4.0, 10.0, 20.0))
                Headline(t("Visibility"), formatVisibility(h?.visibilityM ?: 10000.0), t(listOf("Very poor", "Poor", "Moderate", "Good", "Excellent")[index]), VisibilityColors[index]) {
                    SegmentBar((km / 20.0).toFloat(), VisibilityColors)
                    Text(t("Visibility scale"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun MetricDetailPage(metric: Metric, s: DesktopSettings, c: Conditions, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val days = remember(c) { c.hours.groupBy { it.time.toLocalDate() }.toSortedMap().map { DayData(it.key, it.value) }.take(7) }
    val spec = remember(metric, s, c) { specFor(metric, s, c, days) }
    val valid = days.filter(spec.valid)
    var selected by remember(metric) { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(SmallShape).clickable(onClick = onBack).padding(8.dp)) { GlyphIcon(Glyph.Back, scheme.onSurface) }
            Text(t(metric.titleKey), style = MaterialTheme.typography.titleLarge)
        }
        if (valid.isEmpty()) {
            Card(Modifier.fillMaxWidth()) { Text("—", style = MaterialTheme.typography.titleLarge) }
            return@Column
        }
        val index = selected.coerceIn(0, valid.lastIndex)
        val day = valid[index]
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                valid.forEachIndexed { i, d ->
                    val bottom = if (d.date == c.now.toLocalDate()) t("Now") else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, uiLocale())
                    DayPill(spec.pillTop(d), spec.pillFill(d), bottom, i == index, spec.accent) { selected = i }
                }
            }
        }
        val isToday = day.date == c.now.toLocalDate()
        val focus = if (isToday) day.hours.firstOrNull { it.time.hour == c.now.hour } else spec.pick(day)
        spec.headline(day, focus)
        val nowIndex = if (isToday) day.hours.indexOfFirst { it.time.hour == c.now.hour }.takeIf { it >= 0 } else null
        spec.charts(day).forEach { HourlyChart(it, day, nowIndex) }
    }
}
