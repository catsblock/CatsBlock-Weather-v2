package com.catsblock.weather.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.catsblock.weather.data.Condition
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private val BoltGlow = Color(0.72f, 0.8f, 1f)
private val NoBolt = FloatArray(0)

@Stable
class LightningState {
    var intensity by mutableFloatStateOf(0f)
    var dirX by mutableFloatStateOf(0f)
    var dirY by mutableFloatStateOf(0f)
    var dirZ by mutableFloatStateOf(1f)
    var bolt by mutableStateOf(NoBolt)
    var boltFade by mutableFloatStateOf(0f)
    var boltReveal by mutableFloatStateOf(0f)
    var directionAt: ((Float, Float) -> FloatArray)? = null
}

internal fun lightningPeriodMs(kind: Condition): LongRange = when (kind) {
    Condition.StrongStorms -> 3000L..7000L
    Condition.IsolatedStorms -> 9000L..20000L
    else -> 5000L..11000L
}

private class BoltBuilder(private val random: Random) {
    var data = FloatArray(7 * 320)
    var size = 0

    private fun push(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, d: Float, b: Float) {
        if (size + 7 > data.size) data = data.copyOf(data.size * 2)
        data[size] = x1
        data[size + 1] = y1
        data[size + 2] = x2
        data[size + 3] = y2
        data[size + 4] = w
        data[size + 5] = d
        data[size + 6] = b
        size += 7
    }

    fun channel(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        disp: Float,
        depth: Int,
        w: Float,
        d: Float,
        b: Float,
        level: Int,
        span: Float
    ) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = max(hypot(dx, dy), 1e-4f)
        if (depth <= 0 || len < 5f) {
            push(x1, y1, x2, y2, w, d, b)
            return
        }
        val nx = -dy / len
        val ny = dx / len
        val shift = (random.nextFloat() * 2f - 1f) * disp * (0.55f + random.nextFloat() * 0.9f)
        val mx = (x1 + x2) * 0.5f + nx * shift
        val my = (y1 + y2) * 0.5f + ny * shift
        val half = hypot(mx - x1, my - y1)
        channel(x1, y1, mx, my, disp * 0.5f, depth - 1, w, d, b, level, span)
        channel(mx, my, x2, y2, disp * 0.5f, depth - 1, w * 0.96f, d + half, b, level, span)
        val base = when {
            depth >= 5 -> 0.5f
            depth == 4 -> 0.36f
            depth == 3 -> 0.2f
            depth == 2 -> 0.1f
            else -> 0f
        }
        val chance = base * (if (level == 0) 1f else 0.45f)
        if (level < 2 && random.nextFloat() < chance) {
            val sign = if (random.nextBoolean()) 1f else -1f
            val ang = sign * (0.28f + random.nextFloat() * 0.85f)
            val c = cos(ang)
            val s = sin(ang)
            val bx = dx * c - dy * s
            var by = dx * s + dy * c
            if (by < 0f) by = -by * 0.5f
            val bl = max(hypot(bx, by), 1e-4f)
            val target = span * (0.05f + random.nextFloat() * 0.2f) * (if (level == 0) 1f else 0.5f)
            channel(
                mx,
                my,
                mx + bx / bl * target,
                my + by / bl * target,
                target * 0.2f,
                minOf(depth - 1, 4),
                w * 0.5f,
                d + half,
                b * 0.7f,
                level + 1,
                span
            )
        }
    }
}

internal fun buildBolt(random: Random, x1: Float, y1: Float, x2: Float, y2: Float): FloatArray {
    val builder = BoltBuilder(random)
    val span = hypot(x2 - x1, y2 - y1)
    builder.channel(x1, y1, x2, y2, span * 0.17f, 6, 1f, 0f, 1f, 0, span)
    return builder.data.copyOf(builder.size)
}

internal fun boltReach(bolt: FloatArray): Float {
    var reach = 0f
    var i = 0
    while (i + 6 < bolt.size) {
        if (bolt[i + 6] >= 0.99f) {
            reach = max(reach, bolt[i + 5] + hypot(bolt[i + 2] - bolt[i], bolt[i + 3] - bolt[i + 1]))
        }
        i += 7
    }
    return reach
}

internal fun lightningEnvelope(seconds: Float): Float {
    val pulses = floatArrayOf(0f, 1.0f, 0.09f, 0.55f, 0.19f, 0.8f)
    var value = 0f
    var i = 0
    while (i < pulses.size) {
        val dt = seconds - pulses[i]
        if (dt >= 0f) value = max(value, pulses[i + 1] * exp(-dt / 0.05f))
        i += 2
    }
    return value
}

internal fun DrawScope.drawBolt(bolt: FloatArray, reveal: Float, fade: Float, unit: Float) {
    if (fade < 0.02f || bolt.isEmpty()) return
    var i = 0
    while (i + 6 < bolt.size) {
        if (bolt[i + 5] <= reveal) {
            val a = Offset(bolt[i], bolt[i + 1])
            val b = Offset(bolt[i + 2], bolt[i + 3])
            val w = bolt[i + 4]
            val f = (fade * bolt[i + 6]).coerceIn(0f, 1f)
            drawLine(BoltGlow.copy(alpha = 0.08f * f), a, b, 15f * unit * w, StrokeCap.Round)
            drawLine(BoltGlow.copy(alpha = 0.26f * f), a, b, 5f * unit * w, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = f), a, b, max(1f, 1.5f * unit * w), StrokeCap.Round)
        }
        i += 7
    }
}

@Composable
internal fun SkyLightning(
    state: LightningState,
    scene: SkyScene,
    kind: Condition,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val currentScene by rememberUpdatedState(scene)
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(lifecycleOwner, kind) {
        val random = Random(System.nanoTime())
        val period = lightningPeriodMs(kind)
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(random.nextLong(period.first, period.last))
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (w <= 0f || h <= 0f) continue
                val aspect = w / h
                val sx = w * (0.2f + random.nextFloat() * 0.6f)
                val ex = (sx + (random.nextFloat() - 0.5f) * w * 0.3f).coerceIn(w * 0.1f, w * 0.9f)
                val ey = h * (0.45f + random.nextFloat() * 0.3f)
                val built = buildBolt(random, sx, h * 0.02f, ex, ey)
                state.bolt = built
                val target = currentScene.directionAt(
                    (random.nextFloat() - 0.5f) * aspect,
                    (random.nextFloat() - 0.4f) * 0.7f
                )
                state.dirX = target[0]
                state.dirY = target[1]
                state.dirZ = target[2]
                val strength = 0.7f + random.nextFloat() * 0.3f
                val speed = h * 10f
                val tReach = boltReach(built) / speed
                val start = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    val seconds = (now - start) / 1_000_000_000f
                    val e = if (seconds < tReach) 0.3f else lightningEnvelope(seconds - tReach)
                    state.boltReveal = seconds * speed
                    state.intensity = (if (seconds < tReach) 0.1f else e) * strength
                    state.boltFade = e * strength
                    if (seconds > tReach + 0.6f) break
                }
                state.intensity = 0f
                state.boltFade = 0f
                state.bolt = NoBolt
            }
        }
    }
    Canvas(modifier.onSizeChanged { size = it }) {
        drawBolt(state.bolt, state.boltReveal, state.boltFade, density)
    }
}
