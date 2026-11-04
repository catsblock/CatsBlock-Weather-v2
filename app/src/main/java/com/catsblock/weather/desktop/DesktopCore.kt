package com.catsblock.weather.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.Properties
import kotlin.math.roundToInt

val LANGUAGES = listOf(
    "en" to "English", "pt-BR" to "Português (BR)", "pt-PT" to "Português (PT)", "es" to "Español",
    "fr" to "Français", "ru" to "Русский", "de" to "Deutsch", "it" to "Italiano", "ko" to "한국어", "id" to "Bahasa Indonesia"
)

val uiLanguage = mutableStateOf("en")

private fun row(key: String, vararg values: String): Pair<String, List<String>> = key to values.toList()

private val STRINGS: Map<String, List<String>> = mapOf(
    row("Weather", "Clima", "Meteorologia", "Clima", "Météo", "Погода", "Wetter", "Meteo", "날씨", "Cuaca"),
    row("Settings", "Configurações", "Definições", "Ajustes", "Paramètres", "Настройки", "Einstellungen", "Impostazioni", "설정", "Pengaturan"),
    row("Widget", "Widget", "Widget", "Widget", "Widget", "Виджет", "Widget", "Widget", "위젯", "Widget"),
    row("Show floating widget", "Mostrar widget flutuante", "Mostrar widget flutuante", "Mostrar widget flotante", "Afficher le widget flottant", "Показывать плавающий виджет", "Schwebendes Widget anzeigen", "Mostra widget flottante", "플로팅 위젯 표시", "Tampilkan widget mengambang"),
    row("Widget color", "Cor do widget", "Cor do widget", "Color del widget", "Couleur du widget", "Цвет виджета", "Widget-Farbe", "Colore del widget", "위젯 색상", "Warna widget"),
    row("Follow the weather", "Seguir o clima", "Seguir o tempo", "Seguir el clima", "Suivre la météo", "По погоде", "Dem Wetter folgen", "Segui il meteo", "날씨에 따라 변경", "Ikuti cuaca"),
    row("Fixed color", "Cor fixa", "Cor fixa", "Color fijo", "Couleur fixe", "Фиксированный цвет", "Feste Farbe", "Colore fisso", "고정 색상", "Warna tetap"),
    row("Inactive opacity", "Opacidade inativa", "Opacidade inativa", "Opacidad inactiva", "Opacité inactive", "Прозрачность в покое", "Deckkraft im Ruhezustand", "Opacità inattiva", "비활성 투명도", "Opasitas nonaktif"),
    row("Return to inactive after", "Voltar a inativo após", "Voltar a inativo após", "Volver a inactivo tras", "Redevenir inactif après", "Становиться неактивным через", "Wieder inaktiv nach", "Torna inattivo dopo", "비활성 전환 시간", "Kembali nonaktif setelah"),
    row("Never", "Nunca", "Nunca", "Nunca", "Jamais", "Никогда", "Nie", "Mai", "안 함", "Tidak pernah"),
    row("Always on top", "Sempre no topo", "Sempre no topo", "Siempre visible", "Toujours au premier plan", "Поверх всех окон", "Immer im Vordergrund", "Sempre in primo piano", "항상 위에 표시", "Selalu di atas"),
    row("Temperature unit", "Unidade de temperatura", "Unidade de temperatura", "Unidad de temperatura", "Unité de température", "Единица температуры", "Temperatureinheit", "Unità di temperatura", "온도 단위", "Satuan suhu"),
    row("Celsius", "Celsius", "Celsius", "Celsius", "Celsius", "Цельсий", "Celsius", "Celsius", "섭씨", "Celsius"),
    row("Fahrenheit", "Fahrenheit", "Fahrenheit", "Fahrenheit", "Fahrenheit", "Фаренгейт", "Fahrenheit", "Fahrenheit", "화씨", "Fahrenheit"),
    row("Language", "Idioma", "Idioma", "Idioma", "Langue", "Язык", "Sprache", "Lingua", "언어", "Bahasa"),
    row("City", "Cidade", "Cidade", "Ciudad", "Ville", "Город", "Stadt", "Città", "도시", "Kota"),
    row("Search city", "Buscar cidade", "Pesquisar cidade", "Buscar ciudad", "Rechercher une ville", "Поиск города", "Stadt suchen", "Cerca città", "도시 검색", "Cari kota"),
    row("No results", "Sem resultados", "Sem resultados", "Sin resultados", "Aucun résultat", "Ничего не найдено", "Keine Ergebnisse", "Nessun risultato", "결과 없음", "Tidak ada hasil"),
    row("Choose a city to begin", "Escolha uma cidade para começar", "Escolha uma cidade para começar", "Elige una ciudad para empezar", "Choisissez une ville pour commencer", "Выберите город, чтобы начать", "Wähle eine Stadt, um zu beginnen", "Scegli una città per iniziare", "시작하려면 도시를 선택하세요", "Pilih kota untuk memulai"),
    row("Feels like", "Sensação térmica", "Sensação térmica", "Sensación térmica", "Ressenti", "Ощущается как", "Gefühlt", "Percepita", "체감", "Terasa seperti"),
    row("Humidity", "Umidade", "Humidade", "Humedad", "Humidité", "Влажность", "Luftfeuchtigkeit", "Umidità", "습도", "Kelembapan"),
    row("Wind", "Vento", "Vento", "Viento", "Vent", "Ветер", "Wind", "Vento", "바람", "Angin"),
    row("Hourly", "Por hora", "Por hora", "Por horas", "Par heure", "По часам", "Stündlich", "Orario", "시간별", "Per jam"),
    row("Daily", "Diário", "Diário", "Diario", "Par jour", "По дням", "Täglich", "Giornaliero", "일별", "Harian"),
    row("Loading…", "Carregando…", "A carregar…", "Cargando…", "Chargement…", "Загрузка…", "Wird geladen …", "Caricamento…", "불러오는 중…", "Memuat…"),
    row("Weather unavailable", "Clima indisponível", "Tempo indisponível", "Clima no disponible", "Météo indisponible", "Погода недоступна", "Wetter nicht verfügbar", "Meteo non disponibile", "날씨를 불러올 수 없음", "Cuaca tidak tersedia"),
    row("Retry", "Tentar novamente", "Tentar novamente", "Reintentar", "Réessayer", "Повторить", "Erneut versuchen", "Riprova", "다시 시도", "Coba lagi"),
    row("Open CatsBlock Weather", "Abrir CatsBlock Weather", "Abrir CatsBlock Weather", "Abrir CatsBlock Weather", "Ouvrir CatsBlock Weather", "Открыть CatsBlock Weather", "CatsBlock Weather öffnen", "Apri CatsBlock Weather", "CatsBlock Weather 열기", "Buka CatsBlock Weather"),
    row("Exit", "Sair", "Sair", "Salir", "Quitter", "Выход", "Beenden", "Esci", "종료", "Keluar"),
    row("Clear sky", "Céu limpo", "Céu limpo", "Cielo despejado", "Ciel dégagé", "Ясное небо", "Klarer Himmel", "Cielo sereno", "맑음", "Langit cerah"),
    row("Partly cloudy", "Parcialmente nublado", "Parcialmente nublado", "Parcialmente nublado", "Partiellement nuageux", "Переменная облачность", "Teilweise bewölkt", "Parzialmente nuvoloso", "구름 조금", "Berawan sebagian"),
    row("Cloudy", "Nublado", "Nublado", "Nublado", "Nuageux", "Облачно", "Bewölkt", "Nuvoloso", "흐림", "Berawan"),
    row("Fog", "Neblina", "Nevoeiro", "Niebla", "Brouillard", "Туман", "Nebel", "Nebbia", "안개", "Kabut"),
    row("Drizzle", "Garoa", "Chuvisco", "Llovizna", "Bruine", "Морось", "Nieselregen", "Pioviggine", "이슬비", "Gerimis"),
    row("Rain", "Chuva", "Chuva", "Lluvia", "Pluie", "Дождь", "Regen", "Pioggia", "비", "Hujan"),
    row("Snow", "Neve", "Neve", "Nieve", "Neige", "Снег", "Schnee", "Neve", "눈", "Salju"),
    row("Thunderstorm", "Tempestade", "Trovoada", "Tormenta", "Orage", "Гроза", "Gewitter", "Temporale", "뇌우", "Badai petir"),
    row("Today", "Hoje", "Hoje", "Hoy", "Aujourd'hui", "Сегодня", "Heute", "Oggi", "오늘", "Hari ini")
)

