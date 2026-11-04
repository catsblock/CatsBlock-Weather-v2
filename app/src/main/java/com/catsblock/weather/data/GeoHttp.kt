package com.catsblock.weather.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow

internal fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

internal object GeoHttp {

    const val AGENT = "CatsBlockWeather/2.0 (+https://github.com/catsblock/CatsBlock-Weather-v2)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    fun text(url: String, accept: String = "application/json"): String? = runCatching {
        client.newCall(
            Request.Builder().url(url).header("User-Agent", AGENT).header("Accept", accept).build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    fun json(url: String, accept: String = "application/json"): JSONObject? =
        text(url, accept)?.let { runCatching { JSONObject(it) }.getOrNull() }
}

internal class TimedCache<T : Any>(private val ttl: Long) {

    private val store = ConcurrentHashMap<String, Pair<Long, T>>()

    fun get(key: String): T? {
        val entry = store[key] ?: return null
        return if (System.currentTimeMillis() - entry.first < ttl) entry.second else null
    }

    fun peek(key: String): T? = store[key]?.second

    fun put(key: String, value: T): T {
        store[key] = System.currentTimeMillis() to value
        return value
    }
}

internal object GeoMath {

    private const val EARTH_KM = 6371.0088

    fun round(value: Double, decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return Math.round(value * factor) / factor
    }

    fun compact(node: Any?, decimals: Int): Any? {
        if (node !is JSONArray) return node
        if (node.length() > 0 && node.opt(0) is Number) {
            val out = JSONArray()
            for (i in 0 until minOf(2, node.length())) out.put(round(node.getDouble(i), decimals))
            return out
        }
        val out = JSONArray()
        var previous: String? = null
        for (i in 0 until node.length()) {
            val item = compact(node.opt(i), decimals)
            val key = if (item is JSONArray && item.length() > 0 && item.opt(0) is Number) item.toString() else null
            if (key != null && key == previous) continue
            previous = key
            out.put(item)
        }
        return out
    }

    fun compactGeometry(geometry: JSONObject, decimals: Int): JSONObject? {
        val type = geometry.optString("type")
        val coordinates = geometry.optJSONArray("coordinates") ?: return null
        return JSONObject().put("type", type).put("coordinates", compact(coordinates, decimals))
    }

    fun polygons(geometry: JSONObject?): List<JSONArray> {
        geometry ?: return emptyList()
        val coordinates = geometry.optJSONArray("coordinates") ?: return emptyList()
        return when (geometry.optString("type")) {
            "Polygon" -> listOf(coordinates)
            "MultiPolygon" -> (0 until coordinates.length()).mapNotNull { coordinates.optJSONArray(it) }
            else -> emptyList()
        }
    }

    fun multi(parts: List<JSONArray>): JSONObject =
        JSONObject().put("type", "MultiPolygon").put("coordinates", JSONArray(parts))

    private fun ringArea(ring: JSONArray, cosLat: Double): Double {
        val k = Math.PI / 180.0 * EARTH_KM
        var sum = 0.0
        for (i in 0 until ring.length() - 1) {
            val a = ring.getJSONArray(i)
            val b = ring.getJSONArray(i + 1)
            sum += (a.getDouble(0) * k * cosLat) * (b.getDouble(1) * k) - (b.getDouble(0) * k * cosLat) * (a.getDouble(1) * k)
        }
        return abs(sum) / 2.0
    }

    fun areaKm2(geometry: JSONObject?): Double {
        var total = 0.0
        for (polygon in polygons(geometry)) {
            val outer = polygon.optJSONArray(0) ?: continue
            val lat = outer.optJSONArray(0)?.optDouble(1) ?: 0.0
            val cosLat = cos(Math.toRadians(lat))
            var area = ringArea(outer, cosLat)
            for (h in 1 until polygon.length()) area -= ringArea(polygon.getJSONArray(h), cosLat)
            total += max(area, 0.0)
        }
        return total
    }

    fun fromText(text: String): JSONObject? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("{")) {
            return runCatching { JSONObject(trimmed) }.getOrNull()?.takeIf { it.has("type") }
        }
        val upper = trimmed.uppercase()
        if (upper.startsWith("POLYGON") || upper.startsWith("MULTIPOLYGON")) return fromWkt(trimmed)
        return fromPairs(trimmed)
    }

    private fun fromWkt(text: String): JSONObject? {
        val parts = ArrayList<JSONArray>()
        for (match in Regex("\\(([^()]+)\\)").findAll(text)) {
            val ring = JSONArray()
            for (pair in match.groupValues[1].split(',')) {
                val xy = pair.trim().split(Regex("\\s+"))
                val x = xy.getOrNull(0)?.toDoubleOrNull() ?: continue
                val y = xy.getOrNull(1)?.toDoubleOrNull() ?: continue
                ring.put(JSONArray().put(x).put(y))
            }
            if (ring.length() >= 4) parts.add(JSONArray().put(ring))
        }
        return if (parts.isEmpty()) null else multi(parts)
    }

    private fun fromPairs(text: String): JSONObject? {
        val ring = JSONArray()
        for (match in Regex("(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)").findAll(text)) {
            val lat = match.groupValues[1].toDouble()
            val lon = match.groupValues[2].toDouble()
            ring.put(JSONArray().put(lon).put(lat))
        }
        if (ring.length() < 4) return null
        return JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(ring))
    }
}
