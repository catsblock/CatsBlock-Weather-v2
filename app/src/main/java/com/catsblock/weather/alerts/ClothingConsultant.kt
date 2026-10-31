package com.catsblock.weather.alerts

import com.catsblock.weather.data.Day
import com.catsblock.weather.data.Hour
import com.catsblock.weather.data.WindUnit
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.alerts.Commute
import kotlin.math.roundToInt

data class ClothingItem(val section: String, val text: String)

data class ClothingAdvice(
    val minFeels: Double,
    val maxFeels: Double,
    val rainChance: Int,
    val items: List<ClothingItem>
)

object ClothingConsultant {

    private val SnowCodes = setOf(71, 73, 75, 77, 85, 86)

    private fun fmt(template: String, vararg args: Any): String =
        String.format(I18n.lang.locale, tr(template), *args)

    private fun temp(celsius: Double, fahrenheit: Boolean): String =
        "${(if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius).roundToInt()}°"

    private fun workWindow(hours: List<Hour>, workStart: Int, workEnd: Int): List<Hour> {
        val inside = hours.filter { it.time.hour * 60 + 59 >= workStart && it.time.hour * 60 <= workEnd }
        return if (inside.size >= 2) inside else hours.filter { it.time.hour in 6..22 }
    }

    fun advise(
        day: Day,
        hours: List<Hour>,
        thermal: Int,
        commute: Commute,
        workStart: Int,
        workEnd: Int,
        windUnit: WindUnit,
        fahrenheit: Boolean
    ): ClothingAdvice {
        val window = workWindow(hours, workStart, workEnd)
        val feelsMin = window.minOfOrNull { it.feels } ?: day.min
        val feelsMax = window.maxOfOrNull { it.feels } ?: day.max
        val thermalShift = when (thermal) {
            0 -> -3.0
            2 -> 3.0
            else -> 0.0
        }
        val commuteShift = when (commute) {
            Commute.Bike -> -2.0
            Commute.Car -> 1.5
            else -> 0.0
        }
        val effective = (feelsMin + feelsMax) / 2.0 + thermalShift + commuteShift
        val coldest = feelsMin + thermalShift + commuteShift

        val totalRain = if (window.isNotEmpty()) window.sumOf { it.precip } else day.precipSum
        val rainPeak = window.filter { it.precip >= 0.2 }.maxByOrNull { it.precip }
        val heavyRain = (rainPeak?.precip ?: 0.0) >= 2.5 || totalRain >= 10.0
        val rainy = day.rainChance >= 40 || totalRain >= 1.0
        val snow = window.any { it.code in SnowCodes }
        val windMax = window.maxOfOrNull { it.windSpeed } ?: 0.0
        val uv = window.maxOfOrNull { it.uv } ?: day.uvMax
        val humid = window.isNotEmpty() && window.map { it.humidity }.average() >= 70.0
        val swing = feelsMax - feelsMin

        val items = mutableListOf<ClothingItem>()

        items += ClothingItem(
            tr("Top"),
            tr(
                when {
                    effective <= -5.0 -> "Thermal base layer, wool sweater and an insulated parka."
                    effective <= 2.0 -> "Thermal base layer, sweater and a heavy coat."
                    effective <= 10.0 -> "Long sleeves with a warm jacket."
                    effective <= 16.0 -> "Long-sleeve shirt with a light jacket or sweater."
                    effective <= 21.0 -> "Light long-sleeve shirt; carry a thin extra layer."
                    effective <= 27.0 -> "T-shirt or short-sleeve shirt in breathable fabric."
                    else -> "Loose, light-colored, breathable top."
                }
            )
        )

        items += ClothingItem(
            tr("Bottom"),
            tr(
                when {
                    effective <= -5.0 -> "Lined trousers over thermal leggings."
                    effective <= 2.0 -> "Insulated or lined trousers."
                    effective <= 16.0 -> "Long trousers or jeans."
                    effective <= 24.0 -> "Light trousers."
                    else -> "Shorts or light, loose trousers."
                }
            )
        )

        items += ClothingItem(
            tr("Footwear"),
            tr(
                when {
                    snow || effective <= 0.0 -> "Insulated, non-slip boots and wool socks."
                    rainy -> "Waterproof shoes or boots; pack spare socks if you walk."
                    effective >= 26.0 -> "Breathable, open shoes."
                    else -> "Comfortable closed shoes."
                }
            )
        )

        when {
            effective <= 2.0 -> items += ClothingItem(tr("Accessories"), tr("Warm hat, scarf and gloves."))
            coldest <= 5.0 -> items += ClothingItem(tr("Accessories"), tr("Light scarf and gloves for the coldest hours."))
        }

        if (heavyRain) {
            items += ClothingItem(
                tr("Rain"),
                tr("Heavy rain expected: hooded waterproof jacket, plus waterproof trousers if you walk or ride.")
            )
        } else if (rainy) {
            items += ClothingItem(
                tr("Rain"),
                fmt("Rain is likely (%1\$d%% chance): bring a waterproof jacket or an umbrella.", day.rainChance)
            )
        }
        if (rainy && rainPeak != null) {
            items += ClothingItem(tr("Rain"), fmt("Rain is most likely around %1\$s.", "%02d:00".format(rainPeak.time.hour)))
        }

        val uvRounded = uv.roundToInt()
        when {
            uv >= 8.0 -> items += ClothingItem(tr("Sun protection"), fmt("UV up to %1\$d: SPF 50, sunglasses, a hat and covered arms around midday.", uvRounded))
            uv >= 6.0 -> items += ClothingItem(tr("Sun protection"), fmt("UV up to %1\$d: SPF 30+, sunglasses and a hat.", uvRounded))
            uv >= 3.0 -> items += ClothingItem(tr("Sun protection"), fmt("UV up to %1\$d: sunglasses and sunscreen on exposed skin.", uvRounded))
        }

        if (windMax >= 30.0) {
            val label = "${(windMax * windUnit.factor).roundToInt()} ${windUnit.label}"
            items += ClothingItem(tr("Wind"), fmt("Wind up to %1\$s: a windproof outer layer helps.", label))
            if (windMax >= 35.0 && rainy) {
                items += ClothingItem(tr("Wind"), tr("Strong wind: a hooded jacket works better than an umbrella."))
            }
        }

        if (swing >= 8.0) {
            items += ClothingItem(
                tr("Day plan"),
                fmt(
                    "It feels like %1\$s at the coolest and %2\$s at the warmest: dress in layers you can take off.",
                    temp(feelsMin, fahrenheit),
                    temp(feelsMax, fahrenheit)
                )
            )
        }
        if (humid && effective >= 24.0) {
            items += ClothingItem(tr("Day plan"), tr("Humid air: choose light, breathable fabrics."))
        }
        val sunsetMinutes = day.sunset.hour * 60 + day.sunset.minute
        if (workEnd >= sunsetMinutes) {
            val evening = hours.minByOrNull { kotlin.math.abs(it.time.hour * 60 - workEnd) }
            if (evening != null) {
                items += ClothingItem(
                    tr("Day plan"),
                    fmt("You head back after sunset, when it will feel like %1\$s.", temp(evening.feels, fahrenheit))
                )
            }
        }

        when (commute) {
            Commute.Walk -> items += ClothingItem(tr("Commute"), tr("Walking: comfortable shoes, and leave extra time if it rains."))
            Commute.Bike -> {
                if (effective <= 10.0) items += ClothingItem(tr("Commute"), tr("Cycling feels colder: add windproof gloves."))
                items += ClothingItem(tr("Commute"), tr("Cycling: helmet, and reflective details if you ride after dusk."))
                if (rainy) items += ClothingItem(tr("Commute"), tr("Wet roads: ride slowly and avoid slippery surfaces."))
            }
            Commute.Car -> items += ClothingItem(tr("Commute"), tr("By car: the cabin stays warm, so keep a coat within reach."))
            Commute.Transit -> items += ClothingItem(tr("Commute"), tr("Public transport is often warm inside: wear layers that are easy to remove."))
        }

        return ClothingAdvice(feelsMin, feelsMax, day.rainChance, items)
    }
}
