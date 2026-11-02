package com.catsblock.weather.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.isSystemInDarkTheme
import com.catsblock.weather.data.AgentCard
import com.catsblock.weather.data.AgentMetric
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.FireHotspots
import com.catsblock.weather.data.ThemeMode
import com.catsblock.weather.i18n.tr
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

private const val MapMinZoom = 1.0
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

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
internal fun AgentMapCard(card: AgentCard.MapView, settings: AppSettings, content: Color) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val layer = MapLayer.entries.firstOrNull { it.id == card.layer } ?: MapLayer.None
    val fahrenheit = settings.fahrenheit
    val title = tr(if (layer == MapLayer.None) "Weather map" else layer.label)
    val initJson = remember(card, dark, fahrenheit, title) {
        JSONObject()
            .put("dark", dark)
            .put("top", 0.0)
            .put("bottom", 0.0)
            .put("hasCity", true)
            .put("round", true)
            .put("minZoom", MapMinZoom)
            .put("lat", card.lat)
            .put("lon", card.lon)
            .put("centerLat", card.centerLat)
            .put("centerLon", card.centerLon)
            .put("zoom", card.zoom)
            .put("layer", layer.id)
            .put("title", tr(layer.label))
            .put("err", tr("Map data unavailable"))
            .put("f", fahrenheit)
            .put("labels", mapLabels())
            .toString()
    }

    CardPanel(content) {
        CardTitle(title, card.place, content)
        key(initJson) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(MapHeight),
                factory = { ctx ->
                    WebView(ctx).also { web ->
                        web.settings.javaScriptEnabled = true
                        web.settings.domStorageEnabled = true
                        web.settings.cacheMode = WebSettings.LOAD_DEFAULT
                        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        web.isVerticalScrollBarEnabled = false
                        web.isHorizontalScrollBarEnabled = false
                        web.overScrollMode = View.OVER_SCROLL_NEVER
                        web.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        web.setOnTouchListener { view, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                        web.webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url?.toString().orEmpty()
                                if (!url.endsWith("__fires.json")) return null
                                val data = FireHotspots.json(ctx.applicationContext)
                                val headers = mapOf("Access-Control-Allow-Origin" to "*")
                                return if (data != null) {
                                    WebResourceResponse("application/json", "utf-8", 200, "OK", headers, ByteArrayInputStream(data))
                                } else {
                                    WebResourceResponse("application/json", "utf-8", 503, "Unavailable", headers, ByteArrayInputStream(ByteArray(0)))
                                }
                            }
                        }
                        web.loadDataWithBaseURL(MapPage.BASE_URL, MapPage.page(initJson), "text/html", "utf-8", null)
                    }
                },
                onRelease = { web ->
                    web.stopLoading()
                    web.destroy()
                }
            )
        }
    }
}
