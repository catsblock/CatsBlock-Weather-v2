package com.catsblock.weather.alerts

import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.Moon
import com.catsblock.weather.data.WeatherBundle
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

enum class EclipseType { Lunar, Solar }

class EclipseEvent(val type: EclipseType, val startMs: Long)

object EclipseForecast {

    private const val RAD = PI / 180.0
    private const val EARTH_KM = 6378.14
    private const val AU_KM = 149597870.7
    private const val MOON_KM = 1737.4
    private const val SUN_KM = 695700.0
    private const val OBLIQUITY = RAD * 23.4397
    private const val STEP_MS = 5 * 60_000L
    private const val HORIZON_DAYS = 120

    private fun jd(ms: Long): Double = ms / 86400000.0 + 2440587.5

    private fun equatorial(lonDeg: Double, latDeg: Double, dist: Double): DoubleArray {
        val l = lonDeg * RAD
        val b = latDeg * RAD
        val x = cos(b) * cos(l)
        val y = cos(b) * sin(l)
        val z = sin(b)
        return doubleArrayOf(
            x * dist,
            (y * cos(OBLIQUITY) - z * sin(OBLIQUITY)) * dist,
            (y * sin(OBLIQUITY) + z * cos(OBLIQUITY)) * dist
        )
    }

    private fun angle(a: DoubleArray, b: DoubleArray): Double {
        val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val na = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        val nb = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2])
        return acos((dot / (na * nb)).coerceIn(-1.0, 1.0))
    }

    /** True while the Moon covers part of the Sun for an observer at (lat, lon) and the Sun is up. */
    fun solarVisible(ms: Long, lat: Double, lon: Double): Boolean {
        val d = jd(ms) - 2451545.0
        val t = d / 36525.0
        val lp = 218.3164477 + 481267.88123421 * t
        val dd = RAD * (297.8501921 + 445267.1114034 * t)
        val m = RAD * (357.5291092 + 35999.0502909 * t)
        val mp = RAD * (134.9633964 + 477198.8675055 * t)
        val f = RAD * (93.2720950 + 483202.0175233 * t)
        val moonLon = lp + 6.288774 * sin(mp) + 1.274027 * sin(2 * dd - mp) + 0.658314 * sin(2 * dd) +
            0.213618 * sin(2 * mp) - 0.185116 * sin(m) - 0.114332 * sin(2 * f) +
            0.058793 * sin(2 * dd - 2 * mp) + 0.057066 * sin(2 * dd - m - mp) +
            0.053322 * sin(2 * dd + mp) + 0.045758 * sin(2 * dd - m)
        val moonLat = 5.128122 * sin(f) + 0.280602 * sin(mp + f) + 0.277693 * sin(mp - f) +
            0.173237 * sin(2 * dd - f) + 0.055413 * sin(2 * dd - mp + f) +
            0.046271 * sin(2 * dd - mp - f) + 0.032573 * sin(2 * dd + f)
        val par = 0.950724 + 0.051818 * cos(mp) + 0.009531 * cos(2 * dd - mp) +
            0.007843 * cos(2 * dd) + 0.002824 * cos(2 * mp)
        val sunLon = 280.46646 + 36000.76983 * t + 1.914602 * sin(m) + 0.019993 * sin(2 * m)
        val sr = 1.000140612 - 0.016708617 * cos(m) - 0.000139589 * cos(2 * m)

        // Quick global rejection: Moon must be near the Sun in the sky.
        val dl = ((sunLon - moonLon) % 360.0 + 540.0) % 360.0 - 180.0
        if (hypot(dl * cos(moonLat * RAD), moonLat) > 2.0) return false

        val moonVec = equatorial(moonLon, moonLat, EARTH_KM / sin(par * RAD))
        val sunVec = equatorial(sunLon, 0.0, sr * AU_KM)

        val gmst = (280.46061837 + 360.98564736629 * d) % 360.0
        val theta = (gmst + lon) * RAD
        val phi = lat * RAD
        val zenith = doubleArrayOf(cos(phi) * cos(theta), cos(phi) * sin(theta), sin(phi))
        val observer = doubleArrayOf(zenith[0] * EARTH_KM, zenith[1] * EARTH_KM, zenith[2] * EARTH_KM)

        val moonTopo = doubleArrayOf(moonVec[0] - observer[0], moonVec[1] - observer[1], moonVec[2] - observer[2])
        val sunTopo = doubleArrayOf(sunVec[0] - observer[0], sunVec[1] - observer[1], sunVec[2] - observer[2])

        val sunNorm = sqrt(sunTopo[0] * sunTopo[0] + sunTopo[1] * sunTopo[1] + sunTopo[2] * sunTopo[2])
        val sinAlt = (sunTopo[0] * zenith[0] + sunTopo[1] * zenith[1] + sunTopo[2] * zenith[2]) / sunNorm
        if (asin(sinAlt.coerceIn(-1.0, 1.0)) < -0.8333 * RAD) return false

        val moonNorm = sqrt(moonTopo[0] * moonTopo[0] + moonTopo[1] * moonTopo[1] + moonTopo[2] * moonTopo[2])
        val rMoon = asin((MOON_KM / moonNorm).coerceIn(-1.0, 1.0))
        val rSun = asin((SUN_KM / sunNorm).coerceIn(-1.0, 1.0))
        return angle(moonTopo, sunTopo) < rMoon + rSun
    }

    /** True while a partial or total lunar eclipse is happening and the Moon is above the horizon. */
    fun lunarVisible(ms: Long, lat: Double, lon: Double): Boolean {
        val e = Moon.eclipse(ms) ?: return false
        if (e.kind < 1) return false
        return Moon.position(ms, lat, lon).altitude > 0.0
    }

    /** First eclipse that will begin after [nowMs] and can be seen from the given place. */
    fun next(nowMs: Long, lat: Double, lon: Double): EclipseEvent? {
        var lunarBefore = lunarVisible(nowMs, lat, lon)
        var solarBefore = solarVisible(nowMs, lat, lon)
        val end = nowMs + HORIZON_DAYS * 86_400_000L
        var t = nowMs + STEP_MS
        while (t <= end) {
            val lunar = lunarVisible(t, lat, lon)
            if (lunar && !lunarBefore) return EclipseEvent(EclipseType.Lunar, t)
            val solar = solarVisible(t, lat, lon)
            if (solar && !solarBefore) return EclipseEvent(EclipseType.Solar, t)
            lunarBefore = lunar
            solarBefore = solar
            t += STEP_MS
        }
        return null
    }
}

