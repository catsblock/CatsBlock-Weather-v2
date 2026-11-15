package com.catsblock.weather.ui.particles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.catsblock.weather.ui.rememberPowerSaveMode
import com.catsblock.weather.ui.sky.SkyTier
import com.catsblock.weather.ui.sky.detectSkyTier
import kotlinx.coroutines.flow.first
import kotlin.math.sin

private const val MAX_FRAME_SECONDS = 0.25f

private fun tierScale(tier: SkyTier): Float = when (tier) {
    SkyTier.Minimal -> 0.65f
    SkyTier.VeryLow -> 0.5f
    SkyTier.Low -> 0.65f
    SkyTier.Medium -> 0.85f
    SkyTier.High -> 1f
    SkyTier.Ultra -> 1.2f
}

@Composable
fun AtmosphericPrecipitation(
    mix: PrecipMix,
    windSpeedKmh: Double,
    windDirDeg: Double,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val powerSave = rememberPowerSaveMode()
    val scale = remember(powerSave) { tierScale(detectSkyTier(context, powerSave)) }
    val sim = remember(density) { PrecipitationSimulation(density) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentMix by rememberUpdatedState(mix)
    val windRatio = (-sin(Math.toRadians(windDirDeg)) * (windSpeedKmh / 45.0).coerceIn(0.0, 1.0) * 0.55).toFloat()
    val currentWind by rememberUpdatedState(windRatio)
    val currentScale by rememberUpdatedState(scale)
    var frame by remember { mutableLongStateOf(0L) }

    DisposableEffect(sim) {
        onDispose { sim.clear() }
    }

    LaunchedEffect(sim, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = withFrameNanos { it }
            while (true) {
                if (!currentMix.active && !sim.alive) {
                    snapshotFlow { currentMix.active }.first { it }
                    last = withFrameNanos { it }
                    continue
                }
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SECONDS)
                last = now
                sim.step(dt, currentMix, currentWind, currentScale)
                frame = now
            }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.coerceIn(0f, 1f) }
            .onSizeChanged { sim.resize(it.width.toFloat(), it.height.toFloat()) }
    ) {
        if (frame >= 0L) sim.draw(this)
    }
}
