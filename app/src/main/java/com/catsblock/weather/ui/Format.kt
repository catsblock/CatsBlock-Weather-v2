package com.catsblock.weather.ui

import com.catsblock.weather.data.WeatherSource
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.data.WindUnit
import com.catsblock.weather.data.PressureUnit
import com.catsblock.weather.data.PrecipUnit
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
import com.catsblock.weather.kit.IconKit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val ICON_BASE =
    "https://raw.githubusercontent.com/mrdarrengriffin/google-weather-icons/refs/heads/main/sets/set-4/light/"

const val PRECIP_CHANCE_MIN = 15

private val Condition.isPrecip: Boolean
    get() = when (this) {
        Condition.Drizzle, Condition.Rain, Condition.Storm, Condition.Sleet, Condition.Hail,
        Condition.Snow, Condition.HeavySnow, Condition.Blizzard -> true
        else -> false
    }

fun iconUrl(
    kind: Condition,
    isDay: Boolean,
    precipMm: Double = -1.0,
    cloud: Int = -1,
    windKmh: Double = 0.0,
    chance: Int = -1
): String {
    val sfx = if (isDay) "day" else "night"
    val known = precipMm >= 0.0
    if (kind.isPrecip && chance in 0..PRECIP_CHANCE_MIN) {
        val dry = when {
            windKmh >= 55.0 -> "windy"
            cloud in 0..39 -> "mostly_clear_$sfx"
            cloud in 40..69 -> "partly_cloudy_$sfx"
            cloud in 70..84 -> "mostly_cloudy_$sfx"
            else -> "cloudy"
        }
        return ICON_BASE + dry + ".svg"
    }
    val scattered = chance in (PRECIP_CHANCE_MIN + 1)..39
    val file = when (kind) {
        Condition.Clear -> if (windKmh >= 55.0) "windy" else "clear_$sfx"
        Condition.FewClouds -> "mostly_clear_$sfx"
        Condition.MoreClouds -> if (cloud in 0..69) "partly_cloudy_$sfx" else "mostly_cloudy_$sfx"
        Condition.Cloudy -> if (windKmh >= 55.0) "windy" else "cloudy"
        Condition.Fog -> if (windKmh >= 40.0) "windy" else "haze_fog_dust_smoke"
        Condition.Drizzle -> "drizzle"
        Condition.Rain -> when {
            scattered || (known && precipMm < 1.0) -> "scattered_showers_$sfx"
            known && precipMm >= 4.0 -> "heavy_rain"
            else -> "showers_rain"
        }
        Condition.Storm -> when {
            scattered || (known && precipMm < 0.5) -> "isolated_scattered_thunderstorms_$sfx"
            known && precipMm < 2.0 -> "isolated_thunderstorms"
            else -> "strong_thunderstorms"
        }
        Condition.Sleet -> when {
            known && precipMm >= 2.0 -> "mixed_rain_hail_sleet"
            known && precipMm >= 0.5 -> "mixed_rain_snow"
            else -> "icy"
        }
        Condition.Hail -> "sleet_hail"
        Condition.Snow -> when {
            known && precipMm < 0.2 -> "flurries"
            windKmh >= 25.0 && (scattered || precipMm < 0.8) -> "blowing_snow"
            scattered || (known && precipMm < 0.8) -> "scattered_snow_showers_$sfx"
            else -> "showers_snow"
        }
        Condition.HeavySnow -> "heavy_snow"
        Condition.Blizzard -> "blizzard"
    }
    return ICON_BASE + file + ".svg"
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
    val pressure: PressureUnit = PressureUnit.Hpa,
    val precip: PrecipUnit = PrecipUnit.Mm
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

fun precipValue(mm: Double, unit: PrecipUnit): Double = mm * unit.factor

fun precipNumber(mm: Double, unit: PrecipUnit): String =
    String.format(Locale.US, "%.${unit.decimals}f", precipValue(mm, unit))

fun precipText(mm: Double, unit: PrecipUnit): String = "${precipNumber(mm, unit)} ${unit.label}"

fun precipRateText(mm: Double, unit: PrecipUnit): String = "${precipText(mm, unit)}/h"

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

fun intervalLabel(minutes: Int): String = tr(
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
fun WeatherIcon(
    kind: Condition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    precipMm: Double = -1.0,
    cloud: Int = -1,
    windKmh: Double = 0.0,
    chance: Int = -1
) {
    val context = LocalContext.current
    val version by IconKit.version.collectAsState()
    val custom = remember(kind, isDay, version) { IconKit.custom(context, kind, isDay) }
    AsyncImage(
        model = custom ?: iconUrl(kind, isDay, precipMm, cloud, windKmh, chance),
        contentDescription = kind.label(isDay),
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
