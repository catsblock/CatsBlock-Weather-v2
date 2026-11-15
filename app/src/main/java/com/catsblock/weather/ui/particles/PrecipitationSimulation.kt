package com.catsblock.weather.ui.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val TWO_PI = 6.2831855f
private const val MAX_SUBSTEP = 0.02f
private const val MAX_ADVANCE = 0.25f
private const val RAIN_CAPACITY = 520
private const val SNOW_CAPACITY = 760
private const val HAIL_CAPACITY = 90
private const val KIND_RAIN = 0
private const val KIND_SNOW = 1
private const val KIND_HAIL = 2
private const val FLAG_ICY = 1
private const val FLAG_FAR = 2
private const val FLAG_BLUR = 4

private val RainColor = Color(0xFFDCE8F5)
private val IcyRainColor = Color(0xFFEFF8FF)
private val SnowColor = Color(0xFFF8FBFF)
private val HailBody = Color(0xFFE9F1F6)
private val HailShade = Color(0xFFB9C9D6)
private val HailEdge = Color(0xFFAFC0CC)

private fun hash01(seed: Int, k: Int): Float {
    var h = seed * -1640531535 + k * 668265263
    h = h xor (h ushr 13)
    h *= 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777216f
}

private class Pool(val capacity: Int) {
    var count = 0
    var carry = 0f
    var warmed = false
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)
    val term = FloatArray(capacity)
    val size = FloatArray(capacity)
    val depth = FloatArray(capacity)
    val q = FloatArray(capacity)
    val phase = FloatArray(capacity)
    val age = FloatArray(capacity)
    val flag = IntArray(capacity)
    val seed = IntArray(capacity)

    fun add(): Int = if (count < capacity) count++ else -1

    fun remove(i: Int) {
        val last = count - 1
        if (i != last) {
            x[i] = x[last]
            y[i] = y[last]
            vx[i] = vx[last]
            vy[i] = vy[last]
            term[i] = term[last]
            size[i] = size[last]
            depth[i] = depth[last]
            q[i] = q[last]
            phase[i] = phase[last]
            age[i] = age[last]
            flag[i] = flag[last]
            seed[i] = seed[last]
        }
        count = last
    }

    fun reset() {
        count = 0
        carry = 0f
        warmed = false
    }

    fun rescale(sx: Float, sy: Float) {
        for (i in 0 until count) {
            x[i] *= sx
            y[i] *= sy
        }
    }
}

internal class PrecipitationSimulation(private val d: Float) {
    private val random = Random(System.nanoTime())
    private val rain = Pool(RAIN_CAPACITY)
    private val snow = Pool(SNOW_CAPACITY)
    private val hail = Pool(HAIL_CAPACITY)
    private val hailPath = Path()
    private val hailEdgeStroke = Stroke(max(0.6f, 0.7f * d))
    private var width = 0f
    private var height = 0f
    private var time = 0f
    private var wind = 0f
    private var freezeShare = 0f

    val alive: Boolean get() = rain.count + snow.count + hail.count > 0

    fun resize(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        if (width > 0f && height > 0f && (w != width || h != height)) {
            val sx = w / width
            val sy = h / height
            rain.rescale(sx, sy)
            snow.rescale(sx, sy)
            hail.rescale(sx, sy)
        }
        width = w
        height = h
    }

    fun clear() {
        rain.reset()
        snow.reset()
        hail.reset()
    }

    fun step(dt: Float, mix: PrecipMix, windBase: Float, tierScale: Float) {
        if (width <= 0f || height <= 0f) return
        val rainTotal = mix.rain + mix.freezing
        freezeShare = if (rainTotal > 0.001f) mix.freezing / rainTotal else 0f
        val rainTarget = min((rainTotal * 300f * tierScale).toInt(), RAIN_CAPACITY)
        val snowTarget = min((mix.snow * 480f * tierScale).toInt(), SNOW_CAPACITY)
        val hailTarget = min((mix.hail * 46f * tierScale).toInt(), HAIL_CAPACITY)
        var remaining = min(dt, MAX_ADVANCE)
        while (remaining > 0f) {
            val h = min(remaining, MAX_SUBSTEP)
            remaining -= h
            time += h
            val gust = 1f + 0.32f * sin(time * 0.55f) + 0.18f * sin(time * 1.7f + 1.3f) + 0.08f * sin(time * 3.9f + 0.4f)
            wind += (windBase * gust - wind) * (1f - exp(-h * 1.4f))
            stepRain(h, rainTarget)
            stepSnow(h, snowTarget)
            stepHail(h, hailTarget)
        }
    }

    private fun feed(pool: Pool, kind: Int, target: Int, transit: Float, h: Float) {
        if (target <= 0) {
            pool.carry = 0f
            if (pool.count == 0) pool.warmed = false
            return
        }
        if (!pool.warmed && pool.count == 0) {
            pool.warmed = true
            for (n in 0 until target) spawn(pool, kind, true)
            return
        }
        if (pool.count >= target) {
            pool.carry = 0f
            return
        }
        pool.carry += target / transit * h
        while (pool.carry >= 1f && pool.count < target) {
            pool.carry -= 1f
            spawn(pool, kind, false)
        }
    }

