// ===== WeatherSim.kt =====
package com.catsblock.weather.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.sin
import kotlin.random.Random

class CardRegistry {
    val rects = HashMap<Any, Rect>()
}

class Bead(
    val card: Any,
    var rx: Float,
    var ry: Float,
    var r: Float,
    var life: Float = 7f,
    var trail: Float = 0f
)

class WeatherSim(private val fx: WeatherType.Fx, private val intensity: Float) {
    var w = 0f
    var h = 0f
    private val rnd = Random(System.nanoTime())

    // chuva
    private val maxDrops = 280
    private val dropCount = (50 + 230 * intensity).toInt().coerceAtMost(maxDrops)
    val dx = FloatArray(maxDrops)
    val dy = FloatArray(maxDrops)
    val dv = FloatArray(maxDrops)
    val dl = FloatArray(maxDrops)
    val windX = 45f
    val beads = ArrayList<Bead>()

    // neve
    private val maxFlakes = 220
    val flakeCount = (60 + 160 * intensity).toInt().coerceAtMost(maxFlakes)
    val fx_ = FloatArray(maxFlakes)
    val fy = FloatArray(maxFlakes)
    val fv = FloatArray(maxFlakes)
    val fr = FloatArray(maxFlakes)
    val fp = FloatArray(maxFlakes)

    // raios
    val bolts = ArrayList<List<Offset>>()
    var boltAge = 99f
    private var nextBolt = 1.5f

    private var inited = false

    private fun init(width: Float, height: Float) {
        w = width; h = height; inited = true
        for (i in 0 until dropCount) resetDrop(i, true)
        for (i in 0 until flakeCount) {
            fx_[i] = rnd.nextFloat() * w
            fy[i] = rnd.nextFloat() * h
            fv[i] = 40f + rnd.nextFloat() * 90f
            fr[i] = 1.5f + rnd.nextFloat() * 3.5f
            fp[i] = rnd.nextFloat() * 6.28f
        }
    }

    private fun resetDrop(i: Int, randomY: Boolean = false) {
        dx[i] = rnd.nextFloat() * (w + 120f) - 60f
        dy[i] = if (randomY) rnd.nextFloat() * h else -rnd.nextFloat() * 200f
        dv[i] = 900f + rnd.nextFloat() * 700f
        dl[i] = 16f + rnd.nextFloat() * 26f
    }

    fun resize(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        if (!inited || width != w || height != h) init(width, height)
    }

    fun step(dt: Float, cards: Map<Any, Rect>) {
        if (!inited) return
        when (fx) {
            WeatherType.Fx.RAIN, WeatherType.Fx.STORM -> stepRain(dt, cards)
            WeatherType.Fx.SNOW -> stepSnow(dt)
            else -> {}
        }
        if (fx == WeatherType.Fx.STORM) stepLightning(dt)
        stepBeads(dt, cards)
    }

    private fun stepRain(dt: Float, cards: Map<Any, Rect>) {
        for (i in 0 until dropCount) {
            val oldY = dy[i] + dl[i]
            dy[i] += dv[i] * dt
            dx[i] += windX * dt
            val tipY = dy[i] + dl[i]
            var consumed = false
            for ((id, r) in cards) {
                if (r.bottom < 0f || r.top > h) continue
                if (dx[i] < r.left || dx[i] > r.right) continue
                if (tipY >= r.top && tipY <= r.bottom) {
                    if (oldY < r.top && beads.size < 160) addBead(id, r, dx[i])
                    consumed = true
                    break
                }
            }
            if (consumed || dy[i] > h) resetDrop(i)
        }
    }

    private fun addBead(id: Any, r: Rect, x: Float) {
        val rx = x - r.left
        for (b in beads) {
            if (b.card === id && kotlin.math.abs(b.rx - rx) < 12f && b.ry < 14f + b.trail) {
                b.r = (b.r + 0.9f).coerceAtMost(6.5f)
                b.life = 7f
                return
            }
        }
        beads.add(Bead(id, rx, 1.5f, 1.6f + rnd.nextFloat() * 1.6f))
    }

    private fun stepBeads(dt: Float, cards: Map<Any, Rect>) {
        var i = beads.size - 1
        while (i >= 0) {
            val b = beads[i]
            val r = cards[b.card]
            if (r == null) { beads.removeAt(i); i--; continue }
            if (b.r > 3.2f) {
                val v = 14f + b.r * 9f
                b.ry += v * dt
                b.trail = (b.trail + v * dt * 0.8f).coerceAtMost(110f)
                b.r = (b.r - 0.12f * dt).coerceAtLeast(2.8f)
                b.rx += sin(b.ry * 0.05f) * 3f * dt
            } else {
                b.life -= dt
                b.trail = (b.trail - 8f * dt).coerceAtLeast(0f)
            }
            if (b.ry > r.height || b.life <= 0f) beads.removeAt(i)
            i--
        }
    }

    private fun stepSnow(dt: Float) {
        for (i in 0 until flakeCount) {
            fp[i] += dt * (0.8f + fr[i] * 0.2f)
            fy[i] += fv[i] * dt
            fx_[i] += (sin(fp[i]) * 28f + 12f) * dt
            if (fy[i] > h + 10f) { fy[i] = -10f; fx_[i] = rnd.nextFloat() * w }
            if (fx_[i] > w + 10f) fx_[i] = -10f
        }
    }

    private fun stepLightning(dt: Float) {
        boltAge += dt
        nextBolt -= dt
        if (nextBolt <= 0f) {
            genBolt()
            boltAge = 0f
            nextBolt = 2.2f + rnd.nextFloat() * 5f
        }
    }

    private fun genBolt() {
        bolts.clear()
        val main = ArrayList<Offset>()
        var x = w * (0.15f + rnd.nextFloat() * 0.7f)
        var y = -10f
        main.add(Offset(x, y))
        val target = h * (0.55f + rnd.nextFloat() * 0.4f)
        while (y < target) {
            y += 30f + rnd.nextFloat() * 50f
            x += (rnd.nextFloat() - 0.5f) * 90f
            main.add(Offset(x, y))
        }
        bolts.add(main)
        repeat(2 + rnd.nextInt(3)) {
            val idx = 2 + rnd.nextInt(maxOf(1, main.size - 3))
            var p = main[idx.coerceAtMost(main.size - 1)]
            val dir = if (rnd.nextBoolean()) 1 else -1
            val br = arrayListOf(p)
            repeat(3 + rnd.nextInt(4)) {
                p = Offset(p.x + dir * (15f + rnd.nextFloat() * 45f), p.y + 20f + rnd.nextFloat() * 40f)
                br.add(p)
            }
            bolts.add(br)
        }
    }
}
