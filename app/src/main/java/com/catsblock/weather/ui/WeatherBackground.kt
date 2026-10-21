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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.Condition
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

private fun rnd(i: Int, salt: Int): Float {
    val x = sin((i * 12.9898f + salt * 78.233f).toDouble()) * 43758.5453
    return (x - floor(x)).toFloat()
}

private fun smooth(x: Float, a: Float, b: Float): Float {
    val v = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return v * v * (3f - 2f * v)
}

private class Sky(val top: Color, val mid: Color, val bottom: Color)

private fun sky(kind: Condition, day: Boolean): Sky = when (kind) {
    Condition.Clear -> if (day) Sky(Color(0xFF1B6BD6), Color(0xFF4E9AE8), Color(0xFFA8D3F4))
    else Sky(Color(0xFF050A1C), Color(0xFF111C42), Color(0xFF2A3B70))
    Condition.FewClouds -> if (day) Sky(Color(0xFF2B78D4), Color(0xFF68A4E6), Color(0xFFB6D8F2))
    else Sky(Color(0xFF070D22), Color(0xFF16244E), Color(0xFF2F4378))
    Condition.MoreClouds -> if (day) Sky(Color(0xFF4A74A6), Color(0xFF86A7C7), Color(0xFFC1D2E0))
    else Sky(Color(0xFF0E1630), Color(0xFF222F52), Color(0xFF3C4A6B))
    Condition.Cloudy -> if (day) Sky(Color(0xFF627489), Color(0xFF92A2B3), Color(0xFFC3CDD6))
    else Sky(Color(0xFF161D2B), Color(0xFF2A3445), Color(0xFF475265))
    Condition.Rain -> if (day) Sky(Color(0xFF34455A), Color(0xFF5E7084), Color(0xFF8E9DAB))
    else Sky(Color(0xFF0D131E), Color(0xFF1C2635), Color(0xFF343F50))
    Condition.Storm -> Sky(Color(0xFF121722), Color(0xFF283043), Color(0xFF4B5569))
    Condition.Snow -> if (day) Sky(Color(0xFF7A8EA5), Color(0xFFB3C3D3), Color(0xFFE3EBF2))
    else Sky(Color(0xFF222C3B), Color(0xFF3F4D61), Color(0xFF66768C))
    Condition.Fog -> if (day) Sky(Color(0xFF8493A0), Color(0xFFB4BFC7), Color(0xFFD6DCE0))
    else Sky(Color(0xFF222930), Color(0xFF3D464E), Color(0xFF5C6770))
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

private fun cloudLight(kind: Condition, day: Boolean): Color = when (kind) {
    Condition.Rain, Condition.Storm -> if (day) Color(0xFF8C98A6) else Color(0xFF4A5568)
    Condition.Cloudy, Condition.Fog, Condition.Snow -> if (day) Color(0xFFE2E8EE) else Color(0xFF6B778A)
    else -> if (day) Color.White else Color(0xFF7C8AAB)
}

private fun cloudShade(kind: Condition, day: Boolean): Color = when (kind) {
    Condition.Rain, Condition.Storm -> if (day) Color(0xFF3B4658) else Color(0xFF1A2130)
    Condition.Cloudy, Condition.Fog, Condition.Snow -> if (day) Color(0xFF8B99AB) else Color(0xFF2B3446)
    else -> if (day) Color(0xFF9DB3D1) else Color(0xFF283250)
}

private val puffs = arrayOf(
    floatArrayOf(0.0f, 0.2f, 0.8f),
    floatArrayOf(0.9f, -0.1f, 1.1f),
    floatArrayOf(1.9f, -0.2f, 1.3f),
    floatArrayOf(2.9f, 0.0f, 1.05f),
    floatArrayOf(3.7f, 0.25f, 0.8f),
    floatArrayOf(1.4f, 0.4f, 0.95f),
    floatArrayOf(2.5f, 0.45f, 0.9f)
)

private fun DrawScope.cloud(x: Float, y: Float, u: Float, light: Color, shade: Color, a: Float) {
    for (p in puffs) {
        val r = p[2] * u * 1.2f
        val c = Offset(x + p[0] * u, y + p[1] * u + p[2] * u * 0.3f)
        drawCircle(
            Brush.radialGradient(listOf(shade.copy(alpha = 0.5f * a), Color.Transparent), center = c, radius = r),
            radius = r,
            center = c
        )
    }
    for (p in puffs) {
        val r = p[2] * u
        val c = Offset(x + p[0] * u - r * 0.1f, y + p[1] * u - r * 0.22f)
        drawCircle(
            Brush.radialGradient(
                listOf(light.copy(alpha = 0.9f * a), light.copy(alpha = 0.4f * a), Color.Transparent),
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )
    }
}

private fun DrawScope.landscape(sky: Sky, day: Boolean, t: Float) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.60f
    val ground = if (day) Color(0xFF38564A) else Color(0xFF0A1220)

    drawRect(
        Brush.verticalGradient(
            listOf(lerp(sky.bottom, Color.Black, 0.10f), lerp(sky.mid, Color.Black, 0.5f)),
            startY = horizon,
            endY = h
        ),
        topLeft = Offset(0f, horizon),
        size = Size(w, h - horizon)
    )

    for (j in 0 until 46) {
        val depth = rnd(j, 1).pow(1.7f)
        val y = horizon + 4.dp.toPx() + depth * (h - horizon - 4.dp.toPx())
        val len = (24f + 110f * depth + 30f * rnd(j, 2)).dp.toPx()
        val speed = (6f + 22f * depth).dp.toPx()
        val x = ((rnd(j, 3) * (w + len) + t * speed) % (w + len)) - len
        drawLine(
            Color.White.copy(alpha = 0.05f + 0.12f * rnd(j, 4)),
            Offset(x, y),
            Offset(x + len, y),
            strokeWidth = (0.8f + 1.4f * depth).dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    val steps = 28
    val far = Path().apply {
        moveTo(0f, horizon)
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val y = horizon - (14.dp.toPx() * (0.5f + 0.5f * sin(f * 5.2f + 0.8f)) +
                9.dp.toPx() * abs(sin(f * 11f + 2f)))
            lineTo(w * f, y)
        }
        lineTo(w, horizon)
        close()
    }
    drawPath(far, lerp(sky.bottom, ground, 0.55f))

    val near = Path().apply {
        moveTo(0f, horizon + 2.dp.toPx())
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val ramp = smooth(f, 0.42f, 1f)
            val y = horizon + 1.dp.toPx() - 54.dp.toPx() * ramp * (0.75f + 0.25f * sin(f * 6f + 1f))
            lineTo(w * f, y)
        }
        lineTo(w, horizon + 2.dp.toPx())
        close()
    }
    drawPath(near, lerp(sky.bottom, ground, 0.85f))

    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, sky.bottom.copy(alpha = 0.45f), Color.Transparent),
            startY = horizon - 30.dp.toPx(),
            endY = horizon + 30.dp.toPx()
        ),
        topLeft = Offset(0f, horizon - 30.dp.toPx()),
        size = Size(w, 60.dp.toPx())
    )
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
        val sky = sky(kind, isDay)
        drawRect(Brush.verticalGradient(listOf(sky.top, sky.mid, sky.bottom), endY = h * 0.62f))

        val clearSky = kind == Condition.Clear || kind == Condition.FewClouds

        if (clearSky && isDay) {
            val center = Offset(w * 0.74f, h * 0.17f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xCCFFF1B8), Color(0x55FFE08A), Color.Transparent),
                    center = center,
                    radius = w * 0.6f
                ),
                radius = w * 0.6f,
                center = center
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White, Color(0xFFFFF3C4), Color(0x00FFE9A0)),
                    center = center,
                    radius = 54.dp.toPx()
                ),
                radius = 54.dp.toPx(),
                center = center
            )
        }

        if (!isDay && (clearSky || kind == Condition.MoreClouds)) {
            for (i in 0 until 60) {
                val alpha = 0.3f + 0.6f * abs(sin(t * 1.2f + i))
                drawCircle(
                    Color.White.copy(alpha = alpha),
                    (0.5f + 1.3f * rnd(i, 3)).dp.toPx(),
                    Offset(rnd(i, 1) * w, rnd(i, 2) * h * 0.5f)
                )
            }
            val moon = Offset(w * 0.74f, h * 0.15f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x59FFFFFF), Color.Transparent),
                    center = moon,
                    radius = 120.dp.toPx()
                ),
                radius = 120.dp.toPx(),
                center = moon
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFF8F6EA), Color(0xFFEDEBDC)),
                    center = moon,
                    radius = 26.dp.toPx()
                ),
                radius = 26.dp.toPx(),
                center = moon
            )
        }

        val count = cloudCount(kind)
        val light = cloudLight(kind, isDay)
        val shade = cloudShade(kind, isDay)
        val order = (0 until count).sortedBy { rnd(it, 6) }
        for (i in order) {
            val depth = rnd(i, 6)
            val u = 34.dp.toPx() * (1.3f + 1.7f * depth)
            val cw = u * 4.8f
            val speed = (4f + 10f * depth).dp.toPx()
            val x = ((rnd(i, 4) * (w + cw) + t * speed) % (w + cw)) - cw
            val y = h * (0.04f + 0.34f * rnd(i, 1))
            cloud(x, y, u, light, shade, 0.55f + 0.45f * depth)
        }

        landscape(sky, isDay, t)

        if (kind == Condition.Rain || kind == Condition.Storm) {
            val drops = if (kind == Condition.Storm) 220 else 140
            for (i in 0 until drops) {
                val speed = 900f + 500f * rnd(i, 3)
                val x = rnd(i, 1) * w
                val y = (rnd(i, 2) * h + t * speed * density) % h
                val length = (14f + 16f * rnd(i, 4)).dp.toPx()
                drawLine(
                    Color(0xFFD6E6FF).copy(alpha = 0.25f + 0.35f * rnd(i, 5)),
                    Offset(x, y),
                    Offset(x - length * 0.2f, y + length),
                    strokeWidth = (1f + 0.8f * rnd(i, 6)).dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (kind == Condition.Snow) {
            for (i in 0 until 90) {
                val speed = 60f + 80f * rnd(i, 3)
                val x = rnd(i, 1) * w + sin(t * 0.8f + i) * 20.dp.toPx()
                val y = (rnd(i, 2) * h + t * speed * density) % h
                drawCircle(Color.White.copy(alpha = 0.85f), (1.5f + 2.5f * rnd(i, 4)).dp.toPx(), Offset(x, y))
            }
        }

        if (kind == Condition.Fog) {
            for (i in 0 until 4) {
                val offset = sin(t * 0.2f + i) * w * 0.2f
                val top = h * (0.30f + 0.14f * i)
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.34f), Color.Transparent),
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
                drawRect(Color.White.copy(alpha = flash * 0.4f))
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

        drawRect(
            Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent),
                startY = 0f,
                endY = h * 0.22f
            ),
            size = Size(w, h * 0.22f)
        )
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                startY = h * 0.5f,
                endY = h
            ),
            topLeft = Offset(0f, h * 0.5f),
            size = Size(w, h * 0.5f)
        )
    }
}
