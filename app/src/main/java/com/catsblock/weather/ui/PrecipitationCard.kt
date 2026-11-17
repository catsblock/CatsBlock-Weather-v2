package com.catsblock.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.i18n.tr
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min

private const val WET_THRESHOLD = 0.1
private const val HORIZON = 120

data class PrecipitationForecast(
    val values: List<Double>,
    val raining: Boolean,
    val startMinute: Int,
    val endMinute: Int?,
    val peak: Double,
    val now: LocalDateTime
)

fun buildPrecipitationForecast(bundle: WeatherBundle): PrecipitationForecast? {
    val now = bundle.current.now
    val slots = bundle.rain.sortedBy { it.time }
    val values = List(HORIZON) { minute ->
        if (slots.isEmpty()) {
            0.0
        } else {
            val t = now.plusMinutes(minute.toLong())
            val index = slots.indexOfFirst { it.time.minusMinutes(8).isAfter(t) || it.time.minusMinutes(8) == t }
            when {
                index < 0 -> 0.0
                index == 0 -> slots[0].mm * 4.0
                else -> {
                    val prevCenter = slots[index - 1].time.minusMinutes(8)
                    val span = java.time.Duration.between(prevCenter, slots[index].time.minusMinutes(8)).toMinutes().coerceAtLeast(1)
                    val frac = (java.time.Duration.between(prevCenter, t).toMinutes().toDouble() / span).coerceIn(0.0, 1.0)
                    (slots[index - 1].mm * (1 - frac) + slots[index].mm * frac) * 4.0
                }
            }
        }
    }
    val kind = bundle.current.kind
    val rainyKind = kind == Condition.Drizzle || kind == Condition.Rain || kind == Condition.HeavyRain || kind == Condition.Storm ||
        kind == Condition.ScatteredShowers || kind == Condition.ScatteredStorms ||
        kind == Condition.IsolatedStorms || kind == Condition.StrongStorms
    val raining = values[0] >= WET_THRESHOLD || rainyKind
    val firstWet = values.indexOfFirst { it >= WET_THRESHOLD }
    val start = when {
        raining -> 0
        firstWet in 0 until 60 -> firstWet
        else -> return null
    }
    val end = if (raining && values.take(15).none { it >= WET_THRESHOLD }) {
        0
    } else {
        (start until HORIZON).firstOrNull { it > start && values[it] < WET_THRESHOLD }
    }
    val peak = values.subList(start, min(HORIZON, (end ?: HORIZON).coerceAtLeast(start + 1))).maxOrNull() ?: 0.0
    return PrecipitationForecast(values, raining, start, end, peak, now)
}

private fun precipitationMeasurements(bundle: WeatherBundle, forecast: PrecipitationForecast, total: Int): List<ChartSample> {
    val last = (total - 1).toLong()
    val slots = bundle.rain.sortedBy { it.time }.mapNotNull { slot ->
        val minute = java.time.Duration.between(forecast.now, slot.time.minusMinutes(8)).toMinutes()
        if (minute in 0L..last) ChartSample(minute, slot.mm * 4.0) else null
    }
    val edges = listOf(ChartSample(0L, forecast.values[0]), ChartSample(last, forecast.values[total - 1]))
    return (edges + slots).sortedBy { it.timestamp }.distinctBy { it.timestamp }
}

private fun durationText(minutes: Int): String {
    val m = max(1, minutes)
    val min = tr("min")
    val hour = tr("h")
    return if (m < 60) "$m $min" else {
        val h = m / 60
        val r = m % 60
        if (r == 0) "$h $hour" else "$h $hour $r $min"
    }
}

private fun intensityLabel(mmPerHour: Double): String = tr(
    when {
        mmPerHour < 2.5 -> "Light rain"
        mmPerHour < 7.6 -> "Moderate rain"
        else -> "Heavy rain"
    }
)

