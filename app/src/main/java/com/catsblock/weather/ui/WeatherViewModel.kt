package com.catsblock.weather.ui

import com.catsblock.weather.i18n.tr
import com.catsblock.weather.data.UserKeys
import com.catsblock.weather.data.WeatherSource
import com.catsblock.weather.work.BackgroundUpdates
import com.catsblock.weather.data.PressureUnit
import com.catsblock.weather.data.WindUnit
import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catsblock.weather.alerts.EclipseAlerts
import com.catsblock.weather.alerts.RainAlerts
import com.catsblock.weather.alerts.SnowAlerts
import com.catsblock.weather.ui.developer.AppLog
import com.catsblock.weather.update.AppUpdates
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.City
import com.catsblock.weather.data.CitySummary
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.NetworkPolicy
import com.catsblock.weather.data.SettingsStore
import com.catsblock.weather.data.ActivityStore
import com.catsblock.weather.data.ColorStyle
import com.catsblock.weather.data.FontChoice
import com.catsblock.weather.data.PrecipUnit
import com.catsblock.weather.data.ThemeMode
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import android.os.SystemClock
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

enum class Screen { Welcome, Picker, Main, Map, Settings, IconKit, BackgroundKit, AppIcon, LiveMoon, Privacy, Info }

sealed interface WeatherState {
    data object Idle : WeatherState
    data object Loading : WeatherState
    data class Success(val bundle: WeatherBundle) : WeatherState
    data class Error(val message: String) : WeatherState
}

@OptIn(FlowPreview::class)
class WeatherViewModel(private val app: Application) : AndroidViewModel(app) {

    private companion object {
        const val FRESH_MS = 120_000L
    }

    private val store = SettingsStore(app)
    private val activity = ActivityStore(app)
    private val repo = WeatherRepository()
    private var loadJob: Job? = null

    val screen = MutableStateFlow<Screen?>(null)
    val refreshing = MutableStateFlow(false)
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val usageStatus = repo.usageStatus
    private var lastLimitNotice: String? = null
    private val cache = HashMap<String, WeatherBundle>()
    private val lastUpdated = HashMap<String, Long>()
    private var privacyReturn = Screen.Settings
    private var infoReturn = Screen.Settings
    val city = MutableStateFlow<City?>(null)
    val weather = MutableStateFlow<WeatherState>(WeatherState.Idle)
    val summaries = MutableStateFlow<Map<String, CitySummary>>(emptyMap())
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val activityStats = activity.stats.stateIn(viewModelScope, SharingStarted.Eagerly, com.catsblock.weather.data.ActivityStats())

