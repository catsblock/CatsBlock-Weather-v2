package com.catsblock.weather.ui.sky

import android.content.Context
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class SkyTier(
    val octaves: Int,
    val shadowSamples: Int,
    val atmosphereSteps: Int,
    val renderScale: Float,
    val maxFps: Int,
    val reachKm: Float,
    val liteShadows: Boolean,
    val opaqueCut: Float,
    val cirrus: Boolean
) {
    Minimal(0, 0, 0, 1f, 0, 0f, true, 0.9f, false),
    VeryLow(2, 0, 4, 0.3f, 15, 1.8f, true, 0.9f, false),
    Low(2, 0, 5, 0.4f, 20, 2.2f, true, 0.95f, true),
    Medium(3, 1, 6, 0.55f, 30, 5f, true, 0.99f, true),
    High(4, 2, 8, 0.8f, 60, 8f, false, 0.995f, true),
    Ultra(5, 3, 10, 1f, 60, 0f, false, 0.999f, true);

    val usesShader: Boolean get() = this != Minimal

    fun targetFps(refreshRateHz: Float): Int {
        if (this == Minimal) return 0
        val refresh = refreshRateHz.takeIf { it.isFinite() && it > 0f } ?: 60f
        val cap = when (this) {
            VeryLow -> 15f
            Low -> 20f
            Medium -> 30f
            High, Ultra -> 60f
            Minimal -> return 0
        }
        val divisor = ceil(refresh / cap).toInt().coerceAtLeast(1)
        return (refresh / divisor).roundToInt().coerceIn(1, 60)
    }

    fun lowered(): SkyTier = if (ordinal <= VeryLow.ordinal) this else entries[ordinal - 1]
}

fun detectSkyTier(powerSave: Boolean): SkyTier = if (powerSave) SkyTier.Low else SkyTier.Medium

fun detectSkyTier(context: Context, powerSave: Boolean): SkyTier = detectSkyTier(powerSave)
