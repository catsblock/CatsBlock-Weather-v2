package com.catsblock.weather.desktop

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.Properties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

object AppPaths {
    val dir: File = File(System.getProperty("user.home"), ".catsblock-weather")
}

enum class ThemeMode { System, Light, Dark }

enum class WindUnit(val label: String, val factor: Double) {
    Kmh("km/h", 1.0), Mph("mph", 0.621371), Ms("m/s", 0.277778), Knots("kn", 0.539957)
}

enum class PressureUnit(val label: String, val factor: Double, val decimals: Int) {
    Hpa("hPa", 1.0, 0), Kpa("kPa", 0.1, 1), InHg("inHg", 0.02953, 2), MmHg("mmHg", 0.750062, 0)
}

data class SavedCity(val name: String, val country: String, val lat: Double, val lon: Double) {
    val id: String get() = String.format(Locale.US, "%.2f,%.2f", lat, lon)
    val title: String get() = if (country.isBlank()) name else "$name, $country"
}

data class DesktopSettings(
    val language: String = defaultLanguage(),
    val fahrenheit: Boolean = false,
    val windUnit: WindUnit = WindUnit.Kmh,
    val pressureUnit: PressureUnit = PressureUnit.Hpa,
    val theme: ThemeMode = ThemeMode.System,
    val widgetVisible: Boolean = true,
    val colorMode: String = "weather",
    val fixedColor: Int = 0,
    val inactivePercent: Int = 25,
    val idleSeconds: Int = 5,
    val alwaysOnTop: Boolean = true,
    val widgetX: Int = -1,
    val widgetY: Int = -1,
    val startWithWindows: Boolean = true,
    val setupDone: Boolean = false,
    val cities: List<SavedCity> = emptyList(),
    val selected: Int = 0
) {
    val city: SavedCity? get() = cities.getOrNull(selected)
    val hasCity: Boolean get() = city != null
}

fun defaultLanguage(): String {
    val locale = Locale.getDefault()
    return when (locale.language) {
        "pt" -> if (locale.country == "BR") "pt-BR" else "pt-PT"
        "es", "fr", "ru", "de", "it", "ko" -> locale.language
        "in", "id" -> "id"
        else -> "en"
    }
}

object SettingsStore {

    private val file = File(AppPaths.dir, "settings.properties")

