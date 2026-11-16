package com.catsblock.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal data class MapPointWeather(
    val layer: String,
    val requestedTime: Instant,
    val actualTime: Instant,
    val selectedLatitude: Double,
    val selectedLongitude: Double,
    val gridLatitude: Double,
    val gridLongitude: Double,
    val values: Map<String, Double>,
    val reflectivityDbz: Double? = null,
    val error: String? = null
)

internal object MapPointWeatherLoader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()
    private val hourFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00", Locale.ROOT).withZone(ZoneOffset.UTC)
    private val hourlyFields = mapOf(
        "temp" to "temperature_2m",
        "clouds" to "cloud_cover",
        "wind" to "wind_speed_10m,wind_direction_10m,wind_gusts_10m",
        "pressure" to "pressure_msl",
        "humidity" to "relative_humidity_2m",
        "dew" to "dew_point_2m",
        "uv" to "uv_index",
        "aqi" to "us_aqi",
        "sea" to "sea_level_height_msl"
    )

    suspend fun load(latitude: Double, longitude: Double, layer: String, epochMillis: Long): MapPointWeather = withContext(Dispatchers.IO) {
        val requested = Instant.ofEpochMilli(epochMillis).truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -85.0..85.0 || longitude !in -180.0..180.0) {
            return@withContext error(latitude, longitude, layer, requested, "invalid_location")
        }
        if (layer == "precip") {
            val dbz = RadarProbe.sampleReflectivity(latitude, longitude, requested.epochSecond)
                ?: return@withContext error(latitude, longitude, layer, requested, "no_echo")
            val rate = reflectivityToRate(dbz)
            return@withContext MapPointWeather(
                layer, requested, requested, latitude, longitude, latitude, longitude,
                mapOf("rain_rate_mm_h" to rate, "rain_15m_mm" to rate / 4.0), dbz
            )
        }
        val fields = hourlyFields[layer] ?: return@withContext error(latitude, longitude, layer, requested, "unsupported_layer")
        val api = when (layer) {
            "aqi" -> "https://air-quality-api.open-meteo.com/v1/air-quality"
            "sea" -> "https://marine-api.open-meteo.com/v1/marine"
            else -> "https://api.open-meteo.com/v1/forecast"
        }
        runCatching {
            val hour = hourFormatter.format(requested)
            val url = api.toHttpUrl().newBuilder()
                .addQueryParameter("latitude", String.format(Locale.ROOT, "%.5f", latitude))
                .addQueryParameter("longitude", String.format(Locale.ROOT, "%.5f", longitude))
                .addQueryParameter("hourly", fields)
                .addQueryParameter("start_hour", hour)
                .addQueryParameter("end_hour", hour)
                .addQueryParameter("timezone", "UTC")
                .build()
            val body = client.newCall(Request.Builder().url(url).header("User-Agent", "CatsBlockWeather/2.0").build())
                .execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("http_${response.code}")
                    response.body?.string() ?: throw IllegalStateException("empty_response")
                }
            val json = JSONObject(body)
            if (json.optBoolean("error")) throw IllegalStateException(json.optString("reason", "provider_error"))
            val hourly = json.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val index = closestHour(times, requested)
            val actual = Instant.parse(times.getString(index) + ":00Z")
            val values = linkedMapOf<String, Double>()
            fields.split(',').forEach { field ->
                val array = hourly.optJSONArray(field)
                val value = array?.nullableNumber(index)
                if (value != null && value.isFinite()) values[field] = value
            }
            if (values.isEmpty()) throw IllegalStateException("no_value")
            MapPointWeather(
                layer = layer,
                requestedTime = requested,
                actualTime = actual,
                selectedLatitude = latitude,
                selectedLongitude = longitude,
                gridLatitude = json.optDouble("latitude", latitude),
                gridLongitude = json.optDouble("longitude", longitude),
                values = values
            )
        }.getOrElse { error(latitude, longitude, layer, requested, it.message ?: "provider_error") }
    }

    private fun closestHour(times: JSONArray, requested: Instant): Int {
        var bestIndex = 0
        var bestDelta = Long.MAX_VALUE
        for (index in 0 until times.length()) {
            val actual = Instant.parse(times.getString(index) + ":00Z")
            val delta = abs(actual.epochSecond - requested.epochSecond)
            if (delta < bestDelta) {
                bestIndex = index
                bestDelta = delta
            }
        }
        return bestIndex
    }

    private fun JSONArray.nullableNumber(index: Int): Double? {
        if (index !in 0 until length() || isNull(index)) return null
        val value = optDouble(index, Double.NaN)
        return value.takeIf { it.isFinite() }
    }

    private fun error(latitude: Double, longitude: Double, layer: String, time: Instant, reason: String) =
        MapPointWeather(layer, time, time, latitude, longitude, latitude, longitude, emptyMap(), error = reason)
}
