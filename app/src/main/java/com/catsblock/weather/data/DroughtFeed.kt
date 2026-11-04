package com.catsblock.weather.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DroughtFeed {

    private const val BASE = "https://droughtmonitor.unl.edu/data/json/"
    private const val CURRENT = "current"

    private val cache = TimedCache<ByteArray>(6 * 3600_000L)
    private val format = DateTimeFormatter.BASIC_ISO_DATE
    private val pattern = Regex("\\d{8}")

    fun json(date: String? = null): ByteArray? {
        if (date == null) return load("usdm_current.json", CURRENT)
        if (!pattern.matches(date)) return null
        val day = runCatching { LocalDate.parse(date, format) }.getOrNull() ?: return null
        return load("usdm_$date.json", date)
            ?: load("usdm_" + format.format(day.minusWeeks(1)) + ".json", date)
    }

    private fun load(file: String, key: String): ByteArray? {
        cache.get(key)?.let { return it }
        val root = GeoHttp.json(BASE + file) ?: return cache.peek(key)
        val features = root.optJSONArray("features") ?: return cache.peek(key)
        val out = JSONArray()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val props = feature.optJSONObject("properties") ?: continue
            val dm = props.optInt("DM", props.optInt("dm", -1))
            if (dm < 0) continue
            val geometry = feature.optJSONObject("geometry")?.let { GeoMath.compactGeometry(it, 2) } ?: continue
            out.put(
                JSONObject().put("type", "Feature").put("geometry", geometry)
                    .put("properties", JSONObject().put("lvl", minOf(4, dm + 1)))
            )
        }
        if (out.length() == 0) return cache.peek(key)
        val body = JSONObject().put("type", "FeatureCollection").put("features", out)
        return cache.put(key, body.toString().toByteArray(Charsets.UTF_8))
    }
}