fun t(key: String): String {
    val index = LANGUAGES.indexOfFirst { it.first == uiLanguage.value }
    if (index <= 0) return key
    return STRINGS[key]?.getOrNull(index - 1)?.takeIf { it.isNotEmpty() } ?: key
}

fun uiLocale(): Locale = Locale.forLanguageTag(uiLanguage.value)

data class DesktopSettings(
    val language: String = defaultLanguage(),
    val fahrenheit: Boolean = false,
    val widgetVisible: Boolean = true,
    val colorMode: String = "weather",
    val fixedColor: Int = 0,
    val inactivePercent: Int = 25,
    val idleSeconds: Int = 5,
    val alwaysOnTop: Boolean = true,
    val city: String = "",
    val country: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0
) {
    val hasCity: Boolean get() = city.isNotBlank()
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

    private val file = File(System.getProperty("user.home"), ".catsblock-weather/settings.properties")

    fun load(): DesktopSettings {
        val d = DesktopSettings()
        if (!file.exists()) return d
        val p = Properties()
        runCatching { file.inputStream().use { p.load(it) } }
        fun s(k: String, def: String) = p.getProperty(k) ?: def
        return DesktopSettings(
            language = s("language", d.language).takeIf { code -> LANGUAGES.any { it.first == code } } ?: d.language,
            fahrenheit = s("fahrenheit", "false").toBoolean(),
            widgetVisible = s("widgetVisible", "true").toBoolean(),
            colorMode = s("colorMode", "weather"),
            fixedColor = s("fixedColor", "0").toIntOrNull()?.coerceIn(0, FIXED_COLORS.lastIndex) ?: 0,
            inactivePercent = s("inactivePercent", "25").toIntOrNull()?.coerceIn(10, 60) ?: 25,
            idleSeconds = s("idleSeconds", "5").toIntOrNull()?.coerceIn(0, 120) ?: 5,
            alwaysOnTop = s("alwaysOnTop", "true").toBoolean(),
            city = s("city", ""),
            country = s("country", ""),
            lat = s("lat", "0").toDoubleOrNull() ?: 0.0,
            lon = s("lon", "0").toDoubleOrNull() ?: 0.0
        )
    }

    fun save(v: DesktopSettings) {
        val p = Properties()
        p["language"] = v.language
        p["fahrenheit"] = v.fahrenheit.toString()
        p["widgetVisible"] = v.widgetVisible.toString()
        p["colorMode"] = v.colorMode
        p["fixedColor"] = v.fixedColor.toString()
        p["inactivePercent"] = v.inactivePercent.toString()
        p["idleSeconds"] = v.idleSeconds.toString()
        p["alwaysOnTop"] = v.alwaysOnTop.toString()
        p["city"] = v.city
        p["country"] = v.country
        p["lat"] = v.lat.toString()
        p["lon"] = v.lon.toString()
        runCatching {
            file.parentFile.mkdirs()
            file.outputStream().use { p.store(it, null) }
        }
    }
}

