package com.catsblock.weather.data

import android.content.Context
import android.content.res.Configuration

object MapStyle {

    const val CREDIT = "© OpenStreetMap · CyclOSM · RainViewer"

    const val DARK_BASE = 0xFF45454A.toInt()
    const val LIGHT_BASE = 0xFFF5F5F5.toInt()

    val DARK_WEB = floatArrayOf(
        0.4592f, -1.1440f, -0.1152f, 0f, 244f,
        -0.3408f, -0.3440f, -0.1152f, 0f, 244f,
        -0.3408f, -1.1440f, 0.6848f, 0f, 244f,
        0f, 0f, 0f, 1f, 0f
    )

    val DARK_LIFT: FloatArray get() = DARK_WEB

    fun tileUrl(dark: Boolean, z: Int, x: Int, y: Int): String {
        val shard = "abc"[Math.floorMod(x + y, 3)]
        return "https://$shard.tile-cyclosm.fr/cyclosm/$z/$x/$y.png"
    }

    fun fallbackUrl(z: Int, x: Int, y: Int): String = "https://tile.openstreetmap.org/$z/$x/$y.png"

    fun isDark(context: Context, mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