    fun load(): DesktopSettings {
        val d = DesktopSettings()
        if (!file.exists()) return d
        val p = Properties()
        runCatching { file.inputStream().use { p.load(it) } }
        fun s(k: String, def: String) = p.getProperty(k) ?: def
        val cities = ArrayList<SavedCity>()
        val count = s("cities", "0").toIntOrNull()?.coerceIn(0, 50) ?: 0
        for (i in 0 until count) {
            val parts = s("city.$i", "").split('\t')
            if (parts.size != 4) continue
            val lat = parts[2].toDoubleOrNull() ?: continue
            val lon = parts[3].toDoubleOrNull() ?: continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || parts[0].isBlank()) continue
            cities.add(SavedCity(parts[0], parts[1], lat, lon))
        }
        val legacy = s("city", "")
        if (cities.isEmpty() && legacy.isNotBlank()) {
            val lat = s("lat", "0").toDoubleOrNull() ?: 0.0
            val lon = s("lon", "0").toDoubleOrNull() ?: 0.0
            if (lat in -90.0..90.0 && lon in -180.0..180.0) cities.add(SavedCity(legacy, s("country", ""), lat, lon))
        }
        return DesktopSettings(
            language = s("language", d.language).takeIf { code -> LANGUAGES.any { it.first == code } } ?: d.language,
            fahrenheit = s("fahrenheit", "false").toBoolean(),
            windUnit = WindUnit.entries.firstOrNull { it.name == s("windUnit", "") } ?: d.windUnit,
            pressureUnit = PressureUnit.entries.firstOrNull { it.name == s("pressureUnit", "") } ?: d.pressureUnit,
            theme = ThemeMode.entries.firstOrNull { it.name == s("theme", "") } ?: d.theme,
            widgetVisible = s("widgetVisible", "true").toBoolean(),
            colorMode = s("colorMode", "weather").takeIf { it == "weather" || it == "fixed" } ?: "weather",
            fixedColor = s("fixedColor", "0").toIntOrNull()?.coerceIn(0, FIXED_COLORS.lastIndex) ?: 0,
            inactivePercent = s("inactivePercent", "25").toIntOrNull()?.coerceIn(10, 60) ?: 25,
            idleSeconds = s("idleSeconds", "5").toIntOrNull()?.coerceIn(0, 120) ?: 5,
            alwaysOnTop = s("alwaysOnTop", "true").toBoolean(),
            widgetX = s("widgetX", "-1").toIntOrNull() ?: -1,
            widgetY = s("widgetY", "-1").toIntOrNull() ?: -1,
            startWithWindows = s("startWithWindows", "true").toBoolean(),
            setupDone = s("setupDone", if (cities.isNotEmpty()) "true" else "false").toBoolean(),
            cities = cities,
            selected = (s("selected", "0").toIntOrNull() ?: 0).coerceIn(0, maxOf(0, cities.lastIndex))
        )
    }

    fun save(v: DesktopSettings) {
        val p = Properties()
        p["language"] = v.language
        p["fahrenheit"] = v.fahrenheit.toString()
        p["windUnit"] = v.windUnit.name
        p["pressureUnit"] = v.pressureUnit.name
        p["theme"] = v.theme.name
        p["widgetVisible"] = v.widgetVisible.toString()
        p["colorMode"] = v.colorMode
        p["fixedColor"] = v.fixedColor.toString()
        p["inactivePercent"] = v.inactivePercent.toString()
        p["idleSeconds"] = v.idleSeconds.toString()
        p["alwaysOnTop"] = v.alwaysOnTop.toString()
        p["widgetX"] = v.widgetX.toString()
        p["widgetY"] = v.widgetY.toString()
        p["startWithWindows"] = v.startWithWindows.toString()
        p["setupDone"] = v.setupDone.toString()
        p["cities"] = v.cities.size.toString()
        p["selected"] = v.selected.toString()
        v.cities.forEachIndexed { i, c ->
            fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
            p["city.$i"] = listOf(clean(c.name), clean(c.country), c.lat.toString(), c.lon.toString()).joinToString("\t")
        }
        runCatching {
            file.parentFile.mkdirs()
            val temp = File(file.parentFile, "settings.properties.tmp")
            temp.outputStream().use { p.store(it, null) }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

enum class Kind(val key: String) {
    Clear("Clear"), FewClouds("Mostly clear"), MoreClouds("Partly cloudy"), Cloudy("Cloudy"), Fog("Fog"),
    Drizzle("Drizzle"), Rain("Rain"), Storm("Thunderstorm"), Sleet("Wintry mix"), Hail("Hail"),
    Snow("Snow"), HeavySnow("Heavy snow"), Blizzard("Blizzard");

    val isSnow: Boolean get() = this == Snow || this == HeavySnow || this == Blizzard
    val isWet: Boolean get() = ordinal >= Drizzle.ordinal
    fun label(isDay: Boolean): String = if (this == Clear && isDay) t("Sunny") else t(key)
}

fun kindOf(code: Int, windKmh: Double = 0.0): Kind {
    val snow = when (code) {
        71, 73, 85 -> Kind.Snow
        75, 86 -> Kind.HeavySnow
        77 -> Kind.Hail
        else -> null
    }
    if (snow != null) return if (windKmh >= 45.0 && snow != Kind.Hail) Kind.Blizzard else snow
    return when (code) {
        0 -> Kind.Clear
        1 -> Kind.FewClouds
        2 -> Kind.MoreClouds
        3 -> Kind.Cloudy
        45, 48 -> Kind.Fog
        51, 53, 55, 61, 80 -> Kind.Drizzle
        56, 57, 66, 67 -> Kind.Sleet
        63, 65, 81, 82 -> Kind.Rain
        in 95..99 -> Kind.Storm
        else -> Kind.Cloudy
    }
}

data class Hour(
    val time: LocalDateTime, val temp: Double, val code: Int, val isDay: Boolean,
    val precip: Double, val prob: Int, val windKmh: Double, val uv: Double,
    val pressure: Double, val visibilityM: Double
) {
    val kind: Kind get() = kindOf(code, windKmh)
}

data class Day(
    val date: LocalDate, val max: Double, val min: Double, val code: Int, val precipSum: Double,
    val prob: Int, val sunrise: LocalDateTime, val sunset: LocalDateTime, val uvMax: Double
) {
    val kind: Kind get() = kindOf(code)
}

data class Conditions(
    val now: LocalDateTime, val temp: Double, val feels: Double, val humidity: Int,
    val windKmh: Double, val windDir: Double, val code: Int, val isDay: Boolean, val uv: Double,
    val aqi: Double?, val pressure: Double, val visibilityM: Double,
    val hours: List<Hour>, val days: List<Day>
) {
    val kind: Kind get() = kindOf(code, windKmh)
    val nextHours: List<Hour>
        get() = hours.filter { !it.time.isBefore(now.truncatedTo(ChronoUnit.HOURS)) }.take(36)
    fun hoursOf(day: Day): List<Hour> = hours.filter { it.time.toLocalDate() == day.date }
    val today: Day? get() = days.firstOrNull()
    val wetAhead: Hour?
        get() = nextHours.take(8).firstOrNull { it.kind.isWet && (it.prob >= 50 || it.precip >= 0.2) }
}

data class Place(val name: String, val country: String, val lat: Double, val lon: Double)

object WeatherClient {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    private fun get(url: String): JSONObject? = runCatching {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "CatsBlockWeather-Desktop/2.0 (+https://github.com/catsblock/CatsBlock-Weather-v2)")
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() in 200..299 && response.body().length < 4_000_000) JSONObject(response.body()) else null
    }.getOrNull()

    private fun JSONArray?.num(i: Int, def: Double): Double =
        if (this == null || i >= length() || isNull(i)) def else optDouble(i, def)

    fun search(query: String, language: String): List<Place> {
        val q = java.net.URLEncoder.encode(query.trim().take(80), "UTF-8")
        val lang = language.substringBefore('-')
        val root = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=$lang") ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val r = results.optJSONObject(i) ?: return@mapNotNull null
            val lat = r.optDouble("latitude")
            val lon = r.optDouble("longitude")
            if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            Place(r.optString("name"), r.optString("country"), lat, lon)
        }
    }

    private fun airQuality(lat: Double, lon: Double): Double? {
        val root = get(
            String.format(Locale.US, "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=us_aqi&timezone=auto", lat, lon)
        ) ?: return null
        val cur = root.optJSONObject("current") ?: return null
        return if (cur.isNull("us_aqi")) null else cur.optDouble("us_aqi").takeIf { !it.isNaN() }
    }

    fun forecast(lat: Double, lon: Double): Conditions? {
        val root = get(
            String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f", lat, lon) +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,is_day,uv_index,pressure_msl,visibility" +
                "&hourly=temperature_2m,weather_code,is_day,precipitation,precipitation_probability,wind_speed_10m,uv_index,pressure_msl,visibility" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,sunrise,sunset,uv_index_max" +
                "&forecast_days=7&timezone=auto"
        ) ?: return null
        val cur = root.optJSONObject("current") ?: return null
        val h = root.optJSONObject("hourly") ?: return null
        val d = root.optJSONObject("daily") ?: return null
        return runCatching {
            val times = h.getJSONArray("time")
            val temps = h.optJSONArray("temperature_2m")
            val codes = h.optJSONArray("weather_code")
            val isDay = h.optJSONArray("is_day")
            val precip = h.optJSONArray("precipitation")
            val prob = h.optJSONArray("precipitation_probability")
            val wind = h.optJSONArray("wind_speed_10m")
            val uv = h.optJSONArray("uv_index")
            val pressure = h.optJSONArray("pressure_msl")
            val vis = h.optJSONArray("visibility")
            val hours = (0 until times.length()).map { i ->
                Hour(
                    LocalDateTime.parse(times.getString(i)), temps.num(i, 0.0), codes.num(i, 3.0).toInt(),
                    isDay.num(i, 1.0) > 0.5, precip.num(i, 0.0), prob.num(i, 0.0).toInt(),
                    wind.num(i, 0.0), uv.num(i, 0.0), pressure.num(i, 1013.0), vis.num(i, 10000.0)
                )
            }
            val dates = d.getJSONArray("time")
            val dMax = d.optJSONArray("temperature_2m_max")
            val dMin = d.optJSONArray("temperature_2m_min")
            val dCode = d.optJSONArray("weather_code")
            val dSum = d.optJSONArray("precipitation_sum")
            val dProb = d.optJSONArray("precipitation_probability_max")
            val dUv = d.optJSONArray("uv_index_max")
            val rise = d.getJSONArray("sunrise")
            val set = d.getJSONArray("sunset")
            val days = (0 until dates.length()).map { i ->
                Day(
                    LocalDate.parse(dates.getString(i)), dMax.num(i, 0.0), dMin.num(i, 0.0), dCode.num(i, 3.0).toInt(),
                    dSum.num(i, 0.0), dProb.num(i, 0.0).toInt(),
                    LocalDateTime.parse(rise.getString(i)), LocalDateTime.parse(set.getString(i)), dUv.num(i, 0.0)
                )
            }
            Conditions(
                LocalDateTime.parse(cur.getString("time")), cur.optDouble("temperature_2m"), cur.optDouble("apparent_temperature"),
                cur.optInt("relative_humidity_2m"), cur.optDouble("wind_speed_10m"), cur.optDouble("wind_direction_10m", 0.0),
                cur.optInt("weather_code"), cur.optInt("is_day", 1) == 1, cur.optDouble("uv_index", 0.0),
                airQuality(lat, lon), cur.optDouble("pressure_msl", 1013.0), cur.optDouble("visibility", 10000.0), hours, days
            )
        }.getOrNull()
    }
}

