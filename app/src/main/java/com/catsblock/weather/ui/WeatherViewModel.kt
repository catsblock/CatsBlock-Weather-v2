package com.catsblock.weather.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catsblock.weather.alerts.RainAlerts
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.City
import com.catsblock.weather.data.CitySummary
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.NetworkPolicy
import com.catsblock.weather.data.SettingsStore
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.data.WeatherBus
import com.catsblock.weather.data.WeatherRepository
import com.catsblock.weather.kit.BackgroundKit
import com.catsblock.weather.kit.IconKit
import com.catsblock.weather.kit.Slot
import com.catsblock.weather.widget.WidgetUpdater
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

enum class Screen { Welcome, Picker, Main, Map, Settings, IconKit, BackgroundKit }

sealed interface WeatherState {
    data object Idle : WeatherState
    data object Loading : WeatherState
    data class Success(val bundle: WeatherBundle) : WeatherState
    data class Error(val message: String) : WeatherState
}

@OptIn(FlowPreview::class)
class WeatherViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val repo = WeatherRepository()
    private var loadJob: Job? = null

    val screen = MutableStateFlow<Screen?>(null)
    val city = MutableStateFlow<City?>(null)
    val weather = MutableStateFlow<WeatherState>(WeatherState.Idle)
    val summaries = MutableStateFlow<Map<String, CitySummary>>(emptyMap())
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val savedCities = store.savedCities.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val queryText = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<City>>(emptyList())
    val searchLoading = MutableStateFlow(false)
    val searchMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val saved = store.currentCity.first()
            if (saved != null) {
                city.value = saved
                screen.value = Screen.Main
                refresh()
            } else {
                screen.value = Screen.Welcome
            }
        }
        viewModelScope.launch {
            queryText.debounce(2500).collectLatest { runSearch(it.trim()) }
        }
        viewModelScope.launch {
            settings
                .map { it.fahrenheit to it.language }
                .distinctUntilChanged()
                .drop(1)
                .collect { runCatching { WidgetUpdater.refresh(app) } }
        }
        viewModelScope.launch {
            settings
                .map { it.rainAlerts to it.rainLeadMinutes }
                .distinctUntilChanged()
                .drop(1)
                .collect { runCatching { RainAlerts.reschedule(app) } }
        }
        viewModelScope.launch {
            WeatherBus.updates.collect { update ->
                if (update.city.id == city.value?.id) {
                    weather.value = WeatherState.Success(update.bundle)
                }
            }
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.length < 2) {
            searchResults.value = emptyList()
            searchMessage.value = null
            searchLoading.value = false
            return
        }
        searchLoading.value = true
        searchMessage.value = null
        try {
            val results = repo.searchCities(query)
            searchResults.value = results
            searchMessage.value = if (results.isEmpty()) "No locations found" else null
            searchLoading.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            searchResults.value = emptyList()
            searchMessage.value = "Search failed. Check your connection and try again."
            searchLoading.value = false
        }
    }

    fun onQuery(text: String) {
        queryText.value = text
        val active = text.trim().length >= 2
        searchLoading.value = active
        if (!active) {
            searchResults.value = emptyList()
            searchMessage.value = null
        }
    }

    fun openPicker() {
        screen.value = Screen.Picker
    }

    fun openMap() {
        screen.value = Screen.Map
    }

    fun openSettings() {
        screen.value = Screen.Settings
    }

    fun openIconKit() {
        screen.value = Screen.IconKit
    }

    fun openBackgroundKit() {
        screen.value = Screen.BackgroundKit
    }

    fun back() {
        screen.value = when (screen.value) {
            Screen.Picker -> if (city.value != null) Screen.Main else Screen.Welcome
            Screen.Map, Screen.Settings -> Screen.Main
            Screen.IconKit, Screen.BackgroundKit -> Screen.Settings
            else -> screen.value
        }
    }

    fun selectCity(selected: City) {
        city.value = selected
        screen.value = Screen.Main
        queryText.value = ""
        searchResults.value = emptyList()
        searchLoading.value = false
        searchMessage.value = null
        viewModelScope.launch {
            val list = store.savedCities.first()
            if (list.none { it.id == selected.id }) store.saveCities(list + selected)
            store.setCurrent(selected)
        }
        refresh()
    }

    fun removeCity(target: City) {
        viewModelScope.launch {
            store.saveCities(store.savedCities.first().filter { it.id != target.id })
        }
    }

    @SuppressLint("MissingPermission")
    fun locate() {
        screen.value = Screen.Main
        weather.value = WeatherState.Loading
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(app)
                val location = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                    ?: client.lastLocation.await()
                if (location == null) {
                    weather.value = WeatherState.Error("Could not determine your location.")
                    return@launch
                }
                val located = reverse(location.latitude, location.longitude)
                city.value = located
                store.setCurrent(located)
                val list = store.savedCities.first()
                if (list.none { it.id == located.id }) store.saveCities(list + located)
                load(located, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                weather.value = WeatherState.Error("Could not determine your location.")
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverse(lat: Double, lon: Double): City = withContext(Dispatchers.IO) {
        runCatching {
            val address = Geocoder(app, Locale.ENGLISH).getFromLocation(lat, lon, 1)?.firstOrNull()
            City(
                name = address?.locality ?: address?.subAdminArea ?: "Current location",
                region = address?.adminArea ?: "",
                country = address?.countryName ?: "",
                code = address?.countryCode ?: "",
                lat = lat,
                lon = lon
            )
        }.getOrElse { City("Current location", "", "", "", lat, lon) }
    }

    fun refresh(silent: Boolean = false) {
        val target = city.value
        if (target == null) {
            locate()
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load(target, silent) }
    }

    private suspend fun load(target: City, silent: Boolean) {
        val hasData = weather.value is WeatherState.Success
        if (!silent || !hasData) weather.value = WeatherState.Loading
        val current = store.settings.first()
        if (!NetworkPolicy.foregroundAllowed(app, current)) {
            if (!silent || !hasData) {
                weather.value = WeatherState.Error("Mobile data updates are disabled. Connect to Wi-Fi or enable them in Settings.")
            }
            return
        }
        try {
            val bundle = repo.fetch(target)
            weather.value = WeatherState.Success(bundle)
            runCatching { WidgetUpdater.publish(app, target, bundle) }
            runCatching { RainAlerts.onFresh(app, target, bundle) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!silent || !hasData) {
                weather.value = WeatherState.Error("Could not load the weather. Check your connection and try again.")
            }
        }
    }

    fun loadSummaries() {
        viewModelScope.launch {
            val current = store.settings.first()
            if (!NetworkPolicy.foregroundAllowed(app, current)) return@launch
            val list = store.savedCities.first()
            val results = coroutineScope {
                list.map { c -> async { c.id to runCatching { repo.summary(c) }.getOrNull() } }.awaitAll()
            }
            summaries.value = results.mapNotNull { (id, summary) -> summary?.let { id to it } }.toMap()
        }
    }

    fun setFahrenheit(value: Boolean) {
        viewModelScope.launch { store.setFahrenheit(value) }
    }

    fun setMobileData(value: Boolean) {
        viewModelScope.launch { store.setMobileData(value) }
    }

    fun setSounds(value: Boolean) {
        viewModelScope.launch { store.setSounds(value) }
    }

    fun setRainAlerts(value: Boolean) {
        viewModelScope.launch { store.setRainAlerts(value) }
    }

    fun setRainLead(minutes: Int) {
        viewModelScope.launch { store.setRainLead(minutes) }
    }

    fun importIcon(slot: Slot, uri: Uri, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = IconKit.import(app, slot, uri)
            if (ok) runCatching { WidgetUpdater.refresh(app) }
            done(ok)
        }
    }

    fun resetIcon(slot: Slot) {
        viewModelScope.launch {
            IconKit.reset(app, slot)
            runCatching { WidgetUpdater.refresh(app) }
        }
    }

    fun resetAllIcons() {
        viewModelScope.launch {
            IconKit.resetAll(app)
            runCatching { WidgetUpdater.refresh(app) }
        }
    }

    fun importBackground(slot: Slot, uri: Uri, done: (Boolean) -> Unit) {
        viewModelScope.launch { done(BackgroundKit.importVisual(app, slot, uri)) }
    }

    fun importSound(kind: Condition, uri: Uri, done: (Boolean) -> Unit) {
        viewModelScope.launch { done(BackgroundKit.importSound(app, kind, uri)) }
    }

    fun resetBackground(slot: Slot) {
        viewModelScope.launch { BackgroundKit.resetVisual(app, slot) }
    }

    fun resetSound(kind: Condition) {
        viewModelScope.launch { BackgroundKit.resetSound(app, kind) }
    }

    fun resetAllBackgrounds() {
        viewModelScope.launch { BackgroundKit.resetAll(app) }
    }
}
