package com.catsblock.weather.data

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class CloudBand { Clear, Few, Partly, Mostly, Overcast }

fun cloudBand(percent: Int): CloudBand = when {
    percent <= 10 -> CloudBand.Clear
    percent <= 30 -> CloudBand.Few
    percent <= 60 -> CloudBand.Partly
    percent <= 90 -> CloudBand.Mostly
    else -> CloudBand.Overcast
}

object WeatherDecisionEngine {

    private const val HEAVY_RATE = 7.0
    private const val RAIN_RATE = 2.5
    private const val DRIZZLE_RATE = 0.6
    private const val TRACE_RATE = 0.3
    private const val LOW_PRESSURE = 1005.0
    private const val HIGH_PRESSURE = 1022.0
    private const val STORM_PRESSURE = 1000.0
    private const val STORM_WIND = 40.0
    private const val HIGH_UV = 8.0
    private const val SLEET_TEMP = 4.0
    private const val SNOW_TEMP = 0.5
    private const val HISTORY_HOURS = 3L
    private const val PAST_RAIN_MM = 0.5
    private const val FORECAST_HOURS = 36L
    private const val RADAR_HORIZON_HOURS = 6L
    private const val RADAR_MIN_WINDOW = 15
    private const val RADAR_DEFAULT_NOW_WINDOW = 30
    private const val RADAR_DEFAULT_ARRIVAL_WINDOW = 60
    private const val FORECAST_DAYS = 7
    private const val WET_HOUR_MM = 0.1
    private const val WET_DAY_MM = 1.0
    private const val WET_PROB_FLOOR = 75
    private const val DRY_PROB_CEILING = 49
    private const val MIN_WET_HOURS_FOR_DAY = 2

    fun apply(bundle: WeatherBundle, radar: RadarReading? = null, trusted: Boolean? = null): WeatherBundle {
        val current = bundle.current
        val nowHour = current.now.truncatedTo(ChronoUnit.HOURS)
        val hour = bundle.hours.firstOrNull { it.time == nowHour }
        val cloud = when {
            current.cloud >= 0 -> current.cloud
            hour != null && hour.cloud >= 0 -> hour.cloud
            else -> -1
        }
        val resolved = resolve(current, hour, cloud, bundle, radar, trusted)
        val decided = bundle.copy(current = current.copy(kind = resolved, cloud = cloud))
        return reconcileDays(correctHours(decided, nowHour, radar))
    }

    private fun correctHours(bundle: WeatherBundle, nowHour: LocalDateTime, radar: RadarReading?): WeatherBundle {
        val end = nowHour.plusHours(FORECAST_HOURS)
        val hours = bundle.hours.map { hour ->
            when {
                hour.time.isBefore(nowHour) || !hour.time.isBefore(end) -> hour
                hour.time == nowHour -> align(hour, bundle.current.kind)
                else -> {
                    val pseudo = Current(
                        now = hour.time,
                        temp = hour.temp,
                        feels = hour.feels,
                        text = "",
                        kind = hour.kind,
                        isDay = hour.isDay,
                        windSpeed = hour.windSpeed,
                        windDir = hour.windDir,
                        humidity = hour.humidity,
                        uv = hour.uv,
                        aqi = hour.aqi,
                        pressure = hour.pressure,
                        visibility = hour.visibility,
                        cloud = hour.cloud
                    )
                    val view = radar?.let { radarView(it, bundle.current.now, hour.time) }
                    align(hour, resolve(pseudo, hour, hour.cloud, bundle, view, true))
                }
            }
        }
        return bundle.copy(hours = hours)
    }

    private fun radarView(radar: RadarReading, now: LocalDateTime, hourStart: LocalDateTime): RadarReading? {
        val start = ChronoUnit.MINUTES.between(now, hourStart).toDouble()
        if (start + 60.0 <= 0.0 || start >= RADAR_HORIZON_HOURS * 60.0) return null
        val end = start + 60.0
        val track = radar.track
        var overlap = 0.0
        fun consider(from: Double, to: Double) {
            overlap = max(overlap, min(end, to) - max(start, from))
        }
        val covered = track != null && track.onTarget && track.etaMinutes == 0
        if (radar.wet || covered) {
            val length = (track?.takeIf { it.etaMinutes == 0 }?.durationMinutes ?: RADAR_DEFAULT_NOW_WINDOW).coerceAtLeast(RADAR_MIN_WINDOW)
            consider(0.0, length.toDouble())
        }
        if (track != null && track.onTarget) {
            val eta = track.etaMinutes ?: 0
            if (eta > 0) {
                val length = (track.durationMinutes ?: RADAR_DEFAULT_ARRIVAL_WINDOW).coerceAtLeast(RADAR_MIN_WINDOW)
                consider(eta.toDouble(), (eta + length).toDouble())
            }
        }
        return radar.copy(wet = overlap > 0.0)
    }

