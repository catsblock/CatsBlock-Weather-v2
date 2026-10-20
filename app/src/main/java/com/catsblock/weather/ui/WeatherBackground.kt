package com.catsblock.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.Condition
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private fun rnd(i: Int, salt: Int): Float {
    val x = sin((i * 12.9898f + salt * 78.233f).toDouble()) * 43758.5453
    return (x - floor(x)).toFloat()
}

private fun skyColors(kind: Condition, isDay: Boolean): List<Color> = when (kind) {
    Condition.Clear -> if (isDay) listOf(Color(0xFF2F80ED), Color(0xFF8EC5FC)) else listOf(Color(0xFF0B1026), Color(0xFF283B6E))
    Condition.FewClouds -> if (isDay) listOf(Color(0xFF3F8AE0), Color(0xFF9CCBF5)) else listOf(Color(0xFF0E1530), Color(0xFF2D3F70))
    Condition.MoreClouds -> if (isDay) listOf(Color(0xFF5F7C99), Color(0xFFA7B8C8)) else listOf(Color(0xFF1A2236), Color(0xFF3A4660))
    Condition.Cloudy -> if (isDay) listOf(Color(0xFF6C7A89), Color(0xFFB0BAC4)) else listOf(Color(0xFF222B38), Color(0xFF47515F))
    Condition.Rain -> if (isDay) listOf(Color(0xFF3E4C5E), Color(0xFF7A8896)) else listOf(Color(0xFF141B26), Color(0xFF2E3846))
    Condition.Storm -> listOf(Color(0xFF1C2230), Color(0xFF4A5366))
    Condition.Snow -> if (isDay) listOf(Color(0xFF8FA3B8), Color(0xFFDCE6F0)) else listOf(Color(0xFF2B3646), Color(0xFF5B6B80))
    Condition.Fog -> if (isDay) listOf(Color(0xFF8C9AA5), Color(0xFFCBD3D9)) else listOf(Color(0xFF2B3238), Color(0xFF566068))
}

private fun cloudCount(kind: Condition): Int = when (kind) {
    Condition.Clear -> 0
    Condition.FewClouds -> 3
    Condition.MoreClouds -> 6
    Condition.Cloudy -> 8
    Condition.Rain -> 8
    Condition.Storm -> 9
    Condition.Snow -> 6
    Condition.Fog -> 4
}

private fun cloudColor(kind: Condition, isDay: Boolean): Color {
    val base = when (kind) {
        Condition.Rain, Condition.Storm -> Color(0xFF4B5563)
        Condition.Cloudy, Condition.Snow, Condition.Fog -> Color(0xFF8A949F)
        else -> Color.White
    }
    val tinted = if (isDay) base else Color(
        red = base.red * 0.45f,
        green = base.green * 0.45f,
        blue = base.blue * 0.5f
    )
    return tinted.copy(alpha = 0.82f)
}

private fun DrawScope.cloud(x: Float, y: Float, scale: Float, color: Color) {
    val u = 40.dp.toPx() * scale
    drawCircle(color, u * 0.9f, Offset(x + u, y))
    drawCircle(color, u * 1.2f, Offset(x + u * 2.1f, y - u * 0.4f))
    drawCircle(color, u, Offset(x + u * 3.2f, y))
    drawRoundRect(color, Offset(x + u * 0.2f, y), Size(u * 3.9f, u * 0.9f), CornerRadius(u * 0.45f))
}

