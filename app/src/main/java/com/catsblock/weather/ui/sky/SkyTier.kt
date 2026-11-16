package com.catsblock.weather.ui.sky

import android.content.Context
import android.os.Build
import android.os.PowerManager

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
    VeryLow(2, 0, 4, 0.3f, 8, 1.8f, true, 0.9f, false),
    Low(2, 0, 5, 0.4f, 12, 2.2f, true, 0.95f, true),
    Medium(3, 1, 6, 0.55f, 20, 5f, true, 0.99f, true),
    High(4, 2, 8, 0.8f, 30, 8f, false, 0.995f, true),
    Ultra(5, 3, 10, 1f, 60, 0f, false, 0.999f, true);

    val usesShader: Boolean get() = this != Minimal

    fun lowered(): SkyTier = if (ordinal <= VeryLow.ordinal) this else entries[ordinal - 1]
}

fun detectSkyTier(context: Context, powerSave: Boolean): SkyTier {
    var base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) SkyTier.High else SkyTier.Medium
    val thermalLimit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        manager?.currentThermalStatus?.let { status ->
            when {
                status >= PowerManager.THERMAL_STATUS_SEVERE -> SkyTier.Low
                status >= PowerManager.THERMAL_STATUS_MODERATE -> SkyTier.Medium
                else -> null
            }
        }
    } else null
    if (thermalLimit != null && base.ordinal > thermalLimit.ordinal) base = thermalLimit
    if (powerSave && base.ordinal > SkyTier.Low.ordinal) base = SkyTier.Low
    return base
}
