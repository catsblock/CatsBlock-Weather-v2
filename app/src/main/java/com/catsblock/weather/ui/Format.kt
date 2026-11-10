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

private const val ICON_BASE_V0 =
    "https://raw.githubusercontent.com/bignutty/google-weather-icons/refs/heads/main/icons/weather/v0/dark/"

private val ICON_V0_FILES: Map<String, String> = mapOf(
    "drizzle" to "drizzle",
    "showers_rain" to "showers_rain",
    "heavy_rain" to "heavy_rain",
    "flurries" to "flurries",
    "showers_snow" to "snow_showers_snow",
    "heavy_snow" to "heavy_snow",
    "icy" to "icy",
    "sleet_hail" to "sleet_hail",
    "mixed_rain_hail_sleet" to "wintry_mix_rain_snow",
    "scattered_showers_day" to "scattered_showers_day",
    "scattered_showers_night" to "scattered_showers_night",
    "scattered_snow_showers_day" to "scattered_snow_showers_day",
    "scattered_snow_showers_night" to "scattered_snow_showers_night",
    "isolated_scattered_thunderstorms_day" to "isolated_scattered_tstorms_day",
    "isolated_scattered_thunderstorms_night" to "isolated_scattered_tstorms_night",
    "isolated_thunderstorms" to "isolated_tstorms",
    "strong_thunderstorms" to "strong_tstorms"
)

const val PRECIP_CHANCE_MIN = 15

private val Condition.isPrecip: Boolean
    get() = when (this) {
        Condition.Drizzle, Condition.Rain, Condition.Storm, Condition.Sleet, Condition.Hail,
        Condition.Snow, Condition.HeavySnow, Condition.Blizzard, Condition.Icy, Condition.Flurries,
        Condition.BlowingSnow, Condition.HeavyRain, Condition.ScatteredShowers, Condition.ScatteredStorms,
        Condition.IsolatedStorms, Condition.StrongStorms, Condition.ScatteredSnow, Condition.SnowShowers,
        Condition.RainSnow, Condition.WintryMix -> true
        else -> false
    }

class WeatherLook(val file: String, val title: String)

