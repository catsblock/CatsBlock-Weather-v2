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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private const val ProbeAttempts = 40
private const val ProbeDelayMs = 250L

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
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val chosen = remember(card.layer) { MapLayer.entries.firstOrNull { it.id == card.layer && it != MapLayer.None } ?: MapLayer.Precipitation }
    var selectedId by rememberSaveable(card.layer, card.lat, card.lon) { mutableStateOf(chosen.id) }
    var appliedId by remember(card.layer, card.lat, card.lon) { mutableStateOf(chosen.id) }
    var ready by remember(card.layer, card.lat, card.lon) { mutableStateOf(false) }
    val selected = MapLayer.entries.firstOrNull { it.id == selectedId } ?: chosen
    val background = scheme.surface.toArgb()
    val fahrenheit = settings.fahrenheit
    val initJson = remember(card, dark, fahrenheit) {
        JSONObject()
            .put("dark", dark)
            .put("top", 0.0)
            .put("bottom", 0.0)
            .put("hasCity", true)
            .put("lat", card.lat)
            .put("lon", card.lon)
            .put("layer", chosen.id)
            .put("title", tr(chosen.label))
            .put("err", tr("Map data unavailable"))
            .put("f", fahrenheit)
            .put("labels", mapLabels())
            .toString()
    }
    val webRef = remember { arrayOfNulls<WebView>(1) }

    LaunchedEffect(ready, selectedId) {
        val web = webRef[0]
        if (ready && web != null && selectedId != appliedId) {
            appliedId = selectedId
            web.evaluateJavascript(
                "MapApi.setLayer(" + JSONObject.quote(selected.id) + "," + JSONObject.quote(tr(selected.label)) + "," +
                    JSONObject.quote(tr("Map data unavailable")) + "," + fahrenheit + "," + mapLabels() + ")",
                null
            )
        }
    }

    DisposableEffect(card) {
        onDispose { webRef[0] = null }
    }

    CardPanel(content) {
        CardTitle(tr("Weather map"), card.place, content)
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                factory = { ctx ->
                    WebView(ctx).also { web ->
                        webRef[0] = web
                        web.settings.javaScriptEnabled = true
                        web.settings.domStorageEnabled = true
                        web.settings.cacheMode = WebSettings.LOAD_DEFAULT
                        web.isVerticalScrollBarEnabled = false
                        web.isHorizontalScrollBarEnabled = false
                        web.overScrollMode = View.OVER_SCROLL_NEVER
                        web.setBackgroundColor(background)
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

                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.let { probe(it, 0) { ready = true } }
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
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapLayer.entries.filter { it != MapLayer.None }.forEach { layer ->
                FilterChip(
                    selected = layer.id == selectedId,
                    onClick = { selectedId = layer.id },
                    label = { Text(tr(layer.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(layer.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = content,
                        iconColor = content,
                        selectedContainerColor = content.copy(alpha = 0.28f),
                        selectedLabelColor = content,
                        selectedLeadingIconColor = content
                    )
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                tr("Layer chosen by the Agent") + ": " + tr(chosen.label),
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.8f)
            )
            if (selected != chosen) {
                Text(
                    tr("Layer chosen by you") + ": " + tr(selected.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun probe(web: WebView, attempt: Int, onReady: () -> Unit) {
    web.evaluateJavascript("typeof window.MapApi") { result ->
        when {
            result?.contains("object") == true -> onReady()
            attempt < ProbeAttempts -> web.postDelayed({ probe(web, attempt + 1, onReady) }, ProbeDelayMs)
        }
    }
}
