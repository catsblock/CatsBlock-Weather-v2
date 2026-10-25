package com.catsblock.weather.data

import com.catsblock.weather.i18n.AppLanguage
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
val BackgroundIntervalOptions: List<Int> = listOf(30, 60, 120, 240, 480, 840)

enum class WeatherSource { OpenMeteo, WeatherApi, VisualCrossing }

enum class WindUnit(val label: String, val factor: Double) {
    Kmh("km/h", 1.0),
    Mph("mph", 0.621371),
    Ms("m/s", 0.277778),
    Knots("kn", 0.539957)
}

enum class PressureUnit(val label: String, val title: String, val factor: Double, val decimals: Int) {
    Hpa("hPa", "Hectopascals (hPa)", 1.0, 0),
    Kpa("kPa", "Kilopascals (kPa)", 0.1, 1),
    InHg("inHg", "Inches of mercury (inHg)", 0.02953, 2),
    MmHg("mmHg", "Millimeters of mercury (mmHg)", 0.750062, 0)
}
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
    val utcOffsetSeconds: Int = 0,
    val alerts: List<GovAlert> = emptyList()
)

data class UsageStatus(
    val shortRemaining: Int = 20,
    val shortLimit: Int = 20,
    val longRemaining: Int = 150,
    val longLimit: Int = 150,
    val lockoutSeconds: Long = 0,
    val exhaustedSeconds: Long = 0,
    val usingFallback: Boolean = false,
    val weatherUsed: Int = 0,
    val weatherLimit: Int = 220,
    val weatherResetSeconds: Long = 0,
    val visualUsed: Int = 0,
    val visualLimit: Int = 75,
    val visualResetSeconds: Long = 0,
    val weatherMonthUsed: Int = 0,
    val weatherMonthLimit: Int = 590,
    val weatherMonthResetSeconds: Long = 0,
    val visualMonthUsed: Int = 0,
    val visualMonthLimit: Int = 260,
    val visualMonthResetSeconds: Long = 0
) {
    fun monthUsedFor(source: WeatherSource): Int = when (source) {
        WeatherSource.WeatherApi -> weatherMonthUsed
        WeatherSource.VisualCrossing -> visualMonthUsed
        else -> 0
    }

    fun monthLimitFor(source: WeatherSource): Int = when (source) {
        WeatherSource.WeatherApi -> weatherMonthLimit
        WeatherSource.VisualCrossing -> visualMonthLimit
        else -> 0
    }

    fun monthResetFor(source: WeatherSource): Long = when (source) {
        WeatherSource.WeatherApi -> weatherMonthResetSeconds
        WeatherSource.VisualCrossing -> visualMonthResetSeconds
        else -> 0
    }

    fun usedFor(source: WeatherSource): Int = when (source) {
        WeatherSource.WeatherApi -> weatherUsed
        WeatherSource.VisualCrossing -> visualUsed
        else -> 0
    }

    fun limitFor(source: WeatherSource): Int = when (source) {
        WeatherSource.WeatherApi -> weatherLimit
        WeatherSource.VisualCrossing -> visualLimit
        else -> 0
    }

    fun resetFor(source: WeatherSource): Long = when (source) {
        WeatherSource.WeatherApi -> weatherResetSeconds
        WeatherSource.VisualCrossing -> visualResetSeconds
        else -> 0
    }
}

data class CitySummary(
    val kind: Condition,
    val isDay: Boolean,
    val temp: Double,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val localTime: LocalTime? = null
)

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    val fahrenheit: Boolean = false,
    val weatherSource: WeatherSource = WeatherSource.OpenMeteo,
    val windUnit: WindUnit = WindUnit.Kmh,
    val pressureUnit: PressureUnit = PressureUnit.Hpa,
    val backgroundUpdates: Boolean = true,
    val backgroundIntervalMinutes: Int = 120,
    val theme: ThemeMode = ThemeMode.System,
    val compactHero: Boolean = false,
    val showLifeIndex: Boolean = true,
    val showPrecipitation: Boolean = true,
    val mobileData: Boolean = true,
    val sounds: Boolean = true,
    val language: AppLanguage = AppLanguage.English,
    val rainAlerts: Boolean = true,
    val rainLeadMinutes: Int = 60,
    val eclipseAlerts: Boolean = true,
    val eclipseLeadMinutes: Int = 60,
    val snowAlerts: Boolean = true,
    val snowLeadMinutes: Int = 60,
    val updateAlerts: Boolean = true
)

data class Continent(val title: String, val cities: List<City>)
