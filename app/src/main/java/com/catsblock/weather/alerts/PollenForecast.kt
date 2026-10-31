package com.catsblock.weather.alerts

import com.catsblock.weather.data.City
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PollenLevels(val grasses: Double, val trees: Double, val weeds: Double)

object PollenForecast {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val GrassThresholds = doubleArrayOf(50.0, 20.0, 5.0)
    private val TreeThresholds = doubleArrayOf(90.0, 30.0, 10.0)
    private val WeedThresholds = doubleArrayOf(50.0, 10.0, 5.0)

    private val Fields = listOf(
        "grass_pollen", "alder_pollen", "birch_pollen", "olive_pollen", "mugwort_pollen", "ragweed_pollen"
    )

    suspend fun today(city: City): PollenLevels? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://air-quality-api.open-meteo.com/v1/air-quality".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", String.format(Locale.US, "%.4f", city.lat))
                .addQueryParameter("longitude", String.format(Locale.US, "%.4f", city.lon))
                .addQueryParameter("hourly", Fields.joinToString(","))
                .addQueryParameter("forecast_days", "1")
                .addQueryParameter("timezone", "auto")
                .build()
            val body = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body?.string()
            } ?: return@runCatching null
            val hourly = JSONObject(body).optJSONObject("hourly") ?: return@runCatching null
            fun peak(vararg names: String): Double? {
                var best: Double? = null
                for (name in names) {
                    val array = hourly.optJSONArray(name) ?: continue
                    for (i in 0 until array.length()) {
                        if (array.isNull(i)) continue
                        val value = array.optDouble(i)
                        if (best == null || value > best) best = value
                    }
                }
                return best
            }
            val grasses = peak("grass_pollen")
            val trees = peak("alder_pollen", "birch_pollen", "olive_pollen")
            val weeds = peak("mugwort_pollen", "ragweed_pollen")
            if (grasses == null && trees == null && weeds == null) null
            else PollenLevels(grasses ?: 0.0, trees ?: 0.0, weeds ?: 0.0)
        }.getOrNull()
    }

    fun exceeded(levels: PollenLevels, sensitivity: Int, grasses: Boolean, trees: Boolean, weeds: Boolean): List<String> {
        val index = sensitivity.coerceIn(0, 2)
        val result = mutableListOf<String>()
        if (grasses && levels.grasses >= GrassThresholds[index]) result += "Grasses"
        if (trees && levels.trees >= TreeThresholds[index]) result += "Trees"
        if (weeds && levels.weeds >= WeedThresholds[index]) result += "Weeds"
        return result
    }
}
