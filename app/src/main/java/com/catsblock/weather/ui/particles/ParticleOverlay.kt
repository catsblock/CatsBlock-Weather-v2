package com.catsblock.weather.ui.particles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object CardCollision {
    private val bounds = mutableStateMapOf<Any, Rect>()
    val rects: List<Rect> get() = bounds.values.toList()
    fun update(key: Any, rect: Rect) {
        bounds[key] = rect
    }
    fun remove(key: Any) {
        bounds.remove(key)
    }
}

enum class ParticleWeather { None, Rain, HeavyRain, Snow, Storm }

private val RainColor = Color(0xFFD6E8FF)

private class RainLayer(
    val count: Int,
    val speed: Float,
    val length: Float,
    val width: Float,
    val alpha: Float,
    val windFactor: Float,
    val collides: Boolean
) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val v = FloatArray(count)
    val hits = BooleanArray(count)
    var ready = false
}

private class RainSim(private val d: Float, multiplier: Float) {
    val layers = listOf(
        RainLayer(max(10, (70 * multiplier).toInt()), 520f * d, 11f * d, 0.9f * d, 0.20f, 0.55f, false),
        RainLayer(max(8, (52 * multiplier).toInt()), 900f * d, 20f * d, 1.4f * d, 0.32f, 0.8f, true),
        RainLayer(max(5, (26 * multiplier).toInt()), 1350f * d, 34f * d, 2.1f * d, 0.48f, 1f, true)
    )
    private val dropletCount = 140
    val dx = FloatArray(dropletCount)
    val dy = FloatArray(dropletCount)
    val dvx = FloatArray(dropletCount)
    val dvy = FloatArray(dropletCount)
    val dl = FloatArray(dropletCount)
    private var dp = 0
    private val rippleCount = 48
    val rx = FloatArray(rippleCount)
    val ry = FloatArray(rippleCount)
    val rl = FloatArray(rippleCount)
    private var rp = 0
    var wind = 0f

    private fun respawn(l: RainLayer, i: Int, w: Float, h: Float, wr: Float, initial: Boolean) {
        val drift = abs(wr) * h
        l.x[i] = Random.nextFloat() * (w + drift) - if (wr > 0f) drift else 0f
        l.y[i] = if (initial) Random.nextFloat() * h else -Random.nextFloat() * h * 0.25f - l.length
        l.v[i] = 0.85f + Random.nextFloat() * 0.3f
        l.hits[i] = Random.nextFloat() < 0.3f
    }

    private fun splash(x: Float, y: Float, vx: Float) {
        rx[rp] = x
        ry[rp] = y
        rl[rp] = 0.4f
        rp = (rp + 1) % rippleCount
        repeat(3) {
            dx[dp] = x
            dy[dp] = y
            dvx[dp] = vx * 0.15f + (Random.nextFloat() - 0.5f) * 140f * d
            dvy[dp] = -(70f + Random.nextFloat() * 150f) * d
            dl[dp] = 0.38f
            dp = (dp + 1) % dropletCount
        }
    }

    fun step(dt: Float, w: Float, h: Float, targetWind: Float, rects: List<Rect>) {
        wind += (targetWind - wind) * (dt * 1.5f).coerceAtMost(1f)
        for (l in layers) {
            val wr = wind * l.windFactor
            if (!l.ready) {
                for (i in 0 until l.count) respawn(l, i, w, h, wr, true)
                l.ready = true
            }
            for (i in 0 until l.count) {
                val vy = l.speed * l.v[i]
                val prevY = l.y[i]
                l.y[i] += vy * dt
                l.x[i] += vy * wr * dt
                if (l.collides && l.hits[i]) {
                    for (r in rects) {
                        if (r.top > 24f && r.top < h && l.x[i] >= r.left && l.x[i] <= r.right && prevY < r.top && l.y[i] >= r.top) {
                            splash(l.x[i], r.top, vy * wr)
                            respawn(l, i, w, h, wr, false)
                            break
                        }
                    }
                }
                if (l.y[i] > h + l.length) respawn(l, i, w, h, wr, false)
            }
        }
        for (i in 0 until dropletCount) {
            if (dl[i] > 0f) {
                dl[i] -= dt
                dvy[i] += 1500f * d * dt
                dx[i] += dvx[i] * dt
                dy[i] += dvy[i] * dt
            }
        }
        for (i in 0 until rippleCount) if (rl[i] > 0f) rl[i] -= dt
    }

