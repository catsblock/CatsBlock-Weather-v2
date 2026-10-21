package com.catsblock.weather.data

private val compass = arrayOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
)

fun compassLabel(deg: Double): String {
    val normalized = ((deg % 360.0) + 360.0) % 360.0
    return compass[((normalized / 22.5) + 0.5).toInt() % 16]
}

fun precipLabel(mm: Double): String = when {
    mm <= 0.0 -> "Dry"
    mm < 2.5 -> "Drizzle"
    mm <= 10.0 -> "Moderate"
    mm <= 50.0 -> "Heavy"
    else -> "Extreme"
}

fun windDescription(kmh: Double): String = when {
    kmh <= 15.0 -> "Tree leaves and small twigs sway gently."
    kmh <= 25.0 -> "Pleasant breeze that reduces the heat sensation. Leaves and small branches sway more."
    kmh <= 40.0 -> "Strong wind. Leaves sway heavily and small branches may snap depending on the direction."
    kmh < 60.0 -> "Danger. Raindrops hit much harder and travel farther. Fast droplets can hurt on impact."
    else -> "Extreme. Vehicles can flip and trees may break and fall. Staying indoors without protection is unsafe."
}

fun aqiStatus(v: Double): String = when {
    v <= 50.0 -> "Excellent"
    v <= 100.0 -> "Good"
    v <= 150.0 -> "Moderate"
    v <= 200.0 -> "Poor"
    else -> "Extreme"
}

fun aqiAdvice(v: Double): String = when {
    v <= 50.0 -> "Air quality is ideal. Enjoy outdoor activities."
    v <= 100.0 -> "Air quality is acceptable. Unusually sensitive people should limit long outdoor exertion."
    v <= 150.0 -> "Sensitive groups should reduce prolonged outdoor activity."
    v <= 200.0 -> "Everyone should limit outdoor exertion. Consider wearing a mask outside."
    else -> "Avoid outdoor activity and keep windows closed."
}

fun uvLevel(v: Double): String = when {
    v < 3.0 -> "Weak"
    v < 5.0 -> "Light"
    v < 7.0 -> "Moderate"
    v < 10.0 -> "Strong"
    else -> "Extreme"
}

fun uvAdvice(v: Double): String = when {
    v < 3.0 -> "No sunscreen needed for most people."
    v < 5.0 -> "Use SPF 30 sunscreen if you stay outside for long."
    v < 7.0 -> "Apply SPF 30 sunscreen and wear sunglasses."
    v < 10.0 -> "Apply SPF 50+ sunscreen, wear a hat and seek shade at midday."
    else -> "Apply SPF 50+ sunscreen and avoid direct sun during midday."
}

fun humidityLabel(h: Int): String = when {
    h < 30 -> "Dry"
    h < 45 -> "Slightly dry"
    h <= 65 -> "Comfortable"
    h <= 85 -> "Light fog"
    else -> "Dense fog"
}

fun humidityDescription(h: Int): String = when {
    h < 30 -> "The air is very dry. Drink water and consider a moisturizer."
    h < 45 -> "The air is a little dry."
    h <= 65 -> "The air feels comfortable."
    h <= 85 -> "The air feels damp and visibility may drop slightly."
    else -> "The air is saturated. Dense fog is likely."
}

data class LifeItem(val title: String, val value: String, val text: String)

fun lifeIndex(
    temp: Double,
    feels: Double,
    humidity: Int,
    uv: Double,
    aqi: Double?,
    wind: Double,
    precip: Double,
    kind: Condition,
    dayMax: Double,
    dayMin: Double
): List<LifeItem> {
    val wet = kind.isWet
    val exerciseBad = wet || (aqi != null && aqi > 100.0) || feels > 33.0 || feels < 0.0
    val clothing = when {
        feels < 5.0 -> "Heavy coat, scarf and gloves."
        feels < 12.0 -> "Warm jacket."
        feels < 18.0 -> "Light jacket or sweater."
        feels < 25.0 -> "T-shirt with an optional light layer."
        feels < 30.0 -> "Light, breathable clothing."
        else -> "Very light clothing. Stay hydrated."
    }
    var fluScore = 0
    if (temp < 10.0) fluScore++
    if (dayMax - dayMin >= 10.0) fluScore++
    if (humidity < 30 || humidity > 85) fluScore++
    val flu = when (fluScore) {
        0 -> "Low" to "Conditions are not favorable for catching a cold."
        1 -> "Moderate" to "Dress for temperature changes to avoid catching a cold."
        else -> "High" to "Big temperature swings or uncomfortable air raise the flu risk. Keep warm."
    }
    val travel = when {
        kind == Condition.Storm || kind == Condition.Blizzard || wind >= 60.0 || precip > 10.0 -> "Poor" to "Delay non-essential trips if you can."
        wet || kind == Condition.Fog || wind >= 40.0 -> "Fair" to "Allow extra time and drive carefully."
        else -> "Good" to "Great conditions for traveling."
    }
    val mosquito = if (temp >= 20.0 && humidity >= 60) {
        "Repellent recommended" to "Warm and humid air favors mosquitoes."
    } else {
        "Not needed" to "Conditions are not favorable for mosquitoes."
    }
    return listOf(
        LifeItem("Sun protection", uvLevel(uv), uvAdvice(uv)),
        LifeItem(
            "Outdoor exercise",
            if (exerciseBad) "Not ideal" else "Good",
            if (exerciseBad) "Consider an indoor workout today." else "A good time for a run or a walk."
        ),
        LifeItem("Clothing", "${temp.toInt()}° outside", clothing),
        LifeItem("Flu risk", flu.first, flu.second),
        LifeItem("Travel", travel.first, travel.second),
        LifeItem("Mosquito repellent", mosquito.first, mosquito.second)
    )
}
