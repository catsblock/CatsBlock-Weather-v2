package com.catsblock.weather.ui.sky

import androidx.compose.runtime.Stable
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.Solar
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

@Stable
class SkyScene(
    val moon: MoonView,
    val moonVisible: Boolean,
    val skyEquatorial: FloatArray,
    val gain: Float,
    val style: FloatArray,
    val cirrus: Float,
    val sunTransmittanceHigh: FloatArray,
    val sun: FloatArray,
    val right: FloatArray,
    val up: FloatArray,
    val forward: FloatArray,
    val sunTransmittance: FloatArray,
    val ambient: FloatArray,
    val cover: Float,
    val thickness: Float,
    val dark: Float,
    val fog: Float
)

private const val CAMERA_PITCH_DEG = 58.0

private val STORM_STYLE = floatArrayOf(0.8f, 1.3f, 0.35f, 1.1f)

enum class SkyWeather { Dry, Rain, Storm, Snow, Fog }

fun usesAtmosphere(kind: Condition): Boolean = true

fun skyWeather(kind: Condition): SkyWeather = when (kind) {
    Condition.Fog -> SkyWeather.Fog
    Condition.Drizzle, Condition.Rain, Condition.HeavyRain, Condition.ScatteredShowers, Condition.Sleet,
    Condition.Hail, Condition.RainSnow, Condition.WintryMix, Condition.Icy -> SkyWeather.Rain
    Condition.Storm, Condition.ScatteredStorms, Condition.IsolatedStorms, Condition.StrongStorms -> SkyWeather.Storm
    Condition.Snow, Condition.HeavySnow, Condition.Blizzard, Condition.Flurries, Condition.BlowingSnow,
    Condition.ScatteredSnow, Condition.SnowShowers -> SkyWeather.Snow
    else -> SkyWeather.Dry
}

fun skyCover(kind: Condition, cloudPercent: Int): Float {
    val range = when (kind) {
        Condition.Clear -> 0.0f..0.15f
        Condition.FewClouds -> 0.1f..0.35f
        Condition.PartlyCloudy -> 0.3f..0.6f
        Condition.MoreClouds -> 0.55f..0.85f
        Condition.Windy -> 0.1f..0.7f
        Condition.Fog -> 0.0f..0.7f
        Condition.Storm, Condition.ScatteredStorms, Condition.IsolatedStorms, Condition.StrongStorms -> 0.95f..1.0f
        Condition.Snow, Condition.HeavySnow, Condition.Blizzard, Condition.Flurries, Condition.BlowingSnow,
        Condition.ScatteredSnow, Condition.SnowShowers -> 0.85f..1.0f
        else -> if (skyWeather(kind) == SkyWeather.Rain) 0.9f..1.0f else 0.8f..1.0f
    }
    val fallback = when (kind) {
        Condition.Clear -> 0.03f
        Condition.FewClouds -> 0.2f
        Condition.PartlyCloudy -> 0.45f
        Condition.MoreClouds -> 0.7f
        Condition.Windy -> 0.4f
        Condition.Fog -> 0.3f
        else -> 0.97f
    }
    val measured = if (cloudPercent in 0..100) cloudPercent / 100f else fallback
    return measured.coerceIn(range.start, range.endInclusive)
}

fun skyDarkness(kind: Condition): Float = when (kind) {
    Condition.Drizzle -> 0.25f
    Condition.Rain, Condition.ScatteredShowers -> 0.45f
    Condition.HeavyRain, Condition.Hail -> 0.6f
    Condition.Sleet, Condition.RainSnow, Condition.WintryMix, Condition.Icy -> 0.4f
    Condition.Storm, Condition.ScatteredStorms, Condition.IsolatedStorms -> 0.75f
    Condition.StrongStorms -> 0.85f
    Condition.Blizzard -> 0.2f
    Condition.HeavySnow -> -0.05f
    Condition.Snow, Condition.Flurries, Condition.BlowingSnow, Condition.ScatteredSnow, Condition.SnowShowers -> -0.2f
    Condition.Fog -> 0.1f
    else -> 0f
}

fun skyFog(kind: Condition, visibilityM: Double): Float {
    val haze = ((10000.0 - visibilityM) / 10000.0).coerceIn(0.0, 1.0)
    return if (kind == Condition.Fog) (0.45 + 0.5 * haze).toFloat() else (0.6 * haze).toFloat()
}

fun skyStorm(kind: Condition): Float = if (skyWeather(kind) == SkyWeather.Storm) 1f else 0f

