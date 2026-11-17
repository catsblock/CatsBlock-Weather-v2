package com.catsblock.weather.ui.sky

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

private const val WIND_GAIN = 4.0
private const val WRAP_KM = 2226.087
private const val HIGH_LAYER_SPEED_RATIO = 2.0
private const val FOCAL = 0.5361f
private const val SUN_VIEW_RADIUS = 0.07f

internal class SoftwareQuality(val cloudWidth: Int, val cloudHeight: Int, val fps: Int, val shadowSamples: Int, val octaves: Float) {
    companion object {
        fun forTier(tier: SkyTier, refreshRateHz: Float): SoftwareQuality = when (tier) {
            SkyTier.Ultra -> SoftwareQuality(144, 82, tier.targetFps(refreshRateHz), 2, 4f)
            SkyTier.High -> SoftwareQuality(120, 70, tier.targetFps(refreshRateHz), 1, 4f)
            SkyTier.Medium -> SoftwareQuality(104, 60, tier.targetFps(refreshRateHz), 1, 3f)
            SkyTier.Low -> SoftwareQuality(88, 50, tier.targetFps(refreshRateHz), 1, 3f)
            SkyTier.VeryLow, SkyTier.Minimal -> SoftwareQuality(72, 42, tier.targetFps(refreshRateHz).coerceAtLeast(12), 0, 2f)
        }
    }

    fun lowered(): SoftwareQuality = when {
        cloudWidth > 120 -> SoftwareQuality(112, 64, 18, 1, 4f)
        cloudWidth > 96 -> SoftwareQuality(92, 54, 15, 1, 3f)
        cloudWidth > 80 -> SoftwareQuality(76, 44, 12, 0, 2f)
        else -> this
    }
}

private class SunScreenPoint(val x: Float, val y: Float, val visible: Boolean)

private fun project(direction: FloatArray, scene: SkyScene, aspect: Float): FloatArray {
    val u = direction[0] * scene.right[0] + direction[1] * scene.right[1] + direction[2] * scene.right[2]
    val v = direction[0] * scene.up[0] + direction[1] * scene.up[1] + direction[2] * scene.up[2]
    val w = direction[0] * scene.forward[0] + direction[1] * scene.forward[1] + direction[2] * scene.forward[2]
    if (w <= 0.02f) return floatArrayOf(0f, 0f, 0f, 0f)
    val radial = hypot(u, v)
    val angular = atan2(radial, w)
    val spread = 2f * FOCAL * tan(angular / 2f)
    val px = if (radial > 1e-6f) u / radial * spread else 0f
    val py = if (radial > 1e-6f) v / radial * spread else 0f
    return floatArrayOf(px, py, px / aspect + 0.5f, 0.5f - py)
}

private fun DrawScope.drawSun(scene: SkyScene, aspect: Float) {
    val p = project(scene.sun, scene, aspect)
    if (p[2] <= 0f || p[2] >= 1f || p[3] <= 0f || p[3] >= 1f) return
    val center = Offset(p[2] * size.width, p[3] * size.height)
    val sunI = (scene.sun[1] * 8f + 0.05f).coerceIn(0f, 1f)
    if (sunI <= 0.01f) return
    val t = scene.sunTransmittance
    val base = Color(
        (t[0]).coerceIn(0f, 1f),
        (t[1]).coerceIn(0f, 1f),
        (t[2]).coerceIn(0f, 1f),
        1f
    )
    val glowRadius = size.height * 0.55f
    drawCircle(
        brush = Brush.radialGradient(
            0f to base.copy(alpha = 0.55f * sunI),
            0.12f to base.copy(alpha = 0.22f * sunI),
            0.42f to base.copy(alpha = 0.06f * sunI),
            1f to Color.Transparent,
            center = center,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = center
    )
    val discRadius = size.height * SUN_VIEW_RADIUS
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = sunI),
            0.82f to base.copy(alpha = sunI),
            1f to base.copy(alpha = sunI * 0.85f),
            center = center,
            radius = discRadius
        ),
        radius = discRadius,
        center = center
    )
}

