package com.catsblock.weather.ui.particles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.max
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

private data class RainDrop(
    var x: Float,
    var y: Float,
    var speed: Float,
    var length: Float,
    var alpha: Float,
    var splashing: Boolean = false,
    var splashTtl: Float = 0f,
    var splashX: Float = 0f,
    var splashY: Float = 0f
)

private data class SnowFlake(
    var x: Float,
    var y: Float,
    var speed: Float,
    var radius: Float,
    var sway: Float,
    var swaySpeed: Float,
    var alpha: Float
)

@Composable
fun ParticleOverlay(weather: ParticleWeather, modifier: Modifier = Modifier) {
    if (weather == ParticleWeather.None) return
    val isRain = weather == ParticleWeather.Rain || weather == ParticleWeather.HeavyRain || weather == ParticleWeather.Storm
    val isSnow = weather == ParticleWeather.Snow
    val isStorm = weather == ParticleWeather.Storm
    val dropCount = when (weather) {
        ParticleWeather.HeavyRain -> 220
        ParticleWeather.Storm -> 180
        ParticleWeather.Rain -> 110
        else -> 0
    }
    val flakeCount = if (isSnow) 140 else 0

    var lightningAlpha by remember { mutableFloatStateOf(0f) }
    var frameTick by remember { mutableFloatStateOf(0f) }

    val drops = remember(dropCount) {
        List(dropCount) {
            RainDrop(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -1f,
                speed = 1400f + Random.nextFloat() * 900f,
                length = 14f + Random.nextFloat() * 18f,
                alpha = 0.25f + Random.nextFloat() * 0.35f
            )
        }
    }
    val flakes = remember(flakeCount) {
        List(flakeCount) {
            SnowFlake(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -1f,
                speed = 40f + Random.nextFloat() * 70f,
                radius = 1.5f + Random.nextFloat() * 2.6f,
                sway = Random.nextFloat() * 6.28f,
                swaySpeed = 0.6f + Random.nextFloat() * 1.1f,
                alpha = 0.5f + Random.nextFloat() * 0.5f
            )
        }
    }

    LaunchedEffect(isStorm) {
        if (!isStorm) return@LaunchedEffect
        while (true) {
            val wait = 2200L + Random.nextLong(5200L)
            kotlinx.coroutines.delay(wait)
            lightningAlpha = 0.85f
            kotlinx.coroutines.delay(70)
            lightningAlpha = 0.1f
            kotlinx.coroutines.delay(60)
            lightningAlpha = 0.6f
            kotlinx.coroutines.delay(90)
            lightningAlpha = 0f
        }
    }

    LaunchedEffect(dropCount, flakeCount) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            frameTick += dt
        }
    }

    Canvas(modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION")
        frameTick
        val w = size.width
        val h = size.height
        val cardRects = CardCollision.rects
        val dt = 0.016f

        if (isRain) {
            drops.forEach { d ->
                d.y += d.speed * dt
                val px = d.x * w
                val py = d.y
                var collided = false
                if (!d.splashing) {
                    for (r in cardRects) {
                        if (px in r.left..r.right && py in (r.top - 6f)..(r.top + 6f)) {
                            collided = true
                            break
                        }
                    }
                }
                if (collided) {
                    d.splashing = true
                    d.splashTtl = 0.22f
                    d.splashX = px
                    d.splashY = py
                }
                if (d.splashing) {
                    d.splashTtl -= dt
                    val t = (1f - (d.splashTtl / 0.22f)).coerceIn(0f, 1f)
                    val r = 3f + t * 10f
                    drawCircle(
                        Color.White.copy(alpha = (1f - t) * 0.5f),
                        r,
                        Offset(d.splashX, d.splashY),
                        style = Stroke(1.4f)
                    )
                    if (d.splashTtl <= 0f) {
                        d.splashing = false
                        d.y = -Random.nextFloat() * h * 0.4f
                        d.x = Random.nextFloat()
                    }
                } else if (py > h + 20f) {
                    d.y = -Random.nextFloat() * h * 0.3f
                    d.x = Random.nextFloat()
                } else {
                    val tailX = px - 2.5f
                    val tailY = py - d.length
                    drawLine(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0f), Color(0xFFBFD9FF).copy(alpha = d.alpha)),
                            start = Offset(tailX, tailY),
                            end = Offset(px, py)
                        ),
                        Offset(tailX, tailY),
                        Offset(px, py),
                        strokeWidth = 2.2f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        if (isSnow) {
            flakes.forEach { f ->
                f.y += f.speed * dt
                f.sway += f.swaySpeed * dt
                val px = f.x * w + kotlin.math.sin(f.sway) * 18f
                val py = f.y
                var landed = false
                for (r in cardRects) {
                    if (px in r.left..r.right && py in (r.top - 4f)..(r.top + 4f)) {
                        landed = true
                        break
                    }
                }
                if (landed || py > h + 10f) {
                    f.y = -Random.nextFloat() * h * 0.3f
                    f.x = Random.nextFloat()
                } else {
                    drawCircle(Color.White.copy(alpha = f.alpha), f.radius, Offset(px, py))
                }
            }
        }

        if (isStorm && lightningAlpha > 0f) {
            drawRect(Color.White.copy(alpha = lightningAlpha * 0.55f))
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
