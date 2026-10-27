package com.catsblock.weather.ui.atmosphere

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.WeatherDecisionEngine
import com.catsblock.weather.ui.glass.rainIntensity
import com.catsblock.weather.ui.glass.snowIntensity
import java.time.LocalTime

private const val WIND_REFERENCE_KMH = 50.0

private fun channel(from: Int, to: Int, t: Float): Int = (from + (to - from) * t).toInt().coerceIn(0, 255)

private fun blend(a: Int, b: Int, t: Float): Int {
    val r = channel((a shr 16) and 0xFF, (b shr 16) and 0xFF, t)
    val g = channel((a shr 8) and 0xFF, (b shr 8) and 0xFF, t)
    val bl = channel(a and 0xFF, b and 0xFF, t)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
}

private const val MORNING_SUN = 0xFFFFE0A0.toInt()
private const val NOON_SUN = 0xFFFFF4E2.toInt()
private const val AFTERNOON_SUN = 0xFFFFB05C.toInt()

private fun sunTintFor(tod: Float): Int =
    if (tod < 0.5f) blend(MORNING_SUN, NOON_SUN, tod * 2f) else blend(NOON_SUN, AFTERNOON_SUN, (tod - 0.5f) * 2f)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AtmosphereSky(
    kind: Condition,
    cloudPercent: Int,
    windKmh: Double,
    time: LocalTime?,
    dayOfYear: Int,
    modifier: Modifier = Modifier
) {
    val state = remember { AtmosphereState() }
    val minutes = time?.let { it.hour * 60 + it.minute } ?: 720
    val tod = WeatherDecisionEngine.timeOfDay(minutes)
    val cover = if (cloudPercent >= 0) cloudPercent / 100f else WeatherDecisionEngine.coverFor(kind)
    val rain = rainIntensity(kind)
    val snow = snowIntensity(kind)
    val storm = if (kind == Condition.Storm) 1f else 0f
    val wind = (windKmh / WIND_REFERENCE_KMH).toFloat().coerceIn(0f, 1f)
    val alto = WeatherDecisionEngine.altocumulusChance(cover, dayOfYear)
    val seed = (dayOfYear % 97).toFloat()
    SideEffect {
        state.tod = tod
        state.cover = cover
        state.rain = rain
        state.storm = storm
        state.snow = snow
        state.wind = wind
        state.altoChance = alto
        state.seed = seed
        state.sunTint = sunTintFor(tod)
    }
    Box(modifier.atmosphere(state))
}
