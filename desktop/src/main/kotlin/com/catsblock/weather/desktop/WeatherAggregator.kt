package com.catsblock.weather.desktop

import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class Provider { OpenMeteo, WeatherApi, OpenWeather, VisualCrossing }

private class Contribution(val provider: Provider, val data: Conditions)

private object WorkerProviders {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val clock = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    private fun call(path: String, lat: Double, lon: Double): JSONObject? = runCatching {
        val base = WorkerConfig.baseUrl
        val token = WorkerConfig.appToken
        if (base.isEmpty() || token.isEmpty()) return@runCatching null
        val url = String.format(Locale.US, "%s%s?lat=%.4f&lon=%.4f", base, path, lat, lon)
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("X-App-Token", token)
            .header("X-Device-Id", DeviceFingerprint.id)
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() in 200..299 && response.body().length < 4_000_000) JSONObject(response.body()) else null
    }.getOrNull()

    private fun wetPrecip(o: JSONObject?, key: String): Double = o?.optDouble(key, 0.0) ?: 0.0

    private fun weatherApiCode(raw: Int): Int = when (raw) {
        1000 -> 0
        1003 -> 2
        1006, 1009 -> 3
        1030, 1135, 1147 -> 45
        1150, 1153 -> 51
        1072, 1168 -> 56
        1171 -> 57
        1063, 1180, 1183 -> 61
        1186, 1189 -> 63
        1192, 1195 -> 65
        1198 -> 66
        1069, 1201, 1204, 1207, 1249, 1252 -> 67
        1066, 1210, 1213, 1255 -> 71
        1216, 1219 -> 73
        1114, 1117, 1222, 1225, 1258 -> 75
        1237, 1261, 1264 -> 77
        1240 -> 80
        1243 -> 81
        1246 -> 82
        1087, 1273, 1276, 1279, 1282 -> 95
        else -> 3
    }

    private fun openWeatherCode(id: Int): Int = when (id) {
        in 200..232 -> 95
        in 300..321 -> 51
        500 -> 61
        501 -> 63
        502, 503, 504 -> 65
        511 -> 67
        520 -> 80
        521 -> 81
        522, 531 -> 82
        600 -> 71
        601 -> 73
        602 -> 75
        611, 612, 613, 615, 616 -> 67
        620, 621 -> 85
        622 -> 86
        701, 711, 721, 731, 741, 751, 761, 762 -> 45
        781 -> 95
        800 -> 0
        801 -> 1
        802 -> 2
        else -> 3
    }

    private fun crossingCode(icon: String, precip: Double, daily: Boolean): Int {
        val light = if (daily) 2.0 else 0.5
        val heavy = if (daily) 10.0 else 4.0
        val wet = when {
            precip < light -> 61
            precip < heavy -> 63
            else -> 65
        }
        return when {
            icon.startsWith("clear") -> 0
            icon.startsWith("partly-cloudy") -> 2
            icon == "cloudy" -> 3
            icon == "wind" -> 2
            icon == "fog" -> 45
            icon == "freezing-rain" -> 66
            icon == "sleet" -> 67
            icon == "hail" -> 96
            icon.startsWith("thunder") -> 95
            icon.startsWith("snow-showers") -> 85
            icon == "snow" -> if (precip >= heavy) 75 else 73
            icon.startsWith("showers") -> 80
            icon == "rain" -> wet
            else -> 3
        }
    }

    private fun parseStamp(value: String): LocalDateTime {
        val parts = value.trim().split(' ')
        return LocalDateTime.of(LocalDate.parse(parts[0]), LocalTime.parse(parts[1].padStart(5, '0')))
    }

    private fun astro(date: LocalDate, value: String, fallback: LocalTime): LocalDateTime =
        runCatching { date.atTime(LocalTime.parse(value.trim().uppercase(Locale.ENGLISH), clock)) }
            .getOrElse { date.atTime(fallback) }

    private fun clockOf(value: String, fallback: LocalTime): LocalTime =
        runCatching { LocalTime.parse(value.trim().padStart(8, '0')) }.getOrDefault(fallback)

    private fun peak(o: JSONObject, a: String, b: String): Int = max(o.optInt(a, 0), o.optInt(b, 0))

    fun weatherApi(lat: Double, lon: Double): Conditions? {
        val root = call("/v1/weather", lat, lon) ?: return null
        return runCatching {
            val now = parseStamp(root.getJSONObject("location").getString("localtime"))
            val hours = ArrayList<Hour>()
            val days = ArrayList<Day>()
            val forecastDays = root.getJSONObject("forecast").getJSONArray("forecastday")
            for (i in 0 until forecastDays.length()) {
                val entry = forecastDays.getJSONObject(i)
                val date = LocalDate.parse(entry.getString("date"))
                val day = entry.getJSONObject("day")
                val sun = entry.getJSONObject("astro")
                days += Day(
                    date, day.getDouble("maxtemp_c"), day.getDouble("mintemp_c"),
                    weatherApiCode(day.getJSONObject("condition").getInt("code")),
                    day.optDouble("totalprecip_mm", 0.0), peak(day, "daily_chance_of_rain", "daily_chance_of_snow"),
                    astro(date, sun.optString("sunrise"), LocalTime.of(6, 0)),
                    astro(date, sun.optString("sunset"), LocalTime.of(18, 0)),
                    day.optDouble("uv", 0.0)
                )
                val list = entry.getJSONArray("hour")
                for (h in 0 until list.length()) {
                    val o = list.getJSONObject(h)
                    val temp = o.getDouble("temp_c")
                    val humidity = o.optInt("humidity", 0)
                    hours += Hour(
                        parseStamp(o.getString("time")), temp, weatherApiCode(o.getJSONObject("condition").getInt("code")),
                        o.optInt("is_day", 1) == 1, o.optDouble("precip_mm", 0.0), peak(o, "chance_of_rain", "chance_of_snow"),
                        o.optDouble("wind_kph", 0.0), o.optDouble("uv", 0.0), o.optDouble("pressure_mb", 1013.0),
                        o.optDouble("vis_km", 10.0) * 1000.0, humidity, o.optInt("cloud", 0), o.optDouble("wind_degree", 0.0),
                        o.optDouble("gust_kph", o.optDouble("wind_kph", 0.0)), null,
                        o.optDouble("dewpoint_c", dewPoint(temp, humidity)), o.optDouble("feelslike_c", temp)
                    )
                }
            }
            val cur = root.getJSONObject("current")
            val temp = cur.getDouble("temp_c")
            val humidity = cur.optInt("humidity", 0)
            Conditions(
                now, temp, cur.optDouble("feelslike_c", temp), humidity, cur.optDouble("wind_kph", 0.0),
                cur.optDouble("wind_degree", 0.0), weatherApiCode(cur.getJSONObject("condition").getInt("code")),
                cur.optInt("is_day", 1) == 1, cur.optDouble("uv", 0.0), null, cur.optDouble("pressure_mb", 1013.0),
                cur.optDouble("vis_km", 10.0) * 1000.0, hours, days, cur.optInt("cloud", 0),
                cur.optDouble("gust_kph", cur.optDouble("wind_kph", 0.0)), cur.optDouble("dewpoint_c", dewPoint(temp, humidity))
            )
        }.getOrNull()
    }

    fun openWeather(lat: Double, lon: Double): Conditions? {
        val root = call("/v1/openweather", lat, lon) ?: return null
        return runCatching {
            val zone = ZoneOffset.ofTotalSeconds(root.optInt("timezone_offset", 0))
            fun local(epoch: Long): LocalDateTime = LocalDateTime.ofEpochSecond(epoch, 0, zone)
            fun weatherId(o: JSONObject): Int = o.optJSONArray("weather")?.optJSONObject(0)?.optInt("id", 804) ?: 804
            fun wet(o: JSONObject): Double = wetPrecip(o.optJSONObject("rain"), "1h") + wetPrecip(o.optJSONObject("snow"), "1h")
            val cur = root.getJSONObject("current")
            val now = local(cur.getLong("dt"))
            val days = ArrayList<Day>()
            val sun = HashMap<LocalDate, Pair<LocalDateTime, LocalDateTime>>()
            val dayList = root.optJSONArray("daily")
            if (dayList != null) {
                for (i in 0 until min(dayList.length(), 8)) {
                    val d = dayList.getJSONObject(i)
                    val sunrise = local(d.getLong("sunrise"))
                    val sunset = local(d.getLong("sunset"))
                    val date = local(d.getLong("dt")).toLocalDate()
                    sun[date] = sunrise to sunset
                    val temp = d.getJSONObject("temp")
                    days += Day(
                        date, temp.getDouble("max"), temp.getDouble("min"), openWeatherCode(weatherId(d)),
                        d.optDouble("rain", 0.0) + d.optDouble("snow", 0.0), (d.optDouble("pop", 0.0) * 100.0).roundToInt(),
                        sunrise, sunset, d.optDouble("uvi", 0.0)
                    )
                }
            }
            val hours = ArrayList<Hour>()
            val hourList = root.optJSONArray("hourly")
            if (hourList != null) {
                for (i in 0 until hourList.length()) {
                    val o = hourList.getJSONObject(i)
                    val time = local(o.getLong("dt"))
                    val bounds = sun[time.toLocalDate()]
                    val isDay = if (bounds != null) time.isAfter(bounds.first) && time.isBefore(bounds.second) else time.hour in 6..18
                    val temp = o.getDouble("temp")
                    val wind = o.optDouble("wind_speed", 0.0) * 3.6
                    hours += Hour(
                        time, temp, openWeatherCode(weatherId(o)), isDay, wet(o), (o.optDouble("pop", 0.0) * 100.0).roundToInt(),
                        wind, o.optDouble("uvi", 0.0), o.optDouble("pressure", 1013.0), o.optDouble("visibility", 10000.0),
                        o.optInt("humidity", 0), o.optInt("clouds", 0), o.optDouble("wind_deg", 0.0),
                        o.optDouble("wind_gust", o.optDouble("wind_speed", 0.0)) * 3.6, null,
                        o.optDouble("dew_point", dewPoint(temp, o.optInt("humidity", 50))), o.optDouble("feels_like", temp)
                    )
                }
            }
            val sunrise = if (cur.has("sunrise")) local(cur.getLong("sunrise")) else null
            val sunset = if (cur.has("sunset")) local(cur.getLong("sunset")) else null
            val isDay = if (sunrise != null && sunset != null) now.isAfter(sunrise) && now.isBefore(sunset) else now.hour in 6..18
            val temp = cur.getDouble("temp")
            Conditions(
                now, temp, cur.optDouble("feels_like", temp), cur.optInt("humidity", 0), cur.optDouble("wind_speed", 0.0) * 3.6,
                cur.optDouble("wind_deg", 0.0), openWeatherCode(weatherId(cur)), isDay, cur.optDouble("uvi", 0.0), null,
                cur.optDouble("pressure", 1013.0), cur.optDouble("visibility", 10000.0), hours, days, cur.optInt("clouds", 0),
                cur.optDouble("wind_gust", cur.optDouble("wind_speed", 0.0)) * 3.6,
                cur.optDouble("dew_point", dewPoint(temp, cur.optInt("humidity", 50)))
            )
        }.getOrNull()
    }

    fun visualCrossing(lat: Double, lon: Double): Conditions? {
        val root = call("/v1/visual", lat, lon) ?: return null
        return runCatching {
            val offset = (root.optDouble("tzoffset", 0.0) * 3600.0).roundToInt()
            val cur = root.getJSONObject("currentConditions")
            val now = LocalDateTime.ofEpochSecond(cur.getLong("datetimeEpoch") + offset, 0, ZoneOffset.UTC)
            val dayList = root.getJSONArray("days")
            val hours = ArrayList<Hour>()
            val days = ArrayList<Day>()
            var todaySun: Pair<LocalDateTime, LocalDateTime>? = null
            for (i in 0 until min(dayList.length(), 8)) {
                val d = dayList.getJSONObject(i)
                val date = LocalDate.parse(d.getString("datetime"))
                val sunrise = date.atTime(clockOf(d.optString("sunrise"), LocalTime.of(6, 0)))
                val sunset = date.atTime(clockOf(d.optString("sunset"), LocalTime.of(18, 0)))
                if (date == now.toLocalDate()) todaySun = sunrise to sunset
                val precip = d.optDouble("precip", 0.0)
                days += Day(
                    date, d.getDouble("tempmax"), d.getDouble("tempmin"), crossingCode(d.optString("icon"), precip, true),
                    precip, d.optDouble("precipprob", 0.0).roundToInt(), sunrise, sunset, d.optDouble("uvindex", 0.0)
                )
                val list = d.optJSONArray("hours") ?: continue
                for (h in 0 until list.length()) {
                    val o = list.getJSONObject(h)
                    val time = date.atTime(clockOf(o.getString("datetime"), LocalTime.MIDNIGHT))
                    val hourPrecip = o.optDouble("precip", 0.0)
                    val temp = o.getDouble("temp")
                    val humidity = o.optDouble("humidity", 0.0).roundToInt()
                    hours += Hour(
                        time, temp, crossingCode(o.optString("icon"), hourPrecip, false), time.isAfter(sunrise) && time.isBefore(sunset),
                        hourPrecip, o.optDouble("precipprob", 0.0).roundToInt(), o.optDouble("windspeed", 0.0), o.optDouble("uvindex", 0.0),
                        o.optDouble("pressure", 1013.0), o.optDouble("visibility", 10.0) * 1000.0, humidity,
                        o.optDouble("cloudcover", 0.0).roundToInt(), o.optDouble("winddir", 0.0),
                        o.optDouble("windgust", o.optDouble("windspeed", 0.0)), null,
                        o.optDouble("dew", dewPoint(temp, humidity)), o.optDouble("feelslike", temp)
                    )
                }
            }
            val sun = todaySun
            val isDay = if (sun != null) now.isAfter(sun.first) && now.isBefore(sun.second) else now.hour in 6..18
            val temp = cur.getDouble("temp")
            val humidity = cur.optDouble("humidity", 0.0).roundToInt()
            Conditions(
                now, temp, cur.optDouble("feelslike", temp), humidity, cur.optDouble("windspeed", 0.0), cur.optDouble("winddir", 0.0),
                crossingCode(cur.optString("icon"), cur.optDouble("precip", 0.0), false), isDay, cur.optDouble("uvindex", 0.0), null,
                cur.optDouble("pressure", 1013.0), cur.optDouble("visibility", 10.0) * 1000.0, hours, days,
                cur.optDouble("cloudcover", 0.0).roundToInt(), cur.optDouble("windgust", cur.optDouble("windspeed", 0.0)),
                cur.optDouble("dew", dewPoint(temp, humidity))
            )
        }.getOrNull()
    }
}

