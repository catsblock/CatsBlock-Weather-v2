package com.catsblock.weather.ui.sky

import com.catsblock.weather.data.Moon
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

const val MOON_VISUAL_SCALE = 14.0
private const val MOON_RADIUS_KM = 1737.4
private const val OBLIQUITY_DEG = 23.4397

class MoonView(
    val altitude: Double,
    val azimuth: Double,
    val direction: FloatArray,
    val right: FloatArray,
    val up: FloatArray,
    val radiusTan: Float,
    val cosTheta: Float,
    val sinTheta: Float,
    val light: FloatArray,
    val earthshine: Float,
    val halo: Float,
    val transmittance: FloatArray,
    val moonlight: FloatArray,
    val eclipse: FloatArray
)

internal object SkyAstro {

    fun siderealRadians(epochMs: Long, lonDeg: Double): Double {
        val jd = epochMs / 86400000.0 + 2440587.5
        val degrees = 280.46061837 + 360.98564736629 * (jd - 2451545.0) + lonDeg
        return Math.toRadians(degrees.mod(360.0))
    }

    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun normalize(a: DoubleArray): DoubleArray {
        val l = sqrt(dot(a, a)).coerceAtLeast(1e-9)
        return doubleArrayOf(a[0] / l, a[1] / l, a[2] / l)
    }

    private fun cross(a: DoubleArray, b: DoubleArray): DoubleArray = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0]
    )

    private fun floats(a: DoubleArray) = floatArrayOf(a[0].toFloat(), a[1].toFloat(), a[2].toFloat())

    fun moonView(epochMs: Long, latDeg: Double, lonDeg: Double, sun: FloatArray): MoonView {
        val position = Moon.position(epochMs, latDeg, lonDeg)
        val altitude = position.altitude
        val azimuth = position.azimuth
        val dir = doubleArrayOf(
            cos(altitude) * sin(azimuth),
            sin(altitude),
            cos(altitude) * cos(azimuth)
        )
        val equatorial = Moon.equatorial(epochMs)
        val ra = equatorial[0]
        val dec = equatorial[1]
        val hourAngle = siderealRadians(epochMs, lonDeg) - ra
        val phi = Math.toRadians(latDeg.coerceIn(-89.9, 89.9))
        val parallactic = atan2(sin(hourAngle), tan(phi) * cos(dec) - sin(dec) * cos(hourAngle))
        val poleRa = Math.toRadians(270.0)
        val poleDec = Math.toRadians(90.0 - OBLIQUITY_DEG)
        val dRa = poleRa - ra
        val positionAngle = atan2(
            cos(poleDec) * sin(dRa),
            sin(poleDec) * cos(dec) - cos(poleDec) * sin(dec) * cos(dRa)
        )
        val theta = parallactic - positionAngle

        val zenith = doubleArrayOf(0.0, 1.0, 0.0)
        val projection = dot(zenith, dir)
        var up = doubleArrayOf(zenith[0] - dir[0] * projection, zenith[1] - dir[1] * projection, zenith[2] - dir[2] * projection)
        if (dot(up, up) < 1e-8) up = doubleArrayOf(0.0, 0.0, 1.0)
        up = normalize(up)
        val right = normalize(cross(up, dir))

        val s = doubleArrayOf(sun[0].toDouble(), sun[1].toDouble(), sun[2].toDouble())
        val illuminated = Moon.phase(epochMs).illumination.coerceIn(0.0, 1.0)
        val cosPhase = 2.0 * illuminated - 1.0
        val sinPhase = sqrt((1.0 - cosPhase * cosPhase).coerceAtLeast(0.0))
        val limbX = dot(s, right)
        val limbY = dot(s, up)
        val limbLength = sqrt(limbX * limbX + limbY * limbY).coerceAtLeast(1e-9)
        val light = doubleArrayOf(limbX / limbLength * sinPhase, limbY / limbLength * sinPhase, cosPhase)

        val distance = Moon.distanceKm(epochMs)
        val angularRadius = asin(MOON_RADIUS_KM / distance)
        val radiusTan = tan(angularRadius * MOON_VISUAL_SCALE)

        val altitudeDeg = Math.toDegrees(altitude)
        val horizonFade = smoothstep(-3.0, 5.0, altitudeDeg)
        val transmittance = SkyPhysics.sunTransmittance(sin(altitude).coerceAtLeast(0.0))
        val moonlight = FloatArray(3) { (transmittance[it] * illuminated * 0.006 * horizonFade).toFloat() }

        val eclipse = Moon.eclipse(epochMs)
        val shadow = if (eclipse != null) {
            val cq = cos(parallactic)
            val sq = sin(parallactic)
            floatArrayOf(
                (eclipse.dx * cq + eclipse.dy * sq).toFloat(),
                (-eclipse.dx * sq + eclipse.dy * cq).toFloat(),
                eclipse.umbra.toFloat(),
                eclipse.penumbra.toFloat()
            )
        } else {
            floatArrayOf(0f, 0f, 0f, 0f)
        }

        return MoonView(
            altitude = altitude,
            azimuth = azimuth,
            direction = floats(dir),
            right = floats(right),
            up = floats(up),
            radiusTan = radiusTan.toFloat(),
            cosTheta = cos(theta).toFloat(),
            sinTheta = sin(theta).toFloat(),
            light = floats(light),
            earthshine = (0.012 * (1.0 - illuminated)).toFloat(),
            halo = (illuminated * horizonFade).toFloat(),
            transmittance = transmittance,
            moonlight = moonlight,
            eclipse = shadow
        )
    }
}
