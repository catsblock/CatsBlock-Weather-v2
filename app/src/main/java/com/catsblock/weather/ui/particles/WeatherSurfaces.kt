package com.catsblock.weather.ui.particles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal const val KIND_DEAD = -1
internal const val KIND_BEAD = 0
internal const val KIND_SLIDER = 1
internal const val KIND_SNOW = 2
internal const val KIND_CHIP = 3
internal const val KIND_FROZEN = 4

private const val CAP = 112
private const val TWO_PI = 6.2831855f
private const val NEVER_END = 3.0e38f

private val WaterRim = Color(0xFFD7E9FF)
private val IceBody = Color(0xFFEAF4FA)
private val IceEdge = Color(0xFFA9BCCB)
private val SnowBody = Color(0xFFF6F9FF)
private val GlazeColor = Color(0xFFD9EEFF)
private val DarkRefraction = Color(0xFF07131F)

private val strokes = Array(12) { Stroke(0.5f * (it + 1)) }
private val shapePath = Path()

internal fun strokeFor(width: Float): Stroke = strokes[((width / 0.5f).toInt() - 1).coerceIn(0, 11)]

internal fun hash01(seed: Int, k: Int): Float {
    var h = seed * -1640531535 + k * 668265263
    h = h xor (h ushr 13)
    h *= 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777216f
}