@Composable
fun PrecipitationCard(bundle: WeatherBundle, modifier: Modifier = Modifier) {
    val forecast = remember(bundle) { buildPrecipitationForecast(bundle) } ?: return
    val now = forecast.now
    val end = forecast.endMinute
    val headline: String
    val detail: String
    if (forecast.raining) {
        when {
            end == null -> {
                headline = tr("Rain continuing for over 2 hours")
                detail = "${intensityLabel(forecast.peak)}. ${tr("No end expected before")} ${clockLabel(now.plusMinutes(HORIZON.toLong()))}"
            }
            end <= 1 -> {
                headline = tr("Rain ending shortly")
                detail = tr("Expected to stop within a few minutes")
            }
            else -> {
                headline = "${tr("Rain ending in")} ${durationText(end)}"
                detail = "${intensityLabel(forecast.peak)}. ${tr("Expected to stop around")} ${clockLabel(now.plusMinutes(end.toLong()))}"
            }
        }
    } else {
        headline = "${tr("Rain starting in")} ${durationText(forecast.startMinute)}"
        detail = if (end == null) {
            "${tr("Expected around")} ${clockLabel(now.plusMinutes(forecast.startMinute.toLong()))}, ${tr("continuing for over 2 hours")}"
        } else {
            "${tr("Expected around")} ${clockLabel(now.plusMinutes(forecast.startMinute.toLong()))}, ${tr("ending around")} ${clockLabel(now.plusMinutes(end.toLong()))}"
        }
    }
    val needed = (end ?: HORIZON) + 10
    val total = when {
        needed <= 60 -> 60
        needed <= 90 -> 90
        else -> 120
    }
    val step = if (total == 60) 15 else 30
    val labels = (0..total step step).map { if (it == 0) tr("Now") else clockLabel(now.plusMinutes(it.toLong())) }
    val scale = max(forecast.values.take(total).maxOrNull() ?: 0.0, 2.5)
    val light = Color(0xFFBFE3FF)
    val deep = Color(0xFF3D8BFF)
    val measured = remember(bundle, total) { precipitationMeasurements(bundle, forecast, total) }
    val curve = remember(measured) { monotoneChartSegments(measured, 10) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(650)) }

    GlassCard(modifier.fillMaxWidth()) {
        CardHeader(Icons.Rounded.Umbrella, tr("Precipitation"))
        Spacer(Modifier.height(12.dp))
        Text(
            headline,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${tr("Peak")} ${f1(forecast.peak)} mm/h · $detail",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(16.dp))
        Text("mm/h", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val bottom = size.height - 2.dp.toPx()
            val topPadding = 4.dp.toPx()
            val chartHeight = bottom - topPadding
            for (index in 1..3) {
                val fraction = index / 4f
                val y = bottom - chartHeight * fraction
                drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            drawLine(Color.White.copy(alpha = 0.24f), Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
            val span = (total - 1).coerceAtLeast(1).toFloat()
            fun xOf(minute: Long) = size.width * minute.toFloat() / span
            fun yOf(value: Double) = bottom - chartHeight * value.coerceIn(0.0, scale).toFloat() / scale.toFloat()
            val points = curve.flatten().filter { it.timestamp in 0L..(total - 1).toLong() }.map { Offset(xOf(it.timestamp), yOf(it.value)) }
            if (points.size < 2) return@Canvas
            clipRect(right = size.width * reveal.value) {
                val line = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                val area = Path().apply {
                    moveTo(points.first().x, bottom)
                    lineTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, bottom)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(deep.copy(alpha = 0.22f), deep.copy(alpha = 0.012f)), topPadding, bottom))
                drawPath(
                    line,
                    Brush.horizontalGradient(listOf(light, deep)),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                measured.forEach { sample ->
                    val value = sample.value ?: return@forEach
                    drawCircle(lerp(light, deep, (value / scale).coerceIn(0.0, 1.0).toFloat()), 2.4.dp.toPx(), Offset(xOf(sample.timestamp), yOf(value)))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
