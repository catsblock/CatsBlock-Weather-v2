package com.catsblock.weather.data

import com.catsblock.weather.BuildConfig

internal object SecretKeys {

    fun baseUrl(): String {
        val value = BuildConfig.CLOUDFLARE_WEBSITE.trim().trimEnd('/')
        return if (value.startsWith("https://")) value else ""
    }

    fun appToken(): String = BuildConfig.APP_TOKEN.trim()

    fun available(source: WeatherSource): Boolean = when (source) {
        WeatherSource.OpenMeteo -> true
        WeatherSource.WeatherApi, WeatherSource.VisualCrossing, WeatherSource.OpenWeather -> baseUrl().isNotEmpty()
    }
}