    private fun spawnX(): Float {
        val drift = abs(wind) * height * 1.15f
        return random.nextFloat() * (width + drift) - (if (wind > 0f) drift else 0f)
    }

    private fun spawn(pool: Pool, kind: Int, initial: Boolean) {
        val i = pool.add()
        if (i < 0) return
        val q = random.nextFloat()
        pool.q[i] = q
        pool.phase[i] = random.nextFloat() * TWO_PI
        pool.seed[i] = random.nextInt()
        pool.age[i] = 0f
        pool.flag[i] = 0
        val u = random.nextFloat()
        when (kind) {
            KIND_RAIN -> {
                val z = 0.35f + 0.65f * u * u
                val term = (620f + 700f * z) * d * (0.94f + 0.12f * random.nextFloat())
                pool.depth[i] = z
                pool.term[i] = term
                pool.size[i] = (0.5f + 1.1f * z) * d
                pool.flag[i] = if (random.nextFloat() < freezeShare) FLAG_ICY else 0
                pool.vy[i] = term * (if (initial) 1f else 0.7f)
                pool.vx[i] = wind * pool.vy[i] * (0.85f + 0.3f * q)
            }
            KIND_SNOW -> {
                val roll = random.nextFloat()
                val flag = when {
                    roll < 0.38f -> FLAG_FAR
                    roll < 0.43f -> FLAG_BLUR
                    else -> 0
                }
                val z = when (flag) {
                    FLAG_FAR -> 0.3f + 0.25f * u
                    FLAG_BLUR -> 1f
                    else -> 0.55f + 0.45f * u
                }
                pool.flag[i] = flag
                pool.depth[i] = z
                pool.size[i] = when (flag) {
                    FLAG_FAR -> (0.6f + 0.8f * z) * d
                    FLAG_BLUR -> (3.2f + 2.2f * q) * d
                    else -> (0.7f + 1.5f * z) * d
                }
                pool.term[i] = (if (flag == FLAG_BLUR) 90f else 26f + 36f * z) * d * (0.8f + 0.4f * q)
                pool.vy[i] = pool.term[i] * (if (initial) 1f else 0.8f)
                pool.vx[i] = wind * 260f * d * (0.6f + 0.6f * q)
            }
            else -> {
                val z = 0.55f + 0.45f * u
                pool.depth[i] = z
                pool.size[i] = (1.8f + 2.8f * q * q + z) * d
                pool.term[i] = (1500f + 600f * q) * d
                pool.vy[i] = pool.term[i] * (if (initial) 0.6f + 0.4f * random.nextFloat() else 0.2f)
                pool.vx[i] = wind * pool.vy[i] * 0.7f
            }
        }
        pool.x[i] = spawnX()
        pool.y[i] = if (initial) {
            random.nextFloat() * height
        } else {
            -pool.size[i] * 2f - random.nextFloat() * pool.vy[i] * MAX_SUBSTEP
        }
    }

    private fun stepRain(h: Float, target: Int) {
        feed(rain, KIND_RAIN, target, height / (1000f * d), h)
        val p = rain
        val relaxV = 1f - exp(-h * 4f)
        val relaxH = 1f - exp(-h * 3f)
        val margin = 40f * d
        var i = 0
        while (i < p.count) {
            p.vy[i] += (p.term[i] - p.vy[i]) * relaxV
            p.vx[i] += (wind * p.vy[i] * (0.85f + 0.3f * p.q[i]) - p.vx[i]) * relaxH
            p.x[i] += p.vx[i] * h
            p.y[i] += p.vy[i] * h
            p.age[i] += h
            if (leaving(p, i, margin)) p.remove(i) else i++
        }
    }

    private fun stepSnow(h: Float, target: Int) {
        feed(snow, KIND_SNOW, target, height / (70f * d), h)
        val p = snow
        val relaxV = 1f - exp(-h * 1.4f)
        val relaxH = 1f - exp(-h * 1.6f)
        val margin = 24f * d
        val span = width + margin * 2f
        var i = 0
        while (i < p.count) {
            val q = p.q[i]
            val w = 0.5f + 0.9f * q
            val sway = cos(p.age[i] * w + p.phase[i]) * (10f + 16f * q) * d * w +
                cos(p.age[i] * w * 1.9f + p.phase[i] * 1.7f) * 3f * d * w * 1.9f
            p.vy[i] += (p.term[i] - p.vy[i]) * relaxV
            p.vx[i] += (wind * 260f * d * (0.6f + 0.6f * q) - p.vx[i]) * relaxH
            p.x[i] += (p.vx[i] + sway) * h
            p.y[i] += p.vy[i] * h
            p.age[i] += h
            if (p.x[i] < -margin) p.x[i] += span else if (p.x[i] > width + margin) p.x[i] -= span
            if (p.y[i] > height + margin + p.size[i]) p.remove(i) else i++
        }
    }

