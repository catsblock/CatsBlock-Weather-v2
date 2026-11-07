package com.catsblock.weather.desktop

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

data class MoonDay(val rise: LocalTime?, val transit: LocalTime?, val set: LocalTime?)

data class SunTimes(val noon: LocalTime, val firstLight: LocalTime?, val lastLight: LocalTime?)

object Ephemeris {

    private const val RAD = PI / 180.0
    private const val DAY_MS = 86_400_000.0
    private const val J1970 = 2_440_588.0
    private const val J2000 = 2_451_545.0
    private const val OBLIQUITY = RAD * 23.4397
    private const val SYNODIC_DAYS = 29.530588853
    private const val CIVIL_ZENITH = 96.0

    private class Coords(val ra: Double, val dec: Double, val dist: Double)

    private fun days(ms: Long): Double = ms / DAY_MS - 0.5 + J1970 - J2000

    private fun rightAscension(l: Double, b: Double): Double =
        atan2(sin(l) * cos(OBLIQUITY) - tan(b) * sin(OBLIQUITY), cos(l))

    private fun declination(l: Double, b: Double): Double =
        asin(sin(b) * cos(OBLIQUITY) + cos(b) * sin(OBLIQUITY) * sin(l))

    private fun sun(d: Double): Coords {
        val m = RAD * (357.5291 + 0.98560028 * d)
        val c = RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m))
        val l = m + c + RAD * 102.9372 + PI
        return Coords(rightAscension(l, 0.0), declination(l, 0.0), 149_598_000.0)
    }

    private fun moon(d: Double): Coords {
        val l = RAD * (218.316 + 13.176396 * d)
        val m = RAD * (134.963 + 13.064993 * d)
        val f = RAD * (93.272 + 13.229350 * d)
        val lon = l + RAD * 6.289 * sin(m)
        val lat = RAD * 5.128 * sin(f)
        return Coords(rightAscension(lon, lat), declination(lon, lat), 385_001.0 - 20_905.0 * cos(m))
    }

    private fun moonAltitude(ms: Long, lat: Double, lon: Double): Double {
        val d = days(ms)
        val c = moon(d)
        val h = RAD * (280.16 + 360.9856235 * d) + RAD * lon - c.ra
        val phi = RAD * lat
        var alt = asin(sin(phi) * sin(c.dec) + cos(phi) * cos(c.dec) * cos(h))
        val clamped = if (alt < 0) 0.0 else alt
        alt += 0.0002967 / tan(clamped + 0.00312536 / (clamped + 0.08901179))
        return alt
    }

    fun phaseFraction(ms: Long): Double {
        val d = days(ms)
        val s = sun(d)
        val m = moon(d)
        val phi = acos((sin(s.dec) * sin(m.dec) + cos(s.dec) * cos(m.dec) * cos(s.ra - m.ra)).coerceIn(-1.0, 1.0))
        val inc = atan2(s.dist * sin(phi), m.dist - s.dist * cos(phi))
        val angle = atan2(
            cos(s.dec) * sin(s.ra - m.ra),
            sin(s.dec) * cos(m.dec) - cos(s.dec) * sin(m.dec) * cos(s.ra - m.ra)
        )
        val sign = if (angle < 0) -1.0 else 1.0
        return (0.5 + 0.5 * inc * sign / PI).coerceIn(0.0, 1.0)
    }

    fun illumination(fraction: Double): Double = (1 - cos(2 * PI * fraction)) / 2

    fun ageDays(fraction: Double): Double = fraction * SYNODIC_DAYS

    fun nextPhaseMs(fromMs: Long, target: Double): Long? {
        fun gap(ms: Long): Double = ((phaseFraction(ms) - target + 1.5) % 1.0) - 0.5
        val step = 3L * 3_600_000L
        var previous = gap(fromMs)
        var t = fromMs
        while (t < fromMs + 32L * 86_400_000L) {
            val next = t + step
            val current = gap(next)
            if (previous < 0.0 && current >= 0.0 && current - previous < 0.5) {
                var lo = t
                var hi = next
                repeat(24) {
                    val mid = (lo + hi) / 2
                    if (gap(mid) < 0.0) lo = mid else hi = mid
                }
                return hi
            }
            previous = current
            t = next
        }
        return null
    }

    private fun localTime(ms: Long, offsetSeconds: Int): LocalTime =
        Instant.ofEpochMilli(ms).atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds)).toLocalTime()

    fun moonDay(date: LocalDate, lat: Double, lon: Double, offsetSeconds: Int): MoonDay {
        val start = date.atStartOfDay().toEpochSecond(ZoneOffset.ofTotalSeconds(offsetSeconds)) * 1000L
        val step = 300_000L
        val horizon = 0.133 * RAD
        var rise: LocalTime? = null
        var set: LocalTime? = null
        var best = Double.NEGATIVE_INFINITY
        var bestIndex = 0
        var previous = moonAltitude(start, lat, lon)
        if (previous > best) best = previous
        for (i in 1..288) {
            val altitude = moonAltitude(start + i * step, lat, lon)
            val before = previous - horizon
            val after = altitude - horizon
            if (rise == null && before < 0 && after >= 0) {
                rise = localTime(start + (i - 1) * step + (step * (-before / (after - before))).toLong(), offsetSeconds)
            }
            if (set == null && before >= 0 && after < 0) {
                set = localTime(start + (i - 1) * step + (step * (before / (before - after))).toLong(), offsetSeconds)
            }
            if (altitude > best) {
                best = altitude
                bestIndex = i
            }
            previous = altitude
        }
        var transit: LocalTime? = null
        if (bestIndex in 1..287) {
            val a = moonAltitude(start + (bestIndex - 1) * step, lat, lon)
            val c = moonAltitude(start + (bestIndex + 1) * step, lat, lon)
            val denominator = a - 2 * best + c
            val shift = if (denominator == 0.0) 0.0 else (0.5 * (a - c) / denominator).coerceIn(-1.0, 1.0)
            transit = localTime(start + ((bestIndex + shift) * step).toLong(), offsetSeconds)
        }
        return MoonDay(rise, transit, set)
    }

    fun sunTimes(date: LocalDate, lat: Double, lon: Double, offsetSeconds: Int): SunTimes {
        val midday = date.atTime(12, 0).toEpochSecond(ZoneOffset.ofTotalSeconds(offsetSeconds)) * 1000L
        val jd = midday / DAY_MS + J1970
        val t = (jd - J2000) / 36525.0
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mr = m * RAD
        val c = sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(2 * mr) * (0.019993 - 0.000101 * t) + sin(3 * mr) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val lambda = l0 + c - 0.00569 - 0.00478 * sin(omega * RAD)
        val obliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0 + 0.00256 * cos(omega * RAD)
        val dec = asin(sin(obliquity * RAD) * sin(lambda * RAD))
        val y = tan(obliquity * RAD / 2.0).pow(2)
        val l0r = l0 * RAD
        val equation = 4.0 * (y * sin(2 * l0r) - 2 * e * sin(mr) + 4 * e * y * sin(mr) * cos(2 * l0r) -
            0.5 * y * y * sin(4 * l0r) - 1.25 * e * e * sin(2 * mr)) / RAD
        val noonMinutes = 720.0 - 4.0 * lon - equation + offsetSeconds / 60.0
        val latRad = lat * RAD
        val cosH = cos(CIVIL_ZENITH * RAD) / (cos(latRad) * cos(dec)) - tan(latRad) * tan(dec)
        val half = if (cosH in -1.0..1.0) acos(cosH) / RAD * 4.0 else null
        fun clock(minutes: Double): LocalTime {
            val wrapped = ((minutes % 1440.0) + 1440.0) % 1440.0
            return LocalTime.ofSecondOfDay((wrapped * 60.0).toLong().coerceIn(0L, 86_399L))
        }
        return SunTimes(clock(noonMinutes), half?.let { clock(noonMinutes - it) }, half?.let { clock(noonMinutes + it) })
    }
}

fun cityOffsetSeconds(cityNow: LocalDateTime): Int {
    val delta = cityNow.toEpochSecond(ZoneOffset.UTC) - Instant.now().epochSecond
    return (delta / 900.0).roundToInt() * 900
}
