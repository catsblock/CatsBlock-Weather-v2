package com.catsblock.weather.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.maps.Style
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

private fun isGeographicXyzTemplate(template: String): Boolean =
    template.startsWith("https://") &&
        template.contains("{z}") &&
        template.contains("{x}") &&
        template.contains("{y}") &&
        !template.contains("{s}")

internal enum class NativeWeatherOverlay(val sourceId: String, val layerId: String) {
    Precipitation("weather-precipitation-source", "weather-precipitation-layer"),
    Temperature("weather-temperature-source", "weather-temperature-layer"),
    Clouds("weather-clouds-source", "weather-clouds-layer"),
    Wind("weather-wind-source", "weather-wind-layer"),
    Pressure("weather-pressure-source", "weather-pressure-layer"),
    Humidity("weather-humidity-source", "weather-humidity-layer"),
    DewPoint("weather-dew-point-source", "weather-dew-point-layer"),
    Ultraviolet("weather-uv-source", "weather-uv-layer"),
    AirQuality("weather-air-quality-source", "weather-air-quality-layer"),
    Sea("weather-sea-source", "weather-sea-layer"),
    Cyclones("weather-cyclones-source", "weather-cyclones-layer"),
    Alerts("weather-alerts-source", "weather-alerts-layer"),
    Fires("weather-fires-source", "weather-fires-layer"),
    Drought("weather-drought-source", "weather-drought-layer"),
    Enso("weather-enso-source", "weather-enso-layer")
}

internal data class NativeWeatherTileSource(
    val template: String,
    val attribution: String,
    val minZoom: Int = 0,
    val maxZoom: Int = 18,
    val opacity: Float = 0.78f
) {
    init {
        require(isGeographicXyzTemplate(template))
        require(attribution.isNotBlank())
        require(minZoom >= 0 && maxZoom >= minZoom)
        require(opacity in 0f..1f)
    }
}

internal data class NativeRainViewerFrame(
    val timestampSeconds: Long,
    val tileTemplate: String,
    val observed: Boolean
)

internal class NativeWeatherOverlayManager(
    private val style: Style,
    private val scope: CoroutineScope
) : Closeable {
    private val installed = ConcurrentHashMap<NativeWeatherOverlay, NativeWeatherTileSource>()
    private var animation: Job? = null
    private var closed = false

    fun setOverlay(overlay: NativeWeatherOverlay, source: NativeWeatherTileSource?) {
        check(!closed)
        if (overlay == NativeWeatherOverlay.Precipitation) stopPrecipitationAnimation()
        if (source == null) {
            remove(overlay)
            return
        }
        put(overlay, source)
    }

    fun animateRainViewer(frames: List<NativeRainViewerFrame>, intervalMillis: Long = 700L) {
        check(!closed)
        require(intervalMillis in 250L..10_000L)
        val validFrames = frames
            .filter { it.timestampSeconds > 0L && isGeographicXyzTemplate(it.tileTemplate) }
            .distinctBy { it.timestampSeconds }
            .sortedBy { it.timestampSeconds }
        stopPrecipitationAnimation()
        if (validFrames.isEmpty()) {
            remove(NativeWeatherOverlay.Precipitation)
            return
        }
        val frameSources = validFrames.map {
            NativeWeatherTileSource(
                template = it.tileTemplate,
                attribution = "RainViewer",
                minZoom = 0,
                maxZoom = 7,
                opacity = if (it.observed) 0.82f else 0.68f
            )
        }
        if (frameSources.size == 1) {
            put(NativeWeatherOverlay.Precipitation, frameSources.single())
            return
        }
        animation = scope.launch {
            var index = 0
            while (isActive && !closed) {
                put(NativeWeatherOverlay.Precipitation, frameSources[index])
                index = (index + 1) % frameSources.size
                delay(intervalMillis)
            }
        }
    }

    fun stopPrecipitationAnimation() {
        animation?.cancel()
        animation = null
    }

    fun clear() {
        NativeWeatherOverlay.entries.forEach(::remove)
    }

    override fun close() {
        if (closed) return
        stopPrecipitationAnimation()
        clear()
        closed = true
    }

    private fun put(overlay: NativeWeatherOverlay, source: NativeWeatherTileSource) {
        if (installed[overlay] == source && style.getSource(overlay.sourceId) != null && style.getLayer(overlay.layerId) != null) return
        remove(overlay)
        val tileSet = TileSet("2.2.0", source.template).apply {
            name = overlay.name
            attribution = source.attribution
            minZoom = source.minZoom.toFloat()
            maxZoom = source.maxZoom.toFloat()
        }
        style.addSource(RasterSource(overlay.sourceId, tileSet, 256))
        style.addLayer(
            RasterLayer(overlay.layerId, overlay.sourceId).withProperties(
                PropertyFactory.rasterOpacity(source.opacity),
                PropertyFactory.rasterFadeDuration(200f)
            )
        )
        installed[overlay] = source
    }

    private fun remove(overlay: NativeWeatherOverlay) {
        if (style.getLayer(overlay.layerId) != null) style.removeLayer(overlay.layerId)
        if (style.getSource(overlay.sourceId) != null) style.removeSource(overlay.sourceId)
        installed.remove(overlay)
    }

}