data class Place(val name: String, val country: String, val lat: Double, val lon: Double)
data class HourPoint(val label: String, val tempC: Double, val code: Int)
data class DayPoint(val label: String, val maxC: Double, val minC: Double, val code: Int)
data class Conditions(
    val tempC: Double, val feelsC: Double, val humidity: Int, val windKmh: Double,
    val code: Int, val day: Boolean, val hourly: List<HourPoint>, val daily: List<DayPoint>
)

object WeatherClient {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    private fun get(url: String): JSONObject? = runCatching {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "CatsBlockWeather-Desktop/2.0")
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() in 200..299) JSONObject(response.body()) else null
    }.getOrNull()

    fun search(query: String, language: String): List<Place> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val lang = language.substringBefore('-')
        val root = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=$lang") ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val r = results.optJSONObject(i) ?: return@mapNotNull null
            Place(r.optString("name"), r.optString("country"), r.optDouble("latitude"), r.optDouble("longitude"))
        }
    }

    fun forecast(lat: Double, lon: Double): Conditions? {
        val root = get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
                "&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&forecast_days=7&timezone=auto"
        ) ?: return null
        val cur = root.optJSONObject("current") ?: return null
        val hours = root.optJSONObject("hourly")
        val days = root.optJSONObject("daily")
        val now = cur.optString("time")
        val hourly = ArrayList<HourPoint>()
        if (hours != null) {
            val times = hours.optJSONArray("time")
            val temps = hours.optJSONArray("temperature_2m")
            val codes = hours.optJSONArray("weather_code")
            if (times != null && temps != null && codes != null) {
                val start = (0 until times.length()).firstOrNull { times.getString(it) >= now } ?: 0
                for (i in start until minOf(start + 12, times.length())) {
                    hourly.add(HourPoint(times.getString(i).substringAfter('T'), temps.optDouble(i), codes.optInt(i)))
                }
            }
        }
        val daily = ArrayList<DayPoint>()
        if (days != null) {
            val dates = days.optJSONArray("time")
            val max = days.optJSONArray("temperature_2m_max")
            val min = days.optJSONArray("temperature_2m_min")
            val codes = days.optJSONArray("weather_code")
            if (dates != null && max != null && min != null && codes != null) {
                for (i in 0 until dates.length()) {
                    val label = if (i == 0) t("Today") else
                        LocalDate.parse(dates.getString(i)).dayOfWeek.getDisplayName(TextStyle.SHORT, uiLocale())
                    daily.add(DayPoint(label, max.optDouble(i), min.optDouble(i), codes.optInt(i)))
                }
            }
        }
        return Conditions(
            cur.optDouble("temperature_2m"), cur.optDouble("apparent_temperature"),
            cur.optInt("relative_humidity_2m"), cur.optDouble("wind_speed_10m"),
            cur.optInt("weather_code"), cur.optInt("is_day", 1) == 1, hourly, daily
        )
    }
}

