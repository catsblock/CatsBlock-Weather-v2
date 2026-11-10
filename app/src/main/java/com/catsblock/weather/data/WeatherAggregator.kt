package com.catsblock.weather.data

import android.os.SystemClock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal class ProviderResult(val source: WeatherSource, val bundle: WeatherBundle)

internal class TtlCache<T : Any>(private val ttlMs: Long, private val maxEntries: Int) {

    private class Entry<T>(val at: Long, val value: T)

    private val map = ConcurrentHashMap<String, Entry<T>>()

    fun get(key: String): T? {
        val entry = map[key] ?: return null
        if (SystemClock.elapsedRealtime() - entry.at >= ttlMs) {
            map.remove(key, entry)
            return null
        }
        return entry.value
    }

    fun put(key: String, value: T) {
        val now = SystemClock.elapsedRealtime()
        map.entries.removeAll { now - it.value.at >= ttlMs }
        while (map.size >= maxEntries) {
            val oldest = map.entries.minByOrNull { it.value.at } ?: break
            map.remove(oldest.key)
        }
        map[key] = Entry(now, value)
    }
}

internal object WeatherAggregator {

    const val CACHE_TTL_MS = 30L * 60L * 1000L

    private const val WET_HOUR_MM = 0.1
    private const val WET_DAY_MM = 1.0
    private const val WET_PROB_FLOOR = 75
    private const val DRY_PROB_CEILING = 49
    private const val RADAR_PROB = 92
    private const val RADAR_HORIZON_MINUTES = 360L
    private const val ARBITRATE_WET = 60
    private const val ARBITRATE_DRY = 35
    private const val ARBITRATE_WET_DAY = 50
    private const val ARBITRATE_DRY_DAY = 30
    private const val FILTER_BELOW = 45

    private val order = listOf(
        WeatherSource.OpenMeteo,
        WeatherSource.WeatherApi,
        WeatherSource.OpenWeather,
        WeatherSource.VisualCrossing
    )

    private val trusted = setOf(
        WeatherSource.WeatherApi,
        WeatherSource.OpenWeather,
        WeatherSource.VisualCrossing
    )

    private class Group(val rainy: Boolean, val members: Set<WeatherSource>)

    private class Frame(
        val now: LocalDateTime,
        val radar: RadarReading?,
        val pressures: Map<LocalDateTime, Double>,
        val forced: MutableSet<LocalDate>
    )

    fun required(participants: Int): Int = when {
        participants <= 2 -> max(1, participants)
        else -> participants - 1
    }

    fun physicalLikelihood(
        humidity: Int,
        pressure: Double,
        uv: Double,
        isDay: Boolean,
        cloud: Int,
        visibility: Double,
        temp: Double,
        drop: Double?
    ): Int {
        var total = 0.0
        var weight = 0.0
        fun add(w: Double, score: Double) {
            total += w * score
            weight += w
        }
        add(0.22, when {
            humidity >= 90 -> 1.0
            humidity >= 80 -> 0.7
            humidity >= 70 -> 0.4
            humidity >= 60 -> 0.15
            else -> 0.0
        })
        if (cloud >= 0) {
            add(0.22, when {
                cloud >= 95 -> 1.0
                cloud >= 85 -> 0.8
                cloud >= 70 -> 0.5
                cloud >= 50 -> 0.2
                else -> 0.0
            })
        }
        add(0.14, when {
            pressure < 1000.0 -> 1.0
            pressure < 1005.0 -> 0.8
            pressure < 1010.0 -> 0.55
            pressure < 1015.0 -> 0.3
            pressure < 1020.0 -> 0.1
            else -> 0.0
        })
        if (drop != null) {
            add(0.12, when {
                drop <= -3.0 -> 1.0
                drop <= -2.0 -> 0.75
                drop <= -1.0 -> 0.4
                drop < 0.0 -> 0.1
                else -> 0.0
            })
        }
        val gamma = ln(humidity.coerceIn(1, 100) / 100.0) + 17.62 * temp / (243.12 + temp)
        val spread = temp - 243.12 * gamma / (17.62 - gamma)
        add(0.12, when {
            spread <= 1.0 -> 1.0
            spread <= 2.0 -> 0.75
            spread <= 3.5 -> 0.45
            spread <= 5.0 -> 0.2
            spread <= 7.0 -> 0.05
            else -> 0.0
        })
        add(0.06, when {
            visibility <= 2000.0 -> 1.0
            visibility <= 5000.0 -> 0.6
            visibility <= 8000.0 -> 0.25
            else -> 0.0
        })
        if (isDay) {
            add(0.12, when {
                uv <= 1.0 -> 1.0
                uv <= 2.0 -> 0.7
                uv <= 4.0 -> 0.35
                uv <= 6.0 -> 0.1
                else -> 0.0
            })
        }
        return (100.0 * total / weight).roundToInt().coerceIn(0, 100)
    }

