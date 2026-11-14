package com.catsblock.weather.ui.particles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.catsblock.weather.data.Condition
import com.catsblock.weather.ui.rememberPowerSaveMode
import com.catsblock.weather.ui.sky.LightningState
import com.catsblock.weather.ui.sky.SkyTier
import com.catsblock.weather.ui.sky.boltReach
import com.catsblock.weather.ui.sky.buildBolt
import com.catsblock.weather.ui.sky.detectSkyTier
import com.catsblock.weather.ui.sky.drawBolt
import com.catsblock.weather.ui.sky.lightningEnvelope
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val TWO_PI = 6.2831855f
private const val NEVER = 3.0e38f
private const val F_FAR = 1
private const val F_FROZEN = 2
private const val F_BLUR = 4
private const val D_SPLASH = 0
private const val D_ICE = 1
private const val D_SHARD = 2
private const val D_DRIP = 3

private val RainColor = Color(0xFFD6E8FF)
private val FrostRainColor = Color(0xFFE4F2FF)
private val SnowColor = Color(0xFFF8FBFF)
private val HailBody = Color(0xFFE9F1F6)
private val HailShade = Color(0xFFB9C9D6)
private val HailEdge = Color(0xFFAFC0CC)
private val SplashColor = Color(0xFFE8F3FF)
private val MistColor = Color(0xFFC9D8E6)
private val FlashColor = Color(0xFFB8C8FF)
private val GlowColor = Color(0xFFEAF0FF)
private val EmptyBolt = FloatArray(0)

@Immutable
data class PrecipMix(
    val rain: Float = 0f,
    val snow: Float = 0f,
    val hail: Float = 0f,
    val freezing: Float = 0f,
    val thunder: Float = 0f,
    val tempC: Float = 10f
) {
    val active: Boolean get() = rain + snow + hail + freezing > 0.01f

    companion object {
        val None = PrecipMix()
    }
}

fun precipMixFor(kind: Condition, tempC: Double, precipMm: Double): PrecipMix {
    val t = tempC.toFloat()
    var rain = 0f
    var snow = 0f
    var hail = 0f
    var freezing = 0f
    var thunder = 0f
    when (kind) {
        Condition.Drizzle -> rain = 0.2f
        Condition.Rain -> rain = 0.5f
        Condition.HeavyRain -> rain = 0.85f
        Condition.ScatteredShowers -> rain = 0.4f
        Condition.Storm -> {
            rain = 0.85f
            thunder = 0.85f
        }
        Condition.ScatteredStorms -> {
            rain = 0.6f
            thunder = 0.85f
        }
        Condition.IsolatedStorms -> {
            rain = 0.45f
            thunder = 0.6f
        }
        Condition.StrongStorms -> {
            rain = 1f
            thunder = 1f
        }
        Condition.Hail -> {
            hail = 0.65f
            rain = 0.3f
        }
        Condition.Sleet -> {
            rain = 0.3f
            hail = 0.25f
            snow = 0.2f
        }
        Condition.Icy -> if (t <= 0.5f) freezing = 0.5f else rain = 0.25f
        Condition.RainSnow -> {
            rain = 0.35f
            snow = 0.4f
        }
        Condition.WintryMix -> {
            rain = 0.25f
            snow = 0.35f
            hail = 0.2f
        }
        Condition.Snow -> snow = 0.5f
        Condition.HeavySnow -> snow = 0.85f
        Condition.Blizzard -> snow = 1f
        Condition.Flurries -> snow = 0.22f
        Condition.BlowingSnow -> snow = 0.6f
        Condition.ScatteredSnow -> snow = 0.35f
        Condition.SnowShowers -> snow = 0.55f
        else -> Unit
    }
    if (t <= -0.5f) {
        freezing += rain
        rain = 0f
    }
    val scale = if (precipMm < 0.0) 1f else (0.55f + precipMm.toFloat() * 0.3f).coerceIn(0.4f, 1.3f)
    return PrecipMix(
        rain = min(rain * scale, 1.2f),
        snow = min(snow * scale, 1.2f),
        hail = min(hail * scale, 1.2f),
        freezing = min(freezing * scale, 1.2f),
        thunder = thunder,
        tempC = t
    )
}

private fun thunderPeriod(thunder: Float): LongRange = when {
    thunder >= 0.95f -> 3000L..7000L
    thunder >= 0.8f -> 5000L..11000L
    else -> 9000L..20000L
}

