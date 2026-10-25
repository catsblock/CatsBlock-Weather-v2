package com.catsblock.weather.data

import com.catsblock.weather.i18n.tr
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

enum class AlertKind { LightRain, HeavyRain, LightSnow, HeavySnow, Fog, Warm, Cool }

data class WeatherAlert(val kind: AlertKind, val title: String)

private val snowCodes = setOf(71, 73, 75, 77, 85, 86)
private val fogCodes = setOf(45, 48)

private fun degrees(celsius: Double, fahrenheit: Boolean): Int =
    (if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius).roundToInt()

fun buildAlert(bundle: WeatherBundle, fahrenheit: Boolean): WeatherAlert? {
    val now = bundle.current.now.truncatedTo(ChronoUnit.HOURS)
    val limit = now.plusHours(6)
    val upcoming = bundle.hours.filter { !it.time.isBefore(now) && it.time.isBefore(limit) }

    val snowing = upcoming.filter { it.code in snowCodes && it.precip > 0.0 }
    val raining = upcoming.filter { it.code !in snowCodes && it.precip > 0.0 }

    val snowPeak = snowing.maxOfOrNull { it.precip } ?: 0.0
    val rainPeak = raining.maxOfOrNull { it.precip } ?: 0.0

    if (snowPeak >= 2.0) return WeatherAlert(AlertKind.HeavySnow, tr("Heavy snow expected in the next hours"))
    if (rainPeak >= 4.0) return WeatherAlert(AlertKind.HeavyRain, tr("Heavy rain expected in the next hours"))
    if (snowPeak > 0.0) return WeatherAlert(AlertKind.LightSnow, tr("Light snow expected in the next hours"))
    if (rainPeak > 0.0) return WeatherAlert(AlertKind.LightRain, tr("Light rain expected in the next hours"))

    val foggy = bundle.current.visibility in 0.0..1000.0 || upcoming.any { it.code in fogCodes }
    if (foggy) return WeatherAlert(AlertKind.Fog, tr("Fog may reduce visibility"))

    val today = bundle.days.firstOrNull { it.date == bundle.current.now.toLocalDate() }
    val tomorrow = bundle.days.firstOrNull { it.date == bundle.current.now.toLocalDate().plusDays(1) }
    if (today != null && tomorrow != null) {
        val diff = tomorrow.max - today.max
        if (diff >= 6.0) {
            return WeatherAlert(AlertKind.Warm, tr("Warmer tomorrow:") + " " + degrees(tomorrow.max, fahrenheit) + "°")
        }
        if (diff <= -6.0) {
            return WeatherAlert(AlertKind.Cool, tr("Cooler tomorrow:") + " " + degrees(tomorrow.max, fahrenheit) + "°")
        }
    }
    return null
}
