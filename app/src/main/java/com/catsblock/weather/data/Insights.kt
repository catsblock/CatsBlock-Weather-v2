package com.catsblock.weather.data

import com.catsblock.weather.i18n.AppLanguage
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr

private val compassEn = arrayOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
)

private val compassPt = arrayOf(
    "N", "NNE", "NE", "ENE", "L", "ESE", "SE", "SSE",
    "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO"
)

private val compassEs = arrayOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO"
)

private val compassRu = arrayOf(
    "С", "ССВ", "СВ", "ВСВ", "В", "ВЮВ", "ЮВ", "ЮЮВ",
    "Ю", "ЮЮЗ", "ЮЗ", "ЗЮЗ", "З", "ЗСЗ", "СЗ", "ССЗ"
)

private val compassDe = arrayOf(
    "N", "NNO", "NO", "ONO", "O", "OSO", "SO", "SSO",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
)

private val compassKo = arrayOf(
    "북", "북북동", "북동", "동북동", "동", "동남동", "남동", "남남동",
    "남", "남남서", "남서", "서남서", "서", "서북서", "북서", "북북서"
)

private val compassId = arrayOf(
    "U", "UUTL", "TL", "TTL", "T", "TTG", "TG", "STG",
    "S", "SBD", "BD", "BBD", "B", "BBL", "BL", "UBL"
)

private fun compass(): Array<String> = when (I18n.lang) {
    AppLanguage.English -> compassEn
    AppLanguage.PtBR, AppLanguage.PtPT -> compassPt
    AppLanguage.Spanish, AppLanguage.French, AppLanguage.Italian -> compassEs
    AppLanguage.Russian -> compassRu
    AppLanguage.German -> compassDe
    AppLanguage.Korean -> compassKo
    AppLanguage.Indonesian -> compassId
}

fun compassLabel(deg: Double): String {
    val normalized = ((deg % 360.0) + 360.0) % 360.0
    return compass()[((normalized / 22.5) + 0.5).toInt() % 16]
}

fun precipLabel(mm: Double): String = tr(
    when {
        mm <= 0.0 -> "Dry"
        mm < 2.5 -> "Drizzle"
        mm <= 10.0 -> "Moderate"
        mm <= 50.0 -> "Heavy"
        else -> "Extreme"
    }
)

fun precipToday(mm: Double): String = tr(
    when {
        mm <= 0.0 -> "No precipitation expected today."
        mm < 2.5 -> "Light rain accumulated today."
        mm <= 10.0 -> "Moderate rain accumulated today."
        mm <= 50.0 -> "Heavy rain accumulated today."
        else -> "Extreme rain accumulated today."
    }
)

fun windDescription(kmh: Double): String = tr(
    when {
        kmh <= 15.0 -> "Tree leaves and small twigs sway gently."
        kmh <= 25.0 -> "Pleasant breeze that reduces the heat sensation. Leaves and small branches sway more."
        kmh <= 40.0 -> "Strong wind. Leaves sway heavily and small branches may snap depending on the direction."
        kmh < 60.0 -> "Danger. Raindrops hit much harder and travel farther. Fast droplets can hurt on impact."
        else -> "Extreme. Vehicles can flip and trees may break and fall. Staying indoors without protection is unsafe."
    }
)

fun aqiStatus(v: Double): String = tr(
    when {
        v <= 50.0 -> "Excellent"
        v <= 100.0 -> "Good"
        v <= 150.0 -> "Moderate"
        v <= 200.0 -> "Poor"
        else -> "Extreme"
    }
)

fun aqiAdvice(v: Double): String = tr(
    when {
        v <= 50.0 -> "Air quality is ideal. Enjoy outdoor activities."
        v <= 100.0 -> "Air quality is acceptable. Unusually sensitive people should limit long outdoor exertion."
        v <= 150.0 -> "Sensitive groups should reduce prolonged outdoor activity."
        v <= 200.0 -> "Everyone should limit outdoor exertion. Consider wearing a mask outside."
        else -> "Avoid outdoor activity and keep windows closed."
    }
)

fun uvLevel(v: Double): String = tr(
    when {
        v < 3.0 -> "Weak"
        v < 5.0 -> "Light"
        v < 7.0 -> "Moderate"
        v < 10.0 -> "Strong"
        else -> "Extreme"
    }
)

fun uvAdvice(v: Double): String = tr(
    when {
        v < 3.0 -> "No sunscreen needed for most people."
        v < 5.0 -> "Use SPF 30 sunscreen if you stay outside for long."
        v < 7.0 -> "Apply SPF 30 sunscreen and wear sunglasses."
        v < 10.0 -> "Apply SPF 50+ sunscreen, wear a hat and seek shade at midday."
        else -> "Apply SPF 50+ sunscreen and avoid direct sun during midday."
    }
)

fun humidityLabel(h: Int): String = tr(
    when {
        h < 30 -> "Dry"
        h < 45 -> "Slightly dry"
        h <= 65 -> "Comfortable"
        h <= 85 -> "Light fog"
        else -> "Dense fog"
    }
)

fun humidityDescription(h: Int): String = tr(
    when {
        h < 30 -> "The air is very dry. Drink water and consider a moisturizer."
        h < 45 -> "The air is a little dry."
        h <= 65 -> "The air feels comfortable."
        h <= 85 -> "The air feels damp and visibility may drop slightly."
        else -> "The air is saturated. Dense fog is likely."
    }
)

enum class LifeKind { Sun, Exercise, Clothing, Flu, Travel, Mosquito }

data class LifeItem(val kind: LifeKind, val title: String, val value: String, val text: String)

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
        kind == Condition.Storm || kind == Condition.Blizzard || wind >= 60.0 || precip > 10.0 ->
            "Poor" to "Delay non-essential trips if you can."
        wet || kind == Condition.Fog || wind >= 40.0 ->
            "Fair" to "Allow extra time and drive carefully."
        else -> "Good" to "Great conditions for traveling."
    }
    val mosquito = if (temp >= 20.0 && humidity >= 60) {
        "Repellent recommended" to "Warm and humid air favors mosquitoes."
    } else {
        "Not needed" to "Conditions are not favorable for mosquitoes."
    }
    return listOf(
        LifeItem(LifeKind.Sun, tr("Sun protection"), uvLevel(uv), uvAdvice(uv)),
        LifeItem(
            LifeKind.Exercise,
            tr("Outdoor exercise"),
            tr(if (exerciseBad) "Not ideal" else "Good"),
            tr(if (exerciseBad) "Consider an indoor workout today." else "A good time for a run or a walk.")
        ),
        LifeItem(LifeKind.Clothing, tr("Clothing"), "${temp.toInt()}° ${tr("outside")}", tr(clothing)),
        LifeItem(LifeKind.Flu, tr("Flu risk"), tr(flu.first), tr(flu.second)),
        LifeItem(LifeKind.Travel, tr("Travel"), tr(travel.first), tr(travel.second)),
        LifeItem(LifeKind.Mosquito, tr("Mosquito repellent"), tr(mosquito.first), tr(mosquito.second))
    )
}