private fun tierScale(tier: SkyTier): Float = when (tier) {
    SkyTier.Minimal -> 0.4f
    SkyTier.VeryLow -> 0.5f
    SkyTier.Low -> 0.65f
    SkyTier.Medium -> 0.85f
    SkyTier.High -> 1f
    SkyTier.Ultra -> 1.2f
}

private class Air(val cap: Int) {
    var n = 0
    val x = FloatArray(cap)
    val y = FloatArray(cap)
    val v = FloatArray(cap)
    val s = FloatArray(cap)
    val t = FloatArray(cap)
    val tp = FloatArray(cap)
    val q = FloatArray(cap)
    val fl = IntArray(cap)
    val sd = IntArray(cap)
}

private class Debris(val cap: Int) {
    var n = 0
    val kind = IntArray(cap)
    val x = FloatArray(cap)
    val y = FloatArray(cap)
    val vx = FloatArray(cap)
    val vy = FloatArray(cap)
    val rot = FloatArray(cap)
    val om = FloatArray(cap)
    val sz = FloatArray(cap)
    val age = FloatArray(cap)
    val life = FloatArray(cap)
    val land = FloatArray(cap)
    val seed = IntArray(cap)
    val col = IntArray(cap)
    val poly = FloatArray(cap * 8)
}

internal class WeatherSim(private val d: Float) : DripSink {
    private val random = Random(System.nanoTime())
    private val rain = Air(260)
    private val snow = Air(520)
    private val hail = Air(70)
    private val debris = Debris(300)
    private val path = Path()
    private var windNow = 0f
    private var time = 0f
    private var freezeShare = 0f
    private var lastWarm = true
    private var glazeLimit = 0.32f
    private var width = 0f
    private var height = 0f
    private var originX = 0f
    private var originY = 0f
    private var bolt = EmptyBolt
    private var boltReveal = 0f
    private var boltFade = 0f
    private var boltActive = false
    private var strikeSurface: CardSurface? = null
    private var strikeLx = 0f
    private var strikeLy = 0f
    private var strikeX0 = 0f
    private var glowX = 0f
    private var glowY = 0f
    private var glowAge = 9f
    private var glowRadius = 0f
    private var mist = 0f
    var coords: LayoutCoordinates? = null

    fun clearAir() {
        rain.n = 0
        snow.n = 0
        hail.n = 0
    }

    fun busy(active: Boolean): Boolean {
        if (active || boltActive || debris.n > 0 || glowAge < 0.45f) return true
        val surfaces = WeatherSurfaces.surfaces
        for (k in surfaces.indices) {
            val s = surfaces[k]
            if (s.count > 0 || s.holeActive) return true
        }
        return false
    }

    fun idleTick(dt: Float) {
        var changed = false
        val surfaces = WeatherSurfaces.surfaces
        for (k in surfaces.indices) {
            val s = surfaces[k]
            if (s.glaze > 0f) {
                s.glaze = max(0f, s.glaze - dt * (if (lastWarm) 0.02f else 0.004f))
                changed = true
            }
        }
        if (changed) WeatherSurfaces.frame.longValue += 1L
    }

    private fun syncOrigin(): Boolean {
        val c = coords ?: return false
        if (!c.isAttached) return false
        val p = c.positionInRoot()
        originX = p.x
        originY = p.y
        width = c.size.width.toFloat()
        height = c.size.height.toFloat()
        return width > 0f && height > 0f
    }

    fun step(dt: Float, mix: PrecipMix, windBase: Float, scale: Float) {
        if (!syncOrigin()) return
        time += dt
        val gust = 1f + 0.32f * sin(time * 0.55f) + 0.18f * sin(time * 1.7f + 1.3f) + 0.08f * sin(time * 3.9f + 0.4f)
        windNow += (windBase * gust - windNow) * min(1f, dt * 1.4f)
        val rainTotal = mix.rain + mix.freezing
        freezeShare = if (rainTotal > 0.001f) mix.freezing / rainTotal else 0f
        val mistTarget = if (rainTotal > 0.55f) (rainTotal - 0.45f) * 0.1f else 0f
        mist += (mistTarget - mist) * min(1f, dt * 0.8f)
        val freezeActive = mix.freezing > 0.05f && mix.tempC <= 0.5f
        lastWarm = mix.tempC > 0.5f
        glazeLimit = if (mix.tempC <= -2f) 0.6f else 0.32f
        val surfaces = WeatherSurfaces.surfaces
        for (k in surfaces.indices) surfaces[k].refresh()
        if (mix.active) {
            fill(rain, (rainTotal * 170f * scale).toInt(), 0)
            fill(snow, (mix.snow * 380f * scale).toInt(), 1)
            fill(hail, (mix.hail * 34f * scale).toInt(), 2)
            stepRain(dt)
            stepSnow(dt)
            stepHail(dt)
        }
        for (k in surfaces.indices) surfaces[k].step(dt, d, freezeActive, lastWarm, windNow, random, this)
        stepDebris(dt)
        glowAge += dt
    }

