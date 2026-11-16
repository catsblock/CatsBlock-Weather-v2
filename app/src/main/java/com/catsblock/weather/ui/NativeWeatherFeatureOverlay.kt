package com.catsblock.weather.ui

import android.content.Context
import android.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import com.catsblock.weather.data.AlertFeed
import com.catsblock.weather.data.DroughtFeed
import com.catsblock.weather.data.FireHotspots
import java.io.Closeable
import java.util.concurrent.TimeUnit

internal class NativeWeatherFeatureOverlay(
    private val style: Style,
    private val scope: CoroutineScope,
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
) : Closeable {
    private var job: Job? = null
    private var generation = 0L
    private var installed: String? = null
    private var closed = false

    fun show(layer: String?) {
        check(!closed)
        require(layer == null || layer in LAYERS)
        val token = ++generation
        job?.cancel()
        if (layer == null) { remove(); return }
        if (installed == layer && style.getSource(sourceId(layer)) != null) return
        remove()
        if (layer == "sst") { installSst(); return }
        job = scope.launch {
            try {
                val geoJson = withContext(Dispatchers.IO) {
                    when (layer) {
                        "cyclones" -> fetchEonet()
                        "fires" -> fireCollection(FireHotspots.json(context))
                        "alerts" -> AlertFeed.json()?.toString(Charsets.UTF_8)
                        "drought" -> DroughtFeed.json()?.toString(Charsets.UTF_8)
                        else -> null
                    }
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!closed && token == generation && geoJson != null) installFeatures(layer, geoJson)
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                if (!closed && token == generation) remove()
            }
        }
    }

    fun hide() = show(null)

    private fun fetchEonet(): String? {
        val request = Request.Builder().url("https://eonet.gsfc.nasa.gov/api/v3/events?status=open&limit=500&category=severeStorms").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val events = JSONObject(response.body?.string() ?: return null).optJSONArray("events") ?: return null
            val features = JSONArray()
            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val geometries = event.optJSONArray("geometry") ?: continue
                val props = JSONObject().put("title", event.optString("title")).put("id", event.optString("id")).put("category", "severeStorms")
                for (j in 0 until geometries.length()) {
                    val g = geometries.optJSONObject(j) ?: continue
                    val coords = g.optJSONArray("coordinates") ?: continue
                    if (coords.length() < 2) continue
                    val geometry = JSONObject().put("type", "Point").put("coordinates", JSONArray().put(coords.optDouble(0)).put(coords.optDouble(1)))
                    features.put(JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", props.put("date", g.optString("date"))))
                }
            }
            return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
        }
    }

    private fun fireCollection(bytes: ByteArray?): String? {
        if (bytes == null) return null
        val cells = JSONArray(bytes.toString(Charsets.UTF_8))
        val features = JSONArray()
        for (i in 0 until cells.length()) {
            val cell = cells.optJSONArray(i) ?: continue
            if (cell.length() < 3) continue
            val lat = cell.optDouble(0, Double.NaN); val lon = cell.optDouble(1, Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) continue
            val geom = JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lon + 0.125).put(lat + 0.125))
            val count = cell.optInt(2)
            val props = JSONObject().put("count", count).put("frp", cell.optDouble(3)).put("confidence", cell.optDouble(7))
                .put("radius", (3.0 + kotlin.math.sqrt(count.toDouble())).coerceIn(3.0, 10.0))
            features.put(JSONObject().put("type", "Feature").put("geometry", geom).put("properties", props))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

    private fun installFeatures(layer: String, data: String) {
        style.addSource(GeoJsonSource(sourceId(layer), data))
        when (layer) {
                "alerts" -> {
                    style.addLayer(FillLayer(layerId(layer), sourceId(layer)).withProperties(
                    fillColor(Expression.match(Expression.get("level"), Expression.literal("danger"), Expression.color(Color.parseColor("#d32f2f")), Expression.literal("alert"), Expression.color(Color.parseColor("#f57c00")), Expression.color(Color.parseColor("#fbc02d")))), fillOpacity(0.28f)))
                    style.addLayer(LineLayer(layerId(layer) + "-outline", sourceId(layer)).withProperties(
                    lineColor(Expression.match(Expression.get("level"), Expression.literal("danger"), Expression.color(Color.parseColor("#b71c1c")), Expression.literal("alert"), Expression.color(Color.parseColor("#e65100")), Expression.color(Color.parseColor("#f9a825")))), lineWidth(2f)))
                }
            "drought" -> style.addLayer(FillLayer(layerId(layer), sourceId(layer)).withProperties(
                fillColor(Expression.match(Expression.get("lvl"), Expression.literal(1), Expression.color(Color.parseColor("#fff176")), Expression.literal(2), Expression.color(Color.parseColor("#ffb74d")), Expression.literal(3), Expression.color(Color.parseColor("#ef6c00")), Expression.literal(4), Expression.color(Color.parseColor("#c62828")), Expression.color(Color.parseColor("#7f0000")))), fillOpacity(0.45f)))
            else -> style.addLayer(CircleLayer(layerId(layer), sourceId(layer)).withProperties(
                circleColor(if (layer == "fires") Expression.color(Color.parseColor("#ff5722")) else Expression.color(Color.parseColor("#1976d2"))),
                circleRadius(if (layer == "fires") Expression.get("radius") else Expression.literal(6.0)),
                org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor(Expression.color(Color.parseColor("#ffffff"))),
                org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(1f)))
        }
        installed = layer
    }

    private fun installSst() {
        val name = "GHRSST_L4_MUR_Sea_Surface_Temperature_Anomalies_v4.1_STD"
        val date = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(3).toString()
        val tiles = TileSet("2.2.0", "https://gibs.earthdata.nasa.gov/wmts/epsg3857/all/$name/default/$date/GoogleMapsCompatible_Level7/{z}/{y}/{x}.png").apply {
            this.name = name
            attribution = "NASA GIBS"
            minZoom = 0f
            maxZoom = 7f
        }
        style.addSource(RasterSource(sourceId("sst"), tiles, 256))
        style.addLayer(RasterLayer(layerId("sst"), sourceId("sst")).withProperties(org.maplibre.android.style.layers.PropertyFactory.rasterOpacity(0.78f)))
        installed = "sst"
    }

    private fun remove() {
        for (layer in LAYERS) {
            listOf(layerId(layer) + "-outline", layerId(layer)).forEach { if (style.getLayer(it) != null) style.removeLayer(it) }
            if (style.getSource(sourceId(layer)) != null) style.removeSource(sourceId(layer))
        }
        installed = null
    }

    override fun close() {
        if (closed) return
        closed = true
        generation++
        job?.cancel()
        job = null
        remove()
    }

    private fun sourceId(layer: String) = "native-feature-$layer-source"
    private fun layerId(layer: String) = "native-feature-$layer-layer"

    private companion object {
        val LAYERS = setOf("cyclones", "sst", "fires", "alerts", "drought")
    }
}
