package com.catsblock.weather.ui.glass

import com.catsblock.weather.data.Condition

fun snowIntensity(kind: Condition): Float = when (kind) {
    Condition.Snow -> 0.5f
    Condition.HeavySnow -> 0.85f
    Condition.Blizzard -> 1f
    Condition.Flurries -> 0.25f
    Condition.BlowingSnow -> 0.6f
    Condition.ScatteredSnow -> 0.4f
    Condition.SnowShowers -> 0.6f
    Condition.RainSnow -> 0.3f
    else -> 0f
}
