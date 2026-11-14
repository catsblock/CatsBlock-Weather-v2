package com.catsblock.weather.ui.particles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.drawscope.translate
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
import com.catsblock.weather.ui.sky.SkyTier
import com.catsblock.weather.ui.sky.boltReach
import com.catsblock.weather.ui.sky.buildBolt
import com.catsblock.weather.ui.sky.drawBolt
import com.catsblock.weather.ui.sky.lightningEnvelope
import kotlinx.coroutines.delay
import kotlin.math.abs
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

private val SnowColor = Color(0xFFF8FBFF)
private val HailBody = Color(0xFFE9F1F6)
private val HailShade = Color(0xFFB9C9D6)
private val HailEdge = Color(0xFFAFC0CC)
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

internal class WeatherSim(private val d: Float) {
    private val random = Random(System.nanoTime())
    private val rain = Air(260)
    private val snow = Air(520)
    private val hail = Air(70)
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
    private var strikeY0 = 0f
    private var glowX = 0f
    private var glowY = 0f
    private var glowAge = 9f
    private var glowRadius = 0f
    private var glowSurface: CardSurface? = null
    private var mist = 0f
    var coords: LayoutCoordinates? = null

    fun clearAir() {
        rain.n = 0
        snow.n = 0
        hail.n = 0
        mist = 0f
    }

