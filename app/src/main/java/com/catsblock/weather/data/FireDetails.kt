package com.catsblock.weather.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToLong

object FireDetails {

    private const val KEY = "incidents"
    private const val ACRE_HA = 0.404686
    private const val HOTSPOT_HA = 20.0

    private val cache = TimedCache<List<Incident>>(30 * 60_000L)

    private class Incident(
        val lat: Double,
        val lon: Double,
        val name: String,
        val discovered: Long?,
        val reported: Long?,
        val updated: Long?,
        val hectares: Double?,
        val cost: Double?,
        val contained: Double?,
        val cause: String,
        val fuel: String,
        val behavior: String
    )

    class Query(
        val lat: Double,
        val lon: Double,
        val size: Double,
        val hotspots: Int,
        val frp: Double,
        val firstMinute: Long,
        val lastMinute: Long,
        val brightness: Double,
        val confidence: Double,
        val night: Int
    )

    private fun stamp(o: JSONObject, vararg keys: String): Long? {
        for (key in keys) {
            if (o.isNull(key)) continue
            val value = o.opt(key)
            if (value is Number && value.toLong() > 0L) return value.toLong()
            if (value is String) runCatching { return Instant.parse(value).toEpochMilli() }
        }
        return null
    }

    private fun number(o: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (o.isNull(key)) continue
            val v = o.optDouble(key, Double.NaN)
            if (!v.isNaN()) return v
        }
        return null
    }

    private fun incidents(): List<Incident> {
        cache.get(KEY)?.let { return it }
        val where = URLEncoder.encode("IncidentTypeCategory='WF'", "UTF-8")
        val url = "https://services3.arcgis.com/T4QMspbfLg3qTGWY/arcgis/rest/services/" +
            "WFIGS_Incident_Locations_Current/FeatureServer/0/query?where=$where&outFields=*&returnGeometry=true&outSR=4326&f=geojson"
        val root = GeoHttp.json(url, "application/geo+json") ?: return cache.peek(KEY).orEmpty()
        val features = root.optJSONArray("features") ?: return cache.peek(KEY).orEmpty()
        val list = ArrayList<Incident>()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val p = f.optJSONObject("properties") ?: continue
            val acres = number(p, "IncidentSize", "DailyAcres", "FinalAcres")
            list.add(
                Incident(
                    lat = coords.optDouble(1), lon = coords.optDouble(0),
                    name = p.str("IncidentName"),
                    discovered = stamp(p, "FireDiscoveryDateTime"),
                    reported = stamp(p, "IncidentReportedDateTime", "CreatedOnDateTime", "FireDiscoveryDateTime"),
                    updated = stamp(p, "ModifiedOnDateTime", "ModifiedOnDateTime_dt"),
                    hectares = acres?.takeIf { it > 0 }?.times(ACRE_HA),
                    cost = number(p, "EstimatedCostToDate")?.takeIf { it > 0 },
                    contained = number(p, "PercentContained"),
                    cause = p.str("FireCause").ifBlank { p.str("FireCauseGeneral") },
                    fuel = p.str("PrimaryFuelModel"),
                    behavior = p.str("FireBehaviorGeneral")
                )
            )
        }
        return cache.put(KEY, list)
    }

    private fun fuelFor(lat: Double, lon: Double): String {
        val a = abs(lat)
        return when {
            a >= 52 -> "boreal"
            lat in -10.0..5.0 && lon in -75.0..-50.0 -> "tropical"
            lat in -24.0..-8.0 && lon in -60.0..-41.0 -> "savanna"
            lat in -12.0..8.0 && lon in 95.0..155.0 -> "tropical"
            lat in -35.0..-10.0 && lon in 112.0..155.0 -> "shrub"
            lat in -20.0..14.0 && lon in -20.0..52.0 -> "savanna"
            lat in 23.0..50.0 && lon in -105.0..-95.0 -> "grass"
            lat in 23.0..52.0 -> "temperate"
            a <= 23.0 -> "savanna"
            else -> "temperate"
        }
    }

    private fun behaviorFor(frp: Double): String = when {
        frp < 15 -> "smoldering"
        frp < 50 -> "creeping"
        frp < 150 -> "active"
        frp < 400 -> "intense"
        else -> "extreme"
    }

    private fun behaviorFromText(text: String): String? {
        val t = text.lowercase()
        return when {
            t.contains("extreme") -> "extreme"
            t.contains("active") || t.contains("crown") -> "active"
            t.contains("moderate") -> "creeping"
            t.contains("minimal") || t.contains("smolder") -> "smoldering"
            else -> null
        }
    }

    private fun causeKey(text: String): String = when {
        text.contains("human", true) -> "human"
        text.contains("natural", true) || text.contains("lightning", true) -> "natural"
        text.contains("undetermined", true) -> "undetermined"
        else -> "investigating"
    }

    private fun iso(ms: Long?): String = if (ms == null || ms <= 0) "" else Instant.ofEpochMilli(ms).toString()

    fun build(q: Query): JSONObject {
        val south = q.lat
        val west = q.lon
        val official = incidents()
            .filter { it.lat in south..(south + q.size) && it.lon in west..(west + q.size) }
            .maxByOrNull { it.hectares ?: 0.0 }
        val centerLat = q.lat + q.size / 2
        val centerLon = q.lon + q.size / 2
        val fuelKey = fuelFor(centerLat, centerLon)
        val modeled = JSONArray()
        val hectares = official?.hectares ?: (q.hotspots * HOTSPOT_HA).also { modeled.put("area") }
        val rate = when (fuelKey) {
            "boreal", "temperate", "tropical" -> 2400.0
            "shrub" -> 900.0
            else -> 450.0
        }
        val cost = official?.cost ?: (hectares * rate).roundToLong().toDouble().also { modeled.put("cost") }
        val fuelText = official?.fuel.orEmpty()
        if (fuelText.isBlank()) modeled.put("fuel")
        val contained = official?.contained
        val status = when {
            contained != null && contained >= 100 -> "controlled"
            contained != null && contained >= 50 -> "contained"
            else -> "active"
        }
        val cause = official?.cause?.takeIf { it.isNotBlank() }?.let(::causeKey) ?: "investigating"
        val behavior = official?.behavior?.let(::behaviorFromText) ?: behaviorFor(q.frp)
        val firstMs = q.firstMinute.takeIf { it > 0 }?.times(60_000L)
        val lastMs = q.lastMinute.takeIf { it > 0 }?.times(60_000L)
        val out = JSONObject()
            .put("lat", centerLat).put("lon", centerLon)
            .put("name", official?.name.orEmpty())
            .put("official", official != null)
            .put("detected", iso(official?.discovered ?: firstMs))
            .put("reported", iso(official?.reported ?: FireHotspots.fetchedAt()))
            .put("updated", iso(official?.updated ?: lastMs))
            .put("costUsd", cost)
            .put("areaHa", GeoMath.round(hectares, 1))
            .put("status", status)
            .put("cause", cause)
            .put("behavior", behavior)
            .put("frp", q.frp)
            .put("brightness", q.brightness)
            .put("hotspots", q.hotspots)
            .put("confidence", q.confidence)
            .put("night", q.night > 0)
            .put("fuelKey", fuelKey)
            .put("fuelText", fuelText)
            .put("modeled", modeled)
        if (contained != null) out.put("containment", contained)
        return out
    }
}
