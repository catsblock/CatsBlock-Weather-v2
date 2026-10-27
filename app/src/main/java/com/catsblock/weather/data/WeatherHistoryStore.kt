package com.catsblock.weather.data

import android.content.Context
import com.catsblock.weather.atmosphere.AtmosphericParameters
import com.catsblock.weather.atmosphere.WeatherHistoryRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object WeatherHistoryStore {
    private const val PREFS = "weather_atmospheric_history"
    private const val MAX_RECORDS = 1008
    private const val MIN_SAMPLE_MINUTES = 20L
    private val lock = Any()

    fun record(context: Context, cityId: String, current: Current) {
        if (cityId.isBlank()) return
        synchronized(lock) {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = keyFor(cityId)
            val records = decode(preferences.getString(key, null)).toMutableList()
            val timestamp = current.now
            val last = records.lastOrNull()
            if (last != null) {
                val elapsed = ChronoUnit.MINUTES.between(last.timestamp, timestamp)
                if (elapsed in 0 until MIN_SAMPLE_MINUTES) return
                if (elapsed < 0) return
            }
            val estimatedCloudCover = current.cloudCover < 0.0
            val precipitation = when (current.kind) {
                Condition.Drizzle -> 0.25
                Condition.Rain -> 1.2
                Condition.Storm -> 7.0
                Condition.Sleet -> 0.5
                Condition.Hail -> 2.0
                else -> 0.0
            }
            records += WeatherHistoryRecord(
                timestamp = timestamp,
                cloudCoverPercent = if (estimatedCloudCover) AtmosphericParameters.estimateCloudCover(current.kind).toDouble() else current.cloudCover,
                pressureHpa = current.pressure,
                temperatureCelsius = current.temp,
                windSpeedKmh = current.windSpeed,
                precipitationMmPerHour = precipitation,
                uvIndex = current.uv,
                humidityPercent = current.humidity.toDouble(),
                cloudCoverEstimated = estimatedCloudCover
            )
            val cutoff = timestamp.minusDays(14)
            val retained = records.filter { !it.timestamp.isBefore(cutoff) }.takeLast(MAX_RECORDS)
            preferences.edit().putString(key, encode(retained)).apply()
        }
    }

    fun read(context: Context, cityId: String): List<WeatherHistoryRecord> {
        if (cityId.isBlank()) return emptyList()
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return decode(preferences.getString(keyFor(cityId), null))
    }

    private fun keyFor(cityId: String): String = "city_${Integer.toHexString(cityId.hashCode())}"

    private fun encode(records: List<WeatherHistoryRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("timestamp", record.timestamp.toString())
                    .put("cloudCoverPercent", record.cloudCoverPercent)
                    .put("pressureHpa", record.pressureHpa)
                    .put("temperatureCelsius", record.temperatureCelsius)
                    .put("windSpeedKmh", record.windSpeedKmh)
                    .put("precipitationMmPerHour", record.precipitationMmPerHour)
                    .put("uvIndex", record.uvIndex)
                    .put("humidityPercent", record.humidityPercent)
                    .put("cloudCoverEstimated", record.cloudCoverEstimated)
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<WeatherHistoryRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        WeatherHistoryRecord(
                            timestamp = LocalDateTime.parse(item.getString("timestamp")),
                            cloudCoverPercent = item.optDouble("cloudCoverPercent", 0.0),
                            pressureHpa = item.optDouble("pressureHpa", 1013.25),
                            temperatureCelsius = item.optDouble("temperatureCelsius", 20.0),
                            windSpeedKmh = item.optDouble("windSpeedKmh", 0.0),
                            precipitationMmPerHour = item.optDouble("precipitationMmPerHour", 0.0),
                            uvIndex = item.optDouble("uvIndex", 0.0),
                            humidityPercent = item.optDouble("humidityPercent", 0.0),
                            cloudCoverEstimated = item.optBoolean("cloudCoverEstimated", false)
                        )
                    )
                }
            }.sortedBy { it.timestamp }
        }.getOrDefault(emptyList())
    }
}
