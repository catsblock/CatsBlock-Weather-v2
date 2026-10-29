package com.catsblock.weather.data

import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
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
    private const val WET_CONSENSUS = 0.58
    private const val DRY_CONSENSUS = 0.2
    private const val HISTORY_HOURS = 3L

    private class Signal(val weight: Double, val value: Double)

    fun apply(bundle: WeatherBundle, radar: RadarReading? = null): WeatherBundle {
        val current = bundle.current
        val nowHour = current.now.truncatedTo(ChronoUnit.HOURS)
        val hour = bundle.hours.firstOrNull { it.time == nowHour }
        val cloud = when {
            current.cloud >= 0 -> current.cloud
            hour != null && hour.cloud >= 0 -> hour.cloud
            else -> -1
        }
        val resolved = resolve(current, hour, cloud, bundle, radar)
        return bundle.copy(current = current.copy(kind = resolved, cloud = cloud))
    }

    fun resolve(current: Current, hour: Hour?, cloud: Int, bundle: WeatherBundle? = null, radar: RadarReading? = null): Condition {
        val base = current.kind
        val rate = hour?.precip ?: 0.0
        if (base == Condition.Storm) return Condition.Storm
        if (base == Condition.HeavySnow || base == Condition.Blizzard || base == Condition.Hail || base == Condition.Fog) return base
        val score = bundle?.let { rainScore(current, hour, cloud, it, radar) }
        val nextRate = bundle?.let { nextHourRate(current, it) } ?: 0.0
        val radarRate = radar?.takeIf { it.wet }?.let { it.strength * 6.0 } ?: 0.0
        val intensity = max(rate, max(nextRate, radarRate))
        return when (base) {
            Condition.Snow -> if (current.temp >= SLEET_TEMP && rate > 0.0) Condition.Sleet else base
            Condition.Sleet -> base
            Condition.Drizzle, Condition.Rain -> {
                val dryEvidence = score != null && score < DRY_CONSENSUS && radar?.wet != true && rate <= 0.0
                if (dryEvidence) dryKind(Condition.MoreClouds, 0.0, cloud, current) else wetKind(base, max(rate, intensity), current, radar)
            }
            else -> {
                val consensus = score != null && score >= WET_CONSENSUS && hasHardEvidence(rate, nextRate, radar, bundle, current)
                if (consensus) wetKind(Condition.Rain, intensity, current, radar) else dryKind(base, rate, cloud, current)
            }
        }
    }

    private fun hasHardEvidence(rate: Double, nextRate: Double, radar: RadarReading?, bundle: WeatherBundle?, current: Current): Boolean =
        rate >= TRACE_RATE || nextRate >= DRIZZLE_RATE || radar?.wet == true || (bundle != null && pastRain(current, bundle) >= 0.5)

    private fun rainScore(current: Current, hour: Hour?, cloud: Int, bundle: WeatherBundle, radar: RadarReading?): Double {
        val signals = ArrayList<Signal>(10)
        val rate = hour?.precip ?: 0.0
        signals += Signal(3.0, rateLevel(rate))
        signals += Signal(1.5, rateLevel(nextHourRate(current, bundle)))
        val past = pastRain(current, bundle)
        signals += Signal(1.0, if (past >= 1.0) 1.0 else if (past >= 0.2) 0.5 else 0.0)
        if (radar != null) {
            val level = if (radar.wet) (0.6 + radar.strength).coerceAtMost(1.0) else 0.0
            signals += Signal(3.0, level)
        }
        if (cloud >= 0) {
            signals += Signal(1.5, when {
                cloud >= 85 -> 1.0
                cloud >= 70 -> 0.6
                cloud >= 50 -> 0.2
                else -> 0.0
            })
        }
        signals += Signal(1.0, when {
            current.humidity >= 90 -> 1.0
            current.humidity >= 80 -> 0.6
            current.humidity >= 70 -> 0.2
            else -> 0.0
        })
        signals += Signal(1.0, when {
            current.pressure < LOW_PRESSURE -> 1.0
            current.pressure < 1010.0 -> 0.5
            else -> 0.0
        })
        pressureTrend(current, bundle)?.let { drop ->
            signals += Signal(1.0, when {
                drop <= -2.0 -> 1.0
                drop <= -1.0 -> 0.5
                else -> 0.0
            })
        }
        if (current.isDay) {
            signals += Signal(0.7, when {
                current.uv <= 1.5 -> 1.0
                current.uv <= 3.0 -> 0.4
                else -> 0.0
            })
        }
        signals += Signal(0.5, when {
            current.windSpeed >= 25.0 -> 1.0
            current.windSpeed >= 15.0 -> 0.5
            else -> 0.0
        })
        val total = signals.sumOf { it.weight }
        return signals.sumOf { it.weight * it.value } / total
    }

    private fun rateLevel(rate: Double): Double = when {
        rate >= RAIN_RATE -> 1.0
        rate >= DRIZZLE_RATE -> 0.8
        rate >= TRACE_RATE -> 0.5
        else -> 0.0
    }

    private fun nextHourRate(current: Current, bundle: WeatherBundle): Double {
        val next = current.now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        return bundle.hours.firstOrNull { it.time == next }?.precip ?: 0.0
    }

    private fun pastRain(current: Current, bundle: WeatherBundle): Double {
        val nowHour = current.now.truncatedTo(ChronoUnit.HOURS)
        val from = nowHour.minusHours(HISTORY_HOURS)
        return bundle.hours.filter { !it.time.isBefore(from) && it.time.isBefore(nowHour) }.sumOf { it.precip }
    }

    private fun pressureTrend(current: Current, bundle: WeatherBundle): Double? {
        val ago = current.now.truncatedTo(ChronoUnit.HOURS).minusHours(HISTORY_HOURS)
        val past = bundle.hours.firstOrNull { it.time == ago } ?: return null
        return current.pressure - past.pressure
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
