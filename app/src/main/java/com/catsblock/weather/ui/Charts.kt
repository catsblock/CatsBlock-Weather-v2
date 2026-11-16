package com.catsblock.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

internal data class ChartSample(val timestamp: Long, val value: Double?)

internal data class ChartCoordinate(val timestamp: Long, val value: Double)

internal fun monotoneChartSegments(samples: List<ChartSample>, subdivisions: Int = 12): List<List<ChartCoordinate>> {
    require(subdivisions > 0)
    val segments = mutableListOf<List<ChartSample>>()
    var current = mutableListOf<ChartSample>()
    fun flush() {
        if (current.isEmpty()) return
        val ordered = current.withIndex()
            .sortedBy { it.value.timestamp }
            .groupBy { it.value.timestamp }
            .values
            .map { it.last().value }
        if (ordered.isNotEmpty()) segments.add(ordered)
        current = mutableListOf()
    }
    samples.forEach { sample ->
        val value = sample.value?.takeIf { it.isFinite() }
        if (value == null) {
            flush()
        } else {
            current.add(sample.copy(value = value.coerceAtLeast(0.0)))
        }
    }
    flush()
    return segments.map { segment ->
        if (segment.size < 2) return@map segment.map { ChartCoordinate(it.timestamp, it.value!!) }
        val xs = segment.map { it.timestamp.toDouble() }
        val ys = segment.map { it.value!! }
        val slopes = (0 until segment.lastIndex).map { i -> (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]) }
        val tangents = MutableList(ys.size) { 0.0 }
        tangents[0] = slopes.first()
        tangents[tangents.lastIndex] = slopes.last()
        for (i in 1 until ys.lastIndex) {
            tangents[i] = if (slopes[i - 1] * slopes[i] <= 0.0) 0.0 else {
                val left = xs[i] - xs[i - 1]
                val right = xs[i + 1] - xs[i]
                val w1 = 2.0 * right + left
                val w2 = right + 2.0 * left
                (w1 + w2) / (w1 / slopes[i - 1] + w2 / slopes[i])
            }
        }
        slopes.forEachIndexed { i, slope ->
            if (slope == 0.0) {
                tangents[i] = 0.0
                tangents[i + 1] = 0.0
            } else {
                val a = tangents[i] / slope
                val b = tangents[i + 1] / slope
                val magnitude = a * a + b * b
                if (magnitude > 9.0) {
                    val scale = 3.0 / kotlin.math.sqrt(magnitude)
                    tangents[i] = scale * a * slope
                    tangents[i + 1] = scale * b * slope
                }
            }
        }
        buildList {
            for (i in 0 until segment.lastIndex) {
                val width = xs[i + 1] - xs[i]
                for (step in 0 until subdivisions) {
                    val t = step.toDouble() / subdivisions
                    val t2 = t * t
                    val t3 = t2 * t
                    val h00 = 2 * t3 - 3 * t2 + 1
                    val h10 = t3 - 2 * t2 + t
                    val h01 = -2 * t3 + 3 * t2
                    val h11 = t3 - t2
                    val value = (h00 * ys[i] + h10 * width * tangents[i] + h01 * ys[i + 1] + h11 * width * tangents[i + 1])
                        .coerceIn(min(ys[i], ys[i + 1]), max(ys[i], ys[i + 1])).coerceAtLeast(0.0)
                    add(ChartCoordinate((xs[i] + t * width).toLong(), value))
                }
            }
            add(ChartCoordinate(segment.last().timestamp, ys.last()))
        }
    }
}

internal fun interpolateChartSamples(from: List<ChartSample>, to: List<ChartSample>, progress: Float): List<ChartSample> {
    val previous = from.withIndex()
        .sortedBy { it.value.timestamp }
        .groupBy { it.value.timestamp }
        .values
        .map { it.last().value }
    val fraction = progress.coerceIn(0f, 1f).toDouble()
    fun valueAt(timestamp: Long): Double? {
        previous.lastOrNull { it.timestamp == timestamp }?.let { exact ->
            return exact.value?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
        }
        for (index in 0 until previous.lastIndex) {
            val left = previous[index]
            val right = previous[index + 1]
            if (timestamp !in left.timestamp..right.timestamp) continue
            val leftValue = left.value?.takeIf { it.isFinite() } ?: return null
            val rightValue = right.value?.takeIf { it.isFinite() } ?: return null
            val span = right.timestamp - left.timestamp
            if (span <= 0L) return leftValue
            val position = (timestamp - left.timestamp).toDouble() / span
            return (leftValue + (rightValue - leftValue) * position).coerceAtLeast(0.0)
        }
        return null
    }
    return to.map { target ->
        val value = target.value?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
        if (value == null) {
            ChartSample(target.timestamp, null)
        } else {
            val start = valueAt(target.timestamp) ?: value
            ChartSample(target.timestamp, start + (value - start) * fraction)
        }
    }
}