private fun DrawScope.drawMoon(scene: SkyScene, aspect: Float, paint: Paint, moonVisible: Boolean, bitmap: Bitmap?) {
    if (!moonVisible) return
    val moon = scene.moon
    val lit = (1f + moon.cosTheta) * 0.5f
    if (lit < 0.03f && moon.eclipse[2] <= 0.01f) return
    val p = project(moon.direction, scene, aspect)
    if (p[2] <= 0f || p[2] >= 1f || p[3] <= 0f || p[3] >= 1f) return
    val cx = p[2] * size.width
    val cy = p[3] * size.height
    val radius = (moon.radiusTan / aspect) * size.width * aspect * 0.5f + moon.radiusTan * size.height * 0.5f
    val diameter = (radius * 2f).coerceAtLeast(2f)
    val haloRadius = size.height * 0.22f
    val t = moon.transmittance
    val haloColor = Color(t[0], t[1], t[2], 1f)
    if (moon.halo > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to haloColor.copy(alpha = 0.10f * moon.halo),
                0.4f to haloColor.copy(alpha = 0.03f * moon.halo),
                1f to Color.Transparent,
                center = Offset(cx, cy),
                radius = haloRadius
            ),
            radius = haloRadius,
            center = Offset(cx, cy)
        )
    }
    if (bitmap == null || bitmap.isRecycled) return
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(
            bitmap,
            null,
            Rect(
                (cx - diameter / 2f).roundToInt(),
                (cy - diameter / 2f).roundToInt(),
                (cx + diameter / 2f).roundToInt(),
                (cy + diameter / 2f).roundToInt()
            ),
            paint
        )
    }
}

private fun DrawScope.drawStars(scene: SkyScene, stars: List<StarPoint>) {
    if (stars.isEmpty()) return
    val dayK = smoothstepFloat(-0.16f, 0.35f, scene.sun[1])
    val visibility = 1f - dayK
    if (visibility <= 0.02f) return
    val aspect = size.width / size.height
    for (star in stars) {
        if (abs(star.x) > aspect / 2f || abs(star.y) > 0.5f) continue
        val x = (star.x / aspect + 0.5f) * size.width
        val y = (0.5f - star.y) * size.height
        val radius = 0.6f + 1.1f * star.magnitude
        drawCircle(
            Color(0.85f, 0.92f, 1f, 0.75f * star.magnitude * visibility),
            radius,
            Offset(x, y)
        )
    }
}

private fun smoothstepFloat(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun DrawScope.drawFlash(scene: SkyScene, lightning: LightningState?, aspect: Float) {
    if (lightning == null || lightning.intensity <= 0.01f) return
    val dir = floatArrayOf(lightning.dirX, lightning.dirY, lightning.dirZ)
    val p = project(dir, scene, aspect)
    val intensity = lightning.intensity
    val radius = size.height * 1.1f
    val center = if (p[2] > 0f && p[2] < 1f && p[3] > 0f && p[3] < 1f) {
        Offset(p[2] * size.width, p[3] * size.height)
    } else {
        Offset(size.width * 0.5f, size.height * 0.2f)
    }
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0.78f, 0.84f, 1f, 0.55f * intensity),
            0.35f to Color(0.7f, 0.78f, 1f, 0.22f * intensity),
            1f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
    drawRect(Color(0.72f, 0.8f, 1f, 0.05f * intensity))
}

