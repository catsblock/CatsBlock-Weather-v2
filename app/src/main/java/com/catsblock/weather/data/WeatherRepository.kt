package com.catsblock.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private data class AccuCurrent(
    val text: String,
    val icon: Int,
    val isDay: Boolean,
    val temp: Double?,
    val feels: Double?,
    val humidity: Int?,
    val wind: Double?,
    val windDir: Double?,
    val uv: Double?
)

class WeatherRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val locationKeys = ConcurrentHashMap<String, String>()
    private val translations = ConcurrentHashMap<String, String>()

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

    private fun url(base: String, vararg params: Pair<String, String>): String {
        val builder = base.toHttpUrl().newBuilder()
        params.forEach { builder.addQueryParameter(it.first, it.second) }
        return builder.build().toString()
    }

    suspend fun translate(text: String): String {
        if (text.isBlank()) return text
        translations[text]?.let { return it }
        return try {
            val raw = get(
                url(
                    "https://translate.googleapis.com/translate_a/single",
                    "client" to "gtx",
                    "sl" to "auto",
                    "tl" to "en",
                    "dt" to "t",
                    "q" to text
                )
            )
            val segments = JSONArray(raw).getJSONArray(0)
            val out = StringBuilder()
            for (i in 0 until segments.length()) out.append(segments.getJSONArray(i).optString(0))
            val result = out.toString().ifBlank { text }
            translations[text] = result
            result
        } catch (e: Exception) {
            text
        }
    }

    suspend fun searchCities(query: String): List<City> {
        val raw = get(
            url(
                "https://geocoding-api.open-meteo.com/v1/search",
                "name" to query,
                "count" to "20",
                "language" to "en",
                "format" to "json"
            )
        )
        val results = JSONObject(raw).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map {
            val o = results.getJSONObject(it)
            City(
                name = o.optString("name"),
                region = o.optString("admin1", ""),
                country = o.optString("country", ""),
                code = o.optString("country_code", "").uppercase(),
                lat = o.getDouble("latitude"),
                lon = o.getDouble("longitude")
            )
        }
    }

    suspend fun summary(city: City, apiKey: String): CitySummary = coroutineScope {
        val forecast = async {
            get(
                url(
                    "https://api.open-meteo.com/v1/forecast",
                    "latitude" to city.lat.toString(),
                    "longitude" to city.lon.toString(),
                    "current" to "temperature_2m,weather_code,is_day",
                    "timezone" to "auto"
                )
            )
        }
        val alert = async {
            if (apiKey.isBlank()) false
            else runCatching { accuAlerts(city, apiKey).isNotEmpty() }.getOrDefault(false)
        }
        val cur = JSONObject(forecast.await()).getJSONObject("current")
        CitySummary(
            kind = wmoCondition(cur.getInt("weather_code")),
            isDay = cur.getInt("is_day") == 1,
            temp = cur.getDouble("temperature_2m"),
            hasAlert = alert.await()
        )
    }

    suspend fun fetch(city: City, apiKey: String): WeatherBundle = coroutineScope {
        val lat = city.lat.toString()
        val lon = city.lon.toString()
        val forecast = async {
            get(
                url(
                    "https://api.open-meteo.com/v1/forecast",
                    "latitude" to lat,
                    "longitude" to lon,
                    "current" to "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m",
                    "hourly" to "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_direction_10m,uv_index,is_day",
                    "daily" to "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset,uv_index_max",
                    "timezone" to "auto",
                    "past_days" to "1",
                    "forecast_days" to "7",
                    "wind_speed_unit" to "kmh"
                )
            )
        }
        val air = async {
            runCatching {
                get(
                    url(
                        "https://air-quality-api.open-meteo.com/v1/air-quality",
                        "latitude" to lat,
                        "longitude" to lon,
                        "hourly" to "us_aqi",
                        "timezone" to "auto",
                        "past_days" to "1",
                        "forecast_days" to "7"
                    )
                )
            }.getOrNull()
        }
        val accu = async {
            if (apiKey.isBlank()) null
            else runCatching { accuCurrent(city, apiKey) }.getOrNull()
        }
        val alerts = async {
            if (apiKey.isBlank()) emptyList<String>()
            else runCatching { accuAlerts(city, apiKey) }.getOrDefault(emptyList())
        }

        val root = JSONObject(forecast.await())

        val aqiMap = HashMap<String, Double>()
        air.await()?.let { raw ->
            runCatching {
                val h = JSONObject(raw).getJSONObject("hourly")
                val t = h.getJSONArray("time")
                val v = h.getJSONArray("us_aqi")
                for (i in 0 until t.length()) {
                    if (!v.isNull(i)) aqiMap[LocalDateTime.parse(t.getString(i)).toString()] = v.getDouble(i)
                }
            }
        }

        val hourly = root.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val feels = hourly.getJSONArray("apparent_temperature")
        val hum = hourly.getJSONArray("relative_humidity_2m")
        val precip = hourly.getJSONArray("precipitation")
        val codes = hourly.getJSONArray("weather_code")
        val wind = hourly.getJSONArray("wind_speed_10m")
        val windDir = hourly.getJSONArray("wind_direction_10m")
        val uv = hourly.getJSONArray("uv_index")
        val isDay = hourly.getJSONArray("is_day")

        val hours = (0 until times.length()).map { i ->
            val time = LocalDateTime.parse(times.getString(i))
            Hour(
                time = time,
                temp = temps.optDouble(i, 0.0),
                feels = feels.optDouble(i, 0.0),
                code = codes.optInt(i, 3),
                isDay = isDay.optInt(i, 1) == 1,
                precip = precip.optDouble(i, 0.0),
                windSpeed = wind.optDouble(i, 0.0),
                windDir = windDir.optDouble(i, 0.0),
                humidity = hum.optInt(i, 0),
                uv = uv.optDouble(i, 0.0),
                aqi = aqiMap[time.toString()]
            )
        }

        val daily = root.getJSONObject("daily")
        val dTimes = daily.getJSONArray("time")
        val days = (0 until dTimes.length()).map { i ->
            Day(
                date = LocalDate.parse(dTimes.getString(i)),
                max = daily.getJSONArray("temperature_2m_max").optDouble(i, 0.0),
                min = daily.getJSONArray("temperature_2m_min").optDouble(i, 0.0),
                code = daily.getJSONArray("weather_code").optInt(i, 3),
                precipSum = daily.getJSONArray("precipitation_sum").optDouble(i, 0.0),
                sunrise = LocalDateTime.parse(daily.getJSONArray("sunrise").getString(i)),
                sunset = LocalDateTime.parse(daily.getJSONArray("sunset").getString(i)),
                uvMax = daily.getJSONArray("uv_index_max").optDouble(i, 0.0)
            )
        }

        val cur = root.getJSONObject("current")
        val now = LocalDateTime.parse(cur.getString("time"))
        val omCode = cur.getInt("weather_code")
        val omDay = cur.getInt("is_day") == 1
        val nowKey = now.truncatedTo(ChronoUnit.HOURS).toString()
        val hourNow = hours.firstOrNull { it.time.toString() == nowKey }
        val a = accu.await()

        val current = Current(
            now = now,
            temp = a?.temp ?: cur.getDouble("temperature_2m"),
            feels = a?.feels ?: cur.getDouble("apparent_temperature"),
            text = if (a != null) translate(a.text) else wmoText(omCode, omDay),
            kind = if (a != null) accuCondition(a.icon) else wmoCondition(omCode),
            isDay = a?.isDay ?: omDay,
            windSpeed = a?.wind ?: cur.getDouble("wind_speed_10m"),
            windDir = a?.windDir ?: cur.getDouble("wind_direction_10m"),
            humidity = a?.humidity ?: cur.getInt("relative_humidity_2m"),
            uv = a?.uv ?: hourNow?.uv ?: 0.0,
            aqi = aqiMap[nowKey]
        )

        WeatherBundle(
            current = current,
            hours = hours,
            days = days,
            alerts = alerts.await(),
            usedAccuWeather = a != null
        )
    }

    private suspend fun locationKey(city: City, apiKey: String): String {
        locationKeys[city.id]?.let { return it }
        val raw = get(
            url(
                "https://dataservice.accuweather.com/locations/v1/cities/geoposition/search",
                "apikey" to apiKey,
                "q" to "${city.lat},${city.lon}"
            )
        )
        val key = JSONObject(raw).getString("Key")
        locationKeys[city.id] = key
        return key
    }

    private fun JSONObject.metric(name: String): Double? =
        optJSONObject(name)?.optJSONObject("Metric")?.optDouble("Value", Double.NaN)?.takeIf { !it.isNaN() }

    private suspend fun accuCurrent(city: City, apiKey: String): AccuCurrent {
        val key = locationKey(city, apiKey)
        val raw = get(
            url(
                "https://dataservice.accuweather.com/currentconditions/v1/$key",
                "apikey" to apiKey,
                "details" to "true"
            )
        )
        val o = JSONArray(raw).getJSONObject(0)
        return AccuCurrent(
            text = o.optString("WeatherText", ""),
            icon = o.optInt("WeatherIcon", 7),
            isDay = o.optBoolean("IsDayTime", true),
            temp = o.metric("Temperature"),
            feels = o.metric("RealFeelTemperature"),
            humidity = if (o.has("RelativeHumidity") && !o.isNull("RelativeHumidity")) o.optInt("RelativeHumidity") else null,
            wind = o.optJSONObject("Wind")?.metric("Speed"),
            windDir = o.optJSONObject("Wind")?.optJSONObject("Direction")?.optDouble("Degrees", Double.NaN)?.takeIf { !it.isNaN() },
            uv = if (o.has("UVIndex") && !o.isNull("UVIndex")) o.optDouble("UVIndex") else null
        )
    }

    private suspend fun accuAlerts(city: City, apiKey: String): List<String> {
        val key = locationKey(city, apiKey)
        val raw = get(
            url(
                "https://dataservice.accuweather.com/alerts/v1/$key",
                "apikey" to apiKey
            )
        )
        val arr = JSONArray(raw)
        val titles = (0 until arr.length()).mapNotNull {
            arr.getJSONObject(it).optJSONObject("Description")?.optString("Localized")?.takeIf { s -> s.isNotBlank() }
        }
        return titles.distinct().map { translate(it) }
    }
}
