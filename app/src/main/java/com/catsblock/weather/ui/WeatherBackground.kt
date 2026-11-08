package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    kind == Condition.Clear || kind == Condition.FewClouds || kind == Condition.MoreClouds

private fun backgroundAsset(kind: Condition, phase: SkyPhase): String = ASSET_BASE + when (kind) {
    Condition.Clear, Condition.FewClouds -> when (phase) {
        SkyPhase.Sunrise -> "Clean_sunrise.jpg"
        SkyPhase.Sunset -> "Clean_sunset.jpg"
        SkyPhase.Night -> "Clean_night.jpg"
        SkyPhase.Day -> "Sunny.jpg"
    }
    Condition.MoreClouds -> when (phase) {
        SkyPhase.Sunrise -> "Partly_cloudy_sunrise.jpg"
        SkyPhase.Sunset -> "Partly_cloudy_sunset.jpg"
        SkyPhase.Night -> "Partly_cloudy_night.jpg"
        SkyPhase.Day -> "Partlycloud.jpg"
    }
    Condition.Cloudy, Condition.Fog -> "cloudy.jpg"
    Condition.Drizzle, Condition.Rain, Condition.Sleet -> "Rain.jpg"
    Condition.Storm, Condition.Hail -> "Storm.jpg"
    Condition.Snow, Condition.HeavySnow, Condition.Blizzard -> "Ssnow.jpg"
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