@Composable
fun SimpleChart(
    values: List<Double?>,
    labels: List<String>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    bar: Boolean = false,
    minY: Double? = null,
    maxY: Double? = null,
    stepWidth: Dp = 56.dp,
    height: Dp = 210.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    timestamps: List<Long> = emptyList()
) {
    if (values.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val safeValues = remember(values) { values.map { it?.takeIf { value -> value.isFinite() }?.coerceAtLeast(0.0) } }
    if (safeValues.none { it != null }) return
    val effectiveTimes = remember(values, timestamps) {
        if (timestamps.size == values.size) timestamps else values.indices.map { it.toLong() }
    }
    val chartSamples = remember(safeValues, effectiveTimes) {
        safeValues.mapIndexed { index, value -> ChartSample(effectiveTimes[index], value) }
    }
    var transitionFrom by remember { mutableStateOf(chartSamples) }
    var transitionTo by remember { mutableStateOf(chartSamples) }
    val transitionProgress = remember { Animatable(1f) }
    val chartMotionSpec = ExpressiveMotion.contentChange<Float>()
    LaunchedEffect(chartSamples) {
        if (chartSamples == transitionTo) return@LaunchedEffect
        transitionFrom = interpolateChartSamples(transitionFrom, transitionTo, transitionProgress.value)
        transitionTo = chartSamples
        transitionProgress.snapTo(0f)
        transitionProgress.animateTo(1f, chartMotionSpec)
    }
    val displayedSamples = remember(transitionFrom, transitionTo, transitionProgress.value) {
        interpolateChartSamples(transitionFrom, transitionTo, transitionProgress.value)
    }
    val curveSegments = remember(displayedSamples) { monotoneChartSegments(displayedSamples) }
    val revealProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { revealProgress.animateTo(1f, chartMotionSpec) }
    val scrollState = rememberScrollState()
    val transitionValues = remember(safeValues, transitionFrom, transitionTo) {
        (transitionFrom + transitionTo).mapNotNull { it.value?.takeIf { value -> value.isFinite() } }
    }
    val lo = minY ?: if (bar) 0.0 else transitionValues.minOrNull() ?: 0.0
    val upper = maxY ?: transitionValues.maxOrNull() ?: lo + 1.0
    val hi = if (upper <= lo) lo + 1.0 else upper

    Box(modifier.horizontalScroll(scrollState)) {
        Canvas(Modifier.width(stepWidth * values.size).height(height)) {
            val topPad = 34.dp.toPx()
            val bottomPad = 42.dp.toPx()
            val leftPad = 12.dp.toPx()
            val rightPad = 12.dp.toPx()
            val plotWidth = size.width - leftPad - rightPad
            val plotHeight = size.height - topPad - bottomPad
            val baseline = topPad + plotHeight
            val step = plotWidth / max(values.size, 1)
            val firstTime = effectiveTimes.minOrNull()?.toDouble() ?: 0.0
            val lastTime = effectiveTimes.maxOrNull()?.toDouble() ?: firstTime
            val timeSpan = (lastTime - firstTime).takeIf { it > 0.0 } ?: max(1, values.lastIndex).toDouble()
            fun xOfTimestamp(timestamp: Long) = leftPad + (((timestamp.toDouble() - firstTime) / timeSpan).toFloat() * plotWidth)
            fun xOf(index: Int) = xOfTimestamp(effectiveTimes[index])
            fun yOf(value: Double) = topPad + (1f - ((value.coerceIn(lo, hi) - lo) / (hi - lo)).toFloat()) * plotHeight

            for (gridLine in 0..3) {
                val y = topPad + plotHeight * gridLine / 3f
                drawLine(grid, Offset(leftPad, y), Offset(size.width - rightPad, y), 1.dp.toPx())
            }
            val progress = revealProgress.value
            if (bar) {
                displayedSamples.forEachIndexed { index, sample ->
                    val value = sample.value
                    if (value != null) {
                        val x = xOf(index)
                        val y = yOf(value)
                        val barWidth = min(22.dp.toPx(), step * 0.56f)
                        val visibleY = baseline - (baseline - y) * progress
                        drawRoundRect(color.copy(alpha = 0.16f), Offset(x - barWidth / 2f, topPad), androidx.compose.ui.geometry.Size(barWidth, max(0f, visibleY - topPad)), CornerRadius(barWidth / 2f))
                        drawRoundRect(Brush.verticalGradient(listOf(color.copy(alpha = 0.55f), color), startY = visibleY, endY = baseline), Offset(x - barWidth / 2f, visibleY), androidx.compose.ui.geometry.Size(barWidth, max(0f, baseline - visibleY)), CornerRadius(barWidth / 2f))
                    }
                }
            } else {
                curveSegments.forEach { segment ->
                val path = Path()
                segment.forEachIndexed { i, point ->
                        val x = xOfTimestamp(point.timestamp)
                        val y = yOf(point.value)
                        val animatedY = baseline - (baseline - y) * progress
                        if (i == 0) path.moveTo(x, animatedY) else path.lineTo(x, animatedY)
                    }
                    drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            drawLine(grid, Offset(leftPad, baseline), Offset(size.width - rightPad, baseline), 1.dp.toPx())
            val measured = displayedSamples.mapIndexedNotNull { index, sample ->
                sample.value?.let { index to measurer.measure(valueLabel(it), valueStyle) }
            }
            var priorRight = Float.NEGATIVE_INFINITY
            measured.forEach { (index, text) ->
                val x = xOf(index)
                val y = yOf(displayedSamples[index].value!!)
                if (x - text.size.width / 2f > priorRight + 4.dp.toPx()) {
                    drawText(text, topLeft = Offset((x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width), (y - text.size.height - 6.dp.toPx()).coerceAtLeast(0f)))
                    priorRight = x + text.size.width / 2f
                }
                if (bar) drawCircle(color, 2.5.dp.toPx(), Offset(x, y))
            }
            labels.take(values.size).forEachIndexed { index, label ->
                val text = measurer.measure(label, labelStyle)
                val x = xOf(index)
                drawText(text, topLeft = Offset((x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width), baseline + 7.dp.toPx()))
            }
        }
    }
}
