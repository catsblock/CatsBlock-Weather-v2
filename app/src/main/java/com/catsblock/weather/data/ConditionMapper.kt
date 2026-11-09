package com.catsblock.weather.data

private const val WINDY_KMH = 55.0

fun wmoCondition(code: Int, windKmh: Double = 0.0): Condition {
    val snow = when (code) {
        71 -> Condition.Flurries
        73 -> Condition.Snow
        85 -> Condition.ScatteredSnow
        86 -> Condition.SnowShowers
        75 -> Condition.HeavySnow
        77 -> Condition.Hail
        else -> null
    }
    if (snow != null) {
        return when {
            snow == Condition.Hail -> snow
            windKmh >= 45.0 && snow != Condition.ScatteredSnow -> Condition.Blizzard
            windKmh >= 25.0 && snow == Condition.Flurries -> Condition.BlowingSnow
            else -> snow
        }
    }
    return when (code) {
        in 0..3 -> if (windKmh >= WINDY_KMH) Condition.Windy else when (code) {
            0 -> Condition.Clear
            1 -> Condition.FewClouds
            2 -> Condition.PartlyCloudy
            else -> Condition.Cloudy
        }
        45, 48 -> Condition.Fog
        51, 53, 55, 61 -> Condition.Drizzle
        80 -> Condition.ScatteredShowers
        56, 67 -> Condition.Icy
        57 -> Condition.WintryMix
        66 -> Condition.RainSnow
        63, 81 -> Condition.Rain
        65, 82 -> Condition.HeavyRain
        95 -> Condition.IsolatedStorms
        96 -> Condition.ScatteredStorms
        99 -> Condition.StrongStorms
        in 97..98 -> Condition.Storm
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
