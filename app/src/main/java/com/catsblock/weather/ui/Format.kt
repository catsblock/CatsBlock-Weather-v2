package com.catsblock.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.catsblock.weather.data.Condition
import com.catsblock.weather.i18n.AppLanguage
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val ICON_BASE =
    "https://raw.githubusercontent.com/mrdarrengriffin/google-weather-icons/refs/heads/main/sets/set-5/light/"

fun iconUrl(kind: Condition, isDay: Boolean): String = ICON_BASE + when (kind) {
    Condition.Clear -> if (isDay) "sunny.svg" else "clear_night.svg"
    Condition.FewClouds -> if (isDay) "mostly_sunny.svg" else "mostly_clear_night.svg"
    Condition.MoreClouds -> if (isDay) "partly_cloudy.svg" else "partly_cloudy_night.svg"
    Condition.Cloudy -> "cloudy.svg"
    Condition.Fog -> "windy.svg"
    Condition.Drizzle -> "drizzle.svg"
    Condition.Rain -> "heavy_rain.svg"
    Condition.Storm -> "thunderstorms.svg"
    Condition.Sleet -> "wintry_mix.svg"
    Condition.Hail -> "sleet_hail.svg"
    Condition.Snow -> "snow_showers.svg"
    Condition.HeavySnow -> "heavy_snow.svg"
    Condition.Blizzard -> "blizzard.svg"
}

fun Condition.label(isDay: Boolean): String = tr(
    when (this) {
        Condition.Clear -> if (isDay) "Sunny" else "Clear"
        Condition.FewClouds -> "Mostly clear"
        Condition.MoreClouds -> "Mostly cloudy"
        Condition.Cloudy -> "Cloudy"
        Condition.Fog -> "Fog"
        Condition.Drizzle -> "Drizzle"
        Condition.Rain -> "Rain"
        Condition.Storm -> "Thunderstorm"
        Condition.Sleet -> "Wintry mix"
        Condition.Hail -> "Hail"
        Condition.Snow -> "Snow"
        Condition.HeavySnow -> "Heavy snow"
        Condition.Blizzard -> "Blizzard"
    }
)

fun Double.toUnit(fahrenheit: Boolean): Double = if (fahrenheit) this * 9.0 / 5.0 + 32.0 else this

fun tempText(celsius: Double, fahrenheit: Boolean): String =
    "${celsius.toUnit(fahrenheit).roundToInt()}°"

fun f1(value: Double): String = String.format(Locale.US, "%.1f", value)

private val english get() = I18n.lang == AppLanguage.English

private fun fmt(en: String, other: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (english) en else other, I18n.lang.locale)

private fun String.cap(): String = replaceFirstChar { it.titlecase(I18n.lang.locale) }

fun hourLabel(time: LocalDateTime): String = time.format(fmt("h a", "HH:mm"))

fun clockLabel(time: LocalDateTime): String = time.format(fmt("h:mm a", "HH:mm"))

fun dateLabel(date: LocalDate): String = date.format(fmt("MMM d", "d MMM")).cap()

fun longDateLabel(date: LocalDate): String = date.format(fmt("EEEE, MMM d", "EEEE, d MMM")).cap()

fun weekdayLabel(time: LocalDateTime): String = time.format(fmt("EEE", "EEE")).cap()

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today.minusDays(1) -> tr("Yesterday")
    today -> tr("Today")
    today.plusDays(1) -> tr("Tomorrow")
    else -> date.format(fmt("EEE", "EEE")).cap()
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
