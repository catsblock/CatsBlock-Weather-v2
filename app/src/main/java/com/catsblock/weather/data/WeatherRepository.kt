package com.catsblock.weather.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import kotlin.math.max

internal class WorkerLimitException(val waitSeconds: Long, val exhausted: Boolean) : IOException("Worker limit reached")

class WeatherRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _usageStatus = MutableStateFlow(UsageStatus())
    val usageStatus: StateFlow<UsageStatus> = _usageStatus.asStateFlow()

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

    private fun applyUsage(prev: UsageStatus, path: String, response: okhttp3.Response): UsageStatus {
        val used = response.header("X-Provider-Used")?.toIntOrNull()
        val limit = response.header("X-Provider-Limit")?.toIntOrNull()
        val reset = response.header("X-Provider-Reset-Seconds")?.toLongOrNull() ?: 0L
        val monthUsed = response.header("X-Monthly-Used")?.toIntOrNull()
        val monthLimit = response.header("X-Monthly-Limit")?.toIntOrNull()
        val monthReset = response.header("X-Monthly-Reset-Seconds")?.toLongOrNull() ?: 0L
        if (used == null || limit == null || monthUsed == null || monthLimit == null) return prev
        return when {
            path.endsWith("/v1/weather") -> prev.copy(
                weatherUsed = used,
                weatherLimit = limit,
                weatherResetSeconds = reset,
                weatherMonthUsed = monthUsed,
                weatherMonthLimit = monthLimit,
                weatherMonthResetSeconds = monthReset
            )
            path.endsWith("/v1/visual") -> prev.copy(
                visualUsed = used,
                visualLimit = limit,
                visualResetSeconds = reset,
                visualMonthUsed = monthUsed,
                visualMonthLimit = monthLimit,
                visualMonthResetSeconds = monthReset
            )
            path.endsWith("/v1/openweather") -> prev.copy(
                openWeatherUsed = used,
                openWeatherLimit = limit,
                openWeatherResetSeconds = reset,
                openWeatherMonthUsed = monthUsed,
                openWeatherMonthLimit = monthLimit,
                openWeatherMonthResetSeconds = monthReset
            )
            else -> prev
        }
    }

    private suspend fun workerGet(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val prev = _usageStatus.value
            val shortRemaining = response.header("X-RateLimit-Remaining-10m")?.toIntOrNull() ?: prev.shortRemaining
            val shortLimit = response.header("X-RateLimit-Limit-10m")?.toIntOrNull() ?: prev.shortLimit
            val longRemaining = response.header("X-RateLimit-Remaining-12h")?.toIntOrNull() ?: prev.longRemaining
            val longLimit = response.header("X-RateLimit-Limit-12h")?.toIntOrNull() ?: prev.longLimit
            val lockoutSeconds = response.header("X-Lockout-Seconds")?.toLongOrNull() ?: 0L
            val exhaustedSeconds = response.header("X-Global-Exhausted-Seconds")?.toLongOrNull() ?: 0L
            val base = prev.copy(
                shortRemaining = shortRemaining,
                shortLimit = shortLimit,
                longRemaining = longRemaining,
                longLimit = longLimit,
                lockoutSeconds = lockoutSeconds,
                exhaustedSeconds = exhaustedSeconds,
                usingFallback = lockoutSeconds > 0 || exhaustedSeconds > 0
            )
            _usageStatus.value = applyUsage(base, response.request.url.encodedPath, response)
            if (!response.isSuccessful) {
                val wait = max(lockoutSeconds, exhaustedSeconds)
                if (response.code == 429 || response.code == 503 || wait > 0) {
                    throw WorkerLimitException(wait, exhaustedSeconds > 0)
                }
                throw IOException("HTTP ${response.code}")
            }
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

    private fun url(base: String, vararg params: Pair<String, String>): String {
        val builder = base.toHttpUrl().newBuilder()
        params.forEach { builder.addQueryParameter(it.first, it.second) }
        return builder.build().toString()
    }

    suspend fun refreshUsageStatus() {
        val base = SecretKeys.baseUrl()
        if (base.isEmpty()) return
        runCatching {
            val raw = workerGet("$base/v1/status")
            val root = JSONObject(raw)
            val rate = root.getJSONObject("rateLimit")
            val exhausted = root.getJSONObject("exhausted")
            val weatherExhausted = exhausted.optLong("weather", 0)
            val visualExhausted = exhausted.optLong("visual", 0)
            val openWeatherExhausted = exhausted.optLong("openweather", 0)
            val usage = root.optJSONObject("usage")
            val weatherUsage = usage?.optJSONObject("weather")
            val visualUsage = usage?.optJSONObject("visual")
            val openWeatherUsage = usage?.optJSONObject("openweather")
            val monthly = usage?.optJSONObject("monthly")
            val weatherMonth = monthly?.optJSONObject("weather")
            val visualMonth = monthly?.optJSONObject("visual")
            val openWeatherMonth = monthly?.optJSONObject("openweather")
            val prev = _usageStatus.value
            _usageStatus.value = prev.copy(
                shortRemaining = rate.optInt("shortRemaining", 20),
                shortLimit = rate.optInt("shortLimit", 20),
                longRemaining = rate.optInt("longRemaining", 150),
                longLimit = rate.optInt("longLimit", 150),
                lockoutSeconds = rate.optLong("lockoutSeconds", 0),
                exhaustedSeconds = max(max(weatherExhausted, visualExhausted), openWeatherExhausted),
                usingFallback = rate.optLong("lockoutSeconds", 0) > 0 || weatherExhausted > 0 || visualExhausted > 0 || openWeatherExhausted > 0,
                weatherUsed = weatherUsage?.optInt("used", prev.weatherUsed) ?: prev.weatherUsed,
                weatherLimit = weatherUsage?.optInt("limit", prev.weatherLimit) ?: prev.weatherLimit,
                weatherResetSeconds = weatherUsage?.optLong("resetSeconds", 0) ?: 0L,
                visualUsed = visualUsage?.optInt("used", prev.visualUsed) ?: prev.visualUsed,
                visualLimit = visualUsage?.optInt("limit", prev.visualLimit) ?: prev.visualLimit,
                visualResetSeconds = visualUsage?.optLong("resetSeconds", 0) ?: 0L,
                weatherMonthUsed = weatherMonth?.optInt("used", prev.weatherMonthUsed) ?: prev.weatherMonthUsed,
                weatherMonthLimit = weatherMonth?.optInt("limit", prev.weatherMonthLimit) ?: prev.weatherMonthLimit,
                weatherMonthResetSeconds = weatherMonth?.optLong("resetSeconds", 0) ?: 0L,
                visualMonthUsed = visualMonth?.optInt("used", prev.visualMonthUsed) ?: prev.visualMonthUsed,
                visualMonthLimit = visualMonth?.optInt("limit", prev.visualMonthLimit) ?: prev.visualMonthLimit,
                visualMonthResetSeconds = visualMonth?.optLong("resetSeconds", 0) ?: 0L,
                openWeatherUsed = openWeatherUsage?.optInt("used", prev.openWeatherUsed) ?: prev.openWeatherUsed,
                openWeatherLimit = openWeatherUsage?.optInt("limit", prev.openWeatherLimit) ?: prev.openWeatherLimit,
                openWeatherResetSeconds = openWeatherUsage?.optLong("resetSeconds", 0) ?: 0L,
                openWeatherMonthUsed = openWeatherMonth?.optInt("used", prev.openWeatherMonthUsed) ?: prev.openWeatherMonthUsed,
                openWeatherMonthLimit = openWeatherMonth?.optInt("limit", prev.openWeatherMonthLimit) ?: prev.openWeatherMonthLimit,
                openWeatherMonthResetSeconds = openWeatherMonth?.optLong("resetSeconds", 0) ?: 0L
            )
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
        if (source == WeatherSource.OpenMeteo) return fetchOpenMeteo(city)
        val base = SecretKeys.baseUrl()
        if (base.isEmpty()) return fetchOpenMeteo(city)
        return try {
            fetchViaWorker(city, source, base)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fetchOpenMeteo(city)
        }
    }

    private suspend fun fetchViaWorker(city: City, source: WeatherSource, base: String): WeatherBundle = coroutineScope {
        val air = async { runCatching { airMap(city) }.getOrDefault(emptyMap()) }
        val rainSlots = async { runCatching { rainMap(city) }.getOrDefault(emptyList()) }
        val bundle = try {
            when (source) {
                WeatherSource.WeatherApi -> WeatherApiProvider { workerGet(it) }.fetch(city, base)
                WeatherSource.OpenWeather -> OpenWeatherProvider { workerGet(it) }.fetch(city, base)
                else -> VisualCrossingProvider { workerGet(it) }.fetch(city, base)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: WorkerLimitException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Provider request failed")
        }
        val aqi = air.await()
        bundle.copy(
            rain = rainSlots.await(),
            current = bundle.current.copy(aqi = aqi[bundle.current.now.truncatedTo(ChronoUnit.HOURS).toString()]),
            hours = bundle.hours.map { it.copy(aqi = aqi[it.time.toString()]) }
        )
    }

    private suspend fun rainMap(city: City): List<RainSlot> {
        val raw = get(
            url(
                "https://api.open-meteo.com/v1/forecast",
                "latitude" to city.lat.toString(),
                "longitude" to city.lon.toString(),
                "minutely_15" to "precipitation",
                "forecast_minutely_15" to "48",
                "timezone" to "auto"
            )
        )
        val minutely = JSONObject(raw).getJSONObject("minutely_15")
        val t = minutely.getJSONArray("time")
        val v = minutely.getJSONArray("precipitation")
        return (0 until t.length()).map { i ->
            RainSlot(LocalDateTime.parse(t.getString(i)), v.optDouble(i, 0.0))
        }
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