    private fun blend(api: Int, estimate: Int, agreed: Boolean): Int {
        if (api < 0) return estimate
        val weight = if (agreed) 0.8 else 0.65
        return (api * weight + estimate * (1.0 - weight)).roundToInt()
    }

    private fun trustedGroup(votes: List<Pair<WeatherSource, Boolean>>): Group? {
        val picks = votes.filter { it.first in trusted }
        if (picks.size < 2) return null
        val yes = picks.filter { it.second }
        val no = picks.filterNot { it.second }
        return when {
            yes.size > no.size -> Group(true, yes.map { it.first }.toSet())
            no.size > yes.size -> Group(false, no.map { it.first }.toSet())
            else -> null
        }
    }

    fun trustedVerdict(votes: List<Pair<WeatherSource, Boolean>>): Boolean? = trustedGroup(votes)?.rainy

    fun trustedCurrent(results: List<ProviderResult>): Boolean? =
        trustedVerdict(results.map { it.source to it.bundle.current.kind.isWet })

    private fun wetCode(code: Int): Boolean =
        code in 51..67 || code in 71..77 || code in 80..86 || code in 95..99

    private fun wetHour(hour: Hour): Boolean = hour.precip >= WET_HOUR_MM || wetCode(hour.code)

    private fun wetDay(day: Day): Boolean = day.precipSum >= WET_DAY_MM || wetCode(day.code)

    private fun precipCode(rate: Double, temp: Double, intensity: StormIntensity?): Int {
        val snow = temp <= 0.5
        return when (intensity) {
            StormIntensity.Extreme -> if (snow) 75 else 95
            StormIntensity.Severe -> if (snow) 75 else 65
            StormIntensity.Moderate -> if (snow) 73 else 63
            StormIntensity.Light -> if (snow) 71 else 61
            null -> if (rate >= 2.5) (if (snow) 73 else 63) else (if (snow) 71 else 61)
        }
    }

    private fun dryCode(cloud: Int): Int = when {
        cloud < 0 -> 2
        cloud >= 85 -> 3
        cloud >= 50 -> 2
        cloud >= 20 -> 1
        else -> 0
    }

    private fun mode(codes: List<Int>, fallback: Int): Int =
        codes.groupingBy { it }.eachCount().entries
            .maxWithOrNull(compareBy<Map.Entry<Int, Int>>({ it.value }, { it.key }))?.key ?: fallback

    private fun mean(values: List<Double>, fallback: Double = 0.0): Double =
        if (values.isEmpty()) fallback else values.sum() / values.size

    private fun meanPercent(values: List<Int>): Int {
        val valid = values.filter { it >= 0 }
        return if (valid.isEmpty()) -1 else valid.average().roundToInt()
    }

    private fun meanDirection(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val x = values.sumOf { cos(Math.toRadians(it)) }
        val y = values.sumOf { sin(Math.toRadians(it)) }
        val degrees = Math.toDegrees(atan2(y, x))
        return (degrees + 360.0) % 360.0
    }

    private fun estimateOf(hour: Hour, pressures: Map<LocalDateTime, Double>): Int =
        physicalLikelihood(
            hour.humidity,
            hour.pressure,
            hour.uv,
            hour.isDay,
            hour.cloud,
            hour.visibility,
            hour.temp,
            pressures[hour.time.minusHours(3)]?.let { hour.pressure - it }
        )

