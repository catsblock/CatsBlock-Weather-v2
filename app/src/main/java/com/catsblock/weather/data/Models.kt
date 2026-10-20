package com.catsblock.weather.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

enum class Condition { Clear, FewClouds, MoreClouds, Cloudy, Rain, Storm, Snow, Fog }

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
    val aqi: Double?
) {
    val kind: Condition get() = wmoCondition(code)
}

data class Day(
    val date: LocalDate,
    val max: Double,
    val min: Double,
    val code: Int,
    val precipSum: Double,
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
    val aqi: Double?
)

data class WeatherBundle(
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    val alerts: List<String>,
    val usedAccuWeather: Boolean
)

data class CitySummary(
    val kind: Condition,
    val isDay: Boolean,
    val temp: Double,
    val hasAlert: Boolean
)

data class AppSettings(
    val fahrenheit: Boolean = false,
    val mobileData: Boolean = true,
    val sounds: Boolean = true,
    val apiKey: String = ""
)

data class Continent(val title: String, val cities: List<City>)
