package com.catsblock.weather.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.catsblock.weather.data.Condition
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

val LocalRainIntensity = compositionLocalOf { 0f }

fun rainIntensity(kind: Condition): Float = when (kind) {
    Condition.Drizzle -> 0.35f
    Condition.Rain -> 0.7f
    Condition.Storm -> 1f
    Condition.Sleet -> 0.5f
    Condition.Hail -> 0.6f
    else -> 0f
}

private class Drop(var x: Float, var y: Float, var r: Float, val crit: Float) {
    var vy = 0f
    var sliding = false
    var pause = 0f
    var trailTop = y
    var trailAlpha = 0f
    val phase = Random.nextFloat() * 6.2832f
    var dead = false
}

private class Fade(val x: Float, val y0: Float, val y1: Float, val w: Float, var alpha: Float)

private class DropField {
    private val drops = ArrayList<Drop>()
    private val fades = ArrayList<Fade>()
    private var width = 0f
    private var height = 0f
    private var spawnCarry = 0f
    private var primed = false

    fun resize(w: Float, h: Float, intensity: Float) {
        width = w
        height = h
        if (!primed && w > 0f && h > 0f) {
            primed = true
            val count = (intensity * w * h / 7000f).toInt().coerceIn(2, 14)
            repeat(count) { spawn(initial = true) }
        }
    }

    private fun spawn(initial: Boolean = false) {
        if (drops.size >= 34 || width <= 0f) return
        val r = 1.1f + Random.nextFloat().pow(2.2f) * 3.6f
        val crit = 3.0f + Random.nextFloat() * 1.0f
        val y = if (initial) Random.nextFloat() * height else Random.nextFloat() * height * 0.55f
        drops.add(Drop(Random.nextFloat() * width, y, r, crit))
    }

    private fun archive(d: Drop) {
        if (d.trailAlpha > 0.01f && d.y - d.trailTop > 1f) {
            fades.add(Fade(d.x, d.trailTop, d.y, d.r * 0.5f, d.trailAlpha))
        }
        d.trailTop = d.y
        d.trailAlpha = 0f
    }

    fun step(dt: Float, intensity: Float) {
        if (width <= 0f || height <= 0f) return
        spawnCarry += intensity * width * height / 9000f * dt
        while (spawnCarry >= 1f) {
            spawnCarry -= 1f
            spawn()
        }

        for (d in drops) {
            if (!d.sliding && d.r >= d.crit) {
                d.sliding = true
                d.trailTop = d.y
                d.trailAlpha = 0f
            }
            if (!d.sliding) continue
            if (d.pause > 0f) {
                d.pause -= dt
                d.vy -= d.vy * min(1f, 9f * dt)
            } else {
                val target = (d.r - d.crit * 0.55f) * 24f
                d.vy += (target - d.vy) * min(1f, 2.2f * dt)
                if (Random.nextFloat() < 0.35f * dt) d.pause = 0.12f + Random.nextFloat() * 0.35f
            }
            val dy = d.vy * dt
            d.y += dy
            d.x += sin(d.y * 0.11f + d.phase) * 0.06f * dy
            d.r = sqrt(max(0f, d.r * d.r - dy * 0.22f))
            d.trailAlpha = min(0.16f, d.trailAlpha + dt * 0.5f)
            if (d.r < d.crit * 0.78f) {
                d.sliding = false
                d.vy = 0f
                d.pause = 0f
                archive(d)
            }
            if (d.y - d.r > height) {
                archive(d)
                d.dead = true
            }
        }

        for (i in drops.indices) {
            val a = drops[i]
            if (a.dead) continue
            for (j in i + 1 until drops.size) {
                val b = drops[j]
                if (b.dead) continue
                val dx = a.x - b.x
                val dy = a.y - b.y
                val reach = (a.r + b.r) * 0.72f
                if (dx * dx + dy * dy < reach * reach) {
                    val big = if (a.r >= b.r) a else b
                    val small = if (big === a) b else a
                    val areaBig = big.r * big.r
                    val areaSmall = small.r * small.r
                    val total = areaBig + areaSmall
                    big.x = (big.x * areaBig + small.x * areaSmall) / total
                    if (!big.sliding) big.y = (big.y * areaBig + small.y * areaSmall) / total
                    big.r = sqrt(total)
                    small.dead = true
                    if (small.sliding) {
                        archive(small)
                    }
                    if (big === b) break
                }
            }
        }
        drops.removeAll { it.dead }

        val iterator = fades.iterator()
        while (iterator.hasNext()) {
            val f = iterator.next()
            f.alpha -= dt * 0.035f
            if (f.alpha <= 0f) iterator.remove()
        }
    }

    fun draw(scope: DrawScope) {
        val s = scope.density
        for (f in fades) scope.trail(f.x * s, f.y0 * s, f.y1 * s, f.w * s, f.alpha)
        for (d in drops) {
            if (d.sliding) scope.trail(d.x * s, d.trailTop * s, d.y * s, d.r * 0.5f * s, d.trailAlpha)
            scope.drop(d, s)
        }
    }
}

private fun DrawScope.trail(x: Float, y0: Float, y1: Float, w: Float, alpha: Float) {
    if (y1 - y0 < 1f || alpha <= 0f) return
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = alpha)),
            startY = y0,
            endY = y1
        ),
        start = Offset(x, y0),
        end = Offset(x, y1),
        strokeWidth = max(1f, w),
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drop(d: Drop, s: Float) {
    val r = d.r * s
    if (r < 1f) return
    val c = Offset(d.x * s, d.y * s)
    val stretch = 1f + min(0.45f, d.vy / 90f)
    withTransform({ scale(1f, stretch, c) }) {
        drawCircle(Color.Black.copy(alpha = 0.16f), r * 1.08f, c + Offset(0f, r * 0.12f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.24f),
                    Color.White.copy(alpha = 0.07f),
                    Color.Transparent
                ),
                center = c + Offset(0f, r * 0.25f),
                radius = r
            ),
            radius = r,
            center = c
        )
        drawCircle(Color.White.copy(alpha = 0.18f), r, c, style = Stroke(width = max(1f, r * 0.05f)))
        drawArc(
            color = Color.Black.copy(alpha = 0.30f),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = c - Offset(r * 0.92f, r * 0.92f),
            size = Size(r * 1.84f, r * 1.84f),
            style = Stroke(width = r * 0.16f, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color.White.copy(alpha = 0.55f),
            startAngle = 200f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = c - Offset(r * 0.88f, r * 0.88f),
            size = Size(r * 1.76f, r * 1.76f),
            style = Stroke(width = r * 0.12f, cap = StrokeCap.Round)
        )
        drawCircle(Color.White.copy(alpha = 0.85f), r * 0.17f, c + Offset(-r * 0.38f, -r * 0.42f))
        drawCircle(Color.White.copy(alpha = 0.25f), r * 0.10f, c + Offset(r * 0.35f, r * 0.40f))
    }
}

@Composable
fun Modifier.rainDrops(intensity: Float): Modifier {
    val field = remember { DropField() }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(intensity) {
        if (intensity > 0f) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) field.step(((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f), intensity)
                    last = now
                    frame = now
                }
            }
        }
    }
    return if (intensity <= 0f) {
        this
    } else {
        this.drawWithContent {
            drawContent()
            field.resize(size.width / density, size.height / density, intensity)
            if (frame >= 0L) field.draw(this)
        }
    }
}
