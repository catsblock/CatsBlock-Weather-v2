package com.catsblock.weather.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.uiStore by preferencesDataStore(name = "ui_prefs")

enum class CardTheme(val label: String) {
    Glassmorphism("Glassmorphism"),
    FrostedGlass("Frosted Glass"),
    Neumorphism("Neumorphism"),
    Claymorphism("Claymorphism"),
    FlatDesign("Flat Design"),
    Skeuomorphism("Skeuomorphism"),
    CleanUi("Clean Ui"),
    Glowmorphism("Glowmorphism"),
    MaterialYou("Material You"),
    FluentDesign("Fluent Design"),
    Aqua("Aqua"),
    FrutigerAero("Frutiger Aero")
}

enum class CardShape(val label: String, val ratio: Float, val span: Int) {
    Horizontal("Horizontal", 1.6f, 2),
    Vertical("Vertical", 0.78f, 1),
    Square("Square", 1f, 1)
}

enum class CardId(val title: String, val defaultShape: CardShape) {
    Radar("Rain radar", CardShape.Horizontal),
    Precip("Precipitation", CardShape.Square),
    Wind("Wind", CardShape.Square),
    Air("Air quality", CardShape.Square),
    Uv("UV index", CardShape.Square),
    Humidity("Humidity", CardShape.Square),
    Sun("Sun", CardShape.Square),
    Pressure("Pressure", CardShape.Square),
    Visibility("Visibility", CardShape.Square),
    Moon("Moon", CardShape.Horizontal)
}

enum class FontPreset(val label: String, val device: String?) {
    System("System", null),
    Light("Light", "sans-serif-light"),
    Medium("Medium", "sans-serif-medium"),
    Condensed("Condensed", "sans-serif-condensed"),
    Serif("Serif", "serif"),
    Mono("Monospace", "monospace"),
    Casual("Casual", "casual"),
    Cursive("Cursive", "cursive"),
    Custom("Custom file", null)
}

enum class ApiSource(val label: String, val model: String?) {
    OpenMeteo("Open-Meteo (default)", null),
    Ecmwf("Open-Meteo · ECMWF", "ecmwf_ifs025"),
    Gfs("Open-Meteo · NOAA GFS", "gfs_seamless"),
    Icon("Open-Meteo · DWD ICON", "icon_seamless"),
    MeteoFrance("Open-Meteo · Météo-France", "meteofrance_seamless"),
    Gem("Open-Meteo · GEM Canada", "gem_seamless"),
    Jma("Open-Meteo · JMA Japan", "jma_seamless")
}

object ApiSources {
    @Volatile
    var current: ApiSource = ApiSource.OpenMeteo

    fun extra(): Array<Pair<String, String>> =
        current.model?.let { arrayOf("models" to it) } ?: emptyArray()
}

data class CardStyle(
    val theme: CardTheme? = null,
    val shape: CardShape? = null,
    val c1: Long? = null,
    val c2: Long? = null
)

data class UiPrefs(
    val defaultTheme: CardTheme = CardTheme.Glassmorphism,
    val order: List<CardId> = CardId.entries.toList(),
    val styles: Map<CardId, CardStyle> = emptyMap(),
    val font: FontPreset = FontPreset.System,
    val customName: String? = null,
    val customPath: String? = null,
    val api: ApiSource = ApiSource.OpenMeteo,
    val edit: Boolean = false
) {
    fun shapeOf(id: CardId): CardShape = styles[id]?.shape ?: id.defaultShape

    fun move(id: CardId, dir: Int, visible: Set<CardId>): UiPrefs {
        val vis = order.filter { it in visible }
        val i = vis.indexOf(id)
        val j = i + dir
        if (i < 0 || j !in vis.indices) return this
        val a = order.indexOf(id)
        val b = order.indexOf(vis[j])
        val m = order.toMutableList()
        m[a] = vis[j]
        m[b] = id
        return copy(order = m)
    }

    fun toJson(): String {
        val o = JSONObject()
        o.put("theme", defaultTheme.name)
        o.put("order", JSONArray(order.map { it.name }))
        val s = JSONObject()
        styles.forEach { (id, st) ->
            s.put(
                id.name,
                JSONObject().apply {
                    st.theme?.let { put("theme", it.name) }
                    st.shape?.let { put("shape", it.name) }
                    st.c1?.let { put("c1", it) }
                    st.c2?.let { put("c2", it) }
                }
            )
        }
        o.put("styles", s)
        o.put("font", font.name)
        customName?.let { o.put("customName", it) }
        customPath?.let { o.put("customPath", it) }
        o.put("api", api.name)
        o.put("edit", edit)
        return o.toString()
    }

    companion object {
        fun parse(raw: String): UiPrefs {
            val o = JSONObject(raw)
            val order = o.optJSONArray("order")?.let { a ->
                (0 until a.length()).mapNotNull { i -> runCatching { CardId.valueOf(a.getString(i)) }.getOrNull() }
            } ?: emptyList()
            val full = order.distinct() + CardId.entries.filter { it !in order }
            val styles = HashMap<CardId, CardStyle>()
            o.optJSONObject("styles")?.let { s ->
                s.keys().forEach { k ->
                    val id = runCatching { CardId.valueOf(k) }.getOrNull() ?: return@forEach
                    val v = s.getJSONObject(k)
                    styles[id] = CardStyle(
                        theme = v.optString("theme").takeIf { it.isNotEmpty() }?.let { runCatching { CardTheme.valueOf(it) }.getOrNull() },
                        shape = v.optString("shape").takeIf { it.isNotEmpty() }?.let { runCatching { CardShape.valueOf(it) }.getOrNull() },
                        c1 = if (v.has("c1")) v.getLong("c1") else null,
                        c2 = if (v.has("c2")) v.getLong("c2") else null
                    )
                }
            }
            return UiPrefs(
                defaultTheme = runCatching { CardTheme.valueOf(o.optString("theme")) }.getOrDefault(CardTheme.Glassmorphism),
                order = full,
                styles = styles,
                font = runCatching { FontPreset.valueOf(o.optString("font")) }.getOrDefault(FontPreset.System),
                customName = o.optString("customName").takeIf { it.isNotEmpty() },
                customPath = o.optString("customPath").takeIf { it.isNotEmpty() },
                api = runCatching { ApiSource.valueOf(o.optString("api")) }.getOrDefault(ApiSource.OpenMeteo),
                edit = o.optBoolean("edit", false)
            )
        }
    }
}

class UiPrefsStore(private val context: Context) {
    private val key = stringPreferencesKey("ui_json")

    val flow: Flow<UiPrefs> = context.uiStore.data.map { p ->
        p[key]?.let { runCatching { UiPrefs.parse(it) }.getOrNull() } ?: UiPrefs()
    }

    suspend fun update(block: (UiPrefs) -> UiPrefs) {
        context.uiStore.edit { p ->
            val cur = p[key]?.let { runCatching { UiPrefs.parse(it) }.getOrNull() } ?: UiPrefs()
            p[key] = block(cur).toJson()
        }
    }
}
