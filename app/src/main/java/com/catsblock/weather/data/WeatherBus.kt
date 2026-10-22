package com.catsblock.weather.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object WeatherBus {

    data class Update(val city: City, val bundle: WeatherBundle)

    private val flow = MutableSharedFlow<Update>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val updates: SharedFlow<Update> = flow.asSharedFlow()

    fun emit(city: City, bundle: WeatherBundle) {
        flow.tryEmit(Update(city, bundle))
    }
}