object SnowForecast {

    private const val SLOT_MM = 0.05

    private fun snowy(kind: Condition): Boolean =
        kind == Condition.Snow || kind == Condition.HeavySnow || kind == Condition.Blizzard

    private fun kindAt(bundle: WeatherBundle, time: LocalDateTime): Condition {
        val key = time.minusMinutes(1).truncatedTo(ChronoUnit.HOURS)
        return bundle.hours.firstOrNull { it.time == key }?.kind ?: bundle.current.kind
    }

    fun nextStart(bundle: WeatherBundle): LocalDateTime? {
        val now = bundle.current.now
        var previous = snowy(bundle.current.kind)

        if (bundle.rain.isNotEmpty()) {
            val upcoming = bundle.rain.filter { it.time.isAfter(now) }
            upcoming.forEach { slot ->
                val falling = slot.mm >= SLOT_MM && snowy(kindAt(bundle, slot.time))
                if (falling && !previous) {
                    val start = slot.time.minusMinutes(15)
                    return if (start.isBefore(now)) now else start
                }
                previous = falling
            }
            return null
        }

        bundle.hours.filter { it.time.isAfter(now) }.forEach { hour ->
            val falling = snowy(hour.kind)
            if (falling && !previous) {
                val start = hour.time.minusMinutes(30)
                return if (start.isBefore(now)) now else start
            }
            previous = falling
        }
        return null
    }

    fun nextStartMillis(bundle: WeatherBundle): Long? =
        nextStart(bundle)?.toEpochSecond(ZoneOffset.ofTotalSeconds(bundle.utcOffsetSeconds))?.times(1000L)
}
