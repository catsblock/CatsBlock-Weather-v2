package com.catsblock.weather.atmosphere

import kotlin.math.abs
import kotlin.math.roundToInt

object WeatherDecisionEngine {
    fun evaluate(input: WeatherDecisionInput): WeatherAssessment {
        val cloud = input.cloudCoverPercent.coerceIn(0.0, 100.0)
        val precipitation = input.precipitationMmPerHour.coerceAtLeast(0.0)
        val wind = input.windSpeedKmh.coerceAtLeast(0.0)
        val uv = input.uvIndex.coerceAtLeast(0.0)
        val temperature = input.temperatureCelsius
        val pressureDeficit = (1013.25 - input.pressureHpa).coerceAtLeast(0.0)
        val pressureSignal = (pressureDeficit / 22.0 * 16.0).coerceIn(0.0, 16.0)
        val cloudSignal = cloud / 100.0 * 14.0
        val rainSignal = (precipitation / 8.0 * 25.0).coerceIn(0.0, 25.0)
        val windSignal = (wind / 70.0 * 20.0).coerceIn(0.0, 20.0)
        val uvSignal = (uv / 11.0 * 12.0).coerceIn(0.0, 12.0)
        val thermalSignal = (abs(temperature - 22.0) / 28.0 * 13.0).coerceIn(0.0, 13.0)
        val score = (pressureSignal + cloudSignal + rainSignal + windSignal + uvSignal + thermalSignal)
            .roundToInt()
            .coerceIn(0, 100)
        val decision = when {
            precipitation >= 7.0 || wind >= 70.0 || temperature >= 41.0 || temperature <= -15.0 -> WeatherDecision.SevereWeather
            precipitation >= 1.0 || (cloud >= 85.0 && input.pressureHpa <= 1008.0) -> WeatherDecision.RainLikely
            wind >= 40.0 || (input.pressureHpa <= 990.0 && cloud >= 70.0) -> WeatherDecision.StrongWind
            uv >= 8.0 -> WeatherDecision.HighUv
            temperature >= 34.0 -> WeatherDecision.HeatStress
            temperature <= 2.0 -> WeatherDecision.ColdStress
            cloud >= 65.0 -> WeatherDecision.Overcast
            input.pressureHpa <= 1000.0 -> WeatherDecision.Unsettled
            else -> WeatherDecision.Stable
        }
        return WeatherAssessment(decision, score)
    }
}
