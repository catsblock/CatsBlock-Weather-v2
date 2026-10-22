package com.catsblock.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

object Sky {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val targets = listOf(
        "Sun" to "10",
        "Moon" to "301",
        "Mercury" to "199",
        "Venus" to "299",
        "Mars" to "499",
        "Jupiter" to "599",
        "Saturn" to "699",
        "Uranus" to "799",
        "Neptune" to "899"
    )

    @Volatile
    private var cached: String? = null

    @Volatile
    private var cachedKey = ""

    @Volatile
    private var cachedAt = 0L

    private fun query(command: String, center: String, site: String?, jd: Double): DoubleArray? = runCatching {
        val builder = HttpUrl.Builder()
            .scheme("https")
            .host("ssd.jpl.nasa.gov")
            .addPathSegments("api/horizons.api")
            .addQueryParameter("format", "json")
            .addQueryParameter("COMMAND", "'$command'")
            .addQueryParameter("OBJ_DATA", "'NO'")
            .addQueryParameter("MAKE_EPHEM", "'YES'")
            .addQueryParameter("EPHEM_TYPE", "'OBSERVER'")
            .addQueryParameter("CENTER", "'$center'")
            .addQueryParameter("TLIST", "'${String.format(Locale.US, "%.6f", jd)}'")
            .addQueryParameter("QUANTITIES", "'1,20'")
            .addQueryParameter("ANG_FORMAT", "'DEG'")
            .addQueryParameter("CSV_FORMAT", "'YES'")
        if (site != null) builder.addQueryParameter("SITE_COORD", "'$site'")
        client.newCall(Request.Builder().url(builder.build()).build()).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                val text = JSONObject(response.body?.string().orEmpty()).optString("result")
                val block = text.substringAfter("\$\$SOE", "").substringBefore("\$\$EOE")
                val row = block.trim().lineSequence().firstOrNull().orEmpty()
                val numbers = row.split(",").drop(1).mapNotNull { it.trim().toDoubleOrNull() }
                if (numbers.size >= 3) doubleArrayOf(numbers[0], numbers[1], numbers[2]) else null
            }
        }
    }.getOrNull()

    private suspend fun fetch(lat: Double, lon: Double, ms: Long): String? = coroutineScope {
        val jd = ms / 86400000.0 + 2440587.5
        val site = String.format(Locale.US, "%.4f,%.4f,0.1", lon, lat)
        val gate = Semaphore(4)
        val jobs = targets.map { (name, code) ->
            async { name to gate.withPermit { query(code, "coord@399", site, jd) } }
        }
        val geo = async { gate.withPermit { query("301", "500@399", null, jd) } }
        val results = jobs.awaitAll()
        val array = JSONArray()
        results.forEach { (name, v) ->
            if (v != null) {
                array.put(JSONObject().put("id", name).put("ra", v[0]).put("dec", v[1]).put("d", v[2]))
            }
        }
        val g = geo.await()
        if (array.length() < 2) {
            null
        } else {
            JSONObject()
                .put("bodies", array)
                .put("geo", if (g != null) JSONObject().put("ra", g[0]).put("dec", g[1]) else JSONObject.NULL)
                .toString()
        }
    }

    suspend fun bodies(lat: Double, lon: Double, ms: Long): String? {
        val key = String.format(Locale.US, "%.2f,%.2f", lat, lon)
        cached?.takeIf { cachedKey == key && ms - cachedAt < 240_000L }?.let { return it }
        val fresh = withContext(Dispatchers.IO) { fetch(lat, lon, ms) }
        if (fresh != null) {
            cached = fresh
            cachedKey = key
            cachedAt = ms
        }
        return fresh ?: cached?.takeIf { cachedKey == key }
    }
}
