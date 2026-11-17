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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.normalizeWindDirection

@Composable
fun WindRose(fromDegrees: Double?, modifier: Modifier = Modifier, diameter: Dp = 92.dp) {
    val direction = normalizeWindDirection(fromDegrees)
    val rotation by animateFloatAsState(direction?.toFloat() ?: 0f, tween(300), label = "windRoseDirection")
    val ink = MaterialTheme.colorScheme.onSurface
    val indicator = MaterialTheme.colorScheme.primary
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Text("N", Modifier.align(Alignment.TopCenter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("S", Modifier.align(Alignment.BottomCenter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("W", Modifier.align(Alignment.CenterStart), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("E", Modifier.align(Alignment.CenterEnd), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.32f
            drawCircle(ink.copy(alpha = 0.10f), radius, center, style = Stroke(1.dp.toPx()))
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
