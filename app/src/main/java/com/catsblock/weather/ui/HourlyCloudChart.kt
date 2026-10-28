package com.catsblock.weather.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsblock.weather.i18n.tr
import java.time.LocalDate
import kotlin.math.roundToInt

private val AxisHours = intArrayOf(0, 6, 12, 18, 24)
private val GridLevels = floatArrayOf(0f, 0.5f, 1f)

private fun Modifier.hourlyLine(
    values: List<Int>,
    peak: Int,
    picked: MutableIntState,
    colors: ColorScheme
): Modifier = drawWithCache {
    val valid = values.indices.filter { values[it] >= 0 }
    if (valid.size < 2) {
        onDrawBehind { }
    } else {
        val pad = 6.dp.toPx()
        val w = size.width
        val h = size.height
        val last = (values.size - 1).coerceAtLeast(1)
        val xs = FloatArray(values.size) { w * it / last }
        val ys = FloatArray(values.size) {
            pad + (h - 2f * pad) * (1f - values[it].coerceIn(0, 100) / 100f)
        }
        val line = Path().apply {
            moveTo(xs[valid[0]], ys[valid[0]])
            for (n in 1 until valid.size) {
                val a = valid[n - 1]
                val b = valid[n]
                val mid = (xs[a] + xs[b]) / 2f
                cubicTo(mid, ys[a], mid, ys[b], xs[b], ys[b])
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(xs[valid.last()], h)
            lineTo(xs[valid[0]], h)
            close()
        }
        val fillBrush = Brush.verticalGradient(
            listOf(colors.primary.copy(alpha = 0.28f), colors.primary.copy(alpha = 0.02f)),
            0f,
            h
        )
        val stroke = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val gridStroke = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())))
        val guide = Stroke(1.5f.dp.toPx())
        val dotRadius = 5.dp.toPx()
        val haloRadius = 10.dp.toPx()
        val levelY = FloatArray(GridLevels.size) { pad + (h - 2f * pad) * (1f - GridLevels[it]) }
        onDrawBehind {
            for (y in levelY) {
                drawLine(
                    colors.outlineVariant.copy(alpha = 0.68f),
                    Offset(0f, y),
                    Offset(w, y),
                    gridStroke.width,
                    pathEffect = gridStroke.pathEffect
                )
            }
            drawPath(fill, fillBrush)
            drawPath(line, colors.primary, style = stroke)
            val requested = picked.intValue
            val index = if (requested in values.indices && values[requested] >= 0) requested else peak
            if (index in values.indices && values[index] >= 0) {
                drawLine(
                    colors.primary.copy(alpha = 0.55f),
                    Offset(xs[index], 0f),
                    Offset(xs[index], h),
                    guide.width
                )
                drawCircle(colors.primary.copy(alpha = 0.22f), haloRadius, Offset(xs[index], ys[index]))
                drawCircle(colors.onPrimary, dotRadius, Offset(xs[index], ys[index]))
            }
        }
    }
}

@Composable
fun HourlyCloudChart(values: List<Int>, date: LocalDate, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val picked = remember(date) { mutableIntStateOf(-1) }
    val peak = remember(values) {
        values.indices.filter { values[it] >= 0 }.maxByOrNull { values[it] } ?: -1
    }
    val requested = picked.intValue
    val active = if (requested in values.indices && values[requested] >= 0) requested else peak
    val axis = remember(date) { AxisHours.map { clockLabel(date.atStartOfDay().plusHours(it.toLong())) } }
    val description = tr("24-hour cloud cover")

    Column(modifier.fillMaxWidth()) {
        if (active in values.indices) {
            Text(
                "${clockLabel(date.atTime(active, 0))} · ${values[active]}%",
                color = colors.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Row(Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp)) {
            Column(
                Modifier.fillMaxHeight().padding(end = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("100%", color = colors.onSurfaceVariant, fontSize = 11.sp)
                Text("50%", color = colors.onSurfaceVariant, fontSize = 11.sp)
                Text("0%", color = colors.onSurfaceVariant, fontSize = 11.sp)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = description }
                    .pointerInput(values) {
                        detectTapGestures { offset ->
                            if (values.isNotEmpty()) {
                                val last = (values.size - 1).coerceAtLeast(1)
                                picked.intValue = (offset.x / size.width * last).roundToInt().coerceIn(0, values.lastIndex)
                            }
                        }
                    }
                    .pointerInput(values) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                if (values.isNotEmpty()) {
                                    val last = (values.size - 1).coerceAtLeast(1)
                                    picked.intValue = (offset.x / size.width * last).roundToInt().coerceIn(0, values.lastIndex)
                                }
                            },
                            onHorizontalDrag = { change, _ ->
                                if (values.isNotEmpty()) {
                                    val last = (values.size - 1).coerceAtLeast(1)
                                    picked.intValue = (change.position.x / size.width * last).roundToInt().coerceIn(0, values.lastIndex)
                                }
                            }
                        )
                    }
                    .hourlyLine(values, peak, picked, colors)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, start = 34.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            axis.forEach { Text(it, color = colors.onSurfaceVariant, fontSize = 11.sp, maxLines = 1) }
        }
    }
}
