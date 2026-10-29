package com.catsblock.weather.data

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

class SolarDay(
    val date: LocalDate,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val noon: LocalDateTime,
    val firstLight: LocalDateTime?,
    val lastLight: LocalDateTime?,
    val noonElevation: Double,
    val sunsetAzimuth: Double?,
    val daylightMinutes: Int
)

object Solar {

    private const val RAD = PI / 180.0

    private class Terms(val declination: Double, val equationOfTime: Double)

    private fun terms(jd: Double): Terms {
        val t = (jd - 2451545.0) / 36525.0
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mr = m * RAD
        val c = sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mr) * (0.019993 - 0.000101 * t) +
            sin(3 * mr) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val lambda = l0 + c - 0.00569 - 0.00478 * sin(omega * RAD)
        val obliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0 +
            0.00256 * cos(omega * RAD)
        val declination = asin(sin(obliquity * RAD) * sin(lambda * RAD))
        val y = tan(obliquity * RAD / 2.0).pow(2)
        val l0r = l0 * RAD
        val equation = 4.0 * (
            y * sin(2 * l0r) - 2 * e * sin(mr) + 4 * e * y * sin(mr) * cos(2 * l0r) -
                0.5 * y * y * sin(4 * l0r) - 1.25 * e * e * sin(2 * mr)
            ) / RAD
        return Terms(declination, equation)
    }

    private fun halfDayMinutes(latRad: Double, declination: Double, zenith: Double): Double? {
        val cosH = cos(zenith * RAD) / (cos(latRad) * cos(declination)) - tan(latRad) * tan(declination)
        return if (cosH in -1.0..1.0) acos(cosH) / RAD * 4.0 else null
    }

    fun day(date: LocalDate, lat: Double, lon: Double, offsetSeconds: Int): SolarDay {
        val base = date.toEpochDay() + 2440587.5
        val first = terms(base + 0.5 - lon / 360.0)
        val approx = 720.0 - 4.0 * lon - first.equationOfTime
        val refined = terms(base + approx / 1440.0)
        val noonMinutes = 720.0 - 4.0 * lon - refined.equationOfTime
        val declination = refined.declination
        val latRad = lat * RAD

        fun at(minutes: Double): LocalDateTime =
            date.atStartOfDay().plusSeconds((minutes * 60.0).roundToLong() + offsetSeconds)

        val cosHorizon = cos(90.833 * RAD) / (cos(latRad) * cos(declination)) - tan(latRad) * tan(declination)
        val half = halfDayMinutes(latRad, declination, 90.833)
        val daylight = when {
            half != null -> (half * 2.0).roundToInt()
            cosHorizon < -1.0 -> 1440
            else -> 0
        }
        val civil = halfDayMinutes(latRad, declination, 96.0)
        val cosAzimuth = sin(declination) / cos(latRad)
        val azimuth = if (abs(cosAzimuth) <= 1.0) 360.0 - acos(cosAzimuth) / RAD else null
        return SolarDay(
            date = date,
            sunrise = half?.let { at(noonMinutes - it) },
            sunset = half?.let { at(noonMinutes + it) },
            noon = at(noonMinutes),
            firstLight = civil?.let { at(noonMinutes - it) },
            lastLight = civil?.let { at(noonMinutes + it) },
            noonElevation = 90.0 - abs(lat - declination / RAD),
            sunsetAzimuth = azimuth,
            daylightMinutes = daylight
        )
    }
}
