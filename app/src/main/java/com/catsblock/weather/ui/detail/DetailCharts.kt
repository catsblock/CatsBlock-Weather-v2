package com.catsblock.weather.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsblock.weather.ui.hourLabel
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class Pill(
    val top: String,
    val bottom: String,
    val low: Float,
    val high: Float,
    val tint: Color? = null
)

data class ChartLine(
    val values: List<Float?>,
    val color: Color,
    val label: String,
    val scale: ClosedFloatingPointRange<Float>? = null
)

data class Threshold(val value: Float, val label: String)

enum class HourlyStyle { Bars, Line }

private const val HOURS = 24

@Composable
fun PillChart(
    pills: List<Pill>,
    selected: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
    trackHeight: Dp = 168.dp
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pills.forEachIndexed { index, pill ->
            val active = index == selected
            val progress by animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
                label = "pill-selection"
            )
            var started by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(index * 45L)
                started = true
            }
            val grow by animateFloatAsState(
                targetValue = if (started) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow),
                label = "pill-grow"
            )
            val color = pill.tint ?: accent
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(32.dp))
                    .clickable { onSelect(index) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${pill.bottom}, ${pill.top}"
                        this.selected = active
                        role = Role.RadioButton
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    pill.top,
                    color = if (active) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
                    val w = size.width
                    val h = size.height
                    val radius = CornerRadius(w / 2f, w / 2f)
                    drawRoundRect(scheme.onSurface.copy(alpha = 0.08f), size = size, cornerRadius = radius)
                    val lowF = if (floating) pill.low.coerceIn(0f, 1f) else 0f
                    val highF = lowF + (pill.high.coerceIn(0f, 1f) - lowF) * grow
                    val fillH = max((highF - lowF) * h, w)
                    val bottomY = (h * (1f - lowF)).coerceAtLeast(fillH)
                    val lift = 5.dp.toPx() * progress
                    val topY = (bottomY - fillH - lift).coerceAtLeast(0f)
                    val height = bottomY - topY
                    val fill = lerp(color.copy(alpha = 0.40f), color, progress)
                    drawRoundRect(fill, Offset(0f, topY), Size(w, height), radius)
                    val dotR = w * 0.12f * progress
                    if (dotR > 0.5f) {
                        drawCircle(Color.White.copy(alpha = 0.95f), dotR, Offset(w / 2f, bottomY - w * 0.5f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    pill.bottom,
                    color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun HourlyChart(
    lines: List<ChartLine>,
    style: HourlyStyle,
    yMin: Float,
    yMax: Float,
    thresholds: List<Threshold>,
    nowHour: Int?,
    format: (Int, Float) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    arrows: List<Float?>? = null,
    bandColors: List<Pair<Float, Color>> = emptyList()
) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    var scrub by remember { mutableIntStateOf(-1) }
    val focus = if (scrub >= 0) scrub else (nowHour ?: -1)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(lines, style) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = scheme.onSurfaceVariant)
    val thresholdStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
    val extra = if (arrows != null) 30.dp else 0.dp

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(30.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (focus in 0 until HOURS) {
                Text(
                    hourLabel(LocalDateTime.of(2000, 1, 1, focus, 0)),
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                lines.forEachIndexed { index, line ->
                    val v = line.values.getOrNull(focus)
                    if (v != null) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(line.color.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(format(index, v), color = line.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height + extra)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            scrub = hourAt(offset.x, size.width.toFloat(), style)
                            tryAwaitRelease()
                            scrub = -1
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> scrub = hourAt(offset.x, size.width.toFloat(), style) },
                        onDragEnd = { scrub = -1 },
                        onDragCancel = { scrub = -1 },
                        onHorizontalDrag = { change, _ -> scrub = hourAt(change.position.x, size.width.toFloat(), style) }
                    )
                }
        ) {
            val w = size.width
            val labelBand = 24.dp.toPx()
            val arrowBand = extra.toPx()
            val plotTop = 8.dp.toPx()
            val plotBottom = size.height - labelBand - arrowBand
            val plotH = plotBottom - plotTop
            val pad = 6.dp.toPx()
            val x0 = pad
            val x1 = w - pad
            val slot = (x1 - x0) / HOURS
            val range = (yMax - yMin).coerceAtLeast(0.0001f)
            fun xFor(i: Int): Float = if (style == HourlyStyle.Bars) x0 + slot * (i + 0.5f) else x0 + (x1 - x0) * i / (HOURS - 1).toFloat()
            fun yFor(fraction: Float): Float = plotTop + plotH * (1f - fraction.coerceIn(0f, 1f))
            fun fractionOf(line: ChartLine, v: Float): Float {
                val s = line.scale
                return if (s == null) (v - yMin) / range else (v - s.start) / (s.endInclusive - s.start).coerceAtLeast(0.0001f)
            }

            thresholds.forEach { t ->
                val y = yFor((t.value - yMin) / range)
                drawLine(
                    scheme.onSurface.copy(alpha = 0.22f),
                    Offset(x0, y),
                    Offset(x1, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
                val layout = measurer.measure(t.label, thresholdStyle)
                drawText(layout, topLeft = Offset(x0 + 4.dp.toPx(), y - layout.size.height - 2.dp.toPx()))
            }

            if (style == HourlyStyle.Bars) {
                val line = lines.first()
                val barW = slot * 0.66f
                for (i in 0 until HOURS) {
                    val cx = xFor(i)
                    val v = line.values.getOrNull(i)
                    val r = CornerRadius(barW / 2f, barW / 2f)
                    drawRoundRect(
                        scheme.onSurface.copy(alpha = if (i == focus) 0.14f else 0.07f),
                        Offset(cx - barW / 2f, plotTop),
                        Size(barW, plotH),
                        r
                    )
                    if (v != null) {
                        val stagger = ((reveal.value * 1.6f) - i / 40f).coerceIn(0f, 1f)
                        val f = fractionOf(line, v).coerceIn(0f, 1f) * stagger
                        val fillH = max(f * plotH, if (v > 0f) barW else barW * 0.5f)
                        val top = plotBottom - fillH
                        val color = if (i == focus) line.color else line.color.copy(alpha = 0.78f)
                        drawRoundRect(color, Offset(cx - barW / 2f, top), Size(barW, fillH), r)
                        drawCircle(Color.White.copy(alpha = 0.95f), barW * 0.17f, Offset(cx, top + barW * 0.5f))
                    }
                }
            } else {
                clipRect(left = 0f, top = 0f, right = w * reveal.value, bottom = size.height) {
                    lines.forEachIndexed { lineIndex, line ->
                        val path = Path()
                        var started = false
                        var lastX = 0f
                        var lastY = 0f
                        var firstX = -1f
                        for (i in 0 until HOURS) {
                            val v = line.values.getOrNull(i)
                            if (v == null) {
                                started = false
                                continue
                            }
                            val x = xFor(i)
                            val y = yFor(fractionOf(line, v))
                            if (!started) {
                                path.moveTo(x, y)
                                started = true
                                if (firstX < 0f) firstX = x
                            } else {
                                val mid = (lastX + x) / 2f
                                path.cubicTo(mid, lastY, mid, y, x, y)
                            }
                            lastX = x
                            lastY = y
                        }
                        if (firstX >= 0f) {
                            if (lineIndex == 0) {
                                val fill = Path().apply {
                                    addPath(path)
                                    lineTo(lastX, plotBottom)
                                    lineTo(firstX, plotBottom)
                                    close()
                                }
                                val base = if (bandColors.isEmpty()) line.color else bandColors.last().second
                                drawPath(
                                    fill,
                                    Brush.verticalGradient(
                                        listOf(base.copy(alpha = 0.32f), base.copy(alpha = 0.02f)),
                                        startY = plotTop,
                                        endY = plotBottom
                                    )
                                )
                            }
                            val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            if (bandColors.isNotEmpty() && lineIndex == 0) {
                                val stops = bandColors
                                    .map { (value, color) -> (1f - (value - yMin) / range).coerceIn(0f, 1f) to color }
                                    .sortedBy { it.first }
                                    .toTypedArray()
                                drawPath(path, Brush.verticalGradient(*stops, startY = plotTop, endY = plotBottom), style = stroke)
                            } else {
                                drawPath(path, line.color, style = stroke)
                            }
                        }
                    }
                }
            }

            if (focus in 0 until HOURS) {
                val fx = xFor(focus)
                drawLine(
                    scheme.onSurface.copy(alpha = 0.40f),
                    Offset(fx, plotTop),
                    Offset(fx, plotBottom),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                )
                if (style == HourlyStyle.Line) {
                    lines.forEach { line ->
                        val v = line.values.getOrNull(focus)
                        if (v != null) {
                            val c = Offset(fx, yFor(fractionOf(line, v)))
                            drawCircle(scheme.surfaceContainerHigh, 7.dp.toPx(), c)
                            drawCircle(line.color, 5.dp.toPx(), c)
                            drawCircle(Color.White, 2.dp.toPx(), c)
                        }
                    }
                }
            }

            for (h in 0 until HOURS step 6) {
                val layout = measurer.measure(hourLabel(LocalDateTime.of(2000, 1, 1, h, 0)), labelStyle)
                val cx = xFor(h)
                val left = (cx - layout.size.width / 2f).coerceIn(0f, w - layout.size.width)
                drawText(layout, topLeft = Offset(left, plotBottom + 6.dp.toPx()))
            }

            if (arrows != null) {
                val base = plotBottom + labelBand + 2.dp.toPx()
                for (h in 0 until HOURS step 3) {
                    val dir = arrows.getOrNull(h) ?: continue
                    val c = Offset(xFor(h), base + arrowBand / 2f - 2.dp.toPx())
                    rotate(degrees = (dir + 180f) % 360f, pivot = c) {
                        val a = 6.dp.toPx()
                        val p = Path().apply {
                            moveTo(c.x, c.y - a)
                            lineTo(c.x + a * 0.8f, c.y + a)
                            lineTo(c.x, c.y + a * 0.45f)
                            lineTo(c.x - a * 0.8f, c.y + a)
                            close()
                        }
                        drawPath(p, scheme.onSurfaceVariant.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

private fun hourAt(x: Float, width: Float, style: HourlyStyle): Int {
    val f = (x / width).coerceIn(0f, 1f)
    return if (style == HourlyStyle.Bars) min(HOURS - 1, (f * HOURS).toInt()) else (f * (HOURS - 1)).toInt().coerceIn(0, HOURS - 1)
}

@Composable
fun SegmentBar(
    colors: List<Color>,
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 180f),
        label = "segment-marker"
    )
    Canvas(modifier.fillMaxWidth().height(32.dp)) {
        val gap = 3.dp.toPx()
        val barH = 24.dp.toPx()
        val top = (size.height - barH) / 2f
        val n = colors.size
        val segW = (size.width - gap * (n - 1)) / n
        val cap = barH / 2f
        val inner = 3.dp.toPx()
        colors.forEachIndexed { i, color ->
            val left = i * (segW + gap)
            val tl = if (i == 0) cap else inner
            val tr = if (i == n - 1) cap else inner
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = left + segW,
                        bottom = top + barH,
                        topLeftCornerRadius = CornerRadius(tl, tl),
                        topRightCornerRadius = CornerRadius(tr, tr),
                        bottomRightCornerRadius = CornerRadius(tr, tr),
                        bottomLeftCornerRadius = CornerRadius(tl, tl)
                    )
                )
            }
            drawPath(path, color)
        }
        val cx = (size.width * animated).coerceIn(cap, size.width - cap)
        val c = Offset(cx, size.height / 2f)
        drawCircle(Color.Black.copy(alpha = 0.28f), 14.dp.toPx(), c)
        drawCircle(Color.White, 12.dp.toPx(), c)
        drawCircle(scheme.surfaceContainerHigh, 7.dp.toPx(), c)
    }
}

@Composable
fun ArcGauge(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    center: String? = null,
    size: Dp = 112.dp,
    segments: List<Color>? = null
) {
    val scheme = MaterialTheme.colorScheme
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 120f),
        label = "arc-gauge"
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val start = 150f
            val total = 240f
            if (segments == null) {
                drawArc(scheme.onSurface.copy(alpha = 0.10f), start, total, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (animated > 0.001f) {
                    drawArc(color, start, total * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            } else {
                val n = segments.size
                val gapAngle = 7f
                val seg = (total - gapAngle * (n - 1)) / n
                segments.forEachIndexed { i, c ->
                    val s = start + i * (seg + gapAngle)
                    val reach = ((animated * total) - i * (seg + gapAngle)).coerceIn(0f, seg)
                    drawArc(scheme.onSurface.copy(alpha = 0.10f), s, seg, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (reach > 0.5f) {
                        drawArc(c, s, max(reach, 4f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
            }
        }
        if (center != null) {
            Text(center, color = scheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun WaterGauge(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 104.dp
) {
    val scheme = MaterialTheme.colorScheme
    val level by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 90f),
        label = "water-level"
    )
    val transition = rememberInfiniteTransition(label = "water")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "water-phase"
    )
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
        drawCircle(scheme.onSurface.copy(alpha = 0.08f), r, center)
        clipPath(circle) {
            val base = this.size.height * (1f - level)
            val amp = 4.dp.toPx() * (0.4f + 0.6f * sin(level * PI.toFloat()))
            val wave = Path()
            wave.moveTo(0f, this.size.height)
            wave.lineTo(0f, base)
            var x = 0f
            val step = 4f
            while (x <= this.size.width) {
                wave.lineTo(x, base + amp * sin(x / this.size.width * 2f * PI.toFloat() * 1.6f + phase))
                x += step
            }
            wave.lineTo(this.size.width, this.size.height)
            wave.close()
            drawPath(wave, Brush.verticalGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.60f)), startY = base, endY = this.size.height))
            val back = Path()
            back.moveTo(0f, this.size.height)
            back.lineTo(0f, base)
            x = 0f
            while (x <= this.size.width) {
                back.lineTo(x, base + amp * sin(x / this.size.width * 2f * PI.toFloat() * 1.3f - phase * 0.8f + 1.4f))
                x += step
            }
            back.lineTo(this.size.width, this.size.height)
            back.close()
            drawPath(back, color.copy(alpha = 0.30f))
        }
        drawCircle(color.copy(alpha = 0.45f), r - 1.dp.toPx(), center, style = Stroke(2.dp.toPx()))
    }
}

@Composable
fun WindCompass(
    fromDegrees: Double,
    letters: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp
) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val target = ((fromDegrees + 180.0) % 360.0).toFloat()
    val angle by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 90f),
        label = "compass"
    )
    val style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
    val arrow = scheme.primary
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(scheme.surfaceContainerHighest.copy(alpha = 0.8f), r, c)
        drawCircle(scheme.outlineVariant.copy(alpha = 0.5f), r - 1.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        for (i in 0 until 4) {
            val ticks = i * 90f
            rotate(ticks, c) {
                drawLine(scheme.onSurfaceVariant.copy(alpha = 0.5f), Offset(c.x, c.y - r + 3.dp.toPx()), Offset(c.x, c.y - r + 8.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        val offsets = listOf(
            Offset(c.x, c.y - r + 20.dp.toPx()),
            Offset(c.x + r - 20.dp.toPx(), c.y),
            Offset(c.x, c.y + r - 20.dp.toPx()),
            Offset(c.x - r + 20.dp.toPx(), c.y)
        )
        letters.take(4).forEachIndexed { i, letter ->
            val layout = measurer.measure(letter, style)
            drawText(layout, topLeft = Offset(offsets[i].x - layout.size.width / 2f, offsets[i].y - layout.size.height / 2f))
        }
        rotate(angle, c) {
            val len = r * 0.46f
            val head = Path().apply {
                moveTo(c.x, c.y - len)
                lineTo(c.x + 9.dp.toPx(), c.y - len + 18.dp.toPx())
                lineTo(c.x, c.y - len + 13.dp.toPx())
                lineTo(c.x - 9.dp.toPx(), c.y - len + 18.dp.toPx())
                close()
            }
            drawLine(arrow, Offset(c.x, c.y - len + 12.dp.toPx()), Offset(c.x, c.y + len * 0.7f), 5.dp.toPx(), StrokeCap.Round)
            drawPath(head, arrow)
        }
        drawCircle(scheme.surfaceContainerHigh, 4.dp.toPx(), c)
    }
}

@Composable
fun StackedBar(
    parts: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier
) {
    val total = parts.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(parts) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 140f))
    }
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        val gap = 3.dp.toPx()
        val visible = parts.filter { it.first > 0f }
        val usable = (size.width - gap * (visible.size - 1).coerceAtLeast(0)) * reveal.value
        var x = 0f
        visible.forEachIndexed { i, (value, color) ->
            val w = usable * value / total
            val cap = size.height / 2f
            val inner = 4.dp.toPx()
            val tl = if (i == 0) cap else inner
            val tr = if (i == visible.lastIndex) cap else inner
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = x,
                        top = 0f,
                        right = x + w,
                        bottom = size.height,
                        topLeftCornerRadius = CornerRadius(tl, tl),
                        topRightCornerRadius = CornerRadius(tr, tr),
                        bottomRightCornerRadius = CornerRadius(tr, tr),
                        bottomLeftCornerRadius = CornerRadius(tl, tl)
                    )
                )
            }
            drawPath(path, color)
            x += w + gap
        }
    }
}

fun piecewiseFraction(value: Double, breakpoints: List<Double>): Float {
    val segments = breakpoints.size + 1
    var index = 0
    while (index < breakpoints.size && value >= breakpoints[index]) index++
    val lower = if (index == 0) breakpoints.first() - (breakpoints.getOrElse(1) { breakpoints.first() + 1.0 } - breakpoints.first()) else breakpoints[index - 1]
    val upper = if (index >= breakpoints.size) breakpoints.last() + (breakpoints.last() - breakpoints.getOrElse(breakpoints.size - 2) { breakpoints.last() - 1.0 }) else breakpoints[index]
    val within = ((value - lower) / abs(upper - lower).coerceAtLeast(0.0001)).coerceIn(0.0, 1.0)
    return ((index + within) / segments).toFloat().coerceIn(0f, 1f)
}
