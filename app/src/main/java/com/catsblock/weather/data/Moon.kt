package com.catsblock.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class MoonPhase(val cycle: Double, val illumination: Double)

data class MoonPosition(val azimuth: Double, val altitude: Double)

object Moon {

    private const val RAD = PI / 180.0
    private const val DAY_MS = 86400000.0
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private const val OBLIQUITY = RAD * 23.4397

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
        return Coords(rightAscension(l, 0.0), declination(l, 0.0), 149598000.0)
    }

    private fun moon(d: Double): Coords {
        val l = RAD * (218.316 + 13.176396 * d)
        val m = RAD * (134.963 + 13.064993 * d)
        val f = RAD * (93.272 + 13.229350 * d)
        val lon = l + RAD * 6.289 * sin(m)
        val lat = RAD * 5.128 * sin(f)
        return Coords(rightAscension(lon, lat), declination(lon, lat), 385001.0 - 20905.0 * cos(m))
    }

    fun phase(ms: Long): MoonPhase {
        val d = days(ms)
        val s = sun(d)
        val m = moon(d)
        val phi = acos(sin(s.dec) * sin(m.dec) + cos(s.dec) * cos(m.dec) * cos(s.ra - m.ra))
        val inc = atan2(s.dist * sin(phi), m.dist - s.dist * cos(phi))
        val angle = atan2(
            cos(s.dec) * sin(s.ra - m.ra),
            sin(s.dec) * cos(m.dec) - cos(s.dec) * sin(m.dec) * cos(s.ra - m.ra)
        )
        val sign = if (angle < 0) -1.0 else 1.0
        return MoonPhase((0.5 + 0.5 * inc * sign / PI).coerceIn(0.0, 1.0), (1 + cos(inc)) / 2)
    }

    fun position(ms: Long, lat: Double, lon: Double): MoonPosition {
        val d = days(ms)
        val c = moon(d)
        val h = RAD * (280.16 + 360.9856235 * d) + RAD * lon - c.ra
        val phi = RAD * lat
        var alt = asin(sin(phi) * sin(c.dec) + cos(phi) * cos(c.dec) * cos(h))
        val clamped = if (alt < 0) 0.0 else alt
        alt += 0.0002967 / tan(clamped + 0.00312536 / (clamped + 0.08901179))
        val az = atan2(sin(h), cos(h) * sin(phi) - tan(c.dec) * cos(phi)) + PI
        return MoonPosition(az, alt)
    }

    private fun at(ms: Long, offset: ZoneOffset): LocalTime =
        Instant.ofEpochMilli(ms).atOffset(offset).toLocalTime()

    fun riseSet(date: LocalDate, lat: Double, lon: Double, offset: ZoneOffset): Pair<LocalTime?, LocalTime?> {
        val start = date.atStartOfDay().toInstant(offset).toEpochMilli()
        val step = 600_000L
        val horizon = 0.133 * RAD
        var rise: LocalTime? = null
        var set: LocalTime? = null
        var prev = position(start, lat, lon).altitude - horizon
        for (i in 1..144) {
            val t = start + i * step
            val cur = position(t, lat, lon).altitude - horizon
            if (rise == null && prev < 0 && cur >= 0) {
                rise = at(start + (i - 1) * step + (step * (-prev / (cur - prev))).toLong(), offset)
            }
            if (set == null && prev >= 0 && cur < 0) {
                set = at(start + (i - 1) * step + (step * (prev / (prev - cur))).toLong(), offset)
            }
            prev = cur
        }
        return rise to set
    }

    fun phaseName(cycle: Double): String = when {
        cycle < 0.03 || cycle >= 0.97 -> "New moon"
        cycle < 0.22 -> "Waxing crescent"
        cycle < 0.28 -> "First quarter"
        cycle < 0.47 -> "Waxing gibbous"
        cycle < 0.53 -> "Full moon"
        cycle < 0.72 -> "Waning gibbous"
        cycle < 0.78 -> "Last quarter"
        else -> "Waning crescent"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, String>()

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneOffset.UTC)

    suspend fun imageUrl(ms: Long): String? {
        val key = stamp.format(Instant.ofEpochMilli(ms / 3_600_000L * 3_600_000L))
        cache[key]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url("https://svs.gsfc.nasa.gov/api/dialamoon/$key").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        null
                    } else {
                        val url = JSONObject(response.body?.string().orEmpty())
                            .getJSONObject("image")
                            .getString("url")
                        cache[key] = url
                        url
                    }
                }
            }.getOrNull()
        }
    }
}
