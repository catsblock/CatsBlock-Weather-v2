package com.catsblock.weather.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay

val LocalHazeState = compositionLocalOf { HazeState() }

class EntranceCounter {
    private var value = 0
    fun next(): Int = value++
}

val LocalEntrance = compositionLocalOf<EntranceCounter?> { null }

fun Modifier.hazeBackdropSource(state: HazeState): Modifier = hazeSource(state = state)

@Composable
fun Modifier.cardEntrance(): Modifier {
    val counter = LocalEntrance.current
    val order = remember { counter?.next() ?: 0 }
    val progress = remember { Animatable(if (counter == null) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (counter != null) {
            delay((order * 70L).coerceAtMost(700L))
            progress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}
