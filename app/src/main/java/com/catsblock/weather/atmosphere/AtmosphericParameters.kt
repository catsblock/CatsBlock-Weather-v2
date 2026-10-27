package com.catsblock.weather.atmosphere

import android.graphics.Color
import com.catsblock.weather.data.Condition
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.pow

object AtmosphericParameters {
    fun create(
        kind: Condition,
        isDay: Boolean,
        time: LocalTime?,
        cloudCoverPercent: Double,
        windSpeedKmh: Double
    ): AtmosphereConfig {
        val hour = time?.hour ?: if (isDay) 14 else 0
        val minute = time?.minute ?: 0
        val minuteOfDay = hour * 60 + minute
        val kelvin = when {
            !isDay -> 8500f
            minuteOfDay in 330..719 -> lerp(3500f, 5000f, (minuteOfDay - 330f) / 389f)
            minuteOfDay in 720..1079 -> lerp(3000f, 2000f, (minuteOfDay - 720f) / 359f)
            else -> 5200f
        }
        val solarPhase = ((minuteOfDay - 360f) / 720f * PI.toFloat()).coerceIn(0f, PI.toFloat())
        val elevation = if (isDay) sin(solarPhase).coerceIn(0f, 1f) else 0f
        val cloudCover = if (cloudCoverPercent >= 0.0) {
            (cloudCoverPercent / 100.0).toFloat().coerceIn(0f, 1f)
        } else {
            estimateCloudCover(kind) / 100f
        }
        val rain = when (kind) {
            Condition.Drizzle -> 0.35f
            Condition.Rain -> 0.7f
            Condition.Storm -> 1f
            Condition.Sleet -> 0.5f
            Condition.Hail -> 0.6f
            else -> 0f
        }
        val skyColor = if (isDay) Color.rgb(125, 174, 209) else Color.rgb(24, 43, 69)
        return AtmosphereConfig(
            cloudCoverFraction = cloudCover,
            sunKelvin = kelvin,
            sunElevation = elevation,
            windKmh = windSpeedKmh.toFloat().coerceIn(0f, 130f),
            rainIntensity = rain,
            sunColorArgb = kelvinToColor(kelvin),
            skyColorArgb = skyColor
        )
    }

    fun estimateCloudCover(kind: Condition): Float = when (kind) {
        Condition.Clear -> 4f
        Condition.FewClouds -> 22f
        Condition.MoreClouds -> 48f
        Condition.Cloudy -> 88f
        Condition.Fog -> 94f
        Condition.Drizzle -> 93f
        Condition.Rain -> 96f
        Condition.Storm -> 100f
        Condition.Sleet -> 94f
        Condition.Hail -> 96f
        Condition.Snow -> 93f
        Condition.HeavySnow -> 97f
        Condition.Blizzard -> 100f
    }

    private fun kelvinToColor(kelvin: Float): Int {
        val temperature = kelvin.coerceIn(1000f, 12000f) / 100f
        val red: Double
        val green: Double
        val blue: Double
        if (temperature <= 66f) {
            red = 255.0
            green = (99.4708025861 * kotlin.math.ln(temperature.toDouble()) - 161.1195681661).coerceIn(0.0, 255.0)
            blue = if (temperature <= 19f) 0.0 else {
                (138.5177312231 * kotlin.math.ln(temperature.toDouble() - 10.0) - 305.0447927307).coerceIn(0.0, 255.0)
            }
        } else {
            red = (329.698727446 * (temperature.toDouble() - 60.0).pow(-0.1332047592)).coerceIn(0.0, 255.0)
            green = (288.1221695283 * (temperature.toDouble() - 60.0).pow(-0.0755148492)).coerceIn(0.0, 255.0)
            blue = 255.0
        }
        return Color.rgb(red.toInt(), green.toInt(), blue.toInt())
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount.coerceIn(0f, 1f)
}