    private fun fill(a: Air, target: Int, type: Int) {
        val t = min(target, a.cap)
        while (a.n < t) {
            val i = a.n
            a.n++
            when (type) {
                0 -> spawnRain(i, true)
                1 -> spawnSnow(i, true)
                else -> spawnHail(i, true)
            }
        }
        if (a.n > t) a.n = t
    }

    private fun spawnX(): Float {
        val drift = abs(windNow) * height * 1.1f
        return random.nextFloat() * (width + drift) - (if (windNow > 0f) drift else 0f)
    }

    private fun spawnRain(i: Int, initial: Boolean) {
        val a = rain
        val far = random.nextFloat() < 0.5f
        val frozen = !far && random.nextFloat() < freezeShare
        val u = random.nextFloat()
        val dp = if (far) 0.4f + 0.4f * u else 0.7f + 1.5f * u * u
        val speed = (760f + 330f * dp + 140f * random.nextFloat()) * d * (if (far) 0.88f else 1f)
        a.s[i] = dp * d
        a.v[i] = speed
        a.q[i] = random.nextFloat()
        a.fl[i] = (if (far) F_FAR else 0) or (if (frozen) F_FROZEN else 0)
        a.x[i] = spawnX()
        val fall = (height + 40f * d) / speed
        if (initial) {
            a.y[i] = random.nextFloat() * height
            a.t[i] = a.y[i] / speed
        } else {
            a.y[i] = -random.nextFloat() * height * 0.1f - 20f * d
            a.t[i] = 0f
        }
        a.tp[i] = if (far) NEVER else a.t[i] + 0.05f + random.nextFloat() * max(0.05f, fall - a.t[i])
    }

    private fun spawnSnow(i: Int, initial: Boolean) {
        val a = snow
        val roll = random.nextFloat()
        val flag = when {
            roll < 0.38f -> F_FAR
            roll < 0.43f -> F_BLUR
            else -> 0
        }
        val u = random.nextFloat()
        val dp = when (flag) {
            F_FAR -> 0.45f + 0.5f * u
            F_BLUR -> 2.4f + 2.0f * u
            else -> 0.7f + 1.2f * u * u
        }
        val speed = (24f + 30f * random.nextFloat() + 8f * dp) * d * (if (flag == F_FAR) 0.82f else 1f)
        a.s[i] = dp * d
        a.v[i] = speed
        a.q[i] = random.nextFloat()
        a.fl[i] = flag
        a.x[i] = random.nextFloat() * width
        val fall = (height + 20f * d) / speed
        if (initial) {
            a.y[i] = random.nextFloat() * height
            a.t[i] = a.y[i] / speed
        } else {
            a.y[i] = -random.nextFloat() * height * 0.05f - a.s[i]
            a.t[i] = 0f
        }
        a.tp[i] = if (flag != 0) NEVER else a.t[i] + 0.2f + random.nextFloat() * max(0.05f, fall - a.t[i])
    }

    private fun spawnHail(i: Int, initial: Boolean) {
        val a = hail
        val u = random.nextFloat()
        val dp = 1.5f + 2.2f * u * u
        val speed = (820f + 190f * dp + 120f * random.nextFloat()) * d
        a.s[i] = dp * d
        a.v[i] = speed
        a.q[i] = random.nextFloat()
        a.fl[i] = 0
        a.sd[i] = random.nextInt()
        a.x[i] = spawnX()
        val fall = (height + 40f * d) / speed
        if (initial) {
            a.y[i] = random.nextFloat() * height
            a.t[i] = a.y[i] / speed
        } else {
            a.y[i] = -random.nextFloat() * height * 0.1f - 20f * d
            a.t[i] = 0f
        }
        a.tp[i] = a.t[i] + 0.05f + random.nextFloat() * max(0.05f, fall - a.t[i])
    }

    private fun stepRain(dt: Float) {
        val a = rain
        var i = 0
        while (i < a.n) {
            val wr = windNow * (0.85f + 0.3f * a.q[i])
            a.t[i] += dt
            a.y[i] += a.v[i] * dt
            a.x[i] += a.v[i] * wr * dt
            if (a.t[i] >= a.tp[i]) impactRain(i) else if (a.y[i] > height + 30f * d) spawnRain(i, false)
            i++
        }
    }

