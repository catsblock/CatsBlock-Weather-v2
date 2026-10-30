package com.catsblock.weather.ui.alerts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.alertDataStore by preferencesDataStore(name = "alert_settings")
private val StateKey = stringPreferencesKey("state")

enum class Commute { Walk, Bike, Car, Transit }

private fun monthMask(vararg months: Int): Int = months.fold(0) { acc, m -> acc or (1 shl m) }

val SummerMask: Int = monthMask(3, 4, 5, 6, 7, 8)
val WinterMask: Int = monthMask(10, 11, 0, 1, 2)

data class AlertState(
    val clothingOn: Boolean = false,
    val clothingTime: Int = 450,
    val thermal: Int = 1,
    val homeLocation: String = "",
    val workLocation: String = "",
    val workStart: Int = 540,
    val workEnd: Int = 1080,
    val commute: Commute = Commute.Transit,
    val uvOn: Boolean = false,
    val uvTime: Int = 480,
    val uvMonths: Int = SummerMask,
    val uvThreshold: Int = 6,
    val pollenOn: Boolean = false,
    val pollenTime: Int = 480,
    val pillOn: Boolean = false,
    val pillTime: Int = 540,
    val grasses: Boolean = true,
    val trees: Boolean = true,
    val weeds: Boolean = true,
    val pollenSensitivity: Int = 1,
    val frostOn: Boolean = false,
    val frostTime: Int = 1200,
    val frostMonths: Int = WinterMask,
    val briefingOn: Boolean = false,
    val briefingTime: Int = 1260,
    val briefingDetailed: Boolean = false,
    val pressureOn: Boolean = false,
    val pressureTime: Int = 480,
    val pressureDrop: Int = 4,
    val aqiOn: Boolean = false,
    val aqiTime: Int = 480,
    val aqiThreshold: Int = 4,
    val officialOn: Boolean = true,
    val yellowOn: Boolean = false,
    val orangeOn: Boolean = true,
    val redOn: Boolean = true
)

private fun encode(s: AlertState): String = JSONObject().apply {
    put("clothingOn", s.clothingOn)
    put("clothingTime", s.clothingTime)
    put("thermal", s.thermal)
    put("homeLocation", s.homeLocation)
    put("workLocation", s.workLocation)
    put("workStart", s.workStart)
    put("workEnd", s.workEnd)
    put("commute", s.commute.name)
    put("uvOn", s.uvOn)
    put("uvTime", s.uvTime)
    put("uvMonths", s.uvMonths)
    put("uvThreshold", s.uvThreshold)
    put("pollenOn", s.pollenOn)
    put("pollenTime", s.pollenTime)
    put("pillOn", s.pillOn)
    put("pillTime", s.pillTime)
    put("grasses", s.grasses)
    put("trees", s.trees)
    put("weeds", s.weeds)
    put("pollenSensitivity", s.pollenSensitivity)
    put("frostOn", s.frostOn)
    put("frostTime", s.frostTime)
    put("frostMonths", s.frostMonths)
    put("briefingOn", s.briefingOn)
    put("briefingTime", s.briefingTime)
    put("briefingDetailed", s.briefingDetailed)
    put("pressureOn", s.pressureOn)
    put("pressureTime", s.pressureTime)
    put("pressureDrop", s.pressureDrop)
    put("aqiOn", s.aqiOn)
    put("aqiTime", s.aqiTime)
    put("aqiThreshold", s.aqiThreshold)
    put("officialOn", s.officialOn)
    put("yellowOn", s.yellowOn)
    put("orangeOn", s.orangeOn)
    put("redOn", s.redOn)
}.toString()

private fun decode(raw: String?): AlertState {
    val d = AlertState()
    val o = runCatching { JSONObject(raw ?: "") }.getOrNull() ?: return d
    return AlertState(
        clothingOn = o.optBoolean("clothingOn", d.clothingOn),
        clothingTime = o.optInt("clothingTime", d.clothingTime),
        thermal = o.optInt("thermal", d.thermal).coerceIn(0, 2),
        homeLocation = o.optString("homeLocation", d.homeLocation),
        workLocation = o.optString("workLocation", d.workLocation),
        workStart = o.optInt("workStart", d.workStart),
        workEnd = o.optInt("workEnd", d.workEnd),
        commute = runCatching { Commute.valueOf(o.optString("commute")) }.getOrDefault(d.commute),
        uvOn = o.optBoolean("uvOn", d.uvOn),
        uvTime = o.optInt("uvTime", d.uvTime),
        uvMonths = o.optInt("uvMonths", d.uvMonths),
        uvThreshold = o.optInt("uvThreshold", d.uvThreshold).coerceIn(1, 11),
        pollenOn = o.optBoolean("pollenOn", d.pollenOn),
        pollenTime = o.optInt("pollenTime", d.pollenTime),
        pillOn = o.optBoolean("pillOn", d.pillOn),
        pillTime = o.optInt("pillTime", d.pillTime),
        grasses = o.optBoolean("grasses", d.grasses),
        trees = o.optBoolean("trees", d.trees),
        weeds = o.optBoolean("weeds", d.weeds),
        pollenSensitivity = o.optInt("pollenSensitivity", d.pollenSensitivity).coerceIn(0, 2),
        frostOn = o.optBoolean("frostOn", d.frostOn),
        frostTime = o.optInt("frostTime", d.frostTime),
        frostMonths = o.optInt("frostMonths", d.frostMonths),
        briefingOn = o.optBoolean("briefingOn", d.briefingOn),
        briefingTime = o.optInt("briefingTime", d.briefingTime),
        briefingDetailed = o.optBoolean("briefingDetailed", d.briefingDetailed),
        pressureOn = o.optBoolean("pressureOn", d.pressureOn),
        pressureTime = o.optInt("pressureTime", d.pressureTime),
        pressureDrop = o.optInt("pressureDrop", d.pressureDrop).coerceIn(1, 10),
        aqiOn = o.optBoolean("aqiOn", d.aqiOn),
        aqiTime = o.optInt("aqiTime", d.aqiTime),
        aqiThreshold = o.optInt("aqiThreshold", d.aqiThreshold).coerceIn(1, 6),
        officialOn = o.optBoolean("officialOn", d.officialOn),
        yellowOn = o.optBoolean("yellowOn", d.yellowOn),
        orangeOn = o.optBoolean("orangeOn", d.orangeOn),
        redOn = o.optBoolean("redOn", d.redOn)
    )
}

class AlertStore(private val context: Context) {

    val flow: Flow<AlertState> = context.alertDataStore.data.map { decode(it[StateKey]) }

    suspend fun update(block: (AlertState) -> AlertState) {
        context.alertDataStore.edit { prefs ->
            prefs[StateKey] = encode(block(decode(prefs[StateKey])))
        }
    }

    suspend fun reset() {
        context.alertDataStore.edit { prefs -> prefs[StateKey] = encode(AlertState()) }
    }
}
