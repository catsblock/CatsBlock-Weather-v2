package com.catsblock.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsblock.weather.R
import kotlin.math.roundToInt

@Composable
fun CloudCoverCard(
    cloudCoverPercent: Double,
    hourlyValues: List<Double>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val value = cloudCoverPercent.coerceIn(0.0, 100.0)
    val state = when {
        value < 15.0 -> stringResource(R.string.cloud_cover_clear_state)
        value < 40.0 -> stringResource(R.string.cloud_cover_few_state)
        value < 70.0 -> stringResource(R.string.cloud_cover_partial_state)
        value < 90.0 -> stringResource(R.string.cloud_cover_mostly_state)
        else -> stringResource(R.string.cloud_cover_overcast_state)
    }
    val direction = when {
        hourlyValues.size < 2 -> 0
        hourlyValues.last() - hourlyValues.first() >= 5.0 -> 1
        hourlyValues.first() - hourlyValues.last() >= 5.0 -> -1
        else -> 0
    }
    val points = remember(hourlyValues, value) {
        (hourlyValues.take(12).ifEmpty { listOf(value) }).map { it.coerceIn(0.0, 100.0) }
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val fillBrush = remember(lineColor) {
        Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.015f)))
    }
    val chartPaths = remember(points) {
        val line = Path()
        val fill = Path()
        val count = points.size.coerceAtLeast(2)
        points.forEachIndexed { index, point ->
            val x = index.toFloat() / (count - 1).toFloat()
            val y = 0.94f - (point.toFloat() / 100f) * 0.82f
            if (index == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, 1f)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(1f, 1f)
        fill.close()
        line to fill
    }

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cloud_cover_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                val trendIcon = when (direction) {
                    1 -> Icons.Rounded.TrendingUp
                    -1 -> Icons.Rounded.TrendingDown
                    else -> Icons.Rounded.TrendingFlat
                }
                Icon(trendIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${value.roundToInt()}${stringResource(R.string.percent_symbol)}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = state,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(58.dp)) {
                Canvas(Modifier.fillMaxWidth().height(58.dp)) {
                    scale(size.width, size.height, pivot = Offset.Zero) {
                        drawPath(chartPaths.second, fillBrush)
                        drawPath(
                            chartPaths.first,
                            lineColor,
                            style = Stroke(width = 0.012f, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.cloud_cover_tap_history),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
