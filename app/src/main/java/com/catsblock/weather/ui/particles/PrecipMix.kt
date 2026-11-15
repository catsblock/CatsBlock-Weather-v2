package com.catsblock.weather.ui.particles

import androidx.compose.runtime.Immutable
import com.catsblock.weather.data.Condition
import kotlin.math.min

@Immutable
data class PrecipMix(
    val rain: Float = 0f,
    val snow: Float = 0f,
    val hail: Float = 0f,
    val freezing: Float = 0f,
    val thunder: Float = 0f,
    val tempC: Float = 10f
) {
    val active: Boolean get() = rain + snow + hail + freezing > 0.01f

    companion object {
        val None = PrecipMix()
    }
}

fun precipMixFor(kind: Condition, tempC: Double, precipMm: Double): PrecipMix {
    val t = tempC.toFloat()
    var rain = 0f
    var snow = 0f
    var hail = 0f
    var freezing = 0f
    var thunder = 0f
    when (kind) {
        Condition.Drizzle -> rain = 0.2f
        Condition.Rain -> rain = 0.5f
        Condition.HeavyRain -> rain = 0.85f
        Condition.ScatteredShowers -> rain = 0.4f
        Condition.Storm -> {
            rain = 0.85f
            thunder = 0.85f
        }
        Condition.ScatteredStorms -> {
            rain = 0.6f
            thunder = 0.85f
        }
        Condition.IsolatedStorms -> {
            rain = 0.45f
            thunder = 0.6f
        }
        Condition.StrongStorms -> {
            rain = 1f
            thunder = 1f
        }
        Condition.Hail -> {
            hail = 0.65f
            rain = 0.3f
        }
        Condition.Sleet -> {
            rain = 0.3f
            hail = 0.25f
            snow = 0.2f
        }
        Condition.Icy -> if (t <= 0.5f) freezing = 0.5f else rain = 0.25f
        Condition.RainSnow -> {
            rain = 0.35f
            snow = 0.4f
        }
        Condition.WintryMix -> {
            rain = 0.25f
            snow = 0.35f
            hail = 0.2f
        }
        Condition.Snow -> snow = 0.5f
        Condition.HeavySnow -> snow = 0.85f
        Condition.Blizzard -> snow = 1f
        Condition.Flurries -> snow = 0.22f
        Condition.BlowingSnow -> snow = 0.6f
        Condition.ScatteredSnow -> snow = 0.35f
        Condition.SnowShowers -> snow = 0.55f
        else -> Unit
    }
    if (t <= -0.5f) {
        freezing += rain
        rain = 0f
    }
    val scale = if (precipMm < 0.0) 1f else (0.55f + precipMm.toFloat() * 0.3f).coerceIn(0.4f, 1.3f)
    return PrecipMix(
        rain = min(rain * scale, 1.2f),
        snow = min(snow * scale, 1.2f),
        hail = min(hail * scale, 1.2f),
        freezing = min(freezing * scale, 1.2f),
        thunder = thunder,
        tempC = t
    )
}
