package com.catsblock.weather.data

import com.catsblock.weather.i18n.AppLanguage
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

enum class Condition {
    Clear, FewClouds, MoreClouds, Cloudy, Fog,
    Drizzle, Rain, Storm,
    Sleet, Hail, Snow, HeavySnow, Blizzard
}

val Condition.isWet: Boolean
    get() = this == Condition.Drizzle || this == Condition.Rain || this == Condition.Storm ||
        this == Condition.Sleet || this == Condition.Hail || this == Condition.Snow ||
        this == Condition.HeavySnow || this == Condition.Blizzard

val RainLeadOptions: List<Int> = listOf(120, 60, 30, 15)
val EclipseLeadOptions: List<Int> = listOf(1440, 720, 360, 180, 120, 60, 30, 15)
val SnowLeadOptions: List<Int> = listOf(120, 60, 30, 15)

data class City(
    val name: String,
    val region: String,
    val country: String,
    val code: String,
    val lat: Double,
    val lon: Double
) {
    val id: String get() = String.format(Locale.US, "%.2f,%.2f", lat, lon)

    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("region", region)
        .put("country", country)
        .put("code", code)
        .put("lat", lat)
        .put("lon", lon)

    companion object {
        fun fromJson(o: JSONObject) = City(
            o.getString("name"),
            o.optString("region"),
            o.optString("country"),
            o.optString("code"),
            o.getDouble("lat"),
            o.getDouble("lon")
        )
    }
}

data class Hour(
    val time: LocalDateTime,
    val temp: Double,
    val feels: Double,
    val code: Int,
    val isDay: Boolean,
    val precip: Double,
    val windSpeed: Double,
    val windDir: Double,
    val humidity: Int,
    val uv: Double,
    val aqi: Double?,
    val pressure: Double = 1013.0,
    val visibility: Double = 10000.0
) {
    val kind: Condition get() = wmoCondition(code, windSpeed)
}

data class Day(
    val date: LocalDate,
    val max: Double,
    val min: Double,
    val code: Int,
    val precipSum: Double,
    val rainChance: Int,
    val sunrise: LocalDateTime,
    val sunset: LocalDateTime,
    val uvMax: Double
) {
    val kind: Condition get() = wmoCondition(code)
}

data class Current(
    val now: LocalDateTime,
    val temp: Double,
    val feels: Double,
    val text: String,
    val kind: Condition,
    val isDay: Boolean,
    val windSpeed: Double,
    val windDir: Double,
    val humidity: Int,
    val uv: Double,
    val aqi: Double?,
    val pressure: Double = 1013.0,
    val visibility: Double = 10000.0
)

data class RainSlot(
    val time: LocalDateTime,
    val mm: Double
)

data class WeatherBundle(
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    val rain: List<RainSlot> = emptyList(),
    val utcOffsetSeconds: Int = 0
)

data class CitySummary(
    val kind: Condition,
    val isDay: Boolean,
    val temp: Double,
    val min: Double = 0.0,
    val max: Double = 0.0
)

data class AppSettings(
    val fahrenheit: Boolean = false,
    val mobileData: Boolean = true,
    val sounds: Boolean = true,
    val language: AppLanguage = AppLanguage.English,
    val rainAlerts: Boolean = true,
    val rainLeadMinutes: Int = 60,
    val eclipseAlerts: Boolean = true,
    val eclipseLeadMinutes: Int = 60,
    val snowAlerts: Boolean = true,
    val snowLeadMinutes: Int = 60,
)

data class Continent(val title: String, val cities: List<City>)
