package com.catsblock.weather.data

import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AlertKind { LightRain, LightSnow, HeavyRain, HeavySnow, Fog, Warm, Cool }

data class WeatherAlert(val kind: AlertKind, val title: String)

private val snowCodes = setOf(71, 73, 75, 77, 85, 86)
private val lightCodes = setOf(51, 53, 55, 56, 57, 61, 66, 71, 77, 80, 85)
private val heavyCodes = setOf(63, 65, 67, 73, 75, 81, 82, 86, 95, 96, 99)
private val fogCodes = setOf(45, 48)

private fun hoursText(n: Int) = if (n == 1) "1 hour" else "$n hours"

private fun daysText(n: Int) = if (n == 1) "1 day" else "$n days"

fun buildAlert(bundle: WeatherBundle, fahrenheit: Boolean): WeatherAlert? {
    val nowHour = bundle.current.now.truncatedTo(ChronoUnit.HOURS)
    val start = bundle.hours.indexOfFirst { !it.time.isBefore(nowHour) }.coerceAtLeast(0)
    val ahead = bundle.hours.drop(start).take(24)
    val soon = ahead.take(8)

    val heavyAt = soon.indexOfFirst { it.code in heavyCodes }
    if (heavyAt >= 0) {
        val snow = soon[heavyAt].code in snowCodes
        val word = if (snow) "snow" else "rain"
        val verb = if (snow) "snows" else "rains"
        return WeatherAlert(
            if (snow) AlertKind.HeavySnow else AlertKind.HeavyRain,
            "A moderate, heavy $word is arriving here at ${hoursText(heavyAt.coerceAtLeast(1))}, be alert in case it $verb."
        )
    }

    val lightAt = soon.indexOfFirst { it.code in lightCodes }
    if (lightAt >= 0) {
        val snow = soon[lightAt].code in snowCodes
        val word = if (snow) "snow" else "rain"
        val verb = if (snow) "snows" else "rains"
        var count = 0
        while (lightAt + count < ahead.size && ahead[lightAt + count].code in lightCodes) count++
        return WeatherAlert(
            if (snow) AlertKind.LightSnow else AlertKind.LightRain,
            "A light $word is expected here for ${hoursText(count)}. Be concerned if it $verb."
        )
    }

    val fogAt = ahead.take(12).indexOfFirst { it.code in fogCodes }
    if (fogAt >= 0) {
        var count = 0
        while (fogAt + count < ahead.size && ahead[fogAt + count].code in fogCodes) count++
        if (count <= 4) {
            return WeatherAlert(
                AlertKind.Fog,
                "The city may be shrouded in fog for ${hoursText(count)}. Do not travel when there is fog."
            )
        }
    }

    val today = bundle.current.now.toLocalDate()
    val upcoming = bundle.days.filter { !it.date.isBefore(today) }
    if (upcoming.size < 2) return null
    val base = upcoming[0].max
    for (i in 1 until minOf(upcoming.size, 7)) {
        val delta = upcoming[i].max - base
        if (abs(delta) >= 4.0) {
            val shown = (abs(delta) * (if (fahrenheit) 1.8 else 1.0)).roundToInt()
            return if (delta > 0) {
                WeatherAlert(AlertKind.Warm, "The temperature will increase by approximately $shown° in ${daysText(i)}.")
            } else {
                WeatherAlert(AlertKind.Cool, "The temperature will decrease by $shown° in ${daysText(i)}.")
            }
        }
    }
    return null
}
