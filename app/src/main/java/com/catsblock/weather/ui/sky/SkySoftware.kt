package com.catsblock.weather.ui.sky

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal object SkyNoise {
    fun hash(x: Int, y: Int): Float {
        var h = x * 0x27d4eb2d xor y * 0x165667b1
        h = (h xor (h ushr 15)) * 0x2c1b3c6d
        h = (h xor (h ushr 12)) * 0x297a2d39
        h = h xor (h ushr 15)
        return (h and 0x7fffffff) / 2147483647.0f
    }

    private fun wrap(value: Int): Int = ((value % 256) + 256) % 256

    fun value(x: Float, y: Float): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val fx = x - xi
        val fy = y - yi
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        val x0 = wrap(xi)
        val x1 = wrap(xi + 1)
        val y0 = wrap(yi)
        val y1 = wrap(yi + 1)
        val a = hash(x0, y0)
        val b = hash(x1, y0)
        val c = hash(x0, y1)
        val d = hash(x1, y1)
        val top = a + (b - a) * ux
        val bottom = c + (d - c) * ux
        return top + (bottom - top) * uy
    }

    fun fbm(x: Float, y: Float, octaves: Float): Float {
        var px = x
        var py = y
        var sum = 0f
        var amplitude = 0.5f
        var norm = 0f
        val limit = octaves.coerceIn(1f, 6f)
        var i = 0
        while (i < 6 && i < limit) {
            sum += amplitude * value(px, py)
            norm += amplitude
            px = px * 2f + 17.3f
            py = py * 2f + 9.1f
            amplitude *= 0.5f
            i++
        }
        return sum / max(norm, 0.0001f)
    }
}

private const val PI_F = 3.14159265f
private const val RE = 6371000f
private const val RAT = 6471000f
private const val HR = 7994f
private const val HM = 1200f
private const val HO = 15000f
private const val LUMA_R = 0.2126f
private const val LUMA_G = 0.7152f
private const val LUMA_B = 0.0722f
private val BR = floatArrayOf(5.8e-6f, 13.5e-6f, 33.1e-6f)
private val BO = floatArrayOf(0.65e-6f, 1.881e-6f, 0.085e-6f)
private const val FOCAL = 0.5361f

internal fun skyLuma(r: Float, g: Float, b: Float): Float = r * LUMA_R + g * LUMA_G + b * LUMA_B

