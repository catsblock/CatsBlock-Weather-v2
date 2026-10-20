package com.catsblock.weather.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "weather_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val fahrenheit = booleanPreferencesKey("fahrenheit")
        val mobileData = booleanPreferencesKey("mobile_data")
        val sounds = booleanPreferencesKey("sounds")
        val saved = stringPreferencesKey("saved_cities")
        val current = stringPreferencesKey("current_city")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map {
        AppSettings(
            fahrenheit = it[Keys.fahrenheit] ?: false,
            mobileData = it[Keys.mobileData] ?: true,
            sounds = it[Keys.sounds] ?: true
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

    suspend fun setMobileData(value: Boolean) {
        context.dataStore.edit { it[Keys.mobileData] = value }
    }

    suspend fun setSounds(value: Boolean) {
        context.dataStore.edit { it[Keys.sounds] = value }
    }

    suspend fun saveCities(cities: List<City>) {
        val arr = JSONArray()
        cities.forEach { arr.put(it.toJson()) }
        context.dataStore.edit { it[Keys.saved] = arr.toString() }
    }

    suspend fun setCurrent(city: City) {
        context.dataStore.edit { it[Keys.current] = city.toJson().toString() }
    }
}
