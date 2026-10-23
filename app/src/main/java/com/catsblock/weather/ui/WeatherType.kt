// ===== WeatherType.kt =====
package com.catsblock.weather.ui

private const val B = "https://raw.githubusercontent.com/mrdarrengriffin/google-weather-icons/refs/heads/main/sets"

enum class WeatherType(val label: String, val iconUrl: String) {
    SUNNY("Ensolarado", "$B/set-6/light/sunny.svg"),
    MOSTLY_SUNNY("Predominantemente ensolarado", "$B/set-5/light/mostly_sunny.svg"),
    PARTLY_CLOUDY("Predominantemente nublado", "$B/set-5/light/partly_cloudy.svg"),
    CLOUDY("Nublado", "$B/set-5/light/cloudy.svg"),
    CLEAR_NIGHT("Limpo", "$B/set-6/light/clear_night.svg"),
    MOSTLY_CLEAR_NIGHT("Principalmente limpo", "$B/set-6/light/mostly_clear_night.svg"),
    PARTLY_CLOUDY_NIGHT("Predominantemente nublado", "$B/set-6/light/partly_cloudy_night.svg"),
    DRIZZLE("Chuva", "$B/set-6/light/drizzle.svg"),
    HEAVY_RAIN("Chuva moderada a forte", "$B/set-6/light/heavy_rain.svg"),
    FLURRIES("Neve", "$B/set-6/light/flurries.svg"),
    HEAVY_SNOW("Neve moderada a forte", "$B/set-6/light/heavy_snow.svg"),
    BLOWING_SNOW("Vento com neve", "$B/set-6/light/blowing_snow.svg"),
    WINTRY_MIX("Neve, gelo e chuva", "$B/set-6/light/wintry_mix.svg"),
    SLEET_HAIL("Gelo", "$B/set-6/light/sleet_hail.svg"),
    STORM("Tempestade", "$B/set-6/light/strong_thunderstorms.svg"),
    WINDY("Névoa / Vento", "$B/set-6/light/windy.svg");

    val isNight get() = this == CLEAR_NIGHT || this == MOSTLY_CLEAR_NIGHT || this == PARTLY_CLOUDY_NIGHT

    enum class Fx { NONE, RAIN, SNOW, STORM }

    val fx: Fx
        get() = when (this) {
            CLOUDY, DRIZZLE, HEAVY_RAIN, WINTRY_MIX, SLEET_HAIL -> Fx.RAIN
            FLURRIES, HEAVY_SNOW, BLOWING_SNOW -> Fx.SNOW
            STORM -> Fx.STORM
            else -> Fx.NONE
        }

    val intensity: Float
        get() = when (this) {
            CLOUDY -> 0.3f
            DRIZZLE -> 0.5f
            HEAVY_RAIN, STORM, HEAVY_SNOW, BLOWING_SNOW -> 1f
            WINTRY_MIX, SLEET_HAIL -> 0.8f
            FLURRIES -> 0.55f
            else -> 0f
        }

    companion object {
        fun fromWmo(code: Int, isDay: Boolean): WeatherType = when (code) {
            0 -> if (isDay) SUNNY else CLEAR_NIGHT
            1 -> if (isDay) MOSTLY_SUNNY else MOSTLY_CLEAR_NIGHT
            2 -> if (isDay) PARTLY_CLOUDY else PARTLY_CLOUDY_NIGHT
            3 -> CLOUDY
            45, 48 -> WINDY
            51, 53, 55, 56, 57, 61, 80 -> DRIZZLE
            63, 65, 81, 82 -> HEAVY_RAIN
            66, 67 -> WINTRY_MIX
            71, 73, 85 -> FLURRIES
            75, 86 -> HEAVY_SNOW
            77 -> BLOWING_SNOW
            95, 96, 99 -> STORM
            else -> if (isDay) PARTLY_CLOUDY else PARTLY_CLOUDY_NIGHT
        }
    }
}