    private fun radarOverlap(radar: RadarReading, now: LocalDateTime, hourStart: LocalDateTime): Int {
        val start = ChronoUnit.MINUTES.between(now, hourStart).toDouble()
        val end = start + 60.0
        var best = 0.0
        fun consider(from: Double, to: Double) {
            val overlap = min(end, to) - max(start, from)
            if (overlap > best) best = overlap
        }
        val track = radar.track
        val covered = track != null && track.onTarget && track.etaMinutes == 0
        if (radar.wet || covered) {
            consider(0.0, (track?.takeIf { it.etaMinutes == 0 }?.durationMinutes ?: 30).coerceAtLeast(15).toDouble())
        }
        if (track != null && track.onTarget) {
            val eta = track.etaMinutes ?: 0
            if (eta > 0) consider(eta.toDouble(), eta + (track.durationMinutes ?: 60).coerceAtLeast(15).toDouble())
        }
        return best.roundToInt()
    }

    fun merge(results: List<ProviderResult>, radar: RadarReading? = null): WeatherBundle {
        require(results.isNotEmpty())
        val sorted = results.sortedBy { order.indexOf(it.source) }
        val primary = sorted.first().bundle
        val forced = HashSet<LocalDate>()
        val hours = mergeHours(sorted, primary.current.now, radar, forced)
        val days = mergeDays(sorted, hours, forced)
        val current = mergeCurrent(sorted, hours)
        return WeatherBundle(
            current = current,
            hours = hours,
            days = days,
            rain = sorted.firstOrNull { it.bundle.rain.isNotEmpty() }?.bundle?.rain ?: emptyList(),
            utcOffsetSeconds = primary.utcOffsetSeconds,
            alerts = sorted.filter { it.source == WeatherSource.VisualCrossing }
                .flatMap { it.bundle.alerts }
                .distinctBy { alert -> alert.event.ifBlank { alert.headline }.lowercase().filter { it.isLetterOrDigit() } }
                .take(5),
            radar = radar
        )
    }

    private fun mergeHours(
        sorted: List<ProviderResult>,
        now: LocalDateTime,
        radar: RadarReading?,
        forced: MutableSet<LocalDate>
    ): List<Hour> {
        val grouped = LinkedHashMap<LocalDateTime, MutableList<Pair<WeatherSource, Hour>>>()
        sorted.forEach { result -> result.bundle.hours.forEach { grouped.getOrPut(it.time) { ArrayList() } += result.source to it } }
        val pressures = grouped.mapValues { entry -> mean(entry.value.map { it.second.pressure }, 1013.0) }
        val frame = Frame(now, radar, pressures, forced)
        return grouped.entries.sortedBy { it.key }.map { combineHours(it.value, frame) }
    }