    private fun align(hour: Hour, kind: Condition): Hour {
        val wasWet = hour.kind.isWet
        if (wasWet == kind.isWet) return hour
        return if (kind.isWet) {
            hour.copy(
                code = codeFor(kind),
                precip = max(hour.precip, WET_HOUR_MM),
                precipProb = max(hour.precipProb, WET_PROB_FLOOR)
            )
        } else {
            hour.copy(
                code = codeFor(kind),
                precipProb = if (hour.precipProb < 0) hour.precipProb else min(hour.precipProb, DRY_PROB_CEILING)
            )
        }
    }

    private fun codeFor(kind: Condition): Int = when (kind) {
        Condition.Clear -> 0
        Condition.FewClouds -> 1
        Condition.PartlyCloudy, Condition.MoreClouds -> 2
        Condition.Drizzle -> 61
        Condition.Rain -> 63
        Condition.HeavyRain -> 65
        Condition.Storm -> 97
        Condition.Sleet -> 66
        Condition.Snow -> 73
        Condition.HeavySnow, Condition.Blizzard -> 75
        Condition.Hail -> 77
        else -> if (kind.isWet) 63 else 3
    }

    private fun isWetCode(code: Int): Boolean =
        code in 51..67 || code in 71..77 || code in 80..86 || code in 95..99

    private fun reconcileDays(bundle: WeatherBundle): WeatherBundle {
        val today = bundle.current.now.toLocalDate()
        val covered = bundle.days.filter { !it.date.isBefore(today) }.take(FORECAST_DAYS).map { it.date }.toSet()
        val days = bundle.days.map { day ->
            if (day.date in covered) reconcileDay(day, bundle.hours.filter { it.time.toLocalDate() == day.date }) else day
        }
        return bundle.copy(days = days)
    }

    private fun reconcileDay(day: Day, hours: List<Hour>): Day {
        if (hours.isEmpty()) return day
        val wetHours = hours.filter { it.precip >= WET_HOUR_MM || isWetCode(it.code) }
        val hoursWet = wetHours.size >= MIN_WET_HOURS_FOR_DAY || wetHours.any { it.precip >= RAIN_RATE }
        val dayWet = day.kind.isWet
        return when {
            !dayWet && hoursWet -> {
                val codes = wetHours.map { it.code }.filter { isWetCode(it) }
                val code = codes.groupingBy { it }.eachCount().entries
                    .maxWithOrNull(compareBy<Map.Entry<Int, Int>>({ it.value }, { it.key }))?.key ?: 61
                day.copy(
                    code = code,
                    precipSum = max(day.precipSum, wetHours.sumOf { it.precip }),
                    rainChance = max(day.rainChance, wetHours.maxOf { it.precipProb })
                )
            }
            dayWet && wetHours.isEmpty() && day.precipSum < WET_DAY_MM -> {
                val clouds = hours.map { it.cloud }.filter { it >= 0 }
                val cloud = if (clouds.isEmpty()) -1 else clouds.average().toInt()
                day.copy(
                    code = when {
                        cloud < 0 -> 2
                        cloud >= 85 -> 3
                        cloud >= 50 -> 2
                        cloud >= 20 -> 1
                        else -> 0
                    },
                    rainChance = min(day.rainChance, DRY_PROB_CEILING)
                )
            }
            else -> day
        }
    }

