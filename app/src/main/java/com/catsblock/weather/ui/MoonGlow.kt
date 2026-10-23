package com.catsblock.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.catsblock.weather.data.Eclipse
import com.catsblock.weather.data.Moon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

@Composable
fun MoonDisc(cycle: Double, modifier: Modifier = Modifier, eclipse: Eclipse? = null) {
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val r = min(size.width, size.height) / 2f * 0.94f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            Brush.radialGradient(listOf(Color(0x66FFFFFF), Color.Transparent), center = c, radius = r * 1.06f),
            r * 1.06f, c
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF5E667B), Color(0xFF353B4B)), center = c, radius = r),
            r, c
        )
        val circle = Path().apply { addOval(Rect(c, r)) }
        val half = Path().apply {
            addRect(
                if (cycle < 0.5) Rect(c.x, c.y - r, c.x + r, c.y + r) else Rect(c.x - r, c.y - r, c.x, c.y + r)
            )
        }
        val k = cos(2.0 * PI * cycle).toFloat()
        val ellipse = Path().apply {
            addOval(Rect(c.x - r * abs(k), c.y - r, c.x + r * abs(k), c.y + r))
        }
        val lit = Path.combine(PathOperation.Intersect, circle, half)
        val shape = Path.combine(if (k > 0f) PathOperation.Difference else PathOperation.Union, lit, ellipse)
        drawPath(
            shape,
            Brush.radialGradient(listOf(Color(0xFFFFFBEA), Color(0xFFE9E1C4)), center = c, radius = r)
        )
        drawCircle(Color(0x66FFFFFF), r, c, style = Stroke(1.2f.dp.toPx()))
        if (eclipse != null) drawEclipse(eclipse, c, r)
    }
}

@Composable
fun MoonImage(ms: Long, cycle: Double, modifier: Modifier = Modifier) {
    val url by produceState<String?>(null, ms / 3_600_000L) { value = Moon.imageUrl(ms) }
    val eclipse = remember(ms / 60_000L) { Moon.eclipse(ms) }
    Box(
        modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .clip(CircleShape)
            .background(Color(0xFF353B4B)),
        contentAlignment = Alignment.Center
    ) {
        MoonDisc(cycle, Modifier.fillMaxSize())
        val u = url
        if (u != null) {
            SubcomposeAsyncImage(
                model = u,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawIntoCanvas { canvas ->
                            val paint = Paint().apply { blendMode = BlendMode.Screen }
                            canvas.saveLayer(size.toRect(), paint)
                            drawContent()
                            canvas.restore()
                        }
                    },
                contentScale = ContentScale.Crop,
                loading = {},
                error = {}
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val r = min(size.width, size.height) / 2f
            if (eclipse != null) drawEclipse(eclipse, Offset(size.width / 2f, size.height / 2f), r)
            drawCircle(Color(0x55FFFFFF), r - 0.6f.dp.toPx(), Offset(size.width / 2f, size.height / 2f), style = Stroke(1.2f.dp.toPx()))
        }
    }
}
