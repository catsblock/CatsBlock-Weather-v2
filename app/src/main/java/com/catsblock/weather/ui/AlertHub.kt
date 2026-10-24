package com.catsblock.weather.data

import com.catsblock.weather.i18n.I18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class HubAlert(
    val id: String,
    val event: String,
    val headline: String,
    val description: String,
    val instruction: String,
    val severity: String,
    val urgency: String,
    val certainty: String,
    val sender: String,
    val area: String,
    val language: String,
    val sent: Long?,
    val effective: Long?,
    val expires: Long?,
    val web: String
) {
    val title: String get() = headline.ifBlank { event.ifBlank { "Weather alert" } }

    val rank: Int
        get() = when (severity) {
            "Extreme" -> 4
            "Severe" -> 3
            "Moderate" -> 2
            "Minor" -> 1
            else -> 0
        }
}

object AlertHub {

    private const val ENDPOINT =
        "https://services9.arcgis.com/RHVPKKiFTONKtxq3/ArcGIS/rest/services/CAP_Alerts_Feed/FeatureServer/0/query"

    private const val FIELDS =
        "identifier,event,headline,description,instruction,severity,urgency,certainty," +
            "senderName,orgName,areaDesc,language,sent,effective,onset,expires,web"

    private const val TTL_MS = 300_000L

    private class Entry(val at: Long, val alerts: List<HubAlert>)

    private val cache = ConcurrentHashMap<String, Entry>()

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun JSONObject.text(key: String): String =
        if (isNull(key)) "" else optString(key).trim()

    private fun JSONObject.time(key: String): Long? =
        if (isNull(key)) null else optLong(key).takeIf { it > 0L }

    suspend fun fetch(lat: Double, lon: Double): List<HubAlert> = withContext(Dispatchers.IO) {
        val key = String.format(Locale.US, "%.2f,%.2f|%s", lat, lon, I18n.lang.code)
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.at < TTL_MS }?.let { return@withContext it.alerts }

        val where = "msgType <> 'Cancel' AND status = 'Actual' AND " +
            "(expires IS NULL OR expires >= timestamp '${stamp.format(Instant.ofEpochMilli(now))}')"
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("f", "json")
            .addQueryParameter("where", where)
            .addQueryParameter("geometry", String.format(Locale.US, "%.5f,%.5f", lon, lat))
            .addQueryParameter("geometryType", "esriGeometryPoint")
            .addQueryParameter("inSR", "4326")
            .addQueryParameter("spatialRel", "esriSpatialRelIntersects")
            .addQueryParameter("outFields", FIELDS)
            .addQueryParameter("returnGeometry", "false")
            .addQueryParameter("orderByFields", "sent DESC")
            .addQueryParameter("resultRecordCount", "100")
            .build()

        val raw = client.newCall(
            Request.Builder().url(url).header("User-Agent", "CatsBlockWeather/2.0").build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }

        val root = JSONObject(raw)
        if (root.has("error")) throw IOException(root.optJSONObject("error")?.optString("message") ?: "Alert Hub error")
        val features = root.optJSONArray("features")

        val parsed = ArrayList<HubAlert>()
        if (features != null) {
            for (i in 0 until features.length()) {
                val a = features.getJSONObject(i).optJSONObject("attributes") ?: continue
                val sender = a.text("senderName").ifBlank { a.text("orgName") }
                parsed += HubAlert(
                    id = a.text("identifier"),
                    event = a.text("event"),
                    headline = a.text("headline"),
                    description = a.text("description"),
                    instruction = a.text("instruction"),
                    severity = a.text("severity"),
                    urgency = a.text("urgency"),
                    certainty = a.text("certainty"),
                    sender = sender,
                    area = a.text("areaDesc"),
                    language = a.text("language"),
                    sent = a.time("sent"),
                    effective = a.time("effective") ?: a.time("onset") ?: a.time("sent"),
                    expires = a.time("expires"),
                    web = a.text("web")
                )
            }
        }

        val preferred = I18n.lang.code.substringBefore('-').lowercase()
        val alerts = parsed
            .groupBy { it.id }
            .values
            .map { group ->
                group.firstOrNull { it.language.lowercase().startsWith(preferred) }
                    ?: group.firstOrNull { it.language.lowercase().startsWith("en") }
                    ?: group.first()
            }
            .distinctBy { "${it.sender}|${it.event}|${it.headline}" }
            .sortedWith(compareByDescending<HubAlert> { it.rank }.thenByDescending { it.sent ?: 0L })

        cache[key] = Entry(now, alerts)
        alerts
    }
}
