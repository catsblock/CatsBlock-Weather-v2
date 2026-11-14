package com.catsblock.weather.ui.sky

import android.app.ActivityManager
import android.content.Context
import android.os.Build

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

private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0

fun detectSkyTier(context: Context, powerSave: Boolean): SkyTier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return SkyTier.Minimal
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return SkyTier.Low
    val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
    val ramGb = info.totalMem / BYTES_PER_GB
    val heap = manager.memoryClass
    var base = when {
        manager.isLowRamDevice || ramGb < 2.8 || heap < 128 -> SkyTier.VeryLow
        ramGb < 3.8 || heap < 192 -> SkyTier.Low
        ramGb < 5.8 -> SkyTier.Medium
        ramGb < 9.0 -> SkyTier.High
        else -> SkyTier.Ultra
    }
    if (Runtime.getRuntime().availableProcessors() <= 4) base = base.lowered()
    return if (powerSave && base.ordinal > SkyTier.Low.ordinal) SkyTier.Low else base
}