@Composable
fun WeatherBackground(kind: Condition, isDay: Boolean, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var start = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (start == 0L) start = nanos
                time = (nanos - start) / 1_000_000_000f
            }
        }
    }
    Canvas(modifier) {
        val t = time
        val w = size.width
        val h = size.height
        val colors = skyColors(kind, isDay)
        drawRect(Brush.verticalGradient(colors))

        val clearSky = kind == Condition.Clear || kind == Condition.FewClouds

        if (clearSky && isDay) {
            val center = Offset(w * 0.78f, h * 0.14f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x99FFF2A8), Color.Transparent),
                    center = center,
                    radius = w * 0.45f
                ),
                radius = w * 0.45f,
                center = center
            )
            rotate(t * 8f, center) {
                for (i in 0 until 12) {
                    val a = i * 30f * (Math.PI.toFloat() / 180f)
                    drawLine(
                        Color(0x66FFF2A8),
                        Offset(center.x + cos(a) * 54.dp.toPx(), center.y + sin(a) * 54.dp.toPx()),
                        Offset(center.x + cos(a) * 82.dp.toPx(), center.y + sin(a) * 82.dp.toPx()),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            drawCircle(Color(0xFFFFE27A), 40.dp.toPx(), center)
        }

        if (!isDay && (clearSky || kind == Condition.MoreClouds)) {
            for (i in 0 until 70) {
                val alpha = 0.35f + 0.65f * abs(sin(t * 1.4f + i))
                drawCircle(
                    Color.White.copy(alpha = alpha),
                    (0.6f + 1.6f * rnd(i, 3)).dp.toPx(),
                    Offset(rnd(i, 1) * w, rnd(i, 2) * h * 0.55f)
                )
            }
            val moon = Offset(w * 0.76f, h * 0.13f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x55FFFFFF), Color.Transparent), center = moon, radius = 90.dp.toPx()),
                radius = 90.dp.toPx(),
                center = moon
            )
            drawCircle(Color(0xFFF4F1E4), 30.dp.toPx(), moon)
            drawCircle(Color(0x22000000), 6.dp.toPx(), Offset(moon.x - 9.dp.toPx(), moon.y - 4.dp.toPx()))
            drawCircle(Color(0x22000000), 4.dp.toPx(), Offset(moon.x + 10.dp.toPx(), moon.y + 8.dp.toPx()))
        }

        val clouds = cloudCount(kind)
        val tint = cloudColor(kind, isDay)
        for (i in 0 until clouds) {
            val scale = 0.8f + rnd(i, 2)
            val cw = 40.dp.toPx() * scale * 4.4f
            val speed = (10f + 18f * rnd(i, 3)).dp.toPx()
            val x = ((rnd(i, 4) * (w + cw) + t * speed) % (w + cw)) - cw
            val y = h * (0.06f + 0.38f * rnd(i, 1))
            cloud(x, y, scale, tint)
        }

        if (kind == Condition.Rain || kind == Condition.Storm) {
            val drops = if (kind == Condition.Storm) 220 else 140
            val stroke = 1.6.dp.toPx()
            for (i in 0 until drops) {
                val speed = 900f + 500f * rnd(i, 3)
                val x = rnd(i, 1) * w
                val y = (rnd(i, 2) * h + t * speed * density) % h
                val length = (14f + 16f * rnd(i, 4)).dp.toPx()
                drawLine(
                    Color(0x99CFE3FF),
                    Offset(x, y),
                    Offset(x - length * 0.2f, y + length),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }

        if (kind == Condition.Snow) {
            for (i in 0 until 90) {
                val speed = 60f + 80f * rnd(i, 3)
                val x = rnd(i, 1) * w + sin(t * 0.8f + i) * 20.dp.toPx()
                val y = (rnd(i, 2) * h + t * speed * density) % h
                drawCircle(Color.White.copy(alpha = 0.9f), (2f + 3f * rnd(i, 4)).dp.toPx(), Offset(x, y))
            }
        }

        if (kind == Condition.Fog) {
            for (i in 0 until 4) {
                val offset = sin(t * 0.2f + i) * w * 0.2f
                val top = h * (0.30f + 0.14f * i)
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                        startX = offset - w * 0.3f,
                        endX = offset + w * 1.3f
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(w, h * 0.16f)
                )
            }
        }

        if (kind == Condition.Storm) {
            val cycle = t % 6f
            val flash = when {
                cycle < 0.08f -> 1f
                cycle in 0.18f..0.24f -> 0.7f
                else -> 0f
            }
            if (flash > 0f) {
                drawRect(Color.White.copy(alpha = flash * 0.5f))
                if (cycle < 0.08f) {
                    val seed = (t / 6f).toInt()
                    var x = w * (0.25f + 0.5f * rnd(seed, 9))
                    var y = 0f
                    val bolt = Path().apply { moveTo(x, y) }
                    var step = 0
                    while (y < h * 0.55f) {
                        x += (rnd(seed + step, 5) - 0.5f) * 60.dp.toPx()
                        y += 36.dp.toPx()
                        bolt.lineTo(x, y)
                        step++
                    }
                    drawPath(bolt, Color(0xFFFFFBD6), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
}
