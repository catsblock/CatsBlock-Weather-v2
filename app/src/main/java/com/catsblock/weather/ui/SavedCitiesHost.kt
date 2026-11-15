package com.catsblock.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.CitySummary

@Composable
fun SavedCitiesHost(vm: WeatherViewModel) {
    val open by vm.citiesOpen.collectAsStateWithLifecycle()
    val saved by vm.savedCities.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val city by vm.city.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.weather.collectAsStateWithLifecycle()
    val current = remember(state) {
        (state as? WeatherState.Success)?.bundle?.let { bundle ->
            val today = bundle.current.now.toLocalDate()
            val day = bundle.days.firstOrNull { it.date == today } ?: bundle.days.firstOrNull()
            day?.let {
                CitySummary(
                    bundle.current.kind,
                    bundle.current.isDay,
                    bundle.current.temp,
                    it.min,
                    it.max,
                    bundle.current.now.toLocalTime()
                )
            }
        }
    }
    CitiesOverlay(
        visible = open,
        saved = saved,
        selectedId = city?.id,
        current = current,
        summaries = summaries,
        fahrenheit = settings.fahrenheit,
        onSelect = {
            vm.closeCities()
            vm.selectCity(it)
        },
        onRemove = vm::removeCity,
        onAdd = {
            vm.closeCities()
            vm.openPicker()
        },
        onClose = vm::closeCities
    )
}