data class MoonPhase(val fraction: Double, val illumination: Double, val key: String)

fun moonPhase(date: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): MoonPhase {
    val synodic = 29.530588853
    val reference = LocalDateTime.of(2000, 1, 6, 18, 14).toEpochSecond(ZoneOffset.UTC) / 86400.0
    val days = date.toEpochSecond(ZoneOffset.UTC) / 86400.0 - reference
    val f = ((days / synodic) % 1.0 + 1.0) % 1.0
    val key = when ((f * 8).roundToInt() % 8) {
        0 -> "New moon"; 1 -> "Waxing crescent"; 2 -> "First quarter"; 3 -> "Waxing gibbous"
        4 -> "Full moon"; 5 -> "Waning gibbous"; 6 -> "Last quarter"; else -> "Waning crescent"
    }
    return MoonPhase(f, (1 - cos(2 * PI * f)) / 2, key)
}

fun formatTemp(celsius: Double, fahrenheit: Boolean): String =
    (if (fahrenheit) (celsius * 9 / 5 + 32).roundToInt() else celsius.roundToInt()).toString() + "°"

fun formatWind(kmh: Double, unit: WindUnit): String = "${(kmh * unit.factor).roundToInt()} ${unit.label}"

fun formatPressure(hpa: Double, unit: PressureUnit): String =
    String.format(Locale.US, "%.${unit.decimals}f %s", hpa * unit.factor, unit.label)

