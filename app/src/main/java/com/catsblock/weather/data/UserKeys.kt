package com.catsblock.weather.data

object UserKeys {

    @Volatile
    private var values: Map<WeatherSource, String> = emptyMap()

    fun update(map: Map<WeatherSource, String>) {
        values = map
    }

    fun forPath(path: String): String? {
        val source = when {
            path.endsWith("/v1/openweather") -> WeatherSource.OpenWeather
            path.endsWith("/v1/weather") -> WeatherSource.WeatherApi
            path.endsWith("/v1/visual") -> WeatherSource.VisualCrossing
            else -> return null
        }
        return values[source]?.takeIf { it.isNotBlank() }
    }
}