private fun airmass(cz: Float): Float {
    val c = cz.coerceIn(0f, 1f)
    val z = kotlin.math.acos(c) * 57.29578f
    return 1f / (c + 0.50572f * max(96.07995f - z, 0.01f).pow(-1.6364f))
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun aces(value: Float): Float {
    val c = value.coerceAtLeast(0f)
    return ((c * (2.51f * c + 0.03f)) / (c * (2.43f * c + 0.59f) + 0.14f)).coerceIn(0f, 1f)
}

private fun encode(value: Float): Int = (aces(value).pow(1f / 2.2f) * 255f).toInt().coerceIn(0, 255)

internal class SkyRay(val x: Float, val y: Float, val z: Float)

internal class CloudModel(private val style: FloatArray, private val windX: Float, private val windZ: Float) {
    fun field(qx: Float, qy: Float, detail: Float, octaves: Float): Float {
        val ax = qx * windX + qy * windZ
        val ay = -qx * windZ + qy * windX
        var px = ax * style[0] * 1.15f
        var py = ay * style[1] * 1.15f
        val wx = SkyNoise.fbm(px * 0.5f + 3.1f, py * 0.5f + 7.7f, 2f) - 0.5f
        val wy = SkyNoise.fbm(px * 0.5f + 11.5f, py * 0.5f + 2.3f, 2f) - 0.5f
        px += wx * 1.4f
        py += wy * 1.4f
        val base = SkyNoise.fbm(px, py, octaves)
        val cov = SkyNoise.fbm(px * 0.2f + 5f, py * 0.2f + 1f, 3f)
        var f = (cov * 0.55f + base * 0.45f - 0.5f) * 2.4f + 0.5f
        if (detail > 0f) {
            val det = SkyNoise.fbm(px * 3.5f + 20f, py * 3.5f + 8f, 3f)
            f += 0.34f * (det - 0.5f) * detail * style[3]
        }
        return f
    }

    fun cirrus(qx: Float, qy: Float, octaves: Float): Float {
        val ax = qx * windX + qy * windZ
        val ay = -qx * windZ + qy * windX
        var px = ax * 0.30f
        var py = ay * 1.7f
        val w = SkyNoise.fbm(px * 0.5f + 2f, py * 0.5f + 5f, 2f) - 0.5f
        px += w * 0.8f
        py += w * 1.6f
        val n = SkyNoise.fbm(px, py, min(octaves + 1f, 4f))
        val ridged = 1f - abs(2f * n - 1f)
        val mask = SkyNoise.fbm(px * 0.13f + 9f, py * 0.13f + 3f, 3f)
        return ((mask - 0.50f) * 2.6f).coerceIn(0f, 1f) * ridged.pow(3f)
    }
}

internal class SkySoftwareRenderer(
    val skyWidth: Int,
    val skyHeight: Int,
    val cloudWidth: Int,
    val cloudHeight: Int,
    val moonSize: Int
) {
    val skyBitmap: Bitmap = Bitmap.createBitmap(skyWidth, skyHeight, Bitmap.Config.ARGB_8888)
    val cloudBitmap: Bitmap = Bitmap.createBitmap(cloudWidth, cloudHeight, Bitmap.Config.ARGB_8888)
    val moonBitmap: Bitmap = Bitmap.createBitmap(moonSize, moonSize, Bitmap.Config.ARGB_8888)

    private val skyPixels = IntArray(skyWidth * skyHeight)
    private val cloudPixels = IntArray(cloudWidth * cloudHeight)
    private val moonPixels = IntArray(moonSize * moonSize)
    private val color = FloatArray(3)
    private val mie = SkyPhysics.MIE_COEFFICIENT.toFloat()

    fun rayAt(scene: SkyScene, ux: Float, uy: Float): SkyRay {
        val ur = hypot(ux, uy)
        val theta = 2f * kotlin.math.atan(ur / (2f * FOCAL))
        val ct = cos(theta)
        val spread = if (ur > 1e-6f) sin(theta) / ur else 0f
        val x = scene.forward[0] * ct + (scene.right[0] * ux + scene.up[0] * uy) * spread
        val y = scene.forward[1] * ct + (scene.right[1] * ux + scene.up[1] * uy) * spread
        val z = scene.forward[2] * ct + (scene.right[2] * ux + scene.up[2] * uy) * spread
        val len = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
        return SkyRay(x / len, y / len, z / len)
    }

    private fun scattering(ray: SkyRay, scene: SkyScene, steps: Int) {
        val ry = max(ray.y, 0.003f)
        val len = sqrt(ray.x * ray.x + ry * ry + ray.z * ray.z).coerceAtLeast(1e-6f)
        val dx = ray.x / len
        val dy = ry / len
        val dz = ray.z / len
        val sx = scene.sun[0]
        val sy = scene.sun[1]
        val sz = scene.sun[2]
        val oy = RE + 2f
        val b = oy * dy
        val span = max(-b + sqrt(max(b * b - (oy * oy - RAT * RAT), 0f)), 1f)
        val mu = (dx * sx + dy * sy + dz * sz).coerceIn(-1f, 1f)
        val rayleighPhase = 3f / (16f * PI_F) * (1f + mu * mu)
        val g = 0.76f
        val miePhase = 3f / (8f * PI_F) * ((1f - g * g) * (1f + mu * mu)) /
            ((2f + g * g) * max(1f + g * g - 2f * g * mu, 1e-4f).pow(1.5f))
        var opticalR = 0f
        var opticalM = 0f
        var sumR0 = 0f
        var sumR1 = 0f
        var sumR2 = 0f
        var sumM = 0f
        val count = steps.coerceIn(3, 10)
        var i = 0
        while (i < count) {
            val u = (i + 0.5f) / count
            val t = span * u * u
            val dt = span * 2f * u / count
            val px = dx * t
            val py = oy + dy * t
            val pz = dz * t
            val r = sqrt(px * px + py * py + pz * pz).coerceAtLeast(1f)
            val h = max(r - RE, 0f)
            val er = exp(-h / HR)
            val em = exp(-h / HM)
            val hr = er * dt
            val hm = em * dt
            opticalR += hr
            opticalM += hm
            val cz = (px * sx + py * sy + pz * sz) / r
            val m = airmass(cz)
            val sh = (cz * 20f + 1f).coerceIn(0f, 1f)
            val tauView = mie * 1.11f * opticalM + BO[0] * HO * (opticalR / HR)
            val att0 = exp(-(BR[0] * opticalR + tauView + (BR[0] * HR * er + mie * 1.11f * HM * em + BO[0] * HO) * m)) * sh
            val att1 = exp(
                -(BR[1] * opticalR + mie * 1.11f * opticalM + BO[1] * HO * (opticalR / HR) +
                    (BR[1] * HR * er + mie * 1.11f * HM * em + BO[1] * HO) * m)
            ) * sh
            val att2 = exp(
                -(BR[2] * opticalR + mie * 1.11f * opticalM + BO[2] * HO * (opticalR / HR) +
                    (BR[2] * HR * er + mie * 1.11f * HM * em + BO[2] * HO) * m)
            ) * sh
            sumR0 += att0 * hr
            sumR1 += att1 * hr
            sumR2 += att2 * hr
            sumM += (att0 + att1 + att2) / 3f * hm
            i++
        }
        val r0 = 20f * (sumR0 * BR[0] * rayleighPhase + sumM * mie * miePhase) * 0.6f
        val r1 = 20f * (sumR1 * BR[1] * rayleighPhase + sumM * mie * miePhase) * 0.6f
        val r2 = 20f * (sumR2 * BR[2] * rayleighPhase + sumM * mie * miePhase) * 0.6f
        val below = (-ray.y / 0.12f).coerceIn(0f, 1f)
        val luma = skyLuma(r0, r1, r2)
        val mix = 0.6f * below
        val dim = 1f - 0.8f * below
        color[0] = (r0 + (luma - r0) * mix) * dim
        color[1] = (r1 + (luma - r1) * mix) * dim
        color[2] = (r2 + (luma - r2) * mix) * dim
    }

    fun renderSky(scene: SkyScene, steps: Int, flash: FloatArray?) {
        val aspect = skyWidth.toFloat() / skyHeight.toFloat()
        var index = 0
        var y = 0
        while (y < skyHeight) {
            val uy = 0.5f - (y + 0.5f) / skyHeight
            var x = 0
            while (x < skyWidth) {
                val ux = ((x + 0.5f) / skyWidth - 0.5f) * aspect
                val ray = rayAt(scene, ux, uy)
                scattering(ray, scene, steps)
                var r = color[0]
                var g = color[1]
                var b = color[2]
                val ry = ray.y
                val dayK = smoothstep(-0.16f, 0.35f, scene.sun[1])
                val twilight = smoothstep(-0.14f, 0f, scene.sun[1]) * (1f - smoothstep(0f, 0.22f, scene.sun[1]))
                val shade = scene.shade.coerceIn(0f, 0.985f)
                val nightLum = 0.004f + min(skyLuma(scene.moon.moonlight[0], scene.moon.moonlight[1], scene.moon.moonlight[2]) * 0.9f, 0.01f)
                val litK = (1f - shade).pow(3f)
                val target = (0.95f * dayK + nightLum * (1f - dayK)) * litK
                val tint = scene.tint
                val tintTwilight = 1f - dayK
                val tr = tint[0] * (1f + 0.10f * twilight * 0.6f - 0.12f * tintTwilight)
                val tg = tint[1] * (1f - 0.05f * twilight * 0.6f - 0.05f * tintTwilight)
                val tb = tint[2] * (1f - 0.16f * twilight * 0.6f + 0.12f * tintTwilight)
                val grade = scene.grade.coerceIn(0f, 1f)
                val horizonBand = (1f - ry.coerceIn(0f, 1f)).pow(2f)
                val veil = scene.veil.coerceIn(0f, 1f) * (0.3f + 0.7f * horizonBand) * grade
                val luma = skyLuma(r, g, b)
                val targetLum = target
                val newLum = luma + (targetLum - luma) * grade
                val scale = if (luma > 0.0002f) (newLum / luma).coerceIn(0f, 6f) else 1f
                r = r * scale + (tr * newLum - r * scale) * grade * 0.94f
                g = g * scale + (tg * newLum - g * scale) * grade * 0.94f
                b = b * scale + (tb * newLum - b * scale) * grade * 0.94f
                r += (tr * targetLum * 1.5f - r) * veil
                g += (tg * targetLum * 1.5f - g) * veil
                b += (tb * targetLum * 1.5f - b) * veil
                if (flash != null) {
                    val cosF = (ray.x * flash[0] + ray.y * flash[1] + ray.z * flash[2]).coerceIn(-1f, 1f)
                    val intensity = flash[3] * (0.10f + 0.90f * exp(-(1f - cosF) * 18f))
                    r += 0.75f * intensity
                    g += 0.82f * intensity
                    b += 1.0f * intensity
                }
                skyPixels[index] = (255 shl 24) or (encode(r) shl 16) or (encode(g) shl 8) or encode(b)
                index++
                x++
            }
            y++
        }
        skyBitmap.setPixels(skyPixels, 0, skyWidth, 0, 0, skyWidth, skyHeight)
    }

    fun renderClouds(
        scene: SkyScene,
        offsetLow: FloatArray,
        offsetHigh: FloatArray,
        windDirection: FloatArray,
        shadowSamples: Int,
        octaves: Float,
        flash: FloatArray?
    ) {
        val model = CloudModel(scene.style, windDirection[0], windDirection[1])
        val threshold = 0.90f - 0.83f * scene.cover
        val k = scene.thickness * scene.cover
        val amb = scene.ambient
        val twilight = (1f - smoothstep(0.02f, 0.35f, scene.sun[1])) * smoothstep(-0.14f, 0.02f, scene.sun[1])
        val ambR = amb[0] * (1f - 0.8f * k) + skyLuma(amb[0], amb[1], amb[2]) * 0.8f * k * 0.92f + scene.sunTransmittance[0] * 0.05f * twilight
        val ambG = amb[1] * (1f - 0.8f * k) + skyLuma(amb[0], amb[1], amb[2]) * 0.8f * k * 0.97f + scene.sunTransmittance[1] * 0.05f * twilight
        val ambB = amb[2] * (1f - 0.8f * k) + skyLuma(amb[0], amb[1], amb[2]) * 0.8f * k * 1.12f + scene.sunTransmittance[2] * 0.05f * twilight
        val sunI = (smoothstep(-0.10f, 0.10f, scene.sun[1]) * (0.62f + 0.38f * smoothstep(0f, 0.45f, scene.sun[1]))).coerceIn(0f, 1f)
        val sunX = scene.sun[0]
        val sunY = scene.sun[1]
        val sunZ = scene.sun[2]
        val moonPhaseBase = 0.7f
        val aspect = cloudWidth.toFloat() / cloudHeight.toFloat()
        val sunHoriz = hypot(sunX, sunZ).coerceAtLeast(1e-3f)
        var index = 0
        var y = 0
        while (y < cloudHeight) {
            val uy = 0.5f - (y + 0.5f) / cloudHeight
            var x = 0
            while (x < cloudWidth) {
                val ux = ((x + 0.5f) / cloudWidth - 0.5f) * aspect
                val ray = rayAt(scene, ux, uy)
                val ry = ray.y
                if (ry <= 0.03f) {
                    cloudPixels[index] = 0
                    index++
                    x++
                    continue
                }
                val th = 1.6f / max(ry, 0.03f)
                val qx = ray.x * th - offsetLow[0]
                val qy = ray.z * th - offsetLow[1]
                val detail = smoothstep(0.05f, 0.45f, ry) * ((octaves - 2f) / 2f).coerceIn(0f, 1f)
                val oct = 2f + (octaves - 2f) * smoothstep(0.06f, 0.3f, ry)
                val f = model.field(qx, qy, detail, oct)
                val density = ((f - threshold) / scene.style[2]).coerceIn(0f, 1f).pow(1.3f) *
                    smoothstep(0.04f, 0.26f, ry)
                if (density < 0.03f) {
                    cloudPixels[index] = 0
                    index++
                    x++
                    continue
                }
                val thick = ((f - threshold) / 0.55f).coerceIn(0f, 1f)
                var occluded = 0f
                if (shadowSamples > 0 && ry > 0.08f) {
                    val sx = sunX / sunHoriz
                    val sz = sunZ / sunHoriz
                    var sample = 0
                    while (sample < shadowSamples) {
                        val s = 0.2f + 0.3f * sample
                        val fs = model.field(qx + sx * s, qy + sz * s, detail, min(oct, 2f + 1f))
                        occluded += ((fs - threshold) / 0.3f).coerceIn(0f, 1f) / shadowSamples
                        sample++
                    }
                }
                val cosg = (ray.x * sunX + ray.y * sunY + ray.z * sunZ).coerceIn(-1f, 1f)
                val phase = 0.6f + 1.4f * max(cosg, 0f).pow(6f)
                val silver = 1f + 3f * max(cosg, 0f).pow(24f) * (1f - thick)
                val direct = exp(-occluded * (1.0f + 1.6f * scene.thickness)) *
                    (0.35f + 0.65f * exp(-thick * (0.9f + 1.4f * scene.thickness)))
                val sunLight = sunI * direct * phase * silver * 2f * (1f - 0.6f * k) * (1f - 0.5f * exp(-3f * density))
                val moon = scene.moon.moonlight
                val moonLight = moonPhaseBase * (1f - 0.5f * thick)
                val diffuse = sunI * 0.5f * k * exp(-thick * 1.5f) * (0.6f + 0.4f * (1f - occluded))
                val cR = ambR * 1.15f * (1f - (0.25f + 0.4f * scene.thickness) * thick) +
                    scene.sunTransmittance[0] * sunLight + moon[0] * moonLight + scene.sunTransmittance[0] * diffuse
                val cG = ambG * 1.15f * (1f - (0.25f + 0.4f * scene.thickness) * thick) +
                    scene.sunTransmittance[1] * sunLight + moon[1] * moonLight + scene.sunTransmittance[1] * diffuse
                val cB = ambB * 1.15f * (1f - (0.25f + 0.4f * scene.thickness) * thick) +
                    scene.sunTransmittance[2] * sunLight + moon[2] * moonLight + scene.sunTransmittance[2] * diffuse
                val shade = (1.08f + (0.84f - 1.08f) * ((f - threshold) / 0.8f).coerceIn(0f, 1f))
                var rr = cR * shade
                var gg = cG * shade
                var bb = cB * shade
                if (flash != null) {
                    val cosF = (ray.x * flash[0] + ray.y * flash[1] + ray.z * flash[2]).coerceIn(-1f, 1f)
                    val intensity = flash[3] * (0.10f + 0.90f * exp(-(1f - cosF) * 18f))
                    rr += 0.75f * intensity * 0.9f
                    gg += 0.82f * intensity * 0.9f
                    bb += 1.0f * intensity * 0.9f
                }
                val slant = (1f + (1f / max(ry, 0.25f) - 1f) * 0.22f).coerceAtMost(1.9f)
                val tau = (1f + 3f * scene.thickness) * density * 1.3f * slant
                val alpha = ((1f - exp(-tau)) * 255f).toInt().coerceIn(0, 255)
                cloudPixels[index] = (alpha shl 24) or (encode(rr) shl 16) or (encode(gg) shl 8) or encode(bb)
                index++
                x++
            }
            y++
        }
        cloudBitmap.setPixels(cloudPixels, 0, cloudWidth, 0, 0, cloudWidth, cloudHeight)
    }

    fun renderMoon(moon: MoonView, earthshine: Boolean) {
        val light = moon.light
        val cosTheta = moon.cosTheta
        val sinTheta = moon.sinTheta
        val eclipse = moon.eclipse
        var index = 0
        var y = 0
        while (y < moonSize) {
            val qy = -((y + 0.5f) / moonSize * 2f - 1f)
            var x = 0
            while (x < moonSize) {
                val qx = (x + 0.5f) / moonSize * 2f - 1f
                val r2 = qx * qx + qy * qy
                if (r2 >= 1f) {
                    moonPixels[index] = 0
                    index++
                    x++
                    continue
                }
                val nz = sqrt(1f - r2)
                val px = qx * cosTheta + qy * sinTheta
                val py = -qx * sinTheta + qy * cosTheta
                val maria = SkyNoise.fbm(px * 1.9f + 4.2f, py * 1.9f + 8.6f, 4f)
                val craterField = SkyNoise.fbm(px * 11f + 21.5f, py * 11f + 3.5f, 3f)
                val craterRing = abs(craterField - 0.5f) * 2f
                var albedo = 0.78f
                albedo -= smoothstep(0.52f, 0.68f, maria) * 0.30f
                albedo += smoothstep(0.78f, 0.92f, craterRing) * 0.16f
                albedo -= smoothstep(0.86f, 1f, craterRing) * 0.10f
                albedo *= 0.9f + 0.1f * (1f - r2)
                val mu0 = qx * light[0] + qy * light[1] + nz * light[2]
                var lit = if (mu0 > 0f) mu0 * 0.35f + (2f * mu0 / (mu0 + nz + 0.0001f)) * 0.65f else 0f
                var shade = 1f
                var redR = 0f
                var redG = 0f
                var redB = 0f
                if (eclipse[2] > 0f) {
                    val dd = hypot(qx - eclipse[0], qy - eclipse[1])
                    val inPen = smoothstep(eclipse[3], eclipse[2], dd)
                    val inUmb = 1f - smoothstep(eclipse[2] - 0.05f, eclipse[2] + 0.05f, dd)
                    val ratio = (dd / max(eclipse[2], 0.001f)).coerceIn(0f, 1f)
                    val t = ratio.pow(2.5f)
                    redR = 0.22f + (0.85f - 0.22f) * t
                    redG = 0.025f + (0.20f - 0.025f) * t
                    redB = 0.010f + (0.05f - 0.010f) * t
                    shade = (1f - 0.35f * inPen) * (1f - inUmb)
                    lit *= shade
                    val glow = inUmb * 0.5f * sqrt(nz)
                    redR *= glow
                    redG *= glow
                    redB *= glow
                }
                val earth = if (earthshine) moon.earthshine * nz else 0f
                val base = albedo * (lit + earth)
                val r = base * moon.transmittance[0] * 0.22f + redR * albedo
                val g = base * moon.transmittance[1] * 0.22f + redG * albedo
                val b = base * moon.transmittance[2] * 0.22f + redB * albedo
                val edge = (1f - r2).coerceIn(0f, 1f)
                val coverage = (edge * moonSize * 0.5f).coerceIn(0f, 1f)
                val alpha = (coverage * 255f).toInt().coerceIn(0, 255)
                moonPixels[index] = (alpha shl 24) or (encode(max(r, 0f)) shl 16) or (encode(max(g, 0f)) shl 8) or encode(max(b, 0f))
                index++
                x++
            }
            y++
        }
        moonBitmap.setPixels(moonPixels, 0, moonSize, 0, 0, moonSize, moonSize)
    }
}

internal class StarPoint(val x: Float, val y: Float, val magnitude: Float)

internal fun projectStars(scene: SkyScene, count: Int): List<StarPoint> {
    val result = ArrayList<StarPoint>(count)
    val eq = scene.skyEquatorial
    var i = 0
    while (i < count) {
        val u = SkyNoise.hash(i * 7919 + 13, 104729)
        val v = SkyNoise.hash(i * 104729 + 7, 7919)
        val ra = (u - 0.5f) * 2f * PI_F
        val dec = kotlin.math.asin((v - 0.5f) * 1.8f)
        val cosDec = cos(dec)
        val x = cosDec * cos(ra)
        val y = cosDec * sin(ra)
        val z = sin(dec)
        val a = eq[1] * y - eq[0] * z
        val b = -x
        val dir = floatArrayOf(
            eq[3] * a + eq[2] * b,
            eq[2] * a - eq[3] * b,
            eq[0] * y + eq[1] * z
        )
        val mag = SkyNoise.hash(i * 31 + 5, 97).pow(4f)
        val ux = (dir[0] * scene.right[0] + dir[1] * scene.right[1] + dir[2] * scene.right[2])
        val uy = (dir[0] * scene.up[0] + dir[1] * scene.up[1] + dir[2] * scene.up[2])
        val uf = (dir[0] * scene.forward[0] + dir[1] * scene.forward[1] + dir[2] * scene.forward[2])
        if (uf <= 0.05f) {
            i++
            continue
        }
        val ang = atan2(hypot(ux, uy), uf)
        val spread = (2f * FOCAL * kotlin.math.tan(ang / 2f))
        val px = if (ux * ux + uy * uy > 1e-9f) ux / hypot(ux, uy) * spread else 0f
        val py = if (ux * ux + uy * uy > 1e-9f) uy / hypot(ux, uy) * spread else 0f
        if (abs(px) > 1f || abs(py) > 1f) {
            i++
            continue
        }
        result.add(StarPoint(px, py, 0.15f + 0.85f * mag))
        i++
    }
    return result
}
