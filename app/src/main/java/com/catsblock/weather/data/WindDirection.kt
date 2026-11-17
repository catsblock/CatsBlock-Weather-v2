package com.catsblock.weather.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

fun normalizeWindDirection(degrees: Double?): Double? {
    if (degrees == null || !degrees.isFinite()) return null
    return ((degrees % 360.0) + 360.0) % 360.0
}

fun windDirectionLabel(degrees: Double?): String {
    val normalized = normalizeWindDirection(degrees) ?: return "--"
    return compassLabel(normalized)
}

fun circularMeanWindDirection(values: List<Double?>): Double? {
    var sumSin = 0.0
    var sumCos = 0.0
    var count = 0
    for (value in values) {
        val normalized = normalizeWindDirection(value) ?: continue
        val radians = Math.toRadians(normalized)
        sumSin += sin(radians)
        sumCos += cos(radians)
        count++
    }
    if (count == 0) return null
    if (hypot(sumSin, sumCos) / count < 1e-9) return null
    return normalizeWindDirection(Math.toDegrees(atan2(sumSin, sumCos)))
}
