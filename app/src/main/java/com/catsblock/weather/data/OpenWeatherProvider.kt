package com.catsblock.weather.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

internal class OpenWeatherProvider(private val get: suspend (String) -> String) {

    private fun code(id: Int): Int = when (id) {
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

    private fun weatherId(o: JSONObject): Int =
        o.optJSONArray("weather")?.optJSONObject(0)?.optInt("id", 804) ?: 804

    private fun wet(o: JSONObject): Double =
        (o.optJSONObject("rain")?.optDouble("1h", 0.0) ?: 0.0) + (o.optJSONObject("snow")?.optDouble("1h", 0.0) ?: 0.0)

    suspend fun fetch(city: City, baseUrl: String): WeatherBundle {
        val url = "$baseUrl/v1/openweather".toHttpUrl().newBuilder()
            .addQueryParameter("lat", String.format(Locale.US, "%.4f", city.lat))
            .addQueryParameter("lon", String.format(Locale.US, "%.4f", city.lon))
            .build()
            .toString()
        val root = JSONObject(get(url))
        val offset = root.optInt("timezone_offset", 0)
        val zone = ZoneOffset.ofTotalSeconds(offset)
        fun local(epoch: Long): LocalDateTime = LocalDateTime.ofEpochSecond(epoch, 0, zone)

        val cur = root.getJSONObject("current")
        val now = local(cur.getLong("dt"))

        val dayList = root.optJSONArray("daily")
        val days = ArrayList<Day>()
        val sun = HashMap<LocalDate, Pair<LocalDateTime, LocalDateTime>>()
        if (dayList != null) {
            for (i in 0 until min(dayList.length(), 8)) {
                val d = dayList.getJSONObject(i)
                val sunrise = local(d.getLong("sunrise"))
                val sunset = local(d.getLong("sunset"))
                val date = local(d.getLong("dt")).toLocalDate()
                sun[date] = sunrise to sunset
                val temp = d.getJSONObject("temp")
                days += Day(
                    date = date,
                    max = temp.getDouble("max"),
                    min = temp.getDouble("min"),
                    code = code(weatherId(d)),
                    precipSum = d.optDouble("rain", 0.0) + d.optDouble("snow", 0.0),
                    rainChance = (d.optDouble("pop", 0.0) * 100.0).roundToInt(),
                    sunrise = sunrise,
                    sunset = sunset,
                    uvMax = d.optDouble("uvi", 0.0)
                )
            }
        }

        val hourList = root.optJSONArray("hourly")
        val hours = ArrayList<Hour>()
        if (hourList != null) {
            for (i in 0 until hourList.length()) {
                val o = hourList.getJSONObject(i)
                val time = local(o.getLong("dt"))
                val bounds = sun[time.toLocalDate()]
                val isDay = if (bounds != null) time.isAfter(bounds.first) && time.isBefore(bounds.second) else time.hour in 6..18
                hours += Hour(
                    time = time,
                    temp = o.getDouble("temp"),
                    feels = o.optDouble("feels_like", o.getDouble("temp")),
                    code = code(weatherId(o)),
                    isDay = isDay,
                    precip = wet(o),
                    windSpeed = o.optDouble("wind_speed", 0.0) * 3.6,
                    windDir = o.optDouble("wind_deg", 0.0),
                    humidity = o.optInt("humidity", 0),
                    uv = o.optDouble("uvi", 0.0),
                    aqi = null,
                    pressure = o.optDouble("pressure", 1013.0),
                    visibility = o.optDouble("visibility", 10000.0)
                )
            }
        }

        val sunrise = if (cur.has("sunrise")) local(cur.getLong("sunrise")) else null
        val sunset = if (cur.has("sunset")) local(cur.getLong("sunset")) else null
        val isDay = if (sunrise != null && sunset != null) now.isAfter(sunrise) && now.isBefore(sunset) else now.hour in 6..18
        val curCode = code(weatherId(cur))
        val wind = cur.optDouble("wind_speed", 0.0) * 3.6
        val current = Current(
            now = now,
            temp = cur.getDouble("temp"),
            feels = cur.optDouble("feels_like", cur.getDouble("temp")),
            text = wmoText(curCode, isDay),
            kind = wmoCondition(curCode, wind),
            isDay = isDay,
            windSpeed = wind,
            windDir = cur.optDouble("wind_deg", 0.0),
            humidity = cur.optInt("humidity", 0),
            uv = cur.optDouble("uvi", 0.0),
            aqi = null,
            pressure = cur.optDouble("pressure", 1013.0),
            visibility = cur.optDouble("visibility", 10000.0)
        )

        return WeatherBundle(
            current = current,
            hours = hours,
            days = days,
            rain = emptyList(),
            utcOffsetSeconds = offset,
            alerts = GovAlertParser.openWeather(root)
        )
    }
}