    private fun stepHail(h: Float, target: Int) {
        feed(hail, KIND_HAIL, target, height / (1700f * d), h)
        val p = hail
        val gravity = 2800f * d
        val relaxH = 1f - exp(-h * 2f)
        val margin = 40f * d
        var i = 0
        while (i < p.count) {
            p.vy[i] = min(p.term[i], p.vy[i] + gravity * h)
            p.vx[i] += (wind * p.vy[i] * 0.7f - p.vx[i]) * relaxH
            p.x[i] += p.vx[i] * h
            p.y[i] += p.vy[i] * h
            p.age[i] += h
            if (leaving(p, i, margin)) p.remove(i) else i++
        }
    }

    private fun leaving(p: Pool, i: Int, margin: Float): Boolean =
        p.y[i] > height + margin ||
            (p.x[i] < -margin && p.vx[i] <= 0f) ||
            (p.x[i] > width + margin && p.vx[i] >= 0f)

    fun draw(scope: DrawScope) {
        with(scope) {
            drawRain()
            drawSnow()
            drawHail()
        }
    }

    private fun DrawScope.drawRain() {
        val p = rain
        for (i in 0 until p.count) {
            val vx = p.vx[i]
            val vy = p.vy[i]
            val speed = sqrt(vx * vx + vy * vy)
            if (speed < 1f) continue
            val len = max(speed * 0.021f, 4f * d)
            val ux = vx / speed
            val uy = vy / speed
            val x = p.x[i]
            val y = p.y[i]
            val z = p.depth[i]
            val base = if ((p.flag[i] and FLAG_ICY) != 0) IcyRainColor else RainColor
            drawLine(
                base.copy(alpha = 0.16f + 0.3f * z),
                Offset(x - ux * len, y - uy * len),
                Offset(x, y),
                p.size[i],
                StrokeCap.Round
            )
        }
    }

    private fun DrawScope.drawSnow() {
        val p = snow
        for (i in 0 until p.count) {
            val r = p.size[i]
            val c = Offset(p.x[i], p.y[i])
            val flag = p.flag[i]
            val q = p.q[i]
            when {
                (flag and FLAG_BLUR) != 0 -> {
                    drawCircle(SnowColor.copy(alpha = 0.10f), r * 1.5f, c)
                    drawCircle(SnowColor.copy(alpha = 0.2f), r, c)
                }
                (flag and FLAG_FAR) != 0 -> drawCircle(SnowColor.copy(alpha = 0.28f + 0.2f * q), r, c)
                else -> {
                    drawCircle(SnowColor.copy(alpha = 0.16f), r * 1.8f, c)
                    drawCircle(SnowColor.copy(alpha = 0.5f + 0.42f * q), r, c)
                }
            }
        }
    }

    private fun DrawScope.drawHail() {
        val p = hail
        for (i in 0 until p.count) {
            val s = p.size[i]
            val x = p.x[i]
            val y = p.y[i]
            val vx = p.vx[i]
            val vy = p.vy[i]
            val speed = sqrt(vx * vx + vy * vy)
            if (speed < 1f) continue
            val len = speed * 0.008f
            val ux = vx / speed
            val uy = vy / speed
            val rot = p.q[i] * TWO_PI + p.age[i] * (2.5f + 5f * p.q[i])
            val seed = p.seed[i]
            drawLine(
                HailBody.copy(alpha = 0.22f),
                Offset(x - ux * len, y - uy * len),
                Offset(x, y),
                s * 1.1f,
                StrokeCap.Round
            )
            buildHail(x, y, s, rot, seed, 6)
            drawPath(hailPath, HailBody.copy(alpha = 0.88f))
            drawPath(hailPath, HailEdge.copy(alpha = 0.55f), style = hailEdgeStroke)
            buildHail(x + s * 0.05f, y + s * 0.05f, s * 0.55f, rot + 0.6f, seed + 5, 5)
            drawPath(hailPath, HailShade.copy(alpha = 0.3f))
            drawCircle(Color.White.copy(alpha = 0.8f), s * 0.22f, Offset(x - s * 0.28f, y - s * 0.3f))
        }
    }

    private fun buildHail(cx: Float, cy: Float, r: Float, rot: Float, seed: Int, n: Int) {
        hailPath.reset()
        for (k in 0 until n) {
            val a = rot + (k + (hash01(seed, k) - 0.5f) * 0.6f) * (TWO_PI / n)
            val rr = r * (0.62f + 0.38f * hash01(seed, k + 17))
            val px = cx + cos(a) * rr
            val py = cy + sin(a) * rr
            if (k == 0) hailPath.moveTo(px, py) else hailPath.lineTo(px, py)
        }
        hailPath.close()
    }
}