private object Consensus {

    private const val WET_HOUR_MM = 0.1
    private const val WET_DAY_MM = 1.0
    private const val WET_PROB_FLOOR = 75
    private const val DRY_PROB_CEILING = 49
    private const val CURRENT_SOURCES = 2

    fun required(participants: Int): Int = when {
        participants <= 2 -> max(1, participants)
        else -> participants - 1
    }

    private fun wetCode(code: Int): Boolean = code in 51..67 || code in 71..77 || code in 80..86 || code in 95..99

    private fun wetHour(h: Hour): Boolean = h.precip >= WET_HOUR_MM || wetCode(h.code)

    private fun wetDay(d: Day): Boolean = d.precipSum >= WET_DAY_MM || wetCode(d.code)

    private fun mode(codes: List<Int>, fallback: Int): Int =
        codes.groupingBy { it }.eachCount().entries
            .maxWithOrNull(compareBy<Map.Entry<Int, Int>>({ it.value }, { it.key }))?.key ?: fallback

    private fun mean(values: List<Double>, fallback: Double = 0.0): Double =
        if (values.isEmpty()) fallback else values.sum() / values.size

    private fun direction(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val x = values.sumOf { cos(Math.toRadians(it)) }
        val y = values.sumOf { sin(Math.toRadians(it)) }
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun merge(results: List<Contribution>): Conditions {
        val sorted = results.sortedBy { it.provider.ordinal }
        val hours = LinkedHashMap<LocalDateTime, MutableList<Hour>>()
        val days = LinkedHashMap<LocalDate, MutableList<Day>>()
        sorted.forEach { r ->
            r.data.hours.forEach { hours.getOrPut(it.time) { ArrayList() } += it }
            r.data.days.forEach { days.getOrPut(it.date) { ArrayList() } += it }
        }
        val mergedHours = hours.entries.sortedBy { it.key }.map { hour(it.value) }
        val mergedDays = days.entries.sortedBy { it.key }.map { day(it.value) }
        return current(sorted.take(CURRENT_SOURCES).map { it.data }, mergedHours, mergedDays)
    }

    private fun hour(list: List<Hour>): Hour {
        val wet = list.filter { wetHour(it) }
        val dry = list.filterNot { wetHour(it) }
        val rainy = wet.size >= required(list.size)
        val voters = if (rainy) wet else dry
        val prob = mean(list.map { it.prob.toDouble() }).roundToInt()
        val first = list.first()
        return Hour(
            time = first.time,
            temp = mean(list.map { it.temp }),
            code = mode(voters.map { it.code }, first.code),
            isDay = list.count { it.isDay } * 2 >= list.size,
            precip = if (rainy) max(WET_HOUR_MM, mean(voters.map { it.precip })) else mean(voters.map { it.precip }),
            prob = if (rainy) max(prob, WET_PROB_FLOOR) else min(prob, DRY_PROB_CEILING),
            windKmh = mean(list.map { it.windKmh }),
            uv = mean(list.map { it.uv }),
            pressure = mean(list.map { it.pressure }, 1013.0),
            visibilityM = mean(list.map { it.visibilityM }, 10000.0),
            humidity = mean(list.map { it.humidity.toDouble() }).roundToInt(),
            cloud = mean(list.map { it.cloud.toDouble() }).roundToInt(),
            windDir = direction(list.map { it.windDir }),
            gust = mean(list.map { it.gust }),
            aqi = list.mapNotNull { it.aqi }.takeIf { it.isNotEmpty() }?.average(),
            dew = mean(list.map { it.dew }),
            feels = mean(list.map { it.feels })
        )
    }

    private fun day(list: List<Day>): Day {
        val wet = list.filter { wetDay(it) }
        val dry = list.filterNot { wetDay(it) }
        val rainy = wet.size >= required(list.size)
        val voters = if (rainy) wet else dry
        val chance = mean(list.map { it.prob.toDouble() }).roundToInt()
        val first = list.first()
        return Day(
            date = first.date,
            max = mean(list.map { it.max }),
            min = mean(list.map { it.min }),
            code = mode(voters.map { it.code }, first.code),
            precipSum = if (rainy) max(WET_HOUR_MM, mean(voters.map { it.precipSum })) else mean(voters.map { it.precipSum }),
            prob = if (rainy) max(chance, WET_PROB_FLOOR) else min(chance, DRY_PROB_CEILING),
            sunrise = first.sunrise,
            sunset = first.sunset,
            uvMax = mean(list.map { it.uvMax })
        )
    }

    private fun current(picks: List<Conditions>, hours: List<Hour>, days: List<Day>): Conditions {
        val wet = picks.filter { it.kind.isWet }
        val rainy = wet.size >= required(picks.size)
        val chosen = (if (rainy) wet else picks.filterNot { it.kind.isWet }).firstOrNull() ?: picks.first()
        val key = chosen.now.withMinute(0).withSecond(0).withNano(0)
        val hourAqi = hours.firstOrNull { it.time == key }?.aqi
        return Conditions(
            now = chosen.now,
            temp = mean(picks.map { it.temp }),
            feels = mean(picks.map { it.feels }),
            humidity = mean(picks.map { it.humidity.toDouble() }).roundToInt(),
            windKmh = mean(picks.map { it.windKmh }),
            windDir = direction(picks.map { it.windDir }),
            code = chosen.code,
            isDay = chosen.isDay,
            uv = mean(picks.map { it.uv }),
            aqi = hourAqi ?: picks.mapNotNull { it.aqi }.takeIf { it.isNotEmpty() }?.average(),
            pressure = mean(picks.map { it.pressure }, 1013.0),
            visibilityM = mean(picks.map { it.visibilityM }, 10000.0),
            hours = hours,
            days = days,
            cloud = mean(picks.map { it.cloud.toDouble() }).roundToInt(),
            gust = mean(picks.map { it.gust }),
            dew = mean(picks.map { it.dew })
        )
    }
}

object AggregatedWeather {