fun formatVisibility(meters: Double): String =
    if (meters >= 10_000) "10+ km" else String.format(Locale.US, "%.1f km", meters / 1000.0)

fun aqiLabel(v: Double): String = when {
    v <= 50 -> "Good"; v <= 100 -> "Moderate"; v <= 150 -> "Poor"; v <= 200 -> "Very poor"; else -> "Extreme"
}

fun uvLabel(v: Double): String = when {
    v < 3 -> "Low"; v < 6 -> "Moderate"; v < 8 -> "High"; v < 11 -> "Very high"; else -> "Extreme"
}

val FIXED_COLORS = listOf(
    Color(0xFF1E1E1E), Color(0xFF2F6F9F), Color(0xFFE0A100), Color(0xFF3E7D4F), Color(0xFFB3402F), Color(0xFF5B4B8A)
)

fun weatherColor(kind: Kind, day: Boolean): Color = when (kind) {
    Kind.Clear, Kind.FewClouds -> if (day) Color(0xFFE0A100) else Color(0xFF26335F)
    Kind.MoreClouds -> if (day) Color(0xFF5B8DB8) else Color(0xFF2F4058)
    Kind.Cloudy -> Color(0xFF6B7683)
    Kind.Fog -> Color(0xFF8A9199)
    Kind.Drizzle -> Color(0xFF4F86A6)
    Kind.Rain -> Color(0xFF2F6F9F)
    Kind.Storm -> Color(0xFF4B3F72)
    else -> Color(0xFF7F9BB3)
}

fun widgetColor(settings: DesktopSettings, c: Conditions?): Color =
    if (settings.colorMode == "fixed" || c == null) FIXED_COLORS[settings.fixedColor] else weatherColor(c.kind, c.isDay)

fun inkFor(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color(0xFF111111) else Color.White
}
