package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.catsblock.weather.data.Condition
import com.catsblock.weather.kit.BackgroundKit
import java.time.LocalTime

private const val ASSET_BASE = "file:///android_asset/backgrounds/"

private enum class SkyPhase { Day, Sunrise, Sunset, Night }

private const val SUNRISE_START = 5 * 60 + 30
private const val SUNRISE_END = 8 * 60
private const val SUNSET_START = 17 * 60
private const val SUNSET_END = 18 * 60 + 40

private fun skyPhase(time: LocalTime?, isDay: Boolean): SkyPhase {
    if (time != null) {
        val minutes = time.hour * 60 + time.minute
        if (minutes in SUNRISE_START..SUNRISE_END) return SkyPhase.Sunrise
        if (minutes in SUNSET_START..SUNSET_END) return SkyPhase.Sunset
    }
    return if (isDay) SkyPhase.Day else SkyPhase.Night
}

private fun hasPhaseImage(kind: Condition): Boolean =
    kind == Condition.Clear || kind == Condition.FewClouds ||
        kind == Condition.PartlyCloudy || kind == Condition.MoreClouds

private fun backgroundAsset(kind: Condition, phase: SkyPhase): String = ASSET_BASE + when (kind) {
    Condition.Clear, Condition.FewClouds -> when (phase) {
        SkyPhase.Sunrise -> "Clean_sunrise.jpg"
        SkyPhase.Sunset -> "Clean_sunset.jpg"
        SkyPhase.Night -> "Clean_night.jpg"
        SkyPhase.Day -> "Sunny.jpg"
    }
    Condition.PartlyCloudy, Condition.MoreClouds -> when (phase) {
        SkyPhase.Sunrise -> "Partly_cloudy_sunrise.jpg"
        SkyPhase.Sunset -> "Partly_cloudy_sunset.jpg"
        SkyPhase.Night -> "Partly_cloudy_night.jpg"
        SkyPhase.Day -> "Partlycloud.jpg"
    }
    Condition.Cloudy, Condition.Fog, Condition.Windy -> "cloudy.jpg"
    Condition.Drizzle, Condition.Rain, Condition.Sleet, Condition.Icy, Condition.HeavyRain,
    Condition.ScatteredShowers, Condition.RainSnow, Condition.WintryMix -> "Rain.jpg"
    Condition.Storm, Condition.Hail, Condition.ScatteredStorms, Condition.IsolatedStorms,
    Condition.StrongStorms -> "Storm.jpg"
    Condition.Snow, Condition.HeavySnow, Condition.Blizzard, Condition.Flurries, Condition.BlowingSnow,
    Condition.ScatteredSnow, Condition.SnowShowers -> "Ssnow.jpg"
}

class SkyColors(val base: Color, val card: Color, val accent: Color)

val LocalSkyColors = compositionLocalOf { SkyColors(Color(0xFF0E1A28), Color(0xFF16273A), Color(0xFFCDE6FF)) }

private fun palette(base: Long, card: Long, accent: Long) = SkyColors(Color(base), Color(card), Color(accent))

fun skyColors(kind: Condition, isDay: Boolean, time: LocalTime?): SkyColors {
    val phase = skyPhase(time, isDay)
    val phased = hasPhaseImage(kind)
    val colors = when (kind) {
        Condition.Clear, Condition.FewClouds -> when (phase) {
            SkyPhase.Sunrise -> palette(0xFF2E2145, 0xFF47305F, 0xFFFFD1A8)
            SkyPhase.Sunset -> palette(0xFF3A1F2E, 0xFF55303F, 0xFFFFC38A)
            SkyPhase.Night -> palette(0xFF060D1F, 0xFF0F1B38, 0xFFB8CCFF)
            SkyPhase.Day -> palette(0xFF0B2A4A, 0xFF123B66, 0xFFBFE3FF)
        }
        Condition.PartlyCloudy, Condition.MoreClouds -> when (phase) {
            SkyPhase.Sunrise -> palette(0xFF2B2640, 0xFF3F3758, 0xFFFFD6B8)
            SkyPhase.Sunset -> palette(0xFF35242F, 0xFF4B3542, 0xFFFFCBA0)
            SkyPhase.Night -> palette(0xFF0A1226, 0xFF15213D, 0xFFC0D0F2)
            SkyPhase.Day -> palette(0xFF16314F, 0xFF21456B, 0xFFCFE5F8)
        }
        Condition.Cloudy, Condition.Fog, Condition.Windy -> palette(0xFF1F262E, 0xFF2D3740, 0xFFD3DCE4)
        Condition.Drizzle, Condition.Rain, Condition.Sleet, Condition.Icy, Condition.HeavyRain,
        Condition.ScatteredShowers, Condition.RainSnow, Condition.WintryMix -> palette(0xFF121C27, 0xFF1D2C3B, 0xFFB9D6EE)
        Condition.Storm, Condition.Hail, Condition.ScatteredStorms, Condition.IsolatedStorms,
        Condition.StrongStorms -> palette(0xFF0B0F18, 0xFF161D2B, 0xFFC6CCFF)
        Condition.Snow, Condition.HeavySnow, Condition.Blizzard, Condition.Flurries, Condition.BlowingSnow,
        Condition.ScatteredSnow, Condition.SnowShowers -> palette(0xFF2A3A4C, 0xFF3A4E64, 0xFFE6F1FB)
    }
    if (!isDay && !phased) {
        return SkyColors(
            lerp(colors.base, Color.Black, 0.4f),
            lerp(colors.card, Color.Black, 0.4f),
            colors.accent
        )
    }
    return colors
}

@Composable
fun WeatherBackground(
    kind: Condition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    time: LocalTime? = null,
    cloud: Int = -1,
    windKmh: Double = 0.0,
    dayOfYear: Int = 0,
    windDirDeg: Double = 0.0,
    pressureHpa: Double = Double.NaN,
    precipMm: Double = -1.0
) {
    val context = LocalContext.current
    val version by BackgroundKit.version.collectAsState()
    val custom = remember(kind, isDay, version) { BackgroundKit.visual(context, kind, isDay) }
    val phase = skyPhase(time, isDay)
    val model = remember(custom, kind, phase) {
        ImageRequest.Builder(context)
            .data(custom ?: backgroundAsset(kind, phase))
            .crossfade(true)
            .build()
    }
    Box(modifier) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B1220)))
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (!isDay && custom == null && !(hasPhaseImage(kind) && phase != SkyPhase.Day)) {
            Box(Modifier.fillMaxSize().background(Color(0xFF050B1F).copy(alpha = 0.55f)))
        }
    }
}
