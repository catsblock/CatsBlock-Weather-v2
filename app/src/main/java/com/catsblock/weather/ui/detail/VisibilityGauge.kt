package com.catsblock.weather.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VisibilityGauge(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp
) {
    val scheme = MaterialTheme.colorScheme
    val level = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        level.animateTo(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.65f, stiffness = 90f))
    }
    val transition = rememberInfiniteTransition(label = "visibility")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "visibility-drift"
    )
    val ping by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "visibility-ping"
    )
    Canvas(modifier.size(size)) {
        val stroke = 10.dp.toPx()
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val outer = this.size.minDimension / 2f - stroke / 2f
        val inner = outer - stroke - 4.dp.toPx()
        val ringTopLeft = Offset(center.x - outer, center.y - outer)
        val ringSize = Size(outer * 2f, outer * 2f)
        drawArc(
            scheme.onSurface.copy(alpha = 0.10f),
            150f,
            240f,
            false,
            ringTopLeft,
            ringSize,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        if (level.value > 0.001f) {
            drawArc(
                color,
                150f,
                240f * level.value,
                false,
                ringTopLeft,
                ringSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        val disc = Path().apply { addOval(Rect(center, inner)) }
        clipPath(disc) {
            drawCircle(scheme.onSurface.copy(alpha = 0.06f), inner, center)
            val clear = (0.12f + 0.78f * level.value).coerceIn(0f, 1f)
            val haze = color.copy(alpha = 0.55f * (1f - level.value * 0.7f))
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    clear * 0.85f to Color.Transparent,
                    1f to haze,
                    center = center,
                    radius = inner
                ),
                inner,
                center
            )
            for (i in 0 until 3) {
                val a = drift + i * 2.1f
                val puff = Offset(center.x + cos(a) * inner * 0.45f, center.y + sin(a * 1.3f) * inner * 0.35f)
                val r = inner * (0.42f - 0.22f * level.value)
                drawCircle(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.30f * (1f - level.value * 0.8f)), Color.Transparent),
                        center = puff,
                        radius = r
                    ),
                    r,
                    puff
                )
            }
            drawCircle(
                color.copy(alpha = 0.55f * (1f - ping)),
                inner * 0.18f + inner * 0.7f * ping,
                center,
                style = Stroke(1.5f.dp.toPx())
            )
            drawCircle(color, 4.dp.toPx(), center)
        }
        drawCircle(color.copy(alpha = 0.30f), inner, center, style = Stroke(1.5f.dp.toPx()))
    }
}