    fun busy(active: Boolean): Boolean {
        if (active || boltActive || glowAge < 0.45f) return true
        val surfaces = WeatherSurfaces.surfaces
        for (k in surfaces.indices) {
            val s = surfaces[k]
            if (s.count > 0) return true
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
        for (k in surfaces.indices) surfaces[k].step(dt, d, freezeActive, lastWarm, windNow, random)
        glowAge += dt
        if (glowAge >= 0.4f) glowSurface = null
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
            spawnRain(i, false)
            return
        }
        val frozen = (a.fl[i] and F_FROZEN) != 0
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
            spawnSnow(i, false)
            return
        }
        if (random.nextFloat() < 0.85f) s.snowLand(rx - s.left, ry - s.top, a.s[i] * 0.9f, d, random)
        spawnSnow(i, false)
    }

    private fun impactHail(i: Int) {
        val a = hail
        val rx = a.x[i] + originX
        val ry = a.y[i] + originY
        val surface = WeatherSurfaces.hit(rx, ry, a.s[i])
        if (surface != null) {
            surface.chipLand(rx - surface.left, ry - surface.top, a.s[i] * 0.7f, 15f, a.sd[i])
        }
        spawnHail(i, false)
    }

    private fun strikeable(surface: CardSurface): Boolean {
        val left = surface.left - originX
        val top = surface.top - originY
        return surface.active && surface.width > 60f * d &&
            left + surface.width > 0f && left < width &&
            top + surface.height > 0f && top < height
    }

    private fun planStrike(): Boolean {
        val candidates = WeatherSurfaces.surfaces.filter(::strikeable)
        if (candidates.isEmpty()) {
            strikeSurface = null
            return false
        }
        val surface = candidates[random.nextInt(candidates.size)]
        strikeSurface = surface
        strikeLx = surface.width * (0.2f + 0.6f * random.nextFloat())
        strikeLy = surface.height * (0.12f + 0.3f * random.nextFloat())
        strikeX0 = surface.width * (0.2f + 0.6f * random.nextFloat())
        strikeY0 = -0.02f * surface.height
        bolt = buildBolt(random, strikeX0, strikeY0, strikeLx, strikeLy)
        return true
    }

    private fun strikeCard(surface: CardSurface) {
        if (!surface.active) return
        glowSurface = surface
        glowX = strikeLx
        glowY = strikeLy
        glowRadius = min(surface.width, 260f * d) * 0.7f
        glowAge = 0f
    }

    private suspend fun playStrike() {
        val reach = boltReach(bolt)
        val surface = strikeSurface ?: return
        val speed = max(min(surface.width, surface.height), 200f) * 10f
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
            boltFade = (if (sec < tReach) 0.3f else lightningEnvelope(sec - tReach)) * strength
            if (!struck && sec >= tReach) {
                struck = true
                strikeCard(surface)
            }
            WeatherSurfaces.frame.longValue = now
            if (sec > tReach + 0.65f) break
        }
    }

    private fun endStrike() {
        boltActive = false
        boltFade = 0f
        bolt = EmptyBolt
        strikeSurface = null
    }

    suspend fun lightningLoop(thunder: Float) {
        val period = thunderPeriod(thunder)
        while (true) {
            delay(random.nextLong(period.first, period.last))
            if (!syncOrigin()) continue
            for (surface in WeatherSurfaces.surfaces) surface.refresh()
            if (!planStrike()) continue
            try {
                playStrike()
            } finally {
                endStrike()
            }
        }
    }

    fun drawCard(scope: DrawScope, surface: CardSurface, mix: PrecipMix) {
        if (!surface.active) return
        with(scope) {
            if (mist > 0.002f && mix.rain + mix.freezing > 0.01f) {
                drawRect(MistColor.copy(alpha = mist), size = Size(size.width, size.height))
            }
            translate(
                left = originX - surface.left,
                top = originY - surface.top
            ) {
                if (mix.active) {
                    drawSnow(surface)
                    drawHail(surface)
                }
            }
            if (boltActive && strikeSurface === surface && boltFade > 0.02f) {
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(FlashColor.copy(alpha = 0.16f * boltFade), Color.Transparent),
                        center = Offset(strikeX0, strikeY0),
                        radius = max(surface.width, surface.height) * 0.9f
                    )
                )
                drawBolt(bolt, boltReveal, boltFade, d)
            }
            if (glowSurface === surface && glowAge < 0.4f) {
                val fade = 1f - glowAge / 0.4f
                val center = Offset(glowX, glowY)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(GlowColor.copy(alpha = fade * fade * 0.5f), Color.Transparent),
                        center = center,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = center
                )
            }
        }
    }

    private fun DrawScope.drawSnow(surface: CardSurface) {
        val a = snow
        for (i in 0 until a.n) {
            val r = a.s[i]
            val x = a.x[i]
            val y = a.y[i]
            if (!intersects(surface, x, y, r * 1.8f)) continue
            val c = Offset(x, y)
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

    private fun DrawScope.drawHail(surface: CardSurface) {
        val a = hail
        for (i in 0 until a.n) {
            val s = a.s[i]
            val x = a.x[i]
            val y = a.y[i]
            if (!intersects(surface, x, y, s * 1.5f)) continue
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

    private fun intersects(surface: CardSurface, x: Float, y: Float, radius: Float): Boolean {
        val localX = x + originX - surface.left
        val localY = y + originY - surface.top
        return localX + radius >= 0f && localY + radius >= 0f &&
            localX - radius <= surface.width && localY - radius <= surface.height
    }

}

@Composable
fun ParticleOverlay(
    mix: PrecipMix,
    windSpeedKmh: Double = 0.0,
    windDirDeg: Double = 0.0,
    alpha: Float = 1f,
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

    SideEffect {
        WeatherSurfaces.renderer = sim
        WeatherSurfaces.mix = mix
        WeatherSurfaces.effectAlpha.floatValue = alpha.coerceIn(0f, 1f)
    }

    DisposableEffect(sim) {
        onDispose {
            WeatherSurfaces.clearItems()
            if (WeatherSurfaces.renderer === sim) WeatherSurfaces.renderer = null
            WeatherSurfaces.mix = PrecipMix.None
        }
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

    LaunchedEffect(sim, lifecycleOwner, mix.thunder) {
        if (mix.thunder <= 0f) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            sim.lightningLoop(mix.thunder)
        }
    }

    Box(modifier.fillMaxSize().onGloballyPositioned { sim.coords = it })
}
