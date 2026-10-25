package com.catsblock.weather.data

import com.catsblock.weather.BuildConfig

internal object SecretKeys {

    fun baseUrl(): String {
        val value = BuildConfig.CLOUDFLARE_WEBSITE.trim().trimEnd('/')
        return if (value.startsWith("https://")) value else ""
    }

    fun available(source: WeatherSource): Boolean = when (source) {
        WeatherSource.OpenMeteo -> true
        WeatherSource.WeatherApi, WeatherSource.VisualCrossing -> baseUrl().isNotEmpty()
    }
}
