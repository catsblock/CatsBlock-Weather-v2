package com.catsblock.weather.widget

import android.content.Context
import com.catsblock.weather.data.City
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.WeatherBundle
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class WidgetHour(
    val time: LocalDateTime,
    val temp: Double,
    val kind: Condition,
    val isDay: Boolean
)

data class WidgetSnapshot(
    val city: String,
    val savedAt: Long,
    val cityNow: LocalDateTime,
    val temp: Double,
    val feels: Double,
    val kind: Condition,
    val isDay: Boolean,
    val high: Double,
    val low: Double,
    val humidity: Int,
    val uv: Double,
    val aqi: Double?,
    val wind: Double,
    val precip: Double,
    val hours: List<WidgetHour>
) {

    private fun estimatedNow(): LocalDateTime {
        val elapsed = (System.currentTimeMillis() - savedAt).coerceAtLeast(0L)
        return cityNow.plus(elapsed, ChronoUnit.MILLIS)
    }

    private fun liveHour(): WidgetHour? {
        val floor = estimatedNow().truncatedTo(ChronoUnit.HOURS)
        return hours.firstOrNull { it.time == floor }
    }

    fun liveDay(): Boolean = liveHour()?.isDay ?: isDay

    fun liveKind(): Condition = liveHour()?.kind ?: kind

    fun upcomingHours(count: Int): List<WidgetHour> {
        val floor = estimatedNow().truncatedTo(ChronoUnit.HOURS)
        val future = hours.filter { !it.time.isBefore(floor) }
        return (if (future.size >= count) future else hours.takeLast(count)).take(count)
    }

    fun toJson(): String {
        val array = JSONArray()
        hours.forEach {
            array.put(
                JSONObject()
                    .put("t", it.time.toString())
                    .put("v", it.temp)
                    .put("k", it.kind.name)
                    .put("d", it.isDay)
            )
        }
        val root = JSONObject()
            .put("city", city)
            .put("savedAt", savedAt)
            .put("now", cityNow.toString())
            .put("temp", temp)
            .put("feels", feels)
            .put("kind", kind.name)
            .put("day", isDay)
            .put("high", high)
            .put("low", low)
            .put("hum", humidity)
            .put("uv", uv)
            .put("wind", wind)
            .put("precip", precip)
            .put("hours", array)
        if (aqi != null) root.put("aqi", aqi)
        return root.toString()
    }

    companion object {

        fun fromJson(raw: String): WidgetSnapshot? = runCatching {
            val o = JSONObject(raw)
            val array = o.getJSONArray("hours")
            WidgetSnapshot(
                city = o.getString("city"),
                savedAt = o.getLong("savedAt"),
                cityNow = LocalDateTime.parse(o.getString("now")),
                temp = o.getDouble("temp"),
                feels = o.getDouble("feels"),
                kind = Condition.valueOf(o.getString("kind")),
                isDay = o.getBoolean("day"),
                high = o.getDouble("high"),
                low = o.getDouble("low"),
                humidity = o.getInt("hum"),
                uv = o.getDouble("uv"),
                aqi = if (o.has("aqi")) o.getDouble("aqi") else null,
                wind = o.getDouble("wind"),
                precip = o.getDouble("precip"),
                hours = (0 until array.length()).map {
                    val h = array.getJSONObject(it)
                    WidgetHour(
                        time = LocalDateTime.parse(h.getString("t")),
                        temp = h.getDouble("v"),
                        kind = Condition.valueOf(h.getString("k")),
                        isDay = h.getBoolean("d")
                    )
                }
            )
        }.getOrNull()

        fun from(city: City, bundle: WeatherBundle): WidgetSnapshot {
            val cur = bundle.current
            val nowHour = cur.now.truncatedTo(ChronoUnit.HOURS)
            val start = bundle.hours.indexOfFirst { !it.time.isBefore(nowHour) }.coerceAtLeast(0)
            val today = cur.now.toLocalDate()
            val day = bundle.days.firstOrNull { it.date == today } ?: bundle.days.first()
            return WidgetSnapshot(
                city = city.name,
                savedAt = System.currentTimeMillis(),
                cityNow = cur.now,
                temp = cur.temp,
                feels = cur.feels,
                kind = cur.kind,
                isDay = cur.isDay,
                high = day.max,
                low = day.min,
                humidity = cur.humidity,
                uv = cur.uv,
                aqi = cur.aqi,
                wind = cur.windSpeed,
                precip = day.precipSum,
                hours = bundle.hours.drop(start).take(30).map {
                    WidgetHour(it.time, it.temp, it.kind, it.isDay)
                }
            )
        }
    }
}

object WidgetStore {

    private const val FILE = "widget_state"
    private const val KEY = "snapshot"

    fun save(context: Context, snapshot: WidgetSnapshot) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, snapshot.toJson())
            .apply()
    }

    fun load(context: Context): WidgetSnapshot? =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.let { WidgetSnapshot.fromJson(it) }
}
