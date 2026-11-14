package com.catsblock.weather.ui.sky

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.catsblock.weather.data.Condition

@Stable
class SkyAtmosphere(
    val shade: Float,
    val grade: Float,
    val contrast: Float,
    val veil: Float,
    val glow: Float,
    val scrim: Float,
    val tint: FloatArray
)

private class Look(
    val shade: Float,
    val grade: Float,
    val contrast: Float,
    val veil: Float,
    val glow: Float,
    val tint: FloatArray
)

private val TINT_NEUTRAL = floatArrayOf(1.00f, 1.00f, 1.00f)
private val TINT_OVERCAST = floatArrayOf(0.99f, 1.00f, 1.03f)
private val TINT_RAIN = floatArrayOf(0.95f, 0.99f, 1.05f)
private val TINT_SLATE = floatArrayOf(0.92f, 0.98f, 1.03f)
private val TINT_STORM = floatArrayOf(0.93f, 1.00f, 1.00f)
private val TINT_HAIL = floatArrayOf(0.90f, 1.03f, 0.96f)
private val TINT_SNOW = floatArrayOf(0.99f, 1.00f, 1.04f)
private val TINT_SLEET = floatArrayOf(0.96f, 1.00f, 1.05f)
private val TINT_FOG = floatArrayOf(1.01f, 1.00f, 0.98f)
private val TINT_HAZE = floatArrayOf(1.07f, 0.99f, 0.86f)

private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun blend(a: FloatArray, b: FloatArray, t: Float): FloatArray =
    FloatArray(3) { i -> a[i] + (b[i] - a[i]) * t }

private fun rainIntensity(precipMm: Double, fallback: Float): Float =
    if (precipMm >= 0.0) smooth(0.05f, 10f, precipMm.toFloat()) else fallback

private fun stormIntensity(precipMm: Double, fallback: Float): Float =
    if (precipMm >= 0.0) smooth(0.5f, 15f, precipMm.toFloat()) else fallback

private fun isDryKind(kind: Condition): Boolean = when (kind) {
    Condition.Clear, Condition.FewClouds, Condition.PartlyCloudy, Condition.MoreClouds,
    Condition.Cloudy, Condition.Windy -> true
    else -> false
}

private fun lookFor(kind: Condition, cover: Float, precipMm: Double): Look {
    val rain = rainIntensity(precipMm, 0.35f)
    val storm = stormIntensity(precipMm, 0.4f)
    return when (kind) {
        Condition.Clear, Condition.FewClouds -> Look(0f, 0f, 0.2f, 0f, 0f, TINT_NEUTRAL)
        Condition.PartlyCloudy -> Look(0.04f, 0.08f, 0.2f, 0f, 0.3f, TINT_NEUTRAL)
        Condition.MoreClouds -> Look(0.04f + 0.08f * smooth(0.6f, 0.9f, cover), 0.4f, 0.22f, 0f, 0.5f, TINT_OVERCAST)
        Condition.Cloudy -> Look(0.05f + 0.27f * smooth(0.72f, 1f, cover), 1f, 0.22f, 0.10f, 0.7f, TINT_OVERCAST)
        Condition.Windy -> Look(0.02f, 0.1f, 0.2f, 0f, 0f, TINT_NEUTRAL)
        Condition.Fog -> Look(0.10f, 0.85f, 0.05f, 0.45f, 0.35f, TINT_FOG)
        Condition.Drizzle -> Look(0.30f + 0.10f * rain, 1f, 0.22f, 0.28f, 0.35f, TINT_RAIN)
        Condition.Rain -> Look(0.42f + 0.22f * rain, 1f, 0.30f, 0.32f, 0.40f, TINT_RAIN)
        Condition.ScatteredShowers -> Look(0.36f + 0.20f * rain, 0.9f, 0.45f, 0.30f, 0.55f, TINT_RAIN)
        Condition.HeavyRain -> Look(0.60f + 0.20f * rain, 1f, 0.35f, 0.38f, 0.40f, TINT_SLATE)
        Condition.Hail -> Look(0.64f + 0.14f * rain, 1f, 0.50f, 0.30f, 0.30f, TINT_HAIL)
        Condition.Sleet, Condition.RainSnow, Condition.WintryMix -> Look(0.34f + 0.14f * rain, 1f, 0.20f, 0.30f, 0.35f, TINT_SLEET)
        Condition.Icy -> Look(0.30f + 0.10f * rain, 1f, 0.20f, 0.30f, 0.35f, TINT_SLEET)
        Condition.Storm -> Look(0.78f + 0.12f * storm, 1f, 0.55f, 0.18f, 0.70f, TINT_STORM)
        Condition.ScatteredStorms -> Look(0.74f + 0.12f * storm, 1f, 0.55f, 0.18f, 0.70f, TINT_STORM)
        Condition.IsolatedStorms -> Look(0.68f + 0.12f * storm, 0.95f, 0.55f, 0.18f, 0.75f, TINT_STORM)
        Condition.StrongStorms -> Look(0.90f + 0.06f * storm, 1f, 0.55f, 0.18f, 0.70f, TINT_STORM)
        Condition.Snow -> Look(0.10f + 0.06f * rain, 1f, 0.20f, 0.20f, 0.50f, TINT_SNOW)
        Condition.HeavySnow -> Look(0.16f + 0.08f * rain, 1f, 0.15f, 0.35f, 0.45f, TINT_SNOW)
        Condition.Blizzard -> Look(0.20f + 0.08f * rain, 1f, 0.08f, 0.70f, 0.30f, TINT_SNOW)
        Condition.Flurries -> Look(0.06f, 0.85f, 0.20f, 0.15f, 0.50f, TINT_SNOW)
        Condition.BlowingSnow -> Look(0.14f, 1f, 0.15f, 0.45f, 0.40f, TINT_SNOW)
        Condition.ScatteredSnow -> Look(0.09f, 0.9f, 0.25f, 0.20f, 0.50f, TINT_SNOW)
        Condition.SnowShowers -> Look(0.13f, 1f, 0.25f, 0.25f, 0.50f, TINT_SNOW)
    }
}

