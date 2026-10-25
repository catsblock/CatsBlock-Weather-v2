package com.catsblock.weather.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class WeatherRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

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

    suspend fun summary(city: City): CitySummary {
        val raw = get(
            url(
                "https://api.open-meteo.com/v1/forecast",
                "latitude" to city.lat.toString(),
                "longitude" to city.lon.toString(),
                "current" to "temperature_2m,weather_code,is_day",
                "daily" to "temperature_2m_max,temperature_2m_min",
                "forecast_days" to "1",
                "timezone" to "auto"
            )
        )
        val root = JSONObject(raw)
        val cur = root.getJSONObject("current")
        val now = cur.getDouble("temperature_2m")
        val daily = root.optJSONObject("daily")
        return CitySummary(
            kind = wmoCondition(cur.getInt("weather_code")),
            isDay = cur.getInt("is_day") == 1,
            temp = now,
            min = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, now) ?: now,
            max = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, now) ?: now,
            localTime = runCatching { LocalDateTime.parse(cur.getString("time")).toLocalTime() }.getOrNull()
        )
    }

    suspend fun fetch(city: City, source: WeatherSource = WeatherSource.OpenMeteo): WeatherBundle {
        val key = when (source) {
            WeatherSource.OpenMeteo -> ""
            WeatherSource.WeatherApi -> SecretKeys.weather()
            WeatherSource.VisualCrossing -> SecretKeys.visual()
        }
        if (key.isEmpty()) return fetchOpenMeteo(city)
        return try {
            fetchAlternative(city, source, key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fetchOpenMeteo(city)
        }
    }

    private suspend fun fetchAlternative(city: City, source: WeatherSource, key: String): WeatherBundle = coroutineScope {
        val air = async { runCatching { airMap(city) }.getOrDefault(emptyMap()) }
        val bundle = try {
            when (source) {
                WeatherSource.WeatherApi -> WeatherApiProvider { get(it) }.fetch(city, key)
                else -> VisualCrossingProvider { get(it) }.fetch(city, key)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Provider request failed")
        }
        val aqi = air.await()
        bundle.copy(
            current = bundle.current.copy(aqi = aqi[bundle.current.now.truncatedTo(ChronoUnit.HOURS).toString()]),
            hours = bundle.hours.map { it.copy(aqi = aqi[it.time.toString()]) }
        )
    }

    private suspend fun airMap(city: City): Map<String, Double> {
        val raw = get(
            url(
                "https://air-quality-api.open-meteo.com/v1/air-quality",
                "latitude" to city.lat.toString(),
                "longitude" to city.lon.toString(),
                "hourly" to "us_aqi",
                "timezone" to "auto",
                "past_days" to "1",
                "forecast_days" to "7"
            )
        )
        val h = JSONObject(raw).getJSONObject("hourly")
        val t = h.getJSONArray("time")
        val v = h.getJSONArray("us_aqi")
        val map = HashMap<String, Double>()
        for (i in 0 until t.length()) {
            if (!v.isNull(i)) map[LocalDateTime.parse(t.getString(i)).toString()] = v.getDouble(i)
        }
        return map
    }

    private suspend fun fetchOpenMeteo(city: City): WeatherBundle = coroutineScope {
        val lat = city.lat.toString()
        val lon = city.lon.toString()
        val forecast = async {
            get(
                url(
                    "https://api.open-meteo.com/v1/forecast",
                    "latitude" to lat,
                    "longitude" to lon,
                    "current" to "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m",
                    "hourly" to "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_direction_10m,uv_index,is_day,pressure_msl,visibility",
                    "minutely_15" to "precipitation",
                    "forecast_minutely_15" to "48",
                    "daily" to "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,sunrise,sunset,uv_index_max",
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
        val pressureArr = hourly.getJSONArray("pressure_msl")
        val visArr = hourly.getJSONArray("visibility")

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
                aqi = aqiMap[time.toString()],
                pressure = pressureArr.optDouble(i, 1013.0),
                visibility = visArr.optDouble(i, 10000.0)
            )
        }

        val rain = runCatching {
            val minutely = root.getJSONObject("minutely_15")
            val t = minutely.getJSONArray("time")
            val v = minutely.getJSONArray("precipitation")
            (0 until t.length()).map { i ->
                RainSlot(LocalDateTime.parse(t.getString(i)), v.optDouble(i, 0.0))
            }
        }.getOrDefault(emptyList())

        val daily = root.getJSONObject("daily")
        val dTimes = daily.getJSONArray("time")
        val days = (0 until dTimes.length()).map { i ->
            Day(
                date = LocalDate.parse(dTimes.getString(i)),
                max = daily.getJSONArray("temperature_2m_max").optDouble(i, 0.0),
                min = daily.getJSONArray("temperature_2m_min").optDouble(i, 0.0),
                code = daily.getJSONArray("weather_code").optInt(i, 3),
                precipSum = daily.getJSONArray("precipitation_sum").optDouble(i, 0.0),
                rainChance = daily.getJSONArray("precipitation_probability_max").optInt(i, 0),
                sunrise = LocalDateTime.parse(daily.getJSONArray("sunrise").getString(i)),
                sunset = LocalDateTime.parse(daily.getJSONArray("sunset").getString(i)),
                uvMax = daily.getJSONArray("uv_index_max").optDouble(i, 0.0)
            )
        }

        val cur = root.getJSONObject("current")
        val now = LocalDateTime.parse(cur.getString("time"))
        val code = cur.getInt("weather_code")
        val day = cur.getInt("is_day") == 1
        val nowKey = now.truncatedTo(ChronoUnit.HOURS).toString()
        val hourNow = hours.firstOrNull { it.time.toString() == nowKey }

        val current = Current(
            now = now,
            temp = cur.getDouble("temperature_2m"),
            feels = cur.getDouble("apparent_temperature"),
            text = wmoText(code, day),
            kind = wmoCondition(code, cur.getDouble("wind_speed_10m")),
            isDay = day,
            windSpeed = cur.getDouble("wind_speed_10m"),
            windDir = cur.getDouble("wind_direction_10m"),
            humidity = cur.getInt("relative_humidity_2m"),
            uv = hourNow?.uv ?: 0.0,
            aqi = aqiMap[nowKey],
            pressure = hourNow?.pressure ?: 1013.0,
            visibility = hourNow?.visibility ?: 10000.0
        )

        WeatherBundle(
            current = current,
            hours = hours,
            days = days,
            rain = rain,
            utcOffsetSeconds = root.optInt("utc_offset_seconds", 0)
        )
    }
}
