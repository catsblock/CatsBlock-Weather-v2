package com.catsblock.weather.ui.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.catsblock.weather.data.Condition
import com.catsblock.weather.ui.atmosphere.LocalShadersDisabled
import com.catsblock.weather.ui.atmosphere.glassRain
import kotlin.random.Random

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

@Composable
fun Modifier.rainDrops(intensity: Float, snow: Float = 0f): Modifier {
    if (LocalShadersDisabled.current) return this
    if ((intensity <= 0f && snow <= 0f) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val seed = remember { Random.nextFloat() * 40f }
    return this.glassRain(intensity, seed, snow)
}
