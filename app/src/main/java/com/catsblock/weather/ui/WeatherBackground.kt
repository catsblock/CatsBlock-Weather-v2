package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.catsblock.weather.data.Condition
import com.catsblock.weather.kit.BackgroundKit
import com.catsblock.weather.media.MediaCache
import java.io.File
import java.time.LocalTime

private const val IMAGE_BASE =
    "https://raw.githubusercontent.com/catsblock/CatsBlock-Weather-v2/main/weather/image/"

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

private fun defaultImageUrl(kind: Condition, phase: SkyPhase): String = IMAGE_BASE + when (kind) {
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

private fun backgroundGradient(kind: Condition, isDay: Boolean): List<Color> {
    if (!isDay) {
        return when (kind) {
            Condition.Storm -> listOf(Color(0xFF05070D), Color(0xFF0E131C), Color(0xFF1B2430))
            Condition.Rain, Condition.Drizzle, Condition.Hail, Condition.Sleet ->
                listOf(Color(0xFF0A0F18), Color(0xFF151D2A), Color(0xFF263344))
            Condition.Snow, Condition.HeavySnow, Condition.Blizzard ->
                listOf(Color(0xFF111A27), Color(0xFF223046), Color(0xFF3A4B63))
            else -> listOf(Color(0xFF050B1F), Color(0xFF0B1B3F), Color(0xFF1B2D5C))
        }
    }
    return when (kind) {
        Condition.Clear -> listOf(Color(0xFF2F80ED), Color(0xFF56CCF2), Color(0xFF9CD8F5))
        Condition.FewClouds -> listOf(Color(0xFF3A7BD5), Color(0xFF6DB3F2), Color(0xFFA6D1F0))
        Condition.MoreClouds -> listOf(Color(0xFF4A78A8), Color(0xFF7FA3C7), Color(0xFFB4C7D8))
        Condition.Cloudy -> listOf(Color(0xFF4B5563), Color(0xFF6B7684), Color(0xFF8A95A1))
        Condition.Fog -> listOf(Color(0xFF5A6B78), Color(0xFF7F909C), Color(0xFFA7B4BD))
        Condition.Drizzle -> listOf(Color(0xFF3B4A5C), Color(0xFF55677B), Color(0xFF75879A))
        Condition.Rain -> listOf(Color(0xFF232E3B), Color(0xFF364555), Color(0xFF4F6174))
        Condition.Storm -> listOf(Color(0xFF0F131C), Color(0xFF1E2531), Color(0xFF34404F))
        Condition.Sleet, Condition.Hail -> listOf(Color(0xFF3F4F60), Color(0xFF5E7184), Color(0xFF8799AA))
        Condition.Snow -> listOf(Color(0xFF6C8299), Color(0xFF93A9BE), Color(0xFFBFD0DF))
        Condition.HeavySnow, Condition.Blizzard -> listOf(Color(0xFF55697D), Color(0xFF7D93A9), Color(0xFFA9BCCD))
    }
}

@Composable
fun WeatherBackground(
    kind: Condition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    time: LocalTime? = null
) {
    val context = LocalContext.current
    val version by BackgroundKit.version.collectAsState()
    val custom = remember(kind, isDay, version) { BackgroundKit.visual(context, kind, isDay) }
    val phase = skyPhase(time, isDay)
    val imageUrl = defaultImageUrl(kind, phase)
    val downloaded by produceState<File?>(null, imageUrl, custom) {
        value = null
        if (custom == null) value = MediaCache.fetch(context, listOf(imageUrl))
    }
    val file = custom ?: downloaded
    Box(modifier) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(backgroundGradient(kind, isDay))))
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        if (!isDay && custom == null && !(hasPhaseImage(kind) && phase != SkyPhase.Day)) {
            Box(Modifier.fillMaxSize().background(Color(0xFF050B1F).copy(alpha = 0.62f)))
        }
    }
}
