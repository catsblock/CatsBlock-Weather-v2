package com.catsblock.weather.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.catsblock.weather.data.Condition
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val IMAGE_BASE =
    "https://raw.githubusercontent.com/catsblock/CatsBlock-Weather-v2/main/weather/image/"

private fun dayPhoto(kind: Condition): String = when (kind) {
    Condition.Clear, Condition.FewClouds -> "Sunny.jpg"
    Condition.MoreClouds -> "Partlycloud.jpg"
    Condition.Cloudy, Condition.Fog -> "cloudy.jpg"
    Condition.Drizzle, Condition.Rain -> "Rain.jpg"
    Condition.Storm -> "Storm.jpg"
    Condition.Sleet, Condition.Hail, Condition.Snow, Condition.HeavySnow, Condition.Blizzard -> "Ssnow.jpg"
}

private fun photoFor(kind: Condition, isDay: Boolean): String? {
    if (isDay) return dayPhoto(kind)
    return when (kind) {
        Condition.Clear, Condition.FewClouds, Condition.MoreClouds, Condition.Fog -> null
        else -> dayPhoto(kind)
    }
}

private fun fallbackColors(kind: Condition, isDay: Boolean): List<Color> = when (kind) {
    Condition.Clear, Condition.FewClouds ->
        if (isDay) listOf(Color(0xFF2F80ED), Color(0xFF8EC5FC)) else listOf(Color(0xFF0B1026), Color(0xFF283B6E))
    Condition.MoreClouds, Condition.Cloudy, Condition.Fog ->
        if (isDay) listOf(Color(0xFF6C7A89), Color(0xFFB0BAC4)) else listOf(Color(0xFF222B38), Color(0xFF47515F))
    Condition.Drizzle, Condition.Rain ->
        if (isDay) listOf(Color(0xFF3E4C5E), Color(0xFF7A8896)) else listOf(Color(0xFF141B26), Color(0xFF2E3846))
    Condition.Storm -> listOf(Color(0xFF1C2230), Color(0xFF4A5366))
    Condition.Sleet, Condition.Hail, Condition.Snow, Condition.HeavySnow, Condition.Blizzard ->
        if (isDay) listOf(Color(0xFF8FA3B8), Color(0xFFDCE6F0)) else listOf(Color(0xFF2B3646), Color(0xFF5B6B80))
}

private data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val base: Float,
    val phase: Float,
    val speed: Int,
    val glow: Boolean
)

@Composable
private fun StarrySky(modifier: Modifier = Modifier) {
    val stars = remember {
        val random = Random(11)
        List(170) { i ->
            Star(
                x = random.nextFloat(),
                y = random.nextFloat() * 0.9f,
                radius = 0.5f + random.nextFloat() * 1.4f,
                base = 0.45f + random.nextFloat() * 0.55f,
                phase = random.nextFloat() * (2f * PI.toFloat()),
                speed = 1 + i % 3,
                glow = random.nextFloat() > 0.9f
            )
        }
    }
    val t by rememberInfiniteTransition(label = "stars").animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "twinkle"
    )
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF02040D), Color(0xFF081029), Color(0xFF14224A))
            )
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            stars.forEach { s ->
                val twinkle = 0.55f + 0.45f * sin(t * s.speed + s.phase)
                val alpha = (s.base * twinkle).coerceIn(0f, 1f)
                val center = Offset(s.x * size.width, s.y * size.height)
                if (s.glow) drawCircle(Color.White.copy(alpha = alpha * 0.18f), s.radius.dp.toPx() * 4f, center)
                drawCircle(Color.White.copy(alpha = alpha), s.radius.dp.toPx(), center)
            }
        }
    }
}

@Composable
fun WeatherBackground(kind: Condition, isDay: Boolean, modifier: Modifier = Modifier) {
    val photo = remember(kind, isDay) { photoFor(kind, isDay) }
    val fallback = remember(kind, isDay) { fallbackColors(kind, isDay) }
    Box(modifier) {
        Crossfade(
            targetState = photo to isDay,
            animationSpec = tween(800),
            label = "background"
        ) { (name, day) ->
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(fallback))) {
                if (name == null) {
                    StarrySky(Modifier.fillMaxSize())
                } else {
                    AsyncImage(
                        model = IMAGE_BASE + name,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!day) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF050B1E).copy(alpha = 0.52f)))
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.32f),
                    0.22f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.42f)
                )
            )
        )
    }
}