    private fun combineHours(entries: List<Pair<WeatherSource, Hour>>, frame: Frame): Hour {
        val list = entries.map { it.second }
        val first = list.first()
        val group = trustedGroup(entries.map { it.first to wetHour(it.second) })
        val wet = list.filter { wetHour(it) }
        val dry = list.filterNot { wetHour(it) }
        val humidity = mean(list.map { it.humidity.toDouble() }).roundToInt()
        val uv = mean(list.map { it.uv })
        val pressure = mean(list.map { it.pressure }, 1013.0)
        val isDay = list.count { it.isDay } * 2 >= list.size
        val cloud = meanPercent(list.map { it.cloud })
        val visibility = mean(list.map { it.visibility }, 10000.0)
        val temp = mean(list.map { it.temp })
        val drop = frame.pressures[first.time.minusHours(3)]?.let { pressure - it }
        val estimate = physicalLikelihood(humidity, pressure, uv, isDay, cloud, visibility, temp, drop)

        val trustedCount = entries.count { it.first in trusted }
        val voted = wet.size >= required(list.size)
        var rainy = when {
            group != null -> group.rainy
            trustedCount >= 2 && estimate >= ARBITRATE_WET -> true
            trustedCount >= 2 && estimate <= ARBITRATE_DRY -> false
            else -> voted
        }

        val radar = frame.radar
        val minutesAhead = ChronoUnit.MINUTES.between(frame.now, first.time)
        var radarMinutes = 0
        var radarRate = 0.0
        var radarIntensity: StormIntensity? = null
        if (radar != null && minutesAhead + 60 > 0 && minutesAhead < RADAR_HORIZON_MINUTES) {
            radarMinutes = radarOverlap(radar, frame.now, first.time)
            if (radarMinutes > 0) {
                rainy = true
                val nowWet = minutesAhead <= 0 && radar.wet && radar.cityDbz >= 15.0
                radarRate = if (nowWet) reflectivityToRate(radar.cityDbz) else radar.track?.rateMmh ?: reflectivityToRate(radar.cityDbz)
                radarIntensity = stormIntensityOf(radarRate)
                frame.forced.add(first.time.toLocalDate())
            } else if (rainy && !radar.wet && radar.track == null && estimate < FILTER_BELOW) {
                rainy = false
            }
        }

        val members = group?.takeIf { it.rainy == rainy }?.members
        val voters = when {
            radarMinutes > 0 -> emptyList()
            members != null -> entries.filter { it.first in members }.map { it.second }
            rainy -> wet
            else -> dry
        }
        val voterPrecip = if (voters.isEmpty()) 0.0 else mean(voters.map { it.precip })
        val voterCode = if (voters.isEmpty()) null else mode(voters.map { it.code }, first.code)

        val code: Int
        val precip: Double
        when {
            radarMinutes > 0 -> {
                code = precipCode(radarRate, temp, radarIntensity)
                precip = max(WET_HOUR_MM, radarRate * radarMinutes / 60.0)
            }
            rainy -> {
                code = voterCode?.takeIf { wetCode(it) } ?: precipCode(max(voterPrecip, WET_HOUR_MM), temp, null)
                precip = max(WET_HOUR_MM, voterPrecip)
            }
            else -> {
                code = voterCode?.takeIf { !wetCode(it) } ?: dryCode(cloud)
                precip = if (voterCode != null && !wetCode(voterCode)) voterPrecip else 0.0
            }
        }

        val sourceHours = members?.let { m -> entries.filter { it.first in m }.map { it.second } } ?: list
        val blended = blend(meanPercent(sourceHours.map { it.precipProb }), estimate, group != null)
        val prob = when {
            radarMinutes > 0 -> max(blended, RADAR_PROB)
            rainy -> max(blended, WET_PROB_FLOOR)
            else -> min(blended, DRY_PROB_CEILING)
        }

        return Hour(
            time = first.time,
            temp = temp,
            feels = mean(list.map { it.feels }),
            code = code,
            isDay = isDay,
            precip = precip,
            windSpeed = mean(list.map { it.windSpeed }),
            windDir = meanDirection(list.map { it.windDir }),
            humidity = humidity,
            uv = uv,
            aqi = list.mapNotNull { it.aqi }.takeIf { it.isNotEmpty() }?.average(),
            pressure = pressure,
            visibility = visibility,
            cloud = cloud,
            precipProb = prob
        )
    }

    private fun mergeDays(sorted: List<ProviderResult>, hours: List<Hour>, forced: Set<LocalDate>): List<Day> {
        val grouped = LinkedHashMap<LocalDate, MutableList<Pair<WeatherSource, Day>>>()
        sorted.forEach { result -> result.bundle.days.forEach { grouped.getOrPut(it.date) { ArrayList() } += result.source to it } }
        val pressures = hours.associate { it.time to it.pressure }
        return grouped.entries.sortedBy { it.key }.map { entry ->
            combineDays(
                entry.value,
                hours.filter { it.time.toLocalDate() == entry.key },
                pressures,
                entry.key in forced
            )
        }
    }

