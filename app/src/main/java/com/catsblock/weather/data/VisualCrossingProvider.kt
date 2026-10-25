package com.catsblock.weather.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt

internal class VisualCrossingProvider(private val get: suspend (String) -> String) {

    private fun clock(value: String, fallback: LocalTime): LocalTime =
        runCatching { LocalTime.parse(value.trim().padStart(8, '0')) }.getOrDefault(fallback)

    private fun code(icon: String, precip: Double, daily: Boolean): Int {
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

    suspend fun fetch(city: City, baseUrl: String): WeatherBundle {
        val url = "$baseUrl/v1/visual".toHttpUrl().newBuilder()
            .addQueryParameter("lat", String.format(Locale.US, "%.4f", city.lat))
            .addQueryParameter("lon", String.format(Locale.US, "%.4f", city.lon))
            .build()
            .toString()
        val root = JSONObject(get(url))
        val offset = (root.optDouble("tzoffset", 0.0) * 3600.0).roundToInt()

        val cur = root.getJSONObject("currentConditions")
        val now = LocalDateTime.ofEpochSecond(cur.getLong("datetimeEpoch") + offset, 0, ZoneOffset.UTC)

        val dayList = root.getJSONArray("days")
        val hours = ArrayList<Hour>()
        val days = ArrayList<Day>()
        var todaySun: Pair<LocalDateTime, LocalDateTime>? = null
        for (i in 0 until minOf(dayList.length(), 8)) {
            val d = dayList.getJSONObject(i)
            val date = LocalDate.parse(d.getString("datetime"))
            val sunrise = date.atTime(clock(d.optString("sunrise"), LocalTime.of(6, 0)))
            val sunset = date.atTime(clock(d.optString("sunset"), LocalTime.of(18, 0)))
            if (date == now.toLocalDate()) todaySun = sunrise to sunset
            val precip = d.optDouble("precip", 0.0)
            days += Day(
                date = date,
                max = d.getDouble("tempmax"),
                min = d.getDouble("tempmin"),
                code = code(d.optString("icon"), precip, true),
                precipSum = precip,
                rainChance = d.optDouble("precipprob", 0.0).roundToInt(),
                sunrise = sunrise,
                sunset = sunset,
                uvMax = d.optDouble("uvindex", 0.0)
            )
            val list = d.optJSONArray("hours") ?: continue
            for (h in 0 until list.length()) {
                val o = list.getJSONObject(h)
                val time = date.atTime(clock(o.getString("datetime"), LocalTime.MIDNIGHT))
                val hourPrecip = o.optDouble("precip", 0.0)
                hours += Hour(
                    time = time,
                    temp = o.getDouble("temp"),
                    feels = o.optDouble("feelslike", o.getDouble("temp")),
                    code = code(o.optString("icon"), hourPrecip, false),
                    isDay = time.isAfter(sunrise) && time.isBefore(sunset),
                    precip = hourPrecip,
                    windSpeed = o.optDouble("windspeed", 0.0),
                    windDir = o.optDouble("winddir", 0.0),
                    humidity = o.optDouble("humidity", 0.0).roundToInt(),
                    uv = o.optDouble("uvindex", 0.0),
                    aqi = null,
                    pressure = o.optDouble("pressure", 1013.0),
                    visibility = o.optDouble("visibility", 10.0) * 1000.0
                )
            }
        }

        val sun = todaySun
        val isDay = if (sun != null) now.isAfter(sun.first) && now.isBefore(sun.second) else now.hour in 6..18
        val nowPrecip = cur.optDouble("precip", 0.0)
        val curCode = code(cur.optString("icon"), nowPrecip, false)
        val wind = cur.optDouble("windspeed", 0.0)
        val current = Current(
            now = now,
            temp = cur.getDouble("temp"),
            feels = cur.optDouble("feelslike", cur.getDouble("temp")),
            text = wmoText(curCode, isDay),
            kind = wmoCondition(curCode, wind),
            isDay = isDay,
            windSpeed = wind,
            windDir = cur.optDouble("winddir", 0.0),
            humidity = cur.optDouble("humidity", 0.0).roundToInt(),
            uv = cur.optDouble("uvindex", 0.0),
            aqi = null,
            pressure = cur.optDouble("pressure", 1013.0),
            visibility = cur.optDouble("visibility", 10.0) * 1000.0
        )

        return WeatherBundle(current = current, hours = hours, days = days, rain = emptyList(), utcOffsetSeconds = offset)
    }
}