    fun resolve(current: Current, hour: Hour?, cloud: Int, bundle: WeatherBundle? = null, radar: RadarReading? = null, trusted: Boolean? = null): Condition {
        val base = current.kind
        val rate = hour?.precip ?: 0.0
        if (base == Condition.Storm) return Condition.Storm
        if (base.isFixed) return base
        if (base == Condition.HeavySnow || base == Condition.Blizzard || base == Condition.Hail || base == Condition.Fog) return base
        val nextRate = bundle?.let { nextHourRate(current, it) } ?: 0.0
        val past = bundle?.let { pastRain(current, it) } ?: 0.0
        val radarRate = radar?.takeIf { it.wet }?.let { if (it.cityDbz >= 15.0) reflectivityToRate(it.cityDbz) else it.strength * 6.0 } ?: 0.0
        val radarClear = radar != null && !radar.wet && radar.track == null
        val intensity = max(rate, max(nextRate, radarRate))
        return when (base) {
            Condition.Snow -> if (current.temp >= SLEET_TEMP && rate > 0.0) Condition.Sleet else base
            Condition.Sleet -> base
            Condition.Drizzle, Condition.Rain -> {
                val noPrecipitation = rate < TRACE_RATE && nextRate < DRIZZLE_RATE
                val dryEvidence = radar?.wet != true && noPrecipitation && (trusted != true || radarClear)
                if (dryEvidence) dryKind(Condition.MoreClouds, 0.0, cloud, current) else wetKind(base, max(rate, intensity), current, radar)
            }
            else -> {
                val wet = radar?.wet == true || (trusted != false && precipitationEvidence(rate, nextRate, past))
                if (wet) wetKind(Condition.Rain, intensity, current, radar) else dryKind(base, rate, cloud, current)
            }
        }
    }

    private fun precipitationEvidence(rate: Double, nextRate: Double, past: Double): Boolean =
        rate >= DRIZZLE_RATE || (rate >= TRACE_RATE && (nextRate >= TRACE_RATE || past >= PAST_RAIN_MM))

    private fun nextHourRate(current: Current, bundle: WeatherBundle): Double {
        val next = current.now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        return bundle.hours.firstOrNull { it.time == next }?.precip ?: 0.0
    }

    private fun pastRain(current: Current, bundle: WeatherBundle): Double {
        val nowHour = current.now.truncatedTo(ChronoUnit.HOURS)
        val from = nowHour.minusHours(HISTORY_HOURS)
        return bundle.hours.filter { !it.time.isBefore(from) && it.time.isBefore(nowHour) }.sumOf { it.precip }
    }

    private fun wetKind(base: Condition, rate: Double, current: Current, radar: RadarReading?): Condition {
        val stormy = current.pressure < STORM_PRESSURE && current.windSpeed >= STORM_WIND
        return when {
            current.temp <= SNOW_TEMP && rate > 0.0 -> Condition.Snow
            current.temp <= SLEET_TEMP && current.temp > SNOW_TEMP && rate > 0.0 && base == Condition.Rain -> Condition.Sleet
            stormy && (rate >= HEAVY_RATE || (radar?.strength ?: 0.0) >= 0.85) -> Condition.Storm
            rate >= RAIN_RATE -> Condition.Rain
            rate > 0.0 && rate <= DRIZZLE_RATE -> Condition.Drizzle
            rate > DRIZZLE_RATE -> Condition.Rain
            else -> if (base == Condition.Drizzle) Condition.Drizzle else Condition.Rain
        }
    }

    private fun dryKind(base: Condition, rate: Double, cloud: Int, current: Current): Condition {
        if (rate >= TRACE_RATE) return Condition.Drizzle
        if (cloud < 0) return base
        var effective = cloud
        if (current.pressure < LOW_PRESSURE) effective += 6
        if (current.pressure > HIGH_PRESSURE) effective -= 6
        if (current.isDay && current.uv >= HIGH_UV) effective -= 8
        effective = effective.coerceIn(0, 100)
        return when {
            effective <= 12 -> Condition.Clear
            effective <= 37 -> Condition.FewClouds
            effective <= 55 -> Condition.PartlyCloudy
            effective <= 70 -> Condition.MoreClouds
            else -> Condition.Cloudy
        }
    }

    fun coverFor(kind: Condition): Float = when (kind) {
        Condition.Clear -> 0.04f
        Condition.FewClouds -> 0.2f
        Condition.PartlyCloudy -> 0.35f
        Condition.MoreClouds -> 0.6f
        Condition.Cloudy -> 0.9f
        Condition.Fog -> 0.8f
        Condition.Drizzle -> 0.85f
        Condition.Rain -> 0.95f
        Condition.Storm -> 1f
        else -> 0.9f
    }

    fun altocumulusChance(cover: Float, dayOfYear: Int): Float {
        val shape = (1f - abs(cover - 0.5f) * 2f).coerceIn(0f, 1f)
        val variation = 0.55f + 0.45f * sin(dayOfYear * 12.9898f)
        return (shape * variation * 1.2f).coerceIn(0f, 1f)
    }

    fun timeOfDay(minutes: Int): Float = when {
        minutes <= 540 -> 0f
        minutes <= 660 -> 0.5f * (minutes - 540) / 120f
        minutes <= 900 -> 0.5f
        minutes <= 1050 -> 0.5f + 0.5f * (minutes - 900) / 150f
        else -> 1f
    }
}
