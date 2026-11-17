package com.catsblock.weather.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.data.normalizeWindDirection
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WindRose(fromDegrees: Double?, modifier: Modifier = Modifier, diameter: Dp = 92.dp) {
    val direction = normalizeWindDirection(fromDegrees)
    var unwrapped by remember { mutableFloatStateOf(direction?.toFloat() ?: 0f) }
    LaunchedEffect(direction) {
        if (direction != null) unwrapped += ((direction.toFloat() - unwrapped) % 360f + 540f) % 360f - 180f
    }
    val rotation by animateFloatAsState(
        unwrapped,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
        label = "windRoseDirection"
    )
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface
    val needle = scheme.primary
    val hub = scheme.surface
    val labelStyle = MaterialTheme.typography.labelSmall
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Text(compassLabel(0.0), Modifier.align(Alignment.TopCenter), style = labelStyle, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Text(compassLabel(180.0), Modifier.align(Alignment.BottomCenter), style = labelStyle, color = scheme.onSurfaceVariant)
        Text(compassLabel(270.0), Modifier.align(Alignment.CenterStart), style = labelStyle, color = scheme.onSurfaceVariant)
        Text(compassLabel(90.0), Modifier.align(Alignment.CenterEnd), style = labelStyle, color = scheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.34f
            drawCircle(ink.copy(alpha = 0.22f), radius, center, style = Stroke(1.2.dp.toPx()))
            for (index in 0 until 36) {
                val major = index % 9 == 0
                val medium = index % 3 == 0
                val length = (if (major) 6.dp else if (medium) 4.dp else 2.dp).toPx()
                val alpha = if (major) 0.55f else if (medium) 0.32f else 0.18f
                val radians = Math.toRadians(index * 10.0)
                val dx = sin(radians).toFloat()
                val dy = -cos(radians).toFloat()
                drawLine(
                    ink.copy(alpha = alpha),
                    Offset(center.x + dx * (radius - length), center.y + dy * (radius - length)),
                    Offset(center.x + dx * (radius - 1.dp.toPx()), center.y + dy * (radius - 1.dp.toPx())),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            if (direction != null) {
                rotate(rotation, center) {
                    val half = radius * 0.17f
                    val tip = center.y - radius * 0.78f
                    val tail = center.y + radius * 0.52f
                    val head = Path().apply {
                        moveTo(center.x, tip)
                        lineTo(center.x + half, center.y)
                        lineTo(center.x - half, center.y)
                        close()
                    }
                    val back = Path().apply {
                        moveTo(center.x, tail)
                        lineTo(center.x + half, center.y)
                        lineTo(center.x - half, center.y)
                        close()
                    }
                    drawPath(back, ink.copy(alpha = 0.22f), style = Fill)
                    drawPath(head, needle, style = Fill)
                }
                drawCircle(hub, radius * 0.13f, center)
                drawCircle(needle, radius * 0.13f, center, style = Stroke(1.6.dp.toPx()))
            }
        }
    }
    }
}