    private fun combineDays(
        entries: List<Pair<WeatherSource, Day>>,
        dayHours: List<Hour>,
        pressures: Map<LocalDateTime, Double>,
        radarForced: Boolean
    ): Day {
        val list = entries.map { it.second }
        val first = list.first()
        val group = trustedGroup(entries.map { it.first to wetDay(it.second) })
        val wet = list.filter { wetDay(it) }
        val dry = list.filterNot { wetDay(it) }
        val estimate = if (dayHours.isEmpty()) -1 else dayHours.map { estimateOf(it, pressures) }.average().roundToInt()
        val trustedCount = entries.count { it.first in trusted }
        val voted = wet.size >= required(list.size)
        var rainy = when {
            group != null -> group.rainy
            trustedCount >= 2 && estimate >= ARBITRATE_WET_DAY -> true
            trustedCount >= 2 && estimate in 0..ARBITRATE_DRY_DAY -> false
            else -> voted
        }
        if (radarForced) rainy = true

        val members = group?.takeIf { it.rainy == rainy }?.members
        val voters = when {
            members != null -> entries.filter { it.first in members }.map { it.second }
            rainy -> wet
            else -> dry
        }
        val wetHours = dayHours.filter { wetHour(it) }
        val voterPrecip = if (voters.isEmpty()) 0.0 else mean(voters.map { it.precipSum })
        val voterCode = if (voters.isEmpty()) null else mode(voters.map { it.code }, first.code)

        val code: Int
        val precipSum: Double
        when {
            radarForced -> {
                code = mode(wetHours.map { it.code }, 61)
                precipSum = max(WET_HOUR_MM, max(voterPrecip, wetHours.sumOf { it.precip }))
            }
            rainy -> {
                code = voterCode?.takeIf { wetCode(it) } ?: mode(wetHours.map { it.code }, 61)
                precipSum = max(WET_HOUR_MM, voterPrecip)
            }
            else -> {
                code = voterCode?.takeIf { !wetCode(it) } ?: dryCode(meanPercent(dayHours.map { it.cloud }))
                precipSum = if (voterCode != null && !wetCode(voterCode)) voterPrecip else 0.0
            }
        }

        val sourceDays = members?.let { m -> entries.filter { it.first in m }.map { it.second } } ?: list
        val apiChance = meanPercent(sourceDays.map { it.rainChance })
        val chance = if (estimate < 0) apiChance.coerceAtLeast(0) else blend(apiChance, estimate, group != null)

        return Day(
            date = first.date,
            max = mean(list.map { it.max }),
            min = mean(list.map { it.min }),
            code = code,
            precipSum = precipSum,
            rainChance = if (rainy) max(chance, WET_PROB_FLOOR) else min(chance, DRY_PROB_CEILING),
            sunrise = first.sunrise,
            sunset = first.sunset,
            uvMax = mean(list.map { it.uvMax })
        )
    }

    private fun mergeCurrent(sorted: List<ProviderResult>, hours: List<Hour>): Current {
        val picks = sorted.map { it.bundle.current }
        val group = trustedGroup(sorted.map { it.source to it.bundle.current.kind.isWet })
        val wet = picks.filter { it.kind.isWet }
        val rainy = group?.rainy ?: (wet.size >= required(picks.size))
        val agreeing = sorted.filter { it.bundle.current.kind.isWet == rainy }
        val preferred = group?.let { g -> agreeing.filter { it.source in g.members } }.orEmpty()
        val chosen = (preferred.firstOrNull { it.source == WeatherSource.VisualCrossing }
            ?: preferred.firstOrNull()
            ?: agreeing.firstOrNull())?.bundle?.current
            ?: picks.first()
        val hourKey = chosen.now.withMinute(0).withSecond(0).withNano(0)
        val hourAqi = hours.firstOrNull { it.time == hourKey }?.aqi
        return chosen.copy(
            temp = mean(picks.map { it.temp }),
            feels = mean(picks.map { it.feels }),
            windSpeed = mean(picks.map { it.windSpeed }),
            windDir = meanDirection(picks.map { it.windDir }),
            humidity = mean(picks.map { it.humidity.toDouble() }).roundToInt(),
            uv = mean(picks.map { it.uv }),
            aqi = hourAqi ?: picks.mapNotNull { it.aqi }.takeIf { it.isNotEmpty() }?.average(),
            pressure = mean(picks.map { it.pressure }, 1013.0),
            visibility = mean(picks.map { it.visibility }, 10000.0),
            cloud = meanPercent(picks.map { it.cloud })
        )
    }
}