internal fun polyPath(path: Path, cx: Float, cy: Float, r: Float, rot: Float, seed: Int, n: Int) {
    path.reset()
    for (k in 0 until n) {
        val a = rot + (k + (hash01(seed, k) - 0.5f) * 0.6f) * (TWO_PI / n)
        val rr = r * (0.62f + 0.38f * hash01(seed, k + 17))
        val px = cx + cos(a) * rr
        val py = cy + sin(a) * rr
        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
}

internal class CardSurface {
    var coords: LayoutCoordinates? = null
    var left = 0f
    var top = 0f
    var width = 0f
    var height = 0f
    var radius = 0f
    var active = false
    var glaze = 0f
    var count = 0
    val ik = IntArray(CAP)
    val ix = FloatArray(CAP)
    val iy = FloatArray(CAP)
    val ir = FloatArray(CAP)
    val ia = FloatArray(CAP)
    val il = FloatArray(CAP)
    val iv = FloatArray(CAP)
    val iu = FloatArray(CAP)
    val iseed = IntArray(CAP)

    fun refresh() {
        val c = coords
        if (c == null || !c.isAttached) {
            active = false
            return
        }
        val p = c.positionInRoot()
        left = p.x
        top = p.y
        width = c.size.width.toFloat()
        height = c.size.height.toFloat()
        active = width > 0f && height > 0f
    }

    fun insideLocal(lx: Float, ly: Float, margin: Float): Boolean {
        if (lx < margin || ly < margin || lx > width - margin || ly > height - margin) return false
        val rr = min(radius, min(width, height) * 0.5f)
        val cx = if (lx < rr) rr else if (lx > width - rr) width - rr else lx
        val cy = if (ly < rr) rr else if (ly > height - rr) height - rr else ly
        val dx = lx - cx
        val dy = ly - cy
        val lim = max(rr - margin, 0f)
        return dx * dx + dy * dy <= lim * lim
    }

    fun containsRoot(px: Float, py: Float, margin: Float): Boolean = insideLocal(px - left, py - top, margin)

    fun clear() {
        count = 0
        glaze = 0f
    }

    fun add(kind: Int, x: Float, y: Float, r: Float, life: Float, seed: Int, aux: Float): Int {
        var i = count
        if (i >= CAP) {
            i = oldestStatic()
            if (i < 0) return -1
        } else {
            count++
        }
        ik[i] = kind
        ix[i] = x
        iy[i] = y
        ir[i] = r
        ia[i] = 0f
        il[i] = life
        iv[i] = 0f
        iu[i] = aux
        iseed[i] = seed
        return i
    }

    private fun oldestStatic(): Int {
        var best = -1
        var bestAge = -1f
        for (j in 0 until count) {
            if (ik[j] != KIND_SLIDER && ia[j] > bestAge) {
                bestAge = ia[j]
                best = j
            }
        }
        return best
    }

    private fun remove(i: Int) {
        val last = count - 1
        if (i != last) {
            ik[i] = ik[last]
            ix[i] = ix[last]
            iy[i] = iy[last]
            ir[i] = ir[last]
            ia[i] = ia[last]
            il[i] = il[last]
            iv[i] = iv[last]
            iu[i] = iu[last]
            iseed[i] = iseed[last]
        }
        count = last
    }

    fun addGlaze(amount: Float, limit: Float) {
        glaze = min(limit, glaze + amount)
    }

    fun rainImpact(lx: Float, ly: Float, rd: Float, d: Float, random: Random, frozen: Boolean) {
        var j = 0
        while (j < count) {
            val kj = ik[j]
            val liquid = if (frozen) kj == KIND_FROZEN else (kj == KIND_BEAD || kj == KIND_SLIDER)
            if (liquid) {
                val dx = ix[j] - lx
                val dy = iy[j] - ly
                val rr = ir[j] + rd
                if (dx * dx + dy * dy < rr * rr * 0.55f) {
                    ir[j] = sqrt(ir[j] * ir[j] + rd * rd * 0.85f)
                    if (frozen) {
                        if (ir[j] > 5.2f * d) ir[j] = 5.2f * d
                    } else if (kj == KIND_BEAD && ir[j] > iu[j]) {
                        ik[j] = KIND_SLIDER
                        iv[j] = 5f * d
                        il[j] = 0f
                    }
                    return
                }
            }
            j++
        }
        if (rd < 0.8f * d && random.nextFloat() < 0.35f) return
        val seed = random.nextInt()
        if (frozen) {
            add(KIND_FROZEN, lx, ly, rd * 1.2f, NEVER_END, seed, 0f)
        } else {
            val critical = (2.6f + 2.2f * random.nextFloat()) * d
            val r = rd * 1.15f
            val i = add(KIND_BEAD, lx, ly, r, 12f + random.nextFloat() * 18f, seed, critical)
            if (i >= 0 && r > critical) {
                ik[i] = KIND_SLIDER
                iv[i] = 4f * d
                il[i] = 0f
            }
        }
    }

    fun snowLand(lx: Float, ly: Float, r: Float, d: Float, random: Random) {
        var j = 0
        while (j < count) {
            if (ik[j] == KIND_SNOW) {
                val dx = ix[j] - lx
                val dy = iy[j] - ly
                val rr = ir[j] + r
                if (dx * dx + dy * dy < rr * rr * 0.8f) {
                    if (ir[j] < 3.2f * d) ir[j] = sqrt(ir[j] * ir[j] + r * r * 0.7f)
                    return
                }
            }
            j++
        }
        add(KIND_SNOW, lx, ly, r, 15f, random.nextInt(), 0f)
    }

    fun chipLand(lx: Float, ly: Float, r: Float, life: Float, seed: Int) {
        add(KIND_CHIP, lx, ly, r, life, seed, 0f)
    }

    fun step(dt: Float, d: Float, freezeActive: Boolean, warm: Boolean, windX: Float, random: Random) {
        var i = 0
        while (i < count) {
            val k = ik[i]
            if (k == KIND_DEAD) {
                remove(i)
                continue
            }
            ia[i] += dt
            var gone = false
            when (k) {
                KIND_SLIDER -> gone = stepSlider(i, dt, d, windX, random)
                KIND_BEAD -> if (ia[i] > il[i]) {
                    ir[i] -= ir[i] * dt * 0.7f
                    if (ir[i] < 0.28f * d) gone = true
                }
                KIND_FROZEN -> {
                    if (!freezeActive && il[i] > ia[i] + 40f) {
                        il[i] = ia[i] + 5f + random.nextFloat() * (if (warm) 8f else 30f)
                    }
                    if (ia[i] > il[i]) {
                        ir[i] -= ir[i] * dt * 0.5f
                        if (ir[i] < 0.3f * d) gone = true
                    }
                }
                KIND_SNOW, KIND_CHIP -> if (ia[i] >= il[i]) gone = true
            }
            if (gone) remove(i) else i++
        }
        if (!freezeActive && glaze > 0f) glaze = max(0f, glaze - dt * (if (warm) 0.02f else 0.004f))
    }

    private fun stepSlider(i: Int, dt: Float, d: Float, windX: Float, random: Random): Boolean {
        val seed = iseed[i]
        val phase = ia[i]
        val critical = iu[i]
        val sa = 0.5f + 0.5f * sin(phase * 3.1f + seed * 0.37f)
        val sb = 0.5f + 0.5f * sin(phase * 1.27f + seed * 0.91f)
        val stick = (sa * sb).coerceIn(0f, 1f)
        val heft = (ir[i] / critical - 0.7f).coerceIn(0f, 1.8f)
        val target = (10f + 44f * heft) * d * (0.12f + 1.1f * stick)
        iv[i] += (target - iv[i]) * min(1f, dt * 3.2f)
        val v = iv[i]
        iy[i] += v * dt
        ix[i] += (sin(phase * 2.1f + seed) * 2.2f * d + windX * v * 0.35f) * dt
        il[i] += v * dt
        if (il[i] > (9f + 14f * hash01(seed, (phase * 3f).toInt())) * d) {
            il[i] = 0f
            val rt = (0.4f + 0.55f * random.nextFloat()) * d
            val rest = ir[i] * ir[i] - rt * rt * 0.7f
            if (rest > 0f) ir[i] = sqrt(rest)
            add(
                KIND_BEAD,
                ix[i] + (random.nextFloat() - 0.5f) * ir[i] * 0.5f,
                iy[i] - ir[i] * 1.4f,
                rt,
                10f + random.nextFloat() * 14f,
                random.nextInt(),
                (2.6f + 2.2f * random.nextFloat()) * d
            )
        }
        var j = 0
        while (j < count) {
            if (j != i) {
                val kj = ik[j]
                if (kj == KIND_BEAD || kj == KIND_SLIDER) {
                    val dx = ix[j] - ix[i]
                    val dy = iy[j] - iy[i]
                    val rr = ir[i] + ir[j]
                    if (dx * dx + dy * dy < rr * rr * 0.5f) {
                        if (kj == KIND_SLIDER && ir[j] > ir[i]) {
                            ir[j] = sqrt(ir[j] * ir[j] + ir[i] * ir[i])
                            iv[j] += 3f * d
                            return true
                        }
                        ir[i] = sqrt(ir[i] * ir[i] + ir[j] * ir[j] * 0.9f)
                        iv[i] += 3f * d
                        ik[j] = KIND_DEAD
                    }
                }
            }
            j++
        }
        if (ir[i] < critical * 0.72f && iv[i] < 6f * d) {
            ik[i] = KIND_BEAD
            il[i] = ia[i] + 6f + random.nextFloat() * 12f
            return false
        }
        if (!insideLocal(ix[i], iy[i], ir[i] * 0.5f)) return true
        return false
    }
}

internal object WeatherSurfaces {
    val surfaces = ArrayList<CardSurface>(32)
    val frame = mutableLongStateOf(0L)
    val effectAlpha = mutableFloatStateOf(1f)
    var renderer by mutableStateOf<WeatherSim?>(null)
    var mix by mutableStateOf(PrecipMix.None)

    fun obtain(): CardSurface {
        val surface = CardSurface()
        surfaces.add(surface)
        return surface
    }

    fun release(surface: CardSurface) {
        surfaces.remove(surface)
        surface.clear()
        surface.coords = null
        surface.active = false
    }

    fun clearItems() {
        for (surface in surfaces) surface.clear()
    }

    fun hit(x: Float, y: Float, margin: Float): CardSurface? {
        var index = surfaces.lastIndex
        while (index >= 0) {
            val surface = surfaces[index]
            if (surface.active && surface.containsRoot(x, y, margin)) return surface
            index--
        }
        return null
    }
}

private fun DrawScope.drawWater(x: Float, y: Float, r: Float, stretch: Float, fade: Float, ice: Float, d: Float) {
    val hh = r * (0.94f + stretch)
    val ww = r * (1f - 0.14f * min(stretch, 1f))
    val cy = y - r * stretch
    val tl = Offset(x - ww, cy - hh)
    val sz = Size(ww * 2f, hh * 2f)
    drawOval(Color.White.copy(alpha = (0.055f + 0.2f * ice) * fade), tl, sz)
    if (r > 1.1f * d) {
        drawOval(
            DarkRefraction.copy(alpha = 0.13f * fade),
            Offset(x - ww * 0.72f, cy - hh * 0.92f),
            Size(ww * 1.44f, hh * 0.62f)
        )
        drawOval(
            Color.White.copy(alpha = (0.15f + 0.14f * ice) * fade),
            Offset(x - ww * 0.62f, cy + hh * 0.1f),
            Size(ww * 1.24f, hh * 0.74f)
        )
    }
    drawOval(WaterRim.copy(alpha = (0.34f + 0.3f * ice) * fade), tl, sz, style = strokeFor(max(0.5f, r * 0.12f)))
    drawOval(
        Color.White.copy(alpha = 0.82f * fade),
        Offset(x - ww * 0.54f, cy - hh * 0.62f),
        Size(ww * 0.4f, hh * 0.24f)
    )
    if (stretch > 0.25f) {
        drawLine(
            WaterRim.copy(alpha = 0.14f * fade),
            Offset(x, cy - hh),
            Offset(x, cy - hh - r * (1.2f + stretch * 2.4f)),
            max(0.6f, r * 0.38f),
            StrokeCap.Round
        )
    }
}

internal fun DrawScope.drawSurface(s: CardSurface, d: Float) {
    if (s.count == 0 && s.glaze < 0.01f) return
    if (s.glaze >= 0.01f) {
        val g = s.glaze
        drawRoundRect(GlazeColor.copy(alpha = g * 0.16f), Offset.Zero, Size(s.width, s.height), CornerRadius(s.radius))
        drawRoundRect(
            Color.White.copy(alpha = g * 0.32f),
            Offset(d, d),
            Size(s.width - 2f * d, s.height - 2f * d),
            CornerRadius(s.radius),
            strokeFor(1.2f * d)
        )
    }
    for (i in 0 until s.count) {
        val x = s.ix[i]
        val y = s.iy[i]
        val r = s.ir[i]
        val seed = s.iseed[i]
        when (s.ik[i]) {
            KIND_BEAD -> {
                val fade = if (s.ia[i] > s.il[i]) (r / (1.2f * d)).coerceIn(0f, 1f) else 1f
                drawWater(x, y, r, 0f, fade, 0f, d)
            }
            KIND_SLIDER -> {
                val stretch = (s.iv[i] / (40f * d)).coerceIn(0f, 1f) * 1.1f
                drawWater(x, y, r, stretch, 1f, 0f, d)
            }
            KIND_FROZEN -> {
                val p = ((s.ia[i] - 1.4f) / 6f).coerceIn(0f, 1f)
                val fade = if (s.ia[i] > s.il[i]) (r / (1.2f * d)).coerceIn(0f, 1f) else 1f
                drawWater(x, y, r, 0f, fade, p, d)
            }
            KIND_SNOW -> {
                val f = ((s.il[i] - s.ia[i]) / 1.2f).coerceIn(0f, 1f)
                val rr = r * (0.65f + 0.35f * f)
                val c = Offset(x, y)
                drawCircle(Color.Black.copy(alpha = 0.10f * f), rr * 0.95f, Offset(x + 0.3f * d, y + 0.45f * d))
                drawCircle(SnowBody.copy(alpha = 0.2f * f), rr * 1.7f, c)
                drawCircle(SnowBody.copy(alpha = 0.86f * f), rr, c)
                if (rr > 1.3f * d) {
                    drawCircle(
                        SnowBody.copy(alpha = 0.8f * f),
                        rr * 0.68f,
                        Offset(x + (hash01(seed, 1) - 0.5f) * rr * 1.1f, y + (hash01(seed, 2) - 0.5f) * rr * 0.7f)
                    )
                }
                drawCircle(Color.White.copy(alpha = 0.9f * f), rr * 0.28f, Offset(x - rr * 0.3f, y - rr * 0.32f))
            }
            KIND_CHIP -> {
                val f = ((s.il[i] - s.ia[i]) / 1.2f).coerceIn(0f, 1f)
                val rot = hash01(seed, 99) * TWO_PI
                polyPath(shapePath, x + 0.5f * d, y + 0.9f * d, r, rot, seed, 6)
                drawPath(shapePath, Color.Black.copy(alpha = 0.14f * f))
                polyPath(shapePath, x, y, r, rot, seed, 6)
                drawPath(shapePath, IceBody.copy(alpha = 0.9f * f))
                drawPath(shapePath, IceEdge.copy(alpha = 0.6f * f), style = strokeFor(max(0.5f, r * 0.12f)))
                drawCircle(Color.White.copy(alpha = 0.8f * f), r * 0.2f, Offset(x - r * 0.25f, y - r * 0.28f))
            }
        }
    }
}

@Composable
fun WeatherSurfaceHost(
    modifier: Modifier,
    cornerRadius: Dp,
    content: @Composable (Modifier) -> Unit
) {
    val density = LocalDensity.current
    val particleScale = density.density
    val surface = remember { WeatherSurfaces.obtain() }
    surface.radius = with(density) { cornerRadius.toPx() }
    DisposableEffect(surface) {
        onDispose { WeatherSurfaces.release(surface) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .onGloballyPositioned { surface.coords = it },
        propagateMinConstraints = true
    ) {
        content(Modifier)
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = WeatherSurfaces.effectAlpha.floatValue }
                .drawBehind {
                    val currentFrame = WeatherSurfaces.frame.longValue
                    if (currentFrame >= 0L) {
                        drawSurface(surface, particleScale)
                        WeatherSurfaces.renderer?.drawCard(this, surface, WeatherSurfaces.mix)
                    }
                }
        )
    }
}
