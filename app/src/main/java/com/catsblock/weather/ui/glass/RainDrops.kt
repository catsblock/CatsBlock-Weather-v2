package com.catsblock.weather.ui.glass

import androidx.compose.runtime.compositionLocalOf
import com.catsblock.weather.data.Condition

val LocalRainIntensity = compositionLocalOf { 0f }
val LocalSnowIntensity = compositionLocalOf { 0f }

fun rainIntensity(kind: Condition): Float = when (kind) {
    Condition.Drizzle -> 0.35f
    Condition.Rain -> 0.7f
    Condition.Storm -> 1f
    Condition.Sleet -> 0.5f
    Condition.Hail -> 0.6f
    else -> 0f
}

fun snowIntensity(kind: Condition): Float = when (kind) {
    Condition.Snow -> 0.5f
    Condition.HeavySnow -> 0.85f
    Condition.Blizzard -> 1f
    else -> 0f
}
