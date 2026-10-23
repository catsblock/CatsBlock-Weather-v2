package com.catsblock.weather.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.catsblock.weather.data.Moon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private fun hash(x: Int, y: Int, z: Int): Float {
    var h = x * 374761393 + y * 668265263 + z * 1274126177
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65535f
}

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

private fun valueNoise(x: Float, y: Float, z: Float): Float {
    val xi = floor(x).toInt()
    val yi = floor(y).toInt()
    val zi = floor(z).toInt()
    val xf = smooth(x - xi)
    val yf = smooth(y - yi)
    val zf = smooth(z - zi)
    fun l(a: Float, b: Float, t: Float) = a + (b - a) * t
    val c00 = l(hash(xi, yi, zi), hash(xi + 1, yi, zi), xf)
    val c10 = l(hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi), xf)
    val c01 = l(hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1), xf)
    val c11 = l(hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1), xf)
    return l(l(c00, c10, yf), l(c01, c11, yf), zf)
}

private fun fbm(x: Float, y: Float, z: Float): Float {
    var amp = 0.5f
    var f = 1f
    var sum = 0f
    for (i in 0 until 5) {
        sum += amp * valueNoise(x * f, y * f, z * f)
        amp *= 0.5f
        f *= 2.03f
    }
    return sum
}

private fun renderMoon(cycle: Double, px: Int): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(px * px)
    val alpha = 2.0 * PI * cycle
    val lx = sin(alpha).toFloat()
    val lz = (-cos(alpha)).toFloat()
    val r = px / 2f
    for (j in 0 until px) {
        for (i in 0 until px) {
            val nx = (i + 0.5f - r) / r
            val ny = (j + 0.5f - r) / r
            val d2 = nx * nx + ny * ny
            if (d2 > 1f) continue
            val nz = sqrt(1f - d2)
            val maria = fbm(nx * 2.2f + 5f, ny * 2.2f + 9f, nz * 2.2f + 3f)
            val craters = fbm(nx * 9f + 20f, ny * 9f + 11f, nz * 9f + 7f)
            val albedo = (0.82f - 0.34f * smooth(((maria - 0.42f) * 5f).coerceIn(0f, 1f)) + (craters - 0.5f) * 0.18f)
                .coerceIn(0.2f, 1f)
            val dot = nx * lx + nz * lz
            val lit = smooth(((dot + 0.06f) / 0.14f).coerceIn(0f, 1f)) * max(dot, 0f).pow(0.75f)
            val earthshine = 0.035f * nz
            val v = (albedo * (lit * 1.12f) + earthshine).coerceIn(0f, 1f)
            val rr = (v * 255f * 1.0f).toInt().coerceIn(0, 255)
            val gg = (v * 250f).toInt().coerceIn(0, 255)
            val bb = (v * 232f).toInt().coerceIn(0, 255)
            pixels[j * px + i] = (255 shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
    }
    bmp.setPixels(pixels, 0, px, 0, 0, px, px)
    return bmp
}

@Composable
fun MoonSphere3D(ms: Long, lat: Double, lon: Double, modifier: Modifier = Modifier) {
    val minute = ms / 60_000L
    val phase = remember(minute) { Moon.phase(ms) }
    val position = remember(minute, lat, lon) { Moon.position(ms, lat, lon) }
    val bitmap = remember((phase.cycle * 2000).toInt()) { renderMoon(phase.cycle, 220).asImageBitmap() }
    val tiltDeg = remember(minute, lat, lon) {
        val az = position.azimuth
        val alt = position.altitude
        Math.toDegrees(sin(az - PI) * (1.0 - alt / (PI / 2.0)) * 0.6 + (az - PI) * 0.15)
            .toFloat()
            .coerceIn(-60f, 60f)
    }
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.22f * (0.3f + phase.illumination.toFloat())), Color.Transparent),
                center = c,
                radius = r * 1.25f
            ),
            r * 1.25f, c
        )
        rotate(tiltDeg, c) {
            val side = (r * 2f * 0.94f).toInt()
            drawImage(
                bitmap,
                dstOffset = IntOffset((c.x - side / 2f).toInt(), (c.y - side / 2f).toInt()),
                dstSize = IntSize(side, side),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High
            )
        }
    }
}