    private fun stepSnow(dt: Float) {
        val a = snow
        var i = 0
        while (i < a.n) {
            val q = a.q[i]
            a.t[i] += dt
            val turb = sin(a.t[i] * (0.45f + 0.6f * q) + q * 40f) * 7f * (0.4f + q) +
                sin(a.t[i] * (1.1f + 0.8f * q) + q * 13f) * 3.5f
            a.y[i] += a.v[i] * dt
            a.x[i] += (turb * d + windNow * 260f * d * (0.6f + 0.6f * q)) * dt
            if (a.x[i] < -24f * d) a.x[i] += width + 48f * d else if (a.x[i] > width + 24f * d) a.x[i] -= width + 48f * d
            if (a.t[i] >= a.tp[i]) impactSnow(i) else if (a.y[i] > height + 12f * d) spawnSnow(i, false)
            i++
        }
    }

    private fun stepHail(dt: Float) {
        val a = hail
        var i = 0
        while (i < a.n) {
            a.t[i] += dt
            a.y[i] += a.v[i] * dt
            a.x[i] += a.v[i] * windNow * 0.7f * dt
            if (a.t[i] >= a.tp[i]) impactHail(i) else if (a.y[i] > height + 30f * d) spawnHail(i, false)
            i++
        }
    }

    private fun impactRain(i: Int) {
        val a = rain
        val rx = a.x[i] + originX
        val ry = a.y[i] + originY
        val s = WeatherSurfaces.hit(rx, ry, a.s[i])
        if (s == null) {
            a.tp[i] = NEVER
            return
        }
        val frozen = (a.fl[i] and F_FROZEN) != 0
        splash(a.x[i], a.y[i], a.s[i], frozen)
        s.rainImpact(rx - s.left, ry - s.top, a.s[i], d, random, frozen)
        if (frozen) s.addGlaze(0.004f * (a.s[i] / d), glazeLimit)
        spawnRain(i, false)
    }

    private fun impactSnow(i: Int) {
        val a = snow
        val rx = a.x[i] + originX
        val ry = a.y[i] + originY
        val s = WeatherSurfaces.hit(rx, ry, a.s[i])
        if (s == null) {
            a.tp[i] = NEVER
            return
        }
        if (random.nextFloat() < 0.85f) s.snowLand(rx - s.left, ry - s.top, a.s[i] * 0.9f, d, random)
        spawnSnow(i, false)
    }

    private fun impactHail(i: Int) {
        val a = hail
        val rx = a.x[i] + originX
        val ry = a.y[i] + originY
        val s = WeatherSurfaces.hit(rx, ry, a.s[i])
        if (s == null) {
            a.tp[i] = NEVER
            return
        }
        val sz = a.s[i]
        s.chipLand(rx - s.left, ry - s.top, sz * 0.7f, 15f, a.sd[i])
        val pieces = 3 + random.nextInt(3) + (if (sz > 2.6f * d) 1 else 0)
        for (k in 0 until pieces) {
            val j = newDebris()
            if (j < 0) break
            val ang = -(0.2f + random.nextFloat() * 2.74f)
            val sp = (60f + random.nextFloat() * 220f) * d
            debris.kind[j] = D_ICE
            debris.x[j] = a.x[i]
            debris.y[j] = a.y[i]
            debris.vx[j] = cos(ang) * sp + windNow * 60f * d
            debris.vy[j] = sin(ang) * sp
            debris.rot[j] = random.nextFloat() * TWO_PI
            debris.om[j] = (random.nextFloat() - 0.5f) * 2f * (5f + 11f * random.nextFloat())
            debris.sz[j] = sz * (0.2f + 0.28f * random.nextFloat())
            debris.age[j] = 0f
            debris.life[j] = 15f
            debris.land[j] = 0.3f + random.nextFloat() * 0.7f
            debris.seed[j] = random.nextInt()
        }
        spawnHail(i, false)
    }

    private fun splash(x: Float, y: Float, r: Float, frozen: Boolean) {
        if (r < 0.85f * d && random.nextFloat() < 0.6f) return
        val pieces = if (frozen) 1 + random.nextInt(2) else 2 + random.nextInt(3)
        for (k in 0 until pieces) {
            val j = newDebris()
            if (j < 0) return
            val ang = -(0.25f + random.nextFloat() * 2.6f)
            val sp = (35f + random.nextFloat() * 110f) * d * (0.6f + r / (4f * d))
            debris.kind[j] = D_SPLASH
            debris.x[j] = x
            debris.y[j] = y
            debris.vx[j] = cos(ang) * sp
            debris.vy[j] = sin(ang) * sp
            debris.rot[j] = 0f
            debris.om[j] = 0f
            debris.sz[j] = (0.28f + 0.4f * random.nextFloat()) * d
            debris.age[j] = 0f
            debris.life[j] = 0.28f + random.nextFloat() * 0.3f
            debris.land[j] = NEVER
        }
    }

