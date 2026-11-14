package com.catsblock.weather.ui.sky

import android.app.ActivityManager
import android.content.Context
import android.os.Build

enum class SkyTier(
    val octaves: Int,
    val shadowSamples: Int,
    val atmosphereSteps: Int,
    val renderScale: Float,
    val maxFps: Int
) {
    Minimal(0, 0, 0, 1f, 0),
    Low(2, 0, 5, 0.35f, 12),
    Medium(3, 1, 6, 0.5f, 20),
    High(4, 2, 8, 0.65f, 30),
    Ultra(5, 3, 10, 0.8f, 45);

    val usesShader: Boolean get() = this != Minimal

    fun lowered(): SkyTier = if (ordinal <= Low.ordinal) this else entries[ordinal - 1]
}

fun detectSkyTier(context: Context, powerSave: Boolean): SkyTier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return SkyTier.Minimal
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return SkyTier.Low
    val base = when {
        manager.isLowRamDevice -> SkyTier.Low
        manager.memoryClass < 128 -> SkyTier.Low
        manager.memoryClass < 192 -> SkyTier.Medium
        manager.memoryClass < 320 -> SkyTier.High
        else -> SkyTier.Ultra
    }
    return if (powerSave && base.ordinal > SkyTier.Low.ordinal) SkyTier.Low else base
}
