package com.catsblock.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RoutePoint(val lat: Double, val lon: Double)

data class TravelPlace(val label: String, val point: RoutePoint)

data class TravelStop(
    val index: Int,
    val distanceKm: Double,
    val point: RoutePoint,
    val arrival: LocalDateTime,
    val kind: Condition,
    val isDay: Boolean,
    val precipMm: Double,
    val visibilityKm: Double,
    val cloudPercent: Int
)

data class TravelPlan(
    val from: TravelPlace,
    val to: TravelPlace,
    val path: List<RoutePoint>,
    val distanceKm: Double,
    val durationMinutes: Int,
    val stops: List<TravelStop>
)

class TravelException(val key: String) : IOException(key)

object TravelPlanner {

    private const val STOP_COUNT = 6
    private const val MAX_HOURS = 60

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun plan(fromQuery: String, toQuery: String, start: City?): TravelPlan {
        val from = resolve(fromQuery, start)
        val to = resolve(toQuery, null)
        val route = route(from.point, to.point)
        val samples = sample(route.path, route.distanceKm)
        val departure = Instant.now()
        val stops = forecast(samples, route.durationMinutes, departure)
        return TravelPlan(from, to, route.path, route.distanceKm, route.durationMinutes, stops)
    }

    private suspend fun resolve(query: String, fallback: City?): TravelPlace {
        val text = query.trim()
        if (fallback != null && (text.isEmpty() || text.equals(fallback.name, ignoreCase = true))) {
            return TravelPlace(fallback.name, RoutePoint(fallback.lat, fallback.lon))
        }
        if (text.length < 2) throw TravelException("Enter a start and a destination.")
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", text)
            .addQueryParameter("count", "1")
            .addQueryParameter("language", "en")
            .addQueryParameter("format", "json")
            .build()
        val results = JSONObject(get(url.toString())).optJSONArray("results")
        val first = results?.optJSONObject(0) ?: throw TravelException("We could not find one of the places.")
        val label = listOf(first.optString("name"), first.optString("admin1"), first.optString("country_code"))
            .filter { it.isNotBlank() }
            .joinToString(", ")
        return TravelPlace(label, RoutePoint(first.getDouble("latitude"), first.getDouble("longitude")))
    }

    private class Route(val path: List<RoutePoint>, val distanceKm: Double, val durationMinutes: Int)

    private suspend fun route(from: RoutePoint, to: RoutePoint): Route {
        val coordinates = "${from.lon},${from.lat};${to.lon},${to.lat}"
        val url = "https://router.project-osrm.org/route/v1/driving/$coordinates".toHttpUrl().newBuilder()
            .addQueryParameter("overview", "full")
            .addQueryParameter("geometries", "geojson")
            .build()
        val json = JSONObject(get(url.toString()))
        val routes = json.optJSONArray("routes")
        val best = routes?.optJSONObject(0) ?: throw TravelException("No driving route was found between these places.")
        val coords = best.getJSONObject("geometry").getJSONArray("coordinates")
        val path = (0 until coords.length()).map {
            val pair = coords.getJSONArray(it)
            RoutePoint(pair.getDouble(1), pair.getDouble(0))
        }
        if (path.size < 2) throw TravelException("No driving route was found between these places.")
        return Route(
            path = simplify(path),
            distanceKm = best.getDouble("distance") / 1000.0,
            durationMinutes = (best.getDouble("duration") / 60.0).toInt().coerceAtLeast(1)
        )
    }

    private fun simplify(path: List<RoutePoint>): List<RoutePoint> {
        if (path.size <= 400) return path
        val step = path.size / 400.0
        val reduced = (0 until 400).map { path[(it * step).toInt()] }
        return reduced + path.last()
    }

    private class Sample(val point: RoutePoint, val distanceKm: Double, val fraction: Double)

    private fun sample(path: List<RoutePoint>, totalKm: Double): List<Sample> {
        val cumulative = DoubleArray(path.size)
        for (i in 1 until path.size) cumulative[i] = cumulative[i - 1] + haversineKm(path[i - 1], path[i])
        val length = cumulative.last().coerceAtLeast(0.001)
        return (0 until STOP_COUNT).map { n ->
            val target = length * n / (STOP_COUNT - 1)
            var i = 1
            while (i < path.lastIndex && cumulative[i] < target) i++
            val span = (cumulative[i] - cumulative[i - 1]).coerceAtLeast(0.0001)
            val t = ((target - cumulative[i - 1]) / span).coerceIn(0.0, 1.0)
            val a = path[i - 1]
            val b = path[i]
            val point = RoutePoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            val fraction = n.toDouble() / (STOP_COUNT - 1)
            Sample(point, totalKm * fraction, fraction)
        }
    }

    private suspend fun forecast(samples: List<Sample>, durationMinutes: Int, departure: Instant): List<TravelStop> {
        val tripHours = durationMinutes / 60.0
        if (tripHours > MAX_HOURS) throw TravelException("This trip is too long to forecast.")
        val days = ((tripHours + 24) / 24).toInt().coerceIn(2, 4)
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", samples.joinToString(",") { "%.4f".format(java.util.Locale.US, it.point.lat) })
            .addQueryParameter("longitude", samples.joinToString(",") { "%.4f".format(java.util.Locale.US, it.point.lon) })
            .addQueryParameter("hourly", "precipitation,visibility,cloud_cover,weather_code,is_day")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", days.toString())
            .build()
        val raw = get(url.toString())
        val array = if (raw.trimStart().startsWith("[")) JSONArray(raw) else JSONArray().put(JSONObject(raw))
        if (array.length() != samples.size) throw TravelException("The weather service returned incomplete data.")
        return samples.mapIndexed { index, sample ->
            val item = array.getJSONObject(index)
            val offset = item.optInt("utc_offset_seconds", 0)
            val arrivalUtc = departure.plusSeconds((durationMinutes * 60L * sample.fraction).toLong())
            val arrival = LocalDateTime.ofEpochSecond(arrivalUtc.epochSecond + offset, 0, ZoneOffset.UTC)
            val hourly = item.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val key = arrival.withMinute(0).withSecond(0).withNano(0)
            var slot = 0
            for (i in 0 until times.length()) {
                if (LocalDateTime.parse(times.getString(i)) <= key) slot = i else break
            }
            TravelStop(
                index = index,
                distanceKm = sample.distanceKm,
                point = sample.point,
                arrival = arrival,
                kind = wmoCondition(hourly.getJSONArray("weather_code").optInt(slot, 0)),
                isDay = hourly.getJSONArray("is_day").optInt(slot, 1) == 1,
                precipMm = hourly.getJSONArray("precipitation").optDouble(slot, 0.0).takeIf { !it.isNaN() } ?: 0.0,
                visibilityKm = (hourly.getJSONArray("visibility").optDouble(slot, 10000.0).takeIf { !it.isNaN() } ?: 10000.0) / 1000.0,
                cloudPercent = hourly.getJSONArray("cloud_cover").optInt(slot, 0)
            )
        }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", "CatsBlockWeather").build()).execute().use { response ->
            if (!response.isSuccessful) throw TravelException("The route service is unavailable. Try again later.")
            response.body?.string() ?: throw TravelException("The route service is unavailable. Try again later.")
        }
    }

    private fun haversineKm(a: RoutePoint, b: RoutePoint): Double {
        val r = 6371.0
        val dLat = (b.lat - a.lat) * PI / 180.0
        val dLon = (b.lon - a.lon) * PI / 180.0
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(a.lat * PI / 180.0) * cos(b.lat * PI / 180.0) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}