    override fun drip(rootX: Float, rootY: Float, r: Float, vy: Float) {
        val j = newDebris()
        if (j < 0) return
        debris.kind[j] = D_DRIP
        debris.x[j] = rootX - originX
        debris.y[j] = rootY - originY
        debris.vx[j] = 0f
        debris.vy[j] = vy
        debris.rot[j] = 0f
        debris.om[j] = 0f
        debris.sz[j] = r
        debris.age[j] = 0f
        debris.life[j] = 0.9f
        debris.land[j] = NEVER
    }

    private fun newDebris(): Int {
        if (debris.n >= debris.cap) return -1
        val i = debris.n
        debris.n++
        return i
    }

    private fun removeDebris(i: Int) {
        val last = debris.n - 1
        if (i != last) {
            debris.kind[i] = debris.kind[last]
            debris.x[i] = debris.x[last]
            debris.y[i] = debris.y[last]
            debris.vx[i] = debris.vx[last]
            debris.vy[i] = debris.vy[last]
            debris.rot[i] = debris.rot[last]
            debris.om[i] = debris.om[last]
            debris.sz[i] = debris.sz[last]
            debris.age[i] = debris.age[last]
            debris.life[i] = debris.life[last]
            debris.land[i] = debris.land[last]
            debris.seed[i] = debris.seed[last]
            debris.col[i] = debris.col[last]
            System.arraycopy(debris.poly, last * 8, debris.poly, i * 8, 8)
        }
        debris.n = last
    }

    private fun stepDebris(dt: Float) {
        var i = 0
        while (i < debris.n) {
            val kind = debris.kind[i]
            val g = when (kind) {
                D_SPLASH -> 900f
                D_DRIP -> 1500f
                else -> 1700f
            } * d
            debris.vy[i] += g * dt
            debris.vx[i] *= 1f - min(0.9f, dt * 1.1f)
            debris.vy[i] *= 1f - dt * 0.35f
            debris.x[i] += debris.vx[i] * dt
            debris.y[i] += debris.vy[i] * dt
            debris.rot[i] += debris.om[i] * dt
            debris.age[i] += dt
            var gone = debris.age[i] >= debris.life[i] || debris.y[i] > height + 60f * d
            if (!gone && (kind == D_ICE || kind == D_SHARD) && debris.age[i] >= debris.land[i]) {
                val rx = debris.x[i] + originX
                val ry = debris.y[i] + originY
                val s = WeatherSurfaces.hit(rx, ry, debris.sz[i] * 0.5f)
                if (s != null) {
                    val remaining = max(2f, debris.life[i] - debris.age[i])
                    if (kind == D_ICE) {
                        s.chipLand(rx - s.left, ry - s.top, debris.sz[i], remaining, debris.seed[i])
                    } else {
                        s.shardLand(rx - s.left, ry - s.top, remaining, debris.rot[i], debris.poly, i * 8, debris.seed[i])
                    }
                    gone = true
                } else {
                    debris.land[i] = NEVER
                }
            }
            if (gone) removeDebris(i) else i++
        }
    }

    private fun strikeable(s: CardSurface): Boolean {
        val top = s.top - originY
        return s.active && s.width > 60f * d && top > height * 0.1f && top < height * 0.66f &&
            s.left - originX > -width * 0.2f
    }

