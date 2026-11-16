package com.catsblock.weather.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

internal class NativeWeatherGridOverlay(
    private val style: Style,
    private val scope: CoroutineScope,
    private val client: OkHttpClient = defaultClient
) : Closeable {
    private var requestJob: Job? = null
    private var generation = 0L
    private var closed = false
    private var sourceInstalled = false

    fun show(layerId: String?, epochMillis: Long = System.currentTimeMillis()) {
        check(!closed)
        require(layerId == null || layerId in LAYERS) { "Unsupported weather grid layer: $layerId" }
        val token = ++generation
        requestJob?.cancel()
        if (layerId == null) {
            removeSourceAndLayer()
            return
        }
        val hour = Math.floorDiv(epochMillis, HOUR_MS)
        val cacheKey = if (layerId == "sea" || layerId == "drought") "$layerId" else "$layerId:$hour"
        val now = System.currentTimeMillis()
        val entry = cache[cacheKey]
        if (entry != null && now - entry.createdAt < CACHE_TTL_MS) {
            install(entry.geoJson)
            return
        }
        removeSourceAndLayer()
        requestJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { fetchGrid(layerId, hour) }
                if (closed || token != generation) return@launch
                cache[cacheKey] = CacheEntry(System.currentTimeMillis(), result)
                install(result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (!closed && token == generation) removeSourceAndLayer()
            }
        }
    }

    private suspend fun fetchGrid(layer: String, hour: Long): String {
        val points = ArrayList<Point>(ROWS * COLS)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            points += Point(-75.0 + 150.0 * r / (ROWS - 1), -180.0 + 360.0 * c / (COLS - 1))
        }
        val values = arrayOfNulls<Double>(points.size)
        val (base, tail, chunkSize) = requestSpec(layer, hour)
        for (offset in points.indices step chunkSize) {
            coroutineContextEnsureActive()
            val end = min(points.size, offset + chunkSize)
            val chunk = points.subList(offset, end)
            val la = chunk.joinToString(",") { "%.3f".format(java.util.Locale.US, it.lat) }
            val lo = chunk.joinToString(",") { "%.3f".format(java.util.Locale.US, it.lon) }
            val url = base.toHttpUrl().newBuilder()
                .addQueryParameter("latitude", la)
                .addQueryParameter("longitude", lo)
                .apply { tail.forEach { (key, value) -> addQueryParameter(key, value) } }
                .build()
            val responseData = requestWithRetry(url.toString())
            for (i in chunk.indices) values[offset + i] = extractValue(responseData, i, layer, hour)
        }
        if (values.none { it != null && it.isFinite() }) return EMPTY_COLLECTION
        return makeFeatureCollection(points, values, layer)
    }

    private suspend fun requestWithRetry(url: String): JSONArray {
        var failure: IOException? = null
        repeat(3) { attempt ->
            coroutineContextEnsureActive()
            try {
                return withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Open-Meteo HTTP ${response.code}")
                        val body = response.body?.string() ?: throw IOException("Empty Open-Meteo response")
                        val trimmed = body.trimStart()
                        if (trimmed.startsWith("[")) JSONArray(body) else JSONArray().put(JSONObject(body))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                failure = e
                if (attempt < 2) delay(450L * (1L shl attempt))
            }
        }
        throw failure ?: IOException("Open-Meteo request failed")
    }

    private fun extractValue(data: JSONArray, index: Int, layer: String, hour: Long): Double? {
        if (index >= data.length() || data.isNull(index)) return null
        val item = data.optJSONObject(index) ?: return null
        return try {
            when (layer) {
                "drought" -> {
                    val daily = item.optJSONObject("daily") ?: return null
                    val rain = daily.optJSONArray("precipitation_sum") ?: return null
                    val et = daily.optJSONArray("et0_fao_evapotranspiration") ?: return null
                    val n = min(rain.length(), et.length()) - 1
                    if (n <= 0) null else {
                        var p = 0.0; var e = 0.0
                        for (i in 0 until n) { if (!rain.isNull(i)) p += rain.getDouble(i); if (!et.isNull(i)) e += et.getDouble(i) }
                        if (e < 10.0) 1.5 else p / e
                    }
                }
                else -> {
                    val hourly = item.optJSONObject("hourly") ?: return null
                    val key = VARIABLE.getValue(layer)
                    val arr = hourly.optJSONArray(key) ?: return null
                    val index = if (layer == "sea") {
                        val times = hourly.optJSONArray("time") ?: return null
                        val target = java.time.Instant.ofEpochMilli(hour * HOUR_MS).toString().take(13) + ":00"
                        (0 until times.length()).firstOrNull { times.optString(it) == target } ?: return null
                    } else 0
                    if (index >= arr.length() || arr.isNull(index)) null else arr.optDouble(index).takeIf(Double::isFinite)
                }
            }
        } catch (_: Exception) { null }
    }

    private fun makeFeatureCollection(points: List<Point>, values: Array<Double?>, layer: String): String {
        val features = JSONArray()
        for (r in 0 until ROWS - 1) for (c in 0 until COLS - 1) {
            val i = r * COLS + c
            val four = listOf(values[i], values[i + 1], values[i + COLS], values[i + COLS + 1])
            val available = four.filterNotNull().filter(Double::isFinite)
            if (available.isEmpty()) continue
            val value = available.average()
            val color = colorFor(layer, value)
            if (color == null) continue
            val west = points[i].lon; val south = points[i].lat
            val east = points[i + 1].lon; val north = points[i + COLS].lat
            val ring = JSONArray().put(coord(west, south)).put(coord(east, south)).put(coord(east, north)).put(coord(west, north)).put(coord(west, south))
            val geometry = JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(ring))
            features.put(JSONObject().put("type", "Feature").put("properties", JSONObject().put("color", color)).put("geometry", geometry))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

    private fun install(geoJson: String) {
        removeSourceAndLayer()
        style.addSource(GeoJsonSource(SOURCE_ID, geoJson))
        style.addLayer(FillLayer(LAYER_ID, SOURCE_ID).withProperties(fillColor(Expression.get("color")), fillOpacity(0.70f)))
        sourceInstalled = true
    }

    private fun removeSourceAndLayer() {
        if (style.getLayer(LAYER_ID) != null) style.removeLayer(LAYER_ID)
        if (style.getSource(SOURCE_ID) != null) style.removeSource(SOURCE_ID)
        sourceInstalled = false
    }

    override fun close() {
        if (closed) return
        closed = true
        generation++
        requestJob?.cancel()
        requestJob = null
        removeSourceAndLayer()
    }

    private data class Point(val lat: Double, val lon: Double)
    private data class CacheEntry(val createdAt: Long, val geoJson: String)

    private fun coord(lon: Double, lat: Double) = JSONArray().put(lon).put(lat)

    private fun colorFor(layer: String, value: Double): String? {
        if (!value.isFinite()) return null
        if (layer == "clouds") return "#E6F2FC${(min(0.85, max(0.0, value / 100.0)) * 255).toInt().toString(16).padStart(2, '0')}"
        if (layer == "drought") {
            val colors = listOf("#FFEC8C", "#FFC83C", "#F58C1E", "#D84315", "#961414")
            val idx = when { value >= 0.85 -> return null; value >= 0.65 -> 0; value >= 0.45 -> 1; value >= 0.30 -> 2; value >= 0.18 -> 3; else -> 4 }
            return colors[idx]
        }
        val (stops, alpha) = when (layer) {
            "temp" -> listOf(-30.0 to "#7A30B0", -15.0 to "#3056E2", 0.0 to "#22C4E2", 10.0 to "#48C668", 20.0 to "#FADE34", 30.0 to "#FA8C1C", 40.0 to "#E23428", 50.0 to "#96183C") to 0xB8
            "dew" -> listOf(-20.0 to "#6E3CAA", -5.0 to "#3C64D2", 5.0 to "#3CBED2", 12.0 to "#5AC86E", 18.0 to "#F0DC46", 22.0 to "#FA9628", 26.0 to "#E63C32", 30.0 to "#96143C") to 0xB8
            "wind" -> listOf(0.0 to "#2A6FDB", 15.0 to "#26C6DA", 30.0 to "#66BB6A", 50.0 to "#FDD835", 75.0 to "#FB8C00", 100.0 to "#D32F2F", 140.0 to "#8E24AA") to 0xB8
            "pressure" -> listOf(980.0 to "#7B3294", 995.0 to "#3B6FD4", 1005.0 to "#4FC3F7", 1013.0 to "#E0E0E0", 1022.0 to "#FFB74D", 1030.0 to "#F4511E", 1045.0 to "#B71C1C") to 0xB8
            "humidity" -> listOf(0.0 to "#EBF5FF", 20.0 to "#80CBC4", 40.0 to "#26A69A", 60.0 to "#1E88E5", 80.0 to "#1565C0", 100.0 to "#0D47A1") to 0xB8
            "uv" -> { if (value < 0.3) return null; listOf(0.0 to "#43A047", 3.0 to "#FADC34", 6.0 to "#FB8C00", 8.0 to "#E53935", 11.0 to "#8E24AA", 14.0 to "#5A1482") to 0xB8 }
            "aqi" -> listOf(0.0 to "#00E400", 50.0 to "#C8DC00", 100.0 to "#FF7E00", 150.0 to "#FF0000", 200.0 to "#99004C", 300.0 to "#7E0023") to 0xB8
            "sea" -> listOf(-2.0 to "#2166AC", -1.0 to "#67A9CF", 0.0 to "#F7F7F7", 1.0 to "#FDAE61", 2.0 to "#D73027") to 0xD2
            else -> return null
        }
        val rgb = interpolate(stops, value)
        return "#%02X%02X%02X%02X".format(java.util.Locale.US, rgb[0], rgb[1], rgb[2], alpha)
    }

    private fun interpolate(stops: List<Pair<Double, String>>, v: Double): IntArray {
        val value = v.coerceIn(stops.first().first, stops.last().first)
        val hi = stops.indexOfFirst { it.first >= value }.let { if (it < 0) stops.lastIndex else it }
        val lo = max(0, hi - 1)
        val a = stops[lo]; val b = stops[hi]
        val t = if (a.first == b.first) 0.0 else (value - a.first) / (b.first - a.first)
        fun channel(color: String, shift: Int) = color.substring(1 + shift, 3 + shift).toInt(16)
        return intArrayOf(0, 2, 4).map { shift -> (channel(a.second, shift) + (channel(b.second, shift) - channel(a.second, shift)) * t).toInt().coerceIn(0, 255) }.toIntArray()
    }

    private suspend fun coroutineContextEnsureActive() { kotlinx.coroutines.currentCoroutineContext().ensureActive() }

    private fun requestSpec(layer: String, hour: Long): Triple<String, Map<String, String>, Int> {
        val time = java.time.Instant.ofEpochMilli(hour * HOUR_MS).toString().take(13) + ":00"
        val hourly = mapOf("hourly" to VARIABLE.getValue(layer), "start_hour" to time, "end_hour" to time, "timezone" to "UTC")
        return when (layer) {
            "aqi" -> Triple("https://air-quality-api.open-meteo.com/v1/air-quality", hourly + ("hourly" to "us_aqi"), 120)
            "sea" -> Triple("https://marine-api.open-meteo.com/v1/marine", mapOf("hourly" to "sea_level_height_msl", "forecast_days" to "1", "timezone" to "UTC"), 12)
            "drought" -> Triple("https://api.open-meteo.com/v1/forecast", mapOf("daily" to "precipitation_sum,et0_fao_evapotranspiration", "past_days" to "30", "forecast_days" to "1", "timezone" to "UTC"), 120)
            else -> Triple("https://api.open-meteo.com/v1/forecast", hourly + ("wind_speed_unit" to "kmh"), 120)
        }
    }

    companion object {
        private const val SOURCE_ID = "native-weather-grid-source"
        private const val LAYER_ID = "native-weather-grid-fill"
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
        private const val HOUR_MS = 60 * 60 * 1000L
        private const val ROWS = 16
        private const val COLS = 37
        private const val USER_AGENT = "CatsBlockWeather/2.0 (Android; https://github.com/catsblock/CatsBlock-Weather-v2)"
        private val EMPTY_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}"
        private val LAYERS = setOf("temp", "clouds", "wind", "pressure", "humidity", "dew", "uv", "aqi", "sea")
        private val VARIABLE = mapOf("temp" to "temperature_2m", "clouds" to "cloud_cover", "wind" to "wind_speed_10m", "pressure" to "pressure_msl", "humidity" to "relative_humidity_2m", "dew" to "dew_point_2m", "uv" to "uv_index", "aqi" to "us_aqi", "sea" to "sea_level_height_msl")
        private val cache = object : LinkedHashMap<String, CacheEntry>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?) = size > 20
        }
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
