package com.catsblock.weather.data

import com.catsblock.weather.BuildConfig

internal object SecretKeys {

    fun baseUrl(): String = BuildConfig.CLOUDFLARE_WEBSITE.trim().trimEnd('/')

    fun available(source: WeatherSource): Boolean = when (source) {
        WeatherSource.OpenMeteo -> true
        WeatherSource.WeatherApi, WeatherSource.VisualCrossing -> baseUrl().isNotEmpty()
    }
}
