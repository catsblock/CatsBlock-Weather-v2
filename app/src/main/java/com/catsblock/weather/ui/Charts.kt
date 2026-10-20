package com.catsblock.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SimpleChart(
    values: List<Double>,
    labels: List<String>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    bar: Boolean = false,
    minY: Double? = null,
    maxY: Double? = null,
    stepWidth: Dp = 56.dp,
    height: Dp = 210.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (values.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val lo = minY ?: if (bar) 0.0 else values.min()
    var hi = maxY ?: values.max()
    if (hi - lo < 1e-6) hi = lo + 1.0

    Box(modifier.horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.width(stepWidth * values.size).height(height)) {
            val topPad = 26.dp.toPx()
            val bottomPad = 40.dp.toPx()
            val plotH = size.height - topPad - bottomPad
            val step = size.width / values.size
            val baseline = topPad + plotH

            fun yOf(v: Double): Float =
                topPad + (1f - ((v.coerceIn(lo, hi) - lo) / (hi - lo)).toFloat()) * plotH

            drawLine(grid, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())

            if (bar) {
                values.forEachIndexed { i, v ->
                    val y = yOf(v)
                    drawRoundRect(
                        color,
                        Offset(step * i + step * 0.2f, y),
                        Size(step * 0.6f, (baseline - y).coerceAtLeast(2.dp.toPx())),
                        CornerRadius(6.dp.toPx())
                    )
                }
            } else {
                val line = Path()
                val fill = Path()
                values.forEachIndexed { i, v ->
                    val x = step * (i + 0.5f)
                    val y = yOf(v)
                    if (i == 0) {
                        line.moveTo(x, y)
                        fill.moveTo(x, baseline)
                        fill.lineTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        fill.lineTo(x, y)
                    }
                }
                fill.lineTo(step * (values.size - 0.5f), baseline)
                fill.close()
                drawPath(
                    fill,
                    Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent), startY = topPad, endY = baseline)
                )
                drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            values.forEachIndexed { i, v ->
                val cx = step * (i + 0.5f)
                val y = yOf(v)
                if (!bar) drawCircle(color, 4.dp.toPx(), Offset(cx, y))
                val value = measurer.measure(valueLabel(v), valueStyle)
                drawText(value, topLeft = Offset(cx - value.size.width / 2f, (y - value.size.height - 4.dp.toPx()).coerceAtLeast(0f)))
                labels.getOrNull(i)?.let { label ->
                    val l = measurer.measure(label, labelStyle)
                    drawText(l, topLeft = Offset(cx - l.size.width / 2f, baseline + 6.dp.toPx()))
                }
            }
        }
    }
}
