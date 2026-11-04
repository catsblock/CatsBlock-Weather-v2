package com.catsblock.weather.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.util.Locale
import kotlin.math.floor
import kotlin.math.pow

object BlackoutFeed {

    private const val COLS = 7
    private const val ROWS = 5
    private const val MAX_AREAS = 6
    private const val THRESHOLD = 0.22

    private val cache = TimedCache<ByteArray>(10 * 60_000L)

    private class Stress(val score: Double, val driver: String, val gust: Double)

    private fun wrap(lon: Double): Double = ((lon + 540.0) % 360.0) - 180.0

    private fun stress(c: JSONObject): Stress {
        val gust = c.optDouble("wind_gusts_10m", 0.0)
        val code = c.optInt("weather_code", 0)
        val rain = c.optDouble("precipitation", 0.0)
        val snow = c.optDouble("snowfall", 0.0)
        val parts = linkedMapOf(
            "wind" to ((gust - 45.0) / 70.0).coerceIn(0.0, 1.0) * 0.8,
            "storm" to if (code in 95..99) (if (code >= 96) 0.5 else 0.35) else 0.0,
            "ice" to if (code == 56 || code == 57 || code == 66 || code == 67) 0.6 else 0.0,
            "snow" to if (snow >= 1.0) (snow / 6.0).coerceIn(0.0, 0.5) else 0.0,
            "rain" to if (rain >= 4.0) (rain / 30.0).coerceIn(0.0, 0.3) else 0.0
        )
        val total = parts.values.sum().coerceIn(0.0, 1.0)
        val driver = parts.maxByOrNull { it.value }?.key ?: "wind"
        return Stress(total, driver, gust)
    }

    fun json(south: Double, north: Double, west: Double, east: Double): ByteArray? {
        val key = listOf(south, north, west, east).joinToString(":") { floor(it).toInt().toString() }
        cache.get(key)?.let { return it }
        val lats = ArrayList<String>()
        val lons = ArrayList<String>()
        val centers = ArrayList<Pair<Double, Double>>()
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val lat = south + (north - south) * (r + 0.5) / ROWS
            val lon = wrap(west + (east - west) * (c + 0.5) / COLS)
            centers.add(lat to lon)
            lats.add(String.format(Locale.US, "%.3f", lat))
            lons.add(String.format(Locale.US, "%.3f", lon))
        }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${lats.joinToString(",")}&longitude=${lons.joinToString(",")}" +
            "&current=temperature_2m,precipitation,snowfall,weather_code,wind_gusts_10m&wind_speed_unit=kmh&timezone=UTC"
        val raw = GeoHttp.text(url) ?: return cache.peek(key)
        val node = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
        val items = if (node is JSONArray) (0 until node.length()).map { node.optJSONObject(it) } else listOf(node as? JSONObject)
        val candidates = items.withIndex()
            .mapNotNull { (i, item) ->
                val current = item?.optJSONObject("current") ?: return@mapNotNull null
                val s = stress(current)
                if (s.score >= THRESHOLD && i < centers.size) Triple(centers[i], s, i) else null
            }
            .sortedByDescending { it.second.score }
        val features = JSONArray()
        val seen = HashSet<String>()
        val now = Instant.now().toString()
        for ((center, s, _) in candidates) {
            if (features.length() >= MAX_AREAS) break
            val meta = MunicipalityMeta.resolve(center.first, center.second, true) ?: continue
            val geometry = meta.optJSONObject("geometry") ?: continue
            if (!seen.add(meta.str("name") + "|" + meta.str("region"))) continue
            val area = meta.optDouble("areaKm2", 0.0)
            val known = meta.optLong("population", 0L)
            val population = if (known > 0) known else (area * 110.0).toLong().coerceAtLeast(500L)
            val fraction = (0.04 + 0.9 * s.score.pow(1.4)).coerceIn(0.03, 0.95)
            val level = when {
                s.score >= 0.7 -> "severe"
                s.score >= 0.4 -> "moderate"
                else -> "minor"
            }
            val lights = population / 12
            val shops = population / 16
            val props = JSONObject()
                .put("level", level)
                .put("name", meta.str("name"))
                .put("region", meta.str("region"))
                .put("country", meta.str("country"))
                .put("timestamp", now)
                .put("population", population)
                .put("populationKnown", known > 0)
                .put("people", (population * fraction).toLong())
                .put("lightsTotal", lights)
                .put("lightsOffline", (lights * fraction).toLong())
                .put("businessesTotal", shops)
                .put("businesses", (shops * fraction).toLong())
                .put("driver", s.driver)
                .put("gust", s.gust)
                .put("modeled", true)
            features.put(JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", props))
        }
        val body = JSONObject().put("type", "FeatureCollection").put("features", features)
        return cache.put(key, body.toString().toByteArray(Charsets.UTF_8))
    }
}
