package com.catsblock.weather.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    tint: Color = Color.White.copy(alpha = 0.08f)
): Modifier = this
    .clip(shape)
    .hazeEffect(state = state, style = HazeMaterials.ultraThin(tint)) {
        blurRadius = 24.dp
        noiseFactor = 0.05f
    }
