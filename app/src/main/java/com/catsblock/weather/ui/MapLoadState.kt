package com.catsblock.weather.ui

internal enum class MapLoadPhase {
    IDLE,
    INITIALIZING,
    LOADING_BASE_MAP,
    LOADING_WEATHER_DATA,
    LOADING_LAYERS,
    READY,
    PARTIALLY_READY,
    ERROR
}

internal enum class MapFailure {
    SCRIPT,
    BOOT,
    BASE_TILES,
    RENDERER,
    BRIDGE,
    TIMEOUT
}

internal enum class LayerLoadState {
    NONE,
    LOADING,
    ACTIVE,
    FAILED
}

internal data class MapLoadState(
    val started: Boolean = false,
    val pageReady: Boolean = false,
    val baseReady: Boolean = false,
    val failure: MapFailure? = null,
    val layer: LayerLoadState = LayerLoadState.NONE,
    val layerNeedsWeatherData: Boolean = false
) {
    val phase: MapLoadPhase
        get() = when {
            !started -> MapLoadPhase.IDLE
            !baseReady && failure != null -> MapLoadPhase.ERROR
            !baseReady -> if (pageReady) MapLoadPhase.LOADING_BASE_MAP else MapLoadPhase.INITIALIZING
            layer == LayerLoadState.LOADING ->
                if (layerNeedsWeatherData) MapLoadPhase.LOADING_WEATHER_DATA else MapLoadPhase.LOADING_LAYERS
            layer == LayerLoadState.FAILED -> MapLoadPhase.PARTIALLY_READY
            else -> MapLoadPhase.READY
        }

    val finishedBaseLoading: Boolean
        get() = phase != MapLoadPhase.IDLE && phase != MapLoadPhase.INITIALIZING && phase != MapLoadPhase.LOADING_BASE_MAP

    fun start(): MapLoadState = copy(started = true, pageReady = false, baseReady = false, failure = null)

    fun restartBase(): MapLoadState = copy(started = true, baseReady = false, failure = null)

    fun stop(): MapLoadState = MapLoadState()

    fun onPageReady(): MapLoadState = if (!started || pageReady) this else copy(pageReady = true)

    fun onBaseReady(): MapLoadState = if (!started || baseReady) this else copy(pageReady = true, baseReady = true, failure = null)

    fun onBaseFailed(reason: MapFailure): MapLoadState = if (!started || baseReady) this else copy(failure = reason)

    fun onLayerSelected(layerId: String): MapLoadState =
        if (layerId == "none") copy(layer = LayerLoadState.NONE, layerNeedsWeatherData = false)
        else copy(layer = LayerLoadState.LOADING, layerNeedsWeatherData = layerId in WEATHER_DATA_LAYERS)

    fun onLayerLoading(): MapLoadState = if (layer == LayerLoadState.NONE) this else copy(layer = LayerLoadState.LOADING)

    fun onLayerActive(): MapLoadState = if (layer == LayerLoadState.LOADING) copy(layer = LayerLoadState.ACTIVE) else this

    fun onLayerFailed(): MapLoadState = if (layer == LayerLoadState.LOADING) copy(layer = LayerLoadState.FAILED) else this

    companion object {
        val WEATHER_DATA_LAYERS = setOf("precip", "temp", "clouds", "wind", "pressure", "humidity", "dew", "uv", "aqi", "sea", "coldfront")
    }
}
