package com.catsblock.weather.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

val LocalHazeState = compositionLocalOf { HazeState() }

fun Modifier.hazeBackdropSource(state: HazeState): Modifier = hazeSource(state = state)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.frostedGlassCard(
    state: HazeState,
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.10f)
): Modifier = this
    .clip(shape)
    .hazeEffect(state = state, style = HazeMaterials.ultraThin(tint)) {
        blurRadius = 30.dp
        noiseFactor = 0.04f
    }
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.07f),
                Color.White.copy(alpha = 0.12f)
            ),
            start = Offset.Zero,
            end = Offset(900f, 1400f)
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.65f),
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.30f)
            ),
            start = Offset.Zero,
            end = Offset(900f, 1400f)
        ),
        shape
    )