fun conditionKey(code: Int): String = when (code) {
    0, 1 -> "Clear sky"
    2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67, in 80..82 -> "Rain"
    in 71..77, 85, 86 -> "Snow"
    in 95..99 -> "Thunderstorm"
    else -> "Cloudy"
}

fun formatTemp(celsius: Double, fahrenheit: Boolean): String =
    (if (fahrenheit) (celsius * 9 / 5 + 32).roundToInt() else celsius.roundToInt()).toString() + "°"

val FIXED_COLORS = listOf(
    Color(0xFF1E1E1E), Color(0xFF2F6F9F), Color(0xFFE0A100), Color(0xFF3E7D4F), Color(0xFFB3402F), Color(0xFF5B4B8A)
)

fun weatherColor(code: Int, day: Boolean): Color = when (code) {
    0, 1 -> if (day) Color(0xFFE0A100) else Color(0xFF26335F)
    2 -> if (day) Color(0xFF5B8DB8) else Color(0xFF2F4058)
    3 -> Color(0xFF6B7683)
    45, 48 -> Color(0xFF8A9199)
    in 51..57 -> Color(0xFF4F86A6)
    in 61..67, in 80..82 -> Color(0xFF2F6F9F)
    in 71..77, 85, 86 -> Color(0xFF7F9BB3)
    in 95..99 -> Color(0xFF4B3F72)
    else -> Color(0xFF6B7683)
}

fun widgetColor(settings: DesktopSettings, conditions: Conditions?): Color =
    if (settings.colorMode == "fixed" || conditions == null) FIXED_COLORS[settings.fixedColor]
    else weatherColor(conditions.code, conditions.day)

fun inkFor(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color(0xFF111111) else Color.White
}