    private const val TTL_MS = 30L * 60L * 1000L
    private const val MAX_ENTRIES = 32

    private class Entry(val at: Long, val value: Conditions)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Any>()
    private val pool = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "weather-provider").apply { isDaemon = true }
    }

    private fun key(lat: Double, lon: Double): String = String.format(Locale.US, "%.2f,%.2f", lat, lon)

    private fun fresh(key: String): Conditions? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.at >= TTL_MS) {
            cache.remove(key, entry)
            return null
        }
        return entry.value
    }

    private fun store(key: String, value: Conditions) {
        val now = System.currentTimeMillis()
        cache.entries.removeAll { now - it.value.at >= TTL_MS }
        while (cache.size >= MAX_ENTRIES) {
            val oldest = cache.entries.minByOrNull { it.value.at } ?: break
            cache.remove(oldest.key)
        }
        cache[key] = Entry(now, value)
    }

    private fun run(provider: Provider, block: () -> Conditions?): CompletableFuture<Contribution?> =
        CompletableFuture.supplyAsync({
            runCatching { block() }.getOrNull()?.let { Contribution(provider, it) }
        }, pool)

    fun load(lat: Double, lon: Double): Conditions? {
        val key = key(lat, lon)
        fresh(key)?.let { return it }
        return synchronized(locks.getOrPut(key) { Any() }) { fresh(key) ?: build(key, lat, lon) }
    }

    private fun build(key: String, lat: Double, lon: Double): Conditions? {
        val jobs = listOf(
            run(Provider.OpenMeteo) { WeatherClient.forecast(lat, lon) },
            run(Provider.WeatherApi) { WorkerProviders.weatherApi(lat, lon) },
            run(Provider.OpenWeather) { WorkerProviders.openWeather(lat, lon) },
            run(Provider.VisualCrossing) { WorkerProviders.visualCrossing(lat, lon) }
        )
        val results = jobs.mapNotNull { it.join() }
        if (results.isEmpty()) return null
        val merged = Consensus.merge(results)
        store(key, merged)
        return merged
    }
}
