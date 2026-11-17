package com.catsblock.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.data.normalizeWindDirection

@Composable
fun WindRose(fromDegrees: Double?, modifier: Modifier = Modifier, diameter: Dp = 92.dp) {
    val direction = normalizeWindDirection(fromDegrees)
    var unwrapped by remember { mutableFloatStateOf(direction?.toFloat() ?: 0f) }
    LaunchedEffect(direction) {
        if (direction != null) unwrapped += ((direction.toFloat() - unwrapped) % 360f + 540f) % 360f - 180f
    }
    val rotation by animateFloatAsState(unwrapped, tween(300), label = "windRoseDirection")
    val ink = MaterialTheme.colorScheme.onSurface
    val indicator = MaterialTheme.colorScheme.primary
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Text(compassLabel(0.0), Modifier.align(Alignment.TopCenter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(compassLabel(180.0), Modifier.align(Alignment.BottomCenter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(compassLabel(270.0), Modifier.align(Alignment.CenterStart), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(compassLabel(90.0), Modifier.align(Alignment.CenterEnd), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.32f
            drawCircle(ink.copy(alpha = 0.10f), radius, center, style = Stroke(1.dp.toPx()))
            for (index in 0 until 8) {
                val tickRadians = Math.toRadians(index * 45.0)
                val tickLength = (if (index % 2 == 0) 4.dp else 2.5.dp).toPx()
                val dx = kotlin.math.sin(tickRadians).toFloat()
                val dy = -kotlin.math.cos(tickRadians).toFloat()
                drawLine(
                    ink.copy(alpha = 0.28f),
                    Offset(center.x + dx * radius, center.y + dy * radius),
                    Offset(center.x + dx * (radius + tickLength), center.y + dy * (radius + tickLength)),
                    strokeWidth = 1.dp.toPx()
                )
            }
            if (direction != null) {
                rotate(rotation, center) {
                    val head = Path().apply {
                        moveTo(center.x, center.y - radius)
                        lineTo(center.x - 6.dp.toPx(), center.y + radius * 0.48f)
                        lineTo(center.x, center.y + radius * 0.16f)
                        lineTo(center.x + 6.dp.toPx(), center.y + radius * 0.48f)
                        close()
                    }
                    drawPath(head, indicator, style = Fill)
                    drawCircle(indicator, 2.5.dp.toPx(), center)
                }
            }
        }
    }
}