fun weatherLook(
    kind: Condition,
    isDay: Boolean,
    precipMm: Double = -1.0,
    cloud: Int = -1,
    windKmh: Double = 0.0,
    chance: Int = -1
): WeatherLook {
    val sfx = if (isDay) "day" else "night"
    val known = precipMm >= 0.0
    if (kind.isPrecip && chance in 0..PRECIP_CHANCE_MIN) {
        return when {
            windKmh >= 55.0 -> WeatherLook("windy", "Windy")
            cloud in 0..39 -> WeatherLook("mostly_clear_$sfx", "Mostly clear")
            cloud in 40..69 -> WeatherLook("partly_cloudy_$sfx", "Partly cloudy")
            cloud in 70..84 -> WeatherLook("mostly_cloudy_$sfx", "Mostly cloudy")
            else -> WeatherLook("cloudy", "Cloudy")
        }
    }
    val scattered = chance in (PRECIP_CHANCE_MIN + 1)..39
    return when (kind) {
        Condition.Clear -> when {
            windKmh >= 55.0 -> WeatherLook("windy", "Windy")
            isDay -> WeatherLook("clear_day", "Sunny")
            else -> WeatherLook("clear_night", "Clear")
        }
        Condition.FewClouds -> WeatherLook("mostly_clear_$sfx", "Mostly clear")
        Condition.PartlyCloudy -> WeatherLook("partly_cloudy_$sfx", "Partly cloudy")
        Condition.MoreClouds -> WeatherLook("mostly_cloudy_$sfx", "Mostly cloudy")
        Condition.Cloudy -> if (windKmh >= 55.0) WeatherLook("windy", "Windy") else WeatherLook("cloudy", "Cloudy")
        Condition.Fog -> if (windKmh >= 40.0) WeatherLook("windy", "Windy") else WeatherLook("haze_fog_dust_smoke", "Fog")
        Condition.Drizzle -> WeatherLook("drizzle", "Drizzle")
        Condition.Rain -> when {
            scattered || (known && precipMm < 1.0) -> WeatherLook("scattered_showers_$sfx", "Scattered showers")
            known && precipMm >= 4.0 -> WeatherLook("heavy_rain", "Heavy rain")
            else -> WeatherLook("showers_rain", "Rain")
        }
        Condition.Storm -> when {
            scattered || (known && precipMm < 0.5) ->
                WeatherLook("isolated_scattered_thunderstorms_$sfx", "Scattered thunderstorms")
            known && precipMm < 2.0 -> WeatherLook("isolated_thunderstorms", "Isolated thunderstorms")
            else -> WeatherLook("strong_thunderstorms", "Thunderstorm")
        }
        Condition.Sleet -> when {
            known && precipMm >= 2.0 -> WeatherLook("mixed_rain_hail_sleet", "Wintry mix")
            known && precipMm >= 0.5 -> WeatherLook("mixed_rain_snow", "Rain and snow")
            else -> WeatherLook("icy", "Icy")
        }
        Condition.Hail -> WeatherLook("sleet_hail", "Hail")
        Condition.Snow -> when {
            known && precipMm < 0.2 -> WeatherLook("flurries", "Flurries")
            windKmh >= 25.0 && (scattered || precipMm < 0.8) -> WeatherLook("blowing_snow", "Blowing snow")
            scattered || (known && precipMm < 0.8) ->
                WeatherLook("scattered_snow_showers_$sfx", "Scattered snow showers")
            else -> WeatherLook("showers_snow", "Snow")
        }
        Condition.HeavySnow -> WeatherLook("heavy_snow", "Heavy snow")
        Condition.Blizzard -> WeatherLook("blizzard", "Blizzard")
        Condition.Windy -> WeatherLook("windy", "Windy")
        Condition.Icy -> WeatherLook("icy", "Icy")
        Condition.Flurries -> WeatherLook("flurries", "Flurries")
        Condition.BlowingSnow -> WeatherLook("blowing_snow", "Blowing snow")
        Condition.HeavyRain -> WeatherLook("heavy_rain", "Heavy rain")
        Condition.ScatteredShowers -> WeatherLook("scattered_showers_$sfx", "Scattered showers")
        Condition.ScatteredStorms -> WeatherLook("isolated_scattered_thunderstorms_$sfx", "Scattered thunderstorms")
        Condition.IsolatedStorms -> WeatherLook("isolated_thunderstorms", "Isolated thunderstorms")
        Condition.StrongStorms -> WeatherLook("strong_thunderstorms", "Thunderstorm")
        Condition.ScatteredSnow -> WeatherLook("scattered_snow_showers_$sfx", "Scattered snow showers")
        Condition.SnowShowers -> WeatherLook("showers_snow", "Snow")
        Condition.RainSnow -> WeatherLook("mixed_rain_snow", "Rain and snow")
        Condition.WintryMix -> WeatherLook("mixed_rain_hail_sleet", "Wintry mix")
    }
}

fun iconUrl(
    kind: Condition,
    isDay: Boolean,
    precipMm: Double = -1.0,
    cloud: Int = -1,
    windKmh: Double = 0.0,
    chance: Int = -1
): String {
    val file = weatherLook(kind, isDay, precipMm, cloud, windKmh, chance).file
    val updated = ICON_V0_FILES[file]
    return if (updated != null) ICON_BASE_V0 + updated + ".svg" else ICON_BASE + file + ".svg"
}

fun Condition.label(isDay: Boolean): String = tr(
    when (this) {
        Condition.Clear -> if (isDay) "Sunny" else "Clear"
        Condition.FewClouds -> "Mostly clear"
        Condition.PartlyCloudy -> "Partly cloudy"
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
        Condition.Windy -> "Windy"
        Condition.Icy -> "Icy"
        Condition.Flurries -> "Flurries"
        Condition.BlowingSnow -> "Blowing snow"
        Condition.HeavyRain -> "Heavy rain"
        Condition.ScatteredShowers -> "Scattered showers"
        Condition.ScatteredStorms -> "Scattered thunderstorms"
        Condition.IsolatedStorms -> "Isolated thunderstorms"
        Condition.StrongStorms -> "Thunderstorm"
        Condition.ScatteredSnow -> "Scattered snow showers"
        Condition.SnowShowers -> "Snow"
        Condition.RainSnow -> "Rain and snow"
        Condition.WintryMix -> "Wintry mix"
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
        contentDescription = tr(weatherLook(kind, isDay, precipMm, cloud, windKmh, chance).title),
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
