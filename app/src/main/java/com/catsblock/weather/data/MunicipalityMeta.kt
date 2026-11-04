package com.catsblock.weather.data

import com.catsblock.weather.i18n.I18n
import org.json.JSONObject
import java.util.Locale

object MunicipalityMeta {

    private val cache = TimedCache<JSONObject>(24 * 3600_000L)
    private val gate = Any()
    private var lastCall = 0L

    private fun nominatim(lat: Double, lon: Double, polygon: Boolean): JSONObject? {
        synchronized(gate) {
            val wait = 1100L - (System.currentTimeMillis() - lastCall)
            if (wait > 0) Thread.sleep(wait)
            lastCall = System.currentTimeMillis()
        }
        val shape = if (polygon) "&polygon_geojson=1&polygon_threshold=0.01" else ""
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&addressdetails=1&extratags=1" +
            "&lat=$lat&lon=$lon&accept-language=${I18n.lang.locale.language}$shape"
        return GeoHttp.json(url)
    }

    internal fun resolve(lat: Double, lon: Double, polygon: Boolean): JSONObject? {
        val key = String.format(Locale.US, "%.2f:%.2f:%b:%s", lat, lon, polygon, I18n.lang.code)
        cache.get(key)?.let { return it }
        val raw = nominatim(lat, lon, polygon) ?: return cache.peek(key)
        val address = raw.optJSONObject("address") ?: JSONObject()
        val name = listOf("city", "town", "municipality", "village", "suburb", "county")
            .firstNotNullOfOrNull { address.str(it).takeIf(String::isNotBlank) }
            ?: raw.str("name").takeIf { it.isNotBlank() }
            ?: return null
        val region = listOf("state", "region", "state_district", "province")
            .firstNotNullOfOrNull { address.str(it).takeIf(String::isNotBlank) }.orEmpty()
        val geometry = raw.optJSONObject("geojson")
        val polygonal = geometry?.optString("type") in setOf("Polygon", "MultiPolygon")
        var area = if (polygonal) GeoMath.areaKm2(geometry) else 0.0
        val box = raw.optJSONArray("boundingbox")
        if (area <= 0.0 && box != null && box.length() == 4) {
            val s = box.optString(0).toDoubleOrNull()
            val n = box.optString(1).toDoubleOrNull()
            val w = box.optString(2).toDoubleOrNull()
            val e = box.optString(3).toDoubleOrNull()
            if (s != null && n != null && w != null && e != null) {
                val km = 111.32
                area = (n - s) * km * (e - w) * km * Math.cos(Math.toRadians((n + s) / 2)) * 0.6
            }
        }
        val population = raw.optJSONObject("extratags")?.str("population")
            ?.filter { it.isDigit() }?.toLongOrNull()
        val out = JSONObject()
            .put("name", name)
            .put("region", region)
            .put("country", address.str("country"))
            .put("countryCode", address.str("country_code").uppercase())
            .put("areaKm2", GeoMath.round(area, 1))
            .put("lat", raw.str("lat").toDoubleOrNull() ?: lat)
            .put("lon", raw.str("lon").toDoubleOrNull() ?: lon)
        if (population != null && population > 0) out.put("population", population)
        if (polygon && polygonal && geometry != null) {
            GeoMath.compactGeometry(geometry, 3)?.let { out.put("geometry", it) }
        }
        return cache.put(key, out)
    }

    fun details(lat: Double, lon: Double): JSONObject {
        val out = resolve(lat, lon, false)?.let { JSONObject(it.toString()) } ?: JSONObject()
        out.put("queryLat", lat).put("queryLon", lon)
        GeoHttp.json("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m&timezone=auto")
            ?.let {
                if (!it.isNull("elevation")) out.put("elevation", it.optDouble("elevation"))
                out.put("timezone", it.str("timezone"))
            }
        return out
    }
}
