package com.catsblock.weather.data

private const val WINDY_KMH = 55.0

fun wmoCondition(code: Int, windKmh: Double = 0.0): Condition {
    val snow = when (code) {
        71 -> Condition.Flurries
        73, 85 -> Condition.Snow
        75, 86 -> Condition.HeavySnow
        77 -> Condition.Hail
        else -> null
    }
    if (snow != null) {
        return when {
            snow == Condition.Hail -> snow
            windKmh >= 45.0 -> Condition.Blizzard
            windKmh >= 25.0 && snow == Condition.Flurries -> Condition.BlowingSnow
            else -> snow
        }
    }
    return when (code) {
        in 0..3 -> if (windKmh >= WINDY_KMH) Condition.Windy else when (code) {
            0 -> Condition.Clear
            1 -> Condition.FewClouds
            2 -> Condition.MoreClouds
            else -> Condition.Cloudy
        }
        45, 48 -> Condition.Fog
        51, 53, 55, 61, 80 -> Condition.Drizzle
        56, 57, 67 -> Condition.Icy
        66 -> Condition.Sleet
        63, 81 -> Condition.Rain
        65, 82 -> Condition.HeavyRain
        in 95..99 -> Condition.Storm
        else -> Condition.Cloudy
    }
}

fun wmoText(code: Int, isDay: Boolean): String = when (code) {
    0 -> if (isDay) "Sunny" else "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..65 -> "Rain"
    66, 67 -> "Freezing rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Cloudy"
}
