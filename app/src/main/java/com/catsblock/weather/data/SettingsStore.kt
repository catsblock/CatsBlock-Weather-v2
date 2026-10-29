package com.catsblock.weather.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.catsblock.weather.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "weather_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val fahrenheit = booleanPreferencesKey("fahrenheit")
        val weatherSource = stringPreferencesKey("weather_source")
        val windUnit = stringPreferencesKey("wind_unit")
        val pressureUnit = stringPreferencesKey("pressure_unit")
        val backgroundUpdates = booleanPreferencesKey("background_updates")
        val backgroundInterval = intPreferencesKey("background_interval_minutes")
        val theme = stringPreferencesKey("theme")
        val compactHero = booleanPreferencesKey("compact_hero")
        val showLifeIndex = booleanPreferencesKey("show_life_index")
        val showPrecipitation = booleanPreferencesKey("show_precipitation")
        val mobileData = booleanPreferencesKey("mobile_data")
        val quietHours = booleanPreferencesKey("quiet_hours")
        val sounds = booleanPreferencesKey("sounds")
        val language = stringPreferencesKey("language")
        val rainAlerts = booleanPreferencesKey("rain_alerts")
        val rainLead = intPreferencesKey("rain_lead_minutes")
        val eclipseAlerts = booleanPreferencesKey("eclipse_alerts")
        val eclipseLead = intPreferencesKey("eclipse_lead_minutes")
        val snowAlerts = booleanPreferencesKey("snow_alerts")
        val snowLead = intPreferencesKey("snow_lead_minutes")
        val updateAlerts = booleanPreferencesKey("update_alerts")
        val saved = stringPreferencesKey("saved_cities")
        val current = stringPreferencesKey("current_city")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map {
        AppSettings(
            fahrenheit = it[Keys.fahrenheit] ?: false,
            apiKeys = readKeys(it),
            weatherSource = runCatching { WeatherSource.valueOf(it[Keys.weatherSource] ?: "") }.getOrDefault(WeatherSource.OpenMeteo),
            windUnit = runCatching { WindUnit.valueOf(it[Keys.windUnit] ?: "") }.getOrDefault(WindUnit.Kmh),
            pressureUnit = runCatching { PressureUnit.valueOf(it[Keys.pressureUnit] ?: "") }.getOrDefault(PressureUnit.Hpa),
            backgroundUpdates = it[Keys.backgroundUpdates] ?: true,
            backgroundIntervalMinutes = it[Keys.backgroundInterval]?.takeIf { value -> value in BackgroundIntervalOptions } ?: 120,
            theme = runCatching { ThemeMode.valueOf(it[Keys.theme] ?: "") }.getOrDefault(ThemeMode.System),
            compactHero = it[Keys.compactHero] ?: false,
            showLifeIndex = it[Keys.showLifeIndex] ?: true,
            showPrecipitation = it[Keys.showPrecipitation] ?: true,
            mobileData = it[Keys.mobileData] ?: true,
            quietHours = it[Keys.quietHours] ?: true,
            sounds = it[Keys.sounds] ?: true,
            language = AppLanguage.from(it[Keys.language]),
            rainAlerts = it[Keys.rainAlerts] ?: true,
            rainLeadMinutes = it[Keys.rainLead]?.takeIf { value -> value in RainLeadOptions } ?: 60,
            eclipseAlerts = it[Keys.eclipseAlerts] ?: true,
            eclipseLeadMinutes = it[Keys.eclipseLead]?.takeIf { value -> value in EclipseLeadOptions } ?: 60,
            snowAlerts = it[Keys.snowAlerts] ?: true,
            snowLeadMinutes = it[Keys.snowLead]?.takeIf { value -> value in SnowLeadOptions } ?: 60,
            updateAlerts = it[Keys.updateAlerts] ?: true
        )
    }

    val savedCities: Flow<List<City>> = context.dataStore.data.map { prefs ->
        prefs[Keys.saved]?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { City.fromJson(arr.getJSONObject(it)) }
            }.getOrNull()
        } ?: emptyList()
    }

    val currentCity: Flow<City?> = context.dataStore.data.map { prefs ->
        prefs[Keys.current]?.let { raw -> runCatching { City.fromJson(JSONObject(raw)) }.getOrNull() }
    }

    suspend fun setFahrenheit(value: Boolean) {
        context.dataStore.edit { it[Keys.fahrenheit] = value }
    }

    private fun readKeys(prefs: androidx.datastore.preferences.core.Preferences): Map<WeatherSource, String> =
        WeatherSource.entries.mapNotNull { source ->
            prefs[stringPreferencesKey("api_key_" + source.name)]
                ?.takeIf { value -> value.isNotBlank() }
                ?.let { value -> source to value }
        }.toMap()

    suspend fun setApiKey(source: WeatherSource, value: String) {
        context.dataStore.edit { it[stringPreferencesKey("api_key_" + source.name)] = value.trim() }
    }

    suspend fun setWeatherSource(value: WeatherSource) {
        context.dataStore.edit { it[Keys.weatherSource] = value.name }
    }

    suspend fun setWindUnit(value: WindUnit) {
        context.dataStore.edit { it[Keys.windUnit] = value.name }
    }

    suspend fun setPressureUnit(value: PressureUnit) {
        context.dataStore.edit { it[Keys.pressureUnit] = value.name }
    }

    suspend fun setBackgroundUpdates(value: Boolean) {
        context.dataStore.edit { it[Keys.backgroundUpdates] = value }
    }

    suspend fun setBackgroundInterval(minutes: Int) {
        context.dataStore.edit { it[Keys.backgroundInterval] = minutes }
    }

    suspend fun setTheme(value: ThemeMode) {
        context.dataStore.edit { it[Keys.theme] = value.name }
    }

    suspend fun setCompactHero(value: Boolean) {
        context.dataStore.edit { it[Keys.compactHero] = value }
    }

    suspend fun setShowLifeIndex(value: Boolean) {
        context.dataStore.edit { it[Keys.showLifeIndex] = value }
    }

    suspend fun setShowPrecipitation(value: Boolean) {
        context.dataStore.edit { it[Keys.showPrecipitation] = value }
    }

    suspend fun setMobileData(value: Boolean) {
        context.dataStore.edit { it[Keys.mobileData] = value }
    }

    suspend fun setQuietHours(value: Boolean) {
        context.dataStore.edit { it[Keys.quietHours] = value }
    }

    suspend fun setSounds(value: Boolean) {
        context.dataStore.edit { it[Keys.sounds] = value }
    }

    suspend fun setLanguage(value: AppLanguage) {
        context.dataStore.edit { it[Keys.language] = value.code }
    }

    suspend fun setRainAlerts(value: Boolean) {
        context.dataStore.edit { it[Keys.rainAlerts] = value }
    }

    suspend fun setRainLead(minutes: Int) {
        context.dataStore.edit { it[Keys.rainLead] = minutes }
    }

    suspend fun setEclipseAlerts(value: Boolean) {
        context.dataStore.edit { it[Keys.eclipseAlerts] = value }
    }

    suspend fun setEclipseLead(minutes: Int) {
        context.dataStore.edit { it[Keys.eclipseLead] = minutes }
    }

    suspend fun setSnowAlerts(value: Boolean) {
        context.dataStore.edit { it[Keys.snowAlerts] = value }
    }

    suspend fun setSnowLead(minutes: Int) {
        context.dataStore.edit { it[Keys.snowLead] = minutes }
    }

    suspend fun setUpdateAlerts(value: Boolean) {
        context.dataStore.edit { it[Keys.updateAlerts] = value }
    }

    suspend fun saveCities(cities: List<City>) {
        val arr = JSONArray()
        cities.forEach { arr.put(it.toJson()) }
        context.dataStore.edit { it[Keys.saved] = arr.toString() }
    }

    suspend fun setCurrent(city: City) {
        context.dataStore.edit { it[Keys.current] = city.toJson().toString() }
    }

    suspend fun clearCurrent() {
        context.dataStore.edit { it.remove(Keys.current) }
    }
}