    private fun planStrike(lightning: LightningState?) {
        val w = width
        val h = height
        val surfaces = WeatherSurfaces.surfaces
        var count = 0
        var highest = h
        for (k in surfaces.indices) {
            val s = surfaces[k]
            if (strikeable(s)) {
                count++
                highest = min(highest, s.top - originY)
            }
        }
        strikeSurface = null
        var sx = w * (0.15f + 0.7f * random.nextFloat())
        var ex = sx
        var ey = h * 0.4f
        var chosen: CardSurface? = null
        if (count > 0 && random.nextFloat() < 0.5f) {
            var pick = random.nextInt(count)
            for (k in surfaces.indices) {
                val s = surfaces[k]
                if (strikeable(s)) {
                    if (pick == 0) {
                        chosen = s
                        break
                    }
                    pick--
                }
            }
        }
        val card = chosen
        if (card != null) {
            strikeSurface = card
            strikeLx = card.width * (0.2f + 0.6f * random.nextFloat())
            strikeLy = card.height * (0.05f + 0.15f * random.nextFloat()) + 4f * d
            ex = card.left - originX + strikeLx
            ey = card.top - originY + strikeLy
            sx = (ex + (random.nextFloat() - 0.5f) * w * 0.5f).coerceIn(w * 0.05f, w * 0.95f)
        } else {
            val floorY = (if (count > 0) highest else h * 0.6f).coerceAtLeast(h * 0.25f)
            ey = floorY * (0.5f + 0.4f * random.nextFloat())
            ex = (sx + (random.nextFloat() - 0.5f) * w * 0.3f).coerceIn(w * 0.1f, w * 0.9f)
        }
        strikeX0 = sx
        bolt = buildBolt(random, sx, -0.02f * h, ex, ey)
        val locate = lightning?.directionAt
        if (lightning != null && locate != null) {
            val v = locate((sx / w - 0.5f) * (w / h), ((((-0.02f * h) + ey) * 0.5f) / h - 0.4f) * 0.7f)
            lightning.dirX = v[0]
            lightning.dirY = v[1]
            lightning.dirZ = v[2]
        }
    }

    private fun strikeCard(s: CardSurface) {
        if (!s.active) return
        val rr = (min(s.width, s.height * 1.8f) * 0.22f).coerceIn(26f * d, 70f * d)
        val seed = random.nextInt()
        s.beginHole(strikeLx, strikeLy, rr, seed)
        glowX = s.left - originX + strikeLx
        glowY = s.top - originY + strikeLy
        glowRadius = min(s.width, 260f * d) * 0.7f
        glowAge = 0f
        val n = s.holeCount
        for (k in 0 until n) {
            val i = newDebris()
            if (i < 0) break
            val k2 = (k + 1) % n
            val ax = s.holeVx(k)
            val ay = s.holeVy(k)
            val bx = s.holeVx(k2)
            val by = s.holeVy(k2)
            val mx = (ax + bx) * 0.5f * (1.08f + 0.2f * random.nextFloat())
            val my = (ay + by) * 0.5f * (1.08f + 0.2f * random.nextFloat())
            val ix = (random.nextFloat() - 0.5f) * rr * 0.3f
            val iy = (random.nextFloat() - 0.5f) * rr * 0.3f
            val cx = (ix + ax + mx + bx) * 0.25f
            val cy = (iy + ay + my + by) * 0.25f
            val o = i * 8
            debris.poly[o] = ix - cx
            debris.poly[o + 1] = iy - cy
            debris.poly[o + 2] = ax - cx
            debris.poly[o + 3] = ay - cy
            debris.poly[o + 4] = mx - cx
            debris.poly[o + 5] = my - cy
            debris.poly[o + 6] = bx - cx
            debris.poly[o + 7] = by - cy
            val ang = atan2(cy, cx) + (random.nextFloat() - 0.5f) * 0.9f
            val sp = (150f + 420f * random.nextFloat()) * d
            debris.kind[i] = D_SHARD
            debris.x[i] = glowX + cx
            debris.y[i] = glowY + cy
            debris.vx[i] = cos(ang) * sp
            debris.vy[i] = sin(ang) * sp - 90f * d
            debris.rot[i] = random.nextFloat() * TWO_PI
            debris.om[i] = (random.nextFloat() - 0.5f) * 2f * (2f + 7f * random.nextFloat())
            debris.sz[i] = 6f * d
            debris.age[i] = 0f
            debris.life[i] = 15f
            debris.land[i] = 0.45f + 0.8f * random.nextFloat()
            debris.seed[i] = random.nextInt()
            debris.col[i] = s.color
        }
    }

