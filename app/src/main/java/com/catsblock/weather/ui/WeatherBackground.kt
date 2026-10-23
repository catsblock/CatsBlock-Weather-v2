// ===== WeatherBackground.kt =====
package com.catsblock.weather.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.runtime.withFrameNanos

val LocalBackdrop = compositionLocalOf<GraphicsLayer?> { null }
val LocalCardRegistry = compositionLocalOf { CardRegistry() }

private fun gradientFor(type: WeatherType): List<Color> = when (type) {
    WeatherType.SUNNY -> listOf(Color(0xFF2F80ED), Color(0xFF56CCF2), Color(0xFF9CD8F5))
    WeatherType.MOSTLY_SUNNY -> listOf(Color(0xFF3A7BD5), Color(0xFF6DB3F2), Color(0xFFA6D1F0))
    WeatherType.PARTLY_CLOUDY -> listOf(Color(0xFF4A78A8), Color(0xFF7FA3C7), Color(0xFFB4C7D8))
    WeatherType.CLOUDY -> listOf(Color(0xFF4B5563), Color(0xFF6B7684), Color(0xFF8A95A1))
    WeatherType.CLEAR_NIGHT -> listOf(Color(0xFF050B1F), Color(0xFF0B1B3F), Color(0xFF1B2D5C))
    WeatherType.MOSTLY_CLEAR_NIGHT -> listOf(Color(0xFF070E24), Color(0xFF12214A), Color(0xFF25386B))
    WeatherType.PARTLY_CLOUDY_NIGHT -> listOf(Color(0xFF0E1630), Color(0xFF1F2C4D), Color(0xFF39476A))
    WeatherType.DRIZZLE -> listOf(Color(0xFF3B4A5C), Color(0xFF55677B), Color(0xFF75879A))
    WeatherType.HEAVY_RAIN -> listOf(Color(0xFF232E3B), Color(0xFF364555), Color(0xFF4F6174))
    WeatherType.FLURRIES -> listOf(Color(0xFF6C8299), Color(0xFF93A9BE), Color(0xFFBFD0DF))
    WeatherType.HEAVY_SNOW, WeatherType.BLOWING_SNOW -> listOf(Color(0xFF55697D), Color(0xFF7D93A9), Color(0xFFA9BCCD))
    WeatherType.WINTRY_MIX, WeatherType.SLEET_HAIL -> listOf(Color(0xFF3F4F60), Color(0xFF5E7184), Color(0xFF8799AA))
    WeatherType.STORM -> listOf(Color(0xFF0F131C), Color(0xFF1E2531), Color(0xFF34404F))
    WeatherType.WINDY -> listOf(Color(0xFF5A6B78), Color(0xFF7F909C), Color(0xFFA7B4BD))
}

private fun DrawScope.drawLightning(sim: WeatherSim) {
    val age = sim.boltAge
    if (age > 0.6f || sim.bolts.isEmpty()) return
    val progress = (age / 0.12f).coerceIn(0f, 1f)
    val fade = (1f - ((age - 0.12f) / 0.45f)).coerceIn(0f, 1f)
    val flicker = if ((age * 28f).toInt() % 2 == 0) 1f else 0.55f
    val a = fade * flicker
    // clarão geral
    drawRect(Color(0xFFCFE0FF).copy(alpha = 0.32f * a))
    for (poly in sim.bolts) {
        val n = (poly.size * progress).toInt().coerceAtLeast(2).coerceAtMost(poly.size)
        val path = Path().apply {
            moveTo(poly[0].x, poly[0].y)
            for (i in 1 until n) lineTo(poly[i].x, poly[i].y)
        }
        drawPath(path, Color(0xFF8FB4FF).copy(alpha = 0.18f * a), style = Stroke(14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color(0xFFB9D0FF).copy(alpha = 0.40f * a), style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color.White.copy(alpha = a), style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawBackdrop(type: WeatherType, sim: WeatherSim) {
    drawRect(Brush.verticalGradient(gradientFor(type)))
    when (type.fx) {
        WeatherType.Fx.RAIN, WeatherType.Fx.STORM -> {
            val col = Color.White.copy(alpha = 0.38f)
            for (i in 0 until (50 + 230 * type.intensity).toInt().coerceAtMost(280)) {
                val x = sim.dx[i]; val y = sim.dy[i]; val l = sim.dl[i]
                drawLine(col, Offset(x, y), Offset(x - sim.windX * l / sim.dv[i] , y + l), strokeWidth = 1.3f, cap = StrokeCap.Round)
            }
        }
        WeatherType.Fx.SNOW -> {
            for (i in 0 until sim.flakeCount) {
                drawCircle(Color.White.copy(alpha = 0.55f + sim.fr[i] * 0.08f), sim.fr[i], Offset(sim.fx_[i], sim.fy[i]))
            }
        }
        else -> {}
    }
    if (type.fx == WeatherType.Fx.STORM) drawLightning(sim)
}

@Composable
fun WeatherBackground(
    type: WeatherType,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val registry = remember { CardRegistry() }
    val sim = remember(type) { WeatherSim(type.fx, type.intensity) }
    val layer = rememberGraphicsLayer()
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(sim) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                sim.step(dt, registry.rects)
                tick++
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    tick
                    sim.resize(size.width, size.height)
                    layer.record { drawBackdrop(type, sim) }
                    drawLayer(layer)
                }
        )
        CompositionLocalProvider(
            LocalBackdrop provides layer,
            LocalCardRegistry provides registry
        ) {
            content()
        }
        
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    tick
                    for (b in sim.beads) {
                        val r = registry.rects[b.card] ?: continue
                        val cx = r.left + b.rx
                        val cy = r.top + b.ry
                        if (b.trail > 1f) {
                            drawLine(
                                Color.White.copy(alpha = 0.16f),
                                Offset(cx, cy - b.trail), Offset(cx, cy),
                                strokeWidth = b.r * 0.7f, cap = StrokeCap.Round
                            )
                        }
                        drawCircle(Color.White.copy(alpha = 0.22f), b.r, Offset(cx, cy))
                        drawCircle(Color.White.copy(alpha = 0.55f), b.r, Offset(cx, cy), style = Stroke(1f))
                        drawCircle(Color.White.copy(alpha = 0.85f), b.r * 0.28f, Offset(cx - b.r * 0.3f, cy - b.r * 0.3f))
                    }
                }
        )
    }
}
