package com.catsblock.weather.data

import com.catsblock.weather.BuildConfig

internal object SecretKeys {

    private fun decode(data: String, mask: String): String {
        if (data.isEmpty() || mask.isEmpty()) return ""
        return runCatching {
            val key = ByteArray(mask.length / 2) { mask.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            val out = ByteArray(data.length / 2) { i ->
                (data.substring(i * 2, i * 2 + 2).toInt(16) xor (key[i % key.size].toInt() and 0xff)).toByte()
            }
            String(out, Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun weather(): String = decode(BuildConfig.WEATHER_KEY_DATA, BuildConfig.KEY_MASK)

    fun visual(): String = decode(BuildConfig.VISUAL_KEY_DATA, BuildConfig.KEY_MASK)

    fun available(source: WeatherSource): Boolean = when (source) {
        WeatherSource.OpenMeteo -> true
        WeatherSource.WeatherApi -> BuildConfig.WEATHER_KEY_DATA.isNotEmpty()
        WeatherSource.VisualCrossing -> BuildConfig.VISUAL_KEY_DATA.isNotEmpty()
    }
}