    private suspend fun playStrike(lightning: LightningState?) {
        val reach = boltReach(bolt)
        val speed = max(height, 800f) * 10f
        val tReach = reach / speed
        val strength = 0.7f + random.nextFloat() * 0.3f
        boltActive = true
        boltReveal = 0f
        boltFade = 0.3f
        var struck = false
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val sec = (now - start) / 1_000_000_000f
            boltReveal = sec * speed
            val e = if (sec < tReach) 0.3f else lightningEnvelope(sec - tReach)
            boltFade = e
            if (lightning != null) lightning.intensity = (if (sec < tReach) 0.1f else e) * strength
            if (!struck && sec >= tReach) {
                struck = true
                val s = strikeSurface
                if (s != null) strikeCard(s)
            }
            WeatherSurfaces.frame.longValue = now
            if (sec > tReach + 0.65f) break
        }
    }

    private fun endStrike(lightning: LightningState?) {
        boltActive = false
        boltFade = 0f
        bolt = EmptyBolt
        strikeSurface = null
        if (lightning != null) lightning.intensity = 0f
    }

    suspend fun lightningLoop(thunder: Float, lightning: LightningState?) {
        val period = thunderPeriod(thunder)
        while (true) {
            delay(random.nextLong(period.first, period.last))
            if (!syncOrigin()) continue
            val surfaces = WeatherSurfaces.surfaces
            for (k in surfaces.indices) surfaces[k].refresh()
            planStrike(lightning)
            try {
                playStrike(lightning)
            } finally {
                endStrike(lightning)
            }
        }
    }

    fun draw(scope: DrawScope, mix: PrecipMix, skyHandles: Boolean) {
        with(scope) {
            if (mist > 0.002f) drawRect(MistColor.copy(alpha = mist))
            if (!skyHandles && boltActive && boltFade > 0.02f) {
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(FlashColor.copy(alpha = 0.16f * boltFade), Color.Transparent),
                        center = Offset(strikeX0, 0f),
                        radius = width * 0.9f
                    )
                )
            }
            if (mix.active) {
                drawRain()
                drawSnow()
                drawHail()
            }
            drawDebris()
            if (glowAge < 0.4f) {
                val f = 1f - glowAge / 0.4f
                val center = Offset(glowX, glowY)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(GlowColor.copy(alpha = f * f * 0.5f), Color.Transparent),
                        center = center,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = center
                )
            }
            if (boltActive) drawBolt(bolt, boltReveal, boltFade, d)
        }
    }

    private fun DrawScope.drawRain() {
        val a = rain
        for (i in 0 until a.n) {
            val wr = windNow * (0.85f + 0.3f * a.q[i])
            val norm = sqrt(1f + wr * wr)
            val ux = wr / norm
            val uy = 1f / norm
            val flag = a.fl[i]
            val px = a.x[i]
            val py = a.y[i]
            val head = Offset(px, py)
            if ((flag and F_FAR) != 0) {
                val len = a.v[i] * 0.008f + 2f * d
                drawLine(RainColor.copy(alpha = 0.11f), Offset(px - ux * len, py - uy * len), head, 0.6f * d, StrokeCap.Round)
            } else {
                val c = if ((flag and F_FROZEN) != 0) FrostRainColor else RainColor
                val r = a.s[i]
                val len = a.v[i] * 0.011f + 2.5f * d
                val w = r * 0.9f
                drawLine(c.copy(alpha = 0.10f), Offset(px - ux * len, py - uy * len), head, w * 1.7f, StrokeCap.Round)
                drawLine(c.copy(alpha = 0.3f), Offset(px - ux * len * 0.45f, py - uy * len * 0.45f), head, w, StrokeCap.Round)
                if (r > 1.1f * d) {
                    drawOval(Color.White.copy(alpha = 0.16f), Offset(px - r * 0.6f, py - r * 1.1f), Size(r * 1.2f, r * 1.9f))
                }
                drawCircle(Color.White.copy(alpha = 0.5f), w * 0.45f, Offset(px - r * 0.2f, py - r * 0.3f))
            }
        }
    }

    private fun DrawScope.drawSnow() {
        val a = snow
        for (i in 0 until a.n) {
            val r = a.s[i]
            val c = Offset(a.x[i], a.y[i])
            val flag = a.fl[i]
            val q = a.q[i]
            when {
                (flag and F_BLUR) != 0 -> {
                    drawCircle(SnowColor.copy(alpha = 0.10f), r * 1.5f, c)
                    drawCircle(SnowColor.copy(alpha = 0.2f), r, c)
                }
                (flag and F_FAR) != 0 -> drawCircle(SnowColor.copy(alpha = 0.28f + 0.2f * q), r, c)
                else -> {
                    drawCircle(SnowColor.copy(alpha = 0.16f), r * 1.8f, c)
                    drawCircle(SnowColor.copy(alpha = 0.5f + 0.42f * q), r, c)
                }
            }
        }
    }

    private fun DrawScope.drawHail() {
        val a = hail
        for (i in 0 until a.n) {
            val s = a.s[i]
            val x = a.x[i]
            val y = a.y[i]
            val rot = a.q[i] * TWO_PI + a.t[i] * (2.5f + 5f * a.q[i])
            val len = a.v[i] * 0.008f
            val norm = sqrt(1f + windNow * windNow * 0.49f)
            val ux = windNow * 0.7f / norm
            val uy = 1f / norm
            drawLine(HailBody.copy(alpha = 0.22f), Offset(x - ux * len, y - uy * len), Offset(x, y), s * 1.1f, StrokeCap.Round)
            polyPath(path, x, y, s, rot, a.sd[i], 6)
            drawPath(path, HailBody.copy(alpha = 0.88f))
            drawPath(path, HailEdge.copy(alpha = 0.55f), style = strokeFor(max(0.5f, s * 0.14f)))
            polyPath(path, x + s * 0.05f, y + s * 0.05f, s * 0.55f, rot + 0.6f, a.sd[i] + 5, 5)
            drawPath(path, HailShade.copy(alpha = 0.3f))
            drawCircle(Color.White.copy(alpha = 0.8f), s * 0.22f, Offset(x - s * 0.28f, y - s * 0.3f))
        }
    }

    private fun DrawScope.drawDebris() {
        for (i in 0 until debris.n) {
            val x = debris.x[i]
            val y = debris.y[i]
            val age = debris.age[i]
            val life = debris.life[i]
            val r = debris.sz[i]
            val fade = ((life - age) / 1.2f).coerceIn(0f, 1f)
            when (debris.kind[i]) {
                D_SPLASH -> drawCircle(SplashColor.copy(alpha = 0.55f * (1f - age / life)), r, Offset(x, y))
                D_DRIP -> {
                    val f = 1f - age / life
                    drawOval(RainColor.copy(alpha = 0.45f * f), Offset(x - r * 0.7f, y - r * 1.5f), Size(r * 1.4f, r * 3f))
                    drawCircle(Color.White.copy(alpha = 0.6f * f), r * 0.25f, Offset(x - r * 0.2f, y - r * 0.6f))
                }
                D_ICE -> {
                    polyPath(path, x, y, r, debris.rot[i], debris.seed[i], 5)
                    drawPath(path, HailBody.copy(alpha = 0.9f * fade))
                    drawPath(path, HailEdge.copy(alpha = 0.5f * fade), style = strokeFor(max(0.5f, r * 0.14f)))
                }
                D_SHARD -> {
                    val c = cos(debris.rot[i])
                    val sn = sin(debris.rot[i])
                    path.reset()
                    for (k in 0 until 4) {
                        val px = debris.poly[i * 8 + 2 * k]
                        val py = debris.poly[i * 8 + 2 * k + 1]
                        val rx = x + px * c - py * sn
                        val ry = y + px * sn + py * c
                        if (k == 0) path.moveTo(rx, ry) else path.lineTo(rx, ry)
                    }
                    path.close()
                    drawPath(path, Color(debris.col[i]).copy(alpha = 0.94f * fade))
                    drawPath(path, Color.White.copy(alpha = 0.07f * fade))
                    drawPath(path, Color.White.copy(alpha = 0.45f * fade), style = strokeFor(1.1f * d))
                }
            }
        }
    }
}

