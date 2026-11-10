package com.catsblock.weather.data

import kotlinx.coroutines.withTimeoutOrNull
import com.catsblock.weather.WeatherApp
import com.catsblock.weather.ui.developer.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class WorkerHttpException(val code: Int) : IOException("Worker HTTP $code")

internal class WorkerLimitException(val waitSeconds: Long) : IOException("Worker limit reached")

class WeatherRepository {

    private companion object {
        val bundles = TtlCache<WeatherBundle>(WeatherAggregator.CACHE_TTL_MS, 48)
        val summaries = TtlCache<CitySummary>(WeatherAggregator.CACHE_TTL_MS, 64)
        val locks = ConcurrentHashMap<String, Mutex>()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _usageStatus = MutableStateFlow(UsageStatus())
    val usageStatus: StateFlow<UsageStatus> = _usageStatus.asStateFlow()

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        UserKeys.forPath(runCatching { java.net.URI(url).path }.getOrNull().orEmpty())?.let { builder.header("X-Api-Key", it) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

    private suspend fun workerGet(url: String): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty()
        UserKeys.forPath(path)?.let { builder.header("X-Api-Key", it) }
        SecretKeys.appToken().takeIf { it.isNotEmpty() }?.let { builder.header("X-App-Token", it) }
        builder.header("X-Device-Id", DeviceFingerprint.id)
        client.newCall(builder.build()).execute().use { response ->
            val lockoutSeconds = response.header("X-Lockout-Seconds")?.toLongOrNull() ?: 0L
            _usageStatus.update { it.copy(lockoutSeconds = lockoutSeconds) }
            if (!response.isSuccessful) {
                if (response.code == 429 || response.code == 503 || lockoutSeconds > 0) {
                    throw WorkerLimitException(lockoutSeconds)
                }
                throw WorkerHttpException(response.code)
            }
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
        val key = locationKey(city)
        summaries.get(key)?.let { return it }
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
        val result = CitySummary(
            kind = wmoCondition(cur.getInt("weather_code")),
            isDay = cur.getInt("is_day") == 1,
            temp = now,
            min = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, now) ?: now,
            max = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, now) ?: now,
            localTime = runCatching { LocalDateTime.parse(cur.getString("time")).toLocalTime() }.getOrNull()
        )
        summaries.put(key, result)
        return result
    }

    suspend fun fetch(city: City): WeatherBundle = fetchCached(locationKey(city), city, true)

    suspend fun fetchForRoute(city: City): WeatherBundle = fetchCached("route:" + locationKey(city), city, false)

    private suspend fun fetchCached(key: String, city: City, withRadar: Boolean): WeatherBundle {
        bundles.get(key)?.let { return it }
        return locks.getOrPut(key) { Mutex() }.withLock {
            bundles.get(key)?.let { return@withLock it }
            val loaded = load(city, withRadar)
            bundles.put(key, loaded)
            loaded
        }
    }

    private suspend fun load(city: City, withRadar: Boolean): WeatherBundle = coroutineScope {
        val base = SecretKeys.baseUrl()
        val viaWorker = base.isNotEmpty() && SecretKeys.appToken().isNotEmpty()
        val radar = if (withRadar) {
            async {
                try {
                    withTimeoutOrNull(5000) { RadarProbe.read(city.lat, city.lon) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            null
        }
        val jobs = ArrayList<Deferred<ProviderResult?>>()
        jobs += async { attempt(WeatherSource.OpenMeteo) { fetchOpenMeteo(city) } }
        if (viaWorker) {
            jobs += async { attempt(WeatherSource.WeatherApi) { WeatherApiProvider { workerGet(it) }.fetch(city, base) } }
            jobs += async { attempt(WeatherSource.OpenWeather) { OpenWeatherProvider { workerGet(it) }.fetch(city, base) } }
            jobs += async { attempt(WeatherSource.VisualCrossing) { VisualCrossingProvider { workerGet(it) }.fetch(city, base) } }
        }
        val results = jobs.awaitAll().filterNotNull()
        if (results.isEmpty()) throw IOException("No weather provider responded")
        val merged = WeatherAggregator.merge(results)
        val reading = radar?.await()
        AppLog.info("Weather", "Aggregated ${results.size}/${jobs.size} providers: ${merged.hours.size} hours, ${merged.days.size} days, radar=${reading != null}")
        WeatherDecisionEngine.apply(merged, reading, WeatherAggregator.trustedCurrent(results))
    }

    private suspend fun attempt(source: WeatherSource, block: suspend () -> WeatherBundle): ProviderResult? =
        try {
            ProviderResult(source, block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is WorkerHttpException && (e.code == 401 || e.code == 403)) {
                AppLog.warn("Weather", "Worker rejected the app token (HTTP ${e.code}). The APP_TOKEN used to build the APK must match the Worker secret APP_TOKEN.", e)
            } else {
                AppLog.warn("Weather", "Provider ${source.name} failed and was excluded from the aggregate", e)
            }
            null
        }

    private fun locationKey(city: City): String = String.format(Locale.US, "%.2f,%.2f", city.lat, city.lon)

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
                    "hourly" to "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_direction_10m,uv_index,is_day,pressure_msl,visibility,cloud_cover,precipitation_probability",
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
        val cloudArr = hourly.optJSONArray("cloud_cover")
        val probArr = hourly.optJSONArray("precipitation_probability")

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
                visibility = visArr.optDouble(i, 10000.0),
                cloud = if (cloudArr != null && !cloudArr.isNull(i)) cloudArr.optInt(i, -1) else -1,
                precipProb = if (probArr != null && !probArr.isNull(i)) probArr.optInt(i, -1) else -1
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
            visibility = hourNow?.visibility ?: 10000.0,
            cloud = hourNow?.cloud ?: -1
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
