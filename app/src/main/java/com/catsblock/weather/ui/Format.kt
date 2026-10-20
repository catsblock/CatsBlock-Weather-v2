package com.catsblock.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.catsblock.weather.data.Condition
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val ICON_BASE =
    "https://raw.githubusercontent.com/mrdarrengriffin/google-weather-icons/refs/heads/main/sets/set-4/light/"

fun iconUrl(kind: Condition, isDay: Boolean): String = ICON_BASE + when (kind) {
    Condition.Clear -> if (isDay) "clear_day.svg" else "clear_night.svg"
    Condition.FewClouds -> if (isDay) "mostly_clear_day.svg" else "mostly_clear_night.svg"
    Condition.MoreClouds -> if (isDay) "mostly_cloudy_day.svg" else "mostly_cloudy_night.svg"
    Condition.Cloudy -> "cloudy.svg"
    Condition.Rain -> "showers_rain.svg"
    Condition.Storm -> "isolated_thunderstorms.svg"
    Condition.Snow -> "showers_snow.svg"
    Condition.Fog -> "haze_fog_dust_smoke.svg"
}

fun Condition.label(isDay: Boolean): String = when (this) {
    Condition.Clear -> if (isDay) "Sunny" else "Clear"
    Condition.FewClouds -> "Mostly clear"
    Condition.MoreClouds -> "Mostly cloudy"
    Condition.Cloudy -> "Cloudy"
    Condition.Rain -> "Rain"
    Condition.Storm -> "Thunderstorm"
    Condition.Snow -> "Snow"
    Condition.Fog -> "Fog"
}

fun Double.toUnit(fahrenheit: Boolean): Double = if (fahrenheit) this * 9.0 / 5.0 + 32.0 else this

fun tempText(celsius: Double, fahrenheit: Boolean): String =
    "${celsius.toUnit(fahrenheit).roundToInt()}°"

fun f1(value: Double): String = String.format(Locale.US, "%.1f", value)

private val hourFormat = DateTimeFormatter.ofPattern("h a", Locale.ENGLISH)
private val clockFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val longDateFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)
private val weekdayFormat = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

fun hourLabel(time: LocalDateTime): String = time.format(hourFormat)

fun clockLabel(time: LocalDateTime): String = time.format(clockFormat)

fun dateLabel(date: LocalDate): String = date.format(dateFormat)

fun longDateLabel(date: LocalDate): String = date.format(longDateFormat)

fun weekdayLabel(time: LocalDateTime): String = time.format(weekdayFormat)

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today.minusDays(1) -> "Yesterday"
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(weekdayFormat)
}

@Composable
fun WeatherIcon(kind: Condition, isDay: Boolean, modifier: Modifier = Modifier) {
    AsyncImage(
        model = iconUrl(kind, isDay),
        contentDescription = kind.label(isDay),
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