    fun draw(scope: DrawScope) {
        with(scope) {
            for (l in layers) {
                val wr = wind * l.windFactor
                val norm = sqrt(1f + wr * wr)
                val ux = wr / norm
                val uy = 1f / norm
                for (i in 0 until l.count) {
                    val px = l.x[i]
                    val py = l.y[i]
                    val len = l.length * (0.85f + 0.3f * l.v[i])
                    drawLine(
                        RainColor.copy(alpha = l.alpha * 0.4f),
                        Offset(px - ux * len, py - uy * len),
                        Offset(px, py),
                        l.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        RainColor.copy(alpha = l.alpha),
                        Offset(px - ux * len * 0.45f, py - uy * len * 0.45f),
                        Offset(px, py),
                        l.width,
                        StrokeCap.Round
                    )
                }
            }
            for (i in 0 until dl.size) {
                if (dl[i] > 0f) {
                    drawCircle(RainColor.copy(alpha = (dl[i] / 0.38f).coerceIn(0f, 1f) * 0.7f), 1.2f * d, Offset(dx[i], dy[i]))
                }
            }
            for (i in 0 until rl.size) {
                if (rl[i] > 0f) {
                    val t = 1f - rl[i] / 0.4f
                    val rw = (4f + t * 14f) * d
                    val rh = rw * 0.35f
                    drawOval(
                        RainColor.copy(alpha = (1f - t) * 0.55f),
                        Offset(rx[i] - rw / 2f, ry[i] - rh / 2f),
                        Size(rw, rh),
                        style = Stroke(1.1f * d)
                    )
                }
            }
        }
    }
}

private class SnowSim(private val d: Float, val count: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val z = FloatArray(count)
    val phase = FloatArray(count)
    val amp = FloatArray(count)
    val swaySpeed = FloatArray(count)
    val rot = FloatArray(count)
    val rotSpeed = FloatArray(count)
    val mass = FloatArray(count)
    val flakeSize = FloatArray(count)
    val drawX = FloatArray(count)
    private var ready = false
    private var wind = 0f

    private fun respawn(i: Int, w: Float, h: Float, initial: Boolean) {
        z[i] = Random.nextFloat()
        mass[i] = 0.6f + Random.nextFloat() * 0.8f
        flakeSize[i] = (1.2f + z[i] * 3.8f) * d * (0.8f + Random.nextFloat() * 0.4f)
        x[i] = Random.nextFloat() * w
        y[i] = if (initial) Random.nextFloat() * h else -Random.nextFloat() * h * 0.2f - flakeSize[i]
        phase[i] = Random.nextFloat() * (2f * PI.toFloat())
        amp[i] = (10f + Random.nextFloat() * 18f) * d / mass[i]
        swaySpeed[i] = (0.6f + Random.nextFloat() * 0.8f) / sqrt(mass[i])
        rot[i] = Random.nextFloat() * (2f * PI.toFloat())
        rotSpeed[i] = (Random.nextFloat() - 0.5f) * 2.2f / mass[i]
    }

    fun step(dt: Float, w: Float, h: Float, targetWind: Float) {
        wind += (targetWind - wind) * (dt * 1.2f).coerceAtMost(1f)
        if (!ready) {
            for (i in 0 until count) respawn(i, w, h, true)
            ready = true
        }
        for (i in 0 until count) {
            y[i] += (22f + z[i] * 58f) * d * mass[i] * dt
            phase[i] += swaySpeed[i] * dt
            rot[i] += rotSpeed[i] * dt
            x[i] += wind * 90f * d * (0.4f + z[i]) * dt
            if (x[i] < -amp[i]) x[i] += w + 2f * amp[i]
            if (x[i] > w + amp[i]) x[i] -= w + 2f * amp[i]
            drawX[i] = x[i] + sin(phase[i]) * amp[i]
            if (y[i] > h + flakeSize[i] * 2f) respawn(i, w, h, false)
        }
    }

    fun draw(scope: DrawScope, w: Float) {
        with(scope) {
            for (i in 0 until count) {
                val px = drawX[i]
                val py = y[i]
                val r = flakeSize[i]
                val depthBlur = abs(z[i] - 0.5f) * 1.6f
                val edgeBlur = (abs(px / w - 0.5f) * 2f).pow(2f) * 0.7f
                val soft = max(depthBlur, edgeBlur).coerceIn(0f, 1f)
                val alpha = (0.45f + z[i] * 0.5f) * (1f - soft * 0.35f)
                if (r > 2.6f * d && soft < 0.45f) {
                    val arm = r * 1.6f
                    for (k in 0 until 3) {
                        val a = rot[i] + k * (PI.toFloat() / 3f)
                        val ax = cos(a) * arm
                        val ay = sin(a) * arm
                        drawLine(
                            Color.White.copy(alpha = alpha),
                            Offset(px - ax, py - ay),
                            Offset(px + ax, py + ay),
                            max(1f, r * 0.3f),
                            StrokeCap.Round
                        )
                    }
                } else {
                    if (soft > 0.15f) {
                        drawCircle(Color.White.copy(alpha = alpha * soft * 0.35f), r * (1f + soft * 1.8f), Offset(px, py))
                    }
                    drawCircle(Color.White.copy(alpha = alpha * (1f - soft * 0.6f)), r, Offset(px, py))
                }
            }
        }
    }
}

