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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.catsblock.weather.data.Condition
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

@Stable
class LightningState {
    var intensity by mutableFloatStateOf(0f)
    var dirX by mutableFloatStateOf(0f)
    var dirY by mutableFloatStateOf(0f)
    var dirZ by mutableFloatStateOf(1f)
    var bolt by mutableStateOf(FloatArray(0))
    var boltFade by mutableFloatStateOf(0f)
}

internal fun lightningPeriodMs(kind: Condition): LongRange = when (kind) {
    Condition.StrongStorms -> 3000L..7000L
    Condition.IsolatedStorms -> 9000L..20000L
    else -> 5000L..11000L
}

private fun addBolt(out: MutableList<Float>, random: Random, x1: Float, y1: Float, x2: Float, y2: Float, offset: Float, depth: Int, width: Float) {
    if (depth == 0) {
        out.add(x1); out.add(y1); out.add(x2); out.add(y2); out.add(width)
        return
    }
    val dx = x2 - x1
    val dy = y2 - y1
    val length = max(hypot(dx, dy), 1e-5f)
    val nx = -dy / length
    val ny = dx / length
    val shift = (random.nextFloat() * 2f - 1f) * offset
    val mx = (x1 + x2) / 2f + nx * shift
    val my = (y1 + y2) / 2f + ny * shift
    addBolt(out, random, x1, y1, mx, my, offset * 0.55f, depth - 1, width)
    addBolt(out, random, mx, my, x2, y2, offset * 0.55f, depth - 1, width)
    if (depth >= 3 && random.nextFloat() < 0.45f) {
        val angle = (if (random.nextBoolean()) 1 else -1) * (0.35f + random.nextFloat() * 0.5f)
        val c = kotlin.math.cos(angle)
        val s = kotlin.math.sin(angle)
        val bx = (dx * c - dy * s) * 0.55f
        val by = (dx * s + dy * c) * 0.55f
        addBolt(out, random, mx, my, mx + bx, my + by, offset * 0.5f, depth - 2, width * 0.55f)
    }
}

internal fun generateBolt(random: Random, aspect: Float): FloatArray {
    val out = ArrayList<Float>()
    val startX = 0.2f + random.nextFloat() * 0.6f
    val endX = (startX + (random.nextFloat() - 0.5f) * 0.3f).coerceIn(0.1f, 0.9f)
    val endY = 0.45f + random.nextFloat() * 0.3f
    addBolt(out, random, startX, 0.02f, endX, endY, 0.09f * max(aspect, 0.3f) * 2.2f, 6, 1f)
    return out.toFloatArray()
}

private fun envelope(seconds: Float): Float {
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

@Composable
internal fun SkyLightning(
    state: LightningState,
    scene: SkyScene,
    kind: Condition,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
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
                state.bolt = generateBolt(random, aspect)
                val target = currentScene.directionAt(
                    (random.nextFloat() - 0.5f) * aspect,
                    (random.nextFloat() - 0.4f) * 0.7f
                )
                state.dirX = target[0]
                state.dirY = target[1]
                state.dirZ = target[2]
                val strength = 0.7f + random.nextFloat() * 0.3f
                val start = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    val seconds = (now - start) / 1_000_000_000f
                    val e = envelope(seconds)
                    state.intensity = e * strength
                    state.boltFade = e * strength
                    if (seconds > 0.6f) break
                }
                state.intensity = 0f
                state.boltFade = 0f
                state.bolt = FloatArray(0)
            }
        }
    }
    Canvas(modifier.onSizeChanged { size = it }) {
        val fade = state.boltFade
        val bolt = state.bolt
        if (fade < 0.03f || bolt.isEmpty()) return@Canvas
        val glow = Color(0.72f, 0.8f, 1f)
        var i = 0
        while (i + 4 < bolt.size) {
            val a = Offset(bolt[i] * this.size.width, bolt[i + 1] * this.size.height)
            val b = Offset(bolt[i + 2] * this.size.width, bolt[i + 3] * this.size.height)
            val w = bolt[i + 4]
            drawLine(glow.copy(alpha = 0.10f * fade), a, b, 14f * w, StrokeCap.Round)
            drawLine(glow.copy(alpha = 0.30f * fade), a, b, 5f * w, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = fade), a, b, 1.8f * w, StrokeCap.Round)
            i += 5
        }
    }
}