@Composable
fun ParticleOverlay(
    mix: PrecipMix,
    windSpeedKmh: Double = 0.0,
    windDirDeg: Double = 0.0,
    alpha: Float = 1f,
    lightning: LightningState? = null,
    skyLightning: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val powerSave = rememberPowerSaveMode()
    val scale = remember(powerSave) { tierScale(detectSkyTier(context, powerSave)) }
    val sim = remember(density) { WeatherSim(density) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentMix by rememberUpdatedState(mix)
    val windRatio = (-sin(Math.toRadians(windDirDeg)) * (windSpeedKmh / 45.0).coerceIn(0.0, 1.0) * 0.55).toFloat()
    val currentWind by rememberUpdatedState(windRatio)

    DisposableEffect(sim) {
        onDispose { WeatherSurfaces.clearItems() }
    }

    LaunchedEffect(mix.active) {
        if (!mix.active) sim.clearAir()
    }

    LaunchedEffect(sim, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = withFrameNanos { it }
            while (true) {
                if (!sim.busy(currentMix.active)) {
                    delay(250)
                    sim.idleTick(0.25f)
                    last = withFrameNanos { it }
                    continue
                }
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                sim.step(dt, currentMix, currentWind, scale)
                WeatherSurfaces.frame.longValue = now
            }
        }
    }

    LaunchedEffect(sim, lifecycleOwner, mix.thunder, lightning) {
        if (mix.thunder <= 0f) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            sim.lightningLoop(mix.thunder, lightning)
        }
    }

    val layered = if (alpha < 0.99f) modifier.graphicsLayer { this.alpha = alpha } else modifier
    Canvas(layered.fillMaxSize().onGloballyPositioned { sim.coords = it }) {
        if (WeatherSurfaces.frame.longValue >= 0L) sim.draw(this, currentMix, skyLightning)
    }
}
