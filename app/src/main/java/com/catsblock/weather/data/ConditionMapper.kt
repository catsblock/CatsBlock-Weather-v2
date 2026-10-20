package com.catsblock.weather.data

fun wmoCondition(code: Int): Condition = when (code) {
    0 -> Condition.Clear
    1 -> Condition.FewClouds
    2 -> Condition.MoreClouds
    3 -> Condition.Cloudy
    45, 48 -> Condition.Fog
    in 51..67 -> Condition.Rain
    in 71..77 -> Condition.Snow
    in 80..82 -> Condition.Rain
    85, 86 -> Condition.Snow
    in 95..99 -> Condition.Storm
    else -> Condition.Cloudy
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
