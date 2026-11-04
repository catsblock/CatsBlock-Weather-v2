package com.catsblock.weather.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object AlertFeed {

    private const val KEY = "alerts"
    private const val ZONE_BUDGET = 160

    private val cache = TimedCache<ByteArray>(10 * 60_000L)
    private val zones = ConcurrentHashMap<String, JSONObject>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    fun json(): ByteArray? {
        cache.get(KEY)?.let { return it }
        val features = JSONArray()
        val nws = collectNws(features)
        val inmet = collectInmet(features)
        if (!nws && !inmet) return cache.peek(KEY)
        val body = JSONObject().put("type", "FeatureCollection").put("features", features)
        return cache.put(KEY, body.toString().toByteArray(Charsets.UTF_8))
    }

    private fun rank(severity: String): Int = when (severity) {
        "Extreme" -> 3
        "Severe" -> 2
        else -> 1
    }

    private fun collectNws(out: JSONArray): Boolean {
        val root = GeoHttp.json(
            "https://api.weather.gov/alerts/active?status=actual&message_type=alert,update",
            "application/geo+json"
        ) ?: return false
        val items = root.optJSONArray("features") ?: return false
        val entries = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            .sortedByDescending { rank(it.optJSONObject("properties")?.str("severity").orEmpty()) }
        val needed = LinkedHashSet<String>()
        for (entry in entries) {
            if (!entry.isNull("geometry")) continue
            val list = entry.optJSONObject("properties")?.optJSONArray("affectedZones") ?: continue
            for (i in 0 until list.length()) if (needed.size < ZONE_BUDGET) needed.add(list.getString(i))
        }
        fetchZones(needed)
        for (entry in entries) nwsFeature(entry)?.let { out.put(it) }
        return true
    }

    private fun fetchZones(urls: Collection<String>) {
        val todo = urls.filter { !zones.containsKey(it) && !failed.contains(it) }
        if (todo.isEmpty()) return
        val pool = Executors.newFixedThreadPool(8)
        try {
            val jobs = todo.map { url ->
                pool.submit {
                    val geometry = GeoHttp.json(url, "application/geo+json")?.optJSONObject("geometry")
                    val compact = geometry?.let { GeoMath.compactGeometry(it, 3) }
                    if (compact != null) zones[url] = compact else failed.add(url)
                }
            }
            jobs.forEach { runCatching { it.get(60, TimeUnit.SECONDS) } }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun nwsFeature(entry: JSONObject): JSONObject? {
        val p = entry.optJSONObject("properties") ?: return null
        val geometry: JSONObject = if (!entry.isNull("geometry")) {
            entry.optJSONObject("geometry")?.let { GeoMath.compactGeometry(it, 3) } ?: return null
        } else {
            val list = p.optJSONArray("affectedZones") ?: return null
            val parts = ArrayList<JSONArray>()
            for (i in 0 until list.length()) zones[list.getString(i)]?.let { parts.addAll(GeoMath.polygons(it)) }
            if (parts.isEmpty()) return null
            GeoMath.multi(parts)
        }
        val severity = p.str("severity")
        val level = when (severity) {
            "Extreme" -> "danger"
            "Severe" -> "alert"
            else -> "warning"
        }
        val props = JSONObject()
            .put("id", p.str("id"))
            .put("event", p.str("event"))
            .put("level", level)
            .put("severity", severity)
            .put("urgency", p.str("urgency"))
            .put("certainty", p.str("certainty"))
            .put("sender", p.str("senderName"))
            .put("effective", p.str("onset").ifBlank { p.str("effective") })
            .put("expires", p.str("ends").ifBlank { p.str("expires") })
            .put("area", p.str("areaDesc"))
            .put("headline", p.str("headline"))
            .put("description", p.str("description").take(2500))
            .put("instruction", p.str("instruction").take(1200))
            .put("source", "NWS")
        return JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", props)
    }

    private fun collectInmet(out: JSONArray): Boolean {
        val raw = GeoHttp.text("https://apiprevmet3.inmet.gov.br/avisos/ativos") ?: return false
        val root = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: return false
        val items = ArrayList<JSONObject>()
        gather(root, items)
        for (item in items) inmetFeature(item)?.let { out.put(it) }
        return true
    }

    private fun gather(node: Any?, out: MutableList<JSONObject>) {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) gather(node.opt(i), out)
            is JSONObject -> {
                if (node.has("descricao") || node.has("severidade") || node.has("aviso_severidade")) out.add(node)
                else node.keys().forEach { gather(node.opt(it), out) }
            }
        }
    }

    private fun inmetFeature(item: JSONObject): JSONObject? {
        var geometry: JSONObject? = null
        for (key in listOf("geometry", "geojson", "poligono", "polygon", "wkt", "area")) {
            val value = item.opt(key)
            geometry = when (value) {
                is JSONObject -> value.takeIf { it.has("type") }
                is String -> GeoMath.fromText(value)
                else -> null
            }
            if (geometry != null) break
        }
        val shape = geometry?.let { GeoMath.compactGeometry(it, 3) } ?: return null
        val label = listOf("severidade", "aviso_severidade", "aviso_cor", "cor")
            .joinToString(" ") { item.str(it) }.lowercase()
        val level = when {
            label.contains("grande perigo") || label.contains("vermelho") -> "danger"
            label.contains("perigo potencial") || label.contains("amarelo") -> "warning"
            label.contains("perigo") || label.contains("laranja") -> "alert"
            else -> "warning"
        }
        fun stamp(date: String, hour: String, fallback: String): String =
            if (date.isNotBlank()) (date + " " + hour).trim() else fallback
        val props = JSONObject()
            .put("id", item.str("id"))
            .put("event", item.str("descricao"))
            .put("level", level)
            .put("severity", item.str("severidade"))
            .put("urgency", "")
            .put("certainty", "")
            .put("sender", "INMET")
            .put("effective", stamp(item.str("data_inicio"), item.str("hora_inicio"), item.str("inicio")))
            .put("expires", stamp(item.str("data_fim"), item.str("hora_fim"), item.str("fim")))
            .put("area", item.str("municipios").ifBlank { item.str("estados") })
            .put("headline", item.str("descricao"))
            .put("description", item.str("riscos").take(2500))
            .put("instruction", item.str("instrucoes").take(1200))
            .put("source", "INMET")
        return JSONObject().put("type", "Feature").put("geometry", shape).put("properties", props)
    }
}
