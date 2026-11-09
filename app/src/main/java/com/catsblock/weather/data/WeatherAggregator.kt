package com.catsblock.weather.data

import android.os.SystemClock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
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
    private const val CURRENT_SOURCES = 2

    private val order = listOf(
        WeatherSource.OpenMeteo,
        WeatherSource.WeatherApi,
        WeatherSource.OpenWeather,
        WeatherSource.VisualCrossing
    )

    fun required(participants: Int): Int = when {
        participants <= 2 -> max(1, participants)
        else -> participants - 1
    }

    private fun wetCode(code: Int): Boolean =
        code in 51..67 || code in 71..77 || code in 80..86 || code in 95..99

    private fun wetHour(hour: Hour): Boolean = hour.precip >= WET_HOUR_MM || wetCode(hour.code)

    private fun wetDay(day: Day): Boolean = day.precipSum >= WET_DAY_MM || wetCode(day.code)

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

    fun merge(results: List<ProviderResult>): WeatherBundle {
        require(results.isNotEmpty())
        val sorted = results.sortedBy { order.indexOf(it.source) }
        val primary = sorted.first().bundle
        val hours = mergeHours(sorted)
        val days = mergeDays(sorted)
        val current = mergeCurrent(sorted, hours)
        return WeatherBundle(
            current = current,
            hours = hours,
            days = days,
            rain = sorted.firstOrNull { it.bundle.rain.isNotEmpty() }?.bundle?.rain ?: emptyList(),
            utcOffsetSeconds = primary.utcOffsetSeconds,
            alerts = sorted.flatMap { it.bundle.alerts }
                .distinctBy { alert -> alert.event.ifBlank { alert.headline }.lowercase().filter { it.isLetterOrDigit() } }
                .take(5)
        )
    }

    private fun mergeHours(sorted: List<ProviderResult>): List<Hour> {
        val grouped = LinkedHashMap<LocalDateTime, MutableList<Hour>>()
        sorted.forEach { result -> result.bundle.hours.forEach { grouped.getOrPut(it.time) { ArrayList() } += it } }
        return grouped.entries.sortedBy { it.key }.map { combineHours(it.value) }
    }

    private fun combineHours(list: List<Hour>): Hour {
        val wet = list.filter { wetHour(it) }
        val dry = list.filterNot { wetHour(it) }
        val rainy = wet.size >= required(list.size)
        val voters = if (rainy) wet else dry
        val prob = meanPercent(list.map { it.precipProb })
        val first = list.first()
        return Hour(
            time = first.time,
            temp = mean(list.map { it.temp }),
            feels = mean(list.map { it.feels }),
            code = mode(voters.map { it.code }, first.code),
            isDay = list.count { it.isDay } * 2 >= list.size,
            precip = if (rainy) max(WET_HOUR_MM, mean(voters.map { it.precip })) else mean(voters.map { it.precip }),
            windSpeed = mean(list.map { it.windSpeed }),
            windDir = meanDirection(list.map { it.windDir }),
            humidity = mean(list.map { it.humidity.toDouble() }).roundToInt(),
            uv = mean(list.map { it.uv }),
            aqi = list.mapNotNull { it.aqi }.takeIf { it.isNotEmpty() }?.average(),
            pressure = mean(list.map { it.pressure }, 1013.0),
            visibility = mean(list.map { it.visibility }, 10000.0),
            cloud = meanPercent(list.map { it.cloud }),
            precipProb = when {
                rainy -> max(prob, WET_PROB_FLOOR)
                prob >= 0 -> min(prob, DRY_PROB_CEILING)
                else -> -1
            }
        )
    }

    private fun mergeDays(sorted: List<ProviderResult>): List<Day> {
        val grouped = LinkedHashMap<LocalDate, MutableList<Day>>()
        sorted.forEach { result -> result.bundle.days.forEach { grouped.getOrPut(it.date) { ArrayList() } += it } }
        return grouped.entries.sortedBy { it.key }.map { combineDays(it.value) }
    }

    private fun combineDays(list: List<Day>): Day {
        val wet = list.filter { wetDay(it) }
        val dry = list.filterNot { wetDay(it) }
        val rainy = wet.size >= required(list.size)
        val voters = if (rainy) wet else dry
        val chance = meanPercent(list.map { it.rainChance }).coerceAtLeast(0)
        val first = list.first()
        return Day(
            date = first.date,
            max = mean(list.map { it.max }),
            min = mean(list.map { it.min }),
            code = mode(voters.map { it.code }, first.code),
            precipSum = if (rainy) max(WET_HOUR_MM, mean(voters.map { it.precipSum })) else mean(voters.map { it.precipSum }),
            rainChance = if (rainy) max(chance, WET_PROB_FLOOR) else min(chance, DRY_PROB_CEILING),
            sunrise = first.sunrise,
            sunset = first.sunset,
            uvMax = mean(list.map { it.uvMax })
        )
    }

    private fun mergeCurrent(sorted: List<ProviderResult>, hours: List<Hour>): Current {
        val picks = sorted.take(CURRENT_SOURCES).map { it.bundle.current }
        val wet = picks.filter { it.kind.isWet }
        val rainy = wet.size >= required(picks.size)
        val chosen = (if (rainy) wet else picks.filterNot { it.kind.isWet }).firstOrNull() ?: picks.first()
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
