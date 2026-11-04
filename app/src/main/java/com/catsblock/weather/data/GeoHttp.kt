package com.catsblock.weather.data

import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow

internal fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

internal object GeoHttp {

    const val AGENT = "CatsBlockWeather/2.0 (+https://github.com/catsblock/CatsBlock-Weather-v2)"

    private data class Entry(val value: String, val storedAt: Long, val ttl: Long)

    private const val CACHE_LIMIT = 64
    private const val CACHE_TTL = 90_000L
    private const val STALE_TTL_MULTIPLIER = 6L
    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<String, Entry>(CACHE_LIMIT, 0.75f, true) {}

    private val dispatcher = Dispatcher().apply {
        maxRequests = 12
        maxRequestsPerHost = 4
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun cacheKey(url: String, accept: String): String = "$accept|$url"

    private fun cached(key: String, allowStale: Boolean): String? = synchronized(cacheLock) {
        val entry = cache[key] ?: return@synchronized null
        val age = System.currentTimeMillis() - entry.storedAt
        val limit = if (allowStale) entry.ttl * STALE_TTL_MULTIPLIER else entry.ttl
        entry.value.takeIf { age in 0L..limit }
    }

    private fun store(key: String, value: String, ttl: Long) = synchronized(cacheLock) {
        cache[key] = Entry(value, System.currentTimeMillis(), ttl)
        while (cache.size > CACHE_LIMIT) {
            val iterator = cache.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    private fun retryDelay(response: okhttp3.Response?, attempt: Int): Long {
        val retryAfter = response?.header("Retry-After")
        val retryMillis = retryAfter?.toLongOrNull()?.times(1_000L)
            ?: retryAfter?.let {
                runCatching {
                    ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli() - System.currentTimeMillis()
                }.getOrNull()
            }
        return (retryMillis ?: (500L shl attempt)).coerceIn(250L, 4_000L)
    }

    fun text(url: String, accept: String = "application/json"): String? {
        val key = cacheKey(url, accept)
        cached(key, allowStale = false)?.let { return it }
        val ttl = when {
            url.contains("nominatim.openstreetmap.org") -> 300_000L
            url.contains("natural-earth") -> 86_400_000L
            else -> CACHE_TTL
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", AGENT)
            .header("Accept", accept)
            .build()

        repeat(3) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return cached(key, allowStale = true)
                        store(key, body, ttl)
                        return body
                    }
                    if (response.code != 429 && response.code !in 500..599) {
                        return cached(key, allowStale = true)
                    }
                    if (attempt < 2) {
                        val delay = retryDelay(response, attempt)
                        Thread.sleep(delay)
                    }
                }
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return cached(key, allowStale = true)
            } catch (_: Exception) {
                if (attempt < 2) {
                    try {
                        Thread.sleep((500L shl attempt).coerceAtMost(4_000L))
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return cached(key, allowStale = true)
                    }
                }
            }
        }
        return cached(key, allowStale = true)
    }

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
