package com.catsblock.weather.ui

import com.catsblock.weather.data.WeatherSource
import com.catsblock.weather.i18n.trx
import com.catsblock.weather.data.WindUnit
import com.catsblock.weather.data.PressureUnit
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.catsblock.weather.data.Condition
import com.catsblock.weather.i18n.AppLanguage
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.kit.IconKit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val ICON_SET_5 =
    "https://raw.githubusercontent.com/mrdarrengriffin/google-weather-icons/refs/heads/main/sets/set-5/light/"
private const val ICON_SET_6 =
    "https://raw.githubusercontent.com/mrdarrengriffin/google-weather-icons/refs/heads/main/sets/set-6/light/"

fun iconUrl(kind: Condition, isDay: Boolean): String = when (kind) {
    Condition.Clear -> if (isDay) ICON_SET_6 + "sunny.svg" else ICON_SET_6 + "clear_night.svg"
    Condition.FewClouds -> if (isDay) ICON_SET_5 + "mostly_sunny.svg" else ICON_SET_6 + "mostly_clear_night.svg"
    Condition.MoreClouds -> if (isDay) ICON_SET_5 + "partly_cloudy.svg" else ICON_SET_6 + "partly_cloudy_night.svg"
    Condition.Cloudy -> ICON_SET_5 + "cloudy.svg"
    Condition.Fog -> ICON_SET_6 + "windy.svg"
    Condition.Drizzle -> ICON_SET_6 + "drizzle.svg"
    Condition.Rain -> ICON_SET_6 + "heavy_rain.svg"
    Condition.Storm -> ICON_SET_6 + "strong_thunderstorms.svg"
    Condition.Sleet -> ICON_SET_6 + "wintry_mix.svg"
    Condition.Hail -> ICON_SET_6 + "sleet_hail.svg"
    Condition.Snow -> ICON_SET_6 + "flurries.svg"
    Condition.HeavySnow -> ICON_SET_6 + "heavy_snow.svg"
    Condition.Blizzard -> ICON_SET_6 + "blowing_snow.svg"
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

data class UnitPrefs(
    val wind: WindUnit = WindUnit.Kmh,
    val pressure: PressureUnit = PressureUnit.Hpa
)

val LocalUnits = compositionLocalOf { UnitPrefs() }

fun windValue(kmh: Double, unit: WindUnit): Double = kmh * unit.factor

fun windText(kmh: Double, unit: WindUnit): String = "${windValue(kmh, unit).roundToInt()} ${unit.label}"

fun windCaption(unit: WindUnit): String =
    if (unit == WindUnit.Kmh) tr("Speed in km/h with direction") else tr("Speed in %s with direction").format(unit.label)

fun pressureValue(hpa: Double, unit: PressureUnit): Double = hpa * unit.factor

fun pressureFormat(value: Double, unit: PressureUnit): String =
    String.format(Locale.US, "%.${unit.decimals}f", value)

fun pressureNumber(hpa: Double, unit: PressureUnit): String = pressureFormat(pressureValue(hpa, unit), unit)

fun pressureCaption(unit: PressureUnit): String = tr(unit.title)

fun sourceTitle(source: WeatherSource): String = when (source) {
    WeatherSource.OpenMeteo -> tr("Open-Meteo (free, no key)")
    WeatherSource.WeatherApi -> "WeatherAPI.com"
    WeatherSource.VisualCrossing -> "Visual Crossing Weather"
    WeatherSource.OpenWeather -> "OpenWeather"
}

fun sourceNote(source: WeatherSource, available: Boolean): String = when {
    !available -> tr("Not available in this build")
    source == WeatherSource.OpenMeteo -> tr("Default. Free and works without an API key.")
    source == WeatherSource.WeatherApi -> tr("Forecast from WeatherAPI.com. Free plans cover a shorter forecast range.")
    source == WeatherSource.OpenWeather -> tr("Forecast and government alerts from OpenWeather. Falls back to Open-Meteo if a request fails.")
    else -> tr("Forecast from Visual Crossing. Falls back to Open-Meteo if a request fails.")
}

fun formatWait(totalSeconds: Long): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val hours = clamped / 3600
    val minutes = (clamped % 3600) / 60
    return if (hours > 0) {
        String.format(Locale.US, "%dh %02dm", hours, minutes)
    } else {
        String.format(Locale.US, "%dm", minutes.coerceAtLeast(1))
    }
}

fun intervalLabel(minutes: Int): String = trx(
    when (minutes) {
        30 -> "30 minutes"
        60 -> "1 hour"
        else -> "${minutes / 60} hours"
    }
)

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
    val context = LocalContext.current
    val version by IconKit.version.collectAsState()
    val custom = remember(kind, isDay, version) { IconKit.custom(context, kind, isDay) }
    AsyncImage(
        model = custom ?: iconUrl(kind, isDay),
        contentDescription = kind.label(isDay),
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
