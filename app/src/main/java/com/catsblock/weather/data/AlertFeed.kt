package com.catsblock.weather.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object AlertFeed {

    private const val ENDPOINT =
        "https://services9.arcgis.com/RHVPKKiFTONKtxq3/ArcGIS/rest/services/CAP_Alerts_Feed/FeatureServer/0/query"

    private const val FIELDS =
        "identifier,event,headline,description,instruction,severity,urgency,certainty," +
            "senderName,orgName,areaDesc,sent,effective,onset,expires"

    private const val PAGE_SIZE = 1000
    private const val MAX_PAGES = 4

    private val cache = TimedCache<ByteArray>(10 * 60_000L)

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    fun json(): ByteArray? {
        val key = "live"
        cache.get(key)?.let { return it }
        val time = stamp.format(Instant.now())
        val where = "status = 'Actual' AND msgType <> 'Cancel' AND sent <= timestamp '$time' AND " +
            "(expires IS NULL OR expires >= timestamp '$time')"
        val features = JSONArray()
        val seen = HashSet<String>()
        for (page in 0 until MAX_PAGES) {
            val url = ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("f", "geojson")
                .addQueryParameter("where", where)
                .addQueryParameter("outFields", FIELDS)
                .addQueryParameter("returnGeometry", "true")
                .addQueryParameter("outSR", "4326")
                .addQueryParameter("maxAllowableOffset", "0.03")
                .addQueryParameter("geometryPrecision", "3")
                .addQueryParameter("orderByFields", "sent DESC")
                .addQueryParameter("resultOffset", (page * PAGE_SIZE).toString())
                .addQueryParameter("resultRecordCount", PAGE_SIZE.toString())
                .build()
            val items = GeoHttp.json(url.toString())?.optJSONArray("features")
            if (items == null) {
                if (page == 0) return cache.peek(key)
                break
            }
            for (i in 0 until items.length()) {
                feature(items.getJSONObject(i), seen)?.let { features.put(it) }
            }
            if (items.length() < PAGE_SIZE) break
        }
        val body = JSONObject().put("type", "FeatureCollection").put("features", features)
        return cache.put(key, body.toString().toByteArray(Charsets.UTF_8))
    }

    private fun millis(source: JSONObject, key: String): Long? {
        if (source.isNull(key)) return null
        return when (val value = source.opt(key)) {
            is Number -> value.toLong().takeIf { it > 0L }
            is String -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            else -> null
        }
    }

    private fun iso(millis: Long?): String = millis?.let { Instant.ofEpochMilli(it).toString() }.orEmpty()

    private fun feature(entry: JSONObject, seen: MutableSet<String>): JSONObject? {
        val p = entry.optJSONObject("properties") ?: return null
        val geometry = entry.optJSONObject("geometry")?.let { GeoMath.compactGeometry(it, 3) } ?: return null
        val id = p.str("identifier")
        if (id.isNotBlank() && !seen.add(id)) return null
        val severity = p.str("severity")
        val level = when (severity) {
            "Extreme" -> "danger"
            "Severe" -> "alert"
            else -> "warning"
        }
        val props = JSONObject()
            .put("id", id)
            .put("event", p.str("event"))
            .put("level", level)
            .put("severity", severity)
            .put("urgency", p.str("urgency"))
            .put("certainty", p.str("certainty"))
            .put("sender", p.str("senderName").ifBlank { p.str("orgName") })
            .put("effective", iso(millis(p, "onset") ?: millis(p, "effective") ?: millis(p, "sent")))
            .put("expires", iso(millis(p, "expires")))
            .put("area", p.str("areaDesc"))
            .put("headline", p.str("headline"))
            .put("description", p.str("description").take(2500))
            .put("instruction", p.str("instruction").take(1200))
            .put("source", "Alert-Hub.org")
        return JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", props)
    }
}
