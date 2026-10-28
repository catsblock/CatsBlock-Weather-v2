package com.catsblock.weather.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val LOADER_POINTS = 96
private const val TWO_PI = (2.0 * PI).toFloat()

@Composable
fun ExpressiveLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = Color.Unspecified,
    contained: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (color == Color.Unspecified) {
        if (contained) scheme.onPrimaryContainer else scheme.primary
    } else {
        color
    }
    val container = scheme.primaryContainer
    val transition = rememberInfiniteTransition(label = "expressive-loader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "rotation"
    )
    val morph by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "morph"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val path = remember { Path() }
    Canvas(modifier.size(size)) {
        val half = min(this.size.width, this.size.height) / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        if (contained) drawCircle(container, half, center)
        val radius = half * (if (contained) 0.58f else 0.88f) * pulse
        val a3 = 0.20f * (1f - morph)
        val a5 = 0.15f * morph
        val a7 = 0.05f * sin(morph * TWO_PI / 2f)
        path.rewind()
        for (i in 0..LOADER_POINTS) {
            val theta = i * TWO_PI / LOADER_POINTS
            val r = radius * (1f + a3 * cos(3f * (theta + rotation)) + a5 * cos(5f * (theta - rotation)) + a7 * cos(7f * theta))
            val x = center.x + r * cos(theta)
            val y = center.y + r * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, tint)
    }
}

@Composable
fun ExpressiveProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified
) {
    val scheme = MaterialTheme.colorScheme
    val active = if (color == Color.Unspecified) scheme.primary else color
    val track = if (trackColor == Color.Unspecified) scheme.surfaceContainerHighest else trackColor
    val transition = rememberInfiniteTransition(label = "expressive-progress")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "phase"
    )
    val path = remember { Path() }
    Canvas(modifier.heightIn(min = 12.dp)) {
        val stroke = 4.dp.toPx()
        val amplitude = 2.5.dp.toPx()
        val wavelength = 28.dp.toPx()
        val midY = size.height / 2f
        val end = (size.width - stroke) * progress().coerceIn(0f, 1f) + stroke / 2f
        val gap = 4.dp.toPx()
        if (end + gap < size.width - stroke / 2f) {
            drawLine(track, Offset(end + gap, midY), Offset(size.width - stroke / 2f, midY), stroke, StrokeCap.Round)
        }
        path.rewind()
        val startX = stroke / 2f
        var x = startX
        path.moveTo(x, midY + amplitude * sin(phase))
        while (x < end) {
            x = min(x + 2.dp.toPx(), end)
            val y = midY + amplitude * sin((x - startX) / wavelength * TWO_PI + phase)
            path.lineTo(x, y)
        }
        drawPath(path, active, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
