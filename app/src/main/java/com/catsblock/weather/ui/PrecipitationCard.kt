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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
    val rainyKind = kind == Condition.Drizzle || kind == Condition.Rain || kind == Condition.Storm
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
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val gap = 1.dp.toPx()
            val barWidth = (size.width - gap * (total - 1)) / total
            val minBar = 2.dp.toPx()
            drawLine(
                Color.White.copy(alpha = 0.25f),
                Offset(0f, size.height),
                Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
            for (i in 0 until total) {
                val v = forecast.values[i]
                val wet = v >= WET_THRESHOLD
                val fraction = (v / scale).coerceIn(0.0, 1.0).toFloat()
                val barHeight = if (wet) max(minBar, size.height * (0.12f + 0.88f * fraction)) else minBar
                val color = if (wet) lerp(light, deep, fraction) else Color.White.copy(alpha = 0.18f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
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
