package com.catsblock.weather.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

internal class WeatherApiProvider(private val get: suspend (String) -> String) {

    private val clock = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    private fun parse(value: String): LocalDateTime {
        val parts = value.trim().split(' ')
        return LocalDateTime.of(LocalDate.parse(parts[0]), LocalTime.parse(parts[1].padStart(5, '0')))
    }

    private fun astro(date: LocalDate, value: String, fallback: LocalTime): LocalDateTime =
        runCatching { date.atTime(LocalTime.parse(value.trim().uppercase(Locale.ENGLISH), clock)) }
            .getOrElse { date.atTime(fallback) }

    private fun code(raw: Int): Int = when (raw) {
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

    suspend fun fetch(city: City, baseUrl: String): WeatherBundle {
        val url = "$baseUrl/v1/weather".toHttpUrl().newBuilder()
            .addQueryParameter("lat", String.format(Locale.US, "%.4f", city.lat))
            .addQueryParameter("lon", String.format(Locale.US, "%.4f", city.lon))
            .build()
            .toString()
        val root = JSONObject(get(url))

        val location = root.getJSONObject("location")
        val now = parse(location.getString("localtime"))
        val diff = now.toEpochSecond(ZoneOffset.UTC) - location.getLong("localtime_epoch")
        val offset = (((diff + 450) / 900) * 900).toInt()

        val forecastDays = root.getJSONObject("forecast").getJSONArray("forecastday")
        val hours = ArrayList<Hour>()
        val days = ArrayList<Day>()
        for (i in 0 until forecastDays.length()) {
            val entry = forecastDays.getJSONObject(i)
            val date = LocalDate.parse(entry.getString("date"))
            val day = entry.getJSONObject("day")
            val sun = entry.getJSONObject("astro")
            days += Day(
                date = date,
                max = day.getDouble("maxtemp_c"),
                min = day.getDouble("mintemp_c"),
                code = code(day.getJSONObject("condition").getInt("code")),
                precipSum = day.optDouble("totalprecip_mm", 0.0),
                rainChance = max(day.optInt("daily_chance_of_rain", 0), day.optInt("daily_chance_of_snow", 0)),
                sunrise = astro(date, sun.optString("sunrise"), LocalTime.of(6, 0)),
                sunset = astro(date, sun.optString("sunset"), LocalTime.of(18, 0)),
                uvMax = day.optDouble("uv", 0.0)
            )
            val list = entry.getJSONArray("hour")
            for (h in 0 until list.length()) {
                val o = list.getJSONObject(h)
                hours += Hour(
                    time = parse(o.getString("time")),
                    temp = o.getDouble("temp_c"),
                    feels = o.optDouble("feelslike_c", o.getDouble("temp_c")),
                    code = code(o.getJSONObject("condition").getInt("code")),
                    isDay = o.optInt("is_day", 1) == 1,
                    precip = o.optDouble("precip_mm", 0.0),
                    windSpeed = o.optDouble("wind_kph", 0.0),
                    windDir = o.optDouble("wind_degree", 0.0),
                    humidity = o.optInt("humidity", 0),
                    uv = o.optDouble("uv", 0.0),
                    aqi = null,
                    pressure = o.optDouble("pressure_mb", 1013.0),
                    visibility = o.optDouble("vis_km", 10.0) * 1000.0,
                    cloud = if (o.has("cloud")) o.optInt("cloud", -1) else -1
                )
            }
        }

        val cur = root.getJSONObject("current")
        val curCode = code(cur.getJSONObject("condition").getInt("code"))
        val isDay = cur.optInt("is_day", 1) == 1
        val wind = cur.optDouble("wind_kph", 0.0)
        val current = Current(
            now = now,
            temp = cur.getDouble("temp_c"),
            feels = cur.optDouble("feelslike_c", cur.getDouble("temp_c")),
            text = wmoText(curCode, isDay),
            kind = wmoCondition(curCode, wind),
            isDay = isDay,
            windSpeed = wind,
            windDir = cur.optDouble("wind_degree", 0.0),
            humidity = cur.optInt("humidity", 0),
            uv = cur.optDouble("uv", 0.0),
            aqi = null,
            pressure = cur.optDouble("pressure_mb", 1013.0),
            visibility = cur.optDouble("vis_km", 10.0) * 1000.0,
            cloud = if (cur.has("cloud")) cur.optInt("cloud", -1) else -1
        )

        return WeatherBundle(
            current = current,
            hours = hours,
            days = days,
            rain = emptyList(),
            utcOffsetSeconds = offset,
            alerts = GovAlertParser.weatherApi(root)
        )
    }
}
