package com.catsblock.weather.data

import org.json.JSONArray
import org.json.JSONObject

object DroughtFeed {

    private const val KEY = "usdm"

    private val cache = TimedCache<ByteArray>(6 * 3600_000L)

    fun json(): ByteArray? {
        cache.get(KEY)?.let { return it }
        val root = GeoHttp.json("https://droughtmonitor.unl.edu/data/json/usdm_current.json") ?: return cache.peek(KEY)
        val features = root.optJSONArray("features") ?: return cache.peek(KEY)
        val out = JSONArray()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val props = feature.optJSONObject("properties") ?: continue
            val dm = props.optInt("DM", props.optInt("dm", -1))
            if (dm < 0) continue
            val geometry = feature.optJSONObject("geometry")?.let { GeoMath.compactGeometry(it, 2) } ?: continue
            out.put(
                JSONObject().put("type", "Feature").put("geometry", geometry)
                    .put("properties", JSONObject().put("lvl", minOf(4, dm + 1)))
            )
        }
        if (out.length() == 0) return cache.peek(KEY)
        val body = JSONObject().put("type", "FeatureCollection").put("features", out)
        return cache.put(KEY, body.toString().toByteArray(Charsets.UTF_8))
    }
}
