package com.catsblock.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.catsblock.weather.data.AgentCard
import com.catsblock.weather.data.AgentMetric
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.City
import com.catsblock.weather.data.ThemeMode
import com.catsblock.weather.i18n.tr
import org.maplibre.android.geometry.LatLng
import kotlin.math.roundToInt

private val MapHeight = 320.dp

private fun metricText(metric: AgentMetric, value: Double, settings: AppSettings): String = when (metric) {
    AgentMetric.Humidity, AgentMetric.Clouds -> "${value.roundToInt()}%"
    AgentMetric.Wind -> windText(value, settings.windUnit)
    AgentMetric.Pressure -> pressureNumber(value, settings.pressureUnit) + " " + settings.pressureUnit.label
    AgentMetric.Uv -> f1(value)
    AgentMetric.Rain -> precipText(value, settings.precipUnit)
    AgentMetric.Temperature -> tempText(value, settings.fahrenheit)
}

@Composable
internal fun MetricCard(card: AgentCard.Metric, settings: AppSettings, content: Color) {
    CardPanel(content) {
        CardTitle(tr(card.metric.title), card.place, content)
        Text(
            metricText(card.metric, card.now, settings),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            color = content
        )
        MetricChart(card, content)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr("3 days ago"), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
            Text(tr("Next 24 hours"), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(tr("Lowest"), metricText(card.metric, card.min, settings), content, Modifier.weight(1f))
            Stat(tr("Average"), metricText(card.metric, card.avg, settings), content, Modifier.weight(1f))
            Stat(tr("Highest"), metricText(card.metric, card.max, settings), content, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricChart(card: AgentCard.Metric, content: Color) {
    val series = card.past + card.ahead
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        if (series.size < 2) return@Canvas
        var low = series.min()
        var high = series.max()
        if (card.metric.zeroBased) low = minOf(low, 0.0)
        if (high - low < 1e-6) high = low + 1.0
        val pad = (high - low) * 0.1
        high += pad
        if (!card.metric.zeroBased) low -= pad
        val width = size.width
        val height = size.height
        val last = series.size - 1
        fun px(index: Int): Float = width * index / last
        fun py(value: Double): Float = (height * (1.0 - (value - low) / (high - low))).toFloat()

        val area = Path().apply {
            moveTo(px(0), height)
            series.forEachIndexed { index, value -> lineTo(px(index), py(value)) }
            lineTo(px(last), height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(content.copy(alpha = 0.28f), Color.Transparent)))

        val nowIndex = card.past.size.coerceIn(0, last)
        val pastLine = Path().apply {
            series.take(nowIndex + 1).forEachIndexed { index, value ->
                if (index == 0) moveTo(px(index), py(value)) else lineTo(px(index), py(value))
            }
        }
        val aheadLine = Path().apply {
            moveTo(px(nowIndex), py(series[nowIndex]))
            for (index in nowIndex + 1..last) lineTo(px(index), py(series[index]))
        }
        drawPath(pastLine, content, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(
            aheadLine,
            content.copy(alpha = 0.75f),
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
        )
        drawLine(
            content.copy(alpha = 0.45f),
            Offset(px(nowIndex), 0f),
            Offset(px(nowIndex), height),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
        drawCircle(content, radius = 4.dp.toPx(), center = Offset(px(nowIndex), py(series[nowIndex])))
    }
}

@Composable
internal fun AgentMapCard(card: AgentCard.MapView, settings: AppSettings, content: Color) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val layer = MapLayer.entries.firstOrNull { it.id == card.layer } ?: MapLayer.None
    val title = tr(if (layer == MapLayer.None) "Weather map" else layer.label)
    val city = remember(card) { City(card.place, "", "", "", card.lat, card.lon) }

    CardPanel(content) {
        CardTitle(title, card.place, content)
        NativeWeatherMap(
            city = city,
            layer = layer,
            dark = dark,
            focusRequest = 0,
            radarTileTemplate = null,
            onPointSelected = { _, _, _, _ -> },
            onMapReady = {},
            initialCenter = LatLng(card.centerLat, card.centerLon),
            initialZoom = card.zoom,
            modifier = Modifier.fillMaxWidth().height(MapHeight)
        )
    }
}