private fun isRainFamily(kind: Condition): Boolean = when (kind) {
    Condition.Drizzle, Condition.Rain, Condition.ScatteredShowers, Condition.HeavyRain, Condition.Hail,
    Condition.Storm, Condition.ScatteredStorms, Condition.IsolatedStorms, Condition.StrongStorms -> true
    else -> false
}

fun skyAtmosphereTarget(
    kind: Condition,
    cover: Float,
    precipMm: Double,
    visibilityM: Double,
    pressureHpa: Double
): SkyAtmosphere {
    val look = lookFor(kind, cover, precipMm)
    var shade = look.shade
    var grade = look.grade
    var veil = look.veil
    var glow = look.glow
    var tint = look.tint
    if (look.grade >= 0.8f && !pressureHpa.isNaN()) {
        shade += 0.05f * ((1013.0 - pressureHpa) / 50.0).toFloat().coerceIn(-0.5f, 1f)
    }
    if (isDryKind(kind)) {
        val haze = 1f - smooth(3000f, 10000f, visibilityM.toFloat())
        if (haze > 0f) {
            grade = maxOf(grade, 0.45f * haze)
            shade += 0.08f * haze
            veil = maxOf(veil, 0.4f * haze)
            glow = maxOf(glow, 0.6f * haze)
            tint = blend(tint, TINT_HAZE, haze * 0.85f)
        }
    }
    val scrim = if (isRainFamily(kind) && precipMm >= 0.0) 0.28f * smooth(0.1f, 10f, precipMm.toFloat()) else 0f
    return SkyAtmosphere(
        shade = shade.coerceIn(0f, 0.97f),
        grade = grade.coerceIn(0f, 1f),
        contrast = look.contrast,
        veil = veil.coerceIn(0f, 1f),
        glow = glow.coerceIn(0f, 1f),
        scrim = scrim,
        tint = tint
    )
}

@Composable
internal fun rememberSkyAtmosphere(target: SkyAtmosphere): SkyAtmosphere {
    val shade by animateFloatAsState(target.shade, tween(1200), label = "atmosphereShade")
    val grade by animateFloatAsState(target.grade, tween(1200), label = "atmosphereGrade")
    val contrast by animateFloatAsState(target.contrast, tween(1200), label = "atmosphereContrast")
    val veil by animateFloatAsState(target.veil, tween(1200), label = "atmosphereVeil")
    val glow by animateFloatAsState(target.glow, tween(1200), label = "atmosphereGlow")
    val scrim by animateFloatAsState(target.scrim, tween(1200), label = "atmosphereScrim")
    val red by animateFloatAsState(target.tint[0], tween(1200), label = "atmosphereRed")
    val green by animateFloatAsState(target.tint[1], tween(1200), label = "atmosphereGreen")
    val blue by animateFloatAsState(target.tint[2], tween(1200), label = "atmosphereBlue")
    return remember(shade, grade, contrast, veil, glow, scrim, red, green, blue) {
        SkyAtmosphere(shade, grade, contrast, veil, glow, scrim, floatArrayOf(red, green, blue))
    }
}
