package com.catsblock.weather.ui.sky

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.catsblock.weather.ui.ExpressiveMotion
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIND_GAIN = 4.0
private const val WRAP_KM = 2226.087
private const val HIGH_LAYER_SPEED_RATIO = 2.0

internal fun upperWindKmh(surfaceKmh: Double): Double = surfaceKmh.coerceAtLeast(0.0) * 1.6 + 6.0
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun SkyShaderLayer(
    scene: SkyScene,
    tier: SkyTier,
    fade: Float,
    windKmh: Double,
    windFromDeg: Double,
    lightning: LightningState?,
    refreshRateHz: Float,
    probePerformance: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shader = remember { RuntimeShader(SKY_AGSL) }
    val textureName = if (tier.ordinal >= SkyTier.High.ordinal) "moon/moon_albedo_512.jpg" else "moon/moon_albedo_256.jpg"
    val moonBitmap by produceState<Bitmap?>(null, textureName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(textureName).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    val placeholder = remember { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }
    val activeBitmap = moonBitmap ?: placeholder
    val moonReady = moonBitmap != null
    remember(shader, activeBitmap) {
        shader.setInputShader("uMoonTex", BitmapShader(activeBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
    }
    val brush = remember(shader) { ShaderBrush(shader) }
    val offset = remember { DoubleArray(4) }
    var tick by remember { mutableLongStateOf(0L) }
    val wind by rememberUpdatedState(windKmh)
    val direction by rememberUpdatedState(windFromDeg)
    val lifecycleOwner = LocalLifecycleOwner.current
    val moving = fade > 0.01f

    LaunchedEffect(lifecycleOwner, tier, moving, probePerformance) {
        if (!moving) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val targetFps = if (probePerformance) SkyTier.Ultra.targetFps(refreshRateHz) else tier.targetFps(refreshRateHz)
            val interval = 1_000_000_000L / targetFps.coerceAtLeast(1)
            var drawn = 0L
            while (true) {
                val now = withFrameNanos { it }
                if (now - drawn >= interval) {
                    val dt = if (drawn == 0L) 0.0 else (now - drawn) / 1e9
                    val toward = Math.toRadians(direction + 180.0)
                    val lowKm = upperWindKmh(wind) / 3600.0 * WIND_GAIN * dt
                    val highKm = lowKm * HIGH_LAYER_SPEED_RATIO
                    offset[0] = (offset[0] + sin(toward) * lowKm).mod(WRAP_KM)
                    offset[1] = (offset[1] + cos(toward) * lowKm).mod(WRAP_KM)
                    offset[2] = (offset[2] + sin(toward) * highKm).mod(WRAP_KM)
                    offset[3] = (offset[3] + cos(toward) * highKm).mod(WRAP_KM)
                    drawn = now
                    tick = now
                }
            }
        }
    }

    val scale by animateFloatAsState(tier.renderScale, ExpressiveMotion.contentChange(), label = "skyQualityScale")
    val animatedOctaves by animateFloatAsState(tier.octaves.toFloat(), ExpressiveMotion.contentChange(), label = "skyOctaves")
    val animatedShadowSamples by animateFloatAsState(tier.shadowSamples.toFloat(), ExpressiveMotion.contentChange(), label = "skyShadowSamples")
    val animatedAtmosphereSteps by animateFloatAsState(tier.atmosphereSteps.toFloat(), ExpressiveMotion.contentChange(), label = "skyAtmosphereSteps")
    val animatedOpaqueCut by animateFloatAsState(tier.opaqueCut, ExpressiveMotion.contentChange(), label = "skyOpaqueCut")
    val animatedLiteShadows by animateFloatAsState(if (tier.liteShadows) 1f else 0f, ExpressiveMotion.contentChange(), label = "skyLiteShadows")
    val animatedCirrus by animateFloatAsState(if (tier.cirrus) 1f else 0f, ExpressiveMotion.contentChange(), label = "skyCirrus")
    val chain = Modifier
        .layout { measurable, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val placeable = measurable.measure(
                Constraints.fixed(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1)
                )
            )
            layout(width, height) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            scaleX = 1f / scale
            scaleY = 1f / scale
            transformOrigin = TransformOrigin(0f, 0f)
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawBehind {
            if (tick < 0L || fade <= 0.01f) return@drawBehind
            shader.setFloatUniform("uRes", size.width, size.height)
            shader.setFloatUniform("uSun", scene.sun[0], scene.sun[1], scene.sun[2])
            shader.setFloatUniform("uRight", scene.right[0], scene.right[1], scene.right[2])
            shader.setFloatUniform("uUp", scene.up[0], scene.up[1], scene.up[2])
            shader.setFloatUniform("uFwd", scene.forward[0], scene.forward[1], scene.forward[2])
            shader.setFloatUniform("uSunT", scene.sunTransmittance[0], scene.sunTransmittance[1], scene.sunTransmittance[2])
            shader.setFloatUniform("uAmb", scene.ambient[0], scene.ambient[1], scene.ambient[2])
            shader.setFloatUniform("uOff", offset[0].toFloat(), offset[1].toFloat())
            shader.setFloatUniform("uOffHigh", offset[2].toFloat(), offset[3].toFloat())
            val toward = Math.toRadians(direction + 180.0)
            shader.setFloatUniform("uWindDir", sin(toward).toFloat(), cos(toward).toFloat())
            shader.setFloatUniform("uStyle", scene.style[0], scene.style[1], scene.style[2], scene.style[3])
            shader.setFloatUniform("uCirrus", scene.cirrus * animatedCirrus)
            shader.setFloatUniform("uShade", scene.shade)
            shader.setFloatUniform("uGrade", scene.grade)
            shader.setFloatUniform("uContrast", scene.contrast)
            shader.setFloatUniform("uVeil", scene.veil)
            shader.setFloatUniform("uGlow", scene.glow)
            shader.setFloatUniform("uTint", scene.tint[0], scene.tint[1], scene.tint[2])
            shader.setFloatUniform("uFog", scene.fog)
            if (lightning != null) {
                shader.setFloatUniform("uFlash", lightning.dirX, lightning.dirY, lightning.dirZ, lightning.intensity)
            } else {
                shader.setFloatUniform("uFlash", 0f, 0f, 1f, 0f)
            }
            shader.setFloatUniform("uSunTHigh", scene.sunTransmittanceHigh[0], scene.sunTransmittanceHigh[1], scene.sunTransmittanceHigh[2])
            shader.setFloatUniform("uCover", scene.cover)
            shader.setFloatUniform("uThick", scene.thickness)
            shader.setFloatUniform("uOct", animatedOctaves)
            shader.setFloatUniform("uShadow", animatedShadowSamples)
            shader.setFloatUniform("uReach", tier.reachKm)
            shader.setFloatUniform("uLite", animatedLiteShadows)
            shader.setFloatUniform("uOpaque", animatedOpaqueCut)
            shader.setFloatUniform("uSteps", animatedAtmosphereSteps)
            shader.setFloatUniform("uMie", SkyPhysics.MIE_COEFFICIENT.toFloat())
            shader.setFloatUniform("uFade", fade)
            shader.setFloatUniform("uGain", scene.gain)
            val sky = scene.skyEquatorial
            shader.setFloatUniform("uSkyEq", sky[0], sky[1], sky[2], sky[3])
            val moon = scene.moon
            shader.setFloatUniform("uMoonSize", activeBitmap.width.toFloat(), activeBitmap.height.toFloat())
            shader.setFloatUniform(
                "uMoonA",
                moon.radiusTan,
                moon.cosTheta,
                moon.sinTheta,
                if (moonReady && scene.moonVisible) 1f else 0f
            )
            shader.setFloatUniform("uMoonB", moon.earthshine, moon.halo)
            shader.setFloatUniform("uMoonDir", moon.direction[0], moon.direction[1], moon.direction[2])
            shader.setFloatUniform("uMoonRight", moon.right[0], moon.right[1], moon.right[2])
            shader.setFloatUniform("uMoonUp", moon.up[0], moon.up[1], moon.up[2])
            shader.setFloatUniform("uMoonL", moon.light[0], moon.light[1], moon.light[2])
            shader.setFloatUniform("uMoonT", moon.transmittance[0], moon.transmittance[1], moon.transmittance[2])
            shader.setFloatUniform("uMoonLight", moon.moonlight[0], moon.moonlight[1], moon.moonlight[2])
            shader.setFloatUniform("uEcl", moon.eclipse[0], moon.eclipse[1], moon.eclipse[2], moon.eclipse[3])
            drawRect(brush)
        }
    Box(modifier.then(chain))
}
