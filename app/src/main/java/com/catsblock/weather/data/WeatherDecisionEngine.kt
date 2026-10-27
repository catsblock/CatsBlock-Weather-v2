package com.catsblock.weather.data

import java.time.temporal.ChronoUnit
import kotlin.math.abs
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

    fun apply(bundle: WeatherBundle): WeatherBundle {
        val current = bundle.current
        val nowHour = current.now.truncatedTo(ChronoUnit.HOURS)
        val hour = bundle.hours.firstOrNull { it.time == nowHour }
        val cloud = when {
            current.cloud >= 0 -> current.cloud
            hour != null && hour.cloud >= 0 -> hour.cloud
            else -> -1
        }
        val resolved = resolve(current, hour, cloud)
        return bundle.copy(current = current.copy(kind = resolved, cloud = cloud))
    }

    fun resolve(current: Current, hour: Hour?, cloud: Int): Condition {
        val base = current.kind
        val rate = hour?.precip ?: 0.0
        return when (base) {
            Condition.Storm -> Condition.Storm
            Condition.Drizzle, Condition.Rain -> wetKind(base, rate, current)
            Condition.Snow -> if (current.temp >= SLEET_TEMP && rate > 0.0) Condition.Sleet else base
            Condition.HeavySnow, Condition.Blizzard, Condition.Sleet, Condition.Hail, Condition.Fog -> base
            else -> dryKind(base, rate, cloud, current)
        }
    }

    private fun wetKind(base: Condition, rate: Double, current: Current): Condition = when {
        rate >= HEAVY_RATE && current.pressure < STORM_PRESSURE && current.windSpeed >= STORM_WIND -> Condition.Storm
        rate >= RAIN_RATE -> Condition.Rain
        rate in 0.0..DRIZZLE_RATE && rate > 0.0 -> Condition.Drizzle
        else -> base
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
            effective <= 70 -> Condition.MoreClouds
            else -> Condition.Cloudy
        }
    }

    fun coverFor(kind: Condition): Float = when (kind) {
        Condition.Clear -> 0.04f
        Condition.FewClouds -> 0.2f
        Condition.MoreClouds -> 0.5f
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