@Composable
internal fun SkySoftwareLayer(
    scene: SkyScene,
    tier: SkyTier,
    fade: Float,
    windKmh: Double,
    windFromDeg: Double?,
    lightning: LightningState?,
    refreshRateHz: Float,
    probePerformance: Boolean,
    modifier: Modifier = Modifier
) {
    val currentQuality = remember(tier, refreshRateHz) { SoftwareQuality.forTier(tier, refreshRateHz) }
    val renderer = remember(currentQuality) {
        SkySoftwareRenderer(
            skyWidth = 72,
            skyHeight = 44,
            cloudWidth = currentQuality.cloudWidth,
            cloudHeight = currentQuality.cloudHeight,
            moonSize = 80
        )
    }
    val paint = remember { Paint().apply { isFilterBitmap = true; isAntiAlias = true; isDither = true } }
    var frame by remember { mutableIntStateOf(0) }
    var moonBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val offsets = remember { FloatArray(4) }
    val moonKey = remember { floatArrayOf(Float.NaN, Float.NaN, Float.NaN, Float.NaN) }
    val wind by rememberUpdatedState(windKmh)
    val direction by rememberUpdatedState(windFromDeg)
    val lifecycleOwner = LocalLifecycleOwner.current
    val active = fade > 0.01f
    val windVector = remember(windFromDeg) {
        windFromDeg?.let {
            val toward = Math.toRadians(it + 180.0)
            floatArrayOf(sin(toward).toFloat(), cos(toward).toFloat())
        } ?: floatArrayOf(0f, 0f)
    }
    LaunchedEffect(lifecycleOwner, scene, currentQuality, active, probePerformance) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val scheduledFps = if (probePerformance) SkyTier.Ultra.targetFps(refreshRateHz) else currentQuality.fps
            val interval = 1_000_000_000L / scheduledFps
            var drawn = 0L
            var lastSkySun = Float.NaN
            while (true) {
                val now = withFrameNanos { it }
                if (now - drawn < interval) continue
                val dt = if (drawn == 0L) 0.0 else (now - drawn) / 1e9
                val toward = direction?.let { Math.toRadians(it + 180.0) } ?: 0.0
                val lowKm = if (direction != null) upperWindKmh(wind) / 3600.0 * WIND_GAIN * dt else 0.0
                val highKm = lowKm * HIGH_LAYER_SPEED_RATIO
                offsets[0] = ((offsets[0] + sin(toward) * lowKm).mod(WRAP_KM)).toFloat()
                offsets[1] = ((offsets[1] + cos(toward) * lowKm).mod(WRAP_KM)).toFloat()
                offsets[2] = ((offsets[2] + sin(toward) * highKm).mod(WRAP_KM)).toFloat()
                offsets[3] = ((offsets[3] + cos(toward) * highKm).mod(WRAP_KM)).toFloat()
                val needsSky = lastSkySun.isNaN() || abs(scene.sun[1] - lastSkySun) > 0.0015f
                val lowOffset = floatArrayOf(offsets[0], offsets[1])
                val highOffset = floatArrayOf(offsets[2], offsets[3])
                val needsMoon = moonKey[0] != scene.moon.cosTheta || moonKey[1] != scene.moon.sinTheta ||
                    moonKey[2] != scene.moon.light[0] || moonKey[3] != scene.moon.light[1]
                withContext(Dispatchers.Default) {
                    if (needsSky) {
                        renderer.renderSky(scene, 6, null)
                        lastSkySun = scene.sun[1]
                    }
                    renderer.renderClouds(
                        scene = scene,
                        offsetLow = lowOffset,
                        offsetHigh = highOffset,
                        windDirection = windVector,
                        shadowSamples = currentQuality.shadowSamples,
                        octaves = currentQuality.octaves,
                        flash = null
                    )
                    if (needsMoon) {
                        renderer.renderMoon(scene.moon, true)
                        moonKey[0] = scene.moon.cosTheta
                        moonKey[1] = scene.moon.sinTheta
                        moonKey[2] = scene.moon.light[0]
                        moonKey[3] = scene.moon.light[1]
                    }
                }
                moonBitmap = renderer.moonBitmap
                drawn = now
                frame++
            }
        }
    }
    val stars = remember(scene) { projectStars(scene, 140) }
    Canvas(modifier) {
        if (frame < 0 || fade <= 0.01f) return@Canvas
        val viewAspect = size.width / size.height
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(
                renderer.skyBitmap,
                null,
                Rect(0, 0, size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                paint
            )
        }
        drawStars(scene, stars)
        drawMoon(scene, viewAspect, paint, scene.moonVisible, moonBitmap)
        drawSun(scene, viewAspect)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(
                renderer.cloudBitmap,
                null,
                Rect(0, 0, size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                paint
            )
        }
        drawFlash(scene, lightning, viewAspect)
        if (fade < 0.99f) {
            drawRect(Color.Black.copy(alpha = 1f - fade))
        }
    }
}
