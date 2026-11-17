package com.catsblock.weather.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

fun normalizeWindDirection(degrees: Double?): Double? {
    if (degrees == null || !degrees.isFinite()) return null
    return ((degrees % 360.0) + 360.0) % 360.0
}

fun circularMeanWindDirection(directions: Iterable<Double?>): Double? {
    val normalized = directions.mapNotNull(::normalizeWindDirection)
    if (normalized.isEmpty()) return null
    val x = normalized.sumOf { cos(Math.toRadians(it)) }
    val y = normalized.sumOf { sin(Math.toRadians(it)) }
    if (hypot(x, y) < 1e-9) return null
    return normalizeWindDirection(Math.toDegrees(atan2(y, x)))
}

fun windDirectionLabel(degrees: Double?): String =
    normalizeWindDirection(degrees)?.let(::compassLabel) ?: "—"

fun JSONObject.optWindDirection(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return normalizeWindDirection(optDouble(key, Double.NaN))
}

fun JSONArray.optWindDirection(index: Int): Double? {
    if (index !in 0 until length() || isNull(index)) return null
    return normalizeWindDirection(optDouble(index, Double.NaN))
}