fun skyThickness(kind: Condition, cover: Float): Float {
    val base = 0.3f + 0.55f * cover.pow(1.5f)
    return when (skyWeather(kind)) {
        SkyWeather.Storm -> 1f
        SkyWeather.Rain -> maxOf(base, 0.85f)
        SkyWeather.Snow -> maxOf(base, 0.7f)
        else -> base
    }.coerceIn(0f, 1f)
}

fun sunDirection(azimuth: Double, altitude: Double): FloatArray = floatArrayOf(
    (cos(altitude) * sin(azimuth)).toFloat(),
    sin(altitude).toFloat(),
    (cos(altitude) * cos(azimuth)).toFloat()
)

private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

fun skyStyle(cover: Float): FloatArray {
    val cumulus = floatArrayOf(1.0f, 1.0f, 0.32f, 1.0f)
    val stratocumulus = floatArrayOf(0.7f, 1.5f, 0.40f, 0.8f)
    val stratus = floatArrayOf(0.45f, 0.45f, 0.90f, 0.0f)
    val toStrato = smoothstep(0.5, 0.8, cover.toDouble()).toFloat()
    val toStratus = smoothstep(0.82, 0.95, cover.toDouble()).toFloat()
    return FloatArray(4) { i ->
        val a = cumulus[i] + (stratocumulus[i] - cumulus[i]) * toStrato
        a + (stratus[i] - a) * toStratus
    }
}

fun skyCirrus(cover: Float): Float =
    (0.22 * smoothstep(0.08, 0.30, cover.toDouble()) * (1.0 - smoothstep(0.55, 0.80, cover.toDouble()))).toFloat()

fun buildSkyScene(
    epochMs: Long,
    latitude: Double,
    longitude: Double,
    cover: Float,
    thickness: Float,
    dark: Float,
    fog: Float,
    storm: Float,
    tier: SkyTier
): SkyScene {
    val sunPosition = Solar.position(epochMs, latitude, longitude)
    val sunVector = sunDirection(sunPosition.azimuth, sunPosition.altitude)
    val sx = sunVector[0].toDouble()
    val sy = sunVector[1].toDouble()
    val sz = sunVector[2].toDouble()
    val moon = SkyAstro.moonView(epochMs, latitude, longitude, sunVector)
    val moonAltitudeDeg = Math.toDegrees(moon.altitude)
    val yaw = if (latitude >= 0.0) Math.PI else 0.0
    val pitch = Math.toRadians(CAMERA_PITCH_DEG)
    val forward = floatArrayOf(
        (sin(yaw) * cos(pitch)).toFloat(),
        sin(pitch).toFloat(),
        (cos(yaw) * cos(pitch)).toFloat()
    )
    val right = floatArrayOf(cos(yaw).toFloat(), 0f, (-sin(yaw)).toFloat())
    val up = floatArrayOf(
        (-sin(yaw) * sin(pitch)).toFloat(),
        cos(pitch).toFloat(),
        (-cos(yaw) * sin(pitch)).toFloat()
    )
    val sidereal = SkyAstro.siderealRadians(epochMs, longitude)
    val phi = Math.toRadians(latitude)
    return SkyScene(
        moon = moon,
        moonVisible = moonAltitudeDeg > -3.0,
        skyEquatorial = floatArrayOf(sin(phi).toFloat(), cos(phi).toFloat(), sin(sidereal).toFloat(), cos(sidereal).toFloat()),
        gain = (1.0 + 6.0 * smoothstep(0.0, -12.0, Math.toDegrees(sunPosition.altitude))).toFloat(),
        style = FloatArray(4) { i -> skyStyle(cover)[i] + (STORM_STYLE[i] - skyStyle(cover)[i]) * storm },
        cirrus = skyCirrus(cover) * (1f - storm) * (1f - fog),
        sunTransmittanceHigh = SkyPhysics.sunTransmittance(sy.coerceAtLeast(0.01)),
        sun = sunVector,
        right = right,
        up = up,
        forward = forward,
        sunTransmittance = SkyPhysics.sunTransmittance(sy.coerceAtLeast(0.0)),
        ambient = SkyPhysics.zenithAmbient(doubleArrayOf(sx, sy, sz), max(tier.atmosphereSteps, 4)),
        cover = cover,
        thickness = thickness,
        dark = dark,
        fog = fog
    )
}

fun SkyScene.directionAt(ux: Float, uy: Float): FloatArray {
    val ur = kotlin.math.hypot(ux.toDouble(), uy.toDouble())
    val theta = 2.0 * kotlin.math.atan(ur / (2.0 * 0.5361))
    val spread = if (ur > 1e-6) sin(theta) / ur else 0.0
    val c = cos(theta)
    return FloatArray(3) { i ->
        (forward[i] * c + (right[i] * ux + up[i] * uy) * spread).toFloat()
    }
}
