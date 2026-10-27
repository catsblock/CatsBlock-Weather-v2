package com.catsblock.weather.atmosphere

import androidx.annotation.StringRes
import com.catsblock.weather.R
import java.time.LocalDateTime

data class AtmosphereConfig(
    val cloudCoverFraction: Float,
    val sunKelvin: Float,
    val sunElevation: Float,
    val windKmh: Float,
    val rainIntensity: Float,
    val sunColorArgb: Int,
    val skyColorArgb: Int
)

data class WeatherDecisionInput(
    val pressureHpa: Double,
    val cloudCoverPercent: Double,
    val precipitationMmPerHour: Double,
    val windSpeedKmh: Double,
    val uvIndex: Double,
    val temperatureCelsius: Double
)

enum class WeatherDecision(
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int,
    val severity: Int
) {
    SevereWeather(R.string.weather_decision_severe_title, R.string.weather_decision_severe_detail, 4),
    RainLikely(R.string.weather_decision_rain_title, R.string.weather_decision_rain_detail, 3),
    StrongWind(R.string.weather_decision_wind_title, R.string.weather_decision_wind_detail, 3),
    HighUv(R.string.weather_decision_uv_title, R.string.weather_decision_uv_detail, 2),
    HeatStress(R.string.weather_decision_heat_title, R.string.weather_decision_heat_detail, 2),
    ColdStress(R.string.weather_decision_cold_title, R.string.weather_decision_cold_detail, 2),
    Overcast(R.string.weather_decision_overcast_title, R.string.weather_decision_overcast_detail, 1),
    Unsettled(R.string.weather_decision_unsettled_title, R.string.weather_decision_unsettled_detail, 2),
    Stable(R.string.weather_decision_stable_title, R.string.weather_decision_stable_detail, 0)
}

data class WeatherAssessment(
    val decision: WeatherDecision,
    val riskScore: Int
)

enum class WeatherMetric(
    @StringRes val titleRes: Int,
    @StringRes val unitRes: Int,
    @StringRes val guidanceRes: Int
) {
    CloudCover(R.string.cloud_cover_title, R.string.percent_unit, R.string.history_guidance_cloud_cover),
    Pressure(R.string.pressure_title, R.string.pressure_unit, R.string.history_guidance_pressure),
    Temperature(R.string.temperature_title, R.string.celsius_unit, R.string.history_guidance_temperature),
    Wind(R.string.wind_title, R.string.wind_unit, R.string.history_guidance_wind),
    Precipitation(R.string.precipitation_title, R.string.precipitation_unit, R.string.history_guidance_precipitation),
    UvIndex(R.string.uv_index_title, R.string.uv_index_unit, R.string.history_guidance_uv),
    Humidity(R.string.humidity_title, R.string.percent_unit, R.string.history_guidance_humidity)
}

data class WeatherHistoryRecord(
    val timestamp: LocalDateTime,
    val cloudCoverPercent: Double,
    val pressureHpa: Double,
    val temperatureCelsius: Double,
    val windSpeedKmh: Double,
    val precipitationMmPerHour: Double,
    val uvIndex: Double,
    val humidityPercent: Double,
    val cloudCoverEstimated: Boolean
) {
    fun value(metric: WeatherMetric): Double = when (metric) {
        WeatherMetric.CloudCover -> cloudCoverPercent
        WeatherMetric.Pressure -> pressureHpa
        WeatherMetric.Temperature -> temperatureCelsius
        WeatherMetric.Wind -> windSpeedKmh
        WeatherMetric.Precipitation -> precipitationMmPerHour
        WeatherMetric.UvIndex -> uvIndex
        WeatherMetric.Humidity -> humidityPercent
    }
}