@Composable
fun ParticleOverlay(
    weather: ParticleWeather,
    windSpeedKmh: Double = 0.0,
    windDirDeg: Double = 0.0,
    modifier: Modifier = Modifier
) {
    if (weather == ParticleWeather.None) return
    val density = LocalDensity.current.density
    val isStorm = weather == ParticleWeather.Storm
    val isSnow = weather == ParticleWeather.Snow
    val rainSim = remember(weather, density) {
        when (weather) {
            ParticleWeather.Rain -> RainSim(density, 0.55f)
            ParticleWeather.HeavyRain -> RainSim(density, 1f)
            ParticleWeather.Storm -> RainSim(density, 1.15f)
            else -> null
        }
    }
    val snowSim = remember(weather, density) { if (isSnow) SnowSim(density, 120) else null }
    var lightning by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableLongStateOf(0L) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val windRatio = (-sin(Math.toRadians(windDirDeg)) * (windSpeedKmh / 45.0).coerceIn(0.0, 1.0) * 0.55).toFloat()
    val currentWind by rememberUpdatedState(windRatio)

    LaunchedEffect(isStorm) {
        if (!isStorm) return@LaunchedEffect
        while (true) {
            delay(2200L + Random.nextLong(5200L))
            lightning = 0.85f
            delay(70)
            lightning = 0.1f
            delay(60)
            lightning = 0.6f
            delay(90)
            lightning = 0f
        }
    }

    LaunchedEffect(rainSim, snowSim) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            val w = canvasSize.width.toFloat()
            val h = canvasSize.height.toFloat()
            if (w > 0f && h > 0f) {
                rainSim?.step(dt, w, h, currentWind, CardCollision.rects)
                snowSim?.step(dt, w, h, currentWind)
            }
            tick = now
        }
    }

    Canvas(modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
        @Suppress("UNUSED_EXPRESSION")
        tick
        rainSim?.draw(this)
        snowSim?.draw(this, size.width)
        if (isStorm && lightning > 0f) {
            drawRect(Color.White.copy(alpha = lightning * 0.55f))
        }
    }
}

@Composable
fun Modifier.collideWithParticles(): Modifier {
    val key = remember { Any() }
    DisposableEffect(Unit) {
        onDispose { CardCollision.remove(key) }
    }
    return this.onGloballyPositioned { coords ->
        CardCollision.update(key, coords.boundsInRoot())
    }
}