    init {
        viewModelScope.launch { settings.collect { UserKeys.update(it.apiKeys) } }
        viewModelScope.launch { runCatching { activity.checkIn() } }
    }
    val savedCities = store.savedCities.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val queryText = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<City>>(emptyList())
    val searchLoading = MutableStateFlow(false)
    val searchMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            if (settings.first().weatherSource != WeatherSource.OpenMeteo) repo.refreshUsageStatus()
        }
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
                .map { it.weatherSource }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    cache.clear()
                    lastUpdated.clear()
                    if (it != WeatherSource.OpenMeteo) viewModelScope.launch { repo.refreshUsageStatus() }
                    if (city.value != null) refresh(silent = true)
                }
        }
        viewModelScope.launch {
            settings
                .map { it.backgroundUpdates to it.backgroundIntervalMinutes }
                .distinctUntilChanged()
                .drop(1)
                .collect { runCatching { BackgroundUpdates.apply(app, it.first, it.second) } }
        }
        viewModelScope.launch {
            settings
                .map { it.rainAlerts to it.rainLeadMinutes }
                .distinctUntilChanged()
                .drop(1)
                .collect { runCatching { RainAlerts.reschedule(app) } }
        }
        viewModelScope.launch {
            settings
                .map { it.eclipseAlerts to it.eclipseLeadMinutes }
                .distinctUntilChanged()
                .drop(1)
                .collect { runCatching { EclipseAlerts.reschedule(app) } }
        }
        viewModelScope.launch {
            settings
                .map { it.snowAlerts to it.snowLeadMinutes }
                .distinctUntilChanged()
                .drop(1)
                .collect { runCatching { SnowAlerts.reschedule(app) } }
        }
        viewModelScope.launch {
            WeatherBus.updates.collect { update ->
                cache[update.city.id] = update.bundle
                lastUpdated[update.city.id] = SystemClock.elapsedRealtime()
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

    fun openAppIcon() {
        screen.value = Screen.AppIcon
    }

    fun openLiveMoon() {
        screen.value = Screen.LiveMoon
    }

    fun openPrivacy(from: Screen) {
        privacyReturn = from
        screen.value = Screen.Privacy
    }

    fun openInfo(from: Screen) {
        infoReturn = from
        screen.value = Screen.Info
    }

    fun openSettings() {
        screen.value = Screen.Settings
    }

    fun openMap() {
        screen.value = Screen.Map
    }

    fun openWeather() {
        screen.value = Screen.Main
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
            Screen.Settings, Screen.Map, Screen.LiveMoon -> Screen.Main
            Screen.Privacy -> privacyReturn
            Screen.Info -> infoReturn
            Screen.IconKit, Screen.BackgroundKit, Screen.AppIcon -> Screen.Settings
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
        val cached = cache[selected.id]
        if (cached != null) {
            weather.value = WeatherState.Success(cached)
            val age = SystemClock.elapsedRealtime() - (lastUpdated[selected.id] ?: 0L)
            if (age >= FRESH_MS) refresh(true)
        } else {
            weather.value = WeatherState.Loading
            refresh()
        }
    }

    fun selectAdjacent(step: Int): Boolean {
        val list = savedCities.value
        val index = list.indexOfFirst { it.id == city.value?.id }
        if (index < 0) return false
        val next = list.getOrNull(index + step) ?: return false
        selectCity(next)
        return true
    }

    fun pullRefresh(): Boolean {
        val target = city.value ?: return false
        val age = SystemClock.elapsedRealtime() - (lastUpdated[target.id] ?: 0L)
        if (weather.value is WeatherState.Success && age < FRESH_MS) {
            notices.tryEmit("Weather data is already up to date")
            return false
        }
        refreshing.value = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                load(target, true)
                val rest = 900L - (SystemClock.elapsedRealtime() - started)
                if (rest > 0) delay(rest)
            } finally {
                refreshing.value = false
            }
        }
        return true
    }

    fun removeCity(target: City) {
        viewModelScope.launch {
            val list = store.savedCities.first()
            val index = list.indexOfFirst { it.id == target.id }
            val remaining = list.filter { it.id != target.id }
            store.saveCities(remaining)
            if (city.value?.id == target.id) {
                val next = remaining.getOrNull(index.coerceAtLeast(0)) ?: remaining.lastOrNull()
                if (next != null) {
                    city.value = next
                    store.setCurrent(next)
                    refresh()
                } else {
                    loadJob?.cancel()
                    city.value = null
                    weather.value = WeatherState.Idle
                    store.clearCurrent()
                    screen.value = Screen.Welcome
                }
            }
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
                AppLog.warn("Location", "Could not determine the current location", e)
                weather.value = WeatherState.Error("Could not determine your location.")
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverse(lat: Double, lon: Double): City = withContext(Dispatchers.IO) {
        runCatching {
            val address = Geocoder(app, Locale.ENGLISH).getFromLocation(lat, lon, 1)?.firstOrNull()
            City(
                name = address?.locality ?: address?.subAdminArea ?: tr("Current location"),
                region = address?.adminArea ?: "",
                country = address?.countryName ?: "",
                code = address?.countryCode ?: "",
                lat = lat,
                lon = lon
            )
        }.getOrElse { City(tr("Current location"), "", "", "", lat, lon) }
    }

    fun refresh(silent: Boolean = false) {
        val target = city.value
        if (target == null) {
            locate()
            return
        }
        refreshing.value = false
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load(target, silent) }
    }

    private suspend fun load(target: City, silent: Boolean) {
        val hasData = weather.value is WeatherState.Success
        if (!silent || !hasData) weather.value = WeatherState.Loading
        val current = store.settings.first()
        if (!NetworkPolicy.foregroundAllowed(app, current)) {
            AppLog.warn("Weather", "Refresh skipped because mobile data updates are disabled")
            if (!silent || !hasData) {
                weather.value = WeatherState.Error("Mobile data updates are disabled. Connect to Wi-Fi or enable them in Settings.")
            }
            return
        }
        try {
            val bundle = repo.fetch(target, current.weatherSource)
            cache[target.id] = bundle
            lastUpdated[target.id] = SystemClock.elapsedRealtime()
            weather.value = WeatherState.Success(bundle)
            notifyUsageLimitIfNeeded()
            runCatching { activity.record(target, bundle) }
            runCatching { WidgetUpdater.publish(app, target, bundle) }
            runCatching { RainAlerts.onFresh(app, target, bundle) }
            runCatching { SnowAlerts.onFresh(app, target, bundle) }
            runCatching { EclipseAlerts.onFresh(app, target) }
            runCatching { AppUpdates.check(app) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.error("Weather", "Forecast request failed", e)
            if (!silent || !hasData) {
                weather.value = WeatherState.Error("Could not load the weather. Check your connection and try again.")
            }
        }
    }

    private fun notifyUsageLimitIfNeeded() {
        val status = usageStatus.value
        val message = when {
            status.exhaustedSeconds > 0 ->
                tr("Provider limit reached. Using the free data source for") + " " + formatWait(status.exhaustedSeconds)
            status.lockoutSeconds > 0 ->
                tr("Too many requests. Try again in") + " " + formatWait(status.lockoutSeconds)
            else -> null
        }
        if (message != lastLimitNotice) {
            lastLimitNotice = message
            if (message != null) notices.tryEmit(message)
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

    fun setTheme(value: ThemeMode) {
        viewModelScope.launch { store.setTheme(value) }
    }

    fun setAmoled(value: Boolean) {
        viewModelScope.launch { store.setAmoled(value) }
    }

    fun setColorStyle(value: ColorStyle) {
        viewModelScope.launch { store.setColorStyle(value) }
    }

    fun setCustomColor(value: Int) {
        viewModelScope.launch { store.setCustomColor(value) }
    }

    fun setTextScale(value: Float) {
        viewModelScope.launch { store.setTextScale(value) }
    }

    fun setFontChoice(value: FontChoice) {
        viewModelScope.launch { store.setFontChoice(value) }
    }

    fun setCustomFont(name: String) {
        viewModelScope.launch { store.setCustomFont(name) }
    }

    fun setPrecipUnit(value: PrecipUnit) {
        viewModelScope.launch { store.setPrecipUnit(value) }
    }

    fun setDisableEffectsOnSaver(value: Boolean) {
        viewModelScope.launch { store.setDisableEffectsOnSaver(value) }
    }

    fun setCompactHero(value: Boolean) {
        viewModelScope.launch { store.setCompactHero(value) }
    }

    fun setShowLifeIndex(value: Boolean) {
        viewModelScope.launch { store.setShowLifeIndex(value) }
    }

    fun setShowPrecipitation(value: Boolean) {
        viewModelScope.launch { store.setShowPrecipitation(value) }
    }

    fun setApiKey(source: WeatherSource, value: String) {
        viewModelScope.launch {
            store.setApiKey(source, value)
            UserKeys.update(settings.value.apiKeys.toMutableMap().also { map ->
                if (value.isBlank()) map.remove(source) else map[source] = value.trim()
            })
        }
    }

    fun setWeatherSource(value: WeatherSource) {
        viewModelScope.launch { store.setWeatherSource(value) }
    }

    fun setWindUnit(value: WindUnit) {
        viewModelScope.launch { store.setWindUnit(value) }
    }

    fun setPressureUnit(value: PressureUnit) {
        viewModelScope.launch { store.setPressureUnit(value) }
    }

    fun setBackgroundUpdates(value: Boolean) {
        viewModelScope.launch { store.setBackgroundUpdates(value) }
    }

    fun setBackgroundInterval(minutes: Int) {
        viewModelScope.launch { store.setBackgroundInterval(minutes) }
    }

    fun setFahrenheit(value: Boolean) {
        viewModelScope.launch { store.setFahrenheit(value) }
    }

    fun setMobileData(value: Boolean) {
        viewModelScope.launch { store.setMobileData(value) }
    }

    fun setQuietHours(value: Boolean) {
        viewModelScope.launch { store.setQuietHours(value) }
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

    fun setEclipseAlerts(value: Boolean) {
        viewModelScope.launch { store.setEclipseAlerts(value) }
    }

    fun setEclipseLead(minutes: Int) {
        viewModelScope.launch { store.setEclipseLead(minutes) }
    }

    fun setUpdateAlerts(value: Boolean) {
        viewModelScope.launch { store.setUpdateAlerts(value) }
    }

    fun setSnowAlerts(value: Boolean) {
        viewModelScope.launch { store.setSnowAlerts(value) }
    }

    fun setSnowLead(minutes: Int) {
        viewModelScope.launch { store.setSnowLead(minutes) }
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
